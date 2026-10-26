package org.telegram.ox;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Local membership of Ox agents in ordinary Telegram groups. Telegram's servers do
 * not know synthetic agent users, so membership is kept on-device and merged into
 * the stock participant list whenever a full chat is loaded.
 */
public class OxGroupStore {

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("ox_groups", Context.MODE_PRIVATE);
    }

    /** Positive chat/channel id; accepts the negative dialog id too. */
    private static long key(long id) {
        if (id < 0) {
            if (id <= -1000000000000L) return -id - 1000000000000L;
            return -id;
        }
        return id;
    }

    public static synchronized List<String> agentIds(long chatId) {
        String raw = prefs().getString("chat_" + key(chatId), "");
        ArrayList<String> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return out;
        for (String part : raw.split(",")) {
            if (!part.trim().isEmpty() && !out.contains(part.trim())) out.add(part.trim());
        }
        return out;
    }

    public static synchronized List<OxAgent> agents(long chatId) {
        ArrayList<OxAgent> out = new ArrayList<>();
        for (String id : agentIds(chatId)) {
            OxAgent a = OxAgentStore.loadAgent(id);
            if (a != null) out.add(a);
        }
        return out;
    }

    public static synchronized boolean hasAgents(long chatId) {
        return !agentIds(chatId).isEmpty();
    }

    public static synchronized void addAgent(long chatId, String agentId) {
        if (agentId == null || agentId.isEmpty()) return;
        LinkedHashSet<String> ids = new LinkedHashSet<>(agentIds(chatId));
        ids.add(agentId);
        prefs().edit().putString("chat_" + key(chatId), join(ids)).apply();
    }

    public static synchronized void removeAgent(long chatId, String agentId) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(agentIds(chatId));
        ids.remove(agentId);
        prefs().edit().putString("chat_" + key(chatId), join(ids)).apply();
    }

    public static synchronized List<Long> chatIds() {
        ArrayList<Long> out = new ArrayList<>();
        try {
            for (String key : prefs().getAll().keySet()) {
                if (key == null || !key.startsWith("chat_")) continue;
                try {
                    out.add(Long.parseLong(key.substring(5)));
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static synchronized boolean isLocalGroup(long chatId) {
        return prefs().contains("chat_" + key(chatId));
    }

    /** Recent conversation log for the group, capped at 40 entries. */
    public static synchronized List<OxChatEntry> loadChat(long chatId) {
        ArrayList<OxChatEntry> out = new ArrayList<>();
        try {
            String raw = prefs().getString("log_" + key(chatId), "");
            if (raw != null && !raw.isEmpty()) {
                out.addAll(OxChatEntry.fromJsonArray(new JSONArray(raw)));
            }
        } catch (Throwable t) {
            FileLog.e("OxGroupStore.loadChat", t);
        }
        return out;
    }

    public static synchronized void appendChat(long chatId, String role, String content) {
        appendChat(chatId, role, content, null);
    }

    public static synchronized void appendChat(long chatId, String role, String content, String imagePath) {
        if (content == null || content.trim().isEmpty()) return;
        try {
            List<OxChatEntry> chat = loadChat(chatId);
            chat.add(new OxChatEntry(role, content, System.currentTimeMillis(), imagePath));
            while (chat.size() > 40) chat.remove(0);
            prefs().edit().putString("log_" + key(chatId),
                    OxChatEntry.toJsonArray(chat).toString()).apply();
        } catch (Throwable t) {
            FileLog.e("OxGroupStore.appendChat", t);
        }
    }

    /** Most recent image sent in this group, used when a later turn refers to it. */
    public static synchronized String lastImage(long chatId) {
        List<OxChatEntry> chat = loadChat(chatId);
        for (int i = chat.size() - 1; i >= 0; i--) {
            OxChatEntry e = chat.get(i);
            if (e.imagePath != null && !e.imagePath.isEmpty() && new java.io.File(e.imagePath).isFile()) {
                return e.imagePath;
            }
        }
        return null;
    }

    public static synchronized void clearChat(long chatId) {
        prefs().edit().remove("chat_" + key(chatId)).remove("log_" + key(chatId)).apply();
    }

    private static String join(Iterable<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        return sb.toString();
    }
}
