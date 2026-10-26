package org.telegram.ox;

import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * .oxagent export/import. Agents without workspace files keep the old plain-JSON
 * format; agents with workspace files are written as a zip containing agent.json
 * and workspace/... entries. Both formats are accepted on import.
 */
public class OxAgentArchive {

    private static final String TAG = "OxAgentArchive";
    private static final String ENTRY_AGENT = "agent.json";
    private static final String ENTRY_WORKSPACE = "workspace/";
    private static final int MAX_FILES = 200;
    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 32L * 1024 * 1024;

    public static final class ReadResult {
        public JSONObject agent;
        public final LinkedHashMap<String, byte[]> workspace = new LinkedHashMap<>();
        public String avatarName;
        public byte[] avatar;
    }

    /**
     * Writes the agent to {@code os}. Returns the number of workspace files included;
     * when there are none the stream contains plain JSON (legacy-compatible).
     */
    public static int write(OutputStream os, OxAgent agent, String memory) throws Exception {
        JSONObject o = agent.toJson();
        o.put("format", "oxagent");
        o.put("api_key", "");
        o.put("built_in", false);
        o.put("memory", memory == null ? "" : memory);

        List<File> files = new ArrayList<>();
        File base = OxAgentStore.workspaceDir(agent.id);
        collect(base, base, files);

        File avatar = null;
        String avatarEntry = null;
        if (agent.avatarPath != null && !agent.avatarPath.isEmpty()) {
            File f = new File(agent.avatarPath);
            if (f.isFile() && f.length() > 0 && f.length() <= MAX_FILE_BYTES) {
                avatar = f;
                avatarEntry = "avatar" + extension(f.getName());
                o.put("avatar_name", avatarEntry);
                o.put("avatar_path", "");
            }
        }

        if (files.isEmpty() && avatar == null) {
            os.write(o.toString().getBytes(StandardCharsets.UTF_8));
            return 0;
        }

        ZipOutputStream zos = new ZipOutputStream(os);
        zos.putNextEntry(new ZipEntry(ENTRY_AGENT));
        zos.write(o.toString().getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();

        long total = 0;
        if (avatar != null && avatarEntry != null) {
            zos.putNextEntry(new ZipEntry(avatarEntry));
            copy(avatar, zos);
            zos.closeEntry();
            total += avatar.length();
        }

        int included = 0;
        for (File f : files) {
            if (included >= MAX_FILES || total >= MAX_TOTAL_BYTES) break;
            String rel = relative(base, f);
            if (rel == null) continue;
            if (f.length() <= 0 || f.length() > MAX_FILE_BYTES) continue;
            if (total + f.length() > MAX_TOTAL_BYTES) break;
            zos.putNextEntry(new ZipEntry(ENTRY_WORKSPACE + rel));
            copy(f, zos);
            zos.closeEntry();
            total += f.length();
            included++;
        }
        zos.finish();
        zos.flush();
        return included;
    }

    /** Reads either a legacy JSON file or a zip written by {@link #write}. */
    public static ReadResult read(byte[] data) throws Exception {
        if (data == null || data.length == 0) throw new IOException("empty file");
        ReadResult r = new ReadResult();
        if (isZip(data)) {
            ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data));
            long total = 0;
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = e.getName();
                byte[] body = readEntry(zis, MAX_TOTAL_BYTES - total);
                total += body.length;
                if (total > MAX_TOTAL_BYTES) throw new IOException("archive too large");
                if (ENTRY_AGENT.equals(name)) {
                    r.agent = new JSONObject(new String(body, StandardCharsets.UTF_8));
                } else if (name.startsWith(ENTRY_WORKSPACE)) {
                    String rel = name.substring(ENTRY_WORKSPACE.length());
                    rel = sanitizeRelative(rel);
                    if (rel == null) continue;
                    if (r.workspace.size() >= MAX_FILES) throw new IOException("too many files");
                    r.workspace.put(rel, body);
                } else if (name.startsWith("avatar.") && !name.contains("/")) {
                    r.avatarName = name;
                    r.avatar = body;
                }
            }
            zis.close();
            if (r.agent == null) throw new IOException("agent.json missing");
        } else {
            r.agent = new JSONObject(new String(data, StandardCharsets.UTF_8));
        }
        if (r.agent.optString("name", "").trim().isEmpty()) {
            throw new IOException("not a .oxagent file");
        }
        return r;
    }

    public static void restoreWorkspace(String agentId, ReadResult r) {
        if (r == null) return;
        for (java.util.Map.Entry<String, byte[]> e : r.workspace.entrySet()) {
            try {
                File target = OxAgentStore.resolveWorkspace(agentId, e.getKey());
                File parent = target.getParentFile();
                if (parent != null) parent.mkdirs();
                java.io.FileOutputStream out = new java.io.FileOutputStream(target);
                out.write(e.getValue());
                out.close();
            } catch (Throwable t) {
                FileLog.e(TAG + " restore failed " + e.getKey(), t);
            }
        }
    }

    /** Writes the archived avatar into the new agent directory; null when absent. */
    public static File restoreAvatar(String agentId, ReadResult r) {
        if (r == null || r.avatar == null || r.avatarName == null) return null;
        try {
            File dir = OxAgentStore.memoryFile(agentId).getParentFile();
            if (dir == null) return null;
            dir.mkdirs();
            File target = new File(dir, "avatar" + extension(r.avatarName));
            java.io.FileOutputStream out = new java.io.FileOutputStream(target);
            out.write(r.avatar);
            out.close();
            return target;
        } catch (Throwable t) {
            FileLog.e(TAG + " avatar restore failed", t);
            return null;
        }
    }

    private static String extension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return ".img";
        String ext = name.substring(dot + 1).toLowerCase();
        return ext.matches("[a-z0-9]{1,5}") ? "." + ext : ".img";
    }

    private static boolean isZip(byte[] data) {
        return data.length >= 4 && data[0] == 'P' && data[1] == 'K'
                && (data[2] == 3 || data[2] == 5 || data[2] == 7);
    }

    private static void collect(File base, File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) {
                collect(base, f, out);
            } else if (f.isFile() && relative(base, f) != null) {
                out.add(f);
            }
            if (out.size() > MAX_FILES * 4) return;
        }
    }

    private static String relative(File base, File f) {
        try {
            String bp = base.getCanonicalPath();
            String fp = f.getCanonicalPath();
            if (!fp.startsWith(bp + File.separator)) return null;
            return fp.substring(bp.length() + 1).replace(File.separatorChar, '/');
        } catch (Throwable t) {
            return null;
        }
    }

    private static String sanitizeRelative(String rel) {
        if (rel == null) return null;
        String p = rel.replace('\\', '/').trim();
        while (p.startsWith("/")) p = p.substring(1);
        if (p.isEmpty()) return null;
        for (String part : p.split("/")) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) return null;
        }
        return p;
    }

    private static void copy(File f, OutputStream os) throws IOException {
        InputStream in = new FileInputStream(f);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        in.close();
    }

    private static byte[] readEntry(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
            if (bos.size() > limit) throw new IOException("archive too large");
        }
        return bos.toByteArray();
    }
}
