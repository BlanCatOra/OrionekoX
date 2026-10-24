package org.telegram.messenger.vpn;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;

import org.telegram.messenger.ApplicationLoader;

/**
 * OrionekoX VPN background keep-alive: a user preference that bundles
 *  - battery-optimization exemption (so OEM killers leave the tunnel alone), and
 *  - a periodic SOCKS5 beacon from VpnForegroundService (keeps the tunnel hot through
 *    idle-suspend and gives the auto-switch a liveness signal).
 */
public final class VpnKeepAlive {

    private static final String PREFS = "orioneko_vpn";

    private VpnKeepAlive() {}

    public static boolean enabled() {
        return ApplicationLoader.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("keep_alive", true);
    }

    public static void setEnabled(boolean value) {
        ApplicationLoader.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("keep_alive", value).commit();
    }

    public static boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        try {
            PowerManager pm = (PowerManager) ApplicationLoader.applicationContext
                .getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(
                ApplicationLoader.applicationContext.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    /** Fire the system dialog asking to exempt this app from battery optimizations. */
    public static void requestIgnoreBatteryOptimizations(Activity activity) {
        try {
            if (isIgnoringBatteryOptimizations()) return;
            Intent intent = new Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
            activity.startActivity(intent);
        } catch (Throwable ignored) {
        }
    }
}
