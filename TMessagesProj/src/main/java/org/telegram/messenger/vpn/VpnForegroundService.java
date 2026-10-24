package org.telegram.messenger.vpn;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.ui.LaunchActivity;

/**
 * Keeps the app process - and with it the in-process sing-box proxy and tgnet's push connection -
 * resident while the VPN is on, so message updates arrive over the tunnel and notifications show
 * even with the UI closed.
 *
 * The fork can't receive Telegram's FCM push (push tokens are scoped to Telegram's Firebase
 * project and the fork is signed/packaged differently), so a kept-alive background connection is
 * the only way to get notifications when the app isn't open. Android kills a plain background
 * process (and the proxy dies with it), hence a real foreground service. It uses the specialUse
 * type to avoid the 6h/day cap the platform puts on dataSync services from Android 15 on.
 */
public class VpnForegroundService extends Service {

    private static final int NOTIFICATION_ID = 380266;

    public static void start() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            Intent intent = new Intent(ctx, VpnForegroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent);
            } else {
                ctx.startService(intent);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static void stop() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            ctx.stopService(new Intent(ctx, VpnForegroundService.class));
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private android.os.Handler beaconHandler;
    private final Runnable beacon = new Runnable() {
        @Override
        public void run() {
            try {
                if (VpnController.getInstance().isEnabled() && org.telegram.messenger.vpn.VpnKeepAlive.enabled()) {
                    new Thread(VpnForegroundService::socksBeacon, "ox-vpn-beacon").start();
                }
            } catch (Throwable ignored) {
            }
            if (beaconHandler != null) {
                beaconHandler.postDelayed(this, 60_000L);
            }
        }
    };

    /**
     * Open a connection through the local sing-box SOCKS5 inbound and close it right away.
     * A few packets each minute keeps the tunnel from idling out and gives the auto-switch
     * health check a liveness signal while the app is in the background.
     */
    private static void socksBeacon() {
        java.net.Socket socket = null;
        try {
            int port = VpnController.getInstance().getProxyPort();
            socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", port), 5_000);
            socket.setSoTimeout(5_000);
            java.io.OutputStream out = socket.getOutputStream();
            java.io.InputStream in = socket.getInputStream();
            out.write(new byte[]{5, 1, 0});              // greeting: no-auth
            out.flush();
            if (in.read() != 5) return;
            in.read();
            // CONNECT 1.1.1.1:443 (domain-free IPv4 form)
            out.write(new byte[]{5, 1, 0, 1, 1, 1, 1, 1, 0x01, (byte) 0xBB});
            out.flush();
            byte[] rep = new byte[10];
            int n = 0;
            while (n < rep.length) {
                int r = in.read(rep, n, rep.length - n);
                if (r < 0) break;
                n += r;
            }
        } catch (Throwable ignored) {
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (Throwable ignored) {}
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        beaconHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        beaconHandler.postDelayed(beacon, 15_000L);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            startForeground(NOTIFICATION_ID, buildNotification());
        } catch (Throwable e) {
            FileLog.e(e);
            // If we can't go foreground (e.g. missing notification permission on a weird OEM), don't
            // leave a zombie started service around.
            stopSelf();
        }
        // The OS can restart this sticky service on its own after killing the process; the
        // sing-box core died with the old process, so bring the tunnel back up if it was on.
        try {
            VpnController.getInstance().load();
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (beaconHandler != null) {
            beaconHandler.removeCallbacks(beacon);
            beaconHandler = null;
        }
        try {
            stopForeground(true);
            NotificationManagerCompat.from(ApplicationLoader.applicationContext).cancel(NOTIFICATION_ID);
        } catch (Throwable e) {
            FileLog.e(e);
        }
        super.onDestroy();
    }

    private Notification buildNotification() {
        Context ctx = ApplicationLoader.applicationContext;
        Intent openIntent = new Intent(ctx, LaunchActivity.class);
        openIntent.setAction("org.pelegram.openvpn");
        openIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(ctx, 0, openIntent, piFlags);

        NotificationsController.checkOtherNotificationsChannel();
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, NotificationsController.OTHER_NOTIFICATIONS_CHANNEL);
        b.setSmallIcon(R.drawable.notification);
        b.setContentTitle(LocaleController.getString(R.string.AppName));
        b.setContentText(LocaleController.getString(R.string.VpnForegroundActive));
        b.setContentIntent(contentIntent);
        b.setOngoing(true);
        b.setShowWhen(false);
        b.setPriority(NotificationCompat.PRIORITY_LOW);
        b.setCategory(NotificationCompat.CATEGORY_SERVICE);
        return b.build();
    }
}
