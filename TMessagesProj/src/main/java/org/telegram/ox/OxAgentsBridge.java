package org.telegram.ox;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges local agents into the REAL Telegram UI (architecture ported from the private
 * Orioneko repo): a fabricated local account makes the whole dialogs/chat stack work
 * offline, every OxAgent is materialized as a synthetic contact with its own dialog row,
 * and sends to an agent dialog are intercepted in SendMessagesHelper and answered by the
 * OxModelHub tool-calling loop, streamed into the chat as normal incoming messages.
 */
public class OxAgentsBridge {

    public static final long SELF_ID = 777000001L;
    public static final long AGENT_BASE = 555000000L;
    private static final long AGENT_RANGE = 100000000L;
    private static final int MAX_TOOL_ROUNDS = 6;

    private static final Object SYNC_LOCK = new Object();
    private static volatile boolean accountReady;

    private static void log(String m) {
        android.util.Log.i("OxBridge", m);
    }

    static {
        try {
            if (OxMode.isAgentOnly()) {
                org.telegram.messenger.BuildVars.LOGS_ENABLED = true;
            }
        } catch (Throwable ignore) {
        }
    }

    private static void logErr(String m, Throwable t) {
        android.util.Log.e("OxBridge", m, t);
    }

    public static boolean isAgentDialog(long dialogId) {
        return dialogId >= AGENT_BASE && dialogId < AGENT_BASE + AGENT_RANGE;
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("orioneko_config", android.content.Context.MODE_PRIVATE);
    }

    // ---------------- local account ----------------

    /** Idempotent: activates the offline local account and mirrors every agent as a contact. */
    public static void ensureLocalAccount() {
        synchronized (SYNC_LOCK) {
            if (accountReady) {
                syncAgents(false);
                return;
            }
            accountReady = true;
            log("ensureLocalAccount begin, agentOnly=" + OxMode.isAgentOnly());
            try {
                UserConfig uc = UserConfig.getInstance(0);
                if (!uc.isClientActivated()) {
                    TLRPC.TL_user me = new TLRPC.TL_user();
                    me.flags = 2 | 16 | 1024;
                    me.id = SELF_ID;
                    me.first_name = "Me";
                    me.last_name = "";
                    me.username = "me_" + (Math.abs(SELF_ID) % 100000);
                    me.phone = "+777000001";
                    me.status = onlineStatus();
                    me.self = true;
                    uc.setCurrentUser(me);
                    uc.saveConfig(true);
                    ConnectionsManager.getInstance(0).setUserId(me.id);
                    ArrayList<TLRPC.User> users = new ArrayList<>();
                    users.add(me);
                    MessagesStorage.getInstance(0).putUsersAndChats(users, null, true, true);
                    MessagesController.getInstance(0).putUser(me, false);
                    log("local account activated, id=" + me.id);
                } else {
                    log("account already activated id=" + uc.getClientUserId());
                }
            } catch (Throwable t) {
                logErr("ensureLocalAccount failed", t);
            }
            syncAgents(true);
        }
    }

    private static TLRPC.TL_userStatusOnline onlineStatus() {
        TLRPC.TL_userStatusOnline s = new TLRPC.TL_userStatusOnline();
        // always-online: Integer.MAX_VALUE; currentTime+delta overflows int and renders as an ancient date
        s.expires = Integer.MAX_VALUE;
        return s;
    }

    // ---------------- agents -> synthetic contacts ----------------

    public static void syncAgents(boolean greetNew) {
        try {
            List<OxAgent> agents = OxAgentStore.listAgents();
            log("syncAgents: " + agents.size() + " agents");
            ArrayList<TLRPC.User> users = new ArrayList<>();
            for (OxAgent a : agents) {
                if (a.tgId == 0) {
                    int seq = prefs().getInt("next_agent_seq", 2);
                    prefs().edit().putInt("next_agent_seq", seq + 1).apply();
                    a.tgId = AGENT_BASE + seq;
                    OxAgentStore.saveAgent(a);
                    log("assigned tgId " + a.tgId + " to agent " + a.id);
                }
                users.add(buildAgentUser(a));
            }
            if (users.isEmpty()) return;
            MessagesStorage.getInstance(0).putUsersAndChats(users, null, true, true);
            for (TLRPC.User u : users) {
                // fromCache=false: replace the in-memory dict entry, otherwise an
                // existing (photo-less) user object is kept and avatars never render
                MessagesController.getInstance(0).putUser(u, false);
            }
            for (OxAgent a : agents) {
                ensureDialogRow(a.tgId);
                refreshDialogRow(a.tgId);
                greetIfEmpty(a);
            }
            AndroidUtilities.runOnUIThread(() ->
                    NotificationCenter.getInstance(0).postNotificationName(NotificationCenter.dialogsNeedReload));
        } catch (Throwable t) {
            logErr("syncAgents failed", t);
        }
    }

