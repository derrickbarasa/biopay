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

/** Posts this app's local notifications: an alert that offline-captured records are stuck and
 * haven't reached the server after repeated attempts (gated by {@link SyncAlertsManager}), and an
 * alert that a queued voucher redemption was explicitly rejected by the server on sync. */
public final class NotificationHelper {

    private static final String CHANNEL_ID = "sync_alerts";
    private static final int NOTIFICATION_ID = 1001;
    private static final int VOUCHER_REJECTED_NOTIFICATION_ID = 1002;

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

    /** Called after every sync attempt, regardless of overall outcome -- unlike
     * {@link #reportSyncResult}, this fires immediately (no attempt-count gate or streak
     * de-duplication) because a rejected redemption is a one-time, actionable event: the agent
     * may need to recover cash or goods already handed over on the strength of a stale offline
     * scan. See {@link com.biopay.agent.data.VoucherDao#listFailedRedemptions()}. */
    public static void reportVoucherRedemptionRejections(Context context, int rejectedCount) {
        if (rejectedCount <= 0) return;
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
                .setContentTitle(context.getString(R.string.notification_voucher_rejected_title))
                .setContentText(context.getString(R.string.notification_voucher_rejected_body, rejectedCount))
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManagerCompat.from(context).notify(VOUCHER_REJECTED_NOTIFICATION_ID, notification.build());
    }
}
