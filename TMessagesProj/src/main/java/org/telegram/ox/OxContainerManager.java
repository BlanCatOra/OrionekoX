package org.telegram.ox;

import android.os.SystemClock;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * M4 PoC: a PRoot-backed Ubuntu userland inside the app sandbox.
 * - libproot.so ships in jniLibs (extracted to nativeLibraryDir by the system, W^X-safe)
 * - the Ubuntu base rootfs is downloaded on first use and unpacked with toybox tar
 * - commands run via: proot -r rootfs -0 /bin/sh -c "..."
 */
public class OxContainerManager {

    private static final String ROOTFS_URL =
            "http://cdimage.ubuntu.com/ubuntu-base/releases/22.04/release/ubuntu-base-22.04.5-base-amd64.tar.gz";
    private static final String ROOTFS_URL_ARM64 =
            "http://cdimage.ubuntu.com/ubuntu-base/releases/22.04/release/ubuntu-base-22.04.5-base-arm64.tar.gz";

    public static final int STATE_NONE = 0;
    public static final int STATE_INSTALLING = 1;
    public static final int STATE_READY = 2;
    public static final int STATE_ERROR = 3;

    private static volatile int state = STATE_NONE;   // none / installing / ready / error

    private static volatile String errorText;
    private static final Object INSTALL_LOCK = new Object();

    public static int getState() {
        return state;
    }

    public static String getErrorText() {
        return errorText;
    }

    private static File filesDir() {
        return ApplicationLoader.applicationContext.getFilesDir();
    }

    private static File rootfsDir() {
        return new File(filesDir(), "ubuntu-rootfs");
    }

    private static File prootBinary() {
        // nativeLibraryDir carries libproot.so extracted from jniLibs
        String dir = ApplicationLoader.applicationContext.getApplicationInfo().nativeLibraryDir;
        return new File(dir, "libproot.so");
    }

    public static boolean isProotAvailable() {
        File p = prootBinary();
        return p.exists() && p.canExecute();
    }

    public static boolean isRootfsReady() {
        return new File(rootfsDir(), "bin/sh").exists();
    }

    /** Diagnostics: file counts + key binaries in the extracted rootfs (no proot involved). */
    public static String rootfsDiagnostics() {
        try {
            File dir = rootfsDir();
            File bin = new File(dir, "bin");
            File usrbin = new File(dir, "usr/bin");
            File dash = new File(dir, "usr/bin/dash");
            File sh = new File(dir, "bin/sh");
            int binCount = bin.exists() ? (bin.list() == null ? -1 : bin.list().length) : -1;
            int usrbinCount = usrbin.exists() ? (usrbin.list() == null ? -1 : usrbin.list().length) : -1;
            File lib64 = new File(dir, "lib64");
            File lib = new File(dir, "lib");
            File ld64 = new File(dir, "usr/lib64/ld-linux-x86-64.so.2");
            File ldReal = new File(dir, "usr/lib/x86_64-linux-gnu/ld-linux-x86-64.so.2");
            String lib64Target = "";
            try { lib64Target = java.nio.file.Files.readSymbolicLink(lib64.toPath()).toString(); } catch (Throwable ignore) {}
            String ld64Target = "";
            try { ld64Target = java.nio.file.Files.readSymbolicLink(ld64.toPath()).toString(); } catch (Throwable ignore) {}
            return "binEntries=" + binCount + " usrBinEntries=" + usrbinCount
                    + " dash=" + dash.exists() + " dashLen=" + (dash.exists() ? dash.length() : 0)
                    + " shSymlink=" + sh.exists()
                    + " lib64=" + lib64.exists() + "(->" + lib64Target + ")"
                    + " lib=" + lib.exists()
                    + " ld64=" + ld64.exists() + "(->" + ld64Target + ")"
                    + " ldReal=" + ldReal.exists() + " len=" + (ldReal.exists() ? ldReal.length() : 0);
        } catch (Throwable t) {
            return "diag error: " + t;
        }
    }