    /** Greets on first materialization: injects a welcome message when the dialog has none
     *  (a dialog row without a last message does not show in the chat list). */
    private static void greetIfEmpty(OxAgent a) {
        MessagesStorage.getInstance(0).getStorageQueue().postRunnable(() -> {
            boolean empty;
            try {
                org.telegram.SQLite.SQLiteCursor cur = MessagesStorage.getInstance(0).getDatabase()
                        .queryFinalized("SELECT COUNT(mid) FROM messages_v2 WHERE uid = " + a.tgId);
                empty = !cur.next() || cur.longValue(0) == 0;
                cur.dispose();
            } catch (Throwable t) {
                logErr("greetIfEmpty query failed did=" + a.tgId, t);
                return;
            }
            if (empty) {
                log("greeting empty dialog " + a.tgId);
                injectAgentReply(a.tgId, "你好，我是 " + a.emoji + " " + a.name + "。直接发消息即可开始对话。");
            }
        });
    }

    private static TLRPC.TL_user buildAgentUser(OxAgent a) {
        TLRPC.TL_user u = new TLRPC.TL_user();
        // flags must cover every field set or the next storage round-trip drops them
        u.flags = 2 | 4 | 8 | 16 | 64 | 2048 | 16384;
        u.id = a.tgId;
        u.first_name = (a.emoji == null || a.emoji.isEmpty() ? "" : a.emoji + " ") + a.name;
        u.last_name = a.remark == null ? "" : a.remark;
        u.username = "agent_" + (Math.abs(a.tgId) % 100000);
        u.phone = "+779" + String.valueOf(10000000 + (int) (Math.abs(a.tgId) % 10000000));
        u.contact = true;
        u.status = onlineStatus();
        attachAvatar(u, a);
        return u;
    }

