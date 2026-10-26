package org.telegram.ox;

import android.content.SharedPreferences;

import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;

/**
 * Optional Ox accent imported from Kelivo/RikkaHub-style theme JSON. It intentionally
 * only drives Ox-owned surfaces; Telegram's own theme stays untouched.
 */
public class OxTheme {

    private static final String PREFS = "ox_theme";

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }

    public static boolean hasCustom() {
        return prefs().getInt("primary", 0) != 0;
    }

    public static int primary() {
        return prefs().getInt("primary", 0);
    }

    public static int secondary() {
        return prefs().getInt("secondary", 0);
    }

    public static int tertiary() {
        return prefs().getInt("tertiary", 0);
    }

    public static String name() {
        return prefs().getString("name", "");
    }

    public static int accent() {
        return opaque(primary());
    }

    public static int accentDark() {
        return darken(accent(), 0.82f);
    }

    public static int darkenForUi(int color) {
        return darken(color, 0.82f);
    }

    public static int accentSoft(int alpha) {
        int c = accent();
        return (c & 0x00FFFFFF) | (alpha << 24);
    }

    public static String hex() {
        return hasCustom() ? String.format("#%06X", primary() & 0xFFFFFF) : "";
    }

    public static void applyPreset(int argb, String name) {
        save(argb, 0, 0, name);
    }

    public static void clear() {
        prefs().edit().clear().apply();
    }

    public static void importJson(String raw) throws Exception {
        JSONObject o = new JSONObject(raw);
        int primary = color(o, "primaryColorArgb", "primary_color_argb", "primaryColor");
        if (primary == 0) {
            throw new IllegalArgumentException("primaryColorArgb missing");
        }
        int secondary = color(o, "secondaryColorArgb", "secondary_color_argb", "secondaryColor");
        int tertiary = color(o, "tertiaryColorArgb", "tertiary_color_argb", "tertiaryColor");
        String name = o.optString("name", "").trim();
        if (name.isEmpty()) name = "导入主题";
        save(primary, secondary, tertiary, name);
    }

    private static void save(int primary, int secondary, int tertiary, String name) {
        prefs().edit()
                .putInt("primary", primary)
                .putInt("secondary", secondary)
                .putInt("tertiary", tertiary)
                .putString("name", name == null ? "" : name)
                .apply();
    }

    private static int color(JSONObject o, String... keys) {
        for (String key : keys) {
            if (!o.has(key)) continue;
            Object v = o.opt(key);
            try {
                if (v instanceof Number) {
                    return opaque(((Number) v).longValue());
                }
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

    private static int opaque(long value) {
        int c = (int) value;
        if ((c & 0xFF000000) == 0) c |= 0xFF000000;
        return c;
    }

    private static int darken(int color, float factor) {
        int a = color >>> 24;
        int r = Math.round(((color >> 16) & 0xFF) * factor);
        int g = Math.round(((color >> 8) & 0xFF) * factor);
        int b = Math.round((color & 0xFF) * factor);
        return (a << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
