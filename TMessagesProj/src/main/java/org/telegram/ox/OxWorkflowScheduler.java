package org.telegram.ox;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

/** One alarm per enabled workflow; the alarm starts the runner service. */
public class OxWorkflowScheduler {

    private static final String TAG = "OxWorkflowScheduler";
    public static final String ACTION_RUN = "org.telegram.ox.action.RUN_WORKFLOW";
    public static final String EXTRA_ID = "workflow_id";
    private static final long CATCH_UP_DELAY_MS = 30_000L;

    public static void schedule(OxWorkflow workflow) {
        if (workflow == null || workflow.id == null || workflow.id.isEmpty()) return;
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) return;
        cancelAlarm(ctx, workflow.id);
        long now = System.currentTimeMillis();
        if (!workflow.isScheduled()) {
            workflow.nextRunMs = 0;
            OxWorkflowStore.save(workflow);
            return;
        }
        if (workflow.nextRunMs <= now) {
            workflow.nextRunMs = computeNextRun(workflow, now);
        }
        OxWorkflowStore.save(workflow);
        setAlarm(ctx, workflow.id, workflow.nextRunMs);
    }

    /** Called after a run: stamps the result and arms the next occurrence. */
    public static void onFinished(OxWorkflow workflow, long finishedAt) {
        if (workflow == null) return;
        workflow.lastRunMs = finishedAt;
        workflow.nextRunMs = workflow.isScheduled() ? computeNextRun(workflow, finishedAt) : 0;
        OxWorkflowStore.save(workflow);
        schedule(workflow);
    }

    /** Next occurrence after {@code fromMs}: wall-clock minute first, else the interval. */
    private static long computeNextRun(OxWorkflow w, long fromMs) {
        if (w.isDaily()) {
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.setTimeInMillis(fromMs);
            c.set(java.util.Calendar.HOUR_OF_DAY, w.dailyMinute / 60);
            c.set(java.util.Calendar.MINUTE, w.dailyMinute % 60);
            c.set(java.util.Calendar.SECOND, 0);
            c.set(java.util.Calendar.MILLISECOND, 0);
            if (c.getTimeInMillis() <= fromMs) {
                c.add(java.util.Calendar.DAY_OF_YEAR, 1);
            }
            return c.getTimeInMillis();
        }
        return fromMs + Math.max(1, w.intervalMinutes) * 60_000L;
    }

    public static void cancel(String id) {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null || id == null) return;
        cancelAlarm(ctx, id);
    }

    /** Re-arms everything after boot or process start; overdue jobs run shortly. */
    public static void rescheduleAll(Context context) {
        if (context == null) return;
        long now = System.currentTimeMillis();
        for (OxWorkflow w : OxWorkflowStore.list()) {
            if (!w.isScheduled()) continue;
            if (w.nextRunMs <= now) {
                w.nextRunMs = now + CATCH_UP_DELAY_MS;
                OxWorkflowStore.save(w);
            }
            try {
                setAlarm(context, w.id, w.nextRunMs);
            } catch (Throwable t) {
                FileLog.e(TAG + " reschedule failed id=" + w.id, t);
            }
        }
    }

    public static boolean isExactAllowed(Context context) {
        if (Build.VERSION.SDK_INT < 31) return true;
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        } catch (Throwable t) {
            return false;
        }
    }

    private static void setAlarm(Context context, String id, long atMs) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pendingIntent(context, id);
        try {
            if (isExactAllowed(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi);
            }
        } catch (Throwable t) {
            FileLog.e(TAG + " setAlarm failed id=" + id, t);
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void cancelAlarm(Context context, String id) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(pendingIntent(context, id));
        } catch (Throwable t) {
            FileLog.e(TAG + " cancel failed id=" + id, t);
        }
    }

    private static PendingIntent pendingIntent(Context context, String id) {
        Intent i = new Intent(context, OxWorkflowReceiver.class);
        i.setAction(ACTION_RUN);
        i.putExtra(EXTRA_ID, id);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, id.hashCode(), i, flags);
    }
}
