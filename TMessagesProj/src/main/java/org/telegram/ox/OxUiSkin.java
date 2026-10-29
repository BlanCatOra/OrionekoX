package org.telegram.ox;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * OxUI skin system: named presets distilled from the multi-model design battle,
 * plus a fully custom token set. Every OxUI surface reads its colors, corner
 * radius and type scale from here, so one skin restyles the whole app.
 */
public class OxUiSkin {

    public String id;
    public String name;
    public int bone;
    public int paper;
    public int ink;
    public int muted;
    public int signal;
    public int clay;
    public int coral;
    public int menuPaper;
    public float radiusScale = 1f;
    public float typeScale = 1f;

    // Shell layer: navigation form, surface material and component toggles.
    public String navStyle = "rail";      // rail | bottom | floating
    public String navMaterial = "solid";  // solid | acrylic | mica | glass
    public boolean showRail = true;
    public boolean showFooter = true;
    public float termScale = 1f;
    public String termTheme = "dark";     // dark | black | solarized
    public boolean termQuickKeys = true;
    public boolean termAutocomplete = true;
    public String dockPosition = "aboveInput"; // top | aboveInput | bottom

    // Component layer: density, element visibility and dock composition.
    public String density = "cozy";      // cozy | compact
    public boolean showTimestamps = true;
    public boolean showAccent = true;
    public int dockItems = 15;           // bit0 agents, 1 search, 2 terminal, 3 settings

    public boolean isDark() {
        int lum = (int) (0.299 * ((bone >> 16) & 0xFF) + 0.587 * ((bone >> 8) & 0xFF)
                + 0.114 * (bone & 0xFF));
        return lum < 110;
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name);
            o.put("bone", bone);
            o.put("paper", paper);
            o.put("ink", ink);
            o.put("muted", muted);
            o.put("signal", signal);
            o.put("clay", clay);
            o.put("coral", coral);
            o.put("menuPaper", menuPaper);
            o.put("radiusScale", radiusScale);
            o.put("typeScale", typeScale);
            o.put("navStyle", navStyle);
            o.put("navMaterial", navMaterial);
            o.put("showRail", showRail);
            o.put("showFooter", showFooter);
            o.put("termScale", termScale);
            o.put("termTheme", termTheme);
            o.put("termQuickKeys", termQuickKeys);
            o.put("termAutocomplete", termAutocomplete);
            o.put("dockPosition", dockPosition);
            o.put("density", density);
            o.put("showTimestamps", showTimestamps);
            o.put("showAccent", showAccent);
            o.put("dockItems", dockItems);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static OxUiSkin fromJson(JSONObject o) {
        OxUiSkin s = new OxUiSkin();
        s.id = o.optString("id", "custom");
        s.name = o.optString("name", "Custom");
        s.bone = o.optInt("bone", 0xFFF7F3E8);
        s.paper = o.optInt("paper", 0xFFECE6D6);
        s.ink = o.optInt("ink", 0xFF0E2A24);
        s.muted = o.optInt("muted", 0xFF56655E);
        s.signal = o.optInt("signal", 0xFFC8EF6A);
        s.clay = o.optInt("clay", 0xFFD97757);
        s.coral = o.optInt("coral", 0xFFCE6549);
        s.menuPaper = o.optInt("menuPaper", 0xFFFBF8EF);
        s.radiusScale = (float) o.optDouble("radiusScale", 1);
        s.typeScale = (float) o.optDouble("typeScale", 1);
        s.navStyle = o.optString("navStyle", "rail");
        s.navMaterial = o.optString("navMaterial", "solid");
        s.showRail = o.optBoolean("showRail", true);
        s.showFooter = o.optBoolean("showFooter", true);
        s.termScale = (float) o.optDouble("termScale", 1);
        s.termTheme = o.optString("termTheme", "dark");
        s.termQuickKeys = o.optBoolean("termQuickKeys", true);
        s.termAutocomplete = o.optBoolean("termAutocomplete", true);
        s.dockPosition = o.optString("dockPosition", "aboveInput");
        s.density = o.optString("density", "cozy");
        s.showTimestamps = o.optBoolean("showTimestamps", true);
        s.showAccent = o.optBoolean("showAccent", true);
        s.dockItems = o.optInt("dockItems", 15);
        return s;
    }

