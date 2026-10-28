package org.telegram.ox;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remote MCP (Model Context Protocol) client + tool bridge, compatible with the servers of
 * the Operit/Operit2 plugin ecosystem. Speaks the Streamable HTTP transport (JSON-RPC 2.0
 * over POST, responses as JSON or SSE); each configured server's tools are merged into the
 * agent tool registry next to the built-ins, so any agent can call them through the normal
 * permission-gated tool loop.
 */
public class OxMcpManager {

    public static class McpServer {
        public String id;
        public String name;
        public String url;
        public boolean enabled = true;
        public String headersJson = "";   // optional extra headers, e.g. {"Authorization":"Bearer x"}
        // runtime state
        public volatile String sessionId;
        public volatile int toolCount = -1;      // -1 = never refreshed
        public volatile String lastError;

        public McpServer() {}

        public McpServer(String name, String url) {
            this.id = "mcp" + UUID.randomUUID().toString().substring(0, 8);
            this.name = name;
            this.url = url;
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name);
            o.put("url", url);
            o.put("enabled", enabled);
            o.put("headers", headersJson == null ? "" : headersJson);
            return o;
        }

        static McpServer fromJson(JSONObject o) throws Exception {
            McpServer s = new McpServer();
            s.id = o.optString("id");
            s.name = o.optString("name");
            s.url = o.optString("url");
            s.enabled = o.optBoolean("enabled", true);
            s.headersJson = o.optString("headers", "");
            return s;
        }
    }

    /** A remote MCP tool surfaced as a local OxTool. */
    public static class McpTool extends OxTool {
        public final McpServer server;
        public final String toolName;
        public final String desc;
        public final JSONObject inputSchema;

        McpTool(McpServer server, String name, String desc, JSONObject schema) {
            this.server = server;
            this.toolName = name;
            this.desc = desc == null ? "" : desc;
            this.inputSchema = schema == null ? new JSONObject() : schema;
        }

        @Override public String name() { return toolName; }
        @Override public String description() {
            return desc.isEmpty() ? ("MCP tool from " + server.name) : desc;
        }
        @Override public String defaultPolicy() { return POLICY_ASK; }
        @Override public JSONObject parameters() { return inputSchema; }
        @Override public String execute(JSONObject args) throws Exception {
            return callTool(server, toolName, args == null ? new JSONObject() : args);
        }
    }

    private static final Object IO_LOCK = new Object();
    private static final List<McpServer> servers = new ArrayList<>();
    private static final Map<String, OxTool> mcpTools = new ConcurrentHashMap<>();
    private static volatile boolean loaded;

    // ---------------- store ----------------

    private static File storeFile() {
        return new File(new File(ApplicationLoader.applicationContext.getFilesDir(), "oxmcp"), "servers.json");
    }

    public static synchronized List<McpServer> getServers() {
        loadIfNeeded();
        return servers;
    }

    /** Writes {mcpServers:{name:{type,url,isActive}}} without headers or secrets. */
    public static int exportJson(Context ctx, Uri uri) throws Exception {
        if (ctx == null || uri == null) throw new IllegalArgumentException("missing uri");
        List<McpServer> list = getServers();
        JSONObject map = new JSONObject();
        java.util.HashSet<String> used = new java.util.HashSet<>();
        int count = 0;
        for (McpServer s : list) {
            if (s == null || s.url == null || !s.url.startsWith("http")) continue;
            String base = s.name == null ? "" : s.name.trim();
            if (base.isEmpty()) base = "MCP";
            String key = base;
            int n = 2;
            while (!used.add(key)) key = base + " " + (n++);
            JSONObject c = new JSONObject();
            c.put("type", "streamableHttp");
            c.put("url", s.url);
            c.put("isActive", s.enabled);
            map.put(key, c);
            count++;
        }
        if (count == 0) throw new IllegalStateException("no servers to export");
        JSONObject root = new JSONObject();
        root.put("mcpServers", map);
        java.io.OutputStream out = ctx.getContentResolver().openOutputStream(uri);
        if (out == null) throw new IllegalStateException("cannot open output");
        try {
            out.write(root.toString().getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }
        return count;
    }

    private static void loadIfNeeded() {
        if (loaded) return;
        synchronized (OxMcpManager.class) {
            if (loaded) return;
            loaded = true;
            try {
                File f = storeFile();
                if (!f.exists()) return;
                JSONArray arr = new JSONArray(readFile(f));
                for (int i = 0; i < arr.length(); i++) {
                    McpServer s = McpServer.fromJson(arr.getJSONObject(i));
                    if (s.id != null && !s.id.isEmpty() && s.url != null && !s.url.isEmpty()) {
                        servers.add(s);
                    }
                }
            } catch (Exception e) {
                FileLog.e("OxMcp", e);
            }
        }
    }

    public static void saveServer(McpServer s) {
        loadIfNeeded();
        synchronized (servers) {
            boolean found = false;
            for (int i = 0; i < servers.size(); i++) {
                if (servers.get(i).id.equals(s.id)) {
                    servers.set(i, s);
                    found = true;
                    break;
                }
            }
            if (!found) servers.add(s);
        }
        persist();
    }

    public static void deleteServer(String id) {
        loadIfNeeded();
        synchronized (servers) {
            servers.removeIf(s -> s.id.equals(id));
        }
        persist();
        synchronized (mcpTools) {
            mcpTools.values().removeIf(t -> t instanceof McpTool && ((McpTool) t).server.id.equals(id));
        }
    }

    private static void persist() {
        try {
            File f = storeFile();
            f.getParentFile().mkdirs();
            JSONArray arr = new JSONArray();
            synchronized (servers) {
                for (McpServer s : servers) arr.put(s.toJson());
            }
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            FileLog.e("OxMcp", e);
        }
    }

    // ---------------- refresh (initialize + tools/list) ----------------

    /** Blocking network call - run off the UI thread. Returns a human-readable status. */
    public static String refreshServer(McpServer s) {
        try {
            JSONObject initParams = new JSONObject();
            initParams.put("protocolVersion", "2025-03-26");
            initParams.put("capabilities", new JSONObject());
            JSONObject clientInfo = new JSONObject();
            clientInfo.put("name", "OrionekoX");
            clientInfo.put("version", "0.2.0");
            initParams.put("clientInfo", clientInfo);
            JSONObject init = rpc(s, "initialize", initParams, 15_000, 20_000);
            JSONObject serverInfo = init.optJSONObject("serverInfo");
            String serverName = serverInfo == null ? "?" : serverInfo.optString("name", "?");
            try {
                rpcNotify(s, "notifications/initialized", 10_000);
            } catch (Exception ignored) {
                // some servers accept initialize without the notification; ignore failures
            }
            JSONObject toolsRes = rpc(s, "tools/list", new JSONObject(), 15_000, 30_000);
            JSONArray tools = toolsRes.optJSONArray("tools");
            int n = tools == null ? 0 : tools.length();

            List<OxTool> found = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                JSONObject t = tools.getJSONObject(i);
                String name = t.optString("name", "");
                if (name.isEmpty()) continue;
                found.add(new McpTool(s, name, t.optString("description", ""), t.optJSONObject("inputSchema")));
            }
            synchronized (mcpTools) {
                mcpTools.values().removeIf(t -> t instanceof McpTool && ((McpTool) t).server.id.equals(s.id));
                for (OxTool t : found) mcpTools.put(uniqueName(t.name()), t);
            }
            s.toolCount = found.size();
            s.lastError = null;
            return s.name + " (" + serverName + "): " + found.size() + " tools";
        } catch (Exception e) {
            s.lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            s.toolCount = 0;
            return "error: " + s.lastError;
        }
    }

    private static String uniqueName(String base) {
        if (!mcpTools.containsKey(base) && !OxBuiltinTools.ALL.containsKey(base)) return base;
        int i = 2;
        while (mcpTools.containsKey(base + "_" + i)) i++;
        return base + "_" + i;
    }

    private static void rebuildToolMap() {
        // reserved for future bulk invalidation
    }

    /** Once per process: background-refresh enabled servers so their tools exist
     *  without the user having to open the MCP page first. */
    private static volatile boolean autoRefreshStarted;

    public static void ensureRefreshedAsync() {
        loadIfNeeded();
        if (autoRefreshStarted) return;
        synchronized (OxMcpManager.class) {
            if (autoRefreshStarted) return;
            autoRefreshStarted = true;
            boolean any = false;
            synchronized (servers) {
                for (McpServer s : servers) {
                    if (s.enabled) any = true;
                }
            }
            if (!any) return;
            new Thread(() -> {
                for (McpServer s : getServers()) {
                    if (s.enabled) refreshServer(s);
                }
            }, "ox-mcp-refresh").start();
        }
    }

    public static boolean hasEnabledServers() {
        loadIfNeeded();
        synchronized (servers) {
            for (McpServer s : servers) {
                if (s.enabled) return true;
            }
        }
        return false;
    }

    /** Blocking refresh of every enabled server. Call from a worker thread only. */
    public static void refreshEnabledServersSync() {
        loadIfNeeded();
        for (McpServer s : getServers()) {
            if (s.enabled) refreshServer(s);
        }
    }

    // ---------------- tool call ----------------

    static String callTool(McpServer s, String tool, JSONObject args) throws Exception {
        JSONObject params = new JSONObject();
        params.put("name", tool);
        params.put("arguments", args);
        JSONObject res = rpc(s, "tools/call", params, 15_000, 120_000);
        if (res.has("isError") && res.optBoolean("isError")) {
            throw new IllegalStateException("server reports tool error: " + contentToText(res));
        }
        String text = contentToText(res);
        if (text.isEmpty() && res.has("structuredContent")) {
            return res.getJSONObject("structuredContent").toString();
        }
        return text.isEmpty() ? "(empty result)" : text;
    }

    private static String contentToText(JSONObject result) {
        StringBuilder sb = new StringBuilder();
        JSONArray content = result.optJSONArray("content");
        if (content != null) {
            for (int i = 0; i < content.length(); i++) {
                JSONObject c = content.optJSONObject(i);
                if (c == null) continue;
                if ("text".equals(c.optString("type"))) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(c.optString("text", ""));
                }
            }
        }
        return sb.toString();
    }

    // ---------------- JSON-RPC over Streamable HTTP ----------------

    private static JSONObject rpc(McpServer s, String method, JSONObject params, int connectTimeout, int readTimeout) throws Exception {
        int id = nextId();
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("id", id);
        req.put("method", method);
        req.put("params", params);
        JSONObject resp = post(s, req, connectTimeout, readTimeout);
        if (resp.has("error")) {
            JSONObject err = resp.getJSONObject("error");
            throw new IllegalStateException("MCP " + method + " error " + err.opt("code") + ": " + err.opt("message"));
        }
        return resp.optJSONObject("result") == null ? new JSONObject() : resp.getJSONObject("result");
    }

    private static void rpcNotify(McpServer s, String method, int timeout) throws Exception {
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("method", method);
        post(s, req, timeout, timeout);
    }

    private static final java.util.concurrent.atomic.AtomicInteger IDS = new java.util.concurrent.atomic.AtomicInteger(1);

    private static int nextId() {
        return IDS.getAndIncrement();
    }

    private static JSONObject post(McpServer s, JSONObject body, int connectTimeout, int readTimeout) throws Exception {
        synchronized (IO_LOCK) {  // keep per-server sessions coherent; volume is tiny
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(s.url).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json, text/event-stream");
                if (s.sessionId != null) {
                    conn.setRequestProperty("Mcp-Session-Id", s.sessionId);
                }
                if (s.headersJson != null && !s.headersJson.trim().isEmpty()) {
                    JSONObject hs = new JSONObject(s.headersJson);
                    for (java.util.Iterator<String> it = hs.keys(); it.hasNext(); ) {
                        String k = it.next();
                        conn.setRequestProperty(k, hs.optString(k, ""));
                    }
                }
                conn.setConnectTimeout(connectTimeout);
                conn.setReadTimeout(readTimeout);
                conn.setDoOutput(true);
                conn.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));
                int code = conn.getResponseCode();
                if (code >= 300) {
                    String err = readAll(conn.getErrorStream());
                    throw new IllegalStateException("HTTP " + code + (err.isEmpty() ? "" : " " + err.substring(0, Math.min(err.length(), 200))));
                }
                String sid = conn.getHeaderField("Mcp-Session-Id");
                if (sid != null && !sid.isEmpty()) {
                    s.sessionId = sid;
                }
                String ctype = conn.getHeaderField("Content-Type");
                String raw = readAll(conn.getInputStream());
                if (raw.isEmpty()) {
                    return new JSONObject();   // notification / 202 accepted
                }
                if (ctype != null && ctype.contains("text/event-stream")) {
                    // take the first JSON data: line that carries an id/result/error
                    for (String line : raw.split("\n")) {
                        if (!line.startsWith("data:")) continue;
                        String payload = line.substring(5).trim();
                        if (payload.isEmpty()) continue;
                        JSONObject o = new JSONObject(payload);
                        if (o.has("result") || o.has("error") || o.has("method")) return o;
                    }
                    throw new IllegalStateException("SSE response without a result frame");
                }
                return new JSONObject(raw);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
    }

    // ---------------- unified registry (builtin + MCP) ----------------

    /** All tools an agent may see/use right now: built-ins + enabled MCP tools; tg_* hidden without an account. */
    public static Collection<OxTool> tools() {
        loadIfNeeded();
        Map<String, OxTool> out = new LinkedHashMap<>();
        boolean hasAccount = UserConfig.getActivatedAccountsCount() > 0;
        for (OxTool t : OxBuiltinTools.ALL.values()) {
            if (!hasAccount && t.name().startsWith("tg_")) continue;
            out.put(t.name(), t);
        }
        synchronized (mcpTools) {
            for (OxTool t : mcpTools.values()) out.put(t.name(), t);
        }
        return out.values();
    }

    public static OxTool lookup(String name) {
        for (OxTool t : tools()) {
            if (t.name().equals(name)) return t;
        }
        return null;
    }

    public static int mcpToolCount() {
        synchronized (mcpTools) {
            return mcpTools.size();
        }
    }

    // ---------------- io ----------------

    private static String readFile(File f) throws Exception {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(f), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private static String readAll(java.io.InputStream in) {
        if (in == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
