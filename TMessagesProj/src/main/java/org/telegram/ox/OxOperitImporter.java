package org.telegram.ox;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Migrates Operit (v1) and Operit2 backups into Ox agents/chats/MCP entries.
 *
 * Reads the public export locations:
 *   Download/Operit/backup/character_cards/*.json
 *   Download/Operit/backup/chat/*.json
 *   Download/Operit/mcp_plugins/mcp_config.json
 *   Download/Operit2/backup/*.zip (Operit2 snapshot)
 * and never touches another app's private data.
 */
public class OxOperitImporter {

    public static class Result {
        public int agents;
        public int messages;
        public int mcpServers;
        public int memoryFiles;
        public final List<String> notes = new ArrayList<>();
        public final List<String> importedAgentIds = new ArrayList<>();
        public String error;

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("角色卡 ").append(agents).append(" · 消息 ").append(messages)
                    .append(" · MCP ").append(mcpServers).append(" · 记忆 ").append(memoryFiles);
            for (String n : notes) sb.append("\n• ").append(n);
            if (error != null) sb.append("\n⚠ ").append(error);
            return sb.toString();
        }
    }

    private static class Provider {
        String baseUrl;
        String apiKey;
        String model;
        int context = 0;
        int maxTokens = 0;
        String providerId = "custom";
    }

    public static boolean storageReady() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        return true;
    }

    public static Result importAll() {
        Result r = new Result();
        try {
            Provider provider = readOperit2Provider();
            if (provider == null) provider = existingProvider();
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            Provider finalProvider = provider;
            Set<String> importedNames = new HashSet<>();

            // ---- Operit v1 ----
            File op = new File(downloads, "Operit");
            File cardsDir = new File(new File(new File(op, "backup"), "character_cards"), "");
            File chatDir = new File(new File(new File(op, "backup"), "chat"), "");
            Map<String, OxAgent> byName = new HashMap<>();
            for (File f : listJson(cardsDir)) {
                importOperitCards(f, finalProvider, r, byName, importedNames);
            }
            for (File f : listJson(chatDir)) {
                importOperitChats(f, byName, r);
            }
            importOperitMcp(new File(op, "mcp_plugins/mcp_config.json"), r);

            // ---- Operit2 snapshot ----
            File op2 = new File(downloads, "Operit2");
            List<File> zips = new ArrayList<>();
            collect(zips, op2, ".zip", 3);
            if (!zips.isEmpty()) {
                importOperit2Snapshot(zips.get(zips.size() - 1), finalProvider, r, byName);
            } else {
                r.notes.add("未找到 Operit2 快照 zip（Download/Operit2/backup）");
            }

            if (r.agents == 0 && r.messages == 0 && r.mcpServers == 0 && r.error == null) {
                r.error = "没有找到可导入的 Operit / Operit2 备份";
            }
        } catch (Throwable t) {
            FileLog.e("OxOperitImporter", t);
            r.error = t.getMessage();
        }
        return r;
    }

    private static List<File> listJson(File dir) {
        List<File> out = new ArrayList<>();
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isFile() && f.getName().endsWith(".json")) out.add(f);
            }
        }
        out.sort((a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        return out;
    }

    private static void collect(List<File> out, File dir, String suffix, int depth) {
        if (dir == null || depth < 0 || !dir.exists()) return;
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) collect(out, f, suffix, depth - 1);
            else if (f.getName().toLowerCase().endsWith(suffix)) out.add(f);
        }
        out.sort((a, b) -> Long.compare(a.lastModified(), b.lastModified()));
    }

    private static void importOperitCards(File f, Provider provider, Result r,
                                          Map<String, OxAgent> byName, Set<String> imported) {
        try {
            JSONObject root = new JSONObject(readText(f));
            JSONArray arr = root.optJSONArray("characterCards");
            if (arr == null) return;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject c = arr.getJSONObject(i);
                String name = c.optString("name", "Operit Agent");
                if (c.optBoolean("isDefault", false)) {
                    continue;   // skip Operit's stock card
                }
                String setting = c.optString("characterSetting", "");
                String desc = c.optString("description", "");
                String otherChat = c.optString("otherContentChat", "");
                String otherVoice = c.optString("otherContentVoice", "");
                String advanced = c.optString("advancedCustomPrompt", "");
                String opening = c.optString("openingStatement", "");
                String md = persona(desc, setting, otherChat, otherVoice, advanced);
                String key = "operit:" + c.optString("id", name) + ":" + name;
                if (imported.contains(key)) continue;
                OxAgent a = createAgent(name, md, opening, provider, "operit");
                byName.put(name.toLowerCase(), a);
                imported.add(key);
                r.importedAgentIds.add(a.id);
                r.agents++;
                r.notes.add("Operit 角色卡：" + name);
            }
        } catch (Throwable t) {
            r.notes.add("读取 Operit 角色卡失败：" + f.getName());
        }
    }

    private static void importOperitChats(File f, Map<String, OxAgent> byName, Result r) {
        try {
            JSONObject root = new JSONObject(readText(f));
            JSONArray chats = root.optJSONArray("chats");
            if (chats == null) return;
            for (int i = 0; i < chats.length(); i++) {
                JSONObject chat = chats.getJSONObject(i);
                String charName = chat.optString("characterCardName", "");
                OxAgent a = byName.get(charName.toLowerCase());
                if (a == null) {
                    // try any agent whose name is a prefix/suffix
                    for (OxAgent cand : byName.values()) {
                        if (cand.name != null && (cand.name.equalsIgnoreCase(charName)
                                || charName.toLowerCase().contains(cand.name.toLowerCase()))) {
                            a = cand;
                            break;
                        }
                    }
                }
                if (a == null) {
                    r.notes.add("跳过没有对应角色卡的 Operit 会话：" + chat.optString("title", ""));
                    continue;
                }
                List<OxChatEntry> entries = OxAgentStore.loadChat(a.id);
                JSONArray msgs = chat.optJSONArray("messages");
                int start = entries.size();
                for (int m = 0; m < (msgs == null ? 0 : msgs.length()); m++) {
                    JSONObject base = msgs.getJSONObject(m).optJSONObject("baseMessage");
                    if (base == null) base = msgs.getJSONObject(m);
                    String sender = base.optString("sender", "user");
                    String content = clean(base.optString("content", ""));
                    if (content.isEmpty()) continue;
                    String role = "ai".equalsIgnoreCase(sender) || "assistant".equalsIgnoreCase(sender)
                            || "model".equalsIgnoreCase(sender) ? "assistant" : "user";
                    long ts = base.optLong("timestamp", System.currentTimeMillis());
                    entries.add(new OxChatEntry(role, content, ts));
                }
                // remove exact duplicates introduced by repeated imports
                List<OxChatEntry> dedup = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (OxChatEntry e : entries) {
                    String k = e.role + "\u0000" + e.content + "\u0000" + e.ts;
                    if (seen.add(k)) dedup.add(e);
                }
                OxAgentStore.saveChat(a.id, dedup);
                r.messages += Math.max(0, dedup.size() - start);
            }
        } catch (Throwable t) {
            r.notes.add("读取 Operit 聊天失败：" + f.getName());
        }
    }

    private static void importOperitMcp(File f, Result r) {
        if (!f.exists()) return;
        try {
            JSONObject root = new JSONObject(readText(f));
            JSONObject meta = root.optJSONObject("pluginMetadata");
            if (meta != null) {
                JSONArray names = meta.names();
                for (int i = 0; names != null && i < names.length(); i++) {
                    String id = names.getString(i);
                    JSONObject m = meta.getJSONObject(id);
                    String url = m.optString("endpoint", "");
                    if (url.isEmpty()) continue;
                    if (addMcp(m.optString("name", id), url, m.optString("headers", ""))) r.mcpServers++;
                }
            }
            JSONObject servers = root.optJSONObject("mcpServers");
            if (servers != null) {
                JSONArray names = servers.names();
                for (int i = 0; names != null && i < names.length(); i++) {
                    String id = names.getString(i);
                    JSONObject s = servers.getJSONObject(id);
                    String url = s.optString("url", "");
                    if (url.isEmpty()) continue;
                    if (addMcp(id, url, s.optString("headers", ""))) r.mcpServers++;
                }
            }
        } catch (Throwable t) {
            r.notes.add("读取 Operit MCP 失败");
        }
    }

    private static void importOperit2Snapshot(File zip, Provider provider, Result r, Map<String, OxAgent> byName) {
        File tmp = new File(ApplicationLoader.applicationContext.getCacheDir(), "op2import_" + System.currentTimeMillis());
        tmp.mkdirs();
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry e;
            byte[] buf = new byte[16384];
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = e.getName();
                if (!(name.endsWith(".json") || name.endsWith(".sqlite") || name.endsWith(".md")
                        || name.endsWith(".jsonl"))) continue;
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                int n;
                while ((n = zin.read(buf)) > 0) bos.write(buf, 0, n);
                files.put(name, bos.toByteArray());
            }
        } catch (Throwable t) {
            r.notes.add("读取 Operit2 快照失败：" + t.getMessage());
            return;
        }

        Map<String, OxAgent> byCharacterId = new HashMap<>();
        String charJson = findEnding(files, "character_cards.preferences.json");
        if (charJson != null) {
            try {
                JSONObject o = new JSONObject(charJson);
                JSONArray list = new JSONArray(o.optString("character_card_list", "[]"));
                for (int i = 0; i < list.length(); i++) {
                    String id = list.getString(i);
                    if ("default_character".equals(id)) continue;
                    String p = "character_card_" + id + "_";
                    String name = o.optString(p + "name", "Operit2 Agent");
                    String setting = o.optString(p + "character_setting", "");
                    String desc = o.optString(p + "description", "");
                    String otherChat = o.optString(p + "other_content_chat", "");
                    String otherVoice = o.optString(p + "other_content_voice", "");
                    String advanced = o.optString(p + "advanced_custom_prompt", "");
                    String opening = o.optString(p + "opening_statement", "");
                    OxAgent a = createAgent(name, persona(desc, setting, otherChat, otherVoice, advanced),
                            opening, provider, "operit2");
                    byName.put(name.toLowerCase(), a);
                    byCharacterId.put(id, a);
                    r.importedAgentIds.add(a.id);
                    r.agents++;
                    r.notes.add("Operit2 角色卡：" + name);
                }
            } catch (Throwable t) {
                r.notes.add("读取 Operit2 角色卡失败");
            }
        }

        String modelJson = findEnding(files, "model_configs.preferences.json");
        Provider snapshotProvider = parseOperit2Provider(modelJson);
        if (snapshotProvider != null) {
            // apply the snapshot's provider to all newly imported agents, even if the
            // global template was unavailable
            for (OxAgent a : byName.values()) {
                if (a.baseUrl == null || a.baseUrl.isEmpty()) {
                    applyProvider(a, snapshotProvider);
                    OxAgentStore.saveAgent(a);
                }
            }
        }

        byte[] db = null;
        String dbName = null;
        for (Map.Entry<String, byte[]> en : files.entrySet()) {
            if (en.getKey().endsWith("operit2.sqlite")) {
                db = en.getValue();
                dbName = en.getKey();
                break;
            }
        }
        if (db != null) {
            File dbFile = new File(tmp, "operit2.sqlite");
            try (FileOutputStream out = new FileOutputStream(dbFile)) {
                out.write(db);
            } catch (Throwable ignored) {
            }
            importOperit2Db(dbFile, byName, r);
        }

        String mcpJson = findEnding(files, "mcp_config.json");
        if (mcpJson != null) {
            try {
                JSONObject root = new JSONObject(mcpJson);
                JSONObject servers = root.optJSONObject("mcpServers");
                JSONArray names = servers == null ? null : servers.names();
                for (int i = 0; names != null && i < names.length(); i++) {
                    String id = names.getString(i);
                    JSONObject s = servers.getJSONObject(id);
                    String url = s.optString("url", "");
                    if (url.isEmpty()) continue;
                    if (addMcp(id, url, s.optString("headers", ""))) r.mcpServers++;
                }
            } catch (Throwable t) {
                r.notes.add("读取 Operit2 MCP 失败");
            }
        }

        for (Map.Entry<String, byte[]> en : files.entrySet()) {
            if (!en.getKey().endsWith("USER.md")) continue;
            String text = new String(en.getValue(), StandardCharsets.UTF_8);
            String path = en.getKey();
            OxAgent owner = null;
            for (Map.Entry<String, OxAgent> e2 : byCharacterId.entrySet()) {
                if (path.contains(e2.getKey())) {
                    owner = e2.getValue();
                    break;
                }
            }
            if (owner != null) {
                String line = text.trim().replace('\n', ' ');
                if (!line.isEmpty()) {
                    String existing = OxAgentStore.loadMemory(owner.id, 200000);
                    if (!existing.contains(line)) {
                        OxAgentStore.appendMemory(owner.id, text);
                        r.memoryFiles++;
                    }
                }
            }
        }
        deleteTree(tmp);
    }

    private static void importOperit2Db(File dbFile, Map<String, OxAgent> byName, Result r) {
        SQLiteDatabase db = null;
        Cursor c = null;
        try {
            db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            c = db.rawQuery("SELECT id,title,characterCardName FROM chats ORDER BY createdAt ASC", null);
            Map<String, String> chatAgent = new HashMap<>();
            Map<String, String> chatTitle = new HashMap<>();
            while (c.moveToNext()) {
                chatAgent.put(c.getString(0), c.getString(2) == null ? "" : c.getString(2));
                chatTitle.put(c.getString(0), c.getString(1) == null ? "" : c.getString(1));
            }
            c.close();
            c = db.rawQuery("SELECT chatId,sender,content,timestamp FROM messages ORDER BY timestamp ASC", null);
            while (c.moveToNext()) {
                String chatId = c.getString(0);
                String charName = chatAgent.get(chatId);
                OxAgent a = charName == null ? null : byName.get(charName.toLowerCase());
                if (a == null) {
                    for (OxAgent cand : byName.values()) {
                        if (cand.name != null && charName != null
                                && charName.toLowerCase().contains(cand.name.toLowerCase())) {
                            a = cand;
                            break;
                        }
                    }
                }
                if (a == null) continue;
                String sender = c.getString(1);
                String content = clean(c.getString(2));
                if (content.isEmpty()) continue;
                String role = "ai".equalsIgnoreCase(sender) || "assistant".equalsIgnoreCase(sender)
                        ? "assistant" : "user";
                long ts = c.getLong(3);
                List<OxChatEntry> entries = OxAgentStore.loadChat(a.id);
                boolean dup = false;
                for (OxChatEntry e : entries) {
                    if (e.ts == ts && e.role.equals(role) && e.content.equals(content)) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) {
                    entries.add(new OxChatEntry(role, content, ts));
                    OxAgentStore.saveChat(a.id, entries);
                    r.messages++;
                }
            }
        } catch (Throwable t) {
            r.notes.add("读取 Operit2 聊天数据库失败：" + t.getMessage());
        } finally {
            if (c != null) try { c.close(); } catch (Throwable ignored) {}
            if (db != null) try { db.close(); } catch (Throwable ignored) {}
        }
    }

    private static Provider readOperit2Provider() {
        try {
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File op2 = new File(downloads, "Operit2");
            List<File> zips = new ArrayList<>();
            collect(zips, op2, ".zip", 3);
            if (zips.isEmpty()) return null;
            try (ZipInputStream zin = new ZipInputStream(new FileInputStream(zips.get(zips.size() - 1)))) {
                ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    if (e.getName().endsWith("model_configs.preferences.json")) {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = zin.read(buf)) > 0) bos.write(buf, 0, n);
                        return parseOperit2Provider(bos.toString("UTF-8"));
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Provider parseOperit2Provider(String json) {
        if (json == null) return null;
        try {
            JSONObject o = new JSONObject(json);
            JSONArray keys = o.names();
            for (int i = 0; keys != null && i < keys.length(); i++) {
                String k = keys.getString(i);
                if (!k.startsWith("provider_")) continue;
                String v = o.optString(k, "");
                if (!v.startsWith("{")) continue;
                JSONObject p = new JSONObject(v);
                String endpoint = p.optString("endpoint", "");
                String key = p.optString("apiKey", "");
                if (endpoint.isEmpty() || key.isEmpty()) continue;
                String base = endpoint.replaceAll("/chat/completions/?$", "");
                String model = "";
                int ctx = 0;
                JSONArray models = p.optJSONArray("models");
                if (models != null && models.length() > 0) {
                    JSONObject m = models.optJSONObject(0);
                    if (m != null) {
                        model = m.optString("id", "");
                        JSONObject co = m.optJSONObject("contextOverride");
                        if (co != null) ctx = co.optInt("maxContextLength", 0);
                    }
                }
                Provider r = new Provider();
                r.baseUrl = base;
                r.apiKey = key;
                r.model = model;
                r.context = ctx > 200000 ? ctx : ctx * 1000; // CLI stores 200 for 200k
                r.maxTokens = 128000;
                r.providerId = base.contains("open-design") ? "opendesign" : "custom";
                return r;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Provider existingProvider() {
        for (OxAgent a : OxAgentStore.listAgents()) {
            if (a.baseUrl != null && !a.baseUrl.trim().isEmpty() && a.apiKey != null && !a.apiKey.isEmpty()) {
                Provider p = new Provider();
                p.baseUrl = a.baseUrl;
                p.apiKey = a.apiKey;
                p.model = a.model;
                p.context = a.contextLength;
                p.maxTokens = a.maxTokens;
                p.providerId = a.providerId;
                return p;
            }
        }
        return null;
    }

    private static OxAgent createAgent(String name, String md, String opening, Provider provider, String source) {
        String cleanName = name == null || name.trim().isEmpty() ? "Operit Agent" : name.trim();
        // Re-importing the same Operit card must update that agent, not create another one.
        OxAgent a = findImported(cleanName, source);
        if (a == null) {
            a = new OxAgent();
            a.id = "op_" + source + "_" + Math.abs((source + ":" + cleanName).hashCode());
            a.pinned = false;
            a.remark = "";
        }
        a.name = cleanName;
        a.emoji = "🤖";
        a.agentsMd = md;
        a.builtIn = false;
        applyProvider(a, provider);
        OxAgentStore.saveAgent(a);
        if (opening != null && !opening.trim().isEmpty()) {
            List<OxChatEntry> chat = OxAgentStore.loadChat(a.id);
            if (chat.isEmpty()) {
                chat.add(new OxChatEntry("assistant", opening.trim(), System.currentTimeMillis()));
                OxAgentStore.saveChat(a.id, chat);
            }
        }
        return a;
    }

    /** Existing agent created from the same Operit source and card name, if any. */
    private static OxAgent findImported(String name, String source) {
        for (OxAgent a : OxAgentStore.listAgents()) {
            if (a.builtIn || a.id == null) continue;
            if (!name.equals(a.name)) continue;
            if (a.id.startsWith("op_" + source + "_")) return a;
        }
        return null;
    }

    private static void applyProvider(OxAgent a, Provider p) {
        if (p == null) return;
        a.baseUrl = p.baseUrl;
        a.apiKey = p.apiKey;
        a.model = p.model;
        a.providerId = p.providerId;
        a.contextLength = p.context;
        a.maxTokens = p.maxTokens;
        a.temperature = 0.7;
        a.topP = 1.0;
        a.toolChoice = "auto";
        a.parallelToolCalls = true;
    }

    private static String persona(String desc, String setting, String otherChat, String otherVoice, String advanced) {
        StringBuilder sb = new StringBuilder();
        if (desc != null && !desc.trim().isEmpty()) sb.append(desc.trim()).append("\n\n");
        if (setting != null && !setting.trim().isEmpty()) sb.append(setting.trim()).append("\n\n");
        if (otherChat != null && !otherChat.trim().isEmpty()) sb.append("# Chat style\n").append(otherChat.trim()).append("\n\n");
        if (otherVoice != null && !otherVoice.trim().isEmpty()) sb.append("# Voice style\n").append(otherVoice.trim()).append("\n\n");
        if (advanced != null && !advanced.trim().isEmpty()) sb.append(advanced.trim()).append("\n");
        return sb.toString().trim();
    }

    private static boolean addMcp(String name, String url, String headers) {
        for (OxMcpManager.McpServer s : OxMcpManager.getServers()) {
            if (s.url != null && s.url.equals(url)) return false;
        }
        OxMcpManager.McpServer s = new OxMcpManager.McpServer(name, url);
        if (headers != null && !headers.isEmpty() && !"{}".equals(headers)) {
            s.headersJson = headers;
        }
        OxMcpManager.saveServer(s);
        return true;
    }

    private static String findEnding(Map<String, byte[]> files, String suffix) {
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            if (e.getKey().endsWith(suffix)) return new String(e.getValue(), StandardCharsets.UTF_8);
        }
        return null;
    }

    private static String clean(String s) {
        if (s == null) return "";
        s = s.replaceAll("(?is)<think>.*?</think>", "");
        s = s.replaceAll("(?is)<tool_[^>]*>.*?</tool_[^>]*>", "");
        s = s.replaceAll("(?is)<tool_result[^>]*>.*?</tool_result[^>]*>", "");
        return s.trim();
    }

    private static String readText(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        }
    }

    private static void deleteTree(File f) {
        if (f == null) return;
        File[] fs = f.listFiles();
        if (fs != null) for (File c : fs) deleteTree(c);
        f.delete();
    }
}