    private static OxUiSkin skin(String id, String name, int bone, int paper, int ink,
            int muted, int signal, int clay, int coral, int menuPaper) {
        OxUiSkin s = new OxUiSkin();
        s.id = id;
        s.name = name;
        s.bone = bone;
        s.paper = paper;
        s.ink = ink;
        s.muted = muted;
        s.signal = signal;
        s.clay = clay;
        s.coral = coral;
        s.menuPaper = menuPaper;
        return s;
    }

    /** Built-in presets: the default plus six directions from the design battle
     *  and one high-contrast reading variant. */
    public static List<OxUiSkin> presets() {
        List<OxUiSkin> out = new ArrayList<>();
        out.add(skin("qingmo", "青墨 · 骨纸荧光", 0xFFF7F3E8, 0xFFECE6D6, 0xFF0E2A24,
                0xFF56655E, 0xFFC8EF6A, 0xFFD97757, 0xFFCE6549, 0xFFFBF8EF));
        out.add(skin("nuanzhi", "暖纸 · 陶土橙", 0xFFF8FAF6, 0xFFEFE8DA, 0xFF1F1D19,
                0xFF5D5748, 0xFFED713F, 0xFFC96A4A, 0xFFC0452F, 0xFFFFFDF8));
        out.add(skin("shuwei", "鼠尾草 · 静室", 0xFFF3F1E6, 0xFFE7E4D5, 0xFF2A3128,
                0xFF6C7466, 0xFF8FAE7E, 0xFFB98663, 0xFFC2745B, 0xFFFBF9F0));
        out.add(skin("yesek", "夜色 · 荧光", 0xFF0B0C0B, 0xFF151815, 0xFFEDE9DC,
                0xFF8A9287, 0xFFC8EF6A, 0xFFE08963, 0xFFE07A62, 0xFF121512));
        out.add(skin("gongzuotai", "工作台 · 朱红", 0xFFEFEDE4, 0xFFE4E0D2, 0xFF24211B,
                0xFF6B6558, 0xFFD3441C, 0xFFB9773F, 0xFFB23A22, 0xFFF8F5EC));
        out.add(skin("naiyou", "奶油 · 深青", 0xFFF8FAE6, 0xFFEDEFD8, 0xFF022829,
                0xFF5A6A62, 0xFF176F6B, 0xFFC77E52, 0xFFB85C3E, 0xFFFDFDF2));
        out.add(skin("fangge", "方格纸 · 蓝", 0xFFF2F4F2, 0xFFE6E9E6, 0xFF16211C,
                0xFF5D6A62, 0xFF246BFD, 0xFFC77E52, 0xFFC0452F, 0xFFFAFCFA));
        out.add(skin("yuedu", "阅读 · 高对比", 0xFFFBF9F1, 0xFFF0EBDD, 0xFF111111,
                0xFF4A4A45, 0xFF7A8F2A, 0xFFA85B3B, 0xFFB23A22, 0xFFFFFFFF));
        out.add(skin("molan", "墨蓝 · 夜航", 0xFF0E1418, 0xFF171F24, 0xFFE7ECEF,
                0xFF8B98A0, 0xFFC8EF6A, 0xFFDE8A66, 0xFFE07A62, 0xFF12181C));
        return out;
    }

    public static OxUiSkin byId(String id) {
        for (OxUiSkin s : presets()) {
            if (s.id.equals(id)) return s;
        }
        return presets().get(0);
    }

    private static SharedPreferences prefs() {
        Context ctx = ApplicationLoader.applicationContext;
        return ctx.getSharedPreferences("orioneko_config", Context.MODE_PRIVATE);
    }

    public static boolean hasExplicitChoice() {
        try {
            return prefs().contains("oxui_skin_id");
        } catch (Throwable t) {
            return false;
        }
    }

    public static void clearChoice() {
        try {
            prefs().edit().remove("oxui_skin_id").apply();
        } catch (Throwable ignored) {
        }
    }

    /** Active skin: a built-in preset or the persisted custom token set. */
    public static OxUiSkin active() {
        try {
            SharedPreferences p = prefs();
            String id = p.getString("oxui_skin_id", "qingmo");
            if ("custom".equals(id)) {
                String raw = p.getString("oxui_skin_custom", "");
                if (!raw.isEmpty()) return fromJson(new JSONObject(raw));
            }
            return byId(id);
        } catch (Throwable t) {
            return presets().get(0);
        }
    }

    public static void apply(OxUiSkin s) {
        try {
            prefs().edit().putString("oxui_skin_id", s.id)
                    .putString("oxui_skin_custom", s.toJson().toString()).apply();
        } catch (Throwable ignored) {
        }
    }
}
