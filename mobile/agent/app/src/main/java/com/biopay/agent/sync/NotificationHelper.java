package com.biopay.agent.sync;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.biopay.agent.R;
import com.biopay.agent.home.HomeActivity;
import android.app.PendingIntent;
import android.content.Intent;

/** Posts the one local notification this app sends: an alert that offline-captured records are
 * stuck and haven't reached the server after repeated attempts. Gated by {@link SyncAlertsManager}. */
public final class NotificationHelper {

    private static final String CHANNEL_ID = "sync_alerts";
    private static final int NOTIFICATION_ID = 1001;

    private NotificationHelper() {
    }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.notification_channel_sync_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.notification_channel_sync_description));
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    /** Called after every sync attempt. Notifies once per failure streak (not on every retry) and
     * clears the streak flag as soon as a pass fully succeeds. */
    public static void reportSyncResult(Context context, boolean succeeded, int pendingCount, int runAttemptCount) {
        SyncAlertsManager alerts = new SyncAlertsManager(context);
        if (succeeded) {
            alerts.setAlreadyNotified(false);
            return;
        }
        if (!alerts.isEnabled() || alerts.alreadyNotified() || runAttemptCount < 3) {
            return;
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Intent openIntent = new Intent(context, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sync)
                .setContentTitle(context.getString(R.string.notification_sync_title))
                .setContentText(context.getString(R.string.notification_sync_body, pendingCount))
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification.build());
        alerts.setAlreadyNotified(true);
    }
}
