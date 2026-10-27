package org.telegram.ox;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Imports assistant/persona definitions and chat history from Kelivo / Chatbox
 * backup files. Provider keys stay wrapped by the agent store; skills, MCP,
 * conversations and backup avatars have dedicated sections below.
 */
public class OxKelivoImporter {

    private static final int MAX_JSON = 4 * 1024 * 1024;
    private static final int MAX_CONVERSATIONS = 100;
    private static final int MAX_MESSAGES_PER_CONVERSATION = 500;
    private static final int MAX_MESSAGE_CHARS = 20000;
    private static final int MAX_IMAGES_PER_CONVERSATION = 40;
    private static final int MAX_RESOURCE_FILES = 64;
    private static final int MAX_RESOURCE_BYTES = 2 * 1024 * 1024;
    private static final long MAX_RESOURCE_TOTAL = 24L * 1024 * 1024;

    public static class Preview {
        public int assistants;
        public int existing;
        public int skills;
        public int existingSkills;
        public int mcp;
        public int skippedMcp;
        public int conversations;
        public int messages;
        public int existingConversations;
        public int avatars;
        public int images;
        public final List<String> names = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
    }

    public static class Result {
        public int created;
        public int updated;
        public int skills;
        public int mcpAdded;
        public int mcpSkipped;
        public int conversations;
        public int messages;
        public int convUpdated;
        public int avatars;
        public int images;
        public final List<String> ids = new ArrayList<>();
        public final List<String> conversationIds = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
    }

    private static class Conversation {
        String id;
        String title;
        String systemPrompt = "";
        String providerId = "";
        String model = "";
        String avatarRef = "";
        double temperature = 0.7;
        double topP = 1.0;
        int maxTokens = 0;
        final List<OxChatEntry> entries = new ArrayList<>();
        final List<String> imageRefs = new ArrayList<>();
    }

    private static class Resource {
        String mime;
        byte[] bytes;
    }

    private static class Archive {
        final List<JSONObject> assistants = new ArrayList<>();
        final Map<String, JSONObject> providers = new HashMap<>();
        final Map<String, byte[]> skills = new HashMap<>();
        final List<JSONObject> mcpServers = new ArrayList<>();
        final List<Conversation> conversations = new ArrayList<>();
        final Map<String, Resource> avatars = new HashMap<>();
        final Map<String, Resource> images = new HashMap<>();
        int avatarCount;
        int imageCount;
        String defaultPrompt = "";
    }

    public static Preview preview(Context context, Uri uri) throws Exception {
        Archive archive = read(context, uri);
        Preview p = new Preview();
        Set<String> existing = new HashSet<>();
        for (OxAgent a : OxAgentStore.listAgents()) {
            if (a.id != null) existing.add(a.id);
        }
        for (JSONObject o : archive.assistants) {
            String name = name(o);
            String id = agentId(o.optString("id", ""), name);
            p.assistants++;
            if (p.names.size() < 8) p.names.add(name);
            if (existing.contains(id)) p.existing++;
        }
        for (Conversation c : archive.conversations) {
            p.conversations++;
            p.messages += c.entries.size();
            if (p.names.size() < 8) p.names.add(c.title);
            if (existing.contains(conversationAgentId(c.id))) p.existingConversations++;
        }
        p.notes.add("仅导入 Kelivo / Chatbox 备份中的助手、人设、技能、HTTP MCP 与对话；不含 Provider 密钥。");
        if (!archive.providers.isEmpty()) {
            p.notes.add("将使用备份中的 " + archive.providers.size() + " 个 Provider 配置为助手接线。");
        }
        Set<String> existingSkills = new HashSet<>();
        for (OxSkillStore.Skill s : OxSkillStore.list()) {
            if (s.id != null) existingSkills.add(s.id);
        }
        for (String id : archive.skills.keySet()) {
            String safe = OxSkillStore.skillId(id);
            p.skills++;
            if (existingSkills.contains(safe)) p.existingSkills++;
        }
        if (!archive.skills.isEmpty()) {
            p.notes.add("将导入 " + archive.skills.size() + " 个技能；同名技能会更新。");
        }
        for (JSONObject s : archive.mcpServers) {
            if (isHttpMcp(s)) p.mcp++;
            else p.skippedMcp++;
        }
        if (p.mcp > 0 || p.skippedMcp > 0) {
            p.notes.add("将导入 " + p.mcp + " 个 HTTP MCP；跳过 "
                    + p.skippedMcp + " 个 SSE/stdio/inmemory 条目。");
        }
        if (p.conversations > 0) {
            p.notes.add("将导入 " + p.conversations + " 段对话（" + p.messages
                    + " 条消息）；已存在的对话会替换历史。");
        }
        p.avatars = archive.avatarCount;
        if (p.avatars > 0) {
            p.notes.add("备份含 " + p.avatars + " 个头像资源；匹配到的助手/对话会更新头像。");
        }
        for (Conversation c : archive.conversations) {
            for (String ref : c.imageRefs) {
                if (ref != null && !ref.isEmpty()) p.images++;
            }
        }
        if (p.images > 0) {
            p.notes.add("将导入 " + p.images + " 张聊天图片；原生聊天中显示为照片气泡。");
        }
        return p;
    }

