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

    public static void setAgentOnly(boolean value) {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) return;
            SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            p.edit().putBoolean("agent_only", value).apply();
        } catch (Exception ignored) {
        }
    }
}
