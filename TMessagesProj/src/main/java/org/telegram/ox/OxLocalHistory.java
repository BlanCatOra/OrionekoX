package org.telegram.ox;

import android.util.SparseIntArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.tgnet.TLRPC;

import java.util.HashMap;

/**
 * Bounded local history finishing for Ox local targets (G1 fix contract §4.2/4.3).
 *
 * Review D8: instead of a hand-written SQL projection, the finishing pass reuses
 * MessagesStorage.getMessagesInternal — the exact native query/decoder used by the
 * cache pass — so pagination direction, around/unread anchors, holes, media and
 * message metadata survive verbatim. The call must be dispatched from the
 * storageQueue so it runs behind any previously queued write (write barrier).
 *
 * Review D5 (partial): the in-flight marker below is the once-per-request guard;
 * the terminal empty end itself stays in MessagesController and only fires for the
 * initial page after the bounded re-read. Structured STORAGE_ERROR propagation from
 * getMessagesInternal remains a registered gap (D3) — the native catch swallows SQL
 * errors into an empty result.
 */
public final class OxLocalHistory {
    /**
     * loadIndex values that already had their bounded re-read, per dialog.
     *
     * 2026-10-31 fix: the previous guard stored the *in-flight* loadIndex and
     * cleared it in a finally block. Because the retry loop increments loadIndex
     * on every attempt (1, 2, 3, ...), the guard never matched and the re-read
     * looped forever (observed: index 1..23+ with size 0). We now remember which
     * (dialog, loadIndex) pairs have already been served, so each one gets exactly
     * one re-read and a new page still gets its own.
     */
    private static final java.util.HashSet<String> REREAD_DONE = new java.util.HashSet<>();
    /** Hard ceiling per dialog, so a pathological caller cannot grow the set without bound. */
    private static final int MAX_REREADS_PER_DIALOG = 64;
    private static final HashMap<Long, Integer> REREAD_COUNT = new HashMap<>();

    private OxLocalHistory() {}

    /**
     * True when this (dialog, loadIndex) has not been re-read yet. Consumes the slot:
     * the caller must not call this twice for the same pair without expecting false.
     */
    public static synchronized boolean shouldReread(long dialogId, int loadIndex) {
        Integer used = REREAD_COUNT.get(dialogId);
        if (used != null && used >= MAX_REREADS_PER_DIALOG) {
            return false;
        }
        if (!REREAD_DONE.add(dialogId + ":" + loadIndex)) {
            return false;
        }
        REREAD_COUNT.put(dialogId, used == null ? 1 : used + 1);
        return true;
    }

    /** Kept for call-site compatibility; the done-set is not cleared on delivery. */
    public static synchronized void clearReread(long dialogId, int loadIndex) {
        // No-op: REREAD_DONE is the record of what was already served. Clearing it
        // here is what caused the infinite loop before 2026-10-31.
    }

