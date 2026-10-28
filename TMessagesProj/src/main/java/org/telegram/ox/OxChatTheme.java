package org.telegram.ox;

import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;

import org.json.JSONArray;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Custom appearance for local-agent conversations.
 *
 * <p>The same model powers the global appearance page and per-agent overrides.
 * A snapshot is either the global SharedPreferences record or an agent JSON file
 * under {@code files/ox_chat_agents/}; the runtime only reads it while an agent
 * chat is open and restores Telegram's paints on pause.</p>
 */
public class OxChatTheme {

    public static final String MODE_NATIVE = "native";
    public static final String MODE_CUSTOM = "custom";
    public static final String BG_SOLID = "solid";
    public static final String BG_GRADIENT = "gradient";
    public static final String BG_IMAGE = "image";
    public static final String STYLE_SOLID = "solid";
    public static final String STYLE_FROSTED = "frosted";

    private static final String PREFS = "ox_chat_theme";
    private static final String DIR_PRESETS = "ox_chat_presets";
    private static final String DIR_AGENTS = "ox_chat_agents";
    private static final String DIR_DIALOGS = "ox_chat_dialogs";
    private static final String DIR_FONTS = "oxfonts";
    private static final String BG_FILE = "ox_chat_bg";
    private static String blurCacheKey = "";
    private static android.graphics.Bitmap blurCacheBitmap;

    private static boolean applied;
    private static View appliedRoot;
    private static View appliedDecor;
    private static Drawable appliedDecorBg;
    private static View appliedWallpaper;
    private static Drawable appliedWallpaperBg;
    private static android.view.Window appliedWindow;
    private static int appliedStatusColor, appliedNavColor, appliedUiFlags;
    private static long appliedDialogId;
    private static float appliedLineHeight = 1f;
    private static org.telegram.ui.ChatActivity.ThemeDelegate activityThemeDelegate;
    private static View appliedScrim;
    private static android.view.ViewGroup appliedScrimParent;
    private static org.telegram.ui.Components.SizeNotifierFrameLayout activityContentView;
    private static float originalTextSize;
    private static int originalTextColor;
    private static int originalCodeColor;
    private static int originalCode2Color;
    private static int originalCode3Color;
    private static Typeface originalTypeface;
    private static Typeface originalCodeTypeface;
    private static Typeface originalCode2Typeface;
    private static Typeface originalCode3Typeface;

    /** Non-null while the appearance page is editing one agent's override. */
    private static String activeAgentKey;
    private static Snapshot activeAgent;
    /** Non-null while the appearance page is editing one conversation's override. */
    private static String activeDialogKey;
    private static Snapshot activeDialog;
    private static long activeDialogId;

    public static class Snapshot {
        public String mode = MODE_NATIVE;
        public String backgroundMode = BG_SOLID;
        public int background = 0xFFF3F1F7;
        public int background2 = 0xFFE8E4F0;
        public int backgroundAlpha = 255;
        public int backgroundDim = 0;
        public int backgroundBlur = 0;
        public boolean amoled = false;
        public int gradientIndex = 0;
        public String backgroundImage = "";
        public int inBubble = 0xFFFFFFFF;
        public int outBubble = 0xFFE3F2FD;
        public int bubbleAlpha = 255;
        public int cornerRadius = 16;
        public String bubbleStyle = STYLE_SOLID;
        public int borderColor = 0;
        public int borderWidth = 0;
        public int textColor = 0;
        public float fontScale = 1f;
        public float lineHeight = 1f;
        public String fontPath = "";
        public String codeFontPath = "";

        public Snapshot copy() {
            Snapshot s = new Snapshot();
            s.mode = mode;
            s.backgroundMode = backgroundMode;
            s.background = background;
            s.background2 = background2;
            s.backgroundAlpha = backgroundAlpha;
            s.backgroundDim = backgroundDim;
            s.backgroundBlur = backgroundBlur;
            s.amoled = amoled;
            s.gradientIndex = gradientIndex;
            s.backgroundImage = backgroundImage;
            s.inBubble = inBubble;
            s.outBubble = outBubble;
            s.bubbleAlpha = bubbleAlpha;
            s.cornerRadius = cornerRadius;
            s.bubbleStyle = bubbleStyle;
            s.borderColor = borderColor;
            s.borderWidth = borderWidth;
            s.textColor = textColor;
            s.fontScale = fontScale;
            s.lineHeight = lineHeight;
            s.fontPath = fontPath;
            s.codeFontPath = codeFontPath;
            return s;
        }

        public JSONObject toJson(String name) throws Exception {
            JSONObject o = new JSONObject();
            o.put("name", name == null ? "" : name);
            o.put("mode", mode);
            o.put("backgroundMode", backgroundMode);
            o.put("backgroundArgb", background);
            o.put("background2Argb", background2);
            o.put("backgroundAlpha", backgroundAlpha);
            o.put("backgroundDim", backgroundDim);
            o.put("backgroundBlur", backgroundBlur);
            o.put("amoled", amoled);
            o.put("gradientIndex", gradientIndex);
            o.put("backgroundImage", backgroundImage == null ? "" : backgroundImage);
            o.put("inBubbleArgb", inBubble);
            o.put("outBubbleArgb", outBubble);
            o.put("bubbleAlpha", bubbleAlpha);
            o.put("cornerRadius", cornerRadius);
            o.put("bubbleStyle", bubbleStyle);
            o.put("borderArgb", borderColor);
            o.put("borderWidth", borderWidth);
            o.put("textArgb", textColor);
            o.put("fontScale", fontScale);
            o.put("lineHeight", lineHeight);
            o.put("fontPath", fontPath == null ? "" : fontPath);
            o.put("codeFontPath", codeFontPath == null ? "" : codeFontPath);
            return o;
        }

        public String summary() {
            return "背景 " + (BG_IMAGE.equals(backgroundMode) && backgroundImage != null && !backgroundImage.isEmpty()
                    ? new File(backgroundImage).getName()
                    : hex(background) + (BG_GRADIENT.equals(backgroundMode) ? " → " + hex(background2) : ""))
                    + " · 收 " + hex(inBubble) + " · 发 " + hex(outBubble)
                    + " · " + Math.round(bubbleAlpha * 100f / 255f) + "%"
                    + (backgroundBlur > 0 ? " · 模糊" + blurLabel(backgroundBlur) : "")
                    + (amoled ? " · AMOLED" : "")
                    + " · " + cornerRadius + "dp"
                    + " · " + String.format("%.1fx", fontScale)
                    + (Math.abs(lineHeight - 1f) > 0.001f ? " · 行高 " + String.format("%.1fx", lineHeight) : "")
                    + (STYLE_FROSTED.equals(bubbleStyle) ? " · 磨砂" : "")
                    + (borderWidth > 0 ? " · 边框 " + borderWidth + "dp" : "");
        }
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }

    public static String hex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    public static String blurLabel(int level) {
        if (level >= 3) return "强";
        if (level == 2) return "中";
        if (level == 1) return "轻";
        return "关";
    }

    public static int darken(int color) {
        int a = color >>> 24;
        int r = Math.round(((color >> 16) & 0xFF) * 0.82f);
        int g = Math.round(((color >> 8) & 0xFF) * 0.82f);
        int b = Math.round((color & 0xFF) * 0.82f);
        return (a << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    // ---------------- agent scope ----------------

    private static String agentKey(long tgId) {
        return "agent_" + tgId;
    }

    private static File agentDir() {
        File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), DIR_AGENTS);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File agentFile(String key) {
        return new File(agentDir(), key + ".json");
    }

    private static String dialogKey(long dialogId) {
        return "dialog_" + dialogId;
    }

    private static File dialogDir() {
        File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), DIR_DIALOGS);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File dialogFile(String key) {
        return new File(dialogDir(), key + ".json");
    }

