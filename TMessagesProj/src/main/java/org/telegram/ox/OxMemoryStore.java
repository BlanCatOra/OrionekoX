package org.telegram.ox;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final int DB_VERSION = 2;
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
        public int batchFrom;
        public int batchTo;
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
        double difficulty;
        String memoryType;
        long validFrom;
        long invalidAt;
        String subject;
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
                    + "difficulty REAL NOT NULL DEFAULT 5,"
                    + "memory_type TEXT NOT NULL DEFAULT 'short',"
                    + "valid_from INTEGER NOT NULL DEFAULT 0,"
                    + "invalid_at INTEGER NOT NULL DEFAULT 0,"
                    + "subject TEXT,"
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
            if (oldVersion < 2) {
                d.execSQL("ALTER TABLE memories ADD COLUMN difficulty REAL NOT NULL DEFAULT 5");
                d.execSQL("ALTER TABLE memories ADD COLUMN valid_from INTEGER NOT NULL DEFAULT 0");
                d.execSQL("ALTER TABLE memories ADD COLUMN invalid_at INTEGER NOT NULL DEFAULT 0");
                d.execSQL("ALTER TABLE memories ADD COLUMN subject TEXT");
                d.execSQL("UPDATE memories SET valid_from=created_at WHERE valid_from=0");
            }
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
                scrubAllInvalidated(agentId);
                consolidateSummaries(agentId);
            }
            setStateCount(agentId, target);
            prune(agentId);
        }
        out.ingested = ingested;
        out.batchFrom = from;
        out.batchTo = target;
        out.digest = digest(agentId, 1800);
        return out;
    }

    /** A compact, natural-language view used in the system prompt. */
    public static synchronized String digest(String agentId, int maxChars) {
        if (agentId == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("注意：摘要按时间记录，可能包含后来被更新的事实；有冲突时以最新的“事实”条目为准。\n");
        try {
            Cursor c = db().rawQuery(
                    "SELECT kind,text,created_at FROM memories WHERE agent_id=? "
                            + "AND invalid_at=0 AND kind IN ('summary','fact') ORDER BY "
                            + "CASE kind WHEN 'fact' THEN 0 ELSE 1 END, "
                            + "importance DESC, created_at DESC LIMIT 12",
                    new String[]{agentId});
            try {
                int summaries = 0;
                while (c.moveToNext() && sb.length() < maxChars) {
                    String kind = c.getString(0);
                    if ("summary".equals(kind) && ++summaries > 1) continue;
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
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        boolean vectorMode = MODE_VECTOR.equals(mode);
        boolean temporal = isTemporalQuery(q);
        List<String> qTokens = meaningfulTokens(q);
        Set<String> qSet = new HashSet<>(qTokens);
        float[] qVec = vectorMode ? embed(q) : null;
        long now = System.currentTimeMillis();

        List<Mem> active = new ArrayList<>();
        List<List<String>> docs = new ArrayList<>();
        Map<String, Integer> df = new HashMap<>();
        for (Mem m : rows) {
            if (m.invalidAt > 0 && !temporal) continue;
            List<String> doc = meaningfulTokens(m.text);
            active.add(m);
            docs.add(doc);
            for (String t : new HashSet<>(doc)) {
                Integer n = df.get(t);
                df.put(t, n == null ? 1 : n + 1);
            }
        }
        if (active.isEmpty()) return "";
        int n = active.size();
        double avgdl = 0;
        for (List<String> d : docs) avgdl += d.size();
        avgdl = Math.max(1, avgdl / n);
        int limitN = Math.max(1, Math.min(limit <= 0 ? 6 : limit, n));
        double maxBm25 = 0;
        double[] bm25 = new double[n];
        for (int i = 0; i < n; i++) {
            bm25[i] = bm25(docs.get(i), qSet, df, n, avgdl);
            if (bm25[i] > maxBm25) maxBm25 = bm25[i];
        }
        for (int i = 0; i < n; i++) {
            Mem m = active.get(i);
            double lexical = maxBm25 <= 0 ? 0 : bm25[i] / maxBm25;
            double cosineScore = vectorMode && qVec != null ? Math.max(0, cosine(qVec, m.vector)) : 0;
            double ageDays = Math.max(0, (now - Math.max(m.lastAccess, m.createdAt)) / (double) DAY_MS);
            double stability = Math.max(0.2, m.stability);
            double retention = 1.0 / (1.0 + ageDays / (9.0 * stability));
            double access = Math.min(1.0, Math.log1p(m.accessCount) / 2.5);
            double score;
            if (q.isEmpty()) {
                score = 0.5 * retention + 0.3 * m.importance + 0.2 * access;
            } else if (vectorMode) {
                score = 0.42 * lexical + 0.32 * cosineScore + 0.14 * retention
                        + 0.08 * m.importance + 0.04 * access;
            } else {
                score = 0.58 * lexical + 0.22 * retention
                        + 0.12 * m.importance + 0.08 * access;
            }
            if ("long".equals(m.memoryType)) score += 0.02;
            if (m.invalidAt > 0) score *= 0.55;
            if (!q.isEmpty() && lexical < 0.04 && cosineScore < 0.10) score = 0;
            m.score = Math.max(0, Math.min(1.5, score));
        }
        for (java.util.Iterator<Mem> it = active.iterator(); it.hasNext(); ) {
            if (it.next().score <= 0) it.remove();
        }
        if (active.isEmpty()) return "";
        active.sort((a, b) -> Double.compare(b.score, a.score));

        // MMR: keep the result set diverse so repeated small talk does not
        // crowd out the one fact the user needs.
        List<Mem> chosen = new ArrayList<>();
        while (chosen.size() < limitN && !active.isEmpty()) {
            Mem best = null;
            double bestAdjusted = -1;
            for (Mem candidate : active) {
                double maxSim = 0;
                for (Mem picked : chosen) {
                    double sim = vectorMode
                            ? Math.max(0, cosine(candidate.vector, picked.vector))
                            : jaccard(new HashSet<>(tokenize(candidate.text)),
                            new HashSet<>(tokenize(picked.text)));
                    if (sim > maxSim) maxSim = sim;
                }
                double adjusted = candidate.score - 0.18 * maxSim;
                if (adjusted > bestAdjusted) {
                    bestAdjusted = adjusted;
                    best = candidate;
                }
            }
            if (best == null) break;
            active.remove(best);
            chosen.add(best);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chosen.size(); i++) {
            Mem m = chosen.get(i);
            touch(m);
            sb.append(i + 1).append(". [").append(day(m.createdAt)).append(" · ")
                    .append("summary".equals(m.kind) ? "摘要" : ("fact".equals(m.kind) ? "事实" : "对话"))
                    .append(" · 相关 ").append(String.format(Locale.US, "%.2f", m.score))
                    .append(m.invalidAt > 0 ? " · 历史" : "")
                    .append("] ").append(clip(m.text, 600)).append('\n');
        }
        return sb.toString().trim();
    }

    private static List<String> meaningfulTokens(String text) {
        List<String> out = new ArrayList<>();
        for (String t : tokenize(text)) {
            if (STOPWORDS.contains(t)) continue;
            out.add(t);
        }
        return out;
    }

    private static double bm25(List<String> doc, Set<String> query, Map<String, Integer> df,
            int n, double avgdl) {
        if (doc.isEmpty() || query.isEmpty()) return 0;
        Map<String, Integer> tf = new HashMap<>();
        for (String t : doc) {
            Integer c = tf.get(t);
            tf.put(t, c == null ? 1 : c + 1);
        }
        double k1 = 1.2, b = 0.75, score = 0;
        for (String term : query) {
            Integer count = tf.get(term);
            if (count == null) continue;
            int docFreq = df.getOrDefault(term, 0);
            double idf = Math.log(1 + (n - docFreq + 0.5) / (docFreq + 0.5));
            double denom = count + k1 * (1 - b + b * doc.size() / avgdl);
            score += idf * count * (k1 + 1) / denom;
        }
        return score;
    }

    private static boolean isTemporalQuery(String query) {
        return query.contains("以前") || query.contains("原来") || query.contains("过去")
                || query.contains("上次") || query.contains("曾经") || query.contains("used to")
                || query.contains("previously") || query.contains("in the past");
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
        List<Mem> factHits = new ArrayList<>();
        List<Mem> otherHits = new ArrayList<>();
        for (Mem m : load(agentId, 3000)) {
            if (m.invalidAt > 0) continue;
            String lower = m.text == null ? "" : m.text.toLowerCase(Locale.ROOT);
            boolean whole = lower.contains(full);
            int matched = 0;
            for (String t : wanted) if (lower.contains(t)) matched++;
            if (!whole && matched < need) continue;
            m.score = whole ? 100 + matched : matched;
            if ("fact".equals(m.kind)) factHits.add(m);
            else otherHits.add(m);
        }
        List<Mem> hits = factHits.isEmpty() ? otherHits : factHits;
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

    /** Stores an abstractive summary/facts produced by the agent's own model.
     *  Invalid JSON or oversized values are rejected by the caller. */
    public static synchronized int ingestModelMemory(String agentId, JSONObject result) {
        if (agentId == null || result == null) return 0;
        int facts = 0;
        try {
            JSONArray arr = result.optJSONArray("facts");
            for (int i = 0; arr != null && i < arr.length() && facts < 12; i++) {
                JSONObject f = arr.optJSONObject(i);
                if (f == null) continue;
                String text = f.optString("text", "").trim();
                if (text.length() < 2) continue;
                double importance = f.optDouble("importance", 0.75);
                importance = Math.max(0.3, Math.min(0.95, importance));
                insert(agentId, "fact", "user", "用户：" + clip(text, 500),
                        System.currentTimeMillis(), importance, "long", "model", true);
                facts++;
            }
            String summary = result.optString("summary", "").trim();
            if (summary.length() >= 4) {
                insert(agentId, "summary", "system", clip(summary, 4000),
                        System.currentTimeMillis(), 0.9, "long", "model", true);
                scrubAllInvalidated(agentId);
                consolidateSummaries(agentId);
            }
        } catch (Throwable t) {
            FileLog.e("OxMemoryStore", t);
        }
        return facts;
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
            String subject = "fact".equals(kind) ? subjectKey(normalized) : "";
            if ("fact".equals(kind) && consolidateFact(agentId, normalized, subject,
                    createdAt, importance)) return;
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
            v.put("stability", "long".equals(type)
                    ? Math.max(30.0, importance * 45.0)
                    : Math.max(1.0, importance * 7.0));
            v.put("difficulty", "long".equals(type) ? 4.0 : 6.0);
            v.put("memory_type", type);
            v.put("valid_from", createdAt);
            v.put("invalid_at", 0L);
            v.put("subject", subject);
            v.put("source", source);
            v.put("hash", hash);
            if (withVector) v.put("vec", bytes(embed(normalized)));
            db().insertWithOnConflict("memories", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        } catch (Throwable t) {
            FileLog.e("OxMemoryStore", t);
        }
    }

    /** Mem0-style fact reconciliation: equivalent facts are reinforced, a
     *  richer version replaces a weaker one, and contradictory numeric/negated
     *  facts are invalidated (not deleted) before the new fact is inserted. */
    private static boolean consolidateFact(String agentId, String text, String subject,
            long now, double importance) {
        if (text == null || text.trim().isEmpty()) return false;
        List<Mem> facts = loadFacts(agentId);
        Set<String> newTokens = new HashSet<>(tokenize(text));
        for (Mem old : facts) {
            double jaccard = jaccard(newTokens, new HashSet<>(tokenize(old.text)));
            boolean sameSubject = !subject.isEmpty() && subject.equals(old.subject);
            if (!sameSubject && jaccard < 0.72) continue;
            Set<String> oldTokens = new HashSet<>(meaningfulTokens(old.text));
            Set<String> newMeaningful = new HashSet<>(meaningfulTokens(text));
            boolean newCoversOld = newMeaningful.containsAll(oldTokens);
            boolean oldCoversNew = oldTokens.containsAll(newMeaningful);
            if (contradicts(old.text, text)
                    || (sameSubject && !newCoversOld && !oldCoversNew)) {
                invalidateFact(agentId, old.id, now, old.text);
                return false;
            }
            try {
                ContentValues v = new ContentValues();
                boolean richer = text.length() > old.text.length() * 1.15;
                if (richer) {
                    v.put("text", clip(text, 8000));
                    v.put("hash", sha256("fact\nuser\n" + text));
                    v.put("valid_from", now);
                    v.put("importance", Math.max(old.importance, importance));
                    v.put("vec", bytes(embed(text)));
                }
                v.put("last_access", now);
                v.put("access_count", old.accessCount + 1);
                db().update("memories", v, "id=?", new String[]{String.valueOf(old.id)});
            } catch (Throwable t) {
                FileLog.e("OxMemoryStore", t);
            }
            return true;
        }
        return false;
    }

    /** Keeps the summary stack bounded by merging the oldest four summaries
     *  into one rolling summary (MemGPT-style recursive summary). */
    private static void consolidateSummaries(String agentId) {
        try {
            if (countKind(agentId, "summary") <= 6) return;
            List<Mem> old = new ArrayList<>();
            Cursor c = db().rawQuery(
                    "SELECT id,text,created_at FROM memories WHERE agent_id=? "
                            + "AND kind='summary' AND invalid_at=0 ORDER BY created_at ASC LIMIT 4",
                    new String[]{agentId});
            try {
                while (c.moveToNext()) {
                    Mem m = new Mem();
                    m.id = c.getLong(0);
                    m.text = c.getString(1);
                    m.createdAt = c.getLong(2);
                    old.add(m);
                }
            } finally {
                c.close();
            }
            if (old.size() < 4) return;
            StringBuilder merged = new StringBuilder("较早对话摘要（合并）：");
            Set<String> seen = new HashSet<>();
            for (Mem m : old) {
                for (String line : m.text.split("\n")) {
                    String l = line.trim();
                    if (l.isEmpty() || !seen.add(l)) continue;
                    if (merged.length() + l.length() > 3500) break;
                    merged.append('\n').append(l);
                }
            }
            long now = System.currentTimeMillis();
            for (Mem m : old) invalidateFact(agentId, m.id, now, null);
            insert(agentId, "summary", "system", merged.toString(), now, 0.8,
                    "long", "compression-merge", true);
        } catch (Throwable t) {
            FileLog.e("OxMemoryStore", t);
        }
    }

    private static List<Mem> loadFacts(String agentId) {
        List<Mem> out = new ArrayList<>();
        try {
            Cursor c = db().rawQuery(
                    "SELECT id,kind,role,text,created_at,last_access,access_count,"
                            + "importance,stability,difficulty,memory_type,valid_from,"
                            + "invalid_at,subject,vec FROM memories "
                            + "WHERE agent_id=? AND kind='fact' AND invalid_at=0 "
                            + "ORDER BY created_at DESC LIMIT 500",
                    new String[]{agentId});
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
                    m.difficulty = c.getDouble(9);
                    m.memoryType = c.getString(10);
                    m.validFrom = c.getLong(11);
                    m.invalidAt = c.getLong(12);
                    m.subject = c.getString(13);
                    m.vector = floats(c.getBlob(14));
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

    private static void invalidateFact(String agentId, long id, long now, String oldText) {
        try {
            ContentValues v = new ContentValues();
            v.put("invalid_at", now);
            db().update("memories", v, "id=?", new String[]{String.valueOf(id)});
            if (oldText != null && !oldText.trim().isEmpty()) {
                scrubSummaryFact(agentId, oldText);
            }
        } catch (Throwable t) {
            FileLog.e("OxMemoryStore", t);
        }
    }

    private static void scrubAllInvalidated(String agentId) {
        try {
            Cursor c = db().rawQuery("SELECT text FROM memories WHERE agent_id=? "
                            + "AND kind='fact' AND invalid_at>0 ORDER BY invalid_at DESC LIMIT 200",
                    new String[]{agentId});
            List<String> texts = new ArrayList<>();
            try {
                while (c.moveToNext()) texts.add(c.getString(0));
            } finally {
                c.close();
            }
            for (String text : texts) scrubSummaryFact(agentId, text);
        } catch (Throwable t) {
            FileLog.e("OxMemoryStore", t);
        }
    }

    /** Removes a superseded fact sentence from active summaries so the digest
     *  does not keep repeating an outdated value. */
    private static void scrubSummaryFact(String agentId, String oldText) {
        String body = oldText.trim();
        int colon = body.indexOf('：');
        if (colon >= 0) body = body.substring(colon + 1).trim();
        body = body.replaceAll("[.。]+$", "").trim().toLowerCase(Locale.ROOT);
        if (body.length() < 3) return;
        try {
            Cursor c = db().rawQuery("SELECT id,text FROM memories WHERE agent_id=? "
                            + "AND kind='summary' AND invalid_at=0",
                    new String[]{agentId});
            List<long[]> updates = new ArrayList<>();
            List<String> texts = new ArrayList<>();
            try {
                while (c.moveToNext()) {
                    String text = c.getString(1);
                    StringBuilder kept = new StringBuilder();
                    boolean removed = false;
                    for (String line : text.split("\n")) {
                        if (line.toLowerCase(Locale.ROOT).contains(body)) {
                            removed = true;
                            continue;
                        }
                        if (kept.length() > 0) kept.append('\n');
                        kept.append(line);
                    }
                    if (removed) {
                        updates.add(new long[]{c.getLong(0)});
                        texts.add(kept.toString().trim());
                    }
                }
            } finally {
                c.close();
            }
            for (int i = 0; i < updates.size(); i++) {
                String text = texts.get(i);
                ContentValues v = new ContentValues();
                if (text.length() < 8) {
                    v.put("invalid_at", System.currentTimeMillis());
                } else {
                    v.put("text", text);
                    v.put("hash", sha256("summary\nsystem\n" + text));
                }
                db().update("memories", v, "id=?",
                        new String[]{String.valueOf(updates.get(i)[0])});
            }
        } catch (Throwable t) {
            FileLog.e("OxMemoryStore", t);
        }
    }

    private static boolean contradicts(String oldText, String newText) {
        if (oldText == null || newText == null) return false;
        String a = oldText.toLowerCase(Locale.ROOT);
        String b = newText.toLowerCase(Locale.ROOT);
        Set<String> na = numbers(a);
        Set<String> nb = numbers(b);
        if (!na.isEmpty() && !nb.isEmpty() && !na.equals(nb)) return true;
        boolean negA = hasNegation(a);
        boolean negB = hasNegation(b);
        return negA != negB;
    }

    private static Set<String> numbers(String text) {
        Set<String> out = new HashSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d{1,8}")
                .matcher(text == null ? "" : text);
        while (m.find()) out.add(m.group());
        return out;
    }

    private static boolean hasNegation(String text) {
        return text.contains("不") || text.contains("没") || text.contains("无")
                || text.contains("not ") || text.contains("never") || text.contains("no ");
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return inter.size() / (double) Math.max(1, union.size());
    }

    private static String subjectKey(String text) {
        String t = text == null ? "" : text.toLowerCase(Locale.ROOT);
        int colon = t.indexOf('：');
        if (colon >= 0) t = t.substring(colon + 1).trim();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:我的|我)([\\u4e00-\\u9fff]{1,12})").matcher(t);
        if (m.find()) return m.group(1);
        m = java.util.regex.Pattern.compile("my\\s+([a-z][a-z ]{1,24})").matcher(t);
        if (m.find()) return trimSubject(m.group(1));
        m = java.util.regex.Pattern.compile("[a-z]+'s\\s+([a-z][a-z ]{1,30})").matcher(t);
        if (m.find()) return trimSubject(m.group(1));
        m = java.util.regex.Pattern.compile(
                "i\\s+(?:like|love|live in|work as|prefer|use|am)\\s+([a-z0-9 ]{1,24})")
                .matcher(t);
        if (m.find()) return trimSubject(m.group(1));
        List<String> tokens = tokenize(t);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size() && i < 3; i++) {
            if (i > 0) sb.append(' ');
            sb.append(tokens.get(i));
        }
        return sb.toString();
    }

    private static String trimSubject(String s) {
        String[] words = s.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length && i < 4; i++) {
            String w = words[i];
            if (STOPWORDS.contains(w) || SUBJECT_STOP.contains(w)) break;
            if (sb.length() > 0) sb.append(' ');
            sb.append(w);
            if (sb.toString().split(" ").length >= 2) break;
        }
        return sb.toString();
    }

    private static final Set<String> SUBJECT_STOP = new HashSet<>(java.util.Arrays.asList(
            "is", "are", "was", "were", "am", "be", "like", "likes", "love", "loves",
            "prefer", "prefers", "use", "uses", "live", "lives", "work", "works",
            "have", "has", "do", "does", "can", "will", "now", "also", "and", "as"));


    private static List<Mem> load(String agentId, int limit) {
        List<Mem> out = new ArrayList<>();
        try {
            Cursor c = db().rawQuery(
                    "SELECT id,kind,role,text,created_at,last_access,access_count,"
                            + "importance,stability,difficulty,memory_type,valid_from,"
                            + "invalid_at,subject,vec FROM memories "
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
                    m.difficulty = c.getDouble(9);
                    m.memoryType = c.getString(10);
                    m.validFrom = c.getLong(11);
                    m.invalidAt = c.getLong(12);
                    m.subject = c.getString(13);
                    m.vector = floats(c.getBlob(14));
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
            double ageDays = Math.max(0, (now - m.lastAccess) / (double) DAY_MS);
            double retention = 1.0 / (1.0 + ageDays / (9.0 * Math.max(0.2, m.stability)));
            double nextStability = Math.min(3650.0,
                    Math.max(0.2, m.stability) * (1.0 + 1.2 * (1.0 - retention)));
            double nextDifficulty = Math.max(1.0, m.difficulty - 0.2);
            boolean promote = "short".equals(m.memoryType)
                    && (m.accessCount + 1 >= 2 || nextStability >= 21 || m.importance >= 0.8);
            ContentValues v = new ContentValues();
            v.put("last_access", now);
            v.put("access_count", m.accessCount + 1);
            v.put("stability", nextStability);
            v.put("difficulty", nextDifficulty);
            if (promote) v.put("memory_type", "long");
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
        String sql = "SELECT COUNT(*) FROM memories WHERE agent_id=? AND invalid_at=0"
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
        Cursor c = db().rawQuery("SELECT COUNT(*) FROM memories WHERE agent_id=? "
                        + "AND invalid_at=0 AND kind=?", new String[]{agentId, kind});
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
        String[] parts = text.split("[。！？!?；;\\n\\.]");
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
