package org.telegram.ox;

import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * OxMemoryOntology — Markdown as the single source of truth for an agent's
 * long-term memory (Stage A / app 0.78).
 *
 * Every memory is a block in files/oxagents/&lt;id&gt;/memory.md:
 *   ⟦oxmem⟧{"id":...,"created":...,"type":...,"status":...,"people":...,"topics":...,"source":...,"supersedes":...,"hash":...}
 *   &lt;content lines&gt;
 *
 * SQLite (OxMemoryStore) is a derived, fully rebuildable index. Writes are
 * atomic: a single entry is appended + fsync'd; a rewrite (supersede / migrate)
 * uses a temp file + rename. See docs/memory-architecture-report.md §4.
 */
public final class OxMemoryOntology {

    public static final String SENTINEL = "⟦oxmem⟧";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_SUPERSEDED = "superseded";

    private OxMemoryOntology() {}

    public static File memoryMd(String agentId) {
        File dir = new File(OxAgentStore.root(), agentId);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "memory.md");
    }

    public static final class Entry {
        public String id = "";
        public long createdAt = 0;
        public String type = "note";     // fact | summary | turn | tool | note
        public String status = STATUS_ACTIVE;
        public String people = "";
        public String topics = "";
        public String source = "tool";
        public String supersedes = "";
        public String contentHash = "";
        public String content = "";
        /** Pinned read-always section: CORE / ABOUT_USER / ABOUT_AGENT / CURRENT.
         *  Empty for ordinary retrievable memory. */
        public String section = "";

        public JSONObject toMeta() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("created", createdAt);
                o.put("type", type);
                o.put("status", status);
                o.put("people", people);
                o.put("topics", topics);
                o.put("source", source);
                o.put("supersedes", supersedes);
                o.put("section", section);
                o.put("hash", contentHash);
            } catch (Throwable t) {
                FileLog.e("OxMemoryOntology", t);
            }
            return o;
        }
    }

    public static final class LegacyRow {
        public String kind;
        public String role;
        public String text;
        public long createdAt;
        public long invalidAt;
        public String source;
        public String hash;
    }

    public static String newPublicId() {
        return "mem_" + Long.toHexString(System.currentTimeMillis())
                + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static String contentHash(String content) {
        if (content == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format(Locale.US, "%02x", b & 0xff));
            return sb.toString();
        } catch (Throwable t) {
            return Integer.toHexString(content.hashCode());
        }
    }

    /** Append one entry to the markdown file (atomic enough: append + fsync). */
    public static synchronized void appendEntry(String agentId, Entry e) {
        if (e == null || e.content == null || e.content.trim().isEmpty()) return;
        if (e.id == null || e.id.isEmpty()) e.id = newPublicId();
        if (e.createdAt <= 0) e.createdAt = System.currentTimeMillis();
        if (e.contentHash == null || e.contentHash.isEmpty()) e.contentHash = contentHash(e.content);
        if (e.status == null || e.status.isEmpty()) e.status = STATUS_ACTIVE;
        String block = SENTINEL + e.toMeta().toString() + "\n" + e.content.trim() + "\n";
        File f = memoryMd(agentId);
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f, true);
             OutputStreamWriter w = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            w.write(block);
            w.flush();
            fos.getFD().sync();
        } catch (Throwable t) {
            FileLog.e("OxMemoryOntology", t);
        }
    }

    /** Replace the whole file with the given entries (temp + fsync + rename). */
    public static synchronized void rewriteAll(String agentId, List<Entry> entries) {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            sb.append(SENTINEL).append(e.toMeta().toString())
                    .append("\n").append(e.content.trim()).append("\n");
        }
        File f = memoryMd(agentId);
        File dir = f.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
        File tmp = new File(dir, f.getName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp);
             OutputStreamWriter w = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            w.write(sb.toString());
            w.flush();
            fos.getFD().sync();
        } catch (Throwable t) {
            FileLog.e("OxMemoryOntology", t);
            return;
        }
        if (!tmp.renameTo(f)) {
            f.delete();
            tmp.renameTo(f);
        }
    }

    /** Mark an existing entry superseded and append a new version linking back. */
    public static synchronized String supersede(String agentId, String oldId, Entry next) {
        if (next == null || next.content == null || next.content.trim().isEmpty()) return "";
        List<Entry> all = parse(agentId);
        boolean found = false;
        for (Entry e : all) {
            if (e.id.equals(oldId) && STATUS_ACTIVE.equals(e.status)) {
                e.status = STATUS_SUPERSEDED;
                found = true;
                break;
            }
        }
        if (!found) return "";
        if (next.id == null || next.id.isEmpty()) next.id = newPublicId();
        next.supersedes = oldId;
        next.createdAt = System.currentTimeMillis();
        next.contentHash = contentHash(next.content);
        next.status = STATUS_ACTIVE;
        all.add(next);
        rewriteAll(agentId, all);
        return next.id;
    }

    public static synchronized List<Entry> parse(String agentId) {
        List<Entry> out = new ArrayList<>();
        String all = readAll(agentId);
        if (all.isEmpty()) return out;
        String[] lines = all.split("\n", -1);
        Entry cur = null;
        StringBuilder body = new StringBuilder();
        for (String line : lines) {
            if (line.startsWith(SENTINEL)) {
                if (cur != null) {
                    cur.content = body.toString().trim();
                    out.add(cur);
                }
                cur = new Entry();
                body.setLength(0);
                String json = line.substring(SENTINEL.length()).trim();
                try {
                    JSONObject o = new JSONObject(json);
                    cur.id = o.optString("id", "");
                    cur.createdAt = o.optLong("created", 0);
                    cur.type = o.optString("type", "note");
                    cur.status = o.optString("status", STATUS_ACTIVE);
                    cur.people = o.optString("people", "");
                    cur.topics = o.optString("topics", "");
                    cur.source = o.optString("source", "tool");
                    cur.supersedes = o.optString("supersedes", "");
                    cur.contentHash = o.optString("hash", "");
                } catch (Throwable t) {
                    cur = null;
                }
            } else if (cur != null) {
                body.append(line).append('\n');
            }
        }
        if (cur != null) {
            cur.content = body.toString().trim();
            out.add(cur);
        }
        return out;
    }

    /** Rebuild the SQLite derived index from the markdown source of truth. */
    public static void rebuildIndex(String agentId) {
        OxMemoryStore.upsertFromOntology(agentId, parse(agentId));
    }

    /** Migrate legacy SQLite rows + memory.txt into markdown (idempotent). */
    public static synchronized int migrateLegacy(String agentId) {
        File f = memoryMd(agentId);
        if (f.exists() && f.length() > 0) return 0; // already migrated
        List<Entry> entries = new ArrayList<>();
        for (LegacyRow r : OxMemoryStore.exportLegacyRows(agentId)) {
            Entry e = new Entry();
            e.id = newPublicId();
            e.createdAt = r.createdAt;
            e.type = r.kind == null ? "note" : r.kind;
            e.status = r.invalidAt > 0 ? STATUS_SUPERSEDED : STATUS_ACTIVE;
            e.source = r.source == null || r.source.isEmpty() ? "migrated" : r.source;
            e.content = r.text == null ? "" : r.text;
            e.contentHash = (r.hash == null || r.hash.isEmpty()) ? contentHash(e.content) : r.hash;
            entries.add(e);
        }
        String memTxt = OxAgentStore.loadMemory(agentId, Integer.MAX_VALUE);
        if (memTxt != null) {
            for (String line : memTxt.split("\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                Entry e = new Entry();
                e.id = newPublicId();
                e.createdAt = System.currentTimeMillis();
                e.type = "note";
                e.source = "migrated-memory-txt";
                e.content = line;
                e.contentHash = contentHash(line);
                entries.add(e);
            }
        }
        if (!entries.isEmpty()) rewriteAll(agentId, entries);
        return entries.size();
    }

    private static String readAll(String agentId) {
        File f = memoryMd(agentId);
        if (!f.exists()) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(f), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Throwable t) {
            FileLog.e("OxMemoryOntology", t);
            return "";
        }
    }

    // ---------------- CORE / ABOUT_USER / ABOUT_AGENT / CURRENT ----------------

    /** Pinned read-always layers live in a separate, human-editable file so the
     *  retrievable memory.md stays free of non-recall content. */
    public static File coreFile(String agentId) {
        File dir = new File(OxAgentStore.root(), agentId);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "core.md");
    }

    /** Seed the pinned layers once. ABOUT_USER is pre-filled from the legacy
     *  memory.txt so existing stable user facts survive the migration. */
    public static synchronized void ensureCore(String agentId) {
        File f = coreFile(agentId);
        if (f.exists() && f.length() > 0) return;
        String mem = OxAgentStore.loadMemory(agentId, Integer.MAX_VALUE);
        StringBuilder sb = new StringBuilder();
        sb.append("## CORE\n")
          .append("你是 OrionekoX 本地记忆助手。记忆库以 Markdown 为唯一事实来源，")
          .append("可在 files/oxagents/<id>/memory.md 审阅与编辑。\n")
          .append("稳定记忆每会话固定注入；同一主题有冲突时以时间最新的事实为准。\n\n");
        sb.append("## ABOUT_USER\n");
        if (mem != null && !mem.trim().isEmpty()) {
            sb.append(mem.trim()).append("\n");
        } else {
            sb.append("（暂无用户长期记忆；用户提到的稳定偏好会被自动记录）\n");
        }
        sb.append("\n## ABOUT_AGENT\n");
        sb.append("本助手由 AI 驱动，负责本地记忆的写入、检索与压缩；所有记忆仅存于本机。\n\n");
        sb.append("## CURRENT\n");
        sb.append("（当前任务状态；由会话动态更新）\n");
        writeFile(f, sb.toString());
    }

    public static String readSection(String agentId, String section) {
        File f = coreFile(agentId);
        if (!f.exists()) return "";
        String all = readFile(f);
        String marker = "## " + section;
        int i = all.indexOf(marker);
        if (i < 0) return "";
        int start = i + marker.length();
        int end = all.indexOf("\n## ", start);
        String body = end < 0 ? all.substring(start) : all.substring(start, end);
        return body.trim();
    }

    public static synchronized void writeSection(String agentId, String section, String content) {
        File f = coreFile(agentId);
        ensureCore(agentId);
        String all = readFile(f);
        String marker = "## " + section;
        int i = all.indexOf(marker);
        String body = (content == null ? "" : content.trim()) + "\n";
        if (i < 0) {
            all = all + "\n" + marker + "\n" + body;
        } else {
            int start = i + marker.length();
            int end = all.indexOf("\n## ", start);
            if (end < 0) {
                all = all.substring(0, start) + "\n" + body;
            } else {
                all = all.substring(0, start) + "\n" + body + all.substring(end);
            }
        }
        writeFile(f, all);
    }

    /** Update an existing entry's content in place so the Markdown source of
     *  truth stays consistent with the derived SQLite index after a richer
     *  fact replacement (consolidateFact). */
    public static synchronized boolean updateEntry(String agentId, String publicId,
            String content, String contentHash, long createdAt) {
        List<Entry> all = parse(agentId);
        for (Entry e : all) {
            if (e.id.equals(publicId)) {
                e.content = content;
                e.contentHash = contentHash;
                e.createdAt = createdAt;
                e.status = STATUS_ACTIVE;
                rewriteAll(agentId, all);
                return true;
            }
        }
        return false;
    }

    /** Mark an entry superseded without appending a replacement (the replacement
     *  is created by the caller's normal insert path, carrying supersedes). */
    public static synchronized boolean markSuperseded(String agentId, String oldId) {
        List<Entry> all = parse(agentId);
        boolean found = false;
        for (Entry e : all) {
            if (e.id.equals(oldId) && STATUS_ACTIVE.equals(e.status)) {
                e.status = STATUS_SUPERSEDED;
                found = true;
                break;
            }
        }
        if (found) rewriteAll(agentId, all);
        return found;
    }

    private static String readFile(File f) {
        if (!f.exists()) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(f), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Throwable t) {
            FileLog.e("OxMemoryOntology", t);
            return "";
        }
    }

    private static void writeFile(File f, String text) {
        File dir = f.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
        File tmp = new File(dir, f.getName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp);
             OutputStreamWriter w = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            w.write(text);
            w.flush();
            fos.getFD().sync();
        } catch (Throwable t) {
            FileLog.e("OxMemoryOntology", t);
            return;
        }
        if (!tmp.renameTo(f)) {
            f.delete();
            tmp.renameTo(f);
        }
    }
}