    private static Snapshot readFile(File f) {
        try {
            if (f == null || !f.isFile()) return null;
            byte[] data = new byte[(int) f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n <= 0) break;
                off += n;
            }
            in.close();
            return parseJsonObject(new JSONObject(new String(data, "UTF-8")), new Snapshot());
        } catch (Throwable t) {
            return null;
        }
    }

    /** Lookup used when a dialog override is created or reset. */
    private static Snapshot fallbackForDialog(long dialogId) {
        if (OxAgentsBridge.agentKeyForDialog(dialogId) != null) {
            Snapshot s = readFile(agentFile(agentKey(dialogId)));
            if (s != null) return s;
        }
        return readPrefs();
    }

    /** Called by the agent appearance page so every getter/setter edits this file. */
    public static void openAgent(long tgId) {
        closeDialog();
        activeAgentKey = agentKey(tgId);
        activeAgent = readFile(agentFile(activeAgentKey));
        if (activeAgent == null) {
            // Start from the global look, so "edit this agent" feels like an override.
            activeAgent = readPrefs().copy();
        }
    }

    public static void closeAgent() {
        activeAgentKey = null;
        activeAgent = null;
    }

    /** Called by the per-conversation appearance page; wins over agent and global. */
    public static void openDialog(long dialogId) {
        closeAgent();
        activeDialogId = dialogId;
        activeDialogKey = dialogKey(dialogId);
        activeDialog = readFile(dialogFile(activeDialogKey));
        if (activeDialog == null) {
            activeDialog = fallbackForDialog(dialogId).copy();
        }
    }

    public static void closeDialog() {
        activeDialogKey = null;
        activeDialog = null;
        activeDialogId = 0;
    }

    public static boolean isAgentScope() {
        return activeAgent != null;
    }

    public static boolean isDialogScope() {
        return activeDialog != null;
    }

    public static String agentScopeName() {
        return activeAgentKey;
    }

    private static Snapshot current() {
        if (activeDialog != null) return activeDialog;
        return activeAgent != null ? activeAgent : readPrefs();
    }

    private static void persist(Snapshot s) {
        if (s == null) return;
        if (activeDialog != null) {
            activeDialog = s;
            try {
                FileOutputStream out = new FileOutputStream(dialogFile(activeDialogKey));
                out.write(s.toJson("").toString().getBytes("UTF-8"));
                out.close();
            } catch (Throwable ignored) {
            }
        } else if (activeAgent != null) {
            activeAgent = s;
            try {
                FileOutputStream out = new FileOutputStream(agentFile(activeAgentKey));
                out.write(s.toJson("").toString().getBytes("UTF-8"));
                out.close();
            } catch (Throwable ignored) {
            }
        } else {
            writePrefs(s);
        }
    }

    private static Snapshot readPrefs() {
        Snapshot s = new Snapshot();
        SharedPreferences p = prefs();
        s.mode = p.getString("mode", MODE_NATIVE);
        s.backgroundMode = p.getString("background_mode", BG_SOLID);
        s.background = p.getInt("background", s.background);
        s.background2 = p.getInt("background2", s.background2);
        s.backgroundAlpha = p.getInt("background_alpha", 255);
        s.backgroundDim = p.getInt("background_dim", 0);
        s.backgroundBlur = p.getInt("background_blur", 0);
        s.amoled = p.getBoolean("amoled", false);
        s.gradientIndex = p.getInt("gradient_index", 0);
        s.backgroundImage = p.getString("background_image", "");
        s.inBubble = p.getInt("in_bubble", s.inBubble);
        s.outBubble = p.getInt("out_bubble", s.outBubble);
        s.bubbleAlpha = p.getInt("bubble_alpha", 255);
        s.cornerRadius = p.getInt("corner_radius", 16);
        s.bubbleStyle = p.getString("bubble_style", STYLE_SOLID);
        s.borderColor = p.getInt("border_color", 0);
        s.borderWidth = p.getInt("border_width", 0);
        s.textColor = p.getInt("text_color", 0);
        s.fontScale = p.getFloat("font_scale", 1f);
        s.lineHeight = p.getFloat("line_height", 1f);
        s.fontPath = p.getString("font_path", "");
        s.codeFontPath = p.getString("code_font_path", "");
        return s;
    }

    private static void writePrefs(Snapshot s) {
        prefs().edit()
                .putString("mode", s.mode)
                .putString("background_mode", s.backgroundMode)
                .putInt("background", s.background)
                .putInt("background2", s.background2)
                .putInt("background_alpha", s.backgroundAlpha)
                .putInt("background_dim", s.backgroundDim)
                .putInt("background_blur", s.backgroundBlur)
                .putBoolean("amoled", s.amoled)
                .putInt("gradient_index", s.gradientIndex)
                .putString("background_image", s.backgroundImage == null ? "" : s.backgroundImage)
                .putInt("in_bubble", s.inBubble)
                .putInt("out_bubble", s.outBubble)
                .putInt("bubble_alpha", s.bubbleAlpha)
                .putInt("corner_radius", s.cornerRadius)
                .putString("bubble_style", s.bubbleStyle)
                .putInt("border_color", s.borderColor)
                .putInt("border_width", s.borderWidth)
                .putInt("text_color", s.textColor)
                .putFloat("font_scale", s.fontScale)
                .putFloat("line_height", s.lineHeight)
                .putString("font_path", s.fontPath == null ? "" : s.fontPath)
                .putString("code_font_path", s.codeFontPath == null ? "" : s.codeFontPath)
                .apply();
    }

    // ---------------- getters/setters (current scope) ----------------

    public static boolean isCustom() {
        return MODE_CUSTOM.equals(current().mode);
    }

    public static void setMode(String mode) {
        Snapshot s = current();
        s.mode = MODE_NATIVE.equals(mode) ? MODE_NATIVE : MODE_CUSTOM;
        persist(s);
    }

    public static String backgroundMode() { return current().backgroundMode; }
    public static int background() { return current().background; }
    public static int background2() { return current().background2; }
    public static int backgroundAlpha() { return current().backgroundAlpha; }
    public static int backgroundDim() { return current().backgroundDim; }
    public static int backgroundBlur() { return current().backgroundBlur; }
    public static boolean amoled() { return current().amoled; }
    public static int gradientIndex() { return current().gradientIndex; }
    public static String backgroundImage() { return current().backgroundImage; }
    public static int inBubble() { return current().inBubble; }
    public static int outBubble() { return current().outBubble; }
    public static int bubbleAlpha() { return current().bubbleAlpha; }
    public static int cornerRadius() { return current().cornerRadius; }
    public static String bubbleStyle() { return current().bubbleStyle; }
    public static int borderColor() { return current().borderColor; }
    public static int borderWidth() { return current().borderWidth; }
    public static int textColor() { return current().textColor; }
    public static float fontScale() { return current().fontScale; }
    public static float lineHeight() { return current().lineHeight; }
    public static String fontPath() { return current().fontPath; }
    public static String codeFontPath() { return current().codeFontPath; }

    public static void setInt(String key, int value) {
        Snapshot s = current();
        switch (key) {
            case "background": s.background = value; break;
            case "background2": s.background2 = value; break;
            case "background_alpha": s.backgroundAlpha = value; break;
            case "background_dim": s.backgroundDim = value; break;
            case "background_blur": s.backgroundBlur = Math.max(0, Math.min(3, value)); break;
            case "amoled": s.amoled = value != 0; break;
            case "gradient_index": s.gradientIndex = value; break;
            case "in_bubble": s.inBubble = value; break;
            case "out_bubble": s.outBubble = value; break;
            case "bubble_alpha": s.bubbleAlpha = value; break;
            case "corner_radius": s.cornerRadius = value; break;
            case "border_color": s.borderColor = value; break;
            case "border_width": s.borderWidth = value; break;
            case "text_color": s.textColor = value; break;
            default: break;
        }
        persist(s);
    }

    public static void setBackgroundMode(String mode) {
        Snapshot s = current();
        s.backgroundMode = mode == null ? BG_SOLID : mode;
        persist(s);
    }

    public static void setBubbleStyle(String style) {
        Snapshot s = current();
        s.bubbleStyle = STYLE_FROSTED.equals(style) ? STYLE_FROSTED : STYLE_SOLID;
        persist(s);
    }

    public static void setFloat(String key, float value) {
        Snapshot s = current();
        if ("font_scale".equals(key)) s.fontScale = value;
        else if ("line_height".equals(key)) {
            s.lineHeight = Math.max(0.8f, Math.min(1.8f, value));
        }
        persist(s);
    }

    public static void setString(String key, String value) {
        Snapshot s = current();
        if ("font_path".equals(key)) s.fontPath = value == null ? "" : value;
        else if ("code_font_path".equals(key)) s.codeFontPath = value == null ? "" : value;
        persist(s);
    }

    public static void setBackgroundImage(String path) {
        Snapshot s = current();
        s.backgroundImage = path == null ? "" : path;
        if (!s.backgroundImage.isEmpty()) s.backgroundMode = BG_IMAGE;
        persist(s);
    }

    public static void clearBackgroundImage() {
        Snapshot s = current();
        s.backgroundImage = "";
        if (BG_IMAGE.equals(s.backgroundMode)) s.backgroundMode = BG_SOLID;
        persist(s);
    }

    /** Applies an imported backup background as an agent override unless the
     *  agent already has its own background image (user customization wins). */
    public static boolean applyImportedBackground(long tgId, String path) {
        if (tgId == 0 || path == null || path.isEmpty()) return false;
        File f = agentFile(agentKey(tgId));
        Snapshot s = readFile(f);
        if (s != null && s.backgroundImage != null && !s.backgroundImage.isEmpty()
                && !s.backgroundImage.equals(path)) return false;
        if (s == null) s = readPrefs().copy();
        s.mode = MODE_CUSTOM;
        s.backgroundImage = path;
        s.backgroundMode = BG_IMAGE;
        try {
            FileOutputStream out = new FileOutputStream(f);
            out.write(s.toJson("").toString().getBytes("UTF-8"));
            out.close();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Current agent-scope background file, or "" when unset/global. */
    public static String backgroundForAgent(long tgId) {
        if (tgId == 0) return "";
        Snapshot s = readFile(agentFile(agentKey(tgId)));
        return s == null || s.backgroundImage == null ? "" : s.backgroundImage;
    }

    public static void enableCustom() {
        Snapshot s = current();
        if (!MODE_CUSTOM.equals(s.mode)) {
            s.mode = MODE_CUSTOM;
            persist(s);
        }
    }

    /** Copies the current agent override to the global appearance. */
    public static void applyCurrentToGlobal() {
        if (activeAgent == null) return;
        writePrefs(activeAgent.copy());
    }

    /** WCAG contrast ratio for the effective text/bubble pair. */
    public static double contrast(int fg, int bg) {
        double l1 = luminance(fg);
        double l2 = luminance(bg);
        double hi = Math.max(l1, l2), lo = Math.min(l1, l2);
        return (hi + 0.05) / (lo + 0.05);
    }

    public static int autoTextColor(int bubble) {
        return luminance(bubble) > 0.45 ? 0xFF111111 : 0xFFFFFFFF;
    }

    private static double luminance(int color) {
        double r = channel((color >> 16) & 0xFF);
        double g = channel((color >> 8) & 0xFF);
        double b = channel(color & 0xFF);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int v) {
        double c = v / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    public static void resetNative() {
        if (activeDialog != null) {
            File f = dialogFile(activeDialogKey);
            if (f.exists()) f.delete();
            // Reset means "follow the agent/global look" but keep editing this chat.
            activeDialog = fallbackForDialog(activeDialogId).copy();
            return;
        }
        if (activeAgent != null) {
            File f = agentFile(activeAgentKey);
            if (f.exists()) f.delete();
            // An agent reset falls back to the global look, but the page keeps
            // editing this agent so the next change creates a fresh override.
            activeAgent = readPrefs().copy();
            return;
        }
        prefs().edit().clear().apply();
        File bg = new File(ApplicationLoader.applicationContext.getFilesDir(), BG_FILE);
        if (bg.exists()) bg.delete();
    }

    // ---------------- JSON import/export ----------------

    public static String importJson(String raw) throws Exception {
        Snapshot s = parseJson(raw, current());
        persist(s);
        return s.mode;
    }

    /** Read-only parse used by the import preview dialog. */
    public static String previewJson(String raw) throws Exception {
        Snapshot s = parseJson(raw, current());
        return s.summary();
    }

    public static String exportJson(String name) throws Exception {
        return current().toJson(name).toString(2);
    }

    // ---------------- portable .oxtheme package (JSON + background + fonts) ----------------

    private static boolean isZip(byte[] data) {
        return data != null && data.length > 4 && data[0] == 'P' && data[1] == 'K';
    }

    /** Packages the current appearance with its wallpaper and fonts so it can be
     *  moved to another install/agent without losing assets. */
    public static byte[] exportPackage(String name) throws Exception {
        Snapshot s = current();
        JSONObject theme = s.toJson(name);
        JSONObject assets = new JSONObject();
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bos);
        try {
            File bg = s.backgroundImage == null || s.backgroundImage.isEmpty() ? null : new File(s.backgroundImage);
            if (bg != null && bg.isFile()) {
                String entry = "background" + extension(bg.getName());
                putEntry(zip, entry, readFileBytes(bg, 8 * 1024 * 1024));
                assets.put("background", entry);
            }
            File font = s.fontPath == null || s.fontPath.isEmpty() ? null : new File(s.fontPath);
            if (font != null && font.isFile()) {
                putEntry(zip, "chat-font.ttf", readFileBytes(font, 8 * 1024 * 1024));
                assets.put("font", "chat-font.ttf");
            }
            File codeFont = s.codeFontPath == null || s.codeFontPath.isEmpty() ? null : new File(s.codeFontPath);
            if (codeFont != null && codeFont.isFile()) {
                putEntry(zip, "code-font.ttf", readFileBytes(codeFont, 8 * 1024 * 1024));
                assets.put("codeFont", "code-font.ttf");
            }
            theme.put("assets", assets);
            zip.putNextEntry(new java.util.zip.ZipEntry("theme.json"));
            zip.write(theme.toString(2).getBytes("UTF-8"));
            zip.closeEntry();
            zip.finish();
        } finally {
            zip.close();
        }
        return bos.toByteArray();
    }

    /** Parses a JSON or .oxtheme package and returns a preview without writing. */
    public static String previewBytes(byte[] data) throws Exception {
        if (data == null || data.length == 0) throw new IllegalArgumentException("empty file");
        if (!isZip(data)) return previewJson(new String(data, "UTF-8"));
        String json = null;
        boolean hasBackground = false, hasFont = false;
        java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(data));
        java.util.zip.ZipEntry e;
        int entries = 0;
        while ((e = zis.getNextEntry()) != null && entries++ < 30) {
            String n = e.getName();
            if ("theme.json".equals(n)) {
                json = new String(readAll(zis, 512 * 1024), "UTF-8");
            } else if (n.startsWith("background.")) {
                hasBackground = true;
            } else if (n.endsWith(".ttf")) {
                hasFont = true;
            }
        }
        zis.close();
        if (json == null) throw new IllegalArgumentException("theme.json missing");
        Snapshot s = parseJson(json, current());
        return s.summary()
                + (hasBackground ? " · 含背景图" : "")
                + (hasFont ? " · 含字体" : "");
    }

    /** Applies a JSON or .oxtheme package, restoring its assets into app-private storage. */
    public static void importBytes(byte[] data) throws Exception {
        if (data == null || data.length == 0) throw new IllegalArgumentException("empty file");
        if (!isZip(data)) {
            importJson(new String(data, "UTF-8"));
            return;
        }
        String json = null;
        byte[] background = null;
        byte[] font = null;
        byte[] codeFont = null;
        java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(data));
        java.util.zip.ZipEntry e;
        int entries = 0;
        while ((e = zis.getNextEntry()) != null && entries++ < 30) {
            String n = e.getName();
            if ("theme.json".equals(n)) {
                json = new String(readAll(zis, 512 * 1024), "UTF-8");
            } else if (n.startsWith("background.") && background == null) {
                background = readAll(zis, 8 * 1024 * 1024);
            } else if ("chat-font.ttf".equals(n) && font == null) {
                font = readAll(zis, 8 * 1024 * 1024);
            } else if ("code-font.ttf".equals(n) && codeFont == null) {
                codeFont = readAll(zis, 8 * 1024 * 1024);
            }
        }
        zis.close();
        if (json == null) throw new IllegalArgumentException("theme.json missing");
        Snapshot s = parseJson(json, current());
        if (background != null && background.length > 0) {
            File out = new File(ApplicationLoader.applicationContext.getFilesDir(),
                    "ox_chat_bg_imported_" + System.currentTimeMillis() + ".img");
            writeFile(out, background);
            s.backgroundImage = out.getAbsolutePath();
            s.backgroundMode = BG_IMAGE;
        }
        if (font != null && font.length > 0) {
            File out = new File(fontDir(), "imported_chat_" + System.currentTimeMillis() + ".ttf");
            writeFile(out, font);
            Typeface tf = Typeface.createFromFile(out);
            if (tf != null) s.fontPath = out.getAbsolutePath();
        }
        if (codeFont != null && codeFont.length > 0) {
            File out = new File(fontDir(), "imported_code_" + System.currentTimeMillis() + ".ttf");
            writeFile(out, codeFont);
            Typeface tf = Typeface.createFromFile(out);
            if (tf != null) s.codeFontPath = out.getAbsolutePath();
        }
        persist(s);
    }

    private static File fontDir() {
        File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), DIR_FONTS);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static String extension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 ? name.substring(dot) : ".img";
    }

    private static void putEntry(java.util.zip.ZipOutputStream zip, String name, byte[] data) throws Exception {
        zip.putNextEntry(new java.util.zip.ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private static byte[] readFileBytes(File f, int max) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        try {
            return readAll(in, max);
        } finally {
            in.close();
        }
    }

    private static byte[] readAll(java.io.InputStream in, int max) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0 && bos.size() < max) {
            bos.write(buf, 0, Math.min(n, max - bos.size()));
        }
        return bos.toByteArray();
    }

    private static void writeFile(File f, byte[] data) throws Exception {
        java.io.FileOutputStream out = new java.io.FileOutputStream(f);
        out.write(data);
        out.close();
    }

    private static Snapshot parseJson(String raw, Snapshot base) throws Exception {
        JSONObject o = new JSONObject(raw);
        Snapshot s = parseJsonObject(o, base == null ? new Snapshot() : base.copy());
        if (s.mode == null || (!MODE_CUSTOM.equals(s.mode) && !MODE_NATIVE.equals(s.mode))) {
            s.mode = MODE_CUSTOM;
        }
        return s;
    }

    private static Snapshot parseJsonObject(JSONObject o, Snapshot s) throws Exception {
        JSONObject nested = o.optJSONObject("values");
        if (nested != null) {
            JSONObject merged = new JSONObject();
            java.util.Iterator<String> keys = nested.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                merged.put(k, nested.opt(k));
            }
            if (o.has("name")) merged.put("name", o.optString("name", ""));
            o = merged;
        }
        // Operit / Operit2 appearance snapshot keys.
        boolean operit = o.has("bubble_ai_bubble_color") || o.has("bubble_user_bubble_color")
                || o.has("bubble_ai_text_color") || o.has("bubble_user_text_color")
                || o.has("bubble_ai_bubble_liquid_glass") || o.has("bubble_ai_bubble_water_glass")
                || o.has("bubble_user_bubble_liquid_glass") || o.has("bubble_user_bubble_water_glass");
        if (operit) {
            int ai = color(o, "bubble_ai_bubble_color", "bubble_ai_color");
            int user = color(o, "bubble_user_bubble_color", "bubble_user_color");
            if (ai != 0) s.inBubble = ai;
            if (user != 0) s.outBubble = user;
            int aiText = color(o, "bubble_ai_text_color");
            int userText = color(o, "bubble_user_text_color");
            if (aiText != 0 && userText != 0 && aiText == userText) s.textColor = aiText;
            else if (aiText != 0) s.textColor = aiText;
            else if (userText != 0) s.textColor = userText;
            if (o.has("font_scale")) {
                s.fontScale = Math.max(0.8f, Math.min(1.5f, (float) o.optDouble("font_scale", s.fontScale)));
            }
            if (o.has("background_image_opacity")) {
                float op = (float) o.optDouble("background_image_opacity", 1.0);
                s.backgroundAlpha = Math.max(0, Math.min(255, Math.round(op * 255f)));
            }
            String themeMode = o.optString("theme_mode", "");
            if (!themeMode.isEmpty()) {
                s.background = "dark".equalsIgnoreCase(themeMode) ? 0xFF101114 : 0xFFF3F1F7;
            }
            if (o.optBoolean("bubble_ai_bubble_liquid_glass", false)
                    || o.optBoolean("bubble_ai_bubble_water_glass", false)
                    || o.optBoolean("bubble_user_bubble_liquid_glass", false)
                    || o.optBoolean("bubble_user_bubble_water_glass", false)) {
                s.bubbleStyle = STYLE_FROSTED;
            }
            if (o.optBoolean("use_background_blur", false)
                    || o.optDouble("background_blur_radius", 0) > 0) {
                float radius = (float) o.optDouble("background_blur_radius", 10);
                s.backgroundBlur = radius >= 20 ? 3 : radius >= 12 ? 2 : 1;
            }
            int primary = color(o, "custom_primary_color", "custom_secondary_color");
            if (primary != 0) {
                s.borderColor = primary;
                s.borderWidth = 1;
            }
        }
        if (o.has("mode")) s.mode = MODE_CUSTOM.equals(o.optString("mode")) ? MODE_CUSTOM : s.mode;
        if (o.has("backgroundMode")) s.backgroundMode = o.optString("backgroundMode", s.backgroundMode);
        int bg = color(o, "backgroundArgb", "backgroundColorArgb", "background", "background_color",
                "chat_background", "chatBackground", "backgroundColor");
        if (bg != 0) s.background = bg;
        int bg2 = color(o, "background2Argb", "backgroundGradientArgb", "background_gradient", "gradient_color");
        if (bg2 != 0) s.background2 = bg2;
        if (has(o, "backgroundAlpha", "background_alpha")) {
            s.backgroundAlpha = clamp(intValue(o, s.backgroundAlpha, "backgroundAlpha", "background_alpha"), 0, 255);
        }
        if (has(o, "backgroundDim", "background_dim", "dim")) {
            s.backgroundDim = clamp(intValue(o, s.backgroundDim, "backgroundDim", "background_dim", "dim"), 0, 220);
        }
        if (has(o, "backgroundBlur", "background_blur", "blur", "blurSigma", "blur_sigma")) {
            s.backgroundBlur = clamp(intValue(o, s.backgroundBlur,
                    "backgroundBlur", "background_blur", "blur", "blurSigma", "blur_sigma"), 0, 3);
        }
        if (has(o, "amoled", "amoledMode", "amoled_mode")) {
            s.amoled = boolValue(o, s.amoled, "amoled", "amoledMode", "amoled_mode");
        }
        if (has(o, "gradientIndex", "gradient_index")) {
            s.gradientIndex = clamp(intValue(o, s.gradientIndex, "gradientIndex", "gradient_index"), 0, 2);
        }
        int in = color(o, "inBubbleArgb", "incomingBubbleArgb", "inBubbleColorArgb", "bubbleInArgb",
                "in_bubble", "incoming_bubble", "incomingBubble", "inBubbleColor", "bubble_in",
                "secondaryColorArgb", "secondary_color_argb");
        if (in != 0) s.inBubble = in;
        int out = color(o, "outBubbleArgb", "outgoingBubbleArgb", "outBubbleColorArgb", "bubbleOutArgb",
                "out_bubble", "outgoing_bubble", "outgoingBubble", "outBubbleColor", "bubble_out",
                "primaryColorArgb", "primary_color_argb");
        if (out != 0) s.outBubble = out;
        int alpha = intValue(o, s.bubbleAlpha, "bubbleAlpha", "bubble_alpha", "bubble_opacity", "bubbleOpacity", "opacity");
        if (alpha <= 1 && has(o, "bubbleAlpha", "bubble_alpha", "bubble_opacity", "bubbleOpacity", "opacity")) {
            alpha = Math.round(alpha * 255);
        }
        s.bubbleAlpha = clamp(alpha, 40, 255);
        s.cornerRadius = clamp(intValue(o, s.cornerRadius, "cornerRadius", "bubbleCornerRadius", "corner_radius", "bubble_radius", "radius"), 0, 28);
        s.fontScale = Math.max(0.8f, Math.min(1.5f,
                floatValue(o, s.fontScale, "fontScale", "font_scale", "fontSizeScale", "font_size", "message_font_scale")));
        s.lineHeight = Math.max(0.8f, Math.min(1.8f,
                floatValue(o, s.lineHeight, "lineHeight", "line_height", "lineSpacing", "line_spacing")));
        if (has(o, "bubbleStyle", "bubble_style")) {
            s.bubbleStyle = STYLE_FROSTED.equals(o.optString("bubbleStyle", o.optString("bubble_style")))
                    ? STYLE_FROSTED : STYLE_SOLID;
        }
        int border = color(o, "borderArgb", "borderColorArgb", "border_color", "borderColor", "borderArgbLight", "borderArgbDark");
        if (border != 0) s.borderColor = border;
        if (has(o, "borderWidth", "border_width")) {
            s.borderWidth = clamp(intValue(o, s.borderWidth, "borderWidth", "border_width"), 0, 4);
        }
        int text = color(o, "textArgb", "textColorArgb", "text_color", "textColor", "textArgbLight", "textArgbDark");
        if (text != 0) s.textColor = text;
        String fp = o.optString("fontPath", o.optString("font_path", ""));
        if (!fp.isEmpty()) s.fontPath = fp;
        String cfp = o.optString("codeFontPath", o.optString("code_font_path", ""));
        if (!cfp.isEmpty()) s.codeFontPath = cfp;
        if (o.has("backgroundImage") || o.has("background_image")) {
            String img = o.optString("backgroundImage", o.optString("background_image", ""));
            if (!img.isEmpty()) {
                s.backgroundImage = img;
                s.backgroundMode = BG_IMAGE;
            }
        }
        if (s.background == 0 && s.inBubble == 0 && s.outBubble == 0) {
            throw new IllegalArgumentException("no colors");
        }
        return s;
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

    private static String safePresetName(String name) {
        if (name == null) return "";
        return name.trim().replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
    }

    public static void savePreset(String name) throws Exception {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("name");
        String safe = safePresetName(name);
        JSONObject o = current().toJson(name.trim());
        FileOutputStream out = new FileOutputStream(new File(presetDir(), safe + ".json"));
        out.write(o.toString().getBytes("UTF-8"));
        out.close();
    }

    public static void renamePreset(String oldName, String newName) throws Exception {
        if (newName == null || newName.trim().isEmpty()) throw new IllegalArgumentException("name");
        String safe = safePresetName(newName);
        File oldFile = new File(presetDir(), oldName + ".json");
        if (!oldFile.isFile()) throw new IllegalArgumentException("preset not found");
        File newFile = new File(presetDir(), safe + ".json");
        if (!oldName.equals(safe) && newFile.exists()) {
            throw new IllegalArgumentException("preset exists");
        }
        Snapshot s = readFile(oldFile);
        if (s == null) throw new IllegalArgumentException("preset not found");
        FileOutputStream out = new FileOutputStream(newFile);
        out.write(s.toJson(safe).toString().getBytes("UTF-8"));
        out.close();
        if (!oldName.equals(safe)) oldFile.delete();
    }

    public static void applyPreset(String name) throws Exception {
        File f = new File(presetDir(), name + ".json");
        Snapshot s = readFile(f);
        if (s == null) throw new IllegalArgumentException("preset not found");
        s.mode = MODE_CUSTOM;
        persist(s);
    }

    // ---------------- auto palette from the background ----------------

    /** Derives a readable bubble/text/border set from the current background.
     *  Images are sampled down to 64px so large wallpapers never allocate much. */
    public static void autoPaletteFromBackground() {
        Snapshot s = current();
        int base = averageBackgroundColor(s);
        float[] hsv = new float[3];
        Color.colorToHSV(base, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.25f + 0.08f);
        float[] accentHsv = new float[]{hsv[0], hsv[1], Math.max(0.35f, Math.min(0.75f, hsv[2]))};
        int accent = Color.HSVToColor(accentHsv);
        boolean dark = luminance(base) < 0.42;
        s.inBubble = blend(base, 0xFFFFFFFF, dark ? 0.84f : 0.94f);
        s.outBubble = blend(accent, 0xFFFFFFFF, dark ? 0.62f : 0.72f);
        int text = autoTextColor(blend(s.inBubble, s.outBubble, 0.5f));
        s.textColor = text;
        s.borderColor = blend(s.outBubble, luminance(s.outBubble) > 0.5 ? 0xFF111111 : 0xFFFFFFFF, 0.28f);
        s.borderWidth = 1;
        s.bubbleStyle = STYLE_SOLID;
        s.bubbleAlpha = 255;
        s.cornerRadius = 16;
        s.backgroundDim = dark ? 60 : 0;
        persist(s);
    }

    private static int averageBackgroundColor(Snapshot s) {
        String mode = s.backgroundMode == null ? BG_SOLID : s.backgroundMode;
        if (BG_GRADIENT.equals(mode)) {
            return blend(s.background, s.background2, 0.5f);
        }
        File f = s.backgroundImage == null || s.backgroundImage.isEmpty() ? null : new File(s.backgroundImage);
        if (BG_IMAGE.equals(mode) && f != null && f.isFile()) {
            try {
                android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
                int sample = 1;
                while (bounds.outWidth / sample > 64 || bounds.outHeight / sample > 64) sample *= 2;
                android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                opts.inSampleSize = sample;
                android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
                if (bmp != null) {
                    long r = 0, g = 0, b = 0, n = 0;
                    for (int y = 0; y < bmp.getHeight(); y += 2) {
                        for (int x = 0; x < bmp.getWidth(); x += 2) {
                            int c = bmp.getPixel(x, y);
                            if ((c >>> 24) < 32) continue;
                            r += (c >> 16) & 0xFF;
                            g += (c >> 8) & 0xFF;
                            b += c & 0xFF;
                            n++;
                        }
                    }
                    bmp.recycle();
                    if (n > 0) {
                        return 0xFF000000 | ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return s.background;
    }

    private static int blend(int a, int b, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((a >> 16) & 0xFF) * (1 - k) + ((b >> 16) & 0xFF) * k);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - k) + ((b >> 8) & 0xFF) * k);
        int bl = Math.round((a & 0xFF) * (1 - k) + (b & 0xFF) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    // ---------------- built-in one-tap themes ----------------

    private static final String[] BUILT_IN_NAMES = {
            "午夜墨黑", "纸墨", "深海", "樱花", "森林", "赛博霓虹"
    };

    public static int builtInCount() {
        return BUILT_IN_NAMES.length;
    }

    public static String builtInName(int index) {
        return index >= 0 && index < BUILT_IN_NAMES.length ? BUILT_IN_NAMES[index] : "主题";
    }

    /** One-tap curated themes; they intentionally overwrite the whole snapshot. */
    public static void applyBuiltIn(int index) {
        Snapshot s = new Snapshot();
        s.mode = MODE_CUSTOM;
        s.bubbleAlpha = 255;
        s.cornerRadius = 16;
        s.fontScale = 1f;
        s.bubbleStyle = STYLE_SOLID;
        s.borderWidth = 1;
        s.backgroundMode = BG_SOLID;
        switch (index) {
            case 0: // 午夜墨黑 / AMOLED
                s.background = 0xFF000000;
                s.background2 = 0xFF000000;
                s.inBubble = 0xFF121212;
                s.outBubble = 0xFF1E1E1E;
                s.textColor = 0xFFFFFFFF;
                s.borderColor = 0xFF333333;
                s.cornerRadius = 14;
                break;
            case 1: // 纸墨
                s.background = 0xFFF7F3E8;
                s.background2 = 0xFFEFE7D6;
                s.inBubble = 0xFFFFFFFF;
                s.outBubble = 0xFFE8F0D8;
                s.textColor = 0xFF1A1A1A;
                s.borderColor = 0xFFD8D0C0;
                s.cornerRadius = 12;
                break;
            case 2: // 深海
                s.backgroundMode = BG_GRADIENT;
                s.background = 0xFF0B1E3A;
                s.background2 = 0xFF123A5A;
                s.inBubble = 0xFF16324F;
                s.outBubble = 0xFF1F6F8B;
                s.textColor = 0xFFEAF6FF;
                s.borderColor = 0xFF2A5A7A;
                s.cornerRadius = 18;
                break;
            case 3: // 樱花
                s.background = 0xFFFFF1F5;
                s.background2 = 0xFFFFE3EC;
                s.inBubble = 0xFFFFFFFF;
                s.outBubble = 0xFFFFE0EC;
                s.textColor = 0xFF3A1F2B;
                s.borderColor = 0xFFF2C6D6;
                s.cornerRadius = 20;
                break;
            case 4: // 森林
                s.background = 0xFF0F1F17;
                s.background2 = 0xFF163326;
                s.inBubble = 0xFF173326;
                s.outBubble = 0xFF2E5D3A;
                s.textColor = 0xFFEAF7EE;
                s.borderColor = 0xFF3E6B4D;
                s.cornerRadius = 16;
                break;
            case 5: // 赛博霓虹
                s.backgroundMode = BG_GRADIENT;
                s.background = 0xFF0A0014;
                s.background2 = 0xFF1B0033;
                s.inBubble = 0xFF1B0033;
                s.outBubble = 0xFF7C00FF;
                s.textColor = 0xFF00F0FF;
                s.borderColor = 0xFFFF2D95;
                s.borderWidth = 2;
                s.cornerRadius = 8;
                break;
            default:
                return;
        }
        persist(s);
    }

    /** Deterministic keyword score over an agent's name/persona; no network. */
    public static int recommendBuiltIn(String persona) {
        String text = persona == null ? "" : persona.toLowerCase(java.util.Locale.ROOT);
        int[] scores = new int[BUILT_IN_NAMES.length];
        score(scores, 0, text, "墨黑", "极简", "黑", "暗", "amoled", "midnight", "dark");
        score(scores, 1, text, "纸", "书", "墨", "文", "历史", "古典", "诗", "paper", "ink", "book");
        score(scores, 2, text, "深海", "海洋", "海", "水", "蓝", "冷静", "科技", "代码", "程序", "blue", "ocean", "code");
        score(scores, 3, text, "樱花", "樱", "粉", "少女", "可爱", "恋爱", "甜", "sakura", "pink");
        score(scores, 4, text, "森林", "自然", "植物", "树", "叶", "治愈", "green", "forest", "茶");
        score(scores, 5, text, "赛博", "霓虹", "未来", "cyber", "neon", "深夜", "酒吧", "调酒", "夜店");
        int best = 2;
        int bestScore = 0;
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] > bestScore) {
                best = i;
                bestScore = scores[i];
            }
        }
        return best;
    }

    private static void score(int[] scores, int index, String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) scores[index]++;
        }
    }

    private static File recommendationFile() {
        File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "ox_recommend");
        if (!dir.exists()) dir.mkdirs();
        String scope = activeDialog != null ? "dialog_" + activeDialogId
                : activeAgent != null ? "agent_" + activeAgentKey : "global";
        return new File(dir, scope.replaceAll("[^a-zA-Z0-9._-]", "_") + ".json");
    }

    /** Saves the pre-recommendation snapshot once, so the editor can undo it. */
    public static void backupForRecommendation() {
        File f = recommendationFile();
        if (f.isFile() && f.length() > 0) return;
        try {
            FileOutputStream out = new FileOutputStream(f);
            out.write(current().toJson("").toString().getBytes("UTF-8"));
            out.close();
        } catch (Throwable ignored) {
        }
    }

    public static boolean hasRecommendationBackup() {
        File f = recommendationFile();
        return f.isFile() && f.length() > 0;
    }

    public static boolean restoreRecommendationBackup() {
        File f = recommendationFile();
        Snapshot s = readFile(f);
        if (s == null) return false;
        persist(s);
        f.delete();
        return true;
    }

    public static void deletePreset(String name) {
        new File(presetDir(), name + ".json").delete();
    }

    // ---------------- portable preset bundle ----------------

    private static final String BUNDLE_FORMAT = "orionekox-theme-bundle";
    private static final int BUNDLE_VERSION = 1;
    private static final int BUNDLE_MAX_PRESETS = 100;
    private static final int BUNDLE_MAX_ASSET = 8 * 1024 * 1024;
    private static final int BUNDLE_MAX_TOTAL = 40 * 1024 * 1024;

    public static class BundlePreview {
        public final List<String> names = new ArrayList<>();
        public int duplicates;
        public int backgrounds;
        public int fonts;
    }

    public static class BundleResult {
        public int imported;
        public int skipped;
        public int backgrounds;
        public int fonts;
        public final List<String> names = new ArrayList<>();
    }

    private static class BundlePreset {
        String name;
        JSONObject theme;
        byte[] background;
        byte[] font;
        byte[] codeFont;
    }

    private static class PendingPreset {
        String safeName;
        Snapshot snapshot;
        byte[] background;
        byte[] font;
        byte[] codeFont;
    }

    private static String presetFileName(String name) {
        if (name == null) return "";
        String safe = name.trim().replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
        if (safe.length() > 80) safe = safe.substring(0, 80);
        return safe;
    }

    /** Zips every named preset with the assets its snapshot points at. */
    public static byte[] exportPresetBundle() throws Exception {
        List<String> names = presets();
        if (names.isEmpty()) throw new IllegalArgumentException("no presets");
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bos);
        JSONArray items = new JSONArray();
        long total = 0;
        try {
            for (String name : names) {
                if (items.length() >= BUNDLE_MAX_PRESETS) break;
                Snapshot s = readFile(new File(presetDir(), name + ".json"));
                if (s == null) continue;
                int index = items.length();
                JSONObject assets = new JSONObject();

                File bg = s.backgroundImage == null || s.backgroundImage.isEmpty() ? null : new File(s.backgroundImage);
                if (bg != null && bg.isFile() && bg.length() <= BUNDLE_MAX_ASSET && total + bg.length() <= BUNDLE_MAX_TOTAL) {
                    String entry = "presets/" + index + "/background" + extension(bg.getName());
                    putEntry(zip, entry, readFileBytes(bg, BUNDLE_MAX_ASSET));
                    assets.put("background", entry);
                    total += bg.length();
                }
                File font = s.fontPath == null || s.fontPath.isEmpty() ? null : new File(s.fontPath);
                if (font != null && font.isFile() && font.length() <= BUNDLE_MAX_ASSET && total + font.length() <= BUNDLE_MAX_TOTAL) {
                    String entry = "presets/" + index + "/chat-font.ttf";
                    putEntry(zip, entry, readFileBytes(font, BUNDLE_MAX_ASSET));
                    assets.put("font", entry);
                    total += font.length();
                }
                File codeFont = s.codeFontPath == null || s.codeFontPath.isEmpty() ? null : new File(s.codeFontPath);
                if (codeFont != null && codeFont.isFile() && codeFont.length() <= BUNDLE_MAX_ASSET && total + codeFont.length() <= BUNDLE_MAX_TOTAL) {
                    String entry = "presets/" + index + "/code-font.ttf";
                    putEntry(zip, entry, readFileBytes(codeFont, BUNDLE_MAX_ASSET));
                    assets.put("codeFont", entry);
                    total += codeFont.length();
                }

                JSONObject item = new JSONObject();
                item.put("name", name);
                item.put("theme", s.toJson(name));
                item.put("assets", assets);
                items.put(item);
            }
            if (items.length() == 0) throw new IllegalArgumentException("no presets");
            JSONObject root = new JSONObject();
            root.put("format", BUNDLE_FORMAT);
            root.put("version", BUNDLE_VERSION);
            root.put("createdAt", System.currentTimeMillis());
            root.put("presets", items);
            zip.putNextEntry(new java.util.zip.ZipEntry("bundle.json"));
            zip.write(root.toString(2).getBytes("UTF-8"));
            zip.closeEntry();
            zip.finish();
        } finally {
            zip.close();
        }
        return bos.toByteArray();
    }

    /** Validates a bundle and reports what an import would do, without writing. */
    public static BundlePreview previewPresetBundle(byte[] data) throws Exception {
        List<BundlePreset> list = parseBundle(data);
        BundlePreview p = new BundlePreview();
        Set<String> existing = new HashSet<>(presets());
        Set<String> seen = new HashSet<>();
        for (BundlePreset bp : list) {
            p.names.add(bp.name);
            String safe = presetFileName(bp.name);
            if (safe.isEmpty() || existing.contains(safe) || !seen.add(safe)) {
                p.duplicates++;
            }
            if (bp.background != null && bp.background.length > 0) p.backgrounds++;
            if (bp.font != null || bp.codeFont != null) p.fonts++;
        }
        return p;
    }

    /** Imports presets after the whole bundle has validated; duplicate names are skipped. */
    public static BundleResult importPresetBundle(byte[] data) throws Exception {
        List<BundlePreset> list = parseBundle(data);
        BundleResult r = new BundleResult();
        Set<String> existing = new HashSet<>(presets());
        Set<String> planned = new HashSet<>();
        List<PendingPreset> pending = new ArrayList<>();
        for (BundlePreset bp : list) {
            String safe = presetFileName(bp.name);
            if (safe.isEmpty() || existing.contains(safe) || !planned.add(safe)) {
                r.skipped++;
                continue;
            }
            Snapshot s = parseJsonObject(bp.theme, new Snapshot());
            s.mode = MODE_CUSTOM;
            PendingPreset pp = new PendingPreset();
            pp.safeName = safe;
            pp.snapshot = s;
            pp.background = bp.background;
            pp.font = bp.font;
            pp.codeFont = bp.codeFont;
            pending.add(pp);
        }

        List<File> created = new ArrayList<>();
        try {
            long stamp = System.currentTimeMillis();
            int index = 0;
            for (PendingPreset pp : pending) {
                if (pp.background != null && pp.background.length > 0) {
                    File out = new File(ApplicationLoader.applicationContext.getFilesDir(),
                            "ox_chat_bg_preset_" + stamp + "_" + index + ".img");
                    writeFile(out, pp.background);
                    created.add(out);
                    pp.snapshot.backgroundMode = BG_IMAGE;
                    pp.snapshot.backgroundImage = out.getAbsolutePath();
                    r.backgrounds++;
                } else if (BG_IMAGE.equals(pp.snapshot.backgroundMode)) {
                    pp.snapshot.backgroundMode = BG_SOLID;
                    pp.snapshot.backgroundImage = "";
                }
                if (pp.font != null && pp.font.length > 0) {
                    File out = new File(fontDir(), "preset_font_" + stamp + "_" + index + ".ttf");
                    writeFile(out, pp.font);
                    created.add(out);
                    pp.snapshot.fontPath = out.getAbsolutePath();
                    r.fonts++;
                } else if (!pathExists(pp.snapshot.fontPath)) {
                    pp.snapshot.fontPath = "";
                }
                if (pp.codeFont != null && pp.codeFont.length > 0) {
                    File out = new File(fontDir(), "preset_code_" + stamp + "_" + index + ".ttf");
                    writeFile(out, pp.codeFont);
                    created.add(out);
                    pp.snapshot.codeFontPath = out.getAbsolutePath();
                } else if (!pathExists(pp.snapshot.codeFontPath)) {
                    pp.snapshot.codeFontPath = "";
                }
                index++;
            }
            for (int i = 0; i < pending.size(); i++) {
                PendingPreset pp = pending.get(i);
                File f = new File(presetDir(), pp.safeName + ".json");
                FileOutputStream out = new FileOutputStream(f);
                try {
                    out.write(pp.snapshot.toJson(pp.safeName).toString(2).getBytes("UTF-8"));
                } finally {
                    out.close();
                }
                created.add(f);
                r.imported++;
                r.names.add(pp.safeName);
            }
        } catch (Throwable t) {
            for (File f : created) {
                try {
                    f.delete();
                } catch (Throwable ignored) {
                }
            }
            throw t;
        }
        return r;
    }

    private static boolean pathExists(String path) {
        return path != null && !path.isEmpty() && new File(path).isFile();
    }

    private static List<BundlePreset> parseBundle(byte[] data) throws Exception {
        if (data == null || data.length == 0) throw new IllegalArgumentException("empty file");
        if (!isZip(data)) throw new IllegalArgumentException("not a bundle");
        String json = null;
        Map<String, byte[]> assets = new HashMap<>();
        long total = 0;
        int entries = 0;
        java.util.zip.ZipInputStream zis =
                new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(data));
        try {
            java.util.zip.ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (++entries > 300) throw new IllegalArgumentException("too many entries");
                String n = e.getName();
                if ("bundle.json".equals(n)) {
                    json = new String(readAll(zis, 1024 * 1024), "UTF-8");
                } else if (n.startsWith("presets/") && !n.contains("..")) {
                    byte[] bytes = readAll(zis, BUNDLE_MAX_ASSET);
                    total += bytes.length;
                    if (total > BUNDLE_MAX_TOTAL) throw new IllegalArgumentException("bundle too large");
                    assets.put(n, bytes);
                }
            }
        } finally {
            zis.close();
        }
        if (json == null) throw new IllegalArgumentException("bundle.json missing");
        JSONObject root = new JSONObject(json);
        if (!BUNDLE_FORMAT.equals(root.optString("format"))) {
            throw new IllegalArgumentException("unsupported bundle");
        }
        JSONArray arr = root.optJSONArray("presets");
        if (arr == null) throw new IllegalArgumentException("no presets");
        List<BundlePreset> out = new ArrayList<>();
        for (int i = 0; i < arr.length() && out.size() < BUNDLE_MAX_PRESETS; i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) continue;
            String name = item.optString("name", "").trim();
            JSONObject theme = item.optJSONObject("theme");
            if (name.isEmpty() || theme == null) continue;
            BundlePreset bp = new BundlePreset();
            bp.name = name;
            bp.theme = theme;
            JSONObject a = item.optJSONObject("assets");
            if (a != null) {
                bp.background = assets.get(a.optString("background", ""));
                bp.font = assets.get(a.optString("font", ""));
                bp.codeFont = assets.get(a.optString("codeFont", ""));
            }
            out.add(bp);
        }
        if (out.isEmpty()) throw new IllegalArgumentException("no presets");
        return out;
    }

    public static JSONObject toJson(String name) throws Exception {
        return current().toJson(name);
    }

    // ---------------- fonts ----------------

    public static void importFont(InputStream in, boolean code) throws Exception {
        File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), DIR_FONTS);
        if (!dir.exists()) dir.mkdirs();
        File f = new File(dir, (code ? "code_" : "chat_") + System.currentTimeMillis() + ".ttf");
        FileOutputStream out = new FileOutputStream(f);
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.close();
        in.close();
        Typeface tf = Typeface.createFromFile(f);
        if (tf == null) {
            f.delete();
            throw new IllegalArgumentException("unsupported font");
        }
        Snapshot s = current();
        if (code) s.codeFontPath = f.getAbsolutePath();
        else s.fontPath = f.getAbsolutePath();
        persist(s);
    }

    // ---------------- runtime application ----------------

    /** Reads the override for this dialog: per-chat, then per-agent, then global. */
    private static Snapshot forDialog(long dialogId) {
        Snapshot d = readFile(dialogFile(dialogKey(dialogId)));
        if (d != null) return d;
        if (OxAgentsBridge.agentKeyForDialog(dialogId) != null) {
            Snapshot s = readFile(agentFile(agentKey(dialogId)));
            if (s != null) return s;
        }
        return readPrefs();
    }

    /** Downscaled + stack-blurred wallpaper; cached because applyToChat builds it repeatedly. */
    private static android.graphics.Bitmap blurredBackground(File f, int level) {
        String key = f.getAbsolutePath() + ":" + f.lastModified() + ":" + level;
        if (key.equals(blurCacheKey) && blurCacheBitmap != null && !blurCacheBitmap.isRecycled()) {
            return blurCacheBitmap;
        }
        android.graphics.Bitmap src = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (src == null) return null;
        float scale = level >= 3 ? 8f : level == 2 ? 5f : 3f;
        android.graphics.Bitmap blurred;
        try {
            blurred = org.telegram.messenger.Utilities.stackBlurBitmapWithScaleFactor(src, scale);
        } catch (Throwable t) {
            blurred = src;
        }
        blurCacheKey = key;
        blurCacheBitmap = blurred;
        return blurred;
    }

    /** The active scope's wallpaper, including blur and dim, for the editor preview. */
    public static Drawable currentBackgroundDrawable() {
        return makeBackground(current(), ApplicationLoader.applicationContext.getResources());
    }

    public static Drawable backgroundDrawableFor(Snapshot s) {
        return makeBackground(s, ApplicationLoader.applicationContext.getResources());
    }

    public static Typeface currentTypeface() {
        return typefaceFrom(current().fontPath);
    }

    public static Typeface typefaceFor(Snapshot s) {
        return typefaceFrom(s.fontPath);
    }

    public static Snapshot currentSnapshot() {
        return current();
    }

    public static Snapshot presetSnapshot(String name) {
        return readFile(new File(presetDir(), name + ".json"));
    }

    private static Drawable makeBackground(Snapshot s, android.content.res.Resources res) {
        if (s.amoled) return new ColorDrawable(0xFF000000);
        Drawable base;
        String mode = s.backgroundMode == null ? BG_SOLID : s.backgroundMode;
        if (BG_IMAGE.equals(mode)) {
            File f = s.backgroundImage == null || s.backgroundImage.isEmpty() ? null : new File(s.backgroundImage);
            android.graphics.Bitmap bmp = null;
            if (f != null && f.isFile()) {
                bmp = s.backgroundBlur > 0
                        ? blurredBackground(f, s.backgroundBlur)
                        : BitmapFactory.decodeFile(f.getAbsolutePath());
            }
            if (bmp != null) {
                BitmapDrawable bd = new BitmapDrawable(res, bmp);
                bd.setAlpha(clamp(s.backgroundAlpha, 0, 255));
                base = bd;
            } else {
                base = new ColorDrawable(withAlpha(s.background, s.backgroundAlpha));
            }
        } else if (BG_GRADIENT.equals(mode)) {
            GradientDrawable gd = new GradientDrawable(
                    s.gradientIndex == 1 ? GradientDrawable.Orientation.LEFT_RIGHT
                            : s.gradientIndex == 2 ? GradientDrawable.Orientation.TL_BR
                            : GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{withAlpha(s.background, s.backgroundAlpha), withAlpha(s.background2, s.backgroundAlpha)});
            base = gd;
        } else {
            base = new ColorDrawable(withAlpha(s.background, s.backgroundAlpha));
        }
        if (s.backgroundDim > 0) {
            return new LayerDrawable(new Drawable[]{base, new ColorDrawable(Color.argb(clamp(s.backgroundDim, 0, 220), 0, 0, 0))});
        }
        return base;
    }

    private static void applyBubbleOverride(org.telegram.ui.ActionBar.MessageDrawable d,
                                            int color, int radius, int borderColor, int borderWidth) {
        if (d == null) return;
        d.overrideColor = color;
        d.setOverrideRoundRadius(radius);
        d.setOverrideBorder(borderColor, borderWidth);
    }

    private static void clearBubbleOverride(org.telegram.ui.ActionBar.MessageDrawable d) {
        if (d == null) return;
        d.overrideColor = null;
        d.setOverrideRoundRadius(0);
        d.setOverrideBorder(0, 0);
    }

    public static void applyToChat(ChatActivity activity, long dialogId, View listView) {
        if (activity == null || listView == null) return;
        if (!OxAgentsBridge.isAgentDialog(dialogId) && !org.telegram.ox.OxGroupStore.isLocalGroup(dialogId)) return;
        Snapshot s = forDialog(dialogId);
        if (!MODE_CUSTOM.equals(s.mode)) return;
        try {
            int alpha = s.bubbleAlpha;
            if (STYLE_FROSTED.equals(s.bubbleStyle)) alpha = Math.min(alpha, 200);
            int in = withAlpha(s.inBubble, alpha);
            int out = withAlpha(s.outBubble, alpha);
            int radius = AndroidUtilities.dp(s.cornerRadius);
            applyBubbleOverride(Theme.chat_msgInDrawable, in, radius, s.borderColor, s.borderWidth);
            applyBubbleOverride(Theme.chat_msgInSelectedDrawable, in, radius, s.borderColor, s.borderWidth);
            applyBubbleOverride(Theme.chat_msgInMediaDrawable, in, radius, s.borderColor, s.borderWidth);
            applyBubbleOverride(Theme.chat_msgInMediaSelectedDrawable, in, radius, s.borderColor, s.borderWidth);
            applyBubbleOverride(Theme.chat_msgOutDrawable, out, radius, s.borderColor, s.borderWidth);
            applyBubbleOverride(Theme.chat_msgOutSelectedDrawable, out, radius, s.borderColor, s.borderWidth);
            applyBubbleOverride(Theme.chat_msgOutMediaDrawable, out, radius, s.borderColor, s.borderWidth);
            applyBubbleOverride(Theme.chat_msgOutMediaSelectedDrawable, out, radius, s.borderColor, s.borderWidth);
            android.content.res.Resources res = activity.getParentActivity() != null
                    ? activity.getParentActivity().getResources()
                    : ApplicationLoader.applicationContext.getResources();
            // Put the custom background in its own layer between Telegram's
            // wallpaper view and the message list. Overriding the list background
            // alone leaves the native wallpaper visible around the input bar.
            appliedRoot = activity.getFragmentView();
            listView.setBackground(makeBackground(s, res));
            if (activity.themeDelegate != null) {
                activityThemeDelegate = activity.themeDelegate;
                activity.themeDelegate.setOxTextColor(s.textColor == 0 ? null : s.textColor);
                activity.themeDelegate.setOxWallpaperDrawable(makeBackground(s, res));
            }
            if (activity.getParentActivity() != null && appliedWindow == null) {
                appliedWindow = activity.getParentActivity().getWindow();
                appliedStatusColor = appliedWindow.getStatusBarColor();
                appliedNavColor = appliedWindow.getNavigationBarColor();
                appliedUiFlags = appliedWindow.getDecorView().getSystemUiVisibility();
            }
            appliedDialogId = dialogId;
            appliedLineHeight = s.lineHeight > 0 ? s.lineHeight : 1f;
            final Snapshot fs = s;
            final android.content.res.Resources fres = res;
            applySystemBars(fs);
            // Telegram applies its wallpaper and bar colors after onResume; repeat
            // the chrome pass once so the custom background wins.
            AndroidUtilities.runOnUIThread(() -> {
                if (applied && appliedDialogId == dialogId) {
                    applySystemBars(fs);
                    if (activity.themeDelegate != null) {
                        activity.themeDelegate.setOxWallpaperDrawable(makeBackground(fs, fres));
                    }
                }
            }, 500);
            AndroidUtilities.runOnUIThread(() -> {
                if (applied && appliedDialogId == dialogId) {
                    listView.setBackground(makeBackground(fs, fres));
                }
            }, 1600);
            if (Theme.chat_msgTextPaint != null) {
                if (!applied) {
                    originalTextSize = Theme.chat_msgTextPaint.getTextSize();
                    originalTextColor = Theme.chat_msgTextPaint.getColor();
                    originalTypeface = Theme.chat_msgTextPaint.getTypeface();
                }
                Theme.chat_msgTextPaint.setTextSize(AndroidUtilities.dp(SharedConfig.fontSize * s.fontScale));
                if (s.textColor != 0) Theme.chat_msgTextPaint.setColor(s.textColor);
                Typeface tf = typefaceFrom(s.fontPath);
                if (tf != null) Theme.chat_msgTextPaint.setTypeface(tf);
            }
            if (!applied) {
                if (Theme.chat_msgTextCodePaint != null) {
                    originalCodeColor = Theme.chat_msgTextCodePaint.getColor();
                    originalCodeTypeface = Theme.chat_msgTextCodePaint.getTypeface();
                }
                if (Theme.chat_msgTextCode2Paint != null) {
                    originalCode2Color = Theme.chat_msgTextCode2Paint.getColor();
                    originalCode2Typeface = Theme.chat_msgTextCode2Paint.getTypeface();
                }
                if (Theme.chat_msgTextCode3Paint != null) {
                    originalCode3Color = Theme.chat_msgTextCode3Paint.getColor();
                    originalCode3Typeface = Theme.chat_msgTextCode3Paint.getTypeface();
                }
            }
            Typeface codeTf = typefaceFrom(s.codeFontPath);
            if (codeTf != null) {
                if (Theme.chat_msgTextCodePaint != null) Theme.chat_msgTextCodePaint.setTypeface(codeTf);
                if (Theme.chat_msgTextCode2Paint != null) Theme.chat_msgTextCode2Paint.setTypeface(codeTf);
                if (Theme.chat_msgTextCode3Paint != null) Theme.chat_msgTextCode3Paint.setTypeface(codeTf);
            }
            if (s.textColor != 0) {
                if (Theme.chat_msgTextCodePaint != null) Theme.chat_msgTextCodePaint.setColor(s.textColor);
                if (Theme.chat_msgTextCode2Paint != null) Theme.chat_msgTextCode2Paint.setColor(s.textColor);
                if (Theme.chat_msgTextCode3Paint != null) Theme.chat_msgTextCode3Paint.setColor(s.textColor);
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

    private static Typeface typefaceFrom(String path) {
        if (path == null || path.isEmpty()) return null;
        if ("serif".equals(path)) return Typeface.SERIF;
        if ("monospace".equals(path)) return Typeface.MONOSPACE;
        try {
            File f = new File(path);
            return f.isFile() ? Typeface.createFromFile(f) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void applySystemBars(Snapshot s) {
        if (appliedWindow == null) return;
        int bar = s.amoled ? 0xFF000000 : s.background;
        appliedWindow.setStatusBarColor(bar);
        appliedWindow.setNavigationBarColor(bar);
        int flags = appliedUiFlags;
        boolean light = luminance(bar) > 0.45;
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            if (light) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        appliedWindow.getDecorView().setSystemUiVisibility(flags);
    }

    public static void restoreChat(View listView) {
        if (!applied) return;
        try {
            clearBubbleOverride(Theme.chat_msgInDrawable);
            clearBubbleOverride(Theme.chat_msgInSelectedDrawable);
            clearBubbleOverride(Theme.chat_msgInMediaDrawable);
            clearBubbleOverride(Theme.chat_msgInMediaSelectedDrawable);
            clearBubbleOverride(Theme.chat_msgOutDrawable);
            clearBubbleOverride(Theme.chat_msgOutSelectedDrawable);
            clearBubbleOverride(Theme.chat_msgOutMediaDrawable);
            clearBubbleOverride(Theme.chat_msgOutMediaSelectedDrawable);
            if (Theme.chat_msgTextPaint != null) {
                if (originalTextSize > 0) Theme.chat_msgTextPaint.setTextSize(originalTextSize);
                Theme.chat_msgTextPaint.setColor(originalTextColor);
                Theme.chat_msgTextPaint.setTypeface(originalTypeface);
            }
            if (Theme.chat_msgTextCodePaint != null) {
                Theme.chat_msgTextCodePaint.setColor(originalCodeColor);
                Theme.chat_msgTextCodePaint.setTypeface(originalCodeTypeface);
            }
            if (Theme.chat_msgTextCode2Paint != null) {
                Theme.chat_msgTextCode2Paint.setColor(originalCode2Color);
                Theme.chat_msgTextCode2Paint.setTypeface(originalCode2Typeface);
            }
            if (Theme.chat_msgTextCode3Paint != null) {
                Theme.chat_msgTextCode3Paint.setColor(originalCode3Color);
                Theme.chat_msgTextCode3Paint.setTypeface(originalCode3Typeface);
            }
            if (listView != null) {
                appliedRoot = null;
                if (activityThemeDelegate != null) {
                    activityThemeDelegate.setOxTextColor(null);
                    activityThemeDelegate.clearOxWallpaperDrawable();
                    activityThemeDelegate = null;
                }
                if (appliedWindow != null) {
                    appliedWindow.setStatusBarColor(appliedStatusColor);
                    appliedWindow.setNavigationBarColor(appliedNavColor);
                    appliedWindow.getDecorView().setSystemUiVisibility(appliedUiFlags);
                    appliedWindow = null;
                }
                if (listView instanceof androidx.recyclerview.widget.RecyclerView) {
                    androidx.recyclerview.widget.RecyclerView.Adapter a = ((androidx.recyclerview.widget.RecyclerView) listView).getAdapter();
                    if (a != null) a.notifyDataSetChanged();
                }
                listView.invalidate();
            }
        } catch (Throwable ignored) {
        }
        applied = false;
        appliedLineHeight = 1f;
        appliedDialogId = 0;
    }

    /** Line-height multiplier for message text in the currently themed agent chat. */
    public static float lineHeightFor(long dialogId) {
        if (!applied || dialogId == 0 || dialogId != appliedDialogId) return 1f;
        return appliedLineHeight;
    }

    public static void setBackgroundImage(InputStream in) throws Exception {
        File f = new File(ApplicationLoader.applicationContext.getFilesDir(), BG_FILE);
        FileOutputStream out = new FileOutputStream(f);
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.close();
        in.close();
        setBackgroundImage(f.getAbsolutePath());
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.min(255, Math.max(0, alpha)) << 24);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static boolean has(JSONObject o, String... keys) {
        for (String k : keys) if (o.has(k)) return true;
        return false;
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

    private static boolean boolValue(JSONObject o, boolean def, String... keys) {
        for (String key : keys) {
            if (!o.has(key)) continue;
            Object v = o.opt(key);
            if (v instanceof Boolean) return (Boolean) v;
            if (v instanceof Number) return ((Number) v).intValue() != 0;
            String s = String.valueOf(v).trim().toLowerCase(java.util.Locale.ROOT);
            if ("true".equals(s) || "1".equals(s) || "on".equals(s) || "yes".equals(s)) return true;
            if ("false".equals(s) || "0".equals(s) || "off".equals(s) || "no".equals(s)) return false;
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