    /** Drops the per-dialog bookkeeping when a chat is closed or its history is reset. */
    public static synchronized void forget(long dialogId) {
        REREAD_COUNT.remove(dialogId);
        // minSdk 23: Collection.removeIf needs API 24, so iterate explicitly.
        final String prefix = dialogId + ":";
        java.util.Iterator<String> it = REREAD_DONE.iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(prefix)) {
                it.remove();
            }
        }
    }

    /**
     * Drops the synthetic "everything below the first message is missing" hole that
     * getMessagesInternal writes for a dialog it has never fully synced. A local
     * group has no server side, so every hole is synthetic and would otherwise hide
     * real rows from the paginated query.
     */
    public static void clearSyntheticHoles(long dialogId) {
        try {
            MessagesStorage storage = MessagesStorage.getInstance(0);
            storage.getStorageQueue().postRunnable(() -> {
                try {
                    storage.getDatabase().executeFast("DELETE FROM messages_holes WHERE uid = " + dialogId)
                            .stepThis().dispose();
                } catch (Throwable t) {
                    FileLog.e("OxLocalHistory clearSyntheticHoles failed did=" + dialogId, t);
                }
            });
        } catch (Throwable t) {
            FileLog.e("OxLocalHistory clearSyntheticHoles dispatch failed did=" + dialogId, t);
        }
    }

    /**
     * Finishing pass for a local target that must not touch the network. Runs the
     * native storage query behind the storageQueue barrier and delivers through the
     * normal processLoadedMessages path with the full original request identity.
     */
    public static void finishLocalTarget(MessagesController controller, long dialogId, long mergeDialogId,
                                         int count, int maxId, int offsetDate, int minDate, int classGuid,
                                         int loadType, int mode, long threadMessageId, int loadIndex,
                                         boolean processMessages, boolean isTopic) {
        // 2026-10-31 fix (G1 CP-4): two problems stacked here.
        //
        // 1. max_id == 0 means "start from the newest" on the Telegram server, but
        //    MessagesStorage.getMessagesInternal compares it literally (m.mid <= 0)
        //    and every real message id is positive, so the initial page came back
        //    empty. Native Telegram never hits this because max_id == 0 always goes
        //    to the network; the G1 fix routes local targets to storage instead.
        //
        // 2. getMessagesInternal writes a synthetic messages_holes row (0, minMid)
        //    for any dialog it has not fully synced, then narrows later queries to
        //    (mid >= holeEnd OR mid < 0). A local group has no server side, so that
        //    hole is always synthetic and hides every real row.
        //
        // Both are handled inside the storageQueue task below, in order: drop the
        // synthetic holes, resolve the newest mid, then run the native query. Doing
        // it in one task keeps the write barrier and avoids re-entering the queue.
        MessagesStorage storage = MessagesStorage.getInstance(0);
        storage.getStorageQueue().postRunnable(() -> {
            Runnable processRunnable;
            try {
                storage.getDatabase().executeFast("DELETE FROM messages_holes WHERE uid = " + dialogId)
                        .stepThis().dispose();
            } catch (Throwable t) {
                FileLog.e("OxLocalHistory hole cleanup failed did=" + dialogId, t);
            }
            int anchor = maxId;
            if (anchor == 0) {
                org.telegram.SQLite.SQLiteCursor cursor = null;
                try {
                    cursor = storage.getDatabase().queryFinalized(String.format(java.util.Locale.US,
                            "SELECT max(mid) FROM messages_v2 WHERE uid = %d AND mid > 0", dialogId));
                    if (cursor.next()) {
                        anchor = cursor.intValue(0);
                    }
                } catch (Throwable t) {
                    FileLog.e("OxLocalHistory anchor lookup failed did=" + dialogId, t);
                } finally {
                    if (cursor != null) {
                        cursor.dispose();
                    }
                }
            }
            final int effectiveMaxId = anchor;
            try {
                // Same queue as putMessages: everything written before this point is
                // committed before this query runs (contract §4.2.2 write barrier).
                processRunnable = storage.getMessagesInternal(dialogId, mergeDialogId, count, effectiveMaxId, offsetDate,
                        minDate, classGuid, loadType, mode, threadMessageId, loadIndex, processMessages, isTopic, null);
            } catch (Throwable t) {
                FileLog.e("OxLocalHistory native query failed did=" + dialogId, t);
                AndroidUtilities.runOnUIThread(() ->
                        controller.processLoadedMessages(new TLRPC.TL_messages_messages(), 0, dialogId, mergeDialogId,
                                count, maxId, offsetDate, true, classGuid, 0, 0, 0, 0, loadType, false, mode,
                                threadMessageId, loadIndex, false, 0, false, isTopic, null));
                return;
            }
            org.telegram.messenger.Utilities.stageQueue.postRunnable(() -> {
                try {
                    processRunnable.run();
                } catch (Throwable t) {
                    FileLog.e("OxLocalHistory deliver failed did=" + dialogId, t);
                }
            });
        });
    }
}
