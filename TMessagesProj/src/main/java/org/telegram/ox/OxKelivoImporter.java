package org.telegram.ox;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
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
 * Imports assistant/persona definitions from Kelivo / Chatbox backup files.
 * Only assistants are imported; conversations, provider keys, skills and MCP
 * configurations are left to their dedicated importers.
 */
public class OxKelivoImporter {

    private static final int MAX_JSON = 4 * 1024 * 1024;

    public static class Preview {
        public int assistants;
        public int existing;
        public int skills;
        public int existingSkills;
        public final List<String> names = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
    }

    public static class Result {
        public int created;
        public int updated;
        public int skills;
        public final List<String> ids = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
    }

    private static class Archive {
        final List<JSONObject> assistants = new ArrayList<>();
        final Map<String, JSONObject> providers = new HashMap<>();
        final Map<String, byte[]> skills = new HashMap<>();
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
        p.notes.add("仅导入 Kelivo / Chatbox 备份中的助手、人设与技能；不含对话、Provider 密钥与 MCP。");
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
            a.avatarPath = oldAvatar;
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
        if (!providersUsed.isEmpty()) {
            r.notes.add("使用备份中的 " + providersUsed.size() + " 个 Provider 配置接线");
        }
        if (providersUsed.isEmpty() && fallback == null) {
            r.notes.add("没有找到可继承的 Provider，导入的助手将按离线模式运行");
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
                    }
                }
                if (copilots != null) addArray(new JSONArray(copilots), out);
                if (assistants != null) addArray(new JSONArray(assistants), out);
                if (settings != null) scan(new JSONObject(settings), out, 0);
            } else {
                String json = readString(in);
                if (json == null || json.trim().isEmpty()) throw new IllegalArgumentException("empty file");
                String trimmed = json.trim();
                if (trimmed.startsWith("[")) {
                    addArray(new JSONArray(trimmed), out);
                } else {
                    scan(new JSONObject(trimmed), out, 0);
                }
            }
            if (out.assistants.isEmpty()) throw new IllegalArgumentException("no assistants found");
            return out;
        } finally {
            try { in.close(); } catch (Throwable ignored) {}
        }
    }

    private static String readString(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0 && bos.size() < MAX_JSON) {
            bos.write(buf, 0, Math.min(n, MAX_JSON - bos.size()));
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

    private static void scan(JSONObject o, Archive out, int depth) {
        if (o == null || depth > 3) return;
        addProviders(o.opt("provider_configs_v1"), out);
        addProviders(o.opt("providers"), out);
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
