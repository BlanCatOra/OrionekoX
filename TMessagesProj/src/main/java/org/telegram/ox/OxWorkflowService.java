package org.telegram.ox;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.core.app.ServiceCompat;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs workflow turns one at a time: injects the prompt as a user message into the
 * agent chat, waits for the reply to be persisted, then posts a result notification.
 * Runs are queued because Android keeps a single Service instance per process.
 */
public class OxWorkflowService extends Service {

    private static final String TAG = "OxWorkflowService";
    private static final long POLL_MS = 2000L;
    private static final long TIMEOUT_MS = 5 * 60 * 1000L;
    private static final Set<String> active = Collections.synchronizedSet(new HashSet<>());

    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private Handler handler;
    private PowerManager.WakeLock wakeLock;
    private boolean processing;

    private String currentId;
    private OxWorkflow current;
    private long startedAt;
    private long deadline;

    private final Runnable poller = new Runnable() {
        @Override
        public void run() {
            OxWorkflow w = current;
            if (w == null) {
                finishRun(LocaleController.getString(R.string.OxWorkflowAgentMissing), true, false);
                return;
            }
            try {
                String reply = findReply(w.agentId);
                if (reply != null) {
                    finishRun(reply, false, false);
                    return;
                }
            } catch (Throwable t) {
                FileLog.e(TAG + " poll failed", t);
            }
            if (System.currentTimeMillis() > deadline) {
                finishRun("", false, true);
                return;
            }
            handler.postDelayed(this, POLL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        ApplicationLoader.postInitApplication();
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String id = intent == null ? null : intent.getStringExtra(OxWorkflowScheduler.EXTRA_ID);
        if (id == null || id.isEmpty()) {
            stopIfIdle();
            return START_NOT_STICKY;
        }
        if (!active.add(id)) {
            // Already queued or running.
            stopIfIdle();
            return START_NOT_STICKY;
        }
        synchronized (queue) {
            queue.add(id);
        }
        if (!processing) {
            processing = true;
            handler.post(this::next);
        }
        return START_NOT_STICKY;
    }

    private void next() {
        String id;
        synchronized (queue) {
            id = queue.poll();
        }
        if (id == null) {
            processing = false;
            closeService();
            return;
        }
        currentId = id;
        current = OxWorkflowStore.get(id);
        if (current == null) {
            active.remove(id);
            handler.post(this::next);
            return;
        }
        if (!startForegroundNotification(current)) {
            active.remove(id);
            handler.post(this::next);
            return;
        }
        acquireWakeLock();
        begin(current);
    }

    private boolean startForegroundNotification(OxWorkflow w) {
        try {
            Notification n = OxWorkflowNotifier.running(this, w);
            ServiceCompat.startForeground(this, OxWorkflowNotifier.notificationId(w.id), n,
                    Build.VERSION.SDK_INT >= 29 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC : 0);
            return true;
        } catch (Throwable t) {
            FileLog.e(TAG + " startForeground failed", t);
            postFallback(w);
            return false;
        }
    }

    private void postFallback(OxWorkflow w) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(OxWorkflowNotifier.notificationId(w.id), OxWorkflowNotifier.due(this, w));
            }
        } catch (Throwable t) {
            FileLog.e(TAG + " fallback notification failed", t);
        }
    }

    private void begin(OxWorkflow w) {
        try {
            OxAgentsBridge.ensureLocalAccount();
        } catch (Throwable t) {
            FileLog.e(TAG + " local account failed", t);
        }
        OxAgent agent = OxAgentStore.loadAgent(w.agentId);
        if (agent == null || agent.tgId == 0) {
            finishRun(LocaleController.getString(R.string.OxWorkflowAgentMissing), true, false);
            return;
        }
        startedAt = System.currentTimeMillis();
        deadline = startedAt + TIMEOUT_MS;
        final long dialogId = agent.tgId;
        OxAgentsBridge.ensureNativeDialog(agent, () -> {
            try {
                OxAgentsBridge.sendUserPrompt(dialogId, w.prompt);
            } catch (Throwable t) {
                FileLog.e(TAG + " send prompt failed", t);
                finishRun(LocaleController.getString(R.string.OxWorkflowSendFailed), true, false);
                return;
            }
            handler.postDelayed(poller, POLL_MS);
        });
    }

    /** Returns the newest assistant text produced after this run started, or null. */
    private String findReply(String agentId) {
        List<OxChatEntry> chat = OxAgentStore.loadChat(agentId);
        for (int i = chat.size() - 1; i >= 0; i--) {
            OxChatEntry e = chat.get(i);
            if (e.ts < startedAt - 5000L) break;
            if ("assistant".equals(e.role) && e.content != null && !e.content.trim().isEmpty()) {
                return e.content;
            }
        }
        return null;
    }

    private void finishRun(String text, boolean error, boolean timedOut) {
        handler.removeCallbacks(poller);
        OxWorkflow w = current;
        String snippet;
        if (error) {
            snippet = text == null ? "" : text;
        } else if (timedOut) {
            snippet = LocaleController.getString(R.string.OxWorkflowTimeout);
        } else {
            snippet = OxWorkflowNotifier.snippet(text);
        }
        if (w != null) {
            w.lastResult = snippet.length() > 300 ? snippet.substring(0, 300) : snippet;
            OxWorkflowScheduler.onFinished(w, System.currentTimeMillis());
            org.telegram.ui.OxWorkflowActivity.notifyChanged();
        }
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && w != null) {
                // Remove the ongoing notification first: the result reuses its id.
                stopForegroundCompat();
                nm.notify(OxWorkflowNotifier.notificationId(w.id),
                        OxWorkflowNotifier.result(this, w, snippet));
            }
        } catch (Throwable t) {
            FileLog.e(TAG + " result notification failed", t);
        }
        releaseWakeLock();
        if (currentId != null) active.remove(currentId);
        currentId = null;
        current = null;
        handler.post(this::next);
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm == null) return;
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OrionekoX:workflow");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(TIMEOUT_MS + 60_000L);
        } catch (Throwable t) {
            FileLog.e(TAG + " wake lock failed", t);
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Throwable ignored) {
        }
        wakeLock = null;
    }

    private void stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Throwable ignored) {
        }
    }

    private void closeService() {
        synchronized (queue) {
            queue.clear();
        }
        active.clear();
        stopForegroundCompat();
        stopSelf();
    }

    private void stopIfIdle() {
        boolean idle;
        synchronized (queue) {
            idle = queue.isEmpty();
        }
        if (idle && !processing) stopSelf();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(poller);
        releaseWakeLock();
        // Queued ids would otherwise block future runs in the same process.
        active.clear();
        synchronized (queue) {
            queue.clear();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
