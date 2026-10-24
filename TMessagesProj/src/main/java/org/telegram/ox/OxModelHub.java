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
import java.util.List;

// OpenAI-compatible chat client with SSE streaming, over plain HttpURLConnection
// (no extra dependencies). Echo mode when the agent has no key configured.
public class OxModelHub {

    public interface Callback {
        void onDelta(String text);
        void onDone(String full);
        void onError(String message);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int TIMEOUT_MS = 120_000;

    public static void chat(final OxAgent agent, final String userText, final Callback cb) {
        if (agent.isEchoMode()) {
            echo(agent, userText, cb);
            return;
        }
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                List<OxChatEntry> history = OxAgentStore.loadChat(agent.id);
                JSONArray messages = new JSONArray();
                if (agent.agentsMd != null && !agent.agentsMd.trim().isEmpty()) {
                    messages.put(new JSONObject().put("role", "system").put("content", agent.agentsMd));
                }
                int from = Math.max(0, history.size() - 20);
                for (int i = from; i < history.size(); i++) {
                    OxChatEntry e = history.get(i);
                    if ("user".equals(e.role) || "assistant".equals(e.role)) {
                        messages.put(new JSONObject().put("role", e.role).put("content", e.content));
                    }
                }
                messages.put(new JSONObject().put("role", "user").put("content", userText));

                JSONObject body = new JSONObject();
                body.put("model", agent.model == null ? "" : agent.model);
                body.put("messages", messages);
                body.put("stream", true);

                String url = agent.baseUrl.replaceAll("/+$", "") + "/chat/completions";
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + agent.apiKey);
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setConnectTimeout(30_000);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setDoOutput(true);
                conn.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));

                int code = conn.getResponseCode();
                if (code != 200) {
                    String err = readAll(conn.getErrorStream());
                    postError(cb, "HTTP " + code + (err.isEmpty() ? "" : ": " + err.substring(0, Math.min(err.length(), 300))));
                    return;
                }

                StringBuilder full = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.startsWith("data:")) continue;
                        String payload = line.substring(5).trim();
                        if (payload.isEmpty() || "[DONE]".equals(payload)) continue;
                        JSONObject chunk = new JSONObject(payload);
                        JSONArray choices = chunk.optJSONArray("choices");
                        if (choices == null || choices.length() == 0) continue;
                        JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                        if (delta == null) continue;
                        String piece = delta.optString("content", "");
                        if (!piece.isEmpty()) {
                            full.append(piece);
                            final String p = piece;
                            MAIN.post(() -> cb.onDelta(p));
                        }
                    }
                }
                MAIN.post(() -> cb.onDone(full.toString()));
            } catch (Exception e) {
                FileLog.e("OxModelHub", e);
                postError(cb, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "ox-model").start();
    }

    // Offline fallback so every agent works before any API key is configured.
    private static void echo(final OxAgent agent, final String userText, final Callback cb) {
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("⚡ 离线回声模式（未配置模型 API Key）\n\n");
            sb.append("你说：").append(userText).append('\n');
            if (agent.agentsMd != null && !agent.agentsMd.trim().isEmpty()) {
                String firstLine = agent.agentsMd.split("\n")[0].replaceFirst("^#+\\s*", "");
                sb.append("\n我的人设（AGENTS.md）：").append(firstLine);
            }
            sb.append("\n\n👉 打开右上角菜单 → 设置，填写 Base URL / API Key / 模型后即可接入真实模型。");
            final String full = sb.toString();
            MAIN.post(() -> {
                cb.onDelta(full);
                cb.onDone(full);
            });
        }).start();
    }

    private static void postError(final Callback cb, final String msg) {
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
}