    /** Materializes the agent's custom avatar as a real UserProfilePhoto backed by
     *  files in the FileLoader cache, so every stock avatar view picks it up. */
    private static void attachAvatar(TLRPC.TL_user u, OxAgent a) {
        try {
            if (a.avatarPath == null || a.avatarPath.isEmpty()) return;
            java.io.File src = new java.io.File(a.avatarPath);
            if (!src.exists()) return;
            android.graphics.Bitmap full = android.graphics.BitmapFactory.decodeFile(src.getAbsolutePath());
            if (full == null) return;

            TLRPC.TL_userProfilePhoto photo = new TLRPC.TL_userProfilePhoto();
            photo.photo_id = Math.abs(a.tgId) * 31 + 7;
            photo.dc_id = 1;
            photo.photo_small = new TLRPC.TL_fileLocationToBeDeprecated();
            photo.photo_small.dc_id = 1;
            photo.photo_small.volume_id = Math.abs(a.tgId) + 9000001;
            photo.photo_small.local_id = 11;
            photo.photo_big = new TLRPC.TL_fileLocationToBeDeprecated();
            photo.photo_big.dc_id = 1;
            photo.photo_big.volume_id = Math.abs(a.tgId) + 9000001;
            photo.photo_big.local_id = 12;

            org.telegram.messenger.FileLoader fl = org.telegram.messenger.FileLoader.getInstance(0);
            // the reader may resolve any of the overload variants - cover them all
            java.util.LinkedHashSet<java.io.File> smallPaths = new java.util.LinkedHashSet<>();
            java.util.LinkedHashSet<java.io.File> bigPaths = new java.util.LinkedHashSet<>();
            for (java.io.File f : new java.io.File[]{fl.getPathToAttach(photo.photo_small), fl.getPathToAttach(photo.photo_small, true), fl.getPathToAttach(photo.photo_small, "jpg", true), fl.getPathToAttach(photo.photo_small, "jpg", false)}) {
                if (f != null && f.getParentFile() != null) smallPaths.add(f);
            }
            for (java.io.File f : new java.io.File[]{fl.getPathToAttach(photo.photo_big), fl.getPathToAttach(photo.photo_big, true), fl.getPathToAttach(photo.photo_big, "jpg", true), fl.getPathToAttach(photo.photo_big, "jpg", false)}) {
                if (f != null && f.getParentFile() != null) bigPaths.add(f);
            }
            android.graphics.Bitmap smallBmp = circleCrop(full, 240);
            android.graphics.Bitmap bigBmp = circleCrop(full, 800);
            for (java.io.File f : smallPaths) {
                f.getParentFile().mkdirs();
                java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                smallBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out);
                out.close();
            }
            for (java.io.File f : bigPaths) {
                f.getParentFile().mkdirs();
                java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                bigBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out);
                out.close();
            }
            smallBmp.recycle();
            bigBmp.recycle();
            log("attachAvatar wrote " + smallPaths.size() + " small / " + bigPaths.size() + " big variants: " + smallPaths);
            // instant-render avatar: ImageReceiver draws photo.strippedBitmap directly,
            // no file lookup involved (the cache files are a best-effort extra)
            android.graphics.Bitmap thumb = circleCrop(full, 120);
            photo.strippedBitmap = new android.graphics.drawable.BitmapDrawable(
                    ApplicationLoader.applicationContext.getResources(), thumb);
            u.photo = photo;
            u.flags |= 32;
            log("attachAvatar attached for agent " + a.id + " tg=" + a.tgId + " flags=" + u.flags);
            full.recycle();
        } catch (Throwable t) {
            logErr("attachAvatar failed for " + a.id, t);
        }
    }

    private static android.graphics.Bitmap circleCrop(android.graphics.Bitmap src, int side) {
        try {
            android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(side, side, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(out);
            android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
            int size = Math.min(src.getWidth(), src.getHeight());
            android.graphics.BitmapShader shader = new android.graphics.BitmapShader(src, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
            float scale = side / (float) size;
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.setScale(scale, scale);
            m.postTranslate(-(src.getWidth() - size) / 2f * scale, -(src.getHeight() - size) / 2f * scale);
            shader.setLocalMatrix(m);
            paint.setShader(shader);
            canvas.drawCircle(side / 2f, side / 2f, side / 2f, paint);
            return out;
        } catch (Throwable t) {
            return src;
        }
    }

    public static OxAgent agentByTgId(long tgId) {
        for (OxAgent a : OxAgentStore.listAgents()) {
            if (a.tgId == tgId) return a;
        }
        return null;
    }

    /** Inserts a dialog row for the agent so the chat list has something to show. */
    private static void ensureDialogRow(long dialogId) {
        MessagesStorage.getInstance(0).getStorageQueue().postRunnable(() -> {
            try {
                long now = System.currentTimeMillis() / 1000;
                // dialogs table: did, date, unread_count, last_mid, inbox_max, outbox_max,
                // last_mid_i, unread_count_i, pts, date_i, pinned, flags, folder_id, data,
                // unread_reactions, last_mid_group, ttl_period, unread_poll_votes (18 cols)
                MessagesStorage.getInstance(0).getDatabase().executeFast(
                        "INSERT OR REPLACE INTO dialogs VALUES(" + dialogId + ", " + now + ", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, x'', 0, 0, 0, 0)").stepThis().dispose();
                log("ensureDialogRow ok did=" + dialogId);
            } catch (Throwable t) {
                logErr("ensureDialogRow failed did=" + dialogId, t);
            }
        });
    }

    private static int nextLocalIncomingId() {
        int id = prefs().getInt("local_msg_seq", 100000) + 1;
        prefs().edit().putInt("local_msg_seq", id).apply();
        return id;
    }

    // ---------------- outgoing interception (called from SendMessagesHelper) ----------------

    public static boolean interceptOutgoing(SendMessagesHelper.SendMessageParams p) {
        if (!OxMode.isAgentOnly() || p == null || !isAgentDialog(p.peer)) {
            return false;
        }
        if (p.retryMessageObject != null || p.photo != null || p.document != null || p.location != null
                || p.poll != null || p.invoice != null || p.todo != null || p.game != null
                || p.videoEditedInfo != null || p.sendingStory != null) {
            return false;   // media to agents lands later (M4)
        }
        String text = p.message != null ? p.message : p.caption;
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        log("interceptOutgoing dialog=" + p.peer + " text=" + text);
        handleOutgoingText(p.peer, text.trim());
        return true;
    }

    // ---------------- media (photos) to agents ----------------

    /** Routes media the user sends to an agent dialog. Photos become real outgoing
     *  photo messages in the chat (sizes written locally by generatePhotoSizes) and
     *  the file is archived into the agent's media inbox for tools/vision. */
    public static void handleOutgoingMedia(long dialogId, java.util.List<SendMessagesHelper.SendingMediaInfo> infos) {
        log("handleOutgoingMedia dialog=" + dialogId + " count=" + (infos == null ? 0 : infos.size()));
        final OxAgent agent = agentByTgId(dialogId);
        StringBuilder captionAll = new StringBuilder();
        int photoCount = 0;
        String lastArchivedPath = null;
        if (infos != null) {
            for (SendMessagesHelper.SendingMediaInfo info : infos) {
                String path = info.path;
                if (path == null || path.isEmpty()) {
                    injectAgentReply(dialogId, "（该类型媒体暂不支持发送给智能体）");
                    continue;
                }
                try {
                    SendMessagesHelper smh = SendMessagesHelper.getInstance(0);
                    TLRPC.TL_photo photo = smh.generatePhotoSizes(path, null);
                    if (photo == null) {
                        injectAgentReply(dialogId, "（图片读取失败：" + path + "）");
                        continue;
                    }
                    TLRPC.TL_message msg = new TLRPC.TL_message();
                    msg.flags = 256 | 512;   // HAS_FROM_ID | HAS_MEDIA
                    msg.id = UserConfig.getInstance(0).getNewMessageId();
                    msg.local_id = msg.id;
                    msg.out = true;
                    msg.date = (int) (System.currentTimeMillis() / 1000);
                    msg.media = new TLRPC.TL_messageMediaPhoto();
                    msg.media.flags |= 1;   // photo
                    msg.media.photo = photo;
                    msg.media.ttl_seconds = 0;
                    msg.message = "";
                    msg.peer_id = new TLRPC.TL_peerUser();
                    msg.peer_id.user_id = dialogId;
                    msg.from_id = new TLRPC.TL_peerUser();
                    msg.from_id.user_id = SELF_ID;
                    msg.dialog_id = dialogId;
                    msg.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                    persistAndShow(dialogId, msg);
                    photoCount++;

                    // archive into the agent's media inbox
                    String archivedPath = null;
                    if (agent != null) {
                        File inbox = new File(ApplicationLoader.applicationContext.getFilesDir(),
                                "oxagents/" + agent.id + "/media");
                        inbox.mkdirs();
                        File dst = new File(inbox, System.currentTimeMillis() + "_" + photoCount + ".jpg");
                        try {
                            java.io.FileInputStream in = new java.io.FileInputStream(path);
                            java.io.FileOutputStream out = new java.io.FileOutputStream(dst);
                            byte[] buf = new byte[16384];
                            int n;
                            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                            out.close(); in.close();
                            archivedPath = dst.getAbsolutePath();
                            log("media archived: " + archivedPath);
                        } catch (Throwable t) {
                            logErr("media archive failed", t);
                        }
                    }
                    int[] wh = photoSizeOf(photo);
                    captionAll.append(" [图片 ").append(wh[0]).append("x").append(wh[1]).append("]");
                    lastArchivedPath = archivedPath;
                } catch (Throwable t) {
                    logErr("handleOutgoingMedia item failed", t);
                }
            }
        }
        if (agent != null) {
            List<OxChatEntry> chat = OxAgentStore.loadChat(agent.id);
            chat.add(new OxChatEntry("user", (captionAll.toString().trim() + " 已发送").trim(),
                    System.currentTimeMillis(), lastArchivedPath));
            OxAgentStore.saveChat(agent.id, chat);
            injectAgentReply(dialogId, "📷 收到 " + photoCount + " 张图片" +
                    (agent.isEchoMode() ? "。（接入模型后可对图片做视觉理解）" : "，已存入媒体收件箱。"));
        }
    }

    private static int[] photoSizeOf(TLRPC.TL_photo photo) {
        int w = 0, h = 0;
        if (photo.sizes != null) {
            for (TLRPC.PhotoSize ps : photo.sizes) {
                if (ps.w > w) { w = ps.w; h = ps.h; }
            }
        }
        return new int[]{w, h};
    }

    private static void handleOutgoingText(long dialogId, String text) {
        try {
            TLRPC.TL_message msg = new TLRPC.TL_message();
            msg.flags = 256;
            msg.id = UserConfig.getInstance(0).getNewMessageId();
            msg.local_id = msg.id;
            msg.out = true;
            msg.date = (int) (System.currentTimeMillis() / 1000);
            msg.message = text;
            msg.peer_id = new TLRPC.TL_peerUser();
            msg.peer_id.user_id = dialogId;
            msg.from_id = new TLRPC.TL_peerUser();
            msg.from_id.user_id = SELF_ID;
            msg.dialog_id = dialogId;
            msg.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
            msg.media = new TLRPC.TL_messageMediaEmpty();
            persistAndShow(dialogId, msg);
        } catch (Throwable t) {
            logErr("handleOutgoingText persist failed", t);
        }

        final OxAgent agent = agentByTgId(dialogId);
        if (agent == null) {
            injectAgentReply(dialogId, "（该智能体已被删除）");
            return;
        }
        if (agent.isEchoMode()) {
            StringBuilder sb = new StringBuilder();
            sb.append("⚡ 离线回声模式（未配置模型后端）\n\n你说：").append(text).append('\n');
            sb.append("\n👉 打开 智能体 管理 → 长按 → 编辑设置，填写 Base URL / API Key / 模型后即可接入真实模型与工具调用。");
            injectAgentReply(dialogId, sb.toString());
            List<OxChatEntry> chat = OxAgentStore.loadChat(agent.id);
            chat.add(new OxChatEntry("user", text, System.currentTimeMillis()));
            chat.add(new OxChatEntry("assistant", sb.toString(), System.currentTimeMillis()));
            OxAgentStore.saveChat(agent.id, chat);
            return;
        }
        startTurn(agent, dialogId, text);
    }

    // ---------------- reply pipeline (tool loop + streaming bubble) ----------------

    private static class Stream {
        long dialogId;
        OxAgent agent;
        JSONArray messages;
        int depth;
        String userText;
        TLRPC.TL_message msg;
        final StringBuilder text = new StringBuilder();
        final List<OxChatEntry> acc = new ArrayList<>();
    }

    private static final Map<Long, Stream> active = new ConcurrentHashMap<>();

    // ---------------- typing indicator (local PrintingUser injection) ----------------

    private static final android.os.Handler typingHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final long TYPING_REFRESH_MS = 2000;   // native expiry is 5.9s - refresh well inside it
    private static final Runnable typingTicker = new Runnable() {
        @Override
        public void run() {
            try {
                MessagesController mc = MessagesController.getInstance(0);
                int now = ConnectionsManager.getInstance(0).getCurrentTime();
                boolean any = !active.isEmpty();
                for (Long dialogId : active.keySet()) {
                    setPrintingUser(dialogId, now);
                }
                mc.updatePrintingStrings();
                notifyPrintChanged();
                if (any) {
                    typingHandler.postDelayed(this, TYPING_REFRESH_MS);
                }
            } catch (Throwable t) {
                logErr("typingTicker failed", t);
            }
        }
    };

    private static void setPrintingUser(long dialogId, int now) {
        try {
            MessagesController mc = MessagesController.getInstance(0);
            java.util.concurrent.ConcurrentHashMap<Integer, java.util.ArrayList<MessagesController.PrintingUser>> threads = mc.printingUsers.get(dialogId);
            if (threads == null) {
                threads = new java.util.concurrent.ConcurrentHashMap<>();
                mc.printingUsers.put(dialogId, threads);
            }
            java.util.ArrayList<MessagesController.PrintingUser> arr = threads.get(0);
            if (arr == null) {
                arr = new java.util.ArrayList<>();
                threads.put(0, arr);
            }
            arr.clear();
            MessagesController.PrintingUser pu = new MessagesController.PrintingUser();
            pu.userId = dialogId;
            pu.lastTime = now;
            pu.action = new TLRPC.TL_sendMessageTypingAction();
            arr.add(pu);
        } catch (Throwable t) {
            logErr("setPrintingUser failed", t);
        }
    }

    private static void notifyPrintChanged() {
        // updatePrintingStrings applies its swap via a posted runnable - queue this
        // notification behind it so consumers read the fresh strings, not the old map
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(0)
                .postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_USER_PRINT));
    }

    private static void startTyping(long dialogId) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                setPrintingUser(dialogId, ConnectionsManager.getInstance(0).getCurrentTime());
                MessagesController.getInstance(0).updatePrintingStrings();
                notifyPrintChanged();
                typingHandler.removeCallbacks(typingTicker);
                typingHandler.postDelayed(typingTicker, TYPING_REFRESH_MS);
            } catch (Throwable t) {
                logErr("startTyping failed", t);
            }
        });
    }

    private static void stopTyping(long dialogId) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                MessagesController mc = MessagesController.getInstance(0);
                if (mc.printingUsers.remove(dialogId) != null) {
                    mc.updatePrintingStrings();
                    notifyPrintChanged();
                }
            } catch (Throwable t) {
                logErr("stopTyping failed", t);
            }
        });
    }

    private static void startTurn(OxAgent agent, long dialogId, String userText) {
        log("startTurn agent=" + agent.id + " dialog=" + dialogId);
        Stream s = new Stream();
        s.dialogId = dialogId;
        s.agent = agent;
        s.userText = userText;
        try {
            s.messages = baseMessages(agent, agent.id);
            s.messages.put(new JSONObject().put("role", "user").put("content", userText));
        } catch (Exception e) {
            injectAgentReply(dialogId, "⚠ " + e.getMessage());
            return;
        }
        active.put(dialogId, s);
        startTyping(dialogId);
        runRound(s, 0);
    }

    private static JSONArray baseMessages(OxAgent agent, String agentId) throws Exception {
        JSONArray messages = new JSONArray();
        if (agent.agentsMd != null && !agent.agentsMd.trim().isEmpty()) {
            messages.put(new JSONObject().put("role", "system").put("content", agent.agentsMd));
        }
        List<OxChatEntry> history = OxAgentStore.loadChat(agentId);
        int from = Math.max(0, history.size() - 20);
        for (int i = from; i < history.size(); i++) {
            OxChatEntry e = history.get(i);
            if ("user".equals(e.role) || "assistant".equals(e.role)) {
                if ("user".equals(e.role) && e.imagePath != null && new File(e.imagePath).exists()) {
                    // multimodal user turn: text + inline base64 image
                    JSONObject content = new JSONObject();
                    content.put("type", "text");
                    content.put("text", e.content == null || e.content.isEmpty() ? "（看这张图片）" : e.content);
                    JSONArray parts = new JSONArray().put(content);
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    try (java.io.FileInputStream in = new java.io.FileInputStream(e.imagePath)) {
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                    }
                    String b64 = android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP);
                    parts.put(new JSONObject().put("type", "image_url")
                            .put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + b64)));
                    messages.put(new JSONObject().put("role", "user").put("content", parts));
                } else {
                    messages.put(new JSONObject().put("role", e.role).put("content", e.content));
                }
            }
        }
        return messages;
    }

    private static void runRound(final Stream s, final int depth) {
        s.depth = depth;
        log("runRound dialog=" + s.dialogId + " depth=" + depth);
        if (depth >= MAX_TOOL_ROUNDS) {
            finishTurn(s, "⚠ 工具调用轮数达上限（" + MAX_TOOL_ROUNDS + "）");
            return;
        }
        OxModelHub.completeRound(s.agent, s.messages, true, new OxModelHub.RoundCallback() {
            @Override
            public void onDelta(String piece) {
                streamAppend(s, piece);
            }

            @Override
            public void onRound(OxModelHub.Round round) {
                log("onRound dialog=" + s.dialogId + " tools=" + round.toolCalls.size() + " content=" + round.content.length());
                if (!round.hasToolCalls()) {
                    String t = round.content == null ? "" : round.content.trim();
                    finishTurn(s, t.isEmpty() ? s.text.toString() : t);
                    return;
                }
                recordAssistantToolCalls(s, round);
                executeToolAt(s, round.toolCalls, 0);
            }

            @Override
            public void onError(String message) {
                log("onError dialog=" + s.dialogId + ": " + message);
                finishTurn(s, "⚠ " + message);
            }
        });
    }

    private static void recordAssistantToolCalls(Stream s, OxModelHub.Round round) {
        try {
            JSONArray calls = new JSONArray();
            for (OxModelHub.ToolCall c : round.toolCalls) {
                JSONObject fn = new JSONObject();
                fn.put("name", c.name);
                fn.put("arguments", c.arguments.isEmpty() ? "{}" : new JSONObject(c.arguments));
                calls.put(new JSONObject().put("id", c.id).put("type", "function").put("function", fn));
            }
            JSONObject assistantMsg = new JSONObject();
            assistantMsg.put("role", "assistant");
            if (round.content != null && !round.content.isEmpty()) assistantMsg.put("content", round.content);
            assistantMsg.put("tool_calls", calls);
            s.messages.put(assistantMsg);
        } catch (Exception ignored) {
        }
    }

    private static void executeToolAt(final Stream s, final List<OxModelHub.ToolCall> calls, final int i) {
        if (i >= calls.size()) {
            runRound(s, s.depth + 1);
            return;
        }
        final OxModelHub.ToolCall call = calls.get(i);
        final OxTool tool = OxMcpManager.lookup(call.name);
        log("executeTool dialog=" + s.dialogId + " tool=" + (call == null ? "?" : call.name) + " found=" + (tool != null));
        if (tool == null) {
            toolResult(s, calls, i, call, "error: unknown tool '" + call.name + "'");
            return;
        }
        String policy = s.agent.toolPolicy(tool);
        if (OxTool.POLICY_DENY.equals(policy)) {
            toolResult(s, calls, i, call, "denied: tool policy is 'deny' for this agent");
            return;
        }
        final String argsPretty = call.arguments == null || call.arguments.trim().isEmpty() ? "{}" : call.arguments;
        Runnable run = () -> {
            injectAgentReply(s.dialogId, "🔧 " + call.name + "(" + argsPretty + ") …");
            new Thread(() -> {
                String result;
                try {
                    JSONObject args = call.arguments == null || call.arguments.trim().isEmpty()
                            ? new JSONObject() : new JSONObject(call.arguments);
                    result = tool.execute(args);
                } catch (Exception e) {
                    result = "error: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                }
                log("tool " + call.name + " done: " + result);
                final String r = result;
                AndroidUtilities.runOnUIThread(() -> toolResult(s, calls, i, call, r));
            }, "ox-bridge-tool").start();
        };
        if (OxTool.POLICY_ASK.equals(policy)) {
            confirmTool(s.dialogId, call, granted -> {
                log("permission for " + call.name + ": " + granted);
                if (granted) {
                    run.run();
                } else {
                    toolResult(s, calls, i, call, "denied by user");
                }
            });
        } else {
            run.run();
        }
    }

    private interface PermissionSink { void onDecision(boolean granted); }

    private static void confirmTool(long dialogId, OxModelHub.ToolCall call, PermissionSink sink) {
        AndroidUtilities.runOnUIThread(() -> {
            android.app.Activity act = LaunchActivity.instance;
            if (act == null || act.isFinishing()) {
                sink.onDecision(false);
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(act);
            builder.setTitle(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.OxToolPermissionTitle));
            builder.setMessage(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.OxToolPermissionText) + "\n\n🔧 " + call.name + "(" + call.arguments + ")");
            builder.setPositiveButton(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.Allow), (d, w) -> sink.onDecision(true));
            builder.setNegativeButton(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.Cancel), (d, w) -> sink.onDecision(false));
            builder.show();
        });
    }

    private static void toolResult(Stream s, List<OxModelHub.ToolCall> calls, int i, OxModelHub.ToolCall call, String result) {
        try {
            JSONObject m = new JSONObject();
            m.put("role", "tool");
            m.put("tool_call_id", call.id);
            m.put("content", result);
            s.messages.put(m);
        } catch (Exception ignored) {
        }
        s.acc.add(new OxChatEntry("tool", "🔧 " + call.name + " → " + result, System.currentTimeMillis()));
        executeToolAt(s, calls, i + 1);
    }

    // ---------------- message injection ----------------

    /**
     * Persists the message and surfaces it in the UI. MessageObject construction happens
     * on the main thread (it is not thread-safe off it); storage write goes through the
     * storage queue from any thread.
     */
    private static void persistAndShow(long dialogId, TLRPC.TL_message msg) {
        final boolean out = msg.out;
        ArrayList<TLRPC.Message> arr = new ArrayList<>();
        arr.add(msg);
        try {
            MessagesStorage.getInstance(0).putMessages(arr, false, true, false, 0, 0, 0);
        } catch (Throwable t) {
            logErr("putMessages failed did=" + dialogId, t);
        }
        refreshDialogRow(dialogId);
        AndroidUtilities.runOnUIThread(() -> {
            try {
                MessageObject obj = new MessageObject(0, msg, true, false);
                if (out) {
                    obj.wasJustSent = true;
                }
                if (msg.media != null && !(msg.media instanceof TLRPC.TL_messageMediaEmpty)) {
                    log("persistAndShow media type=" + obj.type + " mediaClass=" + msg.media.getClass().getSimpleName()
                            + " photoNonNull=" + (msg.media.photo != null)
                            + " sizes=" + (msg.media.photo != null && msg.media.photo.sizes != null ? msg.media.photo.sizes.size() : -1));
                }
                ArrayList<MessageObject> objArr = new ArrayList<>();
                objArr.add(obj);
                MessagesController.getInstance(0).updateInterfaceWithMessages(dialogId, objArr, 0);
                NotificationCenter.getInstance(0).postNotificationName(NotificationCenter.dialogsNeedReload);
                log("persistAndShow shown did=" + dialogId + " id=" + msg.id + " out=" + out);
            } catch (Throwable t) {
                logErr("persistAndShow UI failed did=" + dialogId, t);
            }
        });
    }

    /** Recomputes the dialogs row (date/last_mid) from messages_v2 so the chat list shows it. */
    private static void refreshDialogRow(long dialogId) {
        MessagesStorage.getInstance(0).getStorageQueue().postRunnable(() -> {
            try {
                MessagesStorage.getInstance(0).getDatabase().executeFast(
                        "INSERT OR REPLACE INTO dialogs(did, date, unread_count, last_mid, inbox_max, outbox_max, last_mid_i, unread_count_i, pts, date_i, pinned, flags, folder_id, data, unread_reactions, last_mid_group, ttl_period, unread_poll_votes) "
                                + "SELECT uid, MAX(date), 0, mid, 0, 0, 0, 0, 0, 0, 0, 0, 0, x'', 0, 0, 0, 0 FROM messages_v2 WHERE uid = " + dialogId).stepThis().dispose();
            } catch (Throwable t) {
                logErr("refreshDialogRow failed did=" + dialogId, t);
            }
        });
    }

    public static void injectAgentReply(long dialogId, String text) {
        try {
            TLRPC.TL_message msg = new TLRPC.TL_message();
            msg.flags = 256;
            msg.id = nextLocalIncomingId();
            msg.local_id = msg.id;
            msg.out = false;
            msg.date = (int) (System.currentTimeMillis() / 1000);
            msg.message = text;
            msg.peer_id = new TLRPC.TL_peerUser();
            msg.peer_id.user_id = dialogId;
            msg.from_id = new TLRPC.TL_peerUser();
            msg.from_id.user_id = dialogId;
            msg.dialog_id = dialogId;
            msg.unread = false;
            msg.media = new TLRPC.TL_messageMediaEmpty();
            persistAndShow(dialogId, msg);
        } catch (Throwable t) {
            logErr("injectAgentReply failed", t);
        }
    }

    // ---------------- streaming bubble ----------------

    private static void streamAppend(Stream s, String piece) {
        if (piece == null || piece.isEmpty()) return;
        s.text.append(piece);
        boolean first = s.msg == null;
        try {
            if (first) {
                s.msg = new TLRPC.TL_message();
                s.msg.flags = 256;
                s.msg.id = nextLocalIncomingId();
                s.msg.local_id = s.msg.id;
                s.msg.out = false;
                s.msg.date = (int) (System.currentTimeMillis() / 1000);
                s.msg.peer_id = new TLRPC.TL_peerUser();
                s.msg.peer_id.user_id = s.dialogId;
                s.msg.from_id = new TLRPC.TL_peerUser();
                s.msg.from_id.user_id = s.dialogId;
                s.msg.dialog_id = s.dialogId;
                s.msg.unread = false;
                s.msg.media = new TLRPC.TL_messageMediaEmpty();
            }
            s.msg.message = s.text.toString();
            ArrayList<TLRPC.Message> arr = new ArrayList<>();
            arr.add(s.msg);
            MessagesStorage.getInstance(0).putMessages(arr, false, true, false, 0, 0, 0);
        } catch (Throwable t) {
            logErr("streamAppend storage failed", t);
        }
        final ArrayList<TLRPC.TL_message> msgRef = new ArrayList<>();
        msgRef.add(s.msg);
        final boolean isFirst = first;
        AndroidUtilities.runOnUIThread(() -> {
            try {
                TLRPC.TL_message m = msgRef.get(0);
                MessageObject obj = new MessageObject(0, m, true, false);
                ArrayList<MessageObject> objArr = new ArrayList<>();
                objArr.add(obj);
                if (isFirst) {
                    MessagesController.getInstance(0).updateInterfaceWithMessages(s.dialogId, objArr, 0);
                } else {
                    NotificationCenter.getInstance(0).postNotificationName(NotificationCenter.replaceMessagesObjects, s.dialogId, objArr);
                }
            } catch (Throwable t) {
                logErr("streamAppend UI failed", t);
            }
        });
    }

    private static void finishTurn(Stream s, String finalText) {
        log("finishTurn dialog=" + s.dialogId + " len=" + (finalText == null ? 0 : finalText.length()));
        active.remove(s.dialogId);
        stopTyping(s.dialogId);
        if (finalText == null || finalText.trim().isEmpty()) {
            finalText = s.text.length() > 0 ? s.text.toString() : "（空响应）";
        }
        if (s.msg != null) {
            s.msg.message = finalText;
            ArrayList<TLRPC.Message> arr = new ArrayList<>();
            arr.add(s.msg);
            MessagesStorage.getInstance(0).putMessages(arr, false, true, false, 0, 0, 0);
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    MessageObject obj = new MessageObject(0, s.msg, true, false);
                    ArrayList<MessageObject> objArr = new ArrayList<>();
                    objArr.add(obj);
                    NotificationCenter.getInstance(0).postNotificationName(NotificationCenter.replaceMessagesObjects, s.dialogId, objArr);
                } catch (Throwable t) {
                    logErr("finishTurn UI failed", t);
                }
            });
        } else {
            injectAgentReply(s.dialogId, finalText);
        }
        // persist the turn into the shared agent history
        try {
            List<OxChatEntry> chat = OxAgentStore.loadChat(s.agent.id);
            chat.add(new OxChatEntry("user", s.userText, System.currentTimeMillis()));
            chat.addAll(s.acc);
            chat.add(new OxChatEntry("assistant", finalText, System.currentTimeMillis()));
            OxAgentStore.saveChat(s.agent.id, chat);
        } catch (Throwable t) {
            logErr("finishTurn history failed", t);
        }
    }

    /** Removes a deleted agent's synthetic dialog and messages from local storage. */
    public static void onAgentDeleted(long tgId) {
        if (tgId == 0) return;
        MessagesStorage.getInstance(0).getStorageQueue().postRunnable(() -> {
            try {
                MessagesStorage.getInstance(0).getDatabase().executeFast(
                        "DELETE FROM dialogs WHERE did = " + tgId).stepThis().dispose();
                MessagesStorage.getInstance(0).getDatabase().executeFast(
                        "DELETE FROM messages_v2 WHERE uid = " + tgId).stepThis().dispose();
            } catch (Throwable t) {
                logErr("onAgentDeleted cleanup failed", t);
            }
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    MessagesController mc = MessagesController.getInstance(0);
                    if (mc.dialogs_dict != null) {
                        mc.dialogs_dict.remove(tgId);
                    }
                    NotificationCenter.getInstance(0).postNotificationName(NotificationCenter.dialogsNeedReload);
                } catch (Throwable t) {
                    logErr("onAgentDeleted UI failed", t);
                }
            });
        });
    }

    // ---------------- exit to real login ----------------

    /** Leaves agent-only mode: local-only logout (no network call) and back to the intro. */
    public static void exitAgentOnly() {
        OxMode.setAgentOnly(false);
        try {
            MessagesController.getInstance(0).performLogout(0);
        } catch (Throwable t) {
            logErr("exitAgentOnly failed", t);
        }
    }
}
