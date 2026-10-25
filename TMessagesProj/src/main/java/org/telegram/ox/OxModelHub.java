package org.telegram.ox;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible chat completions client for OrionekoX agents. Speaks the same wire
 * protocol for cloud (DeepSeek/OpenAI/...) and local (Ollama, llama.cpp server, container
 * dsh/opencode) backends: baseUrl + /chat/completions with streaming SSE, falling back to
 * plain JSON when a local server ignores stream=true.
 *
 * One round per call; the multi-round tool-calling loop is driven by the UI so permission
 * dialogs never block a thread.
 */
public class OxModelHub {

    /** Result of one completion round. */
    public static class Round {
        public String content = "";
        public final List<ToolCall> toolCalls = new ArrayList<>();
        public boolean hasToolCalls() { return !toolCalls.isEmpty(); }
    }

    public static class ToolCall {
        public String id = "";
        public String name = "";
        public String arguments = "";   // JSON string, may be {}
    }

    public interface RoundCallback {
        /** Streaming text delta (final answer text only, not tool args). */
        default void onDelta(String text) {}
        /** Round finished: either content, or toolCalls to execute, or both. */
        void onRound(Round round);
        void onError(String message);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void completeRound(final OxAgent agent, final JSONArray messages, final boolean withTools, final RoundCallback cb) {
        if (agent.isEchoMode()) {
            // Echo mode has no model to decide tool calls - surface that clearly.
            MAIN.post(() -> cb.onError("echo mode: no model backend configured"));
            return;
        }
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject body = new JSONObject();
                body.put("model", agent.model == null ? "" : agent.model);
                body.put("messages", messages);
                body.put("stream", true);
                if (agent.temperature >= 0) body.put("temperature", agent.temperature);
                if (agent.topP >= 0) body.put("top_p", agent.topP);
                if (agent.maxTokens > 0) body.put("max_tokens", agent.maxTokens);
                if (agent.presencePenalty != 0) body.put("presence_penalty", agent.presencePenalty);
                if (agent.frequencyPenalty != 0) body.put("frequency_penalty", agent.frequencyPenalty);
                if (agent.topK > 0) body.put("top_k", agent.topK);
                if (agent.seed >= 0) body.put("seed", agent.seed);
                if (agent.stop != null && !agent.stop.trim().isEmpty()) {
                    JSONArray stop = new JSONArray();
                    for (String part : agent.stop.split(",")) {
                        if (!part.trim().isEmpty()) stop.put(part.trim());
                    }
                    if (stop.length() > 0) body.put("stop", stop);
                }
                if (agent.reasoningEffort != null && !agent.reasoningEffort.isEmpty()
                        && !"auto".equals(agent.reasoningEffort)) {
                    body.put("reasoning_effort", agent.reasoningEffort);
                }
                java.util.Collection<OxTool> availableTools;
                if (withTools && OxMcpManager.hasEnabledServers() && OxMcpManager.mcpToolCount() == 0) {
                    // first use in this process: fetch MCP tool lists synchronously -
                    // we are on the worker thread, so a blocking initialize is safe
                    OxMcpManager.refreshEnabledServersSync();
                } else {
                    OxMcpManager.ensureRefreshedAsync();
                }
                availableTools = OxMcpManager.tools();
                if (withTools && !availableTools.isEmpty()) {
                    JSONArray tools = new JSONArray();
                    for (OxTool t : availableTools) {
                        JSONObject fn = new JSONObject();
                        fn.put("name", t.name());
                        fn.put("description", t.description());
                        fn.put("parameters", t.parameters());
                        tools.put(new JSONObject().put("type", "function").put("function", fn));
                    }
                    body.put("tools", tools);
                    if (agent.toolChoice != null && !agent.toolChoice.isEmpty()
                            && !"auto".equals(agent.toolChoice)) {
                        body.put("tool_choice", agent.toolChoice);
                    } else {
                        body.put("tool_choice", "auto");
                    }
                    if (agent.parallelToolCalls == false) {
                        body.put("parallel_tool_calls", false);
                    }
                }
                if (agent.extraBody != null && !agent.extraBody.trim().isEmpty()) {
                    try {
                        JSONObject extra = new JSONObject(agent.extraBody);
                        java.util.Iterator<String> keys = extra.keys();
                        while (keys.hasNext()) {
                            String k = keys.next();
                            if ("messages".equals(k) || "stream".equals(k)) continue;
                            body.put(k, extra.get(k));
                        }
                    } catch (Exception ignored) {
                    }
                }

                String url = agent.baseUrl.replaceAll("/+$", "") + "/chat/completions";
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                if (agent.apiKey != null && !agent.apiKey.trim().isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + agent.apiKey);
                }
                conn.setRequestProperty("Accept", "text/event-stream");
                applyHeaders(conn, agent);
                conn.setConnectTimeout(30_000);
                conn.setReadTimeout(120_000);
                conn.setDoOutput(true);
                conn.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));

                int code = conn.getResponseCode();
                if (code != 200) {
                    String err = readAll(conn.getErrorStream());
                    postError(cb, "HTTP " + code + (err.isEmpty() ? "" : ": " + err.substring(0, Math.min(err.length(), 300))));
                    return;
                }

                String ctype = conn.getHeaderField("Content-Type");
                Round round;
                if (ctype != null && ctype.contains("text/event-stream")) {
                    round = parseStream(conn.getInputStream(), cb);
                } else {
                    // Local servers (llama.cpp & friends) sometimes ignore stream=true.
                    round = parseJsonResponse(readAll(conn.getInputStream()));
                }
                final Round r = round;
                MAIN.post(() -> cb.onRound(r));
            } catch (Exception e) {
                FileLog.e("OxModelHub", e);
                postError(cb, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "ox-model").start();
    }

    // ---- streaming: accumulate content deltas AND tool_calls fragments (indexed) ----

    private static Round parseStream(InputStream in, RoundCallback cb) throws Exception {
        Round round = new Round();
        Map<Integer, ToolCall> calls = new LinkedHashMap<>();
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String payload = line.substring(5).trim();
                if (payload.isEmpty() || "[DONE]".equals(payload)) continue;
                JSONObject chunk = new JSONObject(payload);
                JSONArray choices = chunk.optJSONArray("choices");
                if (choices == null || choices.length() == 0) continue;
                JSONObject choice = choices.getJSONObject(0);
                JSONObject delta = choice.optJSONObject("delta");
                if (delta != null) {
                    String piece = delta.optString("content", "");
                    if (!piece.isEmpty()) {
                        content.append(piece);
                        final String p = piece;
                        MAIN.post(() -> cb.onDelta(p));
                    }
                    JSONArray tcs = delta.optJSONArray("tool_calls");
                    if (tcs != null) {
                        for (int i = 0; i < tcs.length(); i++) {
                            JSONObject tc = tcs.getJSONObject(i);
                            int idx = tc.optInt("index", 0);
                            ToolCall c = calls.computeIfAbsent(idx, k -> new ToolCall());
                            if (tc.has("id")) c.id = tc.getString("id");
                            JSONObject fn = tc.optJSONObject("function");
                            if (fn != null) {
                                if (fn.has("name")) c.name += fn.getString("name");
                                if (fn.has("arguments")) c.arguments += fn.getString("arguments");
                            }
                        }
                    }
                }
            }
        }
        round.content = content.toString();
        round.toolCalls.addAll(calls.values());
        round.toolCalls.removeIf(c -> c.name.isEmpty());
        return round;
    }

    // ---- non-streaming response ----

    private static Round parseJsonResponse(String body) throws Exception {
        Round round = new Round();
        JSONObject resp = new JSONObject(body);
        JSONArray choices = resp.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new IllegalArgumentException("no choices in response");
        }
        JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
        if (msg == null) throw new IllegalArgumentException("no message in response");
        round.content = msg.optString("content", "");
        JSONArray tcs = msg.optJSONArray("tool_calls");
        if (tcs != null) {
            for (int i = 0; i < tcs.length(); i++) {
                JSONObject tc = tcs.getJSONObject(i);
                ToolCall c = new ToolCall();
                c.id = tc.optString("id", "call_" + i);
                JSONObject fn = tc.optJSONObject("function");
                if (fn != null) {
                    c.name = fn.optString("name", "");
                    c.arguments = fn.optString("arguments", "{}");
                }
                if (!c.name.isEmpty()) round.toolCalls.add(c);
            }
        }
        return round;
    }

    private static void postError(final RoundCallback cb, final String msg) {
        MAIN.post(() -> cb.onError(msg));
    }

    private static String readAll(InputStream in) {
        if (in == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Applies user-defined headers last, so they can override Authorization if needed. */
    private static void applyHeaders(HttpURLConnection conn, OxAgent agent) {
        if (agent == null || agent.headers == null) return;
        for (int i = 0; i < agent.headers.length(); i++) {
            JSONArray pair = agent.headers.optJSONArray(i);
            if (pair == null || pair.length() < 2) continue;
            String name = pair.optString(0, "").trim();
            String value = pair.optString(1, "");
            if (name.isEmpty() || name.contains("\r") || name.contains("\n")
                    || value.contains("\r") || value.contains("\n")) {
                continue;
            }
            try {
                conn.setRequestProperty(name, value);
            } catch (Throwable ignored) {
            }
        }
    }

    public interface ModelsCallback {
        void onResult(java.util.List<String> ids, String error);
    }

    /** GET /models with the agent's auth and custom headers. */
    public static void fetchModels(final OxAgent agent, final ModelsCallback cb) {
        if (agent == null || agent.baseUrl == null || agent.baseUrl.trim().isEmpty()) {
            MAIN.post(() -> cb.onResult(new ArrayList<>(), "Base URL is empty"));
            return;
        }
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String url = agent.baseUrl.replaceAll("/+$", "") + "/models";
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                if (agent.apiKey != null && !agent.apiKey.trim().isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + agent.apiKey);
                }
                applyHeaders(conn, agent);
                conn.setConnectTimeout(15_000);
                conn.setReadTimeout(30_000);
                int code = conn.getResponseCode();
                String raw = code == 200 ? readAll(conn.getInputStream()) : readAll(conn.getErrorStream());
                if (code != 200) {
                    final String msg = "HTTP " + code + (raw.isEmpty() ? "" : ": " + raw.substring(0, Math.min(raw.length(), 160)));
                    MAIN.post(() -> cb.onResult(new ArrayList<>(), msg));
                    return;
                }
                JSONObject obj = new JSONObject(raw);
                JSONArray data = obj.optJSONArray("data");
                if (data == null) data = obj.optJSONArray("models");
                java.util.List<String> ids = new ArrayList<>();
                if (data != null) {
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject m = data.optJSONObject(i);
                        String id = m == null ? data.optString(i) : m.optString("id", m.optString("name", ""));
                        if (id == null || id.isEmpty()) continue;
                        String lower = id.toLowerCase();
                        if (lower.contains("embedding") || lower.contains("whisper") || lower.contains("tts")
                                || lower.contains("dall-e") || lower.contains("moderation")) {
                            continue;
                        }
                        if (!ids.contains(id)) ids.add(id);
                    }
                }
                java.util.Collections.sort(ids);
                final java.util.List<String> out = ids;
                MAIN.post(() -> cb.onResult(out, out.isEmpty() ? "No models returned" : null));
            } catch (Exception e) {
                final String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
                MAIN.post(() -> cb.onResult(new ArrayList<>(), msg));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "ox-model-list").start();
    }

    public interface TestCallback {
        void onResult(boolean ok, String message);
    }

    /** One tiny non-streaming round-trip used by the editor's "Test connection" row. */
    public static void test(final OxAgent agent, final TestCallback cb) {
        if (agent == null || agent.baseUrl == null || agent.baseUrl.trim().isEmpty()) {
            MAIN.post(() -> cb.onResult(false, "Base URL is empty"));
            return;
        }
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject body = new JSONObject();
                body.put("model", agent.model == null ? "" : agent.model);
                body.put("max_tokens", 8);
                body.put("stream", false);
                JSONArray messages = new JSONArray();
                messages.put(new JSONObject().put("role", "user").put("content", "Reply with OK."));
                body.put("messages", messages);

                String url = agent.baseUrl.replaceAll("/+$", "") + "/chat/completions";
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                if (agent.apiKey != null && !agent.apiKey.trim().isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + agent.apiKey);
                }
                conn.setRequestProperty("Accept", "application/json");
                applyHeaders(conn, agent);
                conn.setConnectTimeout(20_000);
                conn.setReadTimeout(30_000);
                conn.setDoOutput(true);
                conn.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));

                int code = conn.getResponseCode();
                String resp = code == 200 ? readAll(conn.getInputStream()) : readAll(conn.getErrorStream());
                String detail = resp.length() > 180 ? resp.substring(0, 180) : resp;
                boolean ok = code == 200;
                String message = "HTTP " + code + (detail.isEmpty() ? "" : " · " + detail);
                MAIN.post(() -> cb.onResult(ok, message));
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                MAIN.post(() -> cb.onResult(false, message));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "ox-model-test").start();
    }
}
