package org.telegram.ox;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes the local agents, their chats, avatars and attachments as a Chatbox v2
 * backup zip. Provider keys and base URLs are intentionally never exported.
 */
public class OxKelivoExporter {

    private static final int MAX_RESOURCE_BYTES = 2 * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 24L * 1024 * 1024;
    private static final int MAX_RESOURCES = 64;
    private static final int MAX_MEMORY_CHARS = 64 * 1024;
    private static final int MAX_TOTAL_MEMORY_CHARS = 256 * 1024;
    private static final int MAX_SKILLS = 50;
    private static final int MAX_SKILL_BYTES = 512 * 1024;
    private static final long MAX_TOTAL_SKILL_BYTES = 8L * 1024 * 1024;
    private static final int MAX_WORKSPACE_FILES = 200;
    private static final int MAX_WORKSPACE_FILE_BYTES = 2 * 1024 * 1024;
    private static final long MAX_TOTAL_WORKSPACE_BYTES = 8L * 1024 * 1024;
    private static final int MAX_WORKSPACE_DEPTH = 6;

    public static class Result {
        public int assistants;
        public int conversations;
        public int messages;
        public int resources;
        public int memories;
        public int skills;
        public int skillLinks;
        public int workspaceFiles;
        public final List<String> notes = new ArrayList<>();
    }

    private static class Resource {
        String id;
        String key;
        String path;
        String kind;
        String mime;
        String scope;
        final List<String> sessionIds = new ArrayList<>();
        byte[] bytes;
    }

    private static class Session {
        String id;
        byte[] bytes;
        JSONObject meta;
        final List<String> resourceIds = new ArrayList<>();
    }

    public static Result exportTo(Context context, Uri uri) throws Exception {
        return exportTo(context, uri, null);
    }

