package org.telegram.ox;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

// Agent-only mode: use OrionekoX without a Telegram account. The chat/dialog system is
// skipped entirely; the app home becomes the local/cloud agent list. Telegram tg_* tools
// are hidden from agents while no account is signed in (they would only error out).
public class OxMode {

    private static final String PREFS = "orioneko_config";

    public static final int MODE_UNSET = -1;
    public static final int MODE_OX = 0;
    public static final int MODE_LOGIN = 1;
    public static final String IFACE_TELEGRAM = "telegram";
    public static final String IFACE_OXUI = "oxui";

    public static final int PENDING_NONE = 0;
    public static final int PENDING_LOGIN = 1;
    public static final int PENDING_OX = 2;

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

    /** Local "premium": unlocks Telegram's premium UI locally, with no subscription or server call. */
    public static boolean isLocalPremium() {
        try {
            return prefs().getBoolean("local_premium", false);
        } catch (Exception e) {
            return false;
        }
    }

    public static void setLocalPremium(boolean value) {
        try {
            prefs().edit().putBoolean("local_premium", value).apply();
        } catch (Exception ignored) {
        }
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

    /** Mode selected on the onboarding "start mode" step. */
    public static int getOnboardingMode() {
        return prefs().getInt("onboarding_mode", MODE_UNSET);
    }

    public static void setOnboardingMode(int value) {
        prefs().edit().putInt("onboarding_mode", value).apply();
    }

    /** telegram | oxui. OxUI is a preview front end; Telegram UI remains the fallback. */
    public static String getInterfaceMode() {
        try {
            String v = prefs().getString("interface_mode", IFACE_TELEGRAM);
            return IFACE_OXUI.equals(v) ? IFACE_OXUI : IFACE_TELEGRAM;
        } catch (Exception e) {
            return IFACE_TELEGRAM;
        }
    }

    public static void setInterfaceMode(String value) {
        prefs().edit().putString("interface_mode",
                IFACE_OXUI.equals(value) ? IFACE_OXUI : IFACE_TELEGRAM).apply();
    }

    public static boolean isLegalAccepted() {
        return prefs().getBoolean("legal_accepted", false);
    }

    public static void setLegalAccepted(boolean value) {
        prefs().edit().putBoolean("legal_accepted", value).apply();
    }

    public static boolean isContainerEnabled() {
        return prefs().getBoolean("container_enabled", false);
    }

    public static void setContainerEnabled(boolean value) {
        prefs().edit().putBoolean("container_enabled", value).apply();
    }

    /**
     * A logout is asynchronous: LaunchActivity consumes this right after appDidLogout and
     * replaces the intro screen with the requested root (LoginActivity or local MainTabs).
     */
    public static int getPendingMode() {
        return prefs().getInt("pending_mode", PENDING_NONE);
    }

    public static void setPendingMode(int value) {
        prefs().edit().putInt("pending_mode", value).apply();
    }

    public static int consumePendingMode() {
        int v = getPendingMode();
        if (v != PENDING_NONE) {
            prefs().edit().putInt("pending_mode", PENDING_NONE).apply();
        }
        return v;
    }
}
