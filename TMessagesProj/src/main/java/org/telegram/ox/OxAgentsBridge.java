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
        s.expires = ConnectionsManager.getInstance(0).getCurrentTime() + 1000000000;
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
                MessagesController.getInstance(0).putUser(u, true);
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
        u.last_name = "";
        u.username = "agent_" + (Math.abs(a.tgId) % 100000);
        u.phone = "+779" + String.valueOf(10000000 + (int) (Math.abs(a.tgId) % 10000000));
        u.contact = true;
        u.status = onlineStatus();
        return u;
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
                messages.put(new JSONObject().put("role", e.role).put("content", e.content));
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