    public static Result importFrom(Context context, Uri uri) throws Exception {
        Archive archive = read(context, uri);
        Result r = new Result();
        OxAgent fallback = existingProvider();
        Set<String> providersUsed = new HashSet<>();
        for (JSONObject o : archive.assistants) {
            String name = name(o);
            String id = agentId(o.optString("id", ""), name);
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
            a.agentsMd = o.optString("systemPrompt", o.optString("prompt", "")).trim();
            a.remark = "";
            a.tgId = oldTgId;
            if (oldAvatar.isEmpty()) {
                String saved = saveAvatar(a.id, avatarRef(o), archive);
                a.avatarPath = saved == null ? "" : saved;
                if (saved != null) r.avatars++;
            } else {
                a.avatarPath = oldAvatar;
            }
            a.emoji = "🤖";
            a.providerId = "kelivo";
            a.temperature = o.has("temperature") && !o.isNull("temperature")
                    ? o.optDouble("temperature", 0.7) : 0.7;
            a.topP = o.has("topP") && !o.isNull("topP")
                    ? o.optDouble("topP", 1.0) : 1.0;
            a.maxTokens = o.optInt("maxTokens", 0);
            a.toolChoice = "auto";
            a.parallelToolCalls = true;
            String providerKey = o.optString("chatModelProvider", "").trim();
            JSONObject providerConfig = archive.providers.get(providerKey);
            String baseUrl = providerConfig == null ? "" : providerConfig.optString("baseUrl", "").trim();
            if (providerConfig != null && !baseUrl.isEmpty()) {
                a.baseUrl = baseUrl;
                a.apiKey = providerConfig.optString("apiKey", "");
                a.model = firstModel(providerConfig);
                a.contextLength = 0;
                providersUsed.add(providerKey);
            } else if (fallback != null) {
                a.baseUrl = fallback.baseUrl;
                a.apiKey = fallback.apiKey;
                a.model = fallback.model;
                a.contextLength = fallback.contextLength;
            } else {
                a.baseUrl = "";
                a.apiKey = "";
                a.model = "";
            }
            String model = o.optString("chatModelId", "").trim();
            if (!model.isEmpty()) a.model = model;
            JSONArray headers = headers(o.optJSONArray("customHeaders"));
            if (headers.length() > 0) a.headers = headers;
            OxAgentStore.saveAgent(a);
            r.ids.add(a.id);
            if (update) r.updated++;
            else r.created++;
        }
        // Assistants just written can serve as the fallback provider for conversations
        // that carry no provider of their own (full backups often split the two).
        fallback = existingProvider();
        for (Conversation c : archive.conversations) {
            String id = conversationAgentId(c.id);
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
            a.name = c.title;
            a.agentsMd = c.systemPrompt;
            a.remark = "";
            a.tgId = oldTgId;
            if (oldAvatar.isEmpty()) {
                String saved = saveAvatar(a.id, c.avatarRef, archive);
                a.avatarPath = saved == null ? "" : saved;
                if (saved != null) r.avatars++;
            } else {
                a.avatarPath = oldAvatar;
            }
            a.emoji = "💬";
            a.providerId = "kelivo";
            a.temperature = c.temperature;
            a.topP = c.topP;
            a.maxTokens = c.maxTokens;
            a.toolChoice = "auto";
            a.parallelToolCalls = true;
            applyConversationProvider(a, c, archive.providers, fallback);
            if (!c.providerId.isEmpty() && archive.providers.get(c.providerId) != null) {
                providersUsed.add(c.providerId);
            }
            OxAgentStore.saveAgent(a);
            List<OxChatEntry> chat = new ArrayList<>(c.entries);
            r.images += saveChatImages(a.id, c, archive, chat);
            OxAgentStore.saveChat(a.id, chat);
            r.ids.add(a.id);
            r.conversationIds.add(a.id);
            r.conversations++;
            r.messages += c.entries.size();
            if (update) r.convUpdated++;
        }
        if (!providersUsed.isEmpty()) {
            r.notes.add("使用备份中的 " + providersUsed.size() + " 个 Provider 配置接线");
        }
        if (providersUsed.isEmpty() && fallback == null) {
            r.notes.add("没有找到可继承的 Provider，导入的助手与对话将按离线模式运行");
        }
        if (r.convUpdated > 0) {
            r.notes.add("已存在的 " + r.convUpdated + " 段对话历史已替换");
        }
        if (r.avatars > 0) {
            r.notes.add("导入头像 " + r.avatars + " 个");
        }
        if (r.images > 0) {
            r.notes.add("导入聊天图片 " + r.images + " 张");
        }
        for (Map.Entry<String, byte[]> e : archive.skills.entrySet()) {
            try {
                OxSkillStore.importSkill(e.getKey(), e.getValue());
                r.skills++;
            } catch (Throwable t) {
                r.notes.add("技能「" + e.getKey() + "」导入失败");
            }
        }
        if (r.skills > 0) r.notes.add("导入技能 " + r.skills + " 个");
        for (JSONObject s : archive.mcpServers) {
            if (!isHttpMcp(s)) {
                r.mcpSkipped++;
                continue;
            }
            String url = mcpUrl(s);
            if (url.isEmpty() || mcpExists(url)) {
                r.mcpSkipped++;
                continue;
            }
            String name = s.optString("name", s.optString("id", "Kelivo MCP")).trim();
            if (name.isEmpty()) name = "Kelivo MCP";
            OxMcpManager.McpServer server = new OxMcpManager.McpServer(name, url);
            server.enabled = s.optBoolean("isActive", s.optBoolean("enabled", true));
            JSONObject headers = mcpHeaders(s.opt("headers"));
            if (headers.length() > 0) server.headersJson = headers.toString();
            OxMcpManager.saveServer(server);
            r.mcpAdded++;
        }
        if (r.mcpAdded > 0 || r.mcpSkipped > 0) {
            r.notes.add("新增 MCP " + r.mcpAdded + " 个，跳过 " + r.mcpSkipped + " 个");
        }
        return r;
    }