    /** Blocking install (download + unpack). Safe to call from a worker thread repeatedly. */
    public static String ensureInstalled() {
        synchronized (INSTALL_LOCK) {
            if (state == STATE_READY && isRootfsReady()) {
                return "ready";
            }
            state = STATE_INSTALLING;
            errorText = null;
            try {
                if (!isProotAvailable()) {
                    throw new IllegalStateException("libproot.so missing from nativeLibraryDir");
                }
                File tarFile = new File(filesDir(), "ubuntu-base.tar.gz");
                if (!tarFile.exists() || tarFile.length() < 1_000_000) {
                    String url = is64BitEmu() ? ROOTFS_URL : ROOTFS_URL_ARM64;
                    download(url, tarFile);
                }
                File dir = rootfsDir();
                dir.mkdirs();
                Process p = Runtime.getRuntime().exec(new String[]{
                        "/system/bin/tar", "-xzf", tarFile.getAbsolutePath(), "-C", dir.getAbsolutePath()});
                int code = p.waitFor();
                // exit 1 = non-fatal warnings (hardlinks unsupported on some f2fs layouts)
                if (code != 0 && code != 1) {
                    throw new IllegalStateException("tar exit " + code);
                }
                tarFile.delete();
                if (!isRootfsReady()) {
                    throw new IllegalStateException("rootfs unpacked but /bin/sh missing");
                }
                state = STATE_READY;
                return "ready";
            } catch (Throwable t) {
                FileLog.e("OxContainer", t);
                errorText = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                state = STATE_ERROR;
                return "error: " + errorText;
            }
        }
    }

    private static boolean is64BitEmu() {
        String abi = android.os.Build.SUPPORTED_ABIS != null && android.os.Build.SUPPORTED_ABIS.length > 0
                ? android.os.Build.SUPPORTED_ABIS[0] : "";
        return abi.contains("x86");
    }

    private static void download(String url, File dst) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(20_000);
            conn.setReadTimeout(60_000);
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new IllegalStateException("HTTP " + code + " for rootfs");
            }
            try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[32768];
                int n;
                long total = 0;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    total += n;
                }
                FileLog.d("OxContainer rootfs downloaded bytes=" + total);
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Runs a shell command inside the container. Blocking; call from a worker thread. */
    public static String run(String command, long timeoutMs) {
        try {
            String ensure = ensureInstalled();
            if (!"ready".equals(ensure)) {
                return "container error: " + ensure;
            }
            File proot = prootBinary();
            File dir = rootfsDir();
            ProcessBuilder pb = new ProcessBuilder(
                    proot.getAbsolutePath(), "-r", dir.getAbsolutePath(), "-0",
                    "/bin/sh", "-c",
                    "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; " + command);
            pb.redirectErrorStream(true);
            File tmp = new File(filesDir(), "proot-tmp");
            tmp.mkdirs();
            pb.environment().put("PROOT_TMP_DIR", tmp.getAbsolutePath());
            pb.environment().put("PROOT_NO_SECCOMP", "1");
            // termux-built proot is dynamically linked against its deps in nativeLibraryDir
            pb.environment().put("LD_LIBRARY_PATH",
                    ApplicationLoader.applicationContext.getApplicationInfo().nativeLibraryDir);
            pb.environment().put("HOME", "/root");
            pb.environment().put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
            pb.environment().put("TERM", "xterm");
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (InputStream in = p.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                long deadline = SystemClock.elapsedRealtime() + timeoutMs;
                while ((n = in.read(buf)) > 0) {
                    out.append(new String(buf, 0, n));
                    if (out.length() > 16000) break;
                    if (SystemClock.elapsedRealtime() > deadline) break;
                }
            }
            boolean finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroy();
                return out + "\n(timeout)";
            }
            String text = out.toString().trim();
            int exit = -999;
            try { exit = p.exitValue(); } catch (Throwable ignore) {}
            FileLog.d("OxContainer run exit=" + exit + " outLen=" + out.length() + " cmd=" + command);
            return text.isEmpty() ? "(no output, exit=" + exit + ")" : text;
        } catch (Throwable t) {
            FileLog.e("OxContainer", t);
            return "container error: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
        }
    }
}
