package org.telegram.ox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.telegram.messenger.FileLog;

/** Alarm entry point: starts the foreground runner or falls back to a tap-to-run notification. */
public class OxWorkflowReceiver extends BroadcastReceiver {

    private static final String TAG = "OxWorkflowReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !OxWorkflowScheduler.ACTION_RUN.equals(intent.getAction())) return;
        try {
            // The receiver can be the first component in a cold process; the notifier
            // reads locale strings and store prefs, so finish the standard init first.
            org.telegram.messenger.ApplicationLoader.postInitApplication();
        } catch (Throwable t) {
            FileLog.e(TAG + " postInit failed", t);
        }
        String id = intent.getStringExtra(OxWorkflowScheduler.EXTRA_ID);
        OxWorkflow w = OxWorkflowStore.get(id);
        if (w == null || !w.isScheduled()) return;
        Context app = context.getApplicationContext();
        Intent svc = new Intent(app, OxWorkflowService.class)
                .setAction(OxWorkflowScheduler.ACTION_RUN)
                .putExtra(OxWorkflowScheduler.EXTRA_ID, w.id);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                app.startForegroundService(svc);
            } else {
                app.startService(svc);
            }
        } catch (Throwable t) {
            // Android 12+ can refuse a background foreground-service start; let the user
            // start the same workflow from the notification instead of dropping it.
            FileLog.e(TAG + " start service failed id=" + w.id, t);
            try {
                android.app.NotificationManager nm =
                        (android.app.NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    nm.notify(OxWorkflowNotifier.notificationId(w.id), OxWorkflowNotifier.due(app, w));
                }
            } catch (Throwable t2) {
                FileLog.e(TAG + " fallback failed", t2);
            }
        }
    }
}
