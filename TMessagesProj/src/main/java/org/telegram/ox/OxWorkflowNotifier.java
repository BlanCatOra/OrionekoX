package org.telegram.ox;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.LaunchActivity;

/** Notification channel and builders shared by the workflow receiver and service. */
public class OxWorkflowNotifier {

    private static final String TAG = "OxWorkflowNotifier";
    static final String CHANNEL_ID = "ox_workflows";
    private static final int CONTENT_REQUEST = 7910;

    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    LocaleController.getString(R.string.OxWorkflows),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(LocaleController.getString(R.string.OxWorkflowsChannelInfo));
            ch.setShowBadge(true);
            nm.createNotificationChannel(ch);
        } catch (Throwable t) {
            FileLog.e(TAG + " channel failed", t);
        }
    }

    static int notificationId(String workflowId) {
        return 790000 + (Math.abs(workflowId.hashCode()) % 100000);
    }

    static Notification running(Context context, OxWorkflow w) {
        ensureChannel(context);
        return base(context, w, LocaleController.formatString("OxWorkflowRunning",
                        R.string.OxWorkflowRunning, w.name),
                LocaleController.getString(R.string.OxWorkflowRunningText))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    static Notification result(Context context, OxWorkflow w, String text) {
        ensureChannel(context);
        return base(context, w, LocaleController.formatString("OxWorkflowDone",
                        R.string.OxWorkflowDone, w.name), text)
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
    }

    static Notification due(Context context, OxWorkflow w) {
        ensureChannel(context);
        Intent svc = new Intent(context, OxWorkflowService.class)
                .setAction(OxWorkflowScheduler.ACTION_RUN)
                .putExtra(OxWorkflowScheduler.EXTRA_ID, w.id);
        PendingIntent run = Build.VERSION.SDK_INT >= 26
                ? PendingIntent.getForegroundService(context, notificationId(w.id), svc,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
                : PendingIntent.getService(context, notificationId(w.id), svc,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification)
                .setContentTitle(LocaleController.formatString("OxWorkflowDue",
                        R.string.OxWorkflowDue, w.name))
                .setContentText(LocaleController.getString(R.string.OxWorkflowDueText))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(LocaleController.getString(R.string.OxWorkflowDueText)))
                .setContentIntent(run)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
    }

    private static NotificationCompat.Builder base(Context context, OxWorkflow w, String title, String text) {
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openApp(context));
    }

    private static PendingIntent openApp(Context context) {
        Intent intent = new Intent(context, LaunchActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return PendingIntent.getActivity(context, CONTENT_REQUEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static String snippet(String text) {
        if (text == null) return "";
        String oneLine = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (oneLine.length() > 160) oneLine = oneLine.substring(0, 160) + "…";
        return TextUtils.isEmpty(oneLine) ? LocaleController.getString(R.string.OxWorkflowEmptyReply) : oneLine;
    }
}
