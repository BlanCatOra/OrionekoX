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

    /**
     * True when a TL request targets a synthetic agent peer. Such peers only exist in the
     * local database, so the request must never leave the device (Telegram would answer
     * PEER_ID_INVALID and the account would be poking at ids it does not own).
     */
    public static boolean isAgentRequest(org.telegram.tgnet.TLObject req) {
        if (req == null) {
            return false;
        }
        try {
            if (isAgentPeer(field(req, "peer"))) return true;
            if (isAgentPeer(field(req, "to_peer"))) return true;
            if (isAgentPeer(field(req, "from_peer"))) return true;
            if (isAgentPeer(field(req, "id"))) return true;
            if (isAgentPeer(field(req, "user_id"))) return true;
            if (req instanceof TLRPC.TL_channels_inviteToChannel) {
                Object users = field(req, "users");
                if (users instanceof Iterable) {
                    for (Object u : (Iterable<?>) users) {
                        if (isAgentPeer(u)) return true;
                    }
                }
            }
        } catch (Throwable ignore) {
        }
        return false;
    }

    private static boolean isAgentPeer(Object peer) {
        long uid = userIdOf(peer);
        return uid != 0 && isAgentDialog(uid);
    }

    private static long userIdOf(Object peer) {
        if (peer instanceof org.telegram.tgnet.TLRPC.TL_inputPeerUser) {
            return ((org.telegram.tgnet.TLRPC.TL_inputPeerUser) peer).user_id;
        }
        if (peer instanceof org.telegram.tgnet.TLRPC.TL_inputUser) {
            return ((org.telegram.tgnet.TLRPC.TL_inputUser) peer).user_id;
        }
        if (peer instanceof Long) {
            return (Long) peer;
        }
        return 0;
    }

    // ---------------- agents in ordinary groups ----------------

    /**
     * Handles server requests that would otherwise be dropped because they target a
     * synthetic agent. Only adding an agent to a basic group is supported: membership
     * is recorded locally, the stock participant list is updated, and the original
     * request callback receives a synthetic updates object.
     */
    public static boolean handleAgentRequest(org.telegram.tgnet.TLObject req, org.telegram.tgnet.RequestDelegate onComplete) {
        try {
            if (req instanceof TLRPC.TL_messages_addChatUser) {
                TLRPC.TL_messages_addChatUser r = (TLRPC.TL_messages_addChatUser) req;
                OxAgent agent = agentByTgId(userIdOf(r.user_id));
                if (agent == null) return false;
                OxGroupStore.addAgent(r.chat_id, agent.id);
                final TLRPC.TL_updates updates = participantUpdates(r.chat_id, agent);
                req.freeResources();
                if (onComplete != null) {
                    new Thread(() -> onComplete.run(updates, null), "ox-group-add").start();
                }
                log("local add-to-group chat=" + r.chat_id + " agent=" + agent.id);
                return true;
            }
            if (req instanceof TLRPC.TL_channels_inviteToChannel) {
                TLRPC.TL_channels_inviteToChannel r = (TLRPC.TL_channels_inviteToChannel) req;
                long channelId = r.channel.channel_id;
                ArrayList<OxAgent> added = new ArrayList<>();
                for (TLRPC.InputUser iu : r.users) {
                    OxAgent agent = agentByTgId(userIdOf(iu));
                    if (agent != null) {
                        OxGroupStore.addAgent(channelId, agent.id);
                        added.add(agent);
                    }
                }
                if (added.isEmpty()) return false;
                TLRPC.TL_updates updates = new TLRPC.TL_updates();
                for (OxAgent a : added) updates.users.add(buildAgentUser(a));
                TLRPC.Chat chat = MessagesController.getInstance(0).getChat(channelId);
                if (chat != null) {
                    org.telegram.tgnet.tl.TL_update.TL_updateChannel uc = new org.telegram.tgnet.tl.TL_update.TL_updateChannel();
                    uc.channel_id = channelId;
                    updates.updates.add(uc);
                    updates.chats.add(chat);
                }
                req.freeResources();
                if (onComplete != null) {
                    final TLRPC.TL_updates finalUpdates = updates;
                    new Thread(() -> onComplete.run(finalUpdates, null), "ox-group-add").start();
                }
                log("local invite-to-channel channel=" + channelId + " agents=" + added.size());
                return true;
            }
        } catch (Throwable t) {
            logErr("handleAgentRequest failed", t);
        }
        return false;
    }

    private static TLRPC.TL_updates participantUpdates(long chatId, OxAgent agent) {
        TLRPC.TL_updates updates = new TLRPC.TL_updates();
        updates.users.add(buildAgentUser(agent));
        TLRPC.ChatFull full = MessagesController.getInstance(0).getChatFull(chatId);
        TLRPC.Chat chat = MessagesController.getInstance(0).getChat(chatId);
        if (chat != null) updates.chats.add(chat);
        // Without a loaded participant snapshot there is nothing safe to merge; the
        // calling screen still inserts the new row into its own list, and the next
        // full-chat load is patched by ensureGroupParticipants().
        if (full == null || full.participants == null) {
            return updates;
        }
        TLRPC.TL_chatParticipants participants = new TLRPC.TL_chatParticipants();
        participants.chat_id = chatId;
        participants.version = full.participants.version + 1;
        boolean present = false;
        for (TLRPC.ChatParticipant p : full.participants.participants) {
            if (p == null) continue;
            if (p.user_id == agent.tgId) present = true;
            participants.participants.add(p);
        }
        if (!present) {
            TLRPC.TL_chatParticipant p = new TLRPC.TL_chatParticipant();
            p.user_id = agent.tgId;
            p.inviter_id = SELF_ID;
            p.date = (int) (System.currentTimeMillis() / 1000);
            participants.participants.add(p);
        }
        full.participants = participants;
        full.participants_count = participants.participants.size();
        org.telegram.tgnet.tl.TL_update.TL_updateChatParticipants update = new org.telegram.tgnet.tl.TL_update.TL_updateChatParticipants();
        update.participants = participants;
        updates.updates.add(update);
        return updates;
    }

    // ---------------- local group creation ----------------

    /** Called before the normal createChat request: returns false when no agent is selected. */
    public static boolean handleCreateChat(TLRPC.TL_messages_createChat req,
                                           org.telegram.tgnet.RequestDelegate onComplete) {
        try {
            ArrayList<TLRPC.InputUser> real = new ArrayList<>();
            ArrayList<OxAgent> agents = new ArrayList<>();
            for (TLRPC.InputUser iu : req.users) {
                OxAgent a = agentByTgId(userIdOf(iu));
                if (a != null) agents.add(a);
                else real.add(iu);
            }
            if (agents.isEmpty()) return false;

            if (!real.isEmpty()) {
                TLRPC.TL_messages_createChat clone = new TLRPC.TL_messages_createChat();
                clone.flags = req.flags;
                clone.title = req.title;
                clone.ttl_period = req.ttl_period;
                clone.users.addAll(real);
                req.freeResources();
                org.telegram.tgnet.ConnectionsManager.getInstance(0).sendRequest(clone, (response, error) -> {
                    if (error == null && response != null) {
                        try {
                            ArrayList<TLRPC.Chat> chats = chatsFromCreateResponse(response);
                            if (!chats.isEmpty()) {
                                TLRPC.Chat chat = chats.get(0);
                                for (OxAgent a : agents) OxGroupStore.addAgent(chat.id, a.id);
                                chat.participants_count += agents.size();
                            }
                        } catch (Throwable t) {
                            logErr("createChat agent post-add failed", t);
                        }
                    }
                    if (onComplete != null) onComplete.run(response, error);
                }, org.telegram.tgnet.ConnectionsManager.RequestFlagFailOnServerErrors);
                log("createChat split real=" + real.size() + " agents=" + agents.size());
                return true;
            }

            final TLRPC.TL_messages_invitedUsers result = createLocalGroup(req.title, agents);
            req.freeResources();
            if (onComplete != null) {
                new Thread(() -> onComplete.run(result, null), "ox-group-create").start();
            }
            return true;
        } catch (Throwable t) {
            logErr("handleCreateChat failed", t);
            return false;
        }
    }

    private static ArrayList<TLRPC.Chat> chatsFromCreateResponse(Object response) {
        ArrayList<TLRPC.Chat> out = new ArrayList<>();
        TLRPC.Updates updates = null;
        if (response instanceof TLRPC.TL_messages_invitedUsers) {
            updates = ((TLRPC.TL_messages_invitedUsers) response).updates;
        } else if (response instanceof TLRPC.Updates) {
            updates = (TLRPC.Updates) response;
        }
        if (updates != null && updates.chats != null) out.addAll(updates.chats);
        return out;
    }

    private static long nextLocalChatId() {
        int seq = prefs().getInt("next_local_chat", 1800000000);
        prefs().edit().putInt("next_local_chat", seq + 1).apply();
        return seq;
    }

    private static TLRPC.TL_messages_invitedUsers createLocalGroup(String title, ArrayList<OxAgent> agents) {
        long chatId = nextLocalChatId();
        int now = (int) (System.currentTimeMillis() / 1000);
        TLRPC.User me = MessagesController.getInstance(0).getUser(SELF_ID);
        if (me == null) {
            me = MessagesController.getInstance(0).getUser(UserConfig.getInstance(0).getClientUserId());
        }
        long selfId = me == null ? SELF_ID : me.id;

        TLRPC.TL_chat chat = new TLRPC.TL_chat();
        chat.id = chatId;
        chat.title = title == null || title.trim().isEmpty() ? "Ox Group" : title.trim();
        chat.photo = new TLRPC.TL_chatPhotoEmpty();
        chat.participants_count = 1 + agents.size();
        chat.date = now;
        chat.version = 1;
        chat.creator = true;
        chat.admin_rights = new TLRPC.TL_chatAdminRights();
        chat.default_banned_rights = new TLRPC.TL_chatBannedRights();

        TLRPC.TL_chatParticipants participants = new TLRPC.TL_chatParticipants();
        participants.chat_id = chatId;
        participants.version = 1;
        TLRPC.TL_chatParticipantCreator selfPart = new TLRPC.TL_chatParticipantCreator();
        selfPart.user_id = selfId;
        participants.participants.add(selfPart);
        ArrayList<TLRPC.User> users = new ArrayList<>();
        if (me != null) users.add(me);
        for (OxAgent a : agents) {
            TLRPC.TL_chatParticipant p = new TLRPC.TL_chatParticipant();
            p.user_id = a.tgId;
            p.inviter_id = selfId;
            p.date = now;
            participants.participants.add(p);
            OxGroupStore.addAgent(chatId, a.id);
            users.add(buildAgentUser(a));
        }

        TLRPC.TL_chatFull full = new TLRPC.TL_chatFull();
        full.id = chatId;
        full.about = "";
        full.participants = participants;
        full.participants_count = participants.participants.size();
        full.notify_settings = new TLRPC.TL_peerNotifySettings();
        full.can_view_participants = true;

        try {
            MessagesStorage.getInstance(0).putUsersAndChats(users, new ArrayList<>(java.util.Collections.singletonList(chat)), true, true);
            for (TLRPC.User u : users) MessagesController.getInstance(0).putUser(u, false);
            MessagesController.getInstance(0).putChat(chat, false);
            MessagesController.getInstance(0).putChatFull(full);
            MessagesStorage.getInstance(0).updateChatInfo(full, false);
        } catch (Throwable t) {
            logErr("createLocalGroup persist failed", t);
        }

        // A local service message makes the group appear in the dialog list and
        // gives the stock ChatActivity something to render immediately.
        TLRPC.TL_messageService service = new TLRPC.TL_messageService();
        service.id = nextLocalMessageId();
        service.local_id = service.id;
        service.out = true;
        service.date = now;
        service.peer_id = peerFor(-chatId);
        service.from_id = new TLRPC.TL_peerUser();
        service.from_id.user_id = selfId;
        service.dialog_id = -chatId;
        service.unread = false;
        service.flags = 256;
        TLRPC.TL_messageActionChatCreate action = new TLRPC.TL_messageActionChatCreate();
        action.title = chat.title;
        action.users = new ArrayList<>();
        action.users.add(selfId);
        for (OxAgent a : agents) action.users.add(a.tgId);
        service.action = action;
        persistAndShow(-chatId, service);
        refreshDialogRow(-chatId);

        TLRPC.TL_updates updates = new TLRPC.TL_updates();
        org.telegram.tgnet.tl.TL_update.TL_updateNewMessage newMessageUpdate =
                new org.telegram.tgnet.tl.TL_update.TL_updateNewMessage();
        newMessageUpdate.message = service;
        updates.updates.add(newMessageUpdate);
        updates.users.addAll(users);
        updates.chats.add(chat);
        TLRPC.TL_messages_invitedUsers result = new TLRPC.TL_messages_invitedUsers();
        result.updates = updates;
        log("createLocalGroup id=" + chatId + " agents=" + agents.size());
        return result;
    }

    /** Adds agents to a local group without a server request. */
    public static boolean addLocalGroupMembers(TLRPC.Chat chat, ArrayList<TLRPC.User> users,
                                               androidx.core.util.Consumer<TLRPC.User> onAddUser,
                                               Runnable onComplete) {
        if (chat == null || users == null || !OxGroupStore.isLocalGroup(chat.id)) return false;
        try {
            ensureLocalGroupFull(chat.id);
            MessagesController mc = MessagesController.getInstance(0);
            TLRPC.ChatFull full = mc.getChatFull(chat.id);
            if (full == null || !(full.participants instanceof TLRPC.TL_chatParticipants)) return false;
            for (TLRPC.User user : users) {
                OxAgent a = agentByTgId(user.id);
                if (a == null) continue;
                boolean exists = false;
                for (TLRPC.ChatParticipant p : full.participants.participants) {
                    if (p.user_id == user.id) { exists = true; break; }
                }
                if (exists) continue;
                TLRPC.TL_chatParticipant p = new TLRPC.TL_chatParticipant();
                p.user_id = user.id;
                p.inviter_id = UserConfig.getInstance(0).getClientUserId();
                p.date = (int) (System.currentTimeMillis() / 1000);
                full.participants.participants.add(p);
                OxGroupStore.addAgent(chat.id, a.id);
                mc.putUser(user, true);
                // ProfileActivity appends every onAddUser result in onComplete; the full
                // updated here already contains the participant, so skip that callback.
            }
            full.participants_count = full.participants.participants.size();
            chat.participants_count = full.participants_count;
            mc.putChat(chat, false);
            mc.putChatFull(full);
            MessagesStorage.getInstance(0).updateChatInfo(full, false);
            MessagesStorage.getInstance(0).putUsersAndChats(new ArrayList<>(), new ArrayList<>(java.util.Collections.singletonList(chat)), true, true);
            NotificationCenter.getInstance(0).postNotificationName(NotificationCenter.chatInfoDidLoad, full, 0, false, false);
            log("addLocalGroupMembers chat=" + chat.id + " count=" + full.participants_count);
        } catch (Throwable t) {
            logErr("addLocalGroupMembers failed", t);
        }
        if (onComplete != null) onComplete.run();
        return true;
    }

    /** Re-adds locally stored agents after every full-chat load from Telegram. */
    public static void ensureLocalGroupFull(long chatId) {
        if (!OxGroupStore.isLocalGroup(chatId)) return;
        try {
            MessagesController mc = MessagesController.getInstance(0);
            // Always rebuild: an older persisted full may predate the self-user fix.
            TLRPC.TL_chatParticipants participants = new TLRPC.TL_chatParticipants();
            participants.chat_id = chatId;
            participants.version = 1;
            long selfId = UserConfig.getInstance(0).getClientUserId();
            TLRPC.TL_chatParticipantCreator selfPart = new TLRPC.TL_chatParticipantCreator();
            selfPart.user_id = selfId;
            participants.participants.add(selfPart);
            ArrayList<TLRPC.User> users = new ArrayList<>();
            TLRPC.User self = UserConfig.getInstance(0).getCurrentUser();
            if (self == null) self = mc.getUser(selfId);
            if (self != null) {
                users.add(self);
                // false would keep a pre-existing TL_userEmpty placeholder in the cache
                mc.putUser(self, true);
            }
            for (OxAgent a : OxGroupStore.agents(chatId)) {
                TLRPC.TL_chatParticipant p = new TLRPC.TL_chatParticipant();
                p.user_id = a.tgId;
                p.inviter_id = SELF_ID;
                p.date = (int) (System.currentTimeMillis() / 1000);
                participants.participants.add(p);
                TLRPC.User u = buildAgentUser(a);
                users.add(u);
                mc.putUser(u, false);
            }
            TLRPC.TL_chatFull full = new TLRPC.TL_chatFull();
            full.id = chatId;
            full.about = "";
            full.participants = participants;
            full.participants_count = participants.participants.size();
            full.notify_settings = new TLRPC.TL_peerNotifySettings();
            full.can_view_participants = true;
            TLRPC.Chat chat = mc.getChat(chatId);
            ArrayList<TLRPC.Chat> chats = new ArrayList<>();
            if (chat != null) {
                chat.participants_count = participants.participants.size();
                mc.putChat(chat, false);
                chats.add(chat);
            }
            MessagesStorage.getInstance(0).putUsersAndChats(users, chats, true, true);
            mc.putChatFull(full);
            MessagesStorage.getInstance(0).updateChatInfo(full, false);
            log("ensureLocalGroupFull chat=" + chatId + " selfId=" + selfId
                    + " selfUser=" + (self != null)
                    + " selfResolved=" + (mc.getUser(selfId) != null)
                    + " participants=" + participants.participants.size());
        } catch (Throwable t) {
            logErr("ensureLocalGroupFull failed chat=" + chatId, t);
        }
    }

    /** Re-adds locally stored agents after every full-chat load from Telegram. */
    public static void ensureGroupParticipants(TLRPC.ChatFull full) {
        if (full == null || full.id == 0) return;
        List<OxAgent> agents = OxGroupStore.agents(full.id);
        if (agents.isEmpty()) return;
        if (!(full.participants instanceof TLRPC.TL_chatParticipants)) return;
        boolean changed = false;
        java.util.HashSet<Long> ids = new java.util.HashSet<>();
        for (TLRPC.ChatParticipant p : full.participants.participants) {
            if (p != null) ids.add(p.user_id);
        }
        for (OxAgent a : agents) {
            if (ids.contains(a.tgId)) continue;
            TLRPC.TL_chatParticipant p = new TLRPC.TL_chatParticipant();
            p.user_id = a.tgId;
            p.inviter_id = SELF_ID;
            p.date = (int) (System.currentTimeMillis() / 1000);
            full.participants.participants.add(p);
            ids.add(a.tgId);
            changed = true;
        }
        if (changed) {
            full.participants_count = Math.max(full.participants_count, full.participants.participants.size());
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(0)
                    .postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_CHAT_MEMBERS));
            log("ensureGroupParticipants chat=" + full.id + " agents=" + agents.size());
        }
    }

    private static volatile boolean groupObserverInstalled;

    private static void installGroupObserver() {
        if (groupObserverInstalled) return;
        groupObserverInstalled = true;
        NotificationCenter.getInstance(0).addObserver((id, account, args) -> {
            if (id == NotificationCenter.chatInfoDidLoad && args != null && args.length > 0
                    && args[0] instanceof TLRPC.ChatFull) {
                ensureGroupParticipants((TLRPC.ChatFull) args[0]);
            }
        }, NotificationCenter.chatInfoDidLoad);
    }

    /** Installs the observers and mirrors agents into the stock contacts list. */
    public static void ensureAgentContacts() {
        installGroupObserver();
        installContactsObserver();
        syncAgentContacts(OxAgentStore.listAgents());
    }

    private static volatile boolean contactsObserverInstalled;

    private static void installContactsObserver() {
        if (contactsObserverInstalled) return;
        contactsObserverInstalled = true;
        NotificationCenter.getInstance(0).addObserver((id, account, args) -> {
            if (id == NotificationCenter.contactsDidLoad) {
                syncAgentContacts(OxAgentStore.listAgents());
            }
        }, NotificationCenter.contactsDidLoad);
    }

    private static Object field(Object target, String name) {
        Class<?> c = target.getClass();
        while (c != null && c != Object.class) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("orioneko_config", android.content.Context.MODE_PRIVATE);
    }

    // ---------------- local account ----------------

    /** Idempotent: activates the offline local account and mirrors every agent as a contact. */
    public static void ensureLocalAccount() {
        synchronized (SYNC_LOCK) {
            if (OxMode.isAgentOnly()) {
                // Entering Ox mode at runtime (onboarding / "Use Ox mode" link) happens in a
                // process that may have already connected for a real account. Re-pause the
                // native client, otherwise tgnet keeps probing Telegram DCs while the user is
                // supposed to be fully local.
                try {
                    org.telegram.tgnet.ConnectionsManager.getInstance(0).setAppPaused(true, false);
                    // clear a stale "Connecting..." action-bar title left over from login mode
                    NotificationCenter.getInstance(0).postNotificationName(NotificationCenter.didUpdateConnectionState);
                } catch (Throwable t) {
                    logErr("pauseNetwork for Ox mode failed", t);
                }
            }
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
            installGroupObserver();
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

    /** Agents are real contact rows for the stock "add members" / "new group" pickers. */
    private static void syncAgentContacts(List<OxAgent> agents) {
        try {
            org.telegram.messenger.ContactsController cc = org.telegram.messenger.ContactsController.getInstance(0);
            java.util.HashSet<Long> wanted = new java.util.HashSet<>();
            ArrayList<TLRPC.TL_contact> persist = new ArrayList<>();
            for (OxAgent a : agents) {
                if (a.tgId == 0) continue;
                wanted.add(a.tgId);
                TLRPC.TL_contact row = cc.contactsDict.get(a.tgId);
                if (row == null) {
                    row = new TLRPC.TL_contact();
                    row.user_id = a.tgId;
                    cc.contactsDict.put(a.tgId, row);
                }
                boolean present = false;
                for (int i = 0; i < cc.contacts.size(); i++) {
                    if (cc.contacts.get(i).user_id == a.tgId) {
                        present = true;
                        break;
                    }
                }
                if (!present) {
                    TLRPC.TL_contact added = new TLRPC.TL_contact();
                    added.user_id = a.tgId;
                    cc.contacts.add(added);
                    persist.add(added);
                }
            }
            if (!persist.isEmpty()) {
                MessagesStorage.getInstance(0).putContacts(persist, false);
            }
            for (int i = cc.contacts.size() - 1; i >= 0; i--) {
                long uid = cc.contacts.get(i).user_id;
                if (isAgentDialog(uid) && !wanted.contains(uid)) {
                    cc.contacts.remove(i);
                    cc.contactsDict.remove(uid);
                }
            }
        } catch (Throwable t) {
            logErr("syncAgentContacts failed", t);
        }
    }

    public static void syncAgents(boolean greetNew) {
        try {
            List<OxAgent> agents = OxAgentStore.listAgents();
            installGroupObserver();
            installContactsObserver();
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
            for (OxAgent a : agents) putAgentFull(a);
            syncAgentContacts(agents);
            sanitizeLocalGroupTitles();
            AndroidUtilities.runOnUIThread(OxAgentsBridge::sanitizeLocalGroupTitles, 2500);
            AndroidUtilities.runOnUIThread(OxAgentsBridge::sanitizeLocalGroupTitles, 7000);
            final StringBuilder liveIds = new StringBuilder();
            for (OxAgent a : agents) {
                ensureDialogRow(a.tgId);
                refreshDialogRow(a.tgId);
                greetIfEmpty(a);
                if (liveIds.length() > 0) liveIds.append(',');
                liveIds.append(a.tgId);
            }
            pruneOrphanAgentDialogs(liveIds.toString());
            AndroidUtilities.runOnUIThread(() -> {
                NotificationCenter.getInstance(0).postNotificationName(NotificationCenter.dialogsNeedReload);
                // The first cache pass can run before greetIfEmpty() has stored the last
                // message, and a dialog row with last_mid=0 is skipped by the loader. Re-read
                // the local dialog list once the greetings (or refreshDialogRow) have landed.
                org.telegram.messenger.MessagesController.getInstance(0).loadDialogs(0, 0, 100, true);
                AndroidUtilities.runOnUIThread(() ->
                        org.telegram.messenger.MessagesController.getInstance(0).loadDialogs(0, 0, 100, true), 1200);
            });
        } catch (Throwable t) {
            logErr("syncAgents failed", t);
        }
    }

    /** Greets on first materialization: injects a welcome message when the dialog has none
     *  (a dialog row without a last message does not show in the chat list). */
    private static final Object GREET_LOCK = new Object();
    private static final java.util.HashSet<Long> GREETED = new java.util.HashSet<>();

    private static void greetIfEmpty(OxAgent a) {
        MessagesStorage.getInstance(0).getStorageQueue().postRunnable(() -> {
            synchronized (GREET_LOCK) {
                if (GREETED.contains(a.tgId)) return;
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
                GREETED.add(a.tgId);
                if (empty) {
                    log("greeting empty dialog " + a.tgId);
                    injectAgentReply(a.tgId, "你好，我是 " + a.emoji + " " + a.name + "。直接发消息即可开始对话。");
                }
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
        u.phone = "779" + String.valueOf(10000000 + (int) (Math.abs(a.tgId) % 10000000));
        u.contact = true;
        u.bot = true;
        u.status = onlineStatus();
        attachAvatar(u, a);
        return u;
    }

    /** Builds and caches the stock UserFull (bio) for an agent so any profile screen can render it. */
    public static void putAgentFull(OxAgent agent) {
        if (agent == null) return;
        try {
            TLRPC.TL_user u = buildAgentUser(agent);
            TLRPC.TL_userFull full = new TLRPC.TL_userFull();
            full.flags |= 1;
            full.id = u.id;
            full.user = u;
            String about = agent.remark == null ? "" : agent.remark.trim();
            if (about.isEmpty()) {
                about = org.telegram.messenger.LocaleController.formatString(
                        "OxAgentProfileAbout", org.telegram.messenger.R.string.OxAgentProfileAbout,
                        agent.model == null || agent.model.isEmpty() ? "OpenAI-compatible" : agent.model);
            }
            full.about = about;
            full.settings = new TLRPC.TL_peerSettings();
            full.notify_settings = new TLRPC.TL_peerNotifySettings();
            full.common_chats_count = 0;
            MessagesController.getInstance(0).putUserFull(full);
            MessagesStorage.getInstance(0).updateUserInfo(full, false);
        } catch (Throwable t) {
            logErr("putAgentFull failed", t);
        }
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

    /** Old local groups were named with a machine-translated "and" marker; repair them. */
    private static void sanitizeLocalGroupTitles() {
        try {
            ArrayList<TLRPC.Chat> changed = new ArrayList<>();
            for (long id : OxGroupStore.chatIds()) {
                TLRPC.Chat chat = MessagesController.getInstance(0).getChat(id);
                if (chat == null || chat.title == null) continue;
                String title = chat.title;
                if (title.contains("(单位:千美元)")) title = title.replace("(单位:千美元)", "、");
                if (title.contains("（单位：千美元）")) title = title.replace("（单位：千美元）", "、");
                if (!title.equals(chat.title)) {
                    chat.title = title;
                    changed.add(chat);
                }
            }
            if (!changed.isEmpty()) {
                for (TLRPC.Chat chat : changed) MessagesController.getInstance(0).putChat(chat, false);
                MessagesStorage.getInstance(0).putUsersAndChats(new ArrayList<>(), changed, true, true);
                AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(0)
                        .postNotificationName(NotificationCenter.dialogsNeedReload));
            }
        } catch (Throwable t) {
            logErr("sanitizeLocalGroupTitles failed", t);
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

    /** Monotonic local message id for synthetic dialogs. UserConfig.getNewMessageId()
     *  restarts at -210000 on every launch, so ids collided across sessions and the local
     *  REPLACE INTO messages_v2 overwrote earlier turns. */
    private static synchronized int nextLocalMessageId() {
        int id = prefs().getInt("local_msg_seq", 100000) + 1;
        prefs().edit().putInt("local_msg_seq", id).apply();
        return id;
    }

    // ---------------- outgoing interception (called from SendMessagesHelper) ----------------

    public static boolean interceptOutgoing(SendMessagesHelper.SendMessageParams p) {
        if (p == null || !isAgentDialog(p.peer)) {
            return false;
        }
        if (p.user != null || p.document != null || p.location != null || p.poll != null
                || p.todo != null || p.mediaWebPage != null || p.webPage != null) {
            return handleOutgoingSpecial(p);
        }
        if (p.retryMessageObject != null || p.photo != null
                || p.invoice != null || p.game != null
                || p.videoEditedInfo != null || p.sendingStory != null) {
            // Photos arrive through prepareSendingMedia, which has its own local handler.
            // Everything else has no local pipeline yet: answer instead of letting the
            // SendMessagesHelper fall through to a server send for a peer that does not exist.
            log("interceptOutgoing unsupported type dialog=" + p.peer);
            injectAgentReply(p.peer, "（暂不支持向智能体发送这类内容）");
            return true;
        }
        String text = p.message != null ? p.message : p.caption;
        if (text == null || text.trim().isEmpty()) {
            return true;
        }
        log("interceptOutgoing dialog=" + p.peer + " text=" + text);
        handleOutgoingText(p.peer, text.trim(), p.replyToMsg);
        return true;
    }

    /** Injects a user turn into an agent dialog without going through the send UI (workflows). */
    public static void sendUserPrompt(long dialogId, String text) {
        if (dialogId == 0 || text == null || text.trim().isEmpty()) return;
        handleOutgoingText(dialogId, text.trim(), null);
    }

    /**
     * Makes a synthetic agent recognizable to the stock ChatActivity in a real account:
     * its dialog is not part of the server dialog list, so ChatActivity would wait for
     * messages.getHistory forever. Inject the last local message into dialogs_dict so the
     * cached history and the peer header resolve immediately.
     */
    public static void ensureNativeDialog(final OxAgent agent, final Runnable onReady) {
        if (agent == null || agent.tgId == 0) {
            if (onReady != null) AndroidUtilities.runOnUIThread(onReady);
            return;
        }
        final long tgId = agent.tgId;
        MessagesStorage.getInstance(0).getStorageQueue().postRunnable(() -> {
            long mid = 0;
            int date = 0;
            try {
                org.telegram.SQLite.SQLiteCursor cur = MessagesStorage.getInstance(0).getDatabase()
                        .queryFinalized("SELECT mid, date FROM messages_v2 WHERE uid = " + tgId + " ORDER BY date DESC, mid DESC LIMIT 1");
                if (cur.next()) {
                    mid = cur.longValue(0);
                    date = (int) cur.longValue(1);
                }
                cur.dispose();
            } catch (Throwable t) {
                logErr("ensureNativeDialog query failed did=" + tgId, t);
            }
            final long lastMid = mid;
            final int lastDate = date;
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    TLRPC.TL_user u = buildAgentUser(agent);
                    ArrayList<TLRPC.User> users = new ArrayList<>();
                    users.add(u);
                    MessagesStorage.getInstance(0).putUsersAndChats(users, null, true, true);
                    MessagesController.getInstance(0).putUser(u, false);
                    TLRPC.TL_userFull full = new TLRPC.TL_userFull();
                    full.flags |= 1;   // has about
                    full.id = u.id;
                    full.user = u;
                    String about = agent.remark == null ? "" : agent.remark.trim();
                    if (about.isEmpty()) {
                        about = org.telegram.messenger.LocaleController.formatString(
                                "OxAgentProfileAbout", org.telegram.messenger.R.string.OxAgentProfileAbout,
                                agent.model == null || agent.model.isEmpty() ? "OpenAI-compatible" : agent.model);
                    }
                    full.about = about;
                    full.settings = new TLRPC.TL_peerSettings();
                    full.notify_settings = new TLRPC.TL_peerNotifySettings();
                    full.common_chats_count = 0;
                    MessagesController.getInstance(0).putUserFull(full);
                    MessagesStorage.getInstance(0).updateUserInfo(full, false);
                } catch (Throwable t) {
                    logErr("ensureNativeDialog user failed", t);
                }
                try {
                    TLRPC.TL_dialog d = new TLRPC.TL_dialog();
                    d.id = tgId;
                    d.top_message = (int) lastMid;
                    d.last_message_date = lastDate;
                    d.unread_count = 0;
                    d.folder_id = 0;
                    MessagesController.getInstance(0).dialogs_dict.put(tgId, d);
                } catch (Throwable t) {
                    logErr("ensureNativeDialog inject failed", t);
                }
                if (onReady != null) onReady.run();
            });
        });
    }

    // ---------------- special messages (documents, location, contact, poll, todo) ----------------

    private static boolean handleOutgoingSpecial(SendMessagesHelper.SendMessageParams p) {
        long dialogId = p.peer;
        OxAgent agent = agentByTgId(dialogId);
        String caption = p.caption != null ? p.caption : (p.message != null ? p.message : "");
        log("handleOutgoingSpecial dialog=" + dialogId + " doc=" + (p.document != null)
                + " loc=" + (p.location != null) + " poll=" + (p.poll != null)
                + " todo=" + (p.todo != null) + " user=" + (p.user != null));
        try {
            if (p.mediaWebPage != null || p.webPage != null) {
                TLRPC.TL_message msg = baseOutgoing(dialogId);
                msg.flags = 256 | 512;
                if (p.mediaWebPage != null) {
                    msg.media = p.mediaWebPage;
                } else {
                    TLRPC.TL_messageMediaWebPage media = new TLRPC.TL_messageMediaWebPage();
                    media.webpage = p.webPage;
                    msg.media = media;
                }
                msg.message = caption == null ? "" : caption;
                persistAndShow(dialogId, msg);
                TLRPC.WebPage page = p.webPage;
                if (page == null && p.mediaWebPage != null) page = p.mediaWebPage.webpage;
                StringBuilder prompt = new StringBuilder("用户分享了一篇文章");
                if (page != null) {
                    if (page.title != null && !page.title.isEmpty()) prompt.append("：").append(page.title);
                    if (page.url != null && !page.url.isEmpty()) prompt.append("\n链接：").append(page.url);
                    if (page.description != null && !page.description.isEmpty()) prompt.append("\n摘要：").append(page.description);
                }
                if (caption != null && !caption.trim().isEmpty()) prompt.append("\n附带说明：").append(caption.trim());
                startMediaTurn(agent, dialogId, prompt.toString(), null, prompt.toString());
                return true;
            }
            if (p.document != null) {
                TLRPC.TL_document doc = p.document;
                String name = documentName(doc);
                String mime = doc.mime_type == null ? "" : doc.mime_type;
                boolean video = hasAttribute(doc, TLRPC.TL_documentAttributeVideo.class);
                TLRPC.DocumentAttribute audio = findAttribute(doc, TLRPC.TL_documentAttributeAudio.class);
                boolean voice = audio instanceof TLRPC.TL_documentAttributeAudio && ((TLRPC.TL_documentAttributeAudio) audio).voice;
                boolean animated = hasAttribute(doc, TLRPC.TL_documentAttributeAnimated.class);
                boolean sticker = hasAttribute(doc, TLRPC.TL_documentAttributeSticker.class);
                boolean music = audio instanceof TLRPC.TL_documentAttributeAudio && !voice;
                // The picker path is temporary and is deleted after sending. Archive
                // first and point the message at the durable copy, otherwise the bubble
                // renders a download button forever even though the bytes are present.
                String durablePath = p.path;
                String archivedRel = null;
                if (!sticker && p.path != null && agent != null && new File(p.path).exists()) {
                    archivedRel = archiveIntoWorkspace(agent, p.path, name);
                    if (archivedRel != null) {
                        durablePath = new File(OxAgentStore.workspaceDir(agent.id), archivedRel).getAbsolutePath();
                    }
                }
                if (durablePath != null && new File(durablePath).exists()) {
                    doc.id = Integer.MIN_VALUE;
                    doc.dc_id = Integer.MIN_VALUE;
                    doc.file_reference = new byte[0];
                    doc.localPath = durablePath;
                }
                injectDocument(dialogId, doc, durablePath, caption);
                String kind = video ? "视频" : voice ? "语音消息" : music ? "音乐"
                        : animated ? "动图（GIF）" : sticker ? "贴纸" : "文件";
                StringBuilder prompt = new StringBuilder("用户发送了").append(kind);
                prompt.append("：").append(name);
                if (!mime.isEmpty()) prompt.append("（").append(mime);
                if (doc.size > 0) prompt.append(", ").append(doc.size).append(" 字节");
                if (!mime.isEmpty()) prompt.append("）");
                if (caption != null && !caption.trim().isEmpty()) prompt.append("\n附带说明：").append(caption.trim());
                if (sticker) {
                    TLRPC.DocumentAttribute sa = findAttribute(doc, TLRPC.TL_documentAttributeSticker.class);
                    if (sa != null && sa.alt != null) prompt.append("\n贴纸表情：").append(sa.alt);
                } else if (archivedRel != null) {
                    prompt.append("\n文件已存入工作区：").append(archivedRel).append("（可用 read_file / list_dir 工具读取）");
                }
                startMediaTurn(agent, dialogId, prompt.toString(), null, prompt.toString());
                return true;
            }
            if (p.location != null) {
                TLRPC.TL_message msg = baseOutgoing(dialogId);
                msg.flags = 256 | 512;
                msg.media = p.location;
                if (caption != null && !caption.trim().isEmpty()) msg.message = caption.trim();
                persistAndShow(dialogId, msg);
                String prompt = "用户分享了位置";
                if (p.location.geo != null && p.location.geo instanceof TLRPC.TL_geoPoint) {
                    TLRPC.TL_geoPoint gp = (TLRPC.TL_geoPoint) p.location.geo;
                    prompt += "：纬度 " + gp.lat + "，经度 " + gp._long;
                }
                if (caption != null && !caption.trim().isEmpty()) prompt += "\n附带说明：" + caption.trim();
                startMediaTurn(agent, dialogId, prompt, null, prompt);
                return true;
            }
            if (p.user != null) {
                TLRPC.TL_message msg = baseOutgoing(dialogId);
                msg.flags = 256 | 512;
                TLRPC.TL_messageMediaContact contact = new TLRPC.TL_messageMediaContact();
                contact.phone_number = p.user.phone;
                contact.first_name = p.user.first_name;
                contact.last_name = p.user.last_name;
                contact.user_id = p.user.id;
                contact.vcard = "";
                msg.media = contact;
                persistAndShow(dialogId, msg);
                String prompt = "用户分享了联系人：" + (p.user.first_name == null ? "" : p.user.first_name)
                        + (p.user.last_name == null ? "" : " " + p.user.last_name)
                        + (p.user.phone == null ? "" : "，电话 " + p.user.phone);
                startMediaTurn(agent, dialogId, prompt, null, prompt);
                return true;
            }
            if (p.poll != null) {
                TLRPC.TL_message msg = baseOutgoing(dialogId);
                msg.flags = 256 | 512;
                msg.media = p.poll;
                persistAndShow(dialogId, msg);
                StringBuilder prompt = new StringBuilder("用户创建了投票：");
                if (p.poll.poll != null && p.poll.poll.question != null) prompt.append(p.poll.poll.question);
                if (p.poll.poll != null && p.poll.poll.answers != null) {
                    for (int i = 0; i < p.poll.poll.answers.size(); i++) {
                        prompt.append("\n").append(i + 1).append(". ").append(p.poll.poll.answers.get(i).text);
                    }
                }
                startMediaTurn(agent, dialogId, prompt.toString(), null, prompt.toString());
                return true;
            }
            if (p.todo != null) {
                TLRPC.TL_message msg = baseOutgoing(dialogId);
                msg.flags = 256 | 512;
                msg.media = p.todo;
                persistAndShow(dialogId, msg);
                StringBuilder prompt = new StringBuilder("用户发送了待办清单：");
                if (p.todo.todo != null && p.todo.todo.title != null) prompt.append(p.todo.todo.title.text);
                if (p.todo.todo != null && p.todo.todo.list != null) {
                    for (int i = 0; i < p.todo.todo.list.size(); i++) {
                        TLRPC.TodoItem item = p.todo.todo.list.get(i);
                        prompt.append("\n- ").append(item.title == null ? "" : item.title.text);
                    }
                }
                startMediaTurn(agent, dialogId, prompt.toString(), null, prompt.toString());
                return true;
            }
        } catch (Throwable t) {
            logErr("handleOutgoingSpecial failed", t);
            injectAgentReply(dialogId, "（这类内容处理失败：" + t.getMessage() + "）");
        }
        injectAgentReply(dialogId, "（暂不支持向智能体发送这类内容）");
        return true;
    }

    /** Called from SendMessagesHelper.sendSticker before it would reach the server. */
    public static boolean handleOutgoingSticker(TLRPC.Document document, long dialogId, CharSequence caption) {
        if (document == null || !isAgentDialog(dialogId)) {
            return false;
        }
        OxAgent agent = agentByTgId(dialogId);
        String emoji = "";
        TLRPC.DocumentAttribute sticker = findAttribute(document, TLRPC.TL_documentAttributeSticker.class);
        if (sticker != null && sticker.alt != null) emoji = sticker.alt;
        injectDocument(dialogId, document, null, caption == null ? "" : caption.toString());
        String prompt = "用户发送了一张贴纸" + (emoji.isEmpty() ? "" : "：" + emoji)
                + (caption == null || caption.length() == 0 ? "" : "\n附带说明：" + caption);
        startMediaTurn(agent, dialogId, prompt, null, prompt);
        return true;
    }

    private static TLRPC.TL_message baseOutgoing(long dialogId) {
        TLRPC.TL_message msg = new TLRPC.TL_message();
        msg.flags = 256;
        msg.id = nextLocalMessageId();
        msg.local_id = msg.id;
        msg.out = true;
        msg.date = (int) (System.currentTimeMillis() / 1000);
        msg.peer_id = new TLRPC.TL_peerUser();
        msg.peer_id.user_id = dialogId;
        msg.from_id = new TLRPC.TL_peerUser();
        msg.from_id.user_id = SELF_ID;
        msg.dialog_id = dialogId;
        msg.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
        return msg;
    }

    private static void injectDocument(long dialogId, TLRPC.Document doc, String localPath, String caption) {
        TLRPC.TL_message msg = baseOutgoing(dialogId);
        msg.flags = 256 | 512;
        TLRPC.TL_messageMediaDocument media = new TLRPC.TL_messageMediaDocument();
        media.flags |= 1;
        media.document = doc;
        if (hasAttribute(doc, TLRPC.TL_documentAttributeVideo.class)) media.video = true;
        TLRPC.DocumentAttribute audio = findAttribute(doc, TLRPC.TL_documentAttributeAudio.class);
        if (audio instanceof TLRPC.TL_documentAttributeAudio && ((TLRPC.TL_documentAttributeAudio) audio).voice) media.voice = true;
        msg.media = media;
        msg.attachPath = localPath;
        msg.message = caption == null ? "" : caption;
        persistAndShow(dialogId, msg);
    }

    private static boolean hasAttribute(TLRPC.Document doc, Class<?> cls) {
        return findAttribute(doc, cls) != null;
    }

    private static TLRPC.DocumentAttribute findAttribute(TLRPC.Document doc, Class<?> cls) {
        if (doc == null || doc.attributes == null) return null;
        for (TLRPC.DocumentAttribute a : doc.attributes) {
            if (cls.isInstance(a)) return a;
        }
        return null;
    }

    private static String documentName(TLRPC.Document doc) {
        TLRPC.DocumentAttribute a = findAttribute(doc, TLRPC.TL_documentAttributeFilename.class);
        if (a instanceof TLRPC.TL_documentAttributeFilename && a.file_name != null) return a.file_name;
        return "file_" + doc.id;
    }

    private static String archiveIntoWorkspace(OxAgent agent, String path, String name) {
        if (agent == null || path == null) return null;
        try {
            File dir = new File(OxAgentStore.workspaceDir(agent.id), "inbox");
            dir.mkdirs();
            File dst = new File(dir, System.currentTimeMillis() + "_" + name.replaceAll("[^a-zA-Z0-9._-]", "_"));
            java.io.FileInputStream in = new java.io.FileInputStream(path);
            java.io.FileOutputStream out = new java.io.FileOutputStream(dst);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close();
            in.close();
            return "inbox/" + dst.getName();
        } catch (Throwable t) {
            logErr("archiveIntoWorkspace failed", t);
            return null;
        }
    }

    private static JSONArray visionContent(String text, String imagePath) throws Exception {
        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("type", "text").put("text", text == null || text.isEmpty() ? "（看这张图片）" : text));
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        java.io.FileInputStream in = new java.io.FileInputStream(imagePath);
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        String b64 = android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP);
        parts.put(new JSONObject().put("type", "image_url")
                .put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + b64)));
        return parts;
    }

    private static void startMediaTurn(OxAgent agent, long dialogId, String prompt, String imagePath, String displayText) {
        if (agent == null) {
            return;
        }
        if (agent.isEchoMode()) {
            String reply = "⚡ 离线回声模式（未配置模型后端）\n\n" + displayText
                    + "\n\n👉 打开 智能体 管理 → 长按 → 编辑设置，填写接口地址 / API Key / 模型后即可接入真实模型与工具调用。";
            injectAgentReply(dialogId, reply);
            try {
                List<OxChatEntry> chat = OxAgentStore.loadChat(agent.id);
                chat.add(new OxChatEntry("user", displayText, System.currentTimeMillis(), imagePath));
                chat.add(new OxChatEntry("assistant", reply, System.currentTimeMillis()));
                OxAgentStore.saveChat(agent.id, chat);
            } catch (Throwable t) {
                logErr("startMediaTurn history failed", t);
            }
            return;
        }
        startTurn(agent, dialogId, prompt, imagePath);
    }

    // ---------------- media (photos/videos) to agents ----------------

    /** Routes media the user sends to an agent dialog. Photos become real outgoing
     *  photo messages in the chat (sizes written locally by generatePhotoSizes) and
     *  the file is archived into the agent's media inbox for tools/vision. */
    public static void handleOutgoingMedia(long dialogId, java.util.List<SendMessagesHelper.SendingMediaInfo> infos) {
        log("handleOutgoingMedia dialog=" + dialogId + " count=" + (infos == null ? 0 : infos.size()));
        final OxAgent agent = agentByTgId(dialogId);
        StringBuilder prompt = new StringBuilder();
        String firstImagePath = null;
        int images = 0, videos = 0, gifs = 0;
        if (infos != null) {
            for (SendMessagesHelper.SendingMediaInfo info : infos) {
                String path = info.path;
                if (path == null || path.isEmpty()) {
                    injectAgentReply(dialogId, "（该类型媒体暂不支持发送给智能体）");
                    continue;
                }
                String name = new File(path).getName();
                String lower = name.toLowerCase();
                try {
                    if (info.isVideo || lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".mov")
                            || lower.endsWith(".webm") || lower.endsWith(".3gp")) {
                        long[] meta = videoMeta(path);
                        TLRPC.TL_document doc = buildLocalDocument(path, name,
                                lower.endsWith(".webm") ? "video/webm" : "video/mp4",
                                true, false, (int) meta[0], (int) meta[1], meta[2] / 1000.0);
                        injectDocument(dialogId, doc, path, info.caption);
                        videos++;
                        prompt.append("用户发送了视频：").append(name);
                        if (meta[2] > 0) prompt.append("（时长 ").append(meta[2] / 1000).append(" 秒）");
                        if (info.caption != null && !info.caption.trim().isEmpty()) prompt.append("\n附带说明：").append(info.caption.trim());
                    } else if (lower.endsWith(".gif")) {
                        TLRPC.TL_document doc = buildLocalDocument(path, name, "image/gif", false, true, 0, 0, 0);
                        injectDocument(dialogId, doc, path, info.caption);
                        gifs++;
                        prompt.append("用户发送了一张动图（GIF）：").append(name);
                        if (info.caption != null && !info.caption.trim().isEmpty()) prompt.append("\n附带说明：").append(info.caption.trim());
                    } else {
                        SendMessagesHelper smh = SendMessagesHelper.getInstance(0);
                        TLRPC.TL_photo photo = smh.generatePhotoSizes(path, null);
                        if (photo == null) {
                            injectAgentReply(dialogId, "（图片读取失败：" + path + "）");
                            continue;
                        }
                        TLRPC.TL_message msg = baseOutgoing(dialogId);
                        msg.flags = 256 | 512;
                        msg.media = new TLRPC.TL_messageMediaPhoto();
                        msg.media.flags |= 1;
                        msg.media.photo = photo;
                        msg.media.ttl_seconds = 0;
                        msg.message = info.caption == null ? "" : info.caption;
                        persistAndShow(dialogId, msg);
                        images++;
                        String archivedPath = archiveMedia(agent, path, images);
                        if (firstImagePath == null) firstImagePath = archivedPath;
                        int[] wh = photoSizeOf(photo);
                        prompt.append("用户发送了图片");
                        if (wh[0] > 0) prompt.append("（").append(wh[0]).append("x").append(wh[1]).append("）");
                        if (info.caption != null && !info.caption.trim().isEmpty()) prompt.append("\n附带说明：").append(info.caption.trim());
                    }
                    prompt.append("\n");
                } catch (Throwable t) {
                    logErr("handleOutgoingMedia item failed", t);
                    injectAgentReply(dialogId, "（媒体处理失败：" + t.getMessage() + "）");
                }
            }
        }
        if (agent == null) {
            return;
        }
        String text = prompt.toString().trim();
        if (text.isEmpty()) text = "用户发送了媒体内容";
        if (images > 1) text = text + "\n（共 " + images + " 张图片）";
        startMediaTurn(agent, dialogId, text, firstImagePath, text);
    }

    private static String archiveMedia(OxAgent agent, String path, int index) {
        if (agent == null) return null;
        try {
            File inbox = new File(ApplicationLoader.applicationContext.getFilesDir(), "oxagents/" + agent.id + "/media");
            inbox.mkdirs();
            File dst = new File(inbox, System.currentTimeMillis() + "_" + index + ".jpg");
            java.io.FileInputStream in = new java.io.FileInputStream(path);
            java.io.FileOutputStream out = new java.io.FileOutputStream(dst);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close();
            in.close();
            return dst.getAbsolutePath();
        } catch (Throwable t) {
            logErr("archiveMedia failed", t);
            return null;
        }
    }

    private static long[] videoMeta(String path) {
        long w = 0, h = 0, duration = 0;
        try {
            android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
            r.setDataSource(path);
            String sw = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String sh = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            String sd = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (sw != null) w = Long.parseLong(sw);
            if (sh != null) h = Long.parseLong(sh);
            if (sd != null) duration = Long.parseLong(sd);
            r.release();
        } catch (Throwable ignored) {
        }
        return new long[]{w, h, duration};
    }

    private static TLRPC.TL_document buildLocalDocument(String path, String name, String mime,
                                                        boolean video, boolean animated,
                                                        int w, int h, double durationSec) {
        TLRPC.TL_document doc = new TLRPC.TL_document();
        doc.id = Integer.MIN_VALUE;
        doc.access_hash = 0;
        doc.date = (int) (System.currentTimeMillis() / 1000);
        doc.mime_type = mime;
        doc.size = new File(path).length();
        doc.dc_id = Integer.MIN_VALUE;
        doc.file_reference = new byte[0];
        // The attachment already exists on disk; without this FileLoader treats the
        // document as a remote file and the bubble shows a failed 0 KB download.
        doc.localPath = path;
        doc.attributes = new ArrayList<>();
        TLRPC.TL_documentAttributeFilename fn = new TLRPC.TL_documentAttributeFilename();
        fn.file_name = name;
        doc.attributes.add(fn);
        if (video) {
            TLRPC.TL_documentAttributeVideo v = new TLRPC.TL_documentAttributeVideo();
            v.supports_streaming = true;
            v.duration = durationSec;
            v.w = w;
            v.h = h;
            doc.attributes.add(v);
        }
        if (animated) {
            doc.attributes.add(new TLRPC.TL_documentAttributeAnimated());
        }
        return doc;
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
        handleOutgoingText(dialogId, text, null);
    }

    private static void handleOutgoingText(long dialogId, String text, MessageObject replyTo) {
        String quoted = null;
        if (replyTo != null && replyTo.messageOwner != null) {
            quoted = replyTo.messageOwner.message;
            if (quoted == null || quoted.trim().isEmpty()) quoted = "（媒体消息）";
        }
        try {
            TLRPC.TL_message msg = new TLRPC.TL_message();
            msg.flags = 256;
            msg.id = nextLocalMessageId();
            msg.local_id = msg.id;
            msg.out = true;
            msg.date = (int) (System.currentTimeMillis() / 1000);
            msg.message = text;
            if (replyTo != null) {
                msg.flags |= TLRPC.MESSAGE_FLAG_REPLY;
                TLRPC.TL_messageReplyHeader header = new TLRPC.TL_messageReplyHeader();
                header.flags |= 16;
                header.reply_to_msg_id = replyTo.getId();
                msg.reply_to = header;
            }
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
        if (quoted != null) {
            if (quoted.length() > 300) quoted = quoted.substring(0, 300) + "…";
            text = "（回复：" + quoted + "）\n" + text;
        }
        if (agent.isEchoMode()) {
            StringBuilder sb = new StringBuilder();
            sb.append("⚡ 离线回声模式（未配置模型后端）\n\n你说：").append(text).append('\n');
            sb.append("\n👉 打开 智能体 管理 → 长按 → 编辑设置，填写接口地址 / API Key / 模型后即可接入真实模型与工具调用。");
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
        long senderTgId;
        boolean group;
        OxAgent agent;
        JSONArray messages;
        int depth;
        String userText;
        String imagePath;
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
                for (Stream stream : active.values()) {
                    setPrintingUser(stream.dialogId, stream.senderTgId, now);
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

    private static void setPrintingUser(long dialogId, long senderTgId, int now) {
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
            pu.userId = senderTgId;
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

    private static void startTyping(long dialogId, long senderTgId) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                setPrintingUser(dialogId, senderTgId, ConnectionsManager.getInstance(0).getCurrentTime());
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
        startTurn(agent, dialogId, userText, null);
    }

    private static void startTurn(OxAgent agent, long dialogId, String userText, String imagePath) {
        log("startTurn agent=" + agent.id + " dialog=" + dialogId + " image=" + (imagePath != null));
        Stream s = new Stream();
        s.dialogId = dialogId;
        s.senderTgId = dialogId;
        s.group = false;
        s.agent = agent;
        s.userText = userText;
        s.imagePath = imagePath;
        try {
            s.messages = baseMessages(agent, agent.id);
            if (imagePath != null && new File(imagePath).exists()) {
                s.messages.put(new JSONObject().put("role", "user").put("content", visionContent(userText, imagePath)));
            } else {
                s.messages.put(new JSONObject().put("role", "user").put("content", userText));
            }
        } catch (Exception e) {
            injectAgentReply(dialogId, "⚠ " + e.getMessage());
            return;
        }
        active.put(dialogId, s);
        startTyping(dialogId, s.senderTgId);
        runRound(s, 0);
    }

    // ---------------- group turns ----------------

    /** Persists an outgoing text in a locally created group and triggers its agent. */
    public static void sendLocalGroupText(long dialogId, String text, MessageObject replyTo) {
        if (dialogId >= 0 || text == null || text.trim().isEmpty()) return;
        String clean = text.trim();
        try {
            TLRPC.TL_message msg = new TLRPC.TL_message();
            msg.flags = 256;
            msg.id = nextLocalMessageId();
            msg.local_id = msg.id;
            msg.out = true;
            msg.date = (int) (System.currentTimeMillis() / 1000);
            msg.message = clean;
            if (replyTo != null && replyTo.messageOwner != null) {
                msg.flags |= TLRPC.MESSAGE_FLAG_REPLY;
                TLRPC.TL_messageReplyHeader header = new TLRPC.TL_messageReplyHeader();
                header.flags |= 16;
                header.reply_to_msg_id = replyTo.getId();
                msg.reply_to = header;
            }
            msg.peer_id = new TLRPC.TL_peerChat();
            msg.peer_id.chat_id = -dialogId;
            msg.from_id = new TLRPC.TL_peerUser();
            msg.from_id.user_id = SELF_ID;
            msg.dialog_id = dialogId;
            msg.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
            msg.media = new TLRPC.TL_messageMediaEmpty();
            persistAndShow(dialogId, msg);
        } catch (Throwable t) {
            logErr("sendLocalGroupText persist failed", t);
        }
        onGroupMessage(dialogId, clean, replyTo);
    }

    /** Triggered for an outgoing message in a group that has local Ox agents. */
    public static void onGroupMessage(long dialogId, String text, MessageObject replyTo) {
        try {
            if (dialogId >= 0 || text == null || text.trim().isEmpty()) return;
            List<OxAgent> agents = OxGroupStore.agents(dialogId);
            if (agents.isEmpty()) return;
            OxAgent chosen = null;
            if (replyTo != null) {
                long sender = replyTo.getSenderId();
                for (OxAgent a : agents) {
                    if (a.tgId == sender) { chosen = a; break; }
                }
            }
            String lower = text.toLowerCase();
            if (chosen == null) {
                for (OxAgent a : agents) {
                    String name = a.name == null ? "" : a.name.toLowerCase();
                    if (!name.isEmpty() && (lower.contains("@" + name) || lower.contains(name))) {
                        chosen = a;
                        break;
                    }
                }
            }
            if (chosen == null) chosen = agents.get(0);
            startGroupTurn(chosen, dialogId, text.trim());
        } catch (Throwable t) {
            logErr("onGroupMessage failed", t);
        }
    }

    private static void startGroupTurn(OxAgent agent, long dialogId, String userText) {
        Stream s = new Stream();
        s.dialogId = dialogId;
        s.senderTgId = agent.tgId;
        s.group = true;
        s.agent = agent;
        s.userText = userText;
        try {
            s.messages = groupMessages(agent, dialogId, userText);
        } catch (Exception e) {
            injectAgentReply(dialogId, agent.tgId, "⚠ " + e.getMessage());
            return;
        }
        OxGroupStore.appendChat(dialogId, "user", userText);
        active.put(dialogId, s);
        startTyping(dialogId, agent.tgId);
        runRound(s, 0);
    }

    private static JSONArray groupMessages(OxAgent agent, long dialogId, String userText) throws Exception {
        JSONArray messages = new JSONArray();
        StringBuilder system = new StringBuilder();
        if (agent.agentsMd != null) system.append(agent.agentsMd.trim());
        TLRPC.Chat chat = MessagesController.getInstance(0).getChat(-dialogId);
        String title = chat == null || chat.title == null ? "群聊" : chat.title;
        if (system.length() > 0) system.append("\n\n");
        system.append("# 群聊模式\n你正在群聊「").append(title)
                .append("」中以普通成员身份发言。只回复与当前消息相关的内容，称呼用户时保持自然；");
        system.append("不要提及自己是本地合成的账号。");
        String memory = OxAgentStore.loadMemory(agent.id, 4000);
        if (!memory.isEmpty()) {
            system.append("\n\n# 长期记忆\n").append(memory);
        }
        appendSkills(system, agent);
        messages.put(new JSONObject().put("role", "system").put("content", system.toString()));
        for (OxChatEntry e : OxGroupStore.loadChat(dialogId)) {
            if (e.content == null || e.content.trim().isEmpty()) continue;
            messages.put(new JSONObject().put("role", e.role).put("content", e.content));
        }
        messages.put(new JSONObject().put("role", "user").put("content", userText));
        return messages;
    }

    /** Injects the text of the Operit SKILL.md packages the user enabled for this agent. */
    private static void appendSkills(StringBuilder system, OxAgent agent) {
        if (agent == null || agent.skillIds == null || agent.skillIds.length() == 0) return;
        int budget = 8000;
        for (int i = 0; i < agent.skillIds.length() && budget > 0; i++) {
            String id = agent.skillIds.optString(i, "");
            if (id.isEmpty()) continue;
            for (OxSkillStore.Skill skill : OxSkillStore.list()) {
                if (!id.equals(skill.id) && !id.equals(skill.name)) continue;
                String content = OxSkillStore.read(skill.name);
                if (content == null || content.isEmpty() || content.startsWith("error:")) continue;
                if (content.length() > Math.min(4000, budget)) {
                    content = content.substring(0, Math.min(4000, budget));
                }
                if (system.length() > 0) system.append("\n\n");
                system.append("# 技能：").append(skill.name).append('\n').append(content);
                budget -= content.length();
                break;
            }
        }
    }

    private static JSONArray baseMessages(OxAgent agent, String agentId) throws Exception {
        JSONArray messages = new JSONArray();
        StringBuilder system = new StringBuilder();
        if (agent.agentsMd != null) system.append(agent.agentsMd.trim());
        String memory = OxAgentStore.loadMemory(agent.id, 6000);
        if (!memory.isEmpty()) {
            if (system.length() > 0) system.append("\n\n");
            system.append("# 长期记忆（用户允许你记住的事实）\n").append(memory);
        }
        appendSkills(system, agent);
        if (system.length() > 0) {
            messages.put(new JSONObject().put("role", "system").put("content", system.toString()));
        }
        List<OxChatEntry> history = OxAgentStore.loadChat(agentId);
        int from = Math.max(0, history.size() - 20);
        if (agent.contextLength > 0) {
            int budget = Math.max(4000, agent.contextLength * 2);
            int chars = 0;
            int start = history.size();
            for (int i = history.size() - 1; i >= 0 && history.size() - i <= 60; i--) {
                OxChatEntry e = history.get(i);
                int c = e.imagePath != null ? 2000 : (e.content == null ? 0 : e.content.length());
                if (chars + c > budget && start < history.size()) break;
                chars += c;
                start = i;
            }
            from = start;
        }
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
            injectAgentReply(s.dialogId, s.senderTgId, "🔧 " + call.name + "(" + argsPretty + ") …");
            new Thread(() -> {
                String result;
                try {
                    JSONObject args = call.arguments == null || call.arguments.trim().isEmpty()
                            ? new JSONObject() : new JSONObject(call.arguments);
                    result = tool.executeWithAgent(args, s.agent);
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
    private static void persistAndShow(long dialogId, TLRPC.Message msg) {
        final boolean out = msg.out;
        ArrayList<TLRPC.Message> arr = new ArrayList<>();
        arr.add(msg);
        try {
            MessagesStorage.getInstance(0).putMessages(arr, false, true, false, 0, 0, 0);
        } catch (Throwable t) {
            logErr("putMessages failed did=" + dialogId, t);
        }
        if (dialogId >= 0) {
            refreshDialogRow(dialogId);
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                MessageObject obj = new MessageObject(0, msg, true, false);
                if (out) {
                    obj.wasJustSent = true;
                }
                if (msg.attachPath != null && !msg.attachPath.isEmpty()
                        && new File(msg.attachPath).exists()) {
                    // The file is already on disk; without these the freshly built
                    // cell can still render a download button until the chat reloads.
                    obj.attachPathExists = true;
                    obj.mediaExists = true;
                }
                if (msg.media != null && !(msg.media instanceof TLRPC.TL_messageMediaEmpty)) {
                    log("persistAndShow media type=" + obj.type + " mediaClass=" + msg.media.getClass().getSimpleName()
                            + " photoNonNull=" + (msg.media.photo != null)
                            + " sizes=" + (msg.media.photo != null && msg.media.photo.sizes != null ? msg.media.photo.sizes.size() : -1));
                }
                ArrayList<MessageObject> objArr = new ArrayList<>();
                objArr.add(obj);
                MessagesController.getInstance(0).updateInterfaceWithMessages(dialogId, objArr, 0);
                // A stock ChatActivity only appends live messages when it sees this notification;
                // without it the local agent conversation updates in the list but not on screen.
                NotificationCenter.getInstance(0).postNotificationName(NotificationCenter.didReceiveNewMessages,
                        dialogId, objArr, false, 0);
                NotificationCenter.getInstance(0).postNotificationName(NotificationCenter.dialogsNeedReload);
                log("persistAndShow shown did=" + dialogId + " id=" + msg.id + " out=" + out);
            } catch (Throwable t) {
                logErr("persistAndShow UI failed did=" + dialogId, t);
            }
        });
    }

    /**
     * After importing an Operit/Operit2 chat into the Ox store, replace the synthetic
     * dialog's native messages with that history so the stock ChatActivity shows it.
     */
    public static void replayImportedHistory(final String agentId) {
        final OxAgent a = OxAgentStore.loadAgent(agentId);
        if (a == null || a.tgId == 0) return;
        final long did = a.tgId;
        final java.util.List<OxChatEntry> chat = OxAgentStore.loadChat(agentId);
        synchronized (GREET_LOCK) {
            GREETED.add(did);   // do not add a second greeting on top of imported history
        }
        MessagesStorage.getInstance(0).getStorageQueue().postRunnable(() -> {
            try {
                MessagesStorage.getInstance(0).getDatabase().executeFast(
                        "DELETE FROM messages_v2 WHERE uid = " + did).stepThis().dispose();
                MessagesStorage.getInstance(0).getDatabase().executeFast(
                        "DELETE FROM dialogs WHERE did = " + did).stepThis().dispose();
            } catch (Throwable t) {
                logErr("replayImportedHistory clear failed did=" + did, t);
            }
            AndroidUtilities.runOnUIThread(() -> {
                for (OxChatEntry e : chat) {
                    if (e == null || e.content == null || e.content.trim().isEmpty()) continue;
                    boolean out = "user".equals(e.role);
                    TLRPC.TL_message msg = baseOutgoing(did);
                    msg.out = out;
                    msg.date = e.ts > 0 ? (int) (e.ts / 1000) : (int) (System.currentTimeMillis() / 1000);
                    msg.message = e.content;
                    msg.media = new TLRPC.TL_messageMediaEmpty();
                    msg.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                    msg.from_id = new TLRPC.TL_peerUser();
                    msg.from_id.user_id = out ? SELF_ID : did;
                    if (!out) msg.unread = false;
                    persistAndShow(did, msg);
                }
                NotificationCenter.getInstance(0).postNotificationName(NotificationCenter.dialogsNeedReload);
                log("replayImportedHistory did=" + did + " entries=" + chat.size());
            });
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
        injectAgentReply(dialogId, dialogId, text);
    }

    public static void injectAgentReply(long dialogId, long senderTgId, String text) {
        try {
            TLRPC.TL_message msg = new TLRPC.TL_message();
            msg.flags = 256;
            msg.id = nextLocalMessageId();
            msg.local_id = msg.id;
            msg.out = false;
            msg.date = (int) (System.currentTimeMillis() / 1000);
            msg.message = text;
            setPeerAndSender(msg, dialogId, senderTgId);
            msg.unread = false;
            msg.media = new TLRPC.TL_messageMediaEmpty();
            persistAndShow(dialogId, msg);
        } catch (Throwable t) {
            logErr("injectAgentReply failed", t);
        }
    }

    /** Peer for a private user dialog or a basic group / channel dialog. */
    private static TLRPC.Peer peerFor(long dialogId) {
        if (dialogId < 0) {
            if (dialogId <= -1000000000000L) {
                TLRPC.TL_peerChannel peer = new TLRPC.TL_peerChannel();
                peer.channel_id = -dialogId - 1000000000000L;
                return peer;
            }
            TLRPC.TL_peerChat peer = new TLRPC.TL_peerChat();
            peer.chat_id = -dialogId;
            return peer;
        }
        TLRPC.TL_peerUser peer = new TLRPC.TL_peerUser();
        peer.user_id = dialogId;
        return peer;
    }

    private static void setPeerAndSender(TLRPC.TL_message msg, long dialogId, long senderTgId) {
        msg.peer_id = peerFor(dialogId);
        msg.from_id = new TLRPC.TL_peerUser();
        msg.from_id.user_id = senderTgId;
        msg.dialog_id = dialogId;
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
                s.msg.id = nextLocalMessageId();
                s.msg.local_id = s.msg.id;
                s.msg.out = false;
                s.msg.date = (int) (System.currentTimeMillis() / 1000);
                setPeerAndSender(s.msg, s.dialogId, s.senderTgId);
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
                    // stock ChatActivity inserts a live message only from this notification
                    NotificationCenter.getInstance(0).postNotificationName(NotificationCenter.didReceiveNewMessages,
                            s.dialogId, objArr, false, 0);
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
            injectAgentReply(s.dialogId, s.senderTgId, finalText);
        }
        // persist the turn: groups keep their own log so group lines do not leak
        // into the agent's one-to-one history
        try {
            if (s.group) {
                OxGroupStore.appendChat(s.dialogId, "assistant", finalText);
            } else {
                List<OxChatEntry> chat = OxAgentStore.loadChat(s.agent.id);
                chat.add(new OxChatEntry("user", s.userText, System.currentTimeMillis(), s.imagePath));
                chat.addAll(s.acc);
                chat.add(new OxChatEntry("assistant", finalText, System.currentTimeMillis()));
                OxAgentStore.saveChat(s.agent.id, chat);
            }
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

    /** Removes agent-range dialogs/messages whose agent no longer exists (e.g. after a data wipe). */
    private static void pruneOrphanAgentDialogs(String liveIdsCsv) {
        MessagesStorage.getInstance(0).getStorageQueue().postRunnable(() -> {
            try {
                String keep = liveIdsCsv == null || liveIdsCsv.isEmpty() ? "-1" : liveIdsCsv;
                MessagesStorage.getInstance(0).getDatabase().executeFast(
                        "DELETE FROM dialogs WHERE did >= " + AGENT_BASE + " AND did < " + (AGENT_BASE + AGENT_RANGE)
                                + " AND did NOT IN (" + keep + ")").stepThis().dispose();
                MessagesStorage.getInstance(0).getDatabase().executeFast(
                        "DELETE FROM messages_v2 WHERE uid >= " + AGENT_BASE + " AND uid < " + (AGENT_BASE + AGENT_RANGE)
                                + " AND uid NOT IN (" + keep + ")").stepThis().dispose();
                log("pruned orphan agent dialogs, keep=" + keep);
            } catch (Throwable t) {
                logErr("pruneOrphanAgentDialogs failed", t);
            }
        });
    }

    // ---------------- exit to real login ----------------

    /** Leaves agent-only mode: local-only logout (no network call). */
    public static void exitAgentOnly() {
        OxMode.setAgentOnly(false);
        accountReady = false;
        try {
            // Restore MTProto networking before the login screen takes over.
            ConnectionsManager.getInstance(0).checkConnection();
        } catch (Throwable ignored) {
        }
        try {
            MessagesController.getInstance(0).performLogout(0);
        } catch (Throwable t) {
            logErr("exitAgentOnly failed", t);
        }
    }
}
