package org.telegram.ox;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
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
 * Imports the assistants, provider wiring and remote MCP servers from a RikkaHub
 * backup's {@code settings.json}. The archive's chat database and attachments are
 * deliberately ignored; OAuth tokens and SSE-only servers are not imported.
 */
public class OxRikkaImporter {

    private static final int MAX_SETTINGS = 4 * 1024 * 1024;

    public static class Preview {
        public int assistants;
        public int providers;
        public int mcp;
        public int skippedSse;
        public int existingAssistants;
        public int existingMcp;
        public final List<String> names = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
    }

    public static class Result {
        public int agentsCreated;
        public int agentsUpdated;
        public int mcpAdded;
        public int mcpSkipped;
        public final List<String> importedAgentIds = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
    }

    private static class ProviderRef {
        String key;
        String baseUrl;
        String apiKey;
        String model;
        String providerId;
        int contextLength;
        boolean enabled = true;
    }

    public static Preview preview(Context context, Uri uri) throws Exception {
        JSONObject settings = readSettings(context, uri);
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
        p.notes.add("仅导入 settings.json 的助手 / Provider / MCP；不含聊天数据库、附件和 OAuth token。");
        if (p.skippedSse > 0) p.notes.add("SSE 传输的 MCP 暂不导入（" + p.skippedSse + " 个）。");
        return p;
    }

    public static Result importFrom(Context context, Uri uri) throws Exception {
        JSONObject settings = readSettings(context, uri);
        Map<String, ProviderRef> models = providers(settings);
        String defaultModel = settings.optString("chatModelId", "");
        ProviderRef fallback = firstProvider(models);
        Result r = new Result();

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
            a.avatarPath = oldAvatar;
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
            OxAgentStore.saveAgent(a);
            r.importedAgentIds.add(a.id);
            if (update) r.agentsUpdated++;
            else r.agentsCreated++;
        }

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
        return r;
    }

    private static JSONObject readSettings(Context context, Uri uri) throws Exception {
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
            String json;
            if (a == 'P' && b == 'K') {
                json = null;
                ZipInputStream zin = new ZipInputStream(in);
                ZipEntry e;
                int entries = 0;
                while ((e = zin.getNextEntry()) != null) {
                    if (++entries > 1000) throw new IllegalArgumentException("too many entries");
                    if (e.isDirectory()) continue;
                    String n = e.getName();
                    if ("settings.json".equals(n) || n.endsWith("/settings.json")) {
                        json = readAll(zin);
                        break;
                    }
                }
            } else {
                json = readAll(in);
            }
            if (json == null) throw new IllegalArgumentException("settings.json missing");
            JSONObject root = new JSONObject(json);
            if (!root.has("assistants") && !root.has("providers") && !root.has("mcpServers")) {
                throw new IllegalArgumentException("not a RikkaHub settings file");
            }
            return root;
        } finally {
            try {
                in.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0 && bos.size() < MAX_SETTINGS) {
            bos.write(buf, 0, Math.min(n, MAX_SETTINGS - bos.size()));
        }
        return bos.toString("UTF-8");
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
        if ("emoji".equals(avatar.optString("type", ""))) {
            return avatar.optString("content", "").trim();
        }
        return "";
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
