package org.telegram.ox;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client for tools/ox-harness-bridge.py. The bridge owns the external harness
 * process and its native session; this class only moves normalized events into
 * the chat. No local model call and no local compact are involved for external
 * harnesses. If the bridge is missing or unreachable the caller must show that
 * as an error, never as a successful turn.
 */
public class OxHarnessTransport {

    public interface Callback {
        default void onText(String piece) {}
        default void onToolCall(String name, String args) {}
        default void onToolResult(String name, String content, boolean error) {}
        default void onStatus(String state, String message) {}
        default void onAttachment(String url, String kind) {}
        default void onContext(JSONObject context) {}
        default void onSession(String nativeSession) {}
        void onError(String message);
        void onDone(String text, String nativeSession);
    }

    public interface HealthCallback {
        void onResult(boolean ok, String message);
    }

    public interface ContextCallback {
        void onResult(boolean ok, String message);
    }

    private static final Map<String, JSONObject> LAST_CONTEXT = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_COMPACT_AT = new ConcurrentHashMap<>();
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int TURN_READ_TIMEOUT_MS = 900_000;
    private static final int COMPACT_READ_TIMEOUT_MS = 300_000;

    public static boolean supports(String harness) {
        return OxHarnessAdapter.CLAUDE.equals(harness)
                || OxHarnessAdapter.COMMANDCODE.equals(harness)
                || OxHarnessAdapter.OPENCODE.equals(harness)
                || OxHarnessAdapter.OPENCLAW.equals(harness);
    }

    public static boolean isConfigured(OxAgent agent) {
        return agent != null && agent.harnessEndpoint != null
                && !agent.harnessEndpoint.trim().isEmpty();
    }