    private static Archive read(Context context, Uri uri) throws Exception {
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
            if (a == 'P' && b == 'K') {
                ZipInputStream zin = new ZipInputStream(in);
                ZipEntry e;
                int entries = 0;
                String copilots = null;
                String settings = null;
                String assistants = null;
                String manifest = null;
                int sessionBytes = 0;
                long resourceBytes = 0;
                Map<String, String> sessionFiles = new HashMap<>();
                Map<String, byte[]> resourceFiles = new HashMap<>();
                while ((e = zin.getNextEntry()) != null) {
                    if (++entries > 1000) throw new IllegalArgumentException("too many entries");
                    if (e.isDirectory()) continue;
                    String n = e.getName();
                    if (copilots == null && "copilots.json".equals(n)) {
                        copilots = readString(zin);
                    } else if (settings == null && "settings.json".equals(n)) {
                        settings = readString(zin);
                    } else if (assistants == null && "assistants.json".equals(n)) {
                        assistants = readString(zin);
                    } else if (manifest == null && "manifest.json".equals(n)) {
                        manifest = readString(zin);
                    } else if (sessionFiles.size() < 200 && sessionBytes < 12 * 1024 * 1024
                            && n.startsWith("sessions/") && n.endsWith(".json")) {
                        String body = readString(zin, 2 * 1024 * 1024);
                        if (body != null && !body.trim().isEmpty()) {
                            sessionFiles.put(n, body);
                            sessionBytes += body.length();
                        }
                    } else if (out.skills.size() < 50 && n.startsWith("skills/")
                            && n.toLowerCase(java.util.Locale.ROOT).endsWith("skill.md")) {
                        String rel = n.substring("skills/".length());
                        int slash = rel.lastIndexOf('/');
                        String id = slash > 0 ? rel.substring(0, slash) : rel;
                        id = id.replace('/', '_');
                        if (!id.isEmpty()) {
                            byte[] body = readBytes(zin);
                            if (body.length > 0) out.skills.put(id, body);
                        }
                    } else if (resourceFiles.size() < MAX_RESOURCE_FILES
                            && resourceBytes < MAX_RESOURCE_TOTAL
                            && !n.endsWith(".json")
                            && !n.toLowerCase(java.util.Locale.ROOT).endsWith("skill.md")) {
                        byte[] body = readBytes(zin, MAX_RESOURCE_BYTES);
                        if (body != null && body.length > 0) {
                            resourceFiles.put(normPath(n), body);
                            resourceBytes += body.length;
                        }
                    }
                }
                if (settings != null) scan(new JSONObject(settings), out, 0);
                if (copilots != null) addArray(new JSONArray(copilots), out);
                if (assistants != null) addArray(new JSONArray(assistants), out);
                if (manifest != null) {
                    JSONObject m = new JSONObject(manifest);
                    addResources(m, resourceFiles, out);
                    addManifestSessions(m, sessionFiles, out);
                } else {
                    for (Map.Entry<String, String> entry : sessionFiles.entrySet()) {
                        try {
                            JSONObject session = new JSONObject(entry.getValue());
                            addSession(session, session.optString("id", ""), "", out);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } else {
                String json = readString(in);
                if (json == null || json.trim().isEmpty()) throw new IllegalArgumentException("empty file");
                String trimmed = json.trim();
                if (trimmed.startsWith("[")) {
                    addArray(new JSONArray(trimmed), out);
                } else {
                    JSONObject root = new JSONObject(trimmed);
                    scan(root, out, 0);
                    scanSessions(root, out);
                }
            }
            if (out.assistants.isEmpty() && out.conversations.isEmpty()) {
                throw new IllegalArgumentException("nothing importable");
            }
            return out;
        } finally {
            try { in.close(); } catch (Throwable ignored) {}
        }
    }

    private static String readString(InputStream in) throws Exception {
        return readString(in, MAX_JSON);
    }

    private static String readString(InputStream in, int limit) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (bos.size() + n > limit) return null;
            bos.write(buf, 0, n);
        }
        return bos.toString("UTF-8");
    }

