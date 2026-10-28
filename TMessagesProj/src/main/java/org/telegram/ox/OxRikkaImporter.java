package org.telegram.ox;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
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
 * Imports assistants, provider wiring, remote MCP servers, skills, image
 * avatars and the selected text branch of chat history from a RikkaHub backup.
 * Other attachments, tool parts and OAuth tokens are not imported.
 */
public class OxRikkaImporter {

    private static final int MAX_SETTINGS = 4 * 1024 * 1024;

    public static class Preview {
        public int assistants;
        public int providers;
        public int mcp;
        public int skippedSse;
        public int skills;
        public int conversations;
        public int messageNodes;
        public int existingAssistants;
        public int existingMcp;
        public int existingSkills;
        public int avatars;
        public final List<String> names = new ArrayList<>();
        public final List<String> skillNames = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
    }

    public static class Result {
        public int agentsCreated;
        public int agentsUpdated;
        public int mcpAdded;
        public int mcpSkipped;
        public int skills;
        public int messages;
        public int avatars;
        public int files;
        public final List<String> importedAgentIds = new ArrayList<>();
        public final List<String> conversationIds = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
    }

    private static class Archive {
        JSONObject settings;
        byte[] database;
        final Map<String, byte[]> skills = new java.util.LinkedHashMap<>();
        final Map<String, byte[]> uploads = new java.util.LinkedHashMap<>();
    }

    private static class ProviderRef {
        String key;
        String baseUrl;
        String apiKey;
        String model;
        String providerId;
        int contextLength;
        boolean enabled = true;
        JSONArray headers;
    }

    public static Preview preview(Context context, Uri uri) throws Exception {
        Archive archive = readArchive(context, uri);
        JSONObject settings = archive.settings;
        Preview p = new Preview();
        Map<String, ProviderRef> models = providers(settings);
        Set<String> existingIds = new HashSet<>();
        for (OxAgent a : OxAgentStore.listAgents()) {
            if (a.id != null) existingIds.add(a.id);
        }
        JSONArray assistants = settings.optJSONArray("assistants");
        Set<String> referenced = new HashSet<>();
        String defaultModel = settings.optString("chatModelId", "");
        ProviderRef fallback = firstProvider(models);
        for (int i = 0; assistants != null && i < assistants.length(); i++) {
            JSONObject a = assistants.optJSONObject(i);
            if (a == null) continue;
            String id = agentId(a.optString("id", ""), a.optString("name", ""));
            String name = a.optString("name", "").trim();
            if (name.isEmpty()) name = "RikkaHub Assistant";
            p.assistants++;
            if (p.names.size() < 8) p.names.add(name);
            if (existingIds.contains(id)) p.existingAssistants++;
            String avatarName = avatarUploadName(a.optJSONObject("avatar"));
            if (avatarName != null && archive.uploads.containsKey(avatarName)) p.avatars++;
            ProviderRef ref = models.get(a.optString("chatModelId", ""));
            if (ref == null) ref = models.get(defaultModel);
            if (ref == null) ref = fallback;
            if (ref != null) referenced.add(ref.key);
        }
        p.providers = referenced.size();
        JSONArray servers = settings.optJSONArray("mcpServers");
        for (int i = 0; servers != null && i < servers.length(); i++) {
            JSONObject s = servers.optJSONObject(i);
            if (s == null) continue;
            String type = s.optString("type", "");
            if ("sse".equals(type)) {
                p.skippedSse++;
            } else if ("streamable_http".equals(type) && !s.optString("url", "").isEmpty()) {
                p.mcp++;
                if (mcpExists(s.optString("url", ""))) p.existingMcp++;
            }
        }
        Set<String> skillIds = new HashSet<>();
        for (OxSkillStore.Skill s : OxSkillStore.list()) {
            if (s.id != null) skillIds.add(s.id);
        }
        for (String id : archive.skills.keySet()) {
            String safe = OxSkillStore.skillId(id);
            p.skills++;
            if (p.skillNames.size() < 8) p.skillNames.add(safe);
            if (skillIds.contains(safe)) p.existingSkills++;
        }
        if (archive.database != null && archive.database.length > 0) {
            int[] history = countHistory(archive.database);
            p.conversations = history[0];
            p.messageNodes = history[1];
        }
        p.notes.add("仅导入 settings.json、skills/ 技能、助手图像头像、聊天文本历史与匹配的图片/文件附件；不含其他附件和 OAuth token。");
        if (p.avatars > 0) {
            p.notes.add("将导入 " + p.avatars + " 个助手图像头像。");
        }
        if (archive.database != null && archive.database.length > 0) {
            p.notes.add("历史中的 document 附件会匹配 upload/ 后作为文件气泡导入。");
        }
        if (p.conversations > 0) {
            p.notes.add("历史按每个消息节点的当前分支导入；工具与未匹配附件的消息会跳过。");
        }
        if (!archive.uploads.isEmpty()) {
            p.notes.add("备份中的聊天图片附件会随文本历史一起导入（最多 40 个）。");
        }
        if (archive.skills.size() > 0) {
            p.notes.add("技能按目录名导入，同名技能更新，不删除本地其他技能。");
        }
        if (p.skippedSse > 0) p.notes.add("SSE 传输的 MCP 暂不导入（" + p.skippedSse + " 个）。");
        return p;
    }

