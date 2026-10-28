package org.telegram.ox;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Local, per-agent memory library with two recall modes:
 * normal (keyword/token overlap) and vector (a local hashed n-gram vector).
 * Both use a forgetting curve: short-term entries decay quickly, frequently
 * recalled or important entries are promoted to long-term. Context compression
 * writes readable turn notes, extracted facts and a chunk summary here.
 */
public class OxMemoryStore {

    private static final String DB_NAME = "oxmemory.db";
    private static final int DB_VERSION = 1;
    private static final int VEC_DIM = 128;
    private static final int MAX_ROWS_PER_AGENT = 4000;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    public static final String MODE_OFF = "off";
    public static final String MODE_NORMAL = "normal";
    public static final String MODE_VECTOR = "vector";

    private static Helper helper;
    private static SQLiteDatabase db;

    public static class CompressResult {
        public int ingested;
        public String digest = "";
    }

    private static class Mem {
        long id;
        String kind;
        String role;
        String text;
        long createdAt;
        long lastAccess;
        int accessCount;
        double importance;
        double stability;
        String memoryType;
        float[] vector;
        double score;
    }

    private static class Helper extends SQLiteOpenHelper {
        Helper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override public void onCreate(SQLiteDatabase d) {
            d.execSQL("CREATE TABLE memories ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "agent_id TEXT NOT NULL,"
                    + "kind TEXT NOT NULL,"
                    + "role TEXT,"
                    + "text TEXT NOT NULL,"
                    + "created_at INTEGER NOT NULL,"
                    + "last_access INTEGER NOT NULL,"
                    + "access_count INTEGER NOT NULL DEFAULT 0,"
                    + "importance REAL NOT NULL DEFAULT 0.5,"
                    + "stability REAL NOT NULL DEFAULT 3,"
                    + "memory_type TEXT NOT NULL DEFAULT 'short',"
                    + "source TEXT,"
                    + "hash TEXT NOT NULL,"
                    + "vec BLOB)");
            d.execSQL("CREATE UNIQUE INDEX idx_mem_hash ON memories(agent_id, hash)");
            d.execSQL("CREATE INDEX idx_mem_agent ON memories(agent_id, created_at)");
            d.execSQL("CREATE TABLE memory_state ("
                    + "agent_id TEXT PRIMARY KEY,"
                    + "compressed_count INTEGER NOT NULL DEFAULT 0,"
                    + "last_compress_at INTEGER NOT NULL DEFAULT 0)");
        }

        @Override public void onUpgrade(SQLiteDatabase d, int oldVersion, int newVersion) {
            // Version 1 is the first schema; future migrations go here.
        }
    }

    private static synchronized SQLiteDatabase db() {
        if (db != null && db.isOpen()) return db;
        if (helper == null) {
            helper = new Helper(ApplicationLoader.applicationContext);
        }
        db = helper.getWritableDatabase();
        return db;
    }