    private static byte[] readBytes(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0 && bos.size() < 512 * 1024) {
            bos.write(buf, 0, Math.min(n, 512 * 1024 - bos.size()));
        }
        return bos.toByteArray();
    }

    private static byte[] readBytes(InputStream in, int limit) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (bos.size() + n > limit) return null;
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static void addManifestSessions(JSONObject manifest, Map<String, String> files, Archive out) {
        JSONArray sessions = manifest.optJSONArray("sessions");
        for (int i = 0; sessions != null && i < sessions.length(); i++) {
            JSONObject descriptor = sessions.optJSONObject(i);
            if (descriptor == null) continue;
            String body = files.get(descriptor.optString("path", "").trim());
            if (body == null) continue;
            try {
                addSession(new JSONObject(body), descriptor.optString("id", ""), "",
                        asObject(descriptor.opt("meta")), out);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void addResources(JSONObject manifest, Map<String, byte[]> files, Archive out) {
        JSONArray resources = manifest.optJSONArray("resources");
        for (int i = 0; resources != null && i < resources.length(); i++) {
            JSONObject descriptor = resources.optJSONObject(i);
            if (descriptor == null) continue;
            String kind = descriptor.optString("kind", "");
            String mime = descriptor.optString("mimeType", "").trim();
            boolean avatarKind = "avatar".equals(kind) || "copilot-image".equals(kind);
            boolean imageKind = "image".equals(kind)
                    || (("parsed-attachment".equals(kind) || "raw-attachment".equals(kind))
                    && mime.startsWith("image/"));
            if (!avatarKind && !imageKind) continue;
            byte[] bytes = files.get(normPath(descriptor.optString("path", "")));
            if (bytes == null) continue;
            bytes = decodeDataUrl(bytes, descriptor.optString("encoding", ""));
            if (bytes == null || bytes.length == 0 || bytes.length > MAX_RESOURCE_BYTES) continue;
            if (!looksLikeImage(bytes)) continue;
            if (mime.isEmpty() || !mime.startsWith("image/")) mime = guessMime(bytes);
            Resource resource = new Resource();
            resource.mime = mime;
            resource.bytes = bytes;
            Map<String, Resource> target = avatarKind ? out.avatars : out.images;
            JSONArray keys = descriptor.optJSONArray("originalStorageKeys");
            boolean keyed = false;
            for (int j = 0; keys != null && j < keys.length(); j++) {
                String key = keys.optString(j, "").trim();
                if (!key.isEmpty()) {
                    target.put(key, resource);
                    keyed = true;
                }
            }
            String id = descriptor.optString("id", "").trim();
            if (!id.isEmpty()) target.put(id, resource);
            if (keyed) {
                if (avatarKind) out.avatarCount++;
                else out.imageCount++;
            }
        }
    }

    private static byte[] decodeDataUrl(byte[] raw, String encoding) {
        try {
            String text = new String(raw, "UTF-8");
            if (!"data-url-base64".equalsIgnoreCase(encoding) && !text.startsWith("data:")) {
                return raw;
            }
            int comma = text.indexOf(',');
            if (comma < 0) return null;
            return android.util.Base64.decode(text.substring(comma + 1).trim(),
                    android.util.Base64.DEFAULT);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String normPath(String path) {
        String p = path == null ? "" : path.replace('\\', '/').trim();
        while (p.startsWith("./")) p = p.substring(2);
        while (p.startsWith("/")) p = p.substring(1);
        return p;
    }

    private static String avatarRef(JSONObject o) {
        if (o == null) return "";
        Object raw = o.opt("avatar");
        if (raw instanceof JSONObject) {
            JSONObject avatar = (JSONObject) raw;
            String key = avatar.optString("storageKey", "").trim();
            if (!key.isEmpty()) return key;
            String url = avatar.optString("url", "").trim();
            if (!url.isEmpty()) return url;
        } else if (raw instanceof String) {
            String value = ((String) raw).trim();
            if (!value.isEmpty()) return value;
        }
        return o.optString("picUrl", "").trim();
    }

    private static String saveAvatar(String agentId, String ref, Archive archive) {
        if (ref == null || ref.trim().isEmpty()) return null;
        Resource resource = archive.avatars.get(ref.trim());
        if (resource == null) return null;
        File dir = OxAgentStore.memoryFile(agentId).getParentFile();
        if (dir == null) return null;
        dir.mkdirs();
        return writeResource(new File(dir, "avatar" + extensionForMime(resource.mime)), resource);
    }

    private static int saveChatImages(String agentId, Conversation conversation,
            Archive archive, List<OxChatEntry> chat) {
        int count = 0;
        for (int i = 0; i < conversation.imageRefs.size() && i < chat.size(); i++) {
            String ref = conversation.imageRefs.get(i);
            if (ref == null || ref.isEmpty()) continue;
            Resource resource = archive.images.get(ref);
            if (resource == null) continue;
            File dir = new File(OxAgentStore.memoryFile(agentId).getParentFile(), "media");
            dir.mkdirs();
            String path = writeResource(
                    new File(dir, "img_" + i + extensionForMime(resource.mime)), resource);
            if (path != null) {
                chat.get(i).imagePath = path;
                count++;
            }
        }
        return count;
    }

    private static String writeResource(File target, Resource resource) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(target);
            out.write(resource.bytes);
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

    private static String extensionForMime(String mime) {
        String m = mime == null ? "" : mime.toLowerCase(java.util.Locale.ROOT);
        if (m.contains("png")) return ".png";
        if (m.contains("webp")) return ".webp";
        if (m.contains("gif")) return ".gif";
        return ".jpg";
    }

    private static String guessMime(byte[] b) {
        if (isPng(b)) return "image/png";
        if (isJpeg(b)) return "image/jpeg";
        if (isGif(b)) return "image/gif";
        if (isWebp(b)) return "image/webp";
        return "image/jpeg";
    }

    private static boolean looksLikeImage(byte[] b) {
        return isPng(b) || isJpeg(b) || isGif(b) || isWebp(b);
    }

    private static boolean isPng(byte[] b) {
        return b != null && b.length > 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    private static boolean isJpeg(byte[] b) {
        return b != null && b.length > 3 && (b[0] & 0xFF) == 0xFF
                && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isGif(byte[] b) {
        return b != null && b.length > 4 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F';
    }

    private static boolean isWebp(byte[] b) {
        return b != null && b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    private static void scanSessions(JSONObject root, Archive out) {
        Set<String> handled = new HashSet<>();
        JSONArray list = asArray(root.opt("chat-sessions-list"));
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject meta = list.optJSONObject(i);
                if (meta == null) continue;
                String id = meta.optString("id", "").trim();
                if (id.isEmpty()) continue;
                JSONObject session = asObject(root.opt("session:" + id));
                if (session == null) session = meta;
                addSession(session, id, meta.optString("name", ""), out);
                handled.add(id);
            }
        }
        JSONArray keys = root.names();
        for (int i = 0; keys != null && i < keys.length(); i++) {
            String key = keys.optString(i);
            if (!key.startsWith("session:")) continue;
            String id = key.substring("session:".length());
            if (handled.contains(id)) continue;
            JSONObject session = asObject(root.opt(key));
            if (session != null) addSession(session, id, "", out);
        }
        if (list == null && out.conversations.isEmpty() && root.optJSONArray("messages") != null) {
            addSession(root, root.optString("id", ""), root.optString("name", ""), out);
        }
    }

    private static JSONArray asArray(Object raw) {
        try {
            if (raw instanceof JSONArray) return (JSONArray) raw;
            if (raw instanceof String) {
                String s = ((String) raw).trim();
                if (s.startsWith("[")) return new JSONArray(s);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static JSONObject asObject(Object raw) {
        try {
            if (raw instanceof JSONObject) return (JSONObject) raw;
            if (raw instanceof String) {
                String s = ((String) raw).trim();
                if (s.startsWith("{")) return new JSONObject(s);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void addSession(JSONObject session, String fallbackId, String fallbackTitle, Archive out) {
        addSession(session, fallbackId, fallbackTitle, null, out);
    }

    private static void addSession(JSONObject session, String fallbackId, String fallbackTitle,
            JSONObject meta, Archive out) {
        if (session == null || out.conversations.size() >= MAX_CONVERSATIONS) return;
        String id = session.optString("id", fallbackId).trim();
        if (id.isEmpty()) return;
        String title = session.optString("name", "").trim();
        if (title.isEmpty()) title = fallbackTitle == null ? "" : fallbackTitle.trim();
        if (title.isEmpty()) title = "Kelivo 对话";

        JSONObject settings = session.optJSONObject("settings");
        if (settings == null) settings = new JSONObject();
        String providerId = settings.optString("provider", "").trim();
        if ("chatbox-ai".equalsIgnoreCase(providerId)) providerId = "";
        String model = settings.optString("modelId", "").trim();
        double temperature = settings.has("temperature") && !settings.isNull("temperature")
                ? settings.optDouble("temperature", 0.7) : 0.7;
        double topP = settings.has("topP") && !settings.isNull("topP")
                ? settings.optDouble("topP", 1.0) : 1.0;
        int maxTokens = settings.optInt("maxTokens", 0);

        String avatar = meta == null ? "" : meta.optString("assistantAvatarKey", "").trim();
        if (avatar.isEmpty() && meta != null) avatar = meta.optString("picUrl", "").trim();
        if (avatar.isEmpty()) avatar = session.optString("picUrl", "").trim();
        if (avatar.isEmpty()) avatar = avatarRef(session);

        JSONArray current = session.optJSONArray("messages");
        String prompt = firstSystemPrompt(current);
        if (prompt.isEmpty()) prompt = out.defaultPrompt;

        JSONArray threads = session.optJSONArray("threads");
        for (int i = 0; threads != null && i < threads.length(); i++) {
            JSONObject thread = threads.optJSONObject(i);
            if (thread == null) continue;
            JSONArray messages = thread.optJSONArray("messages");
            if (messages == null || messages.length() == 0) continue;
            String threadId = thread.optString("id", "").trim();
            if (threadId.isEmpty()) threadId = "thread" + i;
            String threadTitle = thread.optString("name", "").trim();
            if (threadTitle.isEmpty()) threadTitle = title;
            addConversation(out, id + "_" + threadId, threadTitle, providerId, model,
                    avatar, temperature, topP, maxTokens, prompt, messages);
        }
        if (current != null && current.length() > 0 && !currentDuplicated(current, threads)) {
            String threadName = session.optString("threadName", "").trim();
            addConversation(out, id + "_current", threadName.isEmpty() ? title : threadName,
                    providerId, model, avatar, temperature, topP, maxTokens, prompt, current);
        }
    }

    private static void addConversation(Archive out, String rawId, String title, String providerId,
            String model, String avatarRef, double temperature, double topP, int maxTokens,
            String prompt, JSONArray messages) {
        if (out.conversations.size() >= MAX_CONVERSATIONS) return;
        String safeId = rawId == null ? "" : rawId.replaceAll("[^a-zA-Z0-9_-]", "_");
        while (safeId.startsWith("_")) safeId = safeId.substring(1);
        if (safeId.isEmpty()) safeId = "conversation" + out.conversations.size();
        if (safeId.length() > 80) safeId = safeId.substring(0, 80);
        for (Conversation existing : out.conversations) {
            if (existing.id.equals(safeId)) return;
        }
        List<String> refs = new ArrayList<>();
        List<OxChatEntry> entries = convertMessages(messages, out, refs);
        if (entries.isEmpty()) return;
        Conversation c = new Conversation();
        c.id = safeId;
        c.title = title == null || title.trim().isEmpty() ? "Kelivo 对话" : title.trim();
        c.systemPrompt = prompt == null ? "" : prompt;
        c.providerId = providerId == null ? "" : providerId;
        c.model = model == null ? "" : model;
        c.avatarRef = avatarRef == null ? "" : avatarRef;
        c.temperature = temperature;
        c.topP = topP;
        c.maxTokens = maxTokens;
        c.entries.addAll(entries);
        c.imageRefs.addAll(refs);
        out.conversations.add(c);
    }

    private static boolean currentDuplicated(JSONArray current, JSONArray threads) {
        Set<String> ids = messageIds(current);
        for (int i = 0; threads != null && i < threads.length(); i++) {
            JSONObject thread = threads.optJSONObject(i);
            JSONArray messages = thread == null ? null : thread.optJSONArray("messages");
            if (messages == null || messages.length() != current.length()) continue;
            Set<String> other = messageIds(messages);
            if (!ids.isEmpty() && ids.size() == other.size() && other.containsAll(ids)) return true;
            if (ids.isEmpty() && other.isEmpty()
                    && signature(current).equals(signature(messages))) return true;
        }
        return false;
    }

    private static Set<String> messageIds(JSONArray messages) {
        Set<String> ids = new HashSet<>();
        for (int i = 0; messages != null && i < messages.length(); i++) {
            JSONObject m = messages.optJSONObject(i);
            String id = m == null ? "" : m.optString("id", "").trim();
            if (!id.isEmpty()) ids.add(id);
        }
        return ids;
    }

    private static String signature(JSONArray messages) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; messages != null && i < messages.length(); i++) {
            JSONObject m = messages.optJSONObject(i);
            if (m == null) continue;
            sb.append(m.optString("role", "")).append('|')
                    .append(m.optLong("timestamp", 0L)).append('|')
                    .append(messageText(m)).append('\n');
        }
        return sb.toString();
    }

    private static String firstSystemPrompt(JSONArray messages) {
        for (int i = 0; messages != null && i < messages.length(); i++) {
            JSONObject m = messages.optJSONObject(i);
            if (m == null || !"system".equals(m.optString("role", ""))) continue;
            String text = messageText(m);
            if (!text.isEmpty()) return text;
        }
        return "";
    }

    private static List<OxChatEntry> convertMessages(JSONArray messages, Archive archive,
            List<String> imageRefs) {
        List<OxChatEntry> out = new ArrayList<>();
        if (messages == null) return out;
        boolean consumedSystem = false;
        int imageCount = 0;
        long now = System.currentTimeMillis();
        for (int i = 0; i < messages.length() && out.size() < MAX_MESSAGES_PER_CONVERSATION; i++) {
            JSONObject msg = messages.optJSONObject(i);
            if (msg == null) continue;
            String role = msg.optString("role", "").trim();
            String text = messageText(msg);
            String ref = imageRef(msg, archive);
            if (!ref.isEmpty() && imageCount >= MAX_IMAGES_PER_CONVERSATION) ref = "";
            if (!ref.isEmpty()) {
                imageCount++;
                text = text.replaceFirst("\\[图片\\]", "").trim();
            }
            if ("system".equals(role)) {
                if (!consumedSystem && !text.isEmpty()) {
                    consumedSystem = true;
                    continue;
                }
                if (text.isEmpty() && ref.isEmpty()) continue;
                role = "assistant";
                if (!text.isEmpty()) text = "[系统] " + text;
            } else if ("tool".equals(role)) {
                role = "assistant";
                if (!text.isEmpty()) text = "[工具] " + text;
            } else if (!"user".equals(role) && !"assistant".equals(role)) {
                role = "assistant";
            }
            if (text.isEmpty() && ref.isEmpty()) continue;
            long ts = msg.optLong("timestamp", 0L);
            if (ts <= 0L) ts = now + i;
            if (text.length() > MAX_MESSAGE_CHARS) text = text.substring(0, MAX_MESSAGE_CHARS);
            out.add(new OxChatEntry(role, text, ts));
            imageRefs.add(ref);
        }
        return out;
    }

    private static String imageRef(JSONObject msg, Archive archive) {
        JSONArray parts = msg.optJSONArray("contentParts");
        for (int i = 0; parts != null && i < parts.length(); i++) {
            JSONObject part = parts.optJSONObject(i);
            if (part == null || !"image".equals(part.optString("type", ""))) continue;
            String key = part.optString("storageKey", "").trim();
            if (!key.isEmpty() && archive.images.containsKey(key)) return key;
            String url = part.optString("url", "").trim();
            if (!url.isEmpty() && archive.images.containsKey(url)) return url;
        }
        return "";
    }

    private static String messageText(JSONObject msg) {
        StringBuilder sb = new StringBuilder();
        JSONArray parts = msg.optJSONArray("contentParts");
        boolean hasParts = false;
        for (int i = 0; parts != null && i < parts.length(); i++) {
            JSONObject part = parts.optJSONObject(i);
            if (part == null) continue;
            hasParts = true;
            String type = part.optString("type", "");
            if ("text".equals(type) || "info".equals(type)) {
                appendText(sb, part.optString("text", ""));
            } else if ("image".equals(type)) {
                appendText(sb, "[图片]");
            } else if ("file".equals(type)) {
                String fileName = part.optString("fileName", part.optString("name", ""));
                appendText(sb, fileName.isEmpty() ? "[文件]" : "[文件] " + fileName);
            } else if ("tool-call".equals(type)) {
                StringBuilder tool = new StringBuilder("[工具] ")
                        .append(part.optString("toolName", "tool"));
                String state = part.optString("state", "");
                if (!state.isEmpty()) tool.append(" (").append(state).append(')');
                String result = part.optString("result", "");
                if (!result.trim().isEmpty()) tool.append('\n').append(result);
                appendText(sb, tool.toString());
            }
        }
        if (!hasParts || sb.length() == 0) {
            appendText(sb, msg.optString("content", ""));
        }
        return sb.toString().trim();
    }

    private static void appendText(StringBuilder sb, String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (sb.length() > 0) sb.append('\n');
        sb.append(text.replace("\r\n", "\n").trim());
    }

    private static boolean applyConversationProvider(OxAgent a, Conversation c,
            Map<String, JSONObject> providers, OxAgent fallback) {
        JSONObject config = c.providerId.isEmpty() ? null : providers.get(c.providerId);
        String baseUrl = config == null ? "" : config.optString("baseUrl", "").trim();
        if (config != null && !baseUrl.isEmpty()) {
            a.baseUrl = baseUrl;
            a.apiKey = config.optString("apiKey", "");
            a.model = c.model.isEmpty() ? firstModel(config) : c.model;
            a.contextLength = 0;
            return true;
        }
        if (fallback != null) {
            a.baseUrl = fallback.baseUrl;
            a.apiKey = fallback.apiKey;
            a.model = c.model.isEmpty() ? fallback.model : c.model;
            a.contextLength = fallback.contextLength;
        } else {
            a.baseUrl = "";
            a.apiKey = "";
            a.model = c.model;
        }
        return false;
    }

    private static String conversationAgentId(String id) {
        String safe = id == null ? "" : id.replaceAll("[^a-zA-Z0-9_-]", "");
        if (safe.isEmpty()) safe = "conversation";
        return "kelivo_chat_" + safe;
    }

    private static void scan(JSONObject o, Archive out, int depth) {
        if (o == null || depth > 3) return;
        if (out.defaultPrompt.isEmpty()) {
            String prompt = o.optString("defaultPrompt", "").trim();
            if (!prompt.isEmpty()) out.defaultPrompt = prompt;
        }
        addProviders(o.opt("provider_configs_v1"), out);
        addProviders(o.opt("providers"), out);
        addMcp(o.opt("mcp_servers_v1"), out);
        addMcp(o.opt("mcpServers"), out);
        JSONArray direct = o.optJSONArray("myCopilots");
        if (direct != null) addArray(direct, out);
        Object assistants = o.opt("assistants_v1");
        if (assistants == null) assistants = o.opt("assistants");
        addValue(assistants, out);
        JSONArray keys = o.names();
        for (int i = 0; keys != null && i < keys.length(); i++) {
            Object v = o.opt(keys.optString(i));
            if (v instanceof JSONObject) scan((JSONObject) v, out, depth + 1);
        }
    }

    private static void addMcp(Object value, Archive out) {
        try {
            if (value instanceof String) {
                String s = ((String) value).trim();
                if (s.startsWith("[")) addMcp(new JSONArray(s), out);
                else if (s.startsWith("{")) addMcp(new JSONObject(s), out);
            } else if (value instanceof JSONArray) {
                JSONArray arr = (JSONArray) value;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject s = arr.optJSONObject(i);
                    if (s != null) addMcpEntry(s, "", out);
                }
            } else if (value instanceof JSONObject) {
                JSONObject map = (JSONObject) value;
                if (map.has("mcpServers")) {
                    addMcp(map.opt("mcpServers"), out);
                    return;
                }
                if (map.has("url") || map.has("baseUrl") || map.has("command")) {
                    addMcpEntry(map, "", out);
                    return;
                }
                JSONArray keys = map.names();
                for (int i = 0; keys != null && i < keys.length(); i++) {
                    String key = keys.optString(i);
                    JSONObject s = map.optJSONObject(key);
                    if (s != null) addMcpEntry(s, key, out);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void addMcpEntry(JSONObject s, String fallbackId, Archive out) {
        String id = s.optString("id", fallbackId).trim();
        String url = mcpUrl(s);
        String command = s.optString("command", "").trim();
        if (url.isEmpty() && command.isEmpty()) return;
        for (JSONObject existing : out.mcpServers) {
            if (!id.isEmpty() && id.equals(existing.optString("id", ""))) return;
            if (!url.isEmpty() && url.equals(mcpUrl(existing))) return;
        }
        out.mcpServers.add(s);
    }

    private static boolean isHttpMcp(JSONObject s) {
        String type = s.optString("type", "").toLowerCase(java.util.Locale.ROOT);
        if (type.contains("sse") || type.contains("stdio") || type.contains("inmemory")) return false;
        if (!s.optString("command", "").trim().isEmpty()) return false;
        return !mcpUrl(s).isEmpty();
    }

    private static String mcpUrl(JSONObject s) {
        String url = s.optString("baseUrl", "").trim();
        if (url.isEmpty()) url = s.optString("url", "").trim();
        return url;
    }

    private static boolean mcpExists(String url) {
        for (OxMcpManager.McpServer s : OxMcpManager.getServers()) {
            if (s.url != null && s.url.equals(url)) return true;
        }
        return false;
    }

    private static JSONObject mcpHeaders(Object raw) {
        JSONObject out = new JSONObject();
        try {
            if (raw instanceof JSONObject) {
                JSONObject map = (JSONObject) raw;
                JSONArray keys = map.names();
                for (int i = 0; keys != null && i < keys.length(); i++) {
                    String key = keys.optString(i);
                    out.put(key, map.optString(key, ""));
                }
            } else if (raw instanceof JSONArray) {
                JSONArray arr = (JSONArray) raw;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject h = arr.optJSONObject(i);
                    if (h == null) continue;
                    String name = h.optString("name", h.optString("key", "")).trim();
                    if (!name.isEmpty()) out.put(name, h.optString("value", ""));
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void addProviders(Object value, Archive out) {
        try {
            if (value instanceof String) {
                String s = ((String) value).trim();
                if (s.startsWith("[")) addProviders(new JSONArray(s), out);
                else if (s.startsWith("{")) addProviders(new JSONObject(s), out);
            } else if (value instanceof JSONArray) {
                JSONArray arr = (JSONArray) value;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject p = arr.optJSONObject(i);
                    if (p != null) addProvider(p, "", out);
                }
            } else if (value instanceof JSONObject) {
                JSONObject map = (JSONObject) value;
                if (!map.optString("baseUrl", "").isEmpty()) {
                    addProvider(map, "", out);
                    return;
                }
                JSONArray keys = map.names();
                for (int i = 0; keys != null && i < keys.length(); i++) {
                    String key = keys.optString(i);
                    JSONObject p = map.optJSONObject(key);
                    if (p != null) addProvider(p, key, out);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void addProvider(JSONObject p, String fallbackId, Archive out) {
        if (p == null) return;
        String id = p.optString("id", p.optString("name", fallbackId)).trim();
        String baseUrl = p.optString("baseUrl", "").trim();
        if (id.isEmpty() || baseUrl.isEmpty()) return;
        out.providers.put(id, p);
    }

    private static void addValue(Object value, Archive out) {
        try {
            if (value instanceof JSONArray) {
                addArray((JSONArray) value, out);
            } else if (value instanceof JSONObject) {
                JSONArray keys = ((JSONObject) value).names();
                for (int i = 0; keys != null && i < keys.length(); i++) {
                    Object v = ((JSONObject) value).opt(keys.optString(i));
                    if (v instanceof JSONObject) addObject((JSONObject) v, out);
                }
            } else if (value instanceof String) {
                String s = ((String) value).trim();
                if (s.startsWith("[")) addArray(new JSONArray(s), out);
                else if (s.startsWith("{")) addObject(new JSONObject(s), out);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void addArray(JSONArray arr, Archive out) {
        Set<String> seen = new HashSet<>();
        for (JSONObject o : out.assistants) seen.add(key(o));
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            if (o.optJSONArray("messages") != null
                    && o.optString("prompt", "").isEmpty()
                    && o.optString("systemPrompt", "").isEmpty()) {
                addSession(o, o.optString("id", ""), o.optString("name", ""), out);
                continue;
            }
            if (o.optString("prompt", "").isEmpty() && o.optString("systemPrompt", "").isEmpty()
                    && o.optString("name", "").isEmpty()) continue;
            if (seen.add(key(o))) out.assistants.add(o);
        }
    }

    private static void addObject(JSONObject o, Archive out) {
        if (o == null) return;
        Set<String> seen = new HashSet<>();
        for (JSONObject item : out.assistants) seen.add(key(item));
        if (seen.add(key(o))) out.assistants.add(o);
    }

    private static String key(JSONObject o) {
        String id = o.optString("id", "").trim();
        return id.isEmpty() ? name(o) : id;
    }

    private static String name(JSONObject o) {
        String name = o.optString("name", "").trim();
        if (name.isEmpty()) name = o.optString("title", "").trim();
        return name.isEmpty() ? "Kelivo Assistant" : name;
    }

    private static String agentId(String sourceId, String name) {
        String safe = sourceId == null ? "" : sourceId.replaceAll("[^a-zA-Z0-9_-]", "");
        if (safe.isEmpty()) safe = Integer.toHexString((name == null ? "" : name).hashCode());
        return "kelivo_" + safe;
    }

    private static OxAgent findById(String id) {
        for (OxAgent a : OxAgentStore.listAgents()) {
            if (id.equals(a.id)) return a;
        }
        return null;
    }

    private static OxAgent existingProvider() {
        for (OxAgent a : OxAgentStore.listAgents()) {
            if (a.baseUrl != null && !a.baseUrl.trim().isEmpty()
                    && a.apiKey != null && !a.apiKey.isEmpty()) {
                return a;
            }
        }
        return null;
    }

    private static String firstModel(JSONObject provider) {
        JSONArray models = provider.optJSONArray("models");
        for (int i = 0; models != null && i < models.length(); i++) {
            Object value = models.opt(i);
            if (value instanceof String) {
                String model = ((String) value).trim();
                if (!model.isEmpty()) return model;
            } else if (value instanceof JSONObject) {
                JSONObject model = (JSONObject) value;
                String id = model.optString("apiModelId", model.optString("id",
                        model.optString("modelId", ""))).trim();
                if (!id.isEmpty()) return id;
            }
        }
        return "";
    }

    private static JSONArray headers(JSONArray list) {
        JSONArray out = new JSONArray();
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject h = list.optJSONObject(i);
            if (h == null) continue;
            String name = h.optString("name", h.optString("key", "")).trim();
            if (name.isEmpty()) continue;
            out.put(new JSONArray().put(name).put(h.optString("value", "")));
        }
        return out;
    }
}
