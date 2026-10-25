package org.telegram.ox;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// Built-in tool set for the M2/M3 agent runtime. All execution stays inside the app process.
public class OxBuiltinTools {

    public static final Map<String, OxTool> ALL = new LinkedHashMap<>();

    private static void register(OxTool t) {
        ALL.put(t.name(), t);
    }

    static {
        register(new OxTool() {
            @Override public String name() { return "get_time"; }
            @Override public String description() { return "Get the current local date/time on this device."; }
            @Override public String defaultPolicy() { return POLICY_AUTO; }
            @Override public JSONObject parameters() {
                return schema(new String[0]);
            }
            @Override public String execute(JSONObject args) {
                long now = System.currentTimeMillis();
                return "unix_ms=" + now + "\n" + java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(now));
            }
        });

        register(new OxTool() {
            @Override public String name() { return "device_info"; }
            @Override public String description() { return "Get device model, Android version, Telegram perf class and app settings summary."; }
            @Override public String defaultPolicy() { return POLICY_AUTO; }
            @Override public JSONObject parameters() {
                return schema(new String[0]);
            }
            @Override public String execute(JSONObject args) {
                JSONObject o = new JSONObject();
                try {
                    o.put("model", Build.MANUFACTURER + " " + Build.MODEL);
                    o.put("android", Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
                    o.put("perf_class", org.telegram.messenger.SharedConfig.getDevicePerformanceClass());
                    o.put("locale", java.util.Locale.getDefault().toString());
                } catch (Exception ignored) {}
                return o.toString();
            }
        });

        register(new OxTool() {
            @Override public String name() { return "get_me"; }
            @Override public String description() { return "Get the signed-in Telegram account name and username (local device only)."; }
            @Override public String defaultPolicy() { return POLICY_AUTO; }
            @Override public JSONObject parameters() {
                return schema(new String[0]);
            }
            @Override public String execute(JSONObject args) {
                final String[] out = new String[1];
                final CountDownLatch latch = new CountDownLatch(1);
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        org.telegram.tgnet.TLRPC.User me = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
                        out[0] = me == null ? "not signed in" : (me.first_name + " " + (me.last_name == null ? "" : me.last_name)).trim() + " @" + (me.username == null ? "-" : me.username);
                    } catch (Throwable t) {
                        out[0] = "error: " + t;
                    } finally {
                        latch.countDown();
                    }
                });
                try {
                    latch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return out[0] == null ? "timeout" : out[0];
            }
        });

        register(new OxTool() {
            @Override public String name() { return "tg_unread_summary"; }
            @Override public String description() { return "Summarize unread messages: totals and the chats with the most unread. Read-only, requires user confirmation."; }
            @Override public String defaultPolicy() { return POLICY_ASK; }
            @Override public JSONObject parameters() {
                return schema(new String[]{"top"});
            }
            @Override public String execute(JSONObject args) {
                final int top = Math.max(1, Math.min(20, args.optInt("top", 5)));
                final String[] out = new String[1];
                final CountDownLatch latch = new CountDownLatch(1);
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        org.telegram.messenger.MessagesController mc =
                            org.telegram.messenger.MessagesController.getInstance(UserConfig.selectedAccount);
                        java.util.ArrayList<org.telegram.tgnet.TLRPC.Dialog> dialogs = mc.getAllDialogs();
                        long total = 0;
                        JSONArray items = new JSONArray();
                        java.util.List<long[]> rows = new java.util.ArrayList<>();
                        for (org.telegram.tgnet.TLRPC.Dialog d : dialogs) {
                            total += Math.max(0, d.unread_count);
                            if (d.unread_count > 0) rows.add(new long[]{d.id, d.unread_count});
                        }
                        rows.sort((x, y) -> Long.compare(y[1], x[1]));
                        int k = 0;
                        for (long[] r : rows) {
                            if (k >= top) break;
                            String title;
                            if (r[0] > 0) {
                                org.telegram.tgnet.TLRPC.User u = mc.getUser(r[0]);
                                title = u == null ? "#" + r[0] : org.telegram.messenger.UserObject.getUserName(u);
                            } else {
                                org.telegram.tgnet.TLRPC.Chat c = mc.getChat(-r[0]);
                                title = c == null || c.title == null ? "#" + r[0] : c.title;
                            }
                            items.put(new JSONObject().put("title", title).put("unread", r[1]));
                            k++;
                        }
                        JSONObject o = new JSONObject();
                        o.put("total_unread", total);
                        o.put("top", items);
                        out[0] = o.toString();
                    } catch (Throwable t) {
                        out[0] = "error: " + t;
                    } finally {
                        latch.countDown();
                    }
                });
                try {
                    latch.await(8, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return out[0] == null ? "timeout" : out[0];
            }
        });

        register(new OxTool() {
            @Override public String name() { return "tg_list_chats"; }
            @Override public String description() { return "List the top chats/dialogs of the signed-in Telegram account with type and unread count. Read-only, requires user confirmation."; }
            @Override public String defaultPolicy() { return POLICY_ASK; }
            @Override public JSONObject parameters() {
                JSONObject s = schema(new String[]{"limit"});
                try { s.put("description", "limit: optional, 1-30, default 15"); } catch (Exception ignored) {}
                return s;
            }
            @Override public String execute(JSONObject args) {
                final int limit = Math.max(1, Math.min(30, args.optInt("limit", 15)));
                final String[] out = new String[1];
                final CountDownLatch latch = new CountDownLatch(1);
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        org.telegram.messenger.MessagesController mc =
                            org.telegram.messenger.MessagesController.getInstance(UserConfig.selectedAccount);
                        java.util.ArrayList<org.telegram.tgnet.TLRPC.Dialog> dialogs = mc.getAllDialogs();
                        JSONArray arr = new JSONArray();
                        int count = 0;
                        for (org.telegram.tgnet.TLRPC.Dialog d : dialogs) {
                            if (count >= limit) break;
                            long id = d.id;
                            String title;
                            String type;
                            if (id > 0) {
                                org.telegram.tgnet.TLRPC.User u = mc.getUser(id);
                                title = u == null ? "?" : org.telegram.messenger.UserObject.getUserName(u);
                                type = "user";
                            } else {
                                org.telegram.tgnet.TLRPC.Chat c = mc.getChat(-id);
                                title = c == null ? "?" : (c.title == null ? "?" : c.title);
                                type = (c != null && c.megagroup) ? "supergroup" : (c != null && c.broadcast) ? "channel" : "chat";
                            }
                            if (title == null || title.isEmpty()) continue;
                            JSONObject o = new JSONObject();
                            o.put("title", title);
                            o.put("type", type);
                            o.put("unread", d.unread_count);
                            arr.put(o);
                            count++;
                        }
                        out[0] = arr.toString();
                    } catch (Throwable t) {
                        out[0] = "error: " + t;
                    } finally {
                        latch.countDown();
                    }
                });
                try {
                    latch.await(8, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return out[0] == null ? "timeout" : out[0];
            }
        });

        register(new OxTool() {
            @Override public String name() { return "tg_read_chat"; }
            @Override public String description() { return "Read the latest messages of a Telegram private chat, group or channel by (partial) title. If unsure of the exact title, call tg_list_chats first. Optional contains: substring filter; since_hours: only recent messages. Read-only, requires user confirmation."; }
            @Override public String defaultPolicy() { return POLICY_ASK; }
            @Override public JSONObject parameters() {
                JSONObject s = schema(new String[]{"chat"});
                try {
                    s.put("description", "chat: full or partial chat title; limit: optional, 1-50, default 20; contains: optional substring filter; since_hours: optional time filter");
                    JSONObject props = s.optJSONObject("properties");
                    if (props != null) {
                        props.put("contains", new JSONObject().put("type", "string").put("description", "substring filter"));
                        props.put("since_hours", new JSONObject().put("type", "integer").put("description", "only messages newer than N hours"));
                    }
                } catch (Exception ignored) {}
                return s;
            }
            @Override public String execute(JSONObject args) {
                final String chatQuery = args.optString("chat", "").trim();
                if (chatQuery.isEmpty()) return "error: missing required argument 'chat'";
                final int limit = Math.max(1, Math.min(50, args.optInt("limit", 20)));
                final String[] out = new String[1];
                final CountDownLatch latch = new CountDownLatch(1);
                final int account = UserConfig.selectedAccount;
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        org.telegram.messenger.MessagesController mc =
                            org.telegram.messenger.MessagesController.getInstance(account);
                        java.util.ArrayList<org.telegram.tgnet.TLRPC.Dialog> dialogs = mc.getAllDialogs();
                        if (dialogs.isEmpty()) {
                            out[0] = "error: no dialogs on this device (not signed in?)";
                            latch.countDown();
                            return;
                        }
                        org.telegram.tgnet.TLRPC.Dialog target = null;
                        java.util.List<org.telegram.tgnet.TLRPC.Dialog> candidates = new java.util.ArrayList<>();
                        String q = chatQuery.toLowerCase();
                        for (org.telegram.tgnet.TLRPC.Dialog d : dialogs) {
                            String title = oxDialogTitle(mc, d);
                            if (title == null) continue;
                            if (title.equalsIgnoreCase(chatQuery)) { target = d; break; }
                            if (title.toLowerCase().contains(q)) candidates.add(d);
                        }
                        if (target == null && candidates.size() == 1) target = candidates.get(0);
                        if (target == null) {
                            StringBuilder sb = new StringBuilder("error: chat '" + chatQuery + "' not found. Available chats:");
                            for (org.telegram.tgnet.TLRPC.Dialog d : dialogs) {
                                String t = oxDialogTitle(mc, d);
                                if (t != null) sb.append("\n- ").append(t);
                            }
                            out[0] = sb.toString();
                            latch.countDown();
                            return;
                        }
                        final String title = oxDialogTitle(mc, target);
                        org.telegram.tgnet.TLRPC.InputPeer peer = mc.getInputPeer(target.id);
                        if (peer == null) {
                            out[0] = "error: cannot resolve input peer for '" + title + "'";
                            latch.countDown();
                            return;
                        }
                        org.telegram.tgnet.TLRPC.TL_messages_getHistory req = new org.telegram.tgnet.TLRPC.TL_messages_getHistory();
                        req.peer = peer;
                        req.offset_id = 0;
                        req.offset_date = 0;
                        req.add_offset = 0;
                        req.limit = limit;
                        req.max_id = 0;
                        req.min_id = 0;
                        req.hash = 0;
                        org.telegram.tgnet.ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
                            try {
                                if (error != null) {
                                    out[0] = "error: " + error.code + " " + error.text;
                                    return;
                                }
                                if (!(response instanceof org.telegram.tgnet.TLRPC.TL_messages_messages)) {
                                    out[0] = "error: unexpected response type";
                                    return;
                                }
                                org.telegram.tgnet.TLRPC.TL_messages_messages res =
                                    (org.telegram.tgnet.TLRPC.TL_messages_messages) response;
                                String contains = args.optString("contains", "").trim().toLowerCase();
                                int sinceHours = args.optInt("since_hours", 0);
                                long minDate = sinceHours > 0
                                        ? (System.currentTimeMillis() / 1000L) - sinceHours * 3600L : 0L;
                                java.util.HashMap<Long, String> users = new java.util.HashMap<>();
                                java.util.HashMap<Long, String> chats = new java.util.HashMap<>();
                                for (org.telegram.tgnet.TLRPC.User u : res.users) {
                                    users.put(u.id, org.telegram.messenger.UserObject.getUserName(u));
                                }
                                for (org.telegram.tgnet.TLRPC.Chat c : res.chats) {
                                    chats.put(c.id, c.title == null ? "" : c.title);
                                }
                                StringBuilder sb = new StringBuilder("chat: " + title + ", messages " + res.messages.size() + " (oldest first):\n");
                                java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US);
                                for (int i = res.messages.size() - 1; i >= 0; i--) {
                                    org.telegram.tgnet.TLRPC.Message m = res.messages.get(i);
                                    if (minDate > 0 && m.date * 1000L < minDate) continue;
                                    String sender = title;
                                    if (m.from_id != null) {
                                        if (m.from_id.user_id != 0) {
                                            String n = users.get(m.from_id.user_id);
                                            if (n != null && !n.isEmpty()) sender = n;
                                        } else if (m.from_id.channel_id != 0) {
                                            String n = chats.get(m.from_id.channel_id);
                                            if (n != null && !n.isEmpty()) sender = n;
                                        } else if (m.from_id.chat_id != 0) {
                                            String n = chats.get(m.from_id.chat_id);
                                            if (n != null && !n.isEmpty()) sender = n;
                                        }
                                    }
                                    String text = m.message == null ? "" : m.message.trim();
                                    if (text.isEmpty()) {
                                        text = m.action != null ? "(service message)" : (m.media != null ? "(media/attachment)" : "");
                                    }
                                    if (text.isEmpty()) continue;
                                    if (!contains.isEmpty() && !text.toLowerCase().contains(contains)) continue;
                                    if (text.length() > 500) text = text.substring(0, 500) + "…";
                                    sb.append('[').append(df.format(new java.util.Date(m.date * 1000L))).append("] ")
                                      .append(sender).append(": ").append(text);
                                    if (m.views > 0) sb.append("  👁").append(m.views);
                                    sb.append('\n');
                                }
                                out[0] = sb.toString();
                            } catch (Throwable t) {
                                out[0] = "error: " + t;
                            } finally {
                                latch.countDown();
                            }
                        });
                    } catch (Throwable t) {
                        out[0] = "error: " + t;
                        latch.countDown();
                    }
                });
                try {
                    latch.await(25, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return out[0] == null ? "timeout" : out[0];
            }
        });

        register(new OxTool() {
            @Override public String name() { return "shell_in_container"; }
            @Override public String description() { return "Run a shell command inside the built-in Ubuntu container (PRoot userland, sandboxed to the app). First call installs the rootfs (~30MB download). Use for file operations, text processing, package listing inside the container."; }
            @Override public String defaultPolicy() { return POLICY_ASK; }
            @Override public JSONObject parameters() {
                JSONObject s = schema(new String[]{"command"});
                try {
                    JSONObject props = s.optJSONObject("properties");
                    if (props != null) {
                        props.put("command", new JSONObject().put("type", "string").put("description", "sh command to run inside the container"));
                        props.put("timeout_sec", new JSONObject().put("type", "integer").put("description", "optional timeout, default 30"));
                    }
                } catch (Exception ignored) {}
                return s;
            }
            @Override public String execute(JSONObject args) {
                String cmd = args.optString("command", "").trim();
                if (cmd.isEmpty()) return "error: missing 'command'";
                long timeout = Math.max(5, Math.min(300, args.optInt("timeout_sec", 30))) * 1000L;
                return org.telegram.ox.OxContainerManager.run(cmd, timeout);
            }
        });

        register(new OxTool() {
            @Override public String name() { return "container_status"; }
            @Override public String description() { return "Get the Ubuntu container install state (not installed / installing / ready) and basic info. Auto policy."; }
            @Override public String defaultPolicy() { return POLICY_AUTO; }
            @Override public JSONObject parameters() {
                return schema(new String[0]);
            }
            @Override public String execute(JSONObject args) {
                org.json.JSONObject o = new org.json.JSONObject();
                try {
                    int st = org.telegram.ox.OxContainerManager.getState();
                    o.put("state", st == org.telegram.ox.OxContainerManager.STATE_READY ? "ready"
                            : st == org.telegram.ox.OxContainerManager.STATE_INSTALLING ? "installing"
                            : st == org.telegram.ox.OxContainerManager.STATE_ERROR ? "error: " + org.telegram.ox.OxContainerManager.getErrorText()
                            : "not installed");
                    o.put("prootBinary", org.telegram.ox.OxContainerManager.isProotAvailable());
                    o.put("rootfs", org.telegram.ox.OxContainerManager.isRootfsReady());
                    o.put("rootfsDiag", org.telegram.ox.OxContainerManager.rootfsDiagnostics());
                } catch (Exception ignored) {}
                return o.toString();
            }
        });

        register(new OxTool() {
            @Override public String name() { return "http_get"; }
            @Override public String description() { return "HTTP GET a URL and return the first 2000 bytes of the response body. Use for fetching public information."; }
            @Override public String defaultPolicy() { return POLICY_ASK; }
            @Override public JSONObject parameters() {
                return schema(new String[]{"url"});
            }
            @Override public String execute(JSONObject args) throws Exception {
                String url = args.getString("url");
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    throw new IllegalArgumentException("url must be http(s)");
                }
                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setConnectTimeout(10_000);
                    conn.setReadTimeout(20_000);
                    conn.setRequestProperty("User-Agent", "OrionekoX/0.1.0");
                    int code = conn.getResponseCode();
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while (sb.length() < 2000 && (line = r.readLine()) != null) {
                            sb.append(line).append('\n');
                        }
                    }
                    return "HTTP " + code + "\n" + sb;
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        });
    }


    static {
        register(new OxTool() {
            @Override public String name() { return "remember"; }
            @Override public String description() { return "Save a durable fact about the user into this agent's long-term memory."; }
            @Override public String defaultPolicy() { return POLICY_AUTO; }
            @Override public JSONObject parameters() { return schema(new String[]{"text"}); }
            @Override public String execute(JSONObject args) { return "use executeWithAgent"; }
            @Override public String executeWithAgent(JSONObject args, OxAgent agent) throws Exception {
                String text = args.optString("text", "").trim();
                if (text.isEmpty()) throw new IllegalArgumentException("text is empty");
                OxAgentStore.appendMemory(agent.id, text);
                return "remembered: " + text;
            }
        });

        register(new OxTool() {
            @Override public String name() { return "recall"; }
            @Override public String description() { return "Search this agent's long-term memory for a keyword."; }
            @Override public String defaultPolicy() { return POLICY_AUTO; }
            @Override public JSONObject parameters() { return schema(new String[]{"query"}); }
            @Override public String execute(JSONObject args) { return "use executeWithAgent"; }
            @Override public String executeWithAgent(JSONObject args, OxAgent agent) {
                String q = args.optString("query", "").trim();
                String r = OxAgentStore.searchMemory(agent.id, q, 30);
                if (!r.isEmpty()) return r;
                // Imported agents may carry their facts only in the migrated chat history.
                StringBuilder sb = new StringBuilder();
                int n = 0;
                for (OxChatEntry e : OxAgentStore.loadChat(agent.id)) {
                    if (e.content != null && (q.isEmpty() || e.content.toLowerCase().contains(q.toLowerCase()))) {
                        sb.append(e.role).append(": ").append(e.content).append('\n');
                        if (++n >= 10) break;
                    }
                }
                return sb.length() == 0 ? "no matching memory" : sb.toString().trim();
            }
        });

        register(new OxTool() {
            @Override public String name() { return "list_dir"; }
            @Override public String description() { return "List files in this agent's private workspace."; }
            @Override public String defaultPolicy() { return POLICY_AUTO; }
            @Override public JSONObject parameters() { return schema(new String[]{"path"}); }
            @Override public String execute(JSONObject args) { return "use executeWithAgent"; }
            @Override public String executeWithAgent(JSONObject args, OxAgent agent) throws Exception {
                File f = OxAgentStore.resolveWorkspace(agent.id, args.optString("path", "."));
                if (!f.exists()) throw new java.io.FileNotFoundException("not found");
                if (!f.isDirectory()) throw new IllegalArgumentException("not a directory");
                File[] files = f.listFiles();
                if (files == null) return "";
                java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                StringBuilder sb = new StringBuilder();
                int n = 0;
                for (File c : files) {
                    if (n++ >= 200) { sb.append("...\n"); break; }
                    sb.append(c.isDirectory() ? "d " : "f ").append(c.getName());
                    if (c.isFile()) sb.append("  ").append(c.length()).append("B");
                    sb.append('\n');
                }
                return sb.toString().trim();
            }
        });

        register(new OxTool() {
            @Override public String name() { return "read_file"; }
            @Override public String description() { return "Read a UTF-8 text file from this agent's private workspace (max 64 KB)."; }
            @Override public String defaultPolicy() { return POLICY_ASK; }
            @Override public JSONObject parameters() { return schema(new String[]{"path"}); }
            @Override public String execute(JSONObject args) { return "use executeWithAgent"; }
            @Override public String executeWithAgent(JSONObject args, OxAgent agent) throws Exception {
                File f = OxAgentStore.resolveWorkspace(agent.id, args.optString("path", ""));
                if (!f.exists() || !f.isFile()) throw new java.io.FileNotFoundException("not found");
                try (FileInputStream in = new FileInputStream(f)) {
                    byte[] buf = new byte[(int) Math.min(f.length(), 65536)];
                    int off = 0, n;
                    while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) off += n;
                    return new String(buf, 0, off, StandardCharsets.UTF_8);
                }
            }
        });

        register(new OxTool() {
            @Override public String name() { return "write_file"; }
            @Override public String description() { return "Write a UTF-8 text file inside this agent's private workspace."; }
            @Override public String defaultPolicy() { return POLICY_ASK; }
            @Override public JSONObject parameters() { return schema(new String[]{"path", "content"}); }
            @Override public String execute(JSONObject args) { return "use executeWithAgent"; }
            @Override public String executeWithAgent(JSONObject args, OxAgent agent) throws Exception {
                File f = OxAgentStore.resolveWorkspace(agent.id, args.optString("path", ""));
                File parent = f.getParentFile();
                if (parent != null) parent.mkdirs();
                byte[] data = args.optString("content", "").getBytes(StandardCharsets.UTF_8);
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(data);
                }
                return "wrote " + data.length + " bytes to " + args.optString("path", "");
            }
        });

        register(new OxTool() {
            @Override public String name() { return "notify"; }
            @Override public String description() { return "Show a short toast notification on the device."; }
            @Override public String defaultPolicy() { return POLICY_ASK; }
            @Override public JSONObject parameters() { return schema(new String[]{"text"}); }
            @Override public String execute(JSONObject args) {
                final String text = args.optString("text", "");
                AndroidUtilities.runOnUIThread(() -> Toast.makeText(ApplicationLoader.applicationContext, text, Toast.LENGTH_LONG).show());
                return "notified";
            }
        });

        register(new OxTool() {
            @Override public String name() { return "open_url"; }
            @Override public String description() { return "Open an http(s) URL in the system browser."; }
            @Override public String defaultPolicy() { return POLICY_ASK; }
            @Override public JSONObject parameters() { return schema(new String[]{"url"}); }
            @Override public String execute(JSONObject args) throws Exception {
                String url = args.optString("url", "").trim();
                if (!url.startsWith("http://") && !url.startsWith("https://")) throw new IllegalArgumentException("only http(s)");
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ApplicationLoader.applicationContext.startActivity(i);
                return "opened " + url;
            }
        });
    }

    private static String oxDialogTitle(org.telegram.messenger.MessagesController mc, org.telegram.tgnet.TLRPC.Dialog d) {
        if (d.id > 0) {
            org.telegram.tgnet.TLRPC.User u = mc.getUser(d.id);
            return u == null ? null : org.telegram.messenger.UserObject.getUserName(u);
        }
        org.telegram.tgnet.TLRPC.Chat c = mc.getChat(-d.id);
        return c == null || c.title == null ? null : c.title;
    }

    private static JSONObject schema(String[] requiredProps) {
        JSONObject s = new JSONObject();
        try {
            s.put("type", "object");
            JSONObject props = new JSONObject();
            for (String p : requiredProps) {
                props.put(p, new JSONObject().put("type", "string"));
            }
            s.put("properties", props);
            JSONArray req = new JSONArray();
            for (String p : requiredProps) req.put(p);
            if (requiredProps.length > 0) s.put("required", req);
        } catch (Exception ignored) {}
        return s;
    }
}
