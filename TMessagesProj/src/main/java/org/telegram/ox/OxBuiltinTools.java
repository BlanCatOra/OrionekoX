package org.telegram.ox;

import android.os.Build;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;

import java.io.BufferedReader;
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
                    conn.setRequestProperty("User-Agent", "OrionekoX/" + "0.0.5");
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
