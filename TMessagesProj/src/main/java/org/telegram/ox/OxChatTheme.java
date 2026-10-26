package org.telegram.ox;

import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Custom appearance for local-agent conversations. Native mode leaves Telegram's
 * theme/bubbles untouched; custom mode overrides background, bubble colors, bubble
 * opacity/radius and message font scale only while an agent chat is open.
 */
public class OxChatTheme {

    public static final String MODE_NATIVE = "native";
    public static final String MODE_CUSTOM = "custom";

    private static final String PREFS = "ox_chat_theme";
    private static final String DIR_PRESETS = "ox_chat_presets";
    private static final String BG_FILE = "ox_chat_bg";

    private static boolean applied;
    private static float originalTextSize;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }

    public static boolean isCustom() {
        return MODE_CUSTOM.equals(prefs().getString("mode", MODE_NATIVE));
    }

    public static void setMode(String mode) {
        prefs().edit().putString("mode", MODE_NATIVE.equals(mode) ? MODE_NATIVE : MODE_CUSTOM).apply();
    }

    public static int background() {
        return prefs().getInt("background", 0xFFF3F1F7);
    }

    public static int inBubble() {
        return prefs().getInt("in_bubble", 0xFFFFFFFF);
    }

    public static int outBubble() {
        return prefs().getInt("out_bubble", 0xFFE3F2FD);
    }

    public static int bubbleAlpha() {
        return prefs().getInt("bubble_alpha", 255);
    }

    public static int cornerRadius() {
        return prefs().getInt("corner_radius", 16);
    }

    public static float fontScale() {
        return prefs().getFloat("font_scale", 1f);
    }

    public static String backgroundImage() {
        return prefs().getString("background_image", "");
    }

    public static void setInt(String key, int value) {
        prefs().edit().putInt(key, value).apply();
    }

    public static void setFloat(String key, float value) {
        prefs().edit().putFloat(key, value).apply();
    }

    public static void setString(String key, String value) {
        prefs().edit().putString(key, value).apply();
    }

    public static void enableCustom() {
        if (!isCustom()) {
            prefs().edit().putString("mode", MODE_CUSTOM)
                    .putInt("background", 0xFFF3F1F7)
                    .putInt("in_bubble", 0xFFFFFFFF)
                    .putInt("out_bubble", 0xFFE3F2FD)
                    .putInt("bubble_alpha", 255)
                    .putInt("corner_radius", 16)
                    .putFloat("font_scale", 1f)
                    .apply();
        }
    }

    public static void resetNative() {
        prefs().edit().clear().apply();
        File bg = new File(ApplicationLoader.applicationContext.getFilesDir(), BG_FILE);
        if (bg.exists()) bg.delete();
    }

    public static String hex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    public static int darken(int color) {
        int a = color >>> 24;
        int r = Math.round(((color >> 16) & 0xFF) * 0.82f);
        int g = Math.round(((color >> 8) & 0xFF) * 0.82f);
        int b = Math.round((color & 0xFF) * 0.82f);
        return (a << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    public static String importJson(String raw) throws Exception {
        JSONObject o = new JSONObject(raw);
        int bg = color(o, "backgroundArgb", "backgroundColorArgb", "background", "background_color");
        int in = color(o, "inBubbleArgb", "incomingBubbleArgb", "inBubbleColorArgb", "bubbleInArgb",
                "secondaryColorArgb", "secondary_color_argb");
        int out = color(o, "outBubbleArgb", "outgoingBubbleArgb", "outBubbleColorArgb", "bubbleOutArgb",
                "primaryColorArgb", "primary_color_argb");
        int alpha = intValue(o, 255, "bubbleAlpha", "bubble_alpha", "opacity");
        if (alpha <= 1) alpha = Math.round(alpha * 255);
        int radius = intValue(o, 16, "cornerRadius", "bubbleCornerRadius", "radius");
        float scale = floatValue(o, 1f, "fontScale", "font_scale", "fontSizeScale");
        String name = o.optString("name", "");
        if (bg == 0 && in == 0 && out == 0) throw new IllegalArgumentException("no colors");
        prefs().edit().putString("mode", MODE_CUSTOM)
                .putInt("background", bg == 0 ? 0xFFF3F1F7 : bg)
                .putInt("in_bubble", in == 0 ? 0xFFFFFFFF : in)
                .putInt("out_bubble", out == 0 ? 0xFFE3F2FD : out)
                .putInt("bubble_alpha", Math.max(40, Math.min(255, alpha)))
                .putInt("corner_radius", Math.max(0, Math.min(28, radius)))
                .putFloat("font_scale", Math.max(0.8f, Math.min(1.4f, scale)))
                .apply();
        return name;
    }

    public static void setBackgroundImage(InputStream in) throws Exception {
        File f = new File(ApplicationLoader.applicationContext.getFilesDir(), BG_FILE);
        FileOutputStream out = new FileOutputStream(f);
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.close();
        in.close();
        prefs().edit().putString("background_image", f.getAbsolutePath()).apply();
    }

    public static void clearBackgroundImage() {
        prefs().edit().putString("background_image", "").apply();
    }

    // ---------------- presets ----------------

    private static File presetDir() {
        File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), DIR_PRESETS);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static List<String> presets() {
        File[] files = presetDir().listFiles();
        ArrayList<String> out = new ArrayList<>();
        if (files != null) {
            Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File f : files) {
                if (f.getName().endsWith(".json")) out.add(f.getName().substring(0, f.getName().length() - 5));
            }
        }
        return out;
    }

    public static void savePreset(String name) throws Exception {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("name");
        String safe = name.trim().replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
        JSONObject o = toJson(name.trim());
        FileOutputStream out = new FileOutputStream(new File(presetDir(), safe + ".json"));
        out.write(o.toString().getBytes("UTF-8"));
        out.close();
    }

    public static void applyPreset(String name) throws Exception {
        File f = new File(presetDir(), name + ".json");
        byte[] data = new byte[(int) f.length()];
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        int off = 0;
        while (off < data.length) {
            int n = in.read(data, off, data.length - off);
            if (n <= 0) break;
            off += n;
        }
        in.close();
        importJson(new String(data, "UTF-8"));
    }

    public static void deletePreset(String name) {
        new File(presetDir(), name + ".json").delete();
    }

    public static JSONObject toJson(String name) throws Exception {
        JSONObject o = new JSONObject();
        o.put("name", name == null ? "" : name);
        o.put("backgroundArgb", background());
        o.put("inBubbleArgb", inBubble());
        o.put("outBubbleArgb", outBubble());
        o.put("bubbleAlpha", bubbleAlpha());
        o.put("cornerRadius", cornerRadius());
        o.put("fontScale", fontScale());
        return o;
    }

    // ---------------- runtime application ----------------

    public static void applyToChat(ChatActivity activity, long dialogId, View listView) {
        if (activity == null || listView == null) return;
        if (!isCustom() || !OxAgentsBridge.isAgentDialog(dialogId)) return;
        try {
            int alpha = bubbleAlpha();
            int bg = background();
            bg = (bg & 0x00FFFFFF) | (Math.min(255, Math.max(0, alpha)) << 24);
            String bgPath = backgroundImage();
            File bgFile = bgPath == null || bgPath.isEmpty() ? null : new File(bgPath);
            if (bgFile != null && bgFile.isFile()) {
                android.graphics.Bitmap bmp = BitmapFactory.decodeFile(bgFile.getAbsolutePath());
                if (bmp != null) {
                    BitmapDrawable d = new BitmapDrawable(activity.getParentActivity().getResources(), bmp);
                    d.setAlpha(Math.min(255, Math.max(0, alpha)));
                    listView.setBackground(d);
                } else {
                    listView.setBackgroundColor(bg);
                }
            } else {
                listView.setBackgroundColor(bg);
            }
            int in = withAlpha(inBubble(), alpha);
            int out = withAlpha(outBubble(), alpha);
            if (Theme.chat_msgInDrawable != null) {
                Theme.chat_msgInDrawable.overrideColor = in;
                Theme.chat_msgInDrawable.setOverrideRoundRadius(AndroidUtilities.dp(cornerRadius()));
            }
            if (Theme.chat_msgOutDrawable != null) {
                Theme.chat_msgOutDrawable.overrideColor = out;
                Theme.chat_msgOutDrawable.setOverrideRoundRadius(AndroidUtilities.dp(cornerRadius()));
            }
            if (Theme.chat_msgInSelectedDrawable != null) {
                Theme.chat_msgInSelectedDrawable.overrideColor = in;
                Theme.chat_msgInSelectedDrawable.setOverrideRoundRadius(AndroidUtilities.dp(cornerRadius()));
            }
            if (Theme.chat_msgOutSelectedDrawable != null) {
                Theme.chat_msgOutSelectedDrawable.overrideColor = out;
                Theme.chat_msgOutSelectedDrawable.setOverrideRoundRadius(AndroidUtilities.dp(cornerRadius()));
            }
            if (Theme.chat_msgTextPaint != null) {
                if (!applied) originalTextSize = Theme.chat_msgTextPaint.getTextSize();
                Theme.chat_msgTextPaint.setTextSize(AndroidUtilities.dp(SharedConfig.fontSize * fontScale()));
            }
            if (listView instanceof androidx.recyclerview.widget.RecyclerView) {
                androidx.recyclerview.widget.RecyclerView.Adapter a = ((androidx.recyclerview.widget.RecyclerView) listView).getAdapter();
                if (a != null) a.notifyDataSetChanged();
            }
            listView.invalidate();
            applied = true;
        } catch (Throwable ignored) {
        }
    }

    public static void restoreChat(View listView) {
        if (!applied) return;
        try {
            if (Theme.chat_msgInDrawable != null) {
                Theme.chat_msgInDrawable.overrideColor = null;
                Theme.chat_msgInDrawable.setOverrideRoundRadius(0);
            }
            if (Theme.chat_msgOutDrawable != null) {
                Theme.chat_msgOutDrawable.overrideColor = null;
                Theme.chat_msgOutDrawable.setOverrideRoundRadius(0);
            }
            if (Theme.chat_msgInSelectedDrawable != null) {
                Theme.chat_msgInSelectedDrawable.overrideColor = null;
                Theme.chat_msgInSelectedDrawable.setOverrideRoundRadius(0);
            }
            if (Theme.chat_msgOutSelectedDrawable != null) {
                Theme.chat_msgOutSelectedDrawable.overrideColor = null;
                Theme.chat_msgOutSelectedDrawable.setOverrideRoundRadius(0);
            }
            if (Theme.chat_msgTextPaint != null && originalTextSize > 0) {
                Theme.chat_msgTextPaint.setTextSize(originalTextSize);
            }
            if (listView != null) {
                listView.setBackground(null);
                if (listView instanceof androidx.recyclerview.widget.RecyclerView) {
                    androidx.recyclerview.widget.RecyclerView.Adapter a = ((androidx.recyclerview.widget.RecyclerView) listView).getAdapter();
                    if (a != null) a.notifyDataSetChanged();
                }
                listView.invalidate();
            }
        } catch (Throwable ignored) {
        }
        applied = false;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.min(255, Math.max(0, alpha)) << 24);
    }

    private static int color(JSONObject o, String... keys) {
        for (String key : keys) {
            if (!o.has(key)) continue;
            Object v = o.opt(key);
            try {
                if (v instanceof Number) return opaque(((Number) v).longValue());
                if (v instanceof String) {
                    String s = ((String) v).trim();
                    if (s.startsWith("#")) s = s.substring(1);
                    if (s.length() == 6) s = "FF" + s;
                    return opaque(Long.parseLong(s, 16));
                }
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    private static int intValue(JSONObject o, int def, String... keys) {
        for (String key : keys) {
            if (!o.has(key)) continue;
            Object v = o.opt(key);
            if (v instanceof Number) return ((Number) v).intValue();
            try {
                return Integer.parseInt(String.valueOf(v).replace("%", "").trim());
            } catch (Throwable ignored) {
            }
        }
        return def;
    }

    private static float floatValue(JSONObject o, float def, String... keys) {
        for (String key : keys) {
            if (!o.has(key)) continue;
            Object v = o.opt(key);
            if (v instanceof Number) return ((Number) v).floatValue();
            try {
                return Float.parseFloat(String.valueOf(v).trim());
            } catch (Throwable ignored) {
            }
        }
        return def;
    }

    private static int opaque(long value) {
        int c = (int) value;
        if ((c & 0xFF000000) == 0) c |= 0xFF000000;
        return c;
    }
}
