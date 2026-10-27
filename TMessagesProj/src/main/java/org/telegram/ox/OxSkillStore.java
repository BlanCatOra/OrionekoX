package org.telegram.ox;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;

import org.telegram.messenger.ApplicationLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Read-only view of Operit's SKILL.md packages (Download/Operit/skills and
 * Download/Operit2/skills). Agents can list and read them through built-in tools.
 */
public class OxSkillStore {

    public static class Skill {
        public String id;
        public String name;
        public String description;
        public File file;
    }

    private static long loadedAt;
    private static List<Skill> cached = new ArrayList<>();

    public static synchronized List<Skill> list() {
        long now = System.currentTimeMillis();
        if (now - loadedAt < 5000) return cached;
        loadedAt = now;
        ArrayList<Skill> out = new ArrayList<>();
        try {
            scan(new File(ApplicationLoader.applicationContext.getFilesDir(), "oxskills"), out, 0);
            File download = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            scan(new File(download, "Operit/skills"), out, 0);
            scan(new File(download, "Operit2/skills"), out, 0);
        } catch (Throwable ignored) {
        }
        cached = out;
        return out;
    }

    /** Imports SKILL.md (or a zip containing one/few) into the app-private skill dir. */
    public static synchronized int importFromUri(Context ctx, Uri uri) throws Exception {
        if (ctx == null || uri == null) throw new IllegalArgumentException("missing uri");
        byte[] data = readAll(ctx.getContentResolver().openInputStream(uri), 2 * 1024 * 1024);
        if (data.length == 0) throw new IllegalArgumentException("empty file");
        String display = queryDisplayName(ctx, uri);
        int imported = 0;
        if (isZip(data)) {
            ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(data));
            ZipEntry e;
            int entries = 0;
            while ((e = zis.getNextEntry()) != null && entries++ < 200) {
                if (e.isDirectory()) continue;
                String n = e.getName();
                if (!n.toLowerCase(Locale.ROOT).endsWith("skill.md")) continue;
                byte[] body = readAll(zis, 512 * 1024);
                if (body.length == 0) continue;
                int slash = n.lastIndexOf('/');
                String parent = slash > 0 ? n.substring(0, slash) : "";
                while (parent.endsWith("/")) parent = parent.substring(0, parent.length() - 1);
                // Chatbox/Kelivo backups use skills/<id>/SKILL.md; the wrapper
                // folder is not part of the skill id, otherwise re-importing an
                // export would create skills_<id> next to <id>.
                if (parent.startsWith("skills/")) parent = parent.substring("skills/".length());
                else if ("skills".equals(parent)) parent = "";
                String id = sanitizeSkillId(parent.isEmpty() ? stripMd(display) : parent);
                writeSkill(id, body);
                imported++;
                if (imported >= 50) break;
            }
            zis.close();
        } else {
            String id = sanitizeSkillId(stripMd(display));
            writeSkill(id, data);
            imported = 1;
        }
        if (imported == 0) throw new IllegalArgumentException("no SKILL.md found");
        loadedAt = 0;
        return imported;
    }

    /** Writes all visible SKILL.md files as skills/<id>/SKILL.md, the layout used
     *  by Kelivo/Chatbox backups and this app's own skill importer. */
    public static synchronized int exportZip(Context ctx, Uri uri) throws Exception {
        if (ctx == null || uri == null) throw new IllegalArgumentException("missing uri");
        List<Skill> skills = list();
        if (skills.isEmpty()) throw new IllegalStateException("no skills to export");
        OutputStream raw = ctx.getContentResolver().openOutputStream(uri);
        if (raw == null) throw new IllegalStateException("cannot open output");
        ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(raw));
        int count = 0;
        Set<String> used = new HashSet<>();
        try {
            for (Skill skill : skills) {
                if (skill == null || skill.file == null || !skill.file.isFile()) continue;
                byte[] body = readAll(new FileInputStream(skill.file), 512 * 1024);
                if (body.length == 0) continue;
                String base = sanitizeSkillId(skill.id != null && !skill.id.isEmpty()
                        ? skill.id : skill.name);
                String dir = base;
                int n = 2;
                while (!used.add(dir)) dir = base + "_" + (n++);
                zip.putNextEntry(new ZipEntry("skills/" + dir + "/SKILL.md"));
                zip.write(body);
                zip.closeEntry();
                count++;
            }
            zip.finish();
        } finally {
            zip.close();
        }
        if (count == 0) throw new IllegalStateException("no skills to export");
        return count;
    }

    private static void writeSkill(String id, byte[] body) throws Exception {        File base = new File(ApplicationLoader.applicationContext.getFilesDir(), "oxskills");
        File dir = new File(base, id);
        // Re-importing the same skill updates it instead of piling up
        // `name_1`, `name_2` duplicates.
        if (dir.isDirectory()) {
            File old = new File(dir, "SKILL.md");
            if (old.exists()) old.delete();
        }
        if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("cannot create skill dir");
        java.io.FileOutputStream out = new java.io.FileOutputStream(new File(dir, "SKILL.md"));
        out.write(body);
        out.close();
    }

    /** Adds one SKILL.md body (for example from a third-party backup) to the
     *  app-private skill library; an existing id is updated in place. */
    public static synchronized void importSkill(String id, byte[] body) throws Exception {
        if (body == null || body.length == 0) throw new IllegalArgumentException("empty skill");
        if (body.length > 512 * 1024) throw new IllegalArgumentException("skill too large");
        writeSkill(sanitizeSkillId(id), body);
        loadedAt = 0;
    }

    public static String skillId(String raw) {
        return sanitizeSkillId(raw);
    }

    /** Deletes a skill imported into app-private storage. Skills scanned from the
     *  user's Download/Operit folders stay read-only. */
    public static synchronized boolean delete(Skill skill) {
        if (skill == null || skill.file == null) return false;
        File base = new File(ApplicationLoader.applicationContext.getFilesDir(), "oxskills");
        File parent = skill.file.getParentFile();
        if (parent == null) return false;
        try {
            String basePath = base.getCanonicalPath() + File.separator;
            if (!parent.getCanonicalPath().startsWith(basePath)) return false;
            deleteTree(parent);
            loadedAt = 0;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void deleteTree(File f) {
        if (f == null) return;
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) deleteTree(c);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static String sanitizeSkillId(String raw) {
        String id = raw == null ? "" : raw.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (id.isEmpty()) id = "skill_" + System.currentTimeMillis();
        return id.length() > 48 ? id.substring(0, 48) : id;
    }

    private static String stripMd(String name) {
        if (name == null || name.isEmpty()) return "";
        String n = name;
        int dot = n.toLowerCase(Locale.ROOT).lastIndexOf(".md");
        if (dot > 0) n = n.substring(0, dot);
        return n;
    }

    private static boolean isZip(byte[] data) {
        return data.length > 4 && data[0] == 'P' && data[1] == 'K';
    }

    private static String queryDisplayName(Context ctx, Uri uri) {
        try (android.database.Cursor c = ctx.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && c.moveToFirst()) {
                    String n = c.getString(idx);
                    if (n != null && !n.isEmpty()) return n;
                }
            }
        } catch (Throwable ignored) {
        }
        String last = uri.getLastPathSegment();
        return last == null ? "skill" : last;
    }

    private static byte[] readAll(java.io.InputStream in, int max) throws Exception {
        if (in == null) return new byte[0];
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0 && bos.size() < max) {
            bos.write(buf, 0, Math.min(n, max - bos.size()));
        }
        if (!(in instanceof ZipInputStream)) in.close();
        return bos.toByteArray();
    }

    private static void scan(File dir, List<Skill> out, int depth) {
        if (dir == null || !dir.isDirectory() || depth > 5) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scan(f, out, depth + 1);
            } else if (f.getName().equalsIgnoreCase("SKILL.md")) {
                Skill s = parse(f);
                if (s != null) out.add(s);
            }
        }
    }

    private static Skill parse(File file) {
        try {
            Skill s = new Skill();
            s.file = file;
            s.id = file.getParentFile() == null ? file.getName() : file.getParentFile().getName();
            s.name = s.id;
            StringBuilder body = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                boolean front = false;
                int lines = 0;
                while ((line = r.readLine()) != null && lines++ < 80) {
                    if ("---".equals(line.trim())) {
                        front = !front;
                        continue;
                    }
                    if (front) {
                        int colon = line.indexOf(':');
                        if (colon > 0) {
                            String key = line.substring(0, colon).trim();
                            String value = line.substring(colon + 1).trim();
                            if ("name".equalsIgnoreCase(key) && !value.isEmpty()) s.name = value;
                            if ("description".equalsIgnoreCase(key) && !value.isEmpty()) s.description = value;
                        }
                    } else if (s.description == null) {
                        if (line.startsWith("#")) {
                            String title = line.replaceFirst("^#+\\s*", "").trim();
                            if (!title.isEmpty() && s.name.equals(s.id)) s.name = title;
                        } else if (!line.trim().isEmpty()) {
                            body.append(line.trim()).append(' ');
                            if (body.length() > 300) break;
                        }
                    }
                }
            }
            if (s.description == null) {
                String d = body.toString().trim();
                s.description = d.isEmpty() ? "" : (d.length() > 200 ? d.substring(0, 200) + "…" : d);
            }
            return s;
        } catch (Throwable t) {
            return null;
        }
    }

    public static String read(String name) {
        if (name == null || name.trim().isEmpty()) return "error: name is required";
        String key = name.trim();
        for (Skill s : list()) {
            if (key.equalsIgnoreCase(s.name) || key.equalsIgnoreCase(s.id)) {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(s.file), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    char[] buf = new char[4096];
                    int n;
                    while ((n = r.read(buf)) > 0 && sb.length() < 12000) sb.append(buf, 0, n);
                    return sb.length() >= 12000 ? sb.substring(0, 12000) + "\n…(truncated)" : sb.toString();
                } catch (Throwable t) {
                    return "error: " + t.getMessage();
                }
            }
        }
        return "error: skill not found: " + key;
    }

    public static String listJson() {
        JSONArray arr = new JSONArray();
        for (Skill s : list()) {
            try {
                arr.put(new JSONObject().put("name", s.name).put("id", s.id).put("description", s.description));
            } catch (Exception ignored) {
            }
        }
        return arr.toString();
    }
}