    public static Result importFrom(Context context, Uri uri) throws Exception {
        Archive archive = readArchive(context, uri);
        JSONObject settings = archive.settings;
        Map<String, ProviderRef> models = providers(settings);
        String defaultModel = settings.optString("chatModelId", "");
        ProviderRef fallback = firstProvider(models);
        Result r = new Result();
        for (Map.Entry<String, byte[]> entry : archive.skills.entrySet()) {
            try {
                OxSkillStore.importSkill(entry.getKey(), entry.getValue());
                r.skills++;
            } catch (Throwable t) {
                r.notes.add("技能「" + entry.getKey() + "」导入失败");
            }
        }
        Map<String, String> skillIdsByName = new HashMap<>();
        for (OxSkillStore.Skill s : OxSkillStore.list()) {
            if (s.id != null) skillIdsByName.put(s.id.toLowerCase(java.util.Locale.ROOT), s.id);
            if (s.name != null && s.id != null) {
                skillIdsByName.putIfAbsent(s.name.toLowerCase(java.util.Locale.ROOT), s.id);
            }
        }

        JSONArray assistants = settings.optJSONArray("assistants");
        for (int i = 0; assistants != null && i < assistants.length(); i++) {
            JSONObject src = assistants.optJSONObject(i);
            if (src == null) continue;
            String id = agentId(src.optString("id", ""), src.optString("name", ""));
            String name = src.optString("name", "").trim();
            if (name.isEmpty()) name = "RikkaHub Assistant";
            ProviderRef provider = models.get(src.optString("chatModelId", ""));
            if (provider == null) provider = models.get(defaultModel);
            if (provider == null) provider = fallback;

            OxAgent a = findById(id);
            boolean update = a != null;
            long oldTgId = a == null ? 0 : a.tgId;
            String oldAvatar = a == null ? "" : a.avatarPath;
            if (a == null) {
                a = new OxAgent();
                a.id = id;
                a.builtIn = false;
                a.pinned = false;
            }
            a.name = name;
            a.agentsMd = src.optString("systemPrompt", "").trim();
            a.remark = "";
            a.tgId = oldTgId;
            a.providerId = "rikka";
            a.emoji = avatarEmoji(src.optJSONObject("avatar"));
            if (a.emoji.isEmpty()) a.emoji = "🤖";
            if (oldAvatar.isEmpty()) {
                String saved = saveRikkaAvatar(a.id, src.optJSONObject("avatar"),
                        archive.uploads);
                a.avatarPath = saved == null ? "" : saved;
                if (saved != null) r.avatars++;
            } else {
                a.avatarPath = oldAvatar;
            }
            a.temperature = src.has("temperature") ? src.optDouble("temperature", 0.7) : 0.7;
            a.topP = src.has("topP") ? src.optDouble("topP", 1.0) : 1.0;
            a.maxTokens = src.optInt("maxTokens", 0);
            a.reasoningEffort = reasoning(src.optString("reasoningLevel", "AUTO"));
            a.toolChoice = "auto";
            a.parallelToolCalls = true;
            if (provider != null) {
                a.baseUrl = provider.baseUrl;
                a.apiKey = provider.apiKey;
                a.model = provider.model;
                a.providerId = provider.providerId;
                a.contextLength = provider.contextLength;
            } else {
                a.baseUrl = "";
                a.apiKey = "";
                a.model = "";
                r.notes.add("助手「" + name + "」没有可用的 Provider，按离线模式导入");
            }
            JSONArray enabledSkills = src.optJSONArray("enabledSkills");
            if (enabledSkills != null && enabledSkills.length() > 0) {
                JSONArray ids = new JSONArray();
                Set<String> seen = new HashSet<>();
                for (int s = 0; s < enabledSkills.length(); s++) {
                    String key = enabledSkills.optString(s, "").toLowerCase(java.util.Locale.ROOT);
                    String skillId = skillIdsByName.get(key);
                    if (skillId != null && seen.add(skillId)) ids.put(skillId);
                }
                if (ids.length() > 0) a.skillIds = ids;
            }
            JSONArray mergedHeaders = mergeHeaders(
                    provider == null ? null : provider.headers, src.optJSONArray("customHeaders"));
            if (mergedHeaders.length() > 0) a.headers = mergedHeaders;
            if (mergedHeaders.length() > 0) a.headers = mergedHeaders;
            String opener = firstPresetText(src.optJSONArray("presetMessages"));
            if (!opener.isEmpty()) a.firstMes = opener;
            OxAgentStore.saveAgent(a);
            r.importedAgentIds.add(a.id);
            if (update) r.agentsUpdated++;
            else r.agentsCreated++;
        }
        if (r.avatars > 0) r.notes.add("导入头像 " + r.avatars + " 个");

        JSONArray servers = settings.optJSONArray("mcpServers");
        for (int i = 0; servers != null && i < servers.length(); i++) {
            JSONObject src = servers.optJSONObject(i);
            if (src == null) continue;
            if (!"streamable_http".equals(src.optString("type", ""))) continue;
            String url = src.optString("url", "").trim();
            if (url.isEmpty()) continue;
            JSONObject common = src.optJSONObject("commonOptions");
            String name = common == null ? "" : common.optString("name", "").trim();
            if (name.isEmpty()) name = "Rikka MCP";
            if (mcpExists(url)) {
                r.mcpSkipped++;
                continue;
            }
            OxMcpManager.McpServer s = new OxMcpManager.McpServer(name, url);
            s.enabled = common == null || common.optBoolean("enable", true);
            JSONObject headers = headers(common == null ? null : common.opt("headers"));
            if (headers.length() > 0) s.headersJson = headers.toString();
            OxMcpManager.saveServer(s);
            r.mcpAdded++;
        }
        importConversations(archive.database, archive.uploads, r);
        if (r.files > 0) r.notes.add("导入附件 " + r.files + " 个");
        return r;
    }

