package org.telegram.ox;

import android.util.SparseIntArray;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.HashMap;

/**
 * Bounded local history finishing for Ox local targets (G1 fix contract §4.2/4.3):
 * after an empty cache read, re-read the same loadIndex once from messages_v2 behind
 * the storage-queue write barrier and always deliver a terminal
 * NotificationCenter.messagesDidLoad — isEnd=true even when the dialog has no rows, so
 * a freshly created local group never ends up waiting on a Telegram server response.
 */
public final class OxLocalHistory {
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

    public static void reloadLocalAfterWriteBarrier(MessagesController controller, long dialogId, int count, int maxId, int classGuid, int loadType, int loadIndex) {
        synchronized (OxLocalHistory.class) {
            PENDING_REREAD.put(dialogId, loadIndex);
        }
        MessagesStorage storage = MessagesStorage.getInstance(0);
        storage.getStorageQueue().postRunnable(() -> {
            TLRPC.TL_messages_messages res = new TLRPC.TL_messages_messages();
            int topMid = 0;
            SQLiteCursor cursor = null;
            try {
                String query = "SELECT data, send_state, mid, date FROM messages_v2 WHERE uid = " + dialogId
                        + (maxId != 0 ? " AND mid < " + maxId : "")
                        + " ORDER BY date DESC, mid DESC LIMIT " + count;
                cursor = storage.getDatabase().queryFinalized(query);
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                        if (message != null) {
                            message.send_state = cursor.intValue(1);
                            message.id = cursor.intValue(2);
                            message.date = cursor.intValue(3);
                            message.dialog_id = dialogId;
                            if (message.id > 0 && message.send_state != 0 && message.send_state != 3) {
                                message.send_state = 0;
                            }
                            res.messages.add(message);
                            if (message.id > topMid) {
                                topMid = message.id;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // STORAGE_ERROR by contract: report with a visible terminal, never clear
                // the local table and pretend the dialog is simply empty.
                FileLog.e("OxLocalHistory re-read failed did=" + dialogId, e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            final int finalTopMid = topMid;
            final boolean end = res.messages.size() < count;
            final int resCount = res.messages.size();
            final TLRPC.TL_messages_messages finalRes = res;
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    controller.processLoadedMessages(finalRes, resCount, dialogId, 0, count, maxId, 0, true,
                            classGuid, 0, finalTopMid, 0, 0, loadType, end, 0, 0, loadIndex, false, 0, true, false, null);
                } catch (Throwable t) {
                    FileLog.e("OxLocalHistory deliver failed did=" + dialogId, t);
                }
            });
        });
    }
}