    /** Ingests the oldest not-yet-compressed turns and returns a readable digest. */
    public static synchronized CompressResult compressHistory(String agentId,
            List<OxChatEntry> history, int keep) {
        CompressResult out = new CompressResult();
        if (agentId == null || history == null || history.isEmpty()) {
            out.digest = digest(agentId, 1800);
            return out;
        }
        int target = history.size() - Math.max(1, keep);
        if (target <= 0) {
            out.digest = digest(agentId, 1800);
            return out;
        }
        int from = stateCount(agentId);
        if (from < 0 || from > history.size()) from = 0;
        if (target <= from) {
            out.digest = digest(agentId, 1800);
            return out;
        }
        int ingested = 0;
        List<String> userLines = new ArrayList<>();
        long firstTs = 0;
        long lastTs = 0;
        for (int i = from; i < target && i < history.size(); i++) {
            OxChatEntry e = history.get(i);
            if (e == null || e.content == null || e.content.trim().isEmpty()) continue;
            long ts = e.ts > 0 ? e.ts : System.currentTimeMillis();
            if (firstTs == 0) firstTs = ts;
            lastTs = ts;
            String role = "assistant".equals(e.role) ? "assistant" : "user";
            String readable = ("user".equals(role) ? "用户：" : "助手：") + clip(e.content.trim(), 4000);
            double importance = importance(e.content, role);
            insert(agentId, "turn", role, readable, ts, importance,
                    importance >= 0.85 ? "long" : "short", "compression", true);
            ingested++;
            if ("user".equals(role)) {
                String line = clip(e.content.replace('\n', ' ').trim(), 140);
                if (!line.isEmpty() && userLines.size() < 12) userLines.add(line);
                for (String fact : extractFacts(e.content)) {
                    insert(agentId, "fact", "user", "用户：" + fact, ts,
                            0.9, "long", "extract", true);
                }
            }
        }
        if (ingested > 0 || userLines.size() > 0) {
            if (target - from >= 4 || countKind(agentId, "summary") == 0) {
                String summary = summaryText(userLines, firstTs, lastTs);
                insert(agentId, "summary", "system", summary,
                        lastTs > 0 ? lastTs : System.currentTimeMillis(),
                        0.8, "long", "compression", true);
            }
            setStateCount(agentId, target);
            prune(agentId);
        }
        out.ingested = ingested;
        out.digest = digest(agentId, 1800);
        return out;
    }

    /** A compact, natural-language view used in the system prompt. */
    public static synchronized String digest(String agentId, int maxChars) {
        if (agentId == null) return "";
        StringBuilder sb = new StringBuilder();
        try {
            Cursor c = db().rawQuery(
                    "SELECT kind,text,created_at FROM memories WHERE agent_id=? "
                            + "AND kind IN ('summary','fact') ORDER BY "
                            + "CASE kind WHEN 'summary' THEN 0 ELSE 1 END, "
                            + "importance DESC, created_at DESC LIMIT 12",
                    new String[]{agentId});
            try {
                while (c.moveToNext() && sb.length() < maxChars) {
                    String kind = c.getString(0);
                    String text = c.getString(1);
                    long ts = c.getLong(2);
                    String label = "summary".equals(kind) ? "摘要" : "事实";
                    sb.append("- [").append(label).append(' ')
                            .append(day(ts)).append("] ").append(text).append('\n');
                }
            } finally {
                c.close();
            }
        } catch (Throwable t) {
            FileLog.e("OxMemoryStore", t);
        }
        return clip(sb.toString().trim(), maxChars);
    }

