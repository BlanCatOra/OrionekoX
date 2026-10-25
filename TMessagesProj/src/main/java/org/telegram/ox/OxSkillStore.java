package org.telegram.ox;

import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
            File download = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            scan(new File(download, "Operit/skills"), out, 0);
            scan(new File(download, "Operit2/skills"), out, 0);
        } catch (Throwable ignored) {
        }
        cached = out;
        return out;
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
