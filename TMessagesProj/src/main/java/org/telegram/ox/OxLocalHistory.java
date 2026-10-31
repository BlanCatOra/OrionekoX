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
    /** loadIndex currently in a finishing pass, per dialog (review D4 full key pending). */
    private static final HashMap<Long, Integer> PENDING_REREAD = new HashMap<>();

    private OxLocalHistory() {}

    /** One bounded re-read per (dialog, loadIndex): new pages may re-read, the same one may not loop. */
    public static synchronized boolean shouldReread(long dialogId, int loadIndex) {
        Integer idx = PENDING_REREAD.get(dialogId);
        return idx == null || idx != loadIndex;
    }

    public static synchronized void clearReread(long dialogId, int loadIndex) {
        Integer idx = PENDING_REREAD.get(dialogId);
        if (idx != null && idx == loadIndex) {
            PENDING_REREAD.remove(dialogId);
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
        synchronized (OxLocalHistory.class) {
            PENDING_REREAD.put(dialogId, loadIndex);
        }
        MessagesStorage storage = MessagesStorage.getInstance(0);
        storage.getStorageQueue().postRunnable(() -> {
            Runnable processRunnable;
            try {
                // Same queue as putMessages: everything written before this point is
                // committed before this query runs (contract §4.2.2 write barrier).
                processRunnable = storage.getMessagesInternal(dialogId, mergeDialogId, count, maxId, offsetDate,
                        minDate, classGuid, loadType, mode, threadMessageId, loadIndex, processMessages, isTopic, null);
            } catch (Throwable t) {
                FileLog.e("OxLocalHistory native query failed did=" + dialogId, t);
                clearReread(dialogId, loadIndex);
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
                } finally {
                    clearReread(dialogId, loadIndex);
                }
            });
        });
    }
}