    private static String endpoint(OxAgent agent) {
        String base = agent.harnessEndpoint == null ? "" : agent.harnessEndpoint.trim();
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://" + base;
        }
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }

    private static HttpURLConnection open(OxAgent agent, String path, String method, int readTimeout)
            throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint(agent) + path)
                .openConnection(Proxy.NO_PROXY);
        conn.setRequestMethod(method);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(readTimeout);
        conn.setRequestProperty("Accept", "application/x-ndjson, application/json");
        conn.setRequestProperty("Content-Type", "application/json");
        String token = agent.harnessToken == null ? "" : agent.harnessToken.trim();
        if (!token.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        if (!"GET".equals(method) && !"DELETE".equals(method)) {
            conn.setDoOutput(true);
        }
        return conn;
    }

    public static void turn(final OxAgent agent, final String system, final String text,
            final String imagePath, final Callback callback) {
        new Thread(() -> {
            try {
                runTurn(agent, system, text, imagePath, callback);
            } catch (Throwable t) {
                FileLog.e("OxHarnessTransport", t);
                callback.onError(t.getMessage() == null
                        ? t.getClass().getSimpleName() : t.getMessage());
            }
        }, "ox-harness-turn").start();
    }

    private static void runTurn(OxAgent agent, String system, String text,
            String imagePath, Callback callback) throws Exception {
        if (!isConfigured(agent)) {
            callback.onError("Harness 桥接地址未配置");
            return;
        }
        boolean retried = false;
        while (true) {
            if (agent.harnessSession == null || agent.harnessSession.trim().isEmpty()) {
                createSession(agent, system);
            }
            HttpURLConnection conn = null;
            try {
                conn = open(agent, "/v1/sessions/" + agent.harnessSession + "/turns",
                        "POST", TURN_READ_TIMEOUT_MS);
                JSONObject body = new JSONObject();
                body.put("text", text == null ? "" : text);
                body.put("timeout", 600);
                JSONArray attachments = attachments(imagePath);
                if (attachments.length() > 0) body.put("attachments", attachments);
                write(conn, body.toString());
                int code = conn.getResponseCode();
                if (code == 404 && !retried) {
                    FileLog.d("OxHarnessTransport: bridge session 404, creating a new one");
                    agent.harnessSession = "";
                    retried = true;
                    continue;
                }
                if (code != 200) {
                    callback.onError("桥接 HTTP " + code + ": " + clip(readAll(
                            code >= 400 ? conn.getErrorStream() : conn.getInputStream()), 600));
                    return;
                }
                parseEvents(conn.getInputStream(), agent.id, callback);
                return;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
    }

    private static void parseEvents(InputStream in, String agentId, Callback callback) throws Exception {
        String finalText = null;
        String nativeSession = "";
        boolean done = false;
        boolean failed = false;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                JSONObject event;
                try {
                    event = new JSONObject(line);
                } catch (Exception e) {
                    continue;
                }
                String type = event.optString("type", "");
                if ("session".equals(type)) {
                    nativeSession = event.optString("native_session", nativeSession);
                    callback.onSession(nativeSession);
                } else if ("text".equals(type)) {
                    String piece = event.optString("text", "");
                    if (!piece.isEmpty()) callback.onText(piece);
                } else if ("tool_call".equals(type)) {
                    callback.onToolCall(event.optString("name", "tool"),
                            event.optString("arguments", ""));
                } else if ("tool_result".equals(type)) {
                    callback.onToolResult(event.optString("name", "tool"),
                            event.optString("content", ""),
                            event.optBoolean("is_error", false));
                } else if ("command_output".equals(type)) {
                    callback.onStatus("command_output", event.optString("text", ""));
                } else if ("attachment".equals(type)) {
                    callback.onAttachment(event.optString("url", ""),
                            event.optString("kind", ""));
                } else if ("status".equals(type)) {
                    callback.onStatus(event.optString("state", ""),
                            event.optString("message", ""));
                } else if ("error".equals(type)) {
                    failed = true;
                    callback.onError(event.optString("message", "harness error"));
                    return;
                } else if ("done".equals(type)) {
                    finalText = event.optString("text", "");
                    nativeSession = event.optString("native_session", nativeSession);
                    JSONObject ctx = event.optJSONObject("context");
                    if (ctx != null) {
                        LAST_CONTEXT.put(agentId, ctx);
                        callback.onContext(ctx);
                    }
                    done = true;
                }
            }
        }
        if (!done && !failed) {
            callback.onError("Harness 事件流在 done 之前结束");
            return;
        }
        callback.onDone(finalText == null ? "" : finalText, nativeSession);
    }

    /** True when the harness itself reported that its context should be compacted. */
    public static boolean shouldCompact(String agentId) {
        JSONObject ctx = LAST_CONTEXT.get(agentId);
        if (ctx == null || !ctx.optBoolean("should_compact", false)) return false;
        long last = LAST_COMPACT_AT.getOrDefault(agentId, 0L);
        if (System.currentTimeMillis() - last < 60_000L) return false;
        LAST_COMPACT_AT.put(agentId, System.currentTimeMillis());
        return true;
    }

    public static void context(OxAgent agent, ContextCallback callback) {
        if (!isConfigured(agent)) {
            callback.onResult(false, "未填写桥接地址");
            return;
        }
        if (agent.harnessSession == null || agent.harnessSession.trim().isEmpty()) {
            callback.onResult(false, "暂无会话，请先发一条消息");
            return;
        }
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = open(agent, "/v1/sessions/" + agent.harnessSession + "/context",
                        "GET", 20_000);
                int code = conn.getResponseCode();
                String raw = readAll(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
                if (code != 200) {
                    callback.onResult(false, "HTTP " + code + " · " + clip(raw, 200));
                    return;
                }
                JSONObject obj = new JSONObject(raw);
                JSONObject ctx = obj.optJSONObject("context");
                if (ctx == null || ctx.length() == 0) {
                    callback.onResult(false, "暂无上下文数据，请先发一条消息");
                    return;
                }
                LAST_CONTEXT.put(agent.id, ctx);
                callback.onResult(true, contextSummary(ctx));
            } catch (Throwable t) {
                FileLog.e("OxHarnessTransport", t);
                callback.onResult(false, t.getMessage() == null
                        ? t.getClass().getSimpleName() : t.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "ox-harness-context").start();
    }

    private static String contextSummary(JSONObject ctx) {
        StringBuilder sb = new StringBuilder();
        long prompt = ctx.optLong("prompt_tokens", -1);
        long budget = ctx.optLong("context_budget", -1);
        if (prompt >= 0 && budget > 0) {
            sb.append(prompt).append(" / ").append(budget).append(" tok");
        } else {
            JSONObject usage = ctx.optJSONObject("usage");
            long in = usage == null ? -1 : firstLong(usage, "input_tokens", "inputTokens", "input");
            long out = usage == null ? -1 : firstLong(usage, "output_tokens", "outputTokens", "output");
            if (in >= 0 || out >= 0) {
                sb.append("in ").append(Math.max(0, in))
                        .append(" · out ").append(Math.max(0, out));
            }
        }
        if (ctx.has("should_compact")) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(ctx.optBoolean("should_compact") ? "建议 compact" : "无需 compact");
        }
        return sb.length() == 0 ? "已读取，Harness 未提供 token 统计" : sb.toString();
    }

    private static long firstLong(JSONObject o, String... keys) {
        for (String k : keys) {
            if (o.has(k) && !o.isNull(k)) return o.optLong(k, -1);
        }
        return -1;
    }

    private static void createSession(OxAgent agent, String system) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = open(agent, "/v1/sessions", "POST", 30_000);
            JSONObject body = new JSONObject();
            body.put("harness", agent.harness);
            if (agent.harnessCwd != null && !agent.harnessCwd.trim().isEmpty()) {
                body.put("cwd", agent.harnessCwd.trim());
            }
            if (agent.harnessModel != null && !agent.harnessModel.trim().isEmpty()) {
                body.put("model", agent.harnessModel.trim());
            }
            if (agent.harnessAgent != null && !agent.harnessAgent.trim().isEmpty()) {
                body.put("agent", agent.harnessAgent.trim());
            }
            if (system != null && !system.trim().isEmpty()) {
                body.put("system", clip(system, 60_000));
            }
            write(conn, body.toString());
            int code = conn.getResponseCode();
            String raw = readAll(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (code != 201 && code != 200) {
                throw new IllegalStateException("创建 Harness 会话失败 HTTP " + code
                        + ": " + clip(raw, 600));
            }
            JSONObject session = new JSONObject(raw).optJSONObject("session");
            if (session == null) throw new IllegalStateException("桥接未返回会话");
            String sid = session.optString("session_id", "");
            if (sid.isEmpty()) throw new IllegalStateException("桥接会话 id 为空");
            agent.harnessSession = sid;
            OxAgentStore.saveAgent(agent);
            FileLog.d("OxHarnessTransport: created bridge session " + sid
                    + " native=" + session.optString("native_session", ""));
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Runs the harness-native compact through the bridge. Blocking; call from a worker. */
    public static String compact(OxAgent agent) {
        if (!isConfigured(agent) || !supports(agent.harness)) return "transport-not-connected";
        if (agent.harnessSession == null || agent.harnessSession.trim().isEmpty()) {
            return "no active harness session";
        }
        HttpURLConnection conn = null;
        try {
            conn = open(agent, "/v1/sessions/" + agent.harnessSession + "/compact",
                    "POST", COMPACT_READ_TIMEOUT_MS);
            write(conn, "{}");
            int code = conn.getResponseCode();
            String raw = readAll(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            JSONObject obj = new JSONObject(raw);
            String label = OxHarnessAdapter.label(agent.harness);
            if (obj.optBoolean("ok", false)) {
                return "「" + label + "」原生压缩完成：" + clip(obj.optString("result", ""), 2000);
            }
            return "「" + label + "」原生压缩失败：" + clip(obj.optString("error", "未知错误"), 1200);
        } catch (Throwable t) {
            FileLog.e("OxHarnessTransport", t);
            return "「" + OxHarnessAdapter.label(agent.harness) + "」compact 请求失败："
                    + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static void health(OxAgent agent, HealthCallback callback) {
        if (!isConfigured(agent)) {
            callback.onResult(false, "未填写桥接地址");
            return;
        }
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = open(agent, "/v1/health", "GET", 15_000);
                int code = conn.getResponseCode();
                String raw = readAll(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
                if (code == 401) {
                    callback.onResult(false, "桥接拒绝访问（401，检查令牌）");
                    return;
                }
                if (code != 200) {
                    callback.onResult(false, "HTTP " + code + " · " + clip(raw, 200));
                    return;
                }
                JSONObject obj = new JSONObject(raw);
                JSONObject harness = obj.optJSONObject("harnesses") == null ? null
                        : obj.getJSONObject("harnesses").optJSONObject(agent.harness);
                String version = obj.optString("version", "?");
                if (harness == null) {
                    callback.onResult(false, "桥接 " + version + " 不认识 "
                            + OxHarnessAdapter.label(agent.harness));
                    return;
                }
                boolean found = harness.optBoolean("found", false);
                String bin = harness.optString("version", "");
                String transport = harness.optString("transport", "");
                callback.onResult(found, "桥接 " + version + " · "
                        + OxHarnessAdapter.label(agent.harness) + " "
                        + (found ? (bin.isEmpty() ? "已安装" : bin) : "未安装")
                        + (transport.isEmpty() ? "" : " · " + transport));
            } catch (Throwable t) {
                FileLog.e("OxHarnessTransport", t);
                callback.onResult(false, t.getMessage() == null
                        ? t.getClass().getSimpleName() : t.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "ox-harness-health").start();
    }

    private static JSONArray attachments(String imagePath) throws Exception {
        JSONArray out = new JSONArray();
        if (imagePath == null) return out;
        File f = new File(imagePath);
        if (!f.isFile() || f.length() <= 0 || f.length() > 12 * 1024 * 1024) return out;
        byte[] data = new byte[(int) f.length()];
        try (FileInputStream in = new FileInputStream(f)) {
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n <= 0) break;
                off += n;
            }
        }
        out.put(new JSONObject()
                .put("name", f.getName())
                .put("mime", "image/jpeg")
                .put("kind", "image")
                .put("base64", Base64.encodeToString(data, Base64.NO_WRAP)));
        return out;
    }

    private static void write(HttpURLConnection conn, String body) throws Exception {
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
            if (bos.size() > 1_000_000) break;
        }
        return bos.toString("UTF-8");
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
