package org.telegram.ox;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

// Agent-only mode: use OrionekoX without a Telegram account. The chat/dialog system is
// skipped entirely; the app home becomes the local/cloud agent list. Telegram tg_* tools
// are hidden from agents while no account is signed in (they would only error out).
public class OxMode {

    private static final String PREFS = "orioneko_config";

    public static boolean isAgentOnly() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) return false;
            SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            return p.getBoolean("agent_only", false);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isOnboardingDone() {
        return prefs().getBoolean("onboarding_done", false);
    }

    public static void setOnboardingDone(boolean value) {
        prefs().edit().putBoolean("onboarding_done", value).apply();
    }

    public static String getContainerMirror() {
        return prefs().getString("container_mirror", "official");
    }

    public static void setContainerMirror(String v) {
        prefs().edit().putString("container_mirror", v).apply();
    }

    public static String getContainerVersion() {
        return prefs().getString("container_version", "22.04");
    }

    public static void setContainerVersion(String v) {
        prefs().edit().putString("container_version", v).apply();
    }

    public static String getContainerComponents() {
        return prefs().getString("container_components", "nodejs,python3,git,curl");
    }

    public static void setContainerComponents(String csv) {
        prefs().edit().putString("container_components", csv).apply();
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void setAgentOnly(boolean value) {
        try {
            prefs().edit().putBoolean("agent_only", value).apply();
        } catch (Exception ignored) {
        }
    }
}
