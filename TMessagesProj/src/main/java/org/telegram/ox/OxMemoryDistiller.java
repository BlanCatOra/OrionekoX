package org.telegram.ox;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asynchronous memory distillation using the agent's own chat model. It turns
 * a compressed batch into an abstractive summary plus atomic facts. It never
 * blocks the chat turn and silently keeps the extractive fallback on failure.
 */
public class OxMemoryDistiller {

    private static final int MAX_TRANSCRIPT = 14000;
    private static final int MAX_RESPONSE = 2 * 1024 * 1024;
    private static final Set<String> RUNNING =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private static final String SYSTEM_PROMPT = "[MEMORY_DISTILLER]\n"
            + "You maintain durable long-term memory for a personal assistant. "
            + "Read the conversation transcript and return ONLY a JSON object:\n"
            + "{\"summary\":\"...\",\"facts\":[{\"text\":\"...\",\"importance\":0.8}]}\n"
            + "Rules: use the same language as the transcript; summary <= 1200 chars; "
            + "at most 12 atomic facts; each fact <= 300 chars; importance 0.3-0.95. "
            + "Preserve names, preferences, dates, plans and corrections. Resolve relative "
            + "dates using the timestamps in the transcript. Do not invent details. If the "
            + "transcript has nothing durable, return an empty summary and facts.";

    public static void distill(final OxAgent agent, final List<OxChatEntry> batch) {
        if (agent == null || batch == null || batch.size() < 8 || agent.isEchoMode()) return;
        final String key = agent.id + ":" + batch.get(0).ts + ":" + batch.size();
        if (!RUNNING.add(key)) return;
        new Thread(() -> {
            try {
                run(agent, batch);
            } catch (Throwable t) {
                FileLog.e("OxMemoryDistiller", t);
            } finally {
                RUNNING.remove(key);
            }
        }, "ox-memory-distill").start();
    }

    private static void run(OxAgent agent, List<OxChatEntry> batch) throws Exception {
        String transcript = transcript(batch);
        if (transcript.length() < 40) return;
        JSONObject body = new JSONObject();
        body.put("model", agent.model == null ? "" : agent.model);
        body.put("stream", false);
        body.put("temperature", 0.2);
        body.put("max_tokens", 1200);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", SYSTEM_PROMPT));
        messages.put(new JSONObject().put("role", "user")
                .put("content", "Conversation transcript:\n" + transcript));
        body.put("messages", messages);

        String url = agent.baseUrl.replaceAll("/+$", "") + "/chat/completions";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            if (agent.apiKey != null && !agent.apiKey.trim().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + agent.apiKey);
            }
            applyHeaders(conn, agent.headers);
            conn.setConnectTimeout(20_000);
            conn.setReadTimeout(90_000);
            conn.setDoOutput(true);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code != 200) {
                FileLog.d("OxMemoryDistiller HTTP " + code + " agent=" + agent.id);
                return;
            }
            String raw = readAll(conn.getInputStream(), MAX_RESPONSE);
            JSONObject root = new JSONObject(raw);
            JSONObject choice = root.optJSONArray("choices") == null
                    ? null : root.optJSONArray("choices").optJSONObject(0);
            JSONObject message = choice == null ? null : choice.optJSONObject("message");
            String content = message == null ? "" : content(message.opt("content"));
            JSONObject result = parseJson(content);
            if (result == null) {
                FileLog.d("OxMemoryDistiller invalid JSON agent=" + agent.id);
                return;
            }
            int facts = OxMemoryStore.ingestModelMemory(agent.id, result);
            FileLog.d("OxMemoryDistiller agent=" + agent.id + " facts=" + facts
                    + " summary=" + result.optString("summary", "").length());
        } finally {
            conn.disconnect();
        }
    }

    private static String transcript(List<OxChatEntry> batch) {
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, batch.size() - 120);
        for (int i = start; i < batch.size(); i++) {
            OxChatEntry e = batch.get(i);
            if (e == null || e.content == null || e.content.trim().isEmpty()) continue;
            String line = ("user".equals(e.role) ? "用户：" : "助手：")
                    + e.content.trim().replace('\n', ' ');
            if (line.length() > 800) line = line.substring(0, 800);
            sb.append(line).append('\n');
            if (sb.length() > MAX_TRANSCRIPT) break;
        }
        return sb.toString();
    }

    private static String content(Object raw) {
        if (raw instanceof String) return (String) raw;
        if (raw instanceof JSONArray) {
            StringBuilder sb = new StringBuilder();
            JSONArray parts = (JSONArray) raw;
            for (int i = 0; i < parts.length(); i++) {
                Object p = parts.opt(i);
                if (p instanceof JSONObject) {
                    String t = ((JSONObject) p).optString("text", "");
                    if (!t.isEmpty()) sb.append(t);
                }
            }
            return sb.toString();
        }
        return "";
    }

    private static JSONObject parseJson(String content) {
        if (content == null) return null;
        String s = content.trim();
        int fence = s.indexOf("```");
        if (fence >= 0) {
            int nl = s.indexOf('\n', fence + 3);
            if (nl >= 0) s = s.substring(nl + 1);
            int end = s.lastIndexOf("```");
            if (end >= 0) s = s.substring(0, end);
        }
        int first = s.indexOf('{');
        int last = s.lastIndexOf('}');
        if (first < 0 || last <= first) return null;
        try {
            return new JSONObject(s.substring(first, last + 1));
        } catch (Throwable t) {
            return null;
        }
    }

    private static void applyHeaders(HttpURLConnection conn, JSONArray headers) {
        if (headers == null) return;
        for (int i = 0; i < headers.length(); i++) {
            JSONArray pair = headers.optJSONArray(i);
            if (pair == null || pair.length() < 2) continue;
            String name = pair.optString(0, "").trim();
            String value = pair.optString(1, "");
            if (!name.isEmpty()) conn.setRequestProperty(name, value);
        }
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
}