    /** Ranked recall with forgetting curve and access promotion. */
    public static synchronized String recall(String agentId, String query, int limit, String mode) {
        if (agentId == null) return "";
        List<Mem> rows = load(agentId, 3000);
        if (rows.isEmpty()) return "";
        String q = query == null ? "" : query.trim();
        boolean vectorMode = MODE_VECTOR.equals(mode);
        List<String> qTokens = tokenize(q);
        Set<String> qSet = new HashSet<>(qTokens);
        float[] qVec = vectorMode ? embed(q) : null;
        long now = System.currentTimeMillis();
        for (Mem m : rows) {
            double relevance = 0;
            if (!q.isEmpty()) {
                double keyword = keywordScore(m.text, q, qSet);
                double cosine = vectorMode && qVec != null ? cosine(qVec, m.vector) : 0;
                relevance = vectorMode ? (0.68 * cosine + 0.32 * keyword) : keyword;
            } else {
                relevance = 0.3;
            }
            double ageDays = Math.max(0, (now - m.createdAt) / (double) DAY_MS);
            double halfLife = "long".equals(m.memoryType) ? 90.0 : 7.0;
            double decay = Math.exp(-ageDays / halfLife);
            double access = Math.min(1.0, Math.log1p(m.accessCount) / 2.5);
            double score = relevance * (0.55 + 0.35 * decay + 0.15 * access)
                    + m.importance * 0.12
                    + ("long".equals(m.memoryType) ? 0.03 : 0);
            if (!q.isEmpty() && relevance < 0.06) continue;
            m.score = score;
        }
        for (java.util.Iterator<Mem> it = rows.iterator(); it.hasNext(); ) {
            if (it.next().score <= 0) it.remove();
        }        rows.sort((a, b) -> Double.compare(b.score, a.score));
        int n = Math.max(1, Math.min(limit <= 0 ? 6 : limit, rows.size()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            Mem m = rows.get(i);
            touch(m);
            sb.append(i + 1).append(". [").append(day(m.createdAt)).append(" · ")
                    .append("summary".equals(m.kind) ? "摘要" : ("fact".equals(m.kind) ? "事实" : "对话"))
                    .append(" · 相关 ").append(String.format(Locale.US, "%.2f", m.score))
                    .append("] ").append(clip(m.text, 600)).append('\n');
        }
        return sb.toString().trim();
    }

    /** Verification-oriented search: says explicitly when there is no evidence. */
    public static synchronized String verify(String agentId, String query) {
        String found = strictVerify(agentId, query);
        if (found.isEmpty()) {
            return "未在本地记忆库中找到与「" + query + "」相关的记录。不要把它当作已确认的事实；"
                    + "可以先向用户说明你不确定，或继续对话后再确认。";
        }
        return "在记忆库中找到以下证据（按相关度排序）：\n" + found
                + "\n请区分记录中的事实与推断，不要把未出现的细节补全。";
    }

    /** Strict matching for verification: a multi-word claim must share at
     *  least two meaningful terms, so "favorite color" does not match
     *  "favorite editor" on the single word "favorite". */
    private static String strictVerify(String agentId, String query) {
        if (query == null || query.trim().isEmpty()) return "";
        Set<String> wanted = new HashSet<>();
        for (String t : tokenize(query)) {
            if (t.length() <= 1 && t.matches("[a-z]")) continue;
            if (STOPWORDS.contains(t)) continue;
            wanted.add(t);
        }
        if (wanted.isEmpty()) {
            for (String t : tokenize(query)) wanted.add(t);
        }
        int need = wanted.size() >= 2 ? 2 : 1;
        String full = query.trim().toLowerCase(Locale.ROOT);
        List<Mem> hits = new ArrayList<>();
        for (Mem m : load(agentId, 3000)) {
            String lower = m.text == null ? "" : m.text.toLowerCase(Locale.ROOT);
            boolean whole = lower.contains(full);
            int matched = 0;
            for (String t : wanted) if (lower.contains(t)) matched++;
            if (!whole && matched < need) continue;
            m.score = whole ? 100 + matched : matched;
            hits.add(m);
        }
        hits.sort((a, b) -> Double.compare(b.score, a.score));
        StringBuilder sb = new StringBuilder();
        int n = Math.min(5, hits.size());
        for (int i = 0; i < n; i++) {
            Mem m = hits.get(i);
            touch(m);
            sb.append(i + 1).append(". [").append(day(m.createdAt)).append(" · ")
                    .append(kindLabel(m.kind)).append("] ")
                    .append(clip(m.text, 600)).append('\n');
        }
        return sb.toString().trim();
    }

    private static final Set<String> STOPWORDS = new HashSet<>(java.util.Arrays.asList(
            "a", "an", "the", "is", "are", "am", "do", "does", "did", "my", "me",
            "i", "you", "your", "we", "our", "please", "verify", "remember",
            "about", "of", "to", "and", "or", "in", "on", "for", "that", "this",
            "it", "be", "was", "were", "has", "have", "had"));


    public static synchronized String rememberFact(String agentId, String text) {
        if (agentId == null || text == null || text.trim().isEmpty()) return "";
        String t = text.trim();
        insert(agentId, "fact", "user", "用户：" + t, System.currentTimeMillis(),
                0.9, "long", "tool", true);
        return t;
    }

    public static synchronized String stats(String agentId) {
        try {
            int all = count(agentId, null);
            int longCount = count(agentId, "long");
            int shortCount = count(agentId, "short");
            int facts = countKind(agentId, "fact");
            return all + " 条 · 长期 " + longCount + " · 短期 " + shortCount
                    + " · 事实 " + facts;
        } catch (Throwable t) {
            return "记忆库不可用";
        }
    }

    public static synchronized String recent(String agentId, int maxResults) {
        StringBuilder sb = new StringBuilder();
        try {
            Cursor c = db().rawQuery(
                    "SELECT kind,text,created_at,access_count,memory_type FROM memories "
                            + "WHERE agent_id=? ORDER BY created_at DESC LIMIT ?",
                    new String[]{agentId, String.valueOf(Math.max(1, maxResults))});
            try {
                while (c.moveToNext()) {
                    sb.append('[').append(day(c.getLong(2))).append(" · ")
                            .append(kindLabel(c.getString(0))).append(" · ")
                            .append(c.getString(4)).append(" · 访问 ")
                            .append(c.getInt(3)).append("] ")
                            .append(clip(c.getString(1), 240)).append('\n');
                }
            } finally {
                c.close();
            }
        } catch (Throwable t) {
            FileLog.e("OxMemoryStore", t);
        }
        return sb.toString().trim();
    }

    public static synchronized void clear(String agentId) {
        try {
            db().delete("memories", "agent_id=?", new String[]{agentId});
            db().delete("memory_state", "agent_id=?", new String[]{agentId});
        } catch (Throwable t) {
            FileLog.e("OxMemoryStore", t);
        }
    }

    // ---------------- internals ----------------

    private static int stateCount(String agentId) {
        try {
            Cursor c = db().rawQuery("SELECT compressed_count FROM memory_state WHERE agent_id=?",
                    new String[]{agentId});
            try {
                return c.moveToFirst() ? c.getInt(0) : 0;
            } finally {
                c.close();
            }
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void setStateCount(String agentId, int count) {
        try {
            ContentValues v = new ContentValues();
            v.put("agent_id", agentId);
            v.put("compressed_count", count);
            v.put("last_compress_at", System.currentTimeMillis());
            db().insertWithOnConflict("memory_state", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Throwable t) {
            FileLog.e("OxMemoryStore", t);
        }
    }

    private static void insert(String agentId, String kind, String role, String text,
            long createdAt, double importance, String type, String source, boolean withVector) {
        if (text == null || text.trim().isEmpty()) return;
        try {
            String normalized = text.trim();
            String hash = sha256(kind + "\n" + role + "\n" + normalized
                    + ("turn".equals(kind) ? "\n" + createdAt : ""));
            ContentValues v = new ContentValues();
            v.put("agent_id", agentId);
            v.put("kind", kind);
            v.put("role", role);
            v.put("text", clip(normalized, 8000));
            v.put("created_at", createdAt);
            v.put("last_access", createdAt);
            v.put("access_count", 0);
            v.put("importance", Math.max(0, Math.min(1, importance)));
            v.put("stability", "long".equals(type) ? 90.0 : 3.0);
            v.put("memory_type", type);
            v.put("source", source);
            v.put("hash", hash);
            if (withVector) v.put("vec", bytes(embed(normalized)));
            db().insertWithOnConflict("memories", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        } catch (Throwable t) {
            FileLog.e("OxMemoryStore", t);
        }
    }

    private static List<Mem> load(String agentId, int limit) {
        List<Mem> out = new ArrayList<>();
        try {
            Cursor c = db().rawQuery(
                    "SELECT id,kind,role,text,created_at,last_access,access_count,"
                            + "importance,stability,memory_type,vec FROM memories "
                            + "WHERE agent_id=? ORDER BY created_at DESC LIMIT ?",
                    new String[]{agentId, String.valueOf(limit)});
            try {
                while (c.moveToNext()) {
                    Mem m = new Mem();
                    m.id = c.getLong(0);
                    m.kind = c.getString(1);
                    m.role = c.getString(2);
                    m.text = c.getString(3);
                    m.createdAt = c.getLong(4);
                    m.lastAccess = c.getLong(5);
                    m.accessCount = c.getInt(6);
                    m.importance = c.getDouble(7);
                    m.stability = c.getDouble(8);
                    m.memoryType = c.getString(9);
                    m.vector = floats(c.getBlob(10));
                    out.add(m);
                }
            } finally {
                c.close();
            }
        } catch (Throwable t) {
            FileLog.e("OxMemoryStore", t);
        }
        return out;
    }

    private static void touch(Mem m) {
        try {
            long now = System.currentTimeMillis();
            boolean promote = "short".equals(m.memoryType)
                    && (m.accessCount >= 2 || m.importance >= 0.8);
            ContentValues v = new ContentValues();
            v.put("last_access", now);
            v.put("access_count", m.accessCount + 1);
            if (promote) {
                v.put("memory_type", "long");
                v.put("stability", Math.max(90.0, m.stability * 1.5));
            }
            db().update("memories", v, "id=?", new String[]{String.valueOf(m.id)});
        } catch (Throwable t) {
            FileLog.e("OxMemoryStore", t);
        }
    }

    private static void prune(String agentId) {
        try {
            int all = count(agentId, null);
            if (all > MAX_ROWS_PER_AGENT) {
                db().execSQL("DELETE FROM memories WHERE id IN (SELECT id FROM memories "
                                + "WHERE agent_id=? AND memory_type='short' "
                                + "ORDER BY importance ASC, created_at ASC LIMIT ?)",
                        new Object[]{agentId, all - MAX_ROWS_PER_AGENT});
            }
            long cutoff = System.currentTimeMillis() - 30L * DAY_MS;
            db().delete("memories", "agent_id=? AND memory_type='short' AND access_count=0 "
                    + "AND importance<0.6 AND created_at<?",
                    new String[]{agentId, String.valueOf(cutoff)});
        } catch (Throwable t) {
            FileLog.e("OxMemoryStore", t);
        }
    }

    private static int count(String agentId, String type) {
        String sql = "SELECT COUNT(*) FROM memories WHERE agent_id=?"
                + (type == null ? "" : " AND memory_type=?");
        String[] args = type == null ? new String[]{agentId} : new String[]{agentId, type};
        Cursor c = db().rawQuery(sql, args);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    private static int countKind(String agentId, String kind) {
        Cursor c = db().rawQuery("SELECT COUNT(*) FROM memories WHERE agent_id=? AND kind=?",
                new String[]{agentId, kind});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    private static double importance(String text, String role) {
        if (text == null) return 0.4;
        String t = text.trim();
        double v = 0.42;
        if (t.length() >= 20 && t.length() <= 600) v += 0.08;
        if (t.length() > 600) v += 0.04;
        if (hasFactMarker(t)) v += 0.25;
        if (t.matches(".*\\d.*")) v += 0.06;
        if ("user".equals(role)) v += 0.04;
        return Math.max(0.3, Math.min(0.95, v));
    }

    private static boolean hasFactMarker(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        String[] markers = {"我叫", "我是", "我的", "我喜欢", "我不喜欢", "我住", "我在",
                "记住", "生日", "计划", "偏好", "习惯", "remember", "my ", "i am",
                "i like", "i live", "don't forget", "偏好"};
        for (String m : markers) if (t.contains(m)) return true;
        return false;
    }

    private static List<String> extractFacts(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        String[] parts = text.split("[。！？!?；;\\n]");
        for (String part : parts) {
            String s = part.trim();
            if (s.length() < 4 || s.length() > 220) continue;
            if (!hasFactMarker(s)) continue;
            if (!out.contains(s)) out.add(s);
            if (out.size() >= 4) break;
        }
        return out;
    }

    private static String summaryText(List<String> lines, long firstTs, long lastTs) {
        if (lines.isEmpty()) return "";
        String span = firstTs > 0 && lastTs > 0
                ? day(firstTs) + (day(firstTs).equals(day(lastTs)) ? "" : " 至 " + day(lastTs)) + " 的对话摘要："
                : "对话摘要：";
        StringBuilder sb = new StringBuilder(span);
        int n = 0;
        Set<String> seen = new HashSet<>();
        for (String line : lines) {
            String key = line.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) continue;
            sb.append("\n- 用户提到：").append(line);
            if (++n >= 10) break;
        }
        return sb.toString();
    }

    private static String kindLabel(String kind) {
        if ("summary".equals(kind)) return "摘要";
        if ("fact".equals(kind)) return "事实";
        return "对话";
    }

    private static String day(long ts) {
        if (ts <= 0) return "未知日期";
        return new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(new java.util.Date(ts));
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        int end = max;
        if (end > 0 && Character.isHighSurrogate(s.charAt(end - 1))) end--;
        return s.substring(0, end) + "…";
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format(Locale.US, "%02x", b & 0xff));
            return sb.toString();
        } catch (Throwable t) {
            return Integer.toHexString(s.hashCode());
        }
    }

    // ---------------- local vector mode ----------------

    private static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder cjk = new StringBuilder();
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (isCjk(c)) {
                if (word.length() > 0) { out.add(word.toString()); word.setLength(0); }
                cjk.append(c);
            } else if (Character.isLetterOrDigit(c)) {
                if (cjk.length() > 0) {
                    addCjkTokens(cjk, out);
                    cjk.setLength(0);
                }
                word.append(c);
            } else {
                if (word.length() > 0) { out.add(word.toString()); word.setLength(0); }
                if (cjk.length() > 0) { addCjkTokens(cjk, out); cjk.setLength(0); }
            }
        }
        if (word.length() > 0) out.add(word.toString());
        if (cjk.length() > 0) addCjkTokens(cjk, out);
        return out;
    }

    private static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF)
                || (c >= 0xF900 && c <= 0xFAFF);
    }