    private static Archive readArchive(Context context, Uri uri) throws Exception {
        InputStream raw;
        try {
            raw = context.getContentResolver().openInputStream(uri);
        } catch (Throwable t) {
            throw new IllegalArgumentException("cannot open backup");
        }
        if (raw == null) throw new IllegalArgumentException("cannot open backup");
        BufferedInputStream in = new BufferedInputStream(raw);
        try {
            in.mark(2);
            int a = in.read();
            int b = in.read();
            in.reset();
            Archive out = new Archive();
            String json = null;
            if (a == 'P' && b == 'K') {
                ZipInputStream zin = new ZipInputStream(in);
                ZipEntry e;
                int entries = 0;
                long uploadTotal = 0;
                while ((e = zin.getNextEntry()) != null) {
                    if (++entries > 1000) throw new IllegalArgumentException("too many entries");
                    if (e.isDirectory()) continue;
                    String n = e.getName();
                    if (json == null && ("settings.json".equals(n) || n.endsWith("/settings.json"))) {
                        json = readAll(zin, MAX_SETTINGS);
                    } else if (out.skills.size() < 50 && n.toLowerCase(java.util.Locale.ROOT).endsWith("skill.md")) {
                        String id = skillIdFromEntry(n);
                        if (!id.isEmpty()) {
                            byte[] body = readBytes(zin, 512 * 1024);
                            if (body.length > 0) out.skills.put(id, body);
                        }
                    } else if (out.database == null
                            && ("rikka_hub.db".equals(n) || n.endsWith("/rikka_hub.db"))) {
                        out.database = readBytes(zin, 64 * 1024 * 1024);
                    } else if (out.uploads.size() < 40 && n.startsWith("upload/") && !n.endsWith("/")) {
                        String name = n.substring(n.lastIndexOf('/') + 1);
                        if (!name.isEmpty() && !name.contains("..")) {
                            byte[] data = readBytes(zin, 4 * 1024 * 1024);
                            if (data.length > 0 && uploadTotal + data.length <= 40L * 1024 * 1024) {
                                out.uploads.put(name, data);
                                uploadTotal += data.length;
                            }
                        }
                    }
                }
            } else {
                json = readAll(in, MAX_SETTINGS);
            }
            if (json == null) throw new IllegalArgumentException("settings.json missing");
            JSONObject root = new JSONObject(json);
            if (!root.has("assistants") && !root.has("providers") && !root.has("mcpServers")) {
                throw new IllegalArgumentException("not a RikkaHub settings file");
            }
            out.settings = root;
            return out;
        } finally {
            try {
                in.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String skillIdFromEntry(String name) {
        String n = name == null ? "" : name;
        int at = n.indexOf("skills/");
        if (at < 0) return "";
        String rel = n.substring(at + "skills/".length());
        int slash = rel.lastIndexOf('/');
        String id = slash > 0 ? rel.substring(0, slash) : rel;
        return id.replace('/', '_');
    }

    private static String readAll(InputStream in, int max) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0 && bos.size() < max) {
            bos.write(buf, 0, Math.min(n, max - bos.size()));
        }
        return bos.toString("UTF-8");
    }

    private static byte[] readBytes(InputStream in, int max) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0 && bos.size() < max) {
            bos.write(buf, 0, Math.min(n, max - bos.size()));
        }
        return bos.toByteArray();
    }

    private static Map<String, ProviderRef> providers(JSONObject settings) {
        Map<String, ProviderRef> byModel = new HashMap<>();
        JSONArray providers = settings.optJSONArray("providers");
        for (int i = 0; providers != null && i < providers.length(); i++) {
            JSONObject src = providers.optJSONObject(i);
            if (src == null) continue;
            String baseUrl = src.optString("baseUrl", "").trim();
            String apiKey = src.optString("apiKey", "");
            if (baseUrl.isEmpty()) continue;
            String type = src.optString("type", "openai");
            String providerId = "rikka";
            if ("claude".equals(type)) providerId = "anthropic";
            else if ("google".equals(type)) providerId = "google";
            String key = src.optString("id", src.optString("name", baseUrl));
            JSONArray models = src.optJSONArray("models");
            for (int m = 0; models != null && m < models.length(); m++) {
                JSONObject model = models.optJSONObject(m);
                if (model == null) continue;
                String modelId = model.optString("id", "");
                String modelName = model.optString("modelId", "");
                if (modelId.isEmpty() || modelName.isEmpty()) continue;
                ProviderRef ref = new ProviderRef();
                ref.key = key;
                ref.baseUrl = baseUrl;
                ref.apiKey = apiKey;
                ref.model = modelName;
                ref.providerId = providerId;
                ref.contextLength = model.optInt("contextLength", 0);
                ref.enabled = src.optBoolean("enabled", true);
                ref.headers = src.optJSONArray("customHeaders");
                byModel.put(modelId, ref);
            }
        }
        return byModel;
    }

    private static ProviderRef firstProvider(Map<String, ProviderRef> models) {
        for (ProviderRef p : models.values()) {
            if (p.enabled && p.apiKey != null && !p.apiKey.isEmpty()) return p;
        }
        return null;
    }

    private static String agentId(String sourceId, String name) {
        String safe = sourceId == null ? "" : sourceId.replaceAll("[^a-zA-Z0-9_-]", "");
        if (safe.isEmpty()) {
            safe = Integer.toHexString((name == null ? "" : name).hashCode());
        }
        return "rikka_" + safe;
    }

    private static OxAgent findById(String id) {
        for (OxAgent a : OxAgentStore.listAgents()) {
            if (id.equals(a.id)) return a;
        }
        return null;
    }

    private static boolean mcpExists(String url) {
        for (OxMcpManager.McpServer s : OxMcpManager.getServers()) {
            if (s.url != null && s.url.equals(url)) return true;
        }
        return false;
    }

    private static String avatarEmoji(JSONObject avatar) {
        if (avatar == null) return "";
        String type = avatar.optString("type", "").toLowerCase(java.util.Locale.ROOT);
        if (type.contains("emoji")) {
            return avatar.optString("content", "").trim();
        }
        return "";
    }

    /** Returns the upload/ basename referenced by a RikkaHub Avatar.Image, or null. */
    private static String avatarUploadName(JSONObject avatar) {
        if (avatar == null) return null;
        String url = avatar.optString("url", "").trim();
        if (url.isEmpty()) return null;
        String type = avatar.optString("type", "").toLowerCase(java.util.Locale.ROOT);
        if (!type.contains("image") && !url.startsWith("file:")
                && !url.startsWith("content:")) return null;
        String name = url;
        int q = name.indexOf('?');
        if (q >= 0) name = name.substring(0, q);
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        try {
            name = Uri.decode(name);
        } catch (Throwable ignored) {
        }
        if (name.isEmpty() || name.contains("..")) return null;
        return name;
    }

    /** Copies a RikkaHub Avatar.Image from the backup's upload/ folder into the
     *  agent directory. Network URLs are left alone (no import-time downloads). */
    private static String saveRikkaAvatar(String agentId, JSONObject avatar,
            Map<String, byte[]> uploads) {
        String name = avatarUploadName(avatar);
        if (name == null) return null;
        byte[] data = uploads.get(name);
        if (data == null || data.length == 0) return null;
        File dir = OxAgentStore.memoryFile(agentId).getParentFile();
        if (dir == null) return null;
        dir.mkdirs();
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        String ext = lower.endsWith(".png") ? ".png"
                : lower.endsWith(".jpg") || lower.endsWith(".jpeg") ? ".jpg"
                : lower.endsWith(".webp") ? ".webp"
                : lower.endsWith(".gif") ? ".gif" : ".img";
        File target = new File(dir, "avatar" + ext);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(target);
            out.write(data);
            return target.getAbsolutePath();
        } catch (Throwable t) {
            target.delete();
            return null;
        } finally {
            if (out != null) {
                try { out.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static String reasoning(String level) {
        if (level == null) return "auto";
        switch (level.toUpperCase()) {
            case "LOW":
                return "low";
            case "MEDIUM":
            case "MIDDLE":
                return "medium";
            case "HIGH":
                return "high";
            default:
                return "auto";
        }
    }

    private static int[] countHistory(byte[] database) {
        File file = null;
        SQLiteDatabase db = null;
        Cursor c = null;
        try {
            file = writeHistoryDb(database);
            db = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            c = db.rawQuery("SELECT COUNT(*) FROM conversationentity", null);
            int conversations = c.moveToFirst() ? c.getInt(0) : 0;
            c.close();
            c = db.rawQuery("SELECT COUNT(*) FROM message_node", null);
            int nodes = c.moveToFirst() ? c.getInt(0) : 0;
            return new int[]{conversations, nodes};
        } catch (Throwable t) {
            return new int[]{0, 0};
        } finally {
            if (c != null) try { c.close(); } catch (Throwable ignored) {}
            if (db != null) try { db.close(); } catch (Throwable ignored) {}
            if (file != null) file.delete();
        }
    }

    private static void importConversations(byte[] database, Map<String, byte[]> uploads, Result r) {
        if (database == null || database.length == 0) return;
        File file = null;
        SQLiteDatabase db = null;
        Cursor conversations = null;
        try {
            file = writeHistoryDb(database);
            db = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            conversations = db.rawQuery(
                    "SELECT id, assistant_id, create_at FROM conversationentity ORDER BY create_at ASC LIMIT 200", null);
            List<String> ids = new ArrayList<>();
            List<String> assistants = new ArrayList<>();
            List<Long> created = new ArrayList<>();
            while (conversations.moveToNext()) {
                ids.add(conversations.getString(0));
                assistants.add(conversations.getString(1));
                created.add(conversations.getLong(2));
            }
            Map<String, List<OxChatEntry>> chats = new HashMap<>();
            Map<String, Set<String>> seen = new HashMap<>();
            Map<String, String> uploaded = new HashMap<>();
            int total = 0;
            for (int i = 0; i < ids.size() && total < 5000; i++) {
                String assistantId = assistants.get(i);
                String agentId = agentId(assistantId, assistantId == null ? "" : assistantId);
                OxAgent agent = findById(agentId);
                if (agent == null) continue;
                List<OxChatEntry> chat = chats.get(agentId);
                if (chat == null) {
                    chat = OxAgentStore.loadChat(agentId);
                    chats.put(agentId, chat);
                    Set<String> keys = new HashSet<>();
                    for (OxChatEntry e : chat) keys.add(e.role + "\u0000" + e.content + "\u0000" + e.ts);
                    seen.put(agentId, keys);
                }
                long base = created.get(i) == null || created.get(i) <= 0
                        ? 1_700_000_000_000L + (long) i * 10_000_000L : created.get(i);
                Cursor nodes = null;
                try {
                    nodes = db.rawQuery("SELECT messages, select_index FROM message_node "
                            + "WHERE conversation_id=? ORDER BY node_index ASC LIMIT 500", new String[]{ids.get(i)});
                    int order = 0;
                    while (nodes.moveToNext() && total < 5000) {
                        JSONArray messages;
                        try {
                            messages = new JSONArray(nodes.getString(0));
                        } catch (Throwable t) {
                            continue;
                        }
                        int selected = nodes.getInt(1);
                        JSONObject message = messages.optJSONObject(selected);
                        if (message == null) message = messages.optJSONObject(0);
                        if (message == null) continue;
                        String role = message.optString("role", "").toLowerCase(java.util.Locale.ROOT);
                        if (!"user".equals(role) && !"assistant".equals(role)) continue;
                        StringBuilder text = new StringBuilder();
                        String imagePath = null;
                        String filePath = null;
                        String fileName = "";
                        JSONArray parts = message.optJSONArray("parts");
                        if (parts != null) {
                            for (int p = 0; p < parts.length(); p++) {
                                JSONObject part = parts.optJSONObject(p);
                                if (part == null) continue;
                                String type = part.optString("type", "text");
                                if ("image".equals(type)) {
                                    if (imagePath == null) {
                                        imagePath = materializeUpload(part.optString("url", ""), uploads, uploaded);
                                    }
                                    continue;
                                }
                                if ("document".equals(type)) {
                                    if (filePath == null) {
                                        filePath = materializeUpload(part.optString("url", ""), uploads, uploaded);
                                        fileName = part.optString("fileName", "").trim();
                                        if (fileName.isEmpty() && filePath != null) {
                                            fileName = new File(filePath).getName();
                                        }
                                    }
                                    continue;
                                }
                                if (!"text".equals(type)) continue;
                                String body = part.optString("text", part.optString("content", "")).trim();
                                if (body.isEmpty()) continue;
                                if (text.length() > 0) text.append('\n');
                                text.append(body);
                            }
                        } else {
                            text.append(message.optString("content", "").trim());
                        }
                        String content = text.toString().trim();
                        if (content.isEmpty() && imagePath != null) content = "[图片]";
                        if (content.isEmpty() && imagePath == null && filePath == null) continue;
                        if (content.length() > 100_000) content = content.substring(0, 100_000);
                        long ts = base + order * 1000L;
                        order++;
                        String key = role + "\u0000" + content + "\u0000" + ts;
                        Set<String> keys = seen.get(agentId);
                        if (keys != null && !keys.add(key)) continue;
                        OxChatEntry entry = imagePath == null
                                ? new OxChatEntry(role, content, ts)
                                : new OxChatEntry(role, content, ts, imagePath);
                        if (filePath != null) {
                            entry.filePath = filePath;
                            entry.fileName = fileName;
                            r.files++;
                        }
                        chat.add(entry);
                        total++;
                        if (!r.conversationIds.contains(agentId)) {
                            r.conversationIds.add(agentId);
                        }
                    }
                } catch (Throwable t) {
                    r.notes.add("读取会话历史失败：" + ids.get(i));
                } finally {
                    if (nodes != null) try { nodes.close(); } catch (Throwable ignored) {}
                }
            }
            for (Map.Entry<String, List<OxChatEntry>> e : chats.entrySet()) {
                OxAgentStore.saveChat(e.getKey(), e.getValue());
            }
            r.messages += total;
            if (total > 0) r.notes.add("已导入历史消息 " + total + " 条");
        } catch (Throwable t) {
            r.notes.add("读取 RikkaHub 聊天数据库失败");
        } finally {
            if (conversations != null) try { conversations.close(); } catch (Throwable ignored) {}
            if (db != null) try { db.close(); } catch (Throwable ignored) {}
            if (file != null) file.delete();
        }
    }

    private static String materializeUpload(String url, Map<String, byte[]> uploads,
                                            Map<String, String> cache) {
        if (url == null || url.isEmpty() || url.startsWith("data:")) return null;
        String name = url;
        int query = name.indexOf('?');
        if (query >= 0) name = name.substring(0, query);
        name = name.substring(name.lastIndexOf('/') + 1);
        name = new File(name).getName();
        if (name.isEmpty() || name.contains("..")) return null;
        if (cache.containsKey(name)) return cache.get(name);
        byte[] data = uploads.get(name);
        if (data == null || data.length == 0) return null;
        File out = new File(ApplicationLoader.applicationContext.getFilesDir(), "rikka_upload_" + name);
        try {
            if (!out.isFile() || out.length() != data.length) {
                FileOutputStream fos = new FileOutputStream(out);
                try {
                    fos.write(data);
                } finally {
                    fos.close();
                }
            }
            cache.put(name, out.getAbsolutePath());
            return out.getAbsolutePath();
        } catch (Throwable t) {
            return null;
        }
    }

    private static File writeHistoryDb(byte[] database) throws Exception {
        File file = new File(ApplicationLoader.applicationContext.getCacheDir(),
                "rikka_history_" + System.nanoTime() + ".db");
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(database);
        } finally {
            out.close();
        }
        return file;
    }

    private static JSONArray mergeHeaders(JSONArray provider, JSONArray assistant) {
        java.util.LinkedHashMap<String, String> byName = new java.util.LinkedHashMap<>();
        collectHeaders(provider, byName);
        collectHeaders(assistant, byName);
        JSONArray out = new JSONArray();
        for (Map.Entry<String, String> e : byName.entrySet()) {
            out.put(new JSONArray().put(e.getKey()).put(e.getValue()));
        }
        return out;
    }

    private static void collectHeaders(JSONArray headers,
                                       java.util.LinkedHashMap<String, String> byName) {
        for (int i = 0; headers != null && i < headers.length(); i++) {
            Object item = headers.opt(i);
            String name = null;
            String value = null;
            if (item instanceof JSONObject) {
                JSONObject header = (JSONObject) item;
                name = header.optString("name", header.optString("first", "")).trim();
                value = header.optString("value", header.optString("second", ""));
            } else if (item instanceof JSONArray) {
                JSONArray pair = (JSONArray) item;
                name = pair.optString(0, "").trim();
                value = pair.optString(1, "");
            }
            if (name != null && !name.isEmpty()) byName.put(name, value == null ? "" : value);
        }
    }

    private static String firstPresetText(JSONArray messages) {
        for (int i = 0; messages != null && i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) continue;
            if (!"assistant".equalsIgnoreCase(message.optString("role", ""))) continue;
            JSONArray parts = message.optJSONArray("parts");
            for (int p = 0; parts != null && p < parts.length(); p++) {
                JSONObject part = parts.optJSONObject(p);
                if (part == null) continue;
                String type = part.optString("type", "text");
                if (!"text".equals(type)) continue;
                String text = part.optString("text", part.optString("content", "")).trim();
                if (!text.isEmpty()) return text.length() > 4000 ? text.substring(0, 4000) : text;
            }
        }
        return "";
    }

    /** kotlinx.serialization encodes Kotlin Pair either as [k,v] or {first,second}. */
    private static JSONObject headers(Object raw) {
        JSONObject out = new JSONObject();
        if (raw instanceof JSONObject) {
            JSONObject map = (JSONObject) raw;
            JSONArray keys = map.names();
            for (int i = 0; keys != null && i < keys.length(); i++) {
                String key = keys.optString(i, "");
                if (!key.isEmpty()) {
                    try {
                        out.put(key, map.optString(key, ""));
                    } catch (Throwable t) {
                        FileLog.e("OxRikkaImporter", t);
                    }
                }
            }
            return out;
        }
        JSONArray list = raw instanceof JSONArray ? (JSONArray) raw : null;
        if (list == null) return out;
        for (int i = 0; i < list.length(); i++) {
            Object item = list.opt(i);
            String key = null;
            String value = null;
            if (item instanceof JSONArray) {
                JSONArray pair = (JSONArray) item;
                key = pair.optString(0, "");
                value = pair.optString(1, "");
            } else if (item instanceof JSONObject) {
                JSONObject pair = (JSONObject) item;
                key = pair.optString("first", pair.optString("name", ""));
                value = pair.optString("second", pair.optString("value", ""));
            }
            if (key != null && !key.isEmpty()) {
                try {
                    out.put(key, value == null ? "" : value);
                } catch (Throwable t) {
                    FileLog.e("OxRikkaImporter", t);
                }
            }
        }
        return out;
    }
}