    public static Result exportTo(Context context, Uri uri, List<String> onlyIds) throws Exception {
        Result result = new Result();
        Set<String> only = onlyIds == null ? null : new HashSet<>(onlyIds);
        List<OxAgent> agents = new ArrayList<>();
        for (OxAgent a : OxAgentStore.listAgents()) {
            if (a != null && !a.builtIn && a.id != null && !a.id.isEmpty()
                    && (only == null || only.contains(a.id))) {
                agents.add(a);
            }
        }
        if (agents.isEmpty()) throw new IllegalStateException("no agents to export");

        List<JSONObject> copilots = new ArrayList<>();
        List<Session> sessions = new ArrayList<>();
        List<Resource> resources = new ArrayList<>();
        long totalBytes = 0;
        Set<String> usedSessionIds = new HashSet<>();
        int resourceIndex = 0;
        int memoryChars = 0;

        Map<String, byte[]> skillBodies = new LinkedHashMap<>();
        Set<String> exportedSkillIds = new HashSet<>();
        Set<String> referencedSkills = new HashSet<>();
        for (OxAgent a : agents) {
            for (int i = 0; a.skillIds != null && i < a.skillIds.length(); i++) {
                String raw = a.skillIds.optString(i, "");
                if (!raw.trim().isEmpty()) referencedSkills.add(OxSkillStore.skillId(raw));
            }
        }
        long skillBytes = 0;
        for (OxSkillStore.Skill skill : OxSkillStore.list()) {
            if (skillBodies.size() >= MAX_SKILLS) break;
            if (skill == null || skill.file == null || !skill.file.isFile()) continue;
            String rawId = skill.id != null && !skill.id.isEmpty() ? skill.id : skill.name;
            String id = OxSkillStore.skillId(rawId);
            if (id.isEmpty() || exportedSkillIds.contains(id)) continue;
            if (only != null && !referencedSkills.contains(id)) continue;
            if (skill.file.length() > MAX_SKILL_BYTES) {
                result.notes.add("技能过大，已跳过 " + id);
                continue;
            }
            byte[] body = readFile(skill.file);
            if (body == null || body.length == 0) continue;
            if (skillBytes + body.length > MAX_TOTAL_SKILL_BYTES) {
                result.notes.add("技能总量超出上限，已跳过后续技能");
                break;
            }
            exportedSkillIds.add(id);
            skillBodies.put(id, body);
            skillBytes += body.length;
        }
        result.skills = skillBodies.size();

        Map<String, byte[]> workspaceEntries = new LinkedHashMap<>();
        long[] workspaceBytes = new long[]{0};
        int[] workspaceCount = new int[]{0};

        for (OxAgent a : agents) {
            List<OxChatEntry> chat;
            try {
                chat = OxAgentStore.loadChat(a.id);
            } catch (Throwable t) {
                chat = new ArrayList<>();
            }
            boolean hasPrompt = a.agentsMd != null && !a.agentsMd.trim().isEmpty();
            if (chat.isEmpty() && !hasPrompt) continue;
            if (a.id != null && a.id.matches("[a-zA-Z0-9._-]+")) {
                File dir = OxAgentStore.workspaceDirIfExists(a.id);
                if (dir != null) {
                    collectWorkspace(dir, dir, a.id, 0, workspaceEntries,
                            workspaceBytes, workspaceCount, result);
                }
            }
            result.workspaceFiles = workspaceCount[0];
            result.assistants++;
            boolean sessionOnly = !chat.isEmpty();
            String memory = OxAgentStore.loadMemory(a.id, MAX_MEMORY_CHARS + 1);
            if (memory.length() > MAX_MEMORY_CHARS) {
                memory = memory.substring(memory.length() - MAX_MEMORY_CHARS);
                result.notes.add("长期记忆过长，已保留最近部分 " + a.id);
            }
            if (!memory.trim().isEmpty()) {
                int room = MAX_TOTAL_MEMORY_CHARS - memoryChars;
                if (memory.length() > room) {
                    memory = room > 0 ? memory.substring(memory.length() - room) : "";
                    result.notes.add("长期记忆超出备份总量上限，已截断 " + a.id);
                }
                if (!memory.trim().isEmpty()) {
                    memoryChars += memory.length();
                    result.memories++;
                }
            } else {
                memory = "";
            }
            JSONObject copilot = new JSONObject();
            copilot.put("id", a.id);
            copilot.put("orionekoAgentId", a.id);
            copilot.put("name", a.name == null ? a.id : a.name);
            copilot.put("prompt", a.agentsMd == null ? "" : a.agentsMd);
            copilot.put("chatModelProvider", "");
            copilot.put("chatModelId", a.model == null ? "" : a.model);
            copilot.put("temperature", a.temperature);
            copilot.put("topP", a.topP);
            copilot.put("maxTokens", a.maxTokens);
            copilot.put("customHeaders", new JSONArray());

            Resource avatar = null;
            if (a.avatarPath != null && !a.avatarPath.isEmpty()
                    && resources.size() < MAX_RESOURCES) {
                String mime = mimeForName(a.avatarPath);
                avatar = buildResource("res" + (resourceIndex++), "orioneko-" + UUID.randomUUID(),
                        "avatar", mime, "global", null, new File(a.avatarPath),
                        MAX_RESOURCE_BYTES, result);
                if (avatar != null) {
                    if (totalBytes + avatar.bytes.length > MAX_TOTAL_BYTES) {
                        avatar = null;
                        result.notes.add("头像超出总量上限，已跳过 " + a.id);
                    } else {
                        resources.add(avatar);
                        totalBytes += avatar.bytes.length;
                        copilot.put("avatar", new JSONObject()
                                .put("type", "storage-key")
                                .put("storageKey", avatar.key));
                    }
                }
            }
            if (!sessionOnly) {
                String bgPath = OxChatTheme.backgroundForAgent(a.tgId);
                if (!bgPath.isEmpty() && resources.size() < MAX_RESOURCES) {
                    Resource bg = buildResource("res" + (resourceIndex++),
                            "orioneko-" + UUID.randomUUID(), "background",
                            mimeForName(bgPath), "global", null, new File(bgPath),
                            MAX_RESOURCE_BYTES, result);
                    if (bg != null && totalBytes + bg.bytes.length <= MAX_TOTAL_BYTES) {
                        resources.add(bg);
                        totalBytes += bg.bytes.length;
                        copilot.put("background", new JSONObject()
                                .put("type", "storage-key")
                                .put("storageKey", bg.key));
                    } else if (bg != null) {
                        result.notes.add("背景超出总量上限，已跳过 " + a.id);
                    }
                }
                if (!memory.isEmpty()) copilot.put("orionekoMemory", memory);
                putSkillIds(copilot, a, exportedSkillIds, result);
                copilots.add(copilot);
                continue;
            }

            String sid = safeSessionId(a.id, usedSessionIds);
            usedSessionIds.add(sid);
            JSONObject session = new JSONObject();
            session.put("id", sid);
            session.put("name", a.name == null ? a.id : a.name);
            session.put("threadName", a.name == null ? a.id : a.name);
            session.put("orionekoAgentId", a.id);
            if (!memory.isEmpty()) session.put("orionekoMemory", memory);
            putSkillIds(session, a, exportedSkillIds, result);
            String bgPath = OxChatTheme.backgroundForDialog(a.tgId);
            if (bgPath.isEmpty()) bgPath = OxChatTheme.backgroundForAgent(a.tgId);
            if (!bgPath.isEmpty() && resources.size() < MAX_RESOURCES) {
                Resource bg = buildResource("res" + (resourceIndex++),
                        "orioneko-" + UUID.randomUUID(), "background",
                        mimeForName(bgPath), "session", sid, new File(bgPath),
                        MAX_RESOURCE_BYTES, result);
                if (bg != null && totalBytes + bg.bytes.length <= MAX_TOTAL_BYTES) {
                    resources.add(bg);
                    totalBytes += bg.bytes.length;
                    session.put("background", new JSONObject()
                            .put("type", "storage-key")
                            .put("storageKey", bg.key));
                } else if (bg != null) {
                    result.notes.add("背景超出总量上限，已跳过 " + a.id);
                }
            }
            JSONObject settings = new JSONObject();
            settings.put("provider", "");
            settings.put("modelId", a.model == null ? "" : a.model);
            settings.put("temperature", a.temperature);
            settings.put("topP", a.topP);
            settings.put("maxTokens", a.maxTokens);
            session.put("settings", settings);

            JSONArray messages = new JSONArray();
            int chatCount = 0;
            long now = System.currentTimeMillis();
            if (a.agentsMd != null && !a.agentsMd.trim().isEmpty()) {
                messages.put(message("m0", "system", a.agentsMd, now - 1, null));
            }
            int index = 1;
            for (OxChatEntry e : chat) {
                if (e == null) continue;
                String content = e.content == null ? "" : e.content;
                boolean hasImage = e.imagePath != null && new File(e.imagePath).isFile();
                boolean hasFile = e.filePath != null && new File(e.filePath).isFile();
                if (content.trim().isEmpty() && !hasImage && !hasFile) continue;

                JSONObject imagePart = null;
                JSONObject filePart = null;
                if (hasImage && resources.size() < MAX_RESOURCES) {
                    String mime = mimeForName(e.imagePath);
                    Resource r = buildResource("res" + (resourceIndex++),
                            "orioneko-" + UUID.randomUUID(), "image", mime,
                            "session", sid, new File(e.imagePath), MAX_RESOURCE_BYTES, result);
                    if (r != null && totalBytes + r.bytes.length <= MAX_TOTAL_BYTES) {
                        resources.add(r);
                        totalBytes += r.bytes.length;
                        imagePart = new JSONObject().put("type", "image").put("storageKey", r.key);
                    } else if (r != null) {
                        result.notes.add("图片超出总量上限，已跳过 " + e.imagePath);
                    }
                }
                if (hasFile && resources.size() < MAX_RESOURCES) {
                    String name = e.fileName != null && !e.fileName.isEmpty()
                            ? e.fileName : new File(e.filePath).getName();
                    String mime = mimeForName(name);
                    Resource r = buildResource("res" + (resourceIndex++),
                            "orioneko-" + UUID.randomUUID(), "raw-attachment", mime,
                            "session", sid, new File(e.filePath), MAX_RESOURCE_BYTES, result);
                    if (r != null && totalBytes + r.bytes.length <= MAX_TOTAL_BYTES) {
                        resources.add(r);
                        totalBytes += r.bytes.length;
                        filePart = new JSONObject().put("type", "file")
                                .put("fileName", name).put("storageKey", r.key);
                    } else if (r != null) {
                        result.notes.add("附件超出总量上限，已跳过 " + name);
                    }
                }

                String role = e.role == null ? "user" : e.role;
                JSONArray parts = new JSONArray();
                if (!content.trim().isEmpty()) {
                    parts.put(new JSONObject().put("type", "text").put("text", content));
                }
                if (imagePart != null) parts.put(imagePart);
                if (filePart != null) parts.put(filePart);
                if (parts.length() == 0) continue;
                messages.put(message("m" + index, role, null, e.ts > 0 ? e.ts : now, parts));
                chatCount++;
                index++;
            }
            if (chatCount == 0) {
                copilots.add(copilot);
                continue;
            }
            session.put("messages", messages);
            byte[] sessionBytes = session.toString().getBytes("UTF-8");
            Session out = new Session();
            out.id = sid;
            out.bytes = sessionBytes;
            out.meta = new JSONObject().put("id", sid)
                    .put("name", a.name == null ? a.id : a.name);
            if (avatar != null) out.meta.put("assistantAvatarKey", avatar.key);
            sessions.add(out);
            result.conversations++;
            result.messages += chatCount;
        }

        if (sessions.isEmpty() && copilots.isEmpty()) {
            throw new IllegalStateException("nothing to export");
        }

        // Attach session resource ids once all resources are known.
        for (Session s : sessions) {
            for (Resource r : resources) {
                if (r.scope.equals("session") && r.sessionIds.contains(s.id)) {
                    s.resourceIds.add(r.id);
                }
            }
        }

        byte[] settingsBytes = "{}".getBytes("UTF-8");
        byte[] copilotsBytes = new JSONArray(copilots).toString().getBytes("UTF-8");

        JSONObject manifest = new JSONObject();
        manifest.put("format", "chatbox-backup");
        manifest.put("formatVersion", 2);
        manifest.put("exportedAt", isoNow());
        manifest.put("application", new JSONObject()
                .put("name", "Chatbox").put("version", "1.22.0").put("platform", "android"));
        manifest.put("exportItems", new JSONArray().put("conversations")
                .put("copilot").put("setting"));
        JSONObject data = new JSONObject();
        data.put("settings", descriptor("settings.json", settingsBytes));
        data.put("copilots", descriptor("copilots.json", copilotsBytes));
        manifest.put("data", data);

        JSONArray sessionArray = new JSONArray();
        for (Session s : sessions) {
            JSONObject d = descriptor("sessions/" + s.id + "/session.json", s.bytes);
            d.put("id", s.id);
            d.put("meta", s.meta);
            d.put("resourceIds", new JSONArray(s.resourceIds));
            sessionArray.put(d);
        }
        manifest.put("sessions", sessionArray);

        JSONArray resourceArray = new JSONArray();
        for (Resource r : resources) {
            JSONObject d = descriptor(r.path, r.bytes);
            d.put("id", r.id);
            d.put("kind", r.kind);
            d.put("encoding", "data-url-base64");
            d.put("mimeType", r.mime);
            d.put("scope", r.scope);
            d.put("sessionIds", new JSONArray(r.sessionIds));
            d.put("originalStorageKeys", new JSONArray().put(r.key));
            resourceArray.put(d);
            result.resources++;
        }
        manifest.put("resources", resourceArray);
        manifest.put("warnings", new JSONArray());
        JSONObject stats = new JSONObject();
        stats.put("sessionCount", sessions.size());
        stats.put("resourceCount", resources.size());
        stats.put("warningCount", 0);
        stats.put("deduplicatedResourceCount", 0);
        manifest.put("stats", stats);
        byte[] manifestBytes = manifest.toString().getBytes("UTF-8");

        OutputStream raw = context.getContentResolver().openOutputStream(uri);
        if (raw == null) throw new IllegalStateException("cannot open output");
        ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(raw));
        try {
            put(zip, "manifest.json", manifestBytes);
            put(zip, "settings.json", settingsBytes);
            put(zip, "copilots.json", copilotsBytes);
            for (Session s : sessions) {
                put(zip, "sessions/" + s.id + "/session.json", s.bytes);
            }
            for (Resource r : resources) {
                put(zip, r.path, r.bytes);
            }
            for (Map.Entry<String, byte[]> skill : skillBodies.entrySet()) {
                put(zip, "skills/" + skill.getKey() + "/SKILL.md", skill.getValue());
            }
            for (Map.Entry<String, byte[]> file : workspaceEntries.entrySet()) {
                put(zip, file.getKey(), file.getValue());
            }
            zip.finish();
        } finally {
            zip.close();
        }
        return result;
    }

    private static JSONObject message(String id, String role, String text, long ts,
            JSONArray parts) throws Exception {
        JSONObject m = new JSONObject();
        m.put("id", id);
        m.put("role", role);
        m.put("timestamp", ts);
        JSONArray out = parts;
        if (out == null) {
            out = new JSONArray();
            out.put(new JSONObject().put("type", "text").put("text", text == null ? "" : text));
        }
        m.put("contentParts", out);
        return m;
    }

    /** Walks an agent workspace without following symlinks out of it. Entry
     *  names are workspace/&lt;agentId&gt;/&lt;relative path&gt;. */
    private static void collectWorkspace(File base, File dir, String agentId,
            int depth, Map<String, byte[]> out, long[] totalBytes,
            int[] count, Result result) {
        if (dir == null || depth > MAX_WORKSPACE_DEPTH || count[0] >= MAX_WORKSPACE_FILES) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        String basePath;
        try {
            basePath = base.getCanonicalPath();
        } catch (Throwable t) {
            return;
        }
        for (File child : children) {
            if (child == null || count[0] >= MAX_WORKSPACE_FILES) return;
            File canonical;
            try {
                canonical = child.getCanonicalFile();
            } catch (Throwable t) {
                continue;
            }
            if (!canonical.getPath().startsWith(basePath + File.separator)) continue;
            String rel = canonical.getPath().substring(basePath.length() + 1)
                    .replace(File.separatorChar, '/');
            if (rel.isEmpty() || rel.contains("..")) continue;
            if (canonical.isDirectory()) {
                collectWorkspace(base, canonical, agentId, depth + 1, out,
                        totalBytes, count, result);
                continue;
            }
            if (!canonical.isFile()) continue;
            long size = canonical.length();
            if (size <= 0) continue;
            if (size > MAX_WORKSPACE_FILE_BYTES) {
                result.notes.add("工作区文件过大，已跳过 " + rel);
                continue;
            }
            if (totalBytes[0] + size > MAX_TOTAL_WORKSPACE_BYTES) {
                result.notes.add("工作区总量超出上限，已跳过后续文件");
                return;
            }
            byte[] body = readFile(canonical);
            if (body == null || body.length == 0) continue;
            String key = "workspace/" + agentId + "/" + rel;
            if (out.containsKey(key)) continue;
            out.put(key, body);
            totalBytes[0] += body.length;
            count[0]++;
        }
    }

    private static JSONObject descriptor(String path, byte[] bytes) throws Exception {
        return new JSONObject()
                .put("path", path)
                .put("size", bytes.length)
                .put("checksum", new JSONObject()
                        .put("algorithm", "sha256").put("value", sha256(bytes)));
    }

    /** Writes only skill ids that are actually present in this export, so a
     *  restored agent never points at a missing SKILL.md. */
    private static void putSkillIds(JSONObject target, OxAgent a,
            Set<String> exportedSkillIds, Result result) throws Exception {
        if (a.skillIds == null || a.skillIds.length() == 0 || exportedSkillIds.isEmpty()) return;
        JSONArray ids = new JSONArray();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < a.skillIds.length(); i++) {
            String raw = a.skillIds.optString(i, "");
            if (raw.trim().isEmpty()) continue;
            String id = OxSkillStore.skillId(raw);
            if (exportedSkillIds.contains(id) && seen.add(id)) ids.put(id);
        }
        if (ids.length() > 0) {
            target.put("orionekoSkillIds", ids);
            result.skillLinks++;
        }
    }

    private static Resource buildResource(String id, String key, String kind, String mime,
            String scope, String sessionId, File file, int maxBytes, Result result) {
        if (file == null || !file.isFile()) return null;
        if (file.length() <= 0 || file.length() > maxBytes) {
            result.notes.add("资源过大，已跳过 " + file.getName());
            return null;
        }
        try {
            byte[] raw = readFile(file);
            if (raw == null) return null;
            byte[] encoded = ("data:" + mime + ";base64,"
                    + Base64.encodeToString(raw, Base64.NO_WRAP)).getBytes("UTF-8");
            Resource r = new Resource();
            r.id = id;
            r.key = key;
            r.kind = kind;
            r.mime = mime;
            r.scope = scope;
            if (sessionId != null) r.sessionIds.add(sessionId);
            r.bytes = encoded;
            r.path = "resources/" + key.replaceAll("[^a-zA-Z0-9_-]", "_")
                    + extension(mime);
            return r;
        } catch (Throwable t) {
            result.notes.add("资源读取失败，已跳过 " + file.getName());
            return null;
        }
    }

    private static byte[] readFile(File file) {
        try {
            if (file.length() > MAX_RESOURCE_BYTES) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            FileInputStream in = new FileInputStream(file);
            try {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                in.close();
            }
            return out.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void put(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] out = md.digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : out) sb.append(String.format(Locale.US, "%02x", b & 0xFF));
        return sb.toString();
    }

    private static String isoNow() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    private static String safeSessionId(String id, Set<String> used) {
        String safe = id == null ? "session" : id.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (safe.isEmpty()) safe = "session";
        String base = safe;
        int n = 2;
        while (used.contains(safe)) safe = base + "_" + (n++);
        return safe;
    }

    private static String mimeForName(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".txt") || n.endsWith(".md") || n.endsWith(".log")) return "text/plain";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".csv")) return "text/csv";
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".zip")) return "application/zip";
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html";
        if (n.endsWith(".xml")) return "application/xml";
        if (n.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (n.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (n.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".wav")) return "audio/wav";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".webm")) return "video/webm";
        return "application/octet-stream";
    }

    private static String extension(String mime) {
        String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        if (m.contains("png")) return ".png";
        if (m.contains("jpeg") || m.contains("jpg")) return ".jpg";
        if (m.contains("gif")) return ".gif";
        if (m.contains("webp")) return ".webp";
        if (m.contains("json")) return ".json";
        if (m.contains("pdf")) return ".pdf";
        if (m.contains("zip")) return ".zip";
        if (m.startsWith("text/") || m.contains("xml") || m.contains("html")) return ".txt";
        return ".bin";
    }
}
