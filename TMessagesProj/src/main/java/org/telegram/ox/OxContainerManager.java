package org.telegram.ox;

import android.os.SystemClock;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Random;
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
        return shellPath() != null;
    }

    /** Some Android SELinux policies deny stat/read on symlinks inside app data,
     *  so check the real dash/bash binaries too instead of only bin/sh. */
    private static String shellPath() {
        File dir = rootfsDir();
        String[] candidates = {"/bin/sh", "/usr/bin/sh", "/bin/dash",
                "/usr/bin/dash", "/bin/bash", "/usr/bin/bash"};
        for (String rel : candidates) {
            if (new File(dir, rel).exists()) return rel;
        }
        return null;
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
            // A restored/copied rootfs is already usable; do not force a
            // download just because this process has not unpacked it itself.
            if (isRootfsReady() && isProotAvailable()) {
                state = STATE_READY;
                FileLog.d("OxContainer using existing rootfs shell=" + shellPath());
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
                    throw new IllegalStateException("rootfs unpacked but no shell binary");
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
            String shell = shellPath();
            if (shell == null) {
                throw new IllegalStateException("rootfs has no shell binary");
            }
            ProcessBuilder pb = new ProcessBuilder(
                    proot.getAbsolutePath(), "-r", dir.getAbsolutePath(), "-0",
                    shell, "-c",
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

    // ------------------------------------------------------------------
    // Persistent OxLinux shell: one proot process per app process. Commands
    // are written as a script into rootfs /tmp and executed by the same
    // /bin/sh, so shell state, cwd and background jobs survive between
    // calls. This replaces one-shot `proot ... -c` for harness compact.
    // ------------------------------------------------------------------

    private static final Object PERSISTENT_LOCK = new Object();
    private static PersistentShell persistentShell;

    public static String runPersistent(String command, long timeoutMs) {
        synchronized (PERSISTENT_LOCK) {
            try {
                String ensure = ensureInstalled();
                if (!"ready".equals(ensure)) {
                    return "container error: " + ensure;
                }
                if (persistentShell == null || !persistentShell.isAlive()) {
                    if (persistentShell != null) persistentShell.close();
                    persistentShell = new PersistentShell();
                }
                return persistentShell.run(command, timeoutMs);
            } catch (Throwable t) {
                FileLog.e("OxContainer", t);
                if (persistentShell != null) {
                    persistentShell.close();
                    persistentShell = null;
                }
                return "container error: "
                        + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
            }
        }
    }

    public static void resetPersistent() {
        synchronized (PERSISTENT_LOCK) {
            if (persistentShell != null) {
                persistentShell.close();
                persistentShell = null;
            }
        }
    }

    private static final class PersistentShell {
        private final Process process;
        private final BufferedInputStream in;
        private final BufferedOutputStream out;
        private final Object lock = new Object();
        private final StringBuilder buffer = new StringBuilder();
        private final Random random = new Random();
        private volatile boolean alive = true;

        PersistentShell() throws Exception {
            File proot = prootBinary();
            File dir = rootfsDir();
            String shell = shellPath();
            if (shell == null) {
                throw new IllegalStateException("rootfs has no shell binary");
            }
            ProcessBuilder pb = new ProcessBuilder(
                    proot.getAbsolutePath(), "-r", dir.getAbsolutePath(), "-0", shell);
            pb.redirectErrorStream(true);
            File tmp = new File(filesDir(), "proot-tmp");
            tmp.mkdirs();
            pb.environment().put("PROOT_TMP_DIR", tmp.getAbsolutePath());
            pb.environment().put("PROOT_NO_SECCOMP", "1");
            pb.environment().put("LD_LIBRARY_PATH",
                    ApplicationLoader.applicationContext.getApplicationInfo().nativeLibraryDir);
            pb.environment().put("HOME", "/root");
            pb.environment().put("PATH",
                    "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
            pb.environment().put("TERM", "xterm");
            process = pb.start();
            in = new BufferedInputStream(process.getInputStream());
            out = new BufferedOutputStream(process.getOutputStream());
            Thread reader = new Thread(this::readLoop, "ox-container-shell");
            reader.setDaemon(true);
            reader.start();
            File rootTmp = new File(dir, "tmp");
            rootTmp.mkdirs();
        }

        boolean isAlive() {
            return alive && process.isAlive();
        }

        private void readLoop() {
            byte[] buf = new byte[8192];
            try {
                int n;
                while ((n = in.read(buf)) > 0) {
                    synchronized (lock) {
                        buffer.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                        if (buffer.length() > 131072) {
                            buffer.delete(0, buffer.length() - 65536);
                        }
                        lock.notifyAll();
                    }
                }
            } catch (Throwable t) {
                FileLog.e("OxContainer shell read", t);
            } finally {
                alive = false;
                synchronized (lock) {
                    lock.notifyAll();
                }
            }
        }

        String run(String command, long timeoutMs) throws Exception {
            if (!isAlive()) return "container error: persistent shell is not running";
            String nonce = Long.toHexString(random.nextLong());
            String marker = "__OX_DONE_" + nonce + "__";
            File rootfs = rootfsDir();
            File script = new File(new File(rootfs, "tmp"), "ox-persistent-" + nonce + ".sh");
            try (FileOutputStream fos = new FileOutputStream(script)) {
                String body = "#!/bin/sh\n" + (command == null ? "" : command) + "\n";
                fos.write(body.getBytes(StandardCharsets.UTF_8));
            }
            try {
                synchronized (lock) {
                    buffer.setLength(0);
                    String header = ". /tmp/" + script.getName() + "\n"
                            + "printf '\\n" + marker + "%d\\n' $?\n";
                    out.write(header.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    long deadline = SystemClock.elapsedRealtime() + Math.max(1000, timeoutMs);
                    while (true) {
                        int idx = buffer.indexOf(marker);
                        if (idx >= 0) {
                            String output = buffer.substring(0, idx);
                            int lineEnd = buffer.indexOf("\n", idx + marker.length());
                            if (lineEnd < 0) {
                                long remain = deadline - SystemClock.elapsedRealtime();
                                if (remain <= 0) {
                                    close();
                                    return output.trim() + "\n(timeout)";
                                }
                                lock.wait(Math.min(remain, 100));
                                continue;
                            }
                            String exitText = buffer.substring(idx + marker.length(), lineEnd).trim();
                            buffer.delete(0, lineEnd + 1);
                            int exit = -999;
                            try { exit = Integer.parseInt(exitText); } catch (Throwable ignore) {}
                            String text = output.trim();
                            return text.isEmpty() ? "(no output, exit=" + exit + ")" : text;
                        }
                        if (!process.isAlive()) {
                            String text = buffer.toString().trim();
                            return (text.isEmpty() ? "(no output)" : text)
                                    + "\n(session exited)";
                        }
                        long remain = deadline - SystemClock.elapsedRealtime();
                        if (remain <= 0) {
                            close();
                            String text = buffer.toString().trim();
                            return (text.isEmpty() ? "(no output)" : text) + "\n(timeout)";
                        }
                        lock.wait(Math.min(remain, 200));
                    }
                }
            } finally {
                script.delete();
            }
        }

        void close() {
            alive = false;
            try { out.close(); } catch (Throwable ignore) {}
            try { in.close(); } catch (Throwable ignore) {}
            process.destroy();
        }
    }
}