    private static void addCjkTokens(StringBuilder cjk, List<String> out) {
        String s = cjk.toString();
        for (int i = 0; i < s.length(); i++) {
            out.add(String.valueOf(s.charAt(i)));
            if (i + 1 < s.length()) out.add(s.substring(i, i + 2));
        }
    }

    private static float[] embed(String text) {
        float[] v = new float[VEC_DIM];
        List<String> tokens = tokenize(text);
        for (String t : tokens) {
            if (t.isEmpty()) continue;
            int h = fnv(t);
            int idx = (h & 0x7fffffff) % VEC_DIM;
            float sign = ((h >>> 16) & 1) == 0 ? 1f : -1f;
            v[idx] += sign;
        }
        double norm = 0;
        for (float x : v) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm > 1e-6) for (int i = 0; i < v.length; i++) v[i] /= (float) norm;
        return v;
    }

    private static int fnv(String s) {
        int h = 0x811c9dc5;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x01000193;
        }
        return h;
    }

    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null) return 0;
        int n = Math.min(a.length, b.length);
        double dot = 0;
        for (int i = 0; i < n; i++) dot += a[i] * b[i];
        return Math.max(0, dot);
    }

    private static double keywordScore(String text, String query, Set<String> qSet) {
        if (text == null || query == null || query.trim().isEmpty()) return 0;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains(query.toLowerCase(Locale.ROOT))) return 1.0;
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty() || qSet.isEmpty()) return 0;
        int hit = 0;
        for (String t : tokens) if (qSet.contains(t)) hit++;
        double overlap = hit / (double) Math.max(1, qSet.size());
        return Math.min(1.0, overlap * 0.9);
    }

    private static byte[] bytes(float[] v) {
        ByteBuffer bb = ByteBuffer.allocate(v.length * 4);
        for (float x : v) bb.putFloat(x);
        return bb.array();
    }

    private static float[] floats(byte[] data) {
        if (data == null || data.length < 4) return new float[0];
        ByteBuffer bb = ByteBuffer.wrap(data);
        float[] v = new float[data.length / 4];
        for (int i = 0; i < v.length; i++) v[i] = bb.getFloat();
        return v;
    }
}
