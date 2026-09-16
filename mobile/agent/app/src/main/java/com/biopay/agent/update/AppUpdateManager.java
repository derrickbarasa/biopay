package com.biopay.agent.update;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.biopay.agent.BuildConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Self-update for the sideloaded agent app: with no Play Store to push updates, the app polls the
 * backend's own {@code /biopay/downloads/version/:filename} route (see AppReleaseStore on the
 * backend) for a versionCode newer than its own, downloads the matching APK through the system
 * DownloadManager (so a backgrounded/killed app still finishes the download), and hands the
 * result to the package installer via a FileProvider content:// URI.
 *
 * <p>See {@code SettingsActivity} for the manual "Check for updates" + install flow and
 * {@code HomeActivity} for the passive once-a-day nudge that just points the officer at Settings.
 */
public final class AppUpdateManager {

    private static final String PREFS_NAME = "biopay_app_update";
    private static final String KEY_LAST_CHECKED_AT = "last_checked_at";
    private static final long AUTO_CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(24);

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AppUpdateManager() {
    }

    public static final class UpdateInfo {
        public final int versionCode;
        public final String versionName;
        public final String notes;

        UpdateInfo(int versionCode, String versionName, String notes) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.notes = notes;
        }
    }

    public interface Callback {
        void onUpdateAvailable(UpdateInfo info);
        void onUpToDate();
        void onError(String message);
    }

    /** Settings' manual "Check for updates" button passes {@code force=true} to always hit the
     *  network. Home's passive check passes {@code force=false}: if the last check (success or
     *  failure) was under 24h ago, this returns immediately without calling {@code callback} at
     *  all -- same quiet, advisory spirit as HomeActivity#checkSubscriptionGrace. */
    public static void checkForUpdate(Context context, boolean force, Callback callback) {
        SharedPreferences prefs = prefs(context);
        long lastCheckedAt = prefs.getLong(KEY_LAST_CHECKED_AT, 0);
        if (!force && System.currentTimeMillis() - lastCheckedAt < AUTO_CHECK_INTERVAL_MS) {
            return;
        }

        String url = BuildConfig.BIOPAY_API_BASE_URL + "/downloads/version/" + BuildConfig.RELEASE_APK_FILENAME;
        Request request = new Request.Builder().url(url).get().build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException ex) {
                prefs.edit().putLong(KEY_LAST_CHECKED_AT, System.currentTimeMillis()).apply();
                postError("BioPay couldn't reach the server to check for updates.", callback);
            }

            @Override
            public void onResponse(Call call, Response response) {
                prefs.edit().putLong(KEY_LAST_CHECKED_AT, System.currentTimeMillis()).apply();
                try (Response r = response) {
                    if (r.code() == 404) {
                        // No release metadata published yet -- not an error, just nothing to offer.
                        postUpToDate(callback);
                        return;
                    }
                    if (!r.isSuccessful() || r.body() == null) {
                        postError("The server returned an unexpected response (" + r.code() + ").", callback);
                        return;
                    }
                    JSONObject body = new JSONObject(r.body().string());
                    int versionCode = body.getInt("versionCode");
                    if (versionCode <= BuildConfig.VERSION_CODE) {
                        postUpToDate(callback);
                        return;
                    }
                    postAvailable(new UpdateInfo(versionCode,
                            body.optString("versionName", ""), body.optString("notes", "")), callback);
                } catch (IOException | JSONException ex) {
                    postError("The update check response wasn't understood.", callback);
                }
            }
        });
    }

    /** Where the downloaded APK lands -- also what {@link #promptInstall} hands to the
     *  installer, so this must stay in sync with {@code file_paths.xml}'s
     *  {@code external-files-path path="Download/"}. */
    public static File downloadDestination(Context context) {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            throw new IllegalStateException("Device storage is not available right now.");
        }
        return new File(dir, BuildConfig.RELEASE_APK_FILENAME);
    }

    /** Enqueues the APK with the system DownloadManager (survives BioPay being backgrounded or
     *  killed) and returns the download id to poll via {@link #isDownloadSuccessful}. Overwrites
     *  any previous download under the same name -- DownloadManager refuses to write over an
     *  existing file. */
    public static long enqueueDownload(Context context, UpdateInfo info) {
        File destFile = downloadDestination(context);
        if (destFile.exists()) {
            destFile.delete();
        }
        String url = BuildConfig.BIOPAY_API_BASE_URL + "/downloads/" + BuildConfig.RELEASE_APK_FILENAME;
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .setTitle("BioPay update")
                .setDescription("Downloading version " + info.versionName)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, BuildConfig.RELEASE_APK_FILENAME)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setMimeType("application/vnd.android.package-archive");
        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        return downloadManager.enqueue(request);
    }

    public static boolean isDownloadSuccessful(Context context, long downloadId) {
        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        try (Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterById(downloadId))) {
            if (cursor == null || !cursor.moveToFirst()) return false;
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            return status == DownloadManager.STATUS_SUCCESSFUL;
        }
    }

    /** The "allow installs from this source" toggle -- separate from, and in addition to, the
     *  {@code REQUEST_INSTALL_PACKAGES} manifest permission, which only makes this toggle
     *  available to grant at all. Pre-O devices have no such toggle: installs are always allowed. */
    public static boolean canInstallPackages(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || context.getPackageManager().canRequestPackageInstalls();
    }

    public static Intent installPermissionSettingsIntent(Context context) {
        return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.getPackageName()));
    }

    /** Launches the system installer for the already-downloaded APK. Caller must have confirmed
     *  {@link #canInstallPackages} first (or sent the officer through
     *  {@link #installPermissionSettingsIntent} and be retrying after they come back). */
    public static void promptInstall(Activity activity, File apkFile) {
        Uri contentUri = FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".fileprovider", apkFile);
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(contentUri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static void postAvailable(UpdateInfo info, Callback callback) {
        mainHandler.post(() -> callback.onUpdateAvailable(info));
    }

    private static void postUpToDate(Callback callback) {
        mainHandler.post(callback::onUpToDate);
    }

    private static void postError(String message, Callback callback) {
        mainHandler.post(() -> callback.onError(message));
    }
}
