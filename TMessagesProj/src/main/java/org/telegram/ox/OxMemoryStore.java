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
 * normal (keyword/token overlap) and vector (a local hashed n-gram projection —
 * NOT a semantic embedding; the UI labels it 词面混合).
 * Both use a forgetting curve: short-term entries decay quickly, frequently
 * recalled or important entries are promoted to long-term. Context compression
 * writes readable turn notes, extracted facts and a chunk summary here.
 *
 * Stage A (0.78): Markdown (memory.md) is the single source of truth; SQLite is a
 * rebuildable derived index (see OxMemoryOntology + ensureOntology).
 */
public class OxMemoryStore {

    private static final String DB_NAME = "oxmemory.db";
    private static final int DB_VERSION = 3;
    private static final int VEC_DIM = 128;
    private static final int MAX_ROWS_PER_AGENT = 4000;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    public static final String MODE_OFF = "off";
    public static final String MODE_NORMAL = "normal";
    public static final String MODE_VECTOR = "vector";

    // metrics (stage B 6.5): last recall observation for harness/QA inspection
    private static volatile long lastRecallMs = 0;
    private static volatile int lastRecallTokens = 0;
    private static volatile int lastRecallCount = 0;
    private static volatile String lastRecallMode = "";
    private static volatile long lastRecallAt = 0;

    private static Helper helper;
    private static SQLiteDatabase db;

    public static class CompressResult {
        public int ingested;
        public int batchFrom;
        public int batchTo;
        public String previousSummary = "";
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
        String publicId = "";
        String people = "";
        String topics = "";
        String status = "active";
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
            if (oldVersion < 3) {
                // Stage A (0.78): schema v3 — Markdown is the single source of truth.
                d.execSQL("ALTER TABLE memories ADD COLUMN public_id TEXT");
                d.execSQL("ALTER TABLE memories ADD COLUMN people TEXT");
                d.execSQL("ALTER TABLE memories ADD COLUMN topics TEXT");
                d.execSQL("ALTER TABLE memories ADD COLUMN status TEXT NOT NULL DEFAULT 'active'");
                d.execSQL("ALTER TABLE memories ADD COLUMN supersedes TEXT");
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
        long firstTs = 0;
        long lastTs = 0;
        out.previousSummary = latestSummary(agentId);
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
                for (String fact : extractFacts(e.content)) {
                    insert(agentId, "fact", "user", "用户：" + fact, ts,
                            0.9, "long", "extract", true);
                }
            }
        }
        if (ingested > 0) {
            if (target - from >= 4 || countKind(agentId, "summary") == 0) {
                String summary = OxContextCompactor.summarize(
                        history.subList(from, target), out.previousSummary, 300);
                if (summary.isEmpty()) {
                    summary = "较早对话：" + clip(history.get(from).content, 1000);
                }
                if (firstTs > 0) {
                    summary = "对话压缩摘要（" + day(firstTs)
                            + (lastTs > firstTs ? " 至 " + day(lastTs) : "") + "）：\n" + summary;
                }
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

    /** Latest active summary, used as the previous state for recursive compaction. */
    public static synchronized String latestSummary(String agentId) {
        try {
            Cursor c = db().rawQuery("SELECT text FROM memories WHERE agent_id=? "
                            + "AND kind='summary' AND invalid_at=0 "
                            + "ORDER BY created_at DESC LIMIT 1", new String[]{agentId});
            try {
                return c.moveToFirst() ? c.getString(0) : "";
            } finally {
                c.close();
            }
        } catch (Throwable t) {
            return "";
        }
    }

    /** A compact, natural-language view used in the system prompt. */
    public static synchronized String digest(String agentId, int maxChars) {
        if (agentId == null) return "";
        ensureOntology(agentId);
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
        ensureOntology(agentId);
        long t0 = System.currentTimeMillis();
        List<Mem> rows = load(agentId, 3000);
        if (rows.isEmpty()) { recordRecall(t0, mode, 0, 0); return ""; }
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        RecallFilters filters = parseRecallFilters(query == null ? "" : query);
        if (filters.id != null) return recallById(agentId, filters.id, mode);
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
        if (active.isEmpty()) { recordRecall(t0, mode, 0, 0); return ""; }
        applyRecallFilters(active, filters);
        if (active.isEmpty()) { recordRecall(t0, mode, 0, 0); return ""; }
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
            if (filters.exact != null && m.text != null
                    && m.text.toLowerCase(Locale.ROOT).contains(filters.exact)) {
                m.score = 1.5;
            }
        }
        for (java.util.Iterator<Mem> it = active.iterator(); it.hasNext(); ) {
            if (it.next().score <= 0) it.remove();
        }
        if (active.isEmpty()) { recordRecall(t0, mode, 0, 0); return ""; }
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
        String out = sb.toString().trim();
        recordRecall(t0, mode, chosen.size(), out.length() / 4);
        return out;
    }

    /** Last recall observation as JSON (stage B 6.5 metric surface). */
    public static synchronized String metricsJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("last_recall_ms", lastRecallMs);
            o.put("last_recall_tokens", lastRecallTokens);
            o.put("last_recall_count", lastRecallCount);
            o.put("last_recall_mode", lastRecallMode);
            o.put("last_recall_at", lastRecallAt);
        } catch (Throwable t) {
            FileLog.e("OxMemoryStore", t);
        }
        return o.toString();
    }

    private static void recordRecall(long t0, String mode, int count, int tokens) {
        lastRecallMs = System.currentTimeMillis() - t0;
        lastRecallCount = count;
        lastRecallTokens = tokens;
        lastRecallMode = mode == null ? "" : mode;
        lastRecallAt = System.currentTimeMillis();
    }

    // ---------------- stage B 6.2/6.3: precise recall + filters ----------------
    private static class RecallFilters {
        String id;
        String people;
        String topics;
        String status;
        String exact;
        long dateFrom = 0;
        long dateTo = 0;
        long dateExact = 0;
    }

    private static RecallFilters parseRecallFilters(String raw) {
        RecallFilters f = new RecallFilters();
        if (raw == null) return f;
        java.util.regex.Matcher mq =
                java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(raw);
        if (mq.find()) f.exact = mq.group(1).toLowerCase(Locale.ROOT);
        for (String kv : raw.split("\\s+")) {
            int idx = kv.indexOf(':');
            if (idx <= 0) continue;
            String key = kv.substring(0, idx).toLowerCase(Locale.ROOT);
            String val = kv.substring(idx + 1).trim();
            if (val.isEmpty()) continue;
            if (key.equals("id") || key.equals("mem")) f.id = val;
            else if (key.equals("who") || key.equals("people") || key.equals("person")) {
                f.people = val.toLowerCase(Locale.ROOT);
            } else if (key.equals("topic") || key.equals("topics") || key.equals("tag")) {
                f.topics = val.toLowerCase(Locale.ROOT);
            }             else if (key.equals("status")) {
                f.status = val;
            } else if (key.equals("date") || key.equals("on")) {
                f.dateExact = parseLocalDate(val);
            } else if (key.equals("after") || key.equals("since")) {
                f.dateFrom = parseLocalDate(val);
            } else if (key.equals("before") || key.equals("until")) {
                f.dateTo = parseLocalDate(val);
            }
        }
        return f;
    }

    private static void applyRecallFilters(List<Mem> active, RecallFilters f) {
        if (f.people == null && f.topics == null && f.status == null
                && f.dateFrom == 0 && f.dateTo == 0 && f.dateExact == 0) return;
        for (java.util.Iterator<Mem> it = active.iterator(); it.hasNext(); ) {
            Mem m = it.next();
            boolean drop = false;
            if (f.people != null && (m.people == null
                    || !m.people.toLowerCase(Locale.ROOT).contains(f.people))) {
                drop = true;
            } else if (f.topics != null && (m.topics == null
                    || !m.topics.toLowerCase(Locale.ROOT).contains(f.topics))) {
                drop = true;
            } else if (f.status != null && (m.status == null
                    || !m.status.equalsIgnoreCase(f.status))) {
                drop = true;
            }
            if (!drop && f.dateExact > 0 && dayStart(m.createdAt) != f.dateExact) drop = true;
            if (!drop && f.dateFrom > 0 && m.createdAt < f.dateFrom) drop = true;
            if (!drop && f.dateTo > 0 && m.createdAt >= f.dateTo + DAY_MS) drop = true;
            if (drop) it.remove();
        }
    }

    /** Parse a local ISO date (YYYY-MM-DD) to the epoch millis of its start. */
    private static long parseLocalDate(String s) {
        if (s == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d{4})-(\\d{2})-(\\d{2})").matcher(s);
        if (!m.find()) return 0;
        try {
            int y = Integer.parseInt(m.group(1));
            int mo = Integer.parseInt(m.group(2)) - 1;
            int d = Integer.parseInt(m.group(3));
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(y, mo, d, 0, 0, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Start-of-day epoch millis for a timestamp (local timezone). */
    private static long dayStart(long ts) {
        if (ts <= 0) return 0;
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(ts);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static String recallById(String agentId, String id, String mode) {
        long t0 = System.currentTimeMillis();
        List<Mem> rows = load(agentId, 3000);
        for (Mem m : rows) {
            if (id != null && id.equals(m.publicId)) {
                StringBuilder sb = new StringBuilder();
                sb.append("# 记忆 ").append(m.publicId).append(" [").append(m.kind).append("]\n")
                        .append(m.text).append('\n');
                if (m.invalidAt > 0) sb.append("(该记忆条目已失效/被取代，仅供溯源)\n");
                touch(m);
                String out = sb.toString().trim();
                recordRecall(t0, mode, 1, out.length() / 4);
                return out;
            }
        }
        String out = "未在本地记忆库中找到 public_id 为「" + id + "」的记录。";
        recordRecall(t0, mode, 0, 0);
        return out;
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
        return ingestModelMemory(agentId, result, 0);
    }

    /** Stores model output; a cutoff replaces summaries that existed before the
     *  model call so the abstractive summary becomes the rolling summary. */
    public static synchronized int ingestModelMemory(String agentId, JSONObject result,
            long cutoff) {
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
                if (cutoff > 0) {
                    ContentValues old = new ContentValues();
                    old.put("invalid_at", System.currentTimeMillis());
                    db().update("memories", old, "agent_id=? AND kind='summary' "
                                    + "AND invalid_at=0 AND created_at<=?",
                            new String[]{agentId, String.valueOf(cutoff)});
                }
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

    /** Mirrors one external-harness exchange into the local library, read-only.
     *  Stable text hashes make a retried/replayed mirror idempotent. The rows are
     *  never read by the harness; they become available to local recall and can
     *  be injected only if the user switches that agent back to API mode. */
    public static synchronized int ingestHarnessMirror(String agentId, String userText,
            String assistantText, List<String> tools, long ts) {
        if (agentId == null) return 0;
        int attempted = 0;
        long t = ts > 0 ? ts : System.currentTimeMillis();
        if (userText != null && !userText.trim().isEmpty()) {
            String user = clip(userText.trim(), 4000);
            double importance = importance(user, "user");
            insert(agentId, "turn", "user", "用户：" + user, t, importance,
                    importance >= 0.85 ? "long" : "short", "harness-mirror", true);
            attempted++;
            for (String fact : extractFacts(userText)) {
                insert(agentId, "fact", "user", "用户：" + fact, t,
                        0.9, "long", "harness-mirror", true);
            }
        }
        if (assistantText != null && !assistantText.trim().isEmpty()) {
            String reply = clip(assistantText.trim(), 4000);
            double importance = importance(reply, "assistant");
            insert(agentId, "turn", "assistant", "助手：" + reply, t + 1, importance,
                    importance >= 0.85 ? "long" : "short", "harness-mirror", true);
            attempted++;
        }
        if (tools != null) {
            for (String tool : tools) {
                if (tool == null || tool.trim().isEmpty()) continue;
                insert(agentId, "tool", "tool", "工具：" + clip(tool.trim(), 4000),
                        t + 2, 0.45, "short", "harness-mirror-tool", true);
                attempted++;
            }
        }
        prune(agentId);
        return attempted;
    }

    public static synchronized String stats(String agentId) {
        try {
            int all = count(agentId, null);
            int longCount = count(agentId, "long");
            int shortCount = count(agentId, "short");
            int facts = countKind(agentId, "fact");
            int mirror = countSource(agentId, "harness-mirror%");
            String line = all + " 条 · 长期 " + longCount + " · 短期 " + shortCount
                    + " · 事实 " + facts;
            return mirror > 0 ? line + " · 镜像 " + mirror : line;
        } catch (Throwable t) {
            return "记忆库不可用";
        }
    }

    public static synchronized String recent(String agentId, int maxResults) {
        StringBuilder sb = new StringBuilder();
        try {
            Cursor c = db().rawQuery(
                    "SELECT kind,text,created_at,access_count,memory_type,source FROM memories "
                            + "WHERE agent_id=? ORDER BY created_at DESC LIMIT ?",
                    new String[]{agentId, String.valueOf(Math.max(1, maxResults))});
            try {
                while (c.moveToNext()) {
                    String source = c.getString(5);
                    boolean mirrored = source != null && source.startsWith("harness-mirror");
                    sb.append('[').append(day(c.getLong(2))).append(" · ")
                            .append(mirrored ? "镜像" : "").append(kindLabel(c.getString(0)))
                            .append(" · ").append(c.getString(4)).append(" · 访问 ")
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
        ensureOntology(agentId);
        try {
            String normalized = text.trim();
            String subject = "fact".equals(kind) ? subjectKey(normalized) : "";
            if ("fact".equals(kind) && consolidateFact(agentId, normalized, subject,
                    createdAt, importance)) return;
            String hash = source != null && source.startsWith("harness-mirror")
                    ? sha256("mirror\n" + kind + "\n" + role + "\n" + normalized)
                    : sha256(kind + "\n" + role + "\n" + normalized
                            + ("turn".equals(kind) ? "\n" + createdAt : ""));
            // Stage B (0.79): extract people/topics locally so the 6.3 filters
            // operate on real data instead of always-empty columns.
            String people = extractPeople(normalized);
            String topics = extractTopics(normalized);
            // Stage A (0.78): Markdown is the single source of truth. Persist the
            // entry to memory.md first; SQLite stays a rebuildable derived index.
            String publicId = OxMemoryOntology.newPublicId();
            OxMemoryOntology.Entry ont = new OxMemoryOntology.Entry();
            ont.id = publicId;
            ont.createdAt = createdAt;
            ont.type = kind;
            ont.status = "active";
            ont.source = source == null ? "tool" : source;
            ont.contentHash = hash;
            ont.people = people;
            ont.topics = topics;
            // A fact that supersedes a just-invalidated one records the link so
            // the Markdown source of truth carries the version chain.
            String pend = pendingSupersedes.get();
            if (pend != null && "fact".equals(kind)) {
                ont.supersedes = pend;
                pendingSupersedes.remove();
            }
            ont.content = normalized;
            OxMemoryOntology.appendEntry(agentId, ont);
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
            v.put("public_id", publicId);
            v.put("people", people);
            v.put("topics", topics);
            v.put("status", "active");
            v.put("supersedes", "");
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
                if (old.publicId != null && !old.publicId.isEmpty()) {
                    OxMemoryOntology.markSuperseded(agentId, old.publicId);
                    pendingSupersedes.set(old.publicId);
                }
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
                    if (old.publicId != null && !old.publicId.isEmpty()) {
                        OxMemoryOntology.updateEntry(agentId, old.publicId,
                                clip(text, 8000), sha256("fact\nuser\n" + text), now);
                    }
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
                            + "invalid_at,subject,public_id,vec FROM memories "
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
                    m.publicId = c.getString(14);
                    m.vector = floats(c.getBlob(15));
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

    // ---------------- stage B 6.3: people / topics extraction ----------------
    // Lightweight, fully local extraction so the people/topics/status filters
    // (6.3) operate on real data instead of always-empty columns. No network.

    private static final Set<String> SURNAMES = new HashSet<>(java.util.Arrays.asList(
            "王", "李", "张", "刘", "陈", "杨", "黄", "赵", "吴", "周", "徐", "孙", "马", "朱", "胡",
            "郭", "何", "高", "林", "罗", "郑", "梁", "谢", "宋", "唐", "许", "韩", "冯", "邓", "曹",
            "彭", "曾", "萧", "田", "董", "袁", "潘", "于", "蒋", "蔡", "余", "杜", "叶", "程", "苏",
            "魏", "吕", "丁", "任", "沈", "姚", "卢", "傅", "钟", "姜", "崔", "谭", "廖", "范", "汪",
            "陆", "金", "石", "覃", "贾", "夏", "韦", "方", "白", "邹", "孟", "熊", "秦", "邱", "江",
            "尹", "薛", "闫", "段", "雷", "侯", "龙", "史", "陶", "黎", "贺", "顾", "毛", "郝", "龚",
            "邵", "万", "钱", "严", "武", "戴", "莫", "孔", "向", "汤"));

    /** Best-effort person extraction: explicit self/other name cues, Chinese
     *  surname-led 2-4 char runs, and Mr/Ms/Mrs/Dr + Latin proper nouns. */
    private static String extractPeople(String text) {
        if (text == null) return "";
        Set<String> out = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(?:我叫|我是|我找|我联系|我给|我跟|my friend|my colleague|talk to|called|named|with|"
                        + "i am|i'm|name is|my name is|call me)\\s*"
                        + "([\\u4e00-\\u9fff]{1,4}|[A-Z][a-z]{1,20})").matcher(text);
        while (m.find()) {
            String n = m.group(1).trim();
            if (!n.isEmpty()) out.add(n.toLowerCase(Locale.ROOT));
        }
        java.util.regex.Matcher title = java.util.regex.Pattern
                .compile("(?:mr|ms|mrs|dr)\\.?\\s+([A-Z][a-z]{1,20})").matcher(text);
        while (title.find()) out.add(title.group(1).toLowerCase(Locale.ROOT));
        java.util.regex.Matcher cjk = java.util.regex.Pattern
                .compile("[\\u4e00-\\u9fff]{1,4}").matcher(text);
        while (cjk.find()) {
            String run = cjk.group();
            if (run.length() >= 2 && run.length() <= 4
                    && SURNAMES.contains(run.substring(0, 1))) {
                out.add(run);
            }
        }
        if (out.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String p : out) {
            if (sb.length() > 0) sb.append(';');
            sb.append(p);
        }
        return sb.toString();
    }

    private static final Set<String> TOPIC_KEYWORDS = new HashSet<>(java.util.Arrays.asList(
            "生日", "偏好", "习惯", "工作", "项目", "城市", "家人", "宠物", "饮食", "过敏",
            "会议", "旅行", "学习", "健身", "账户", "密码", "手机号", "地址", "纪念日", "学校",
            "birthday", "preference", "habit", "work", "project", "city", "family",
            "pet", "diet", "allergy", "meeting", "travel", "study", "account",
            "password", "phone", "address", "school", "anniversary"));

    /** Topic extraction: reuse subjectKey (subject of a fact) plus any recognized
     *  domain keywords so people/topics/status filters can narrow by theme. */
    private static String extractTopics(String text) {
        if (text == null) return "";
        Set<String> out = new java.util.LinkedHashSet<>();
        String subj = subjectKey(text);
        if (subj != null && !subj.trim().isEmpty()) out.add(subj.trim());
        String lower = text.toLowerCase(Locale.ROOT);
        for (String kw : TOPIC_KEYWORDS) {
            if (lower.contains(kw.toLowerCase(Locale.ROOT))) out.add(kw);
        }
        if (out.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String t : out) {
            if (sb.length() > 0) sb.append(';');
            sb.append(t);
        }
        return sb.toString();
    }


    private static List<Mem> load(String agentId, int limit) {
        List<Mem> out = new ArrayList<>();
        try {
            Cursor c = db().rawQuery(
                    "SELECT id,kind,role,text,created_at,last_access,access_count,"
                            + "importance,stability,difficulty,memory_type,valid_from,"
                    + "invalid_at,subject,vec,public_id,people,topics,status FROM memories "
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
                    m.publicId = c.getString(15) == null ? "" : c.getString(15);
                    m.people = c.getString(16) == null ? "" : c.getString(16);
                    m.topics = c.getString(17) == null ? "" : c.getString(17);
                    m.status = c.getString(18) == null ? "active" : c.getString(18);
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

    private static int countSource(String agentId, String sourceLike) {
        try {
            Cursor c = db().rawQuery("SELECT COUNT(*) FROM memories WHERE agent_id=? "
                            + "AND source LIKE ?",
                    new String[]{agentId, sourceLike});
            try {
                return c.moveToFirst() ? c.getInt(0) : 0;
            } finally {
                c.close();
            }
        } catch (Throwable t) {
            return 0;
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
        if ("tool".equals(kind)) return "工具";
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

    static List<String> tokenize(String text) {
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

    // ---------------- Stage A (0.78): Markdown ontology bridge ----------------

    private static final java.util.Set<String> migratedAgents =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());

    /** Stages the old public_id so the immediately-following fact insert can
     *  record it in supersedes (consolidateFact -> insert, same thread/call). */
    private static final ThreadLocal<String> pendingSupersedes = new ThreadLocal<>();

    /** Lazy one-time migration of legacy SQLite + memory.txt into memory.md. */
    public static synchronized void ensureOntology(String agentId) {
        if (agentId == null || migratedAgents.contains(agentId)) return;
        migratedAgents.add(agentId);
        try {
            OxMemoryOntology.migrateLegacy(agentId);
            OxMemoryOntology.ensureCore(agentId);
        } catch (Throwable t) {
            FileLog.e("OxMemoryStore", t);
        }
    }

    /** Pinned read-always layer content (CORE / ABOUT_USER / ABOUT_AGENT / CURRENT). */
    public static synchronized String coreSection(String agentId, String section) {
        return OxMemoryOntology.readSection(agentId, section);
    }

    /** Rebuild the derived SQLite index from the markdown source of truth. */
    public static synchronized void rebuildFromMarkdown(String agentId) {
        OxMemoryOntology.rebuildIndex(agentId);
    }

    /** Export every legacy row for migration into the ontology (idempotent via hash). */
    synchronized static List<OxMemoryOntology.LegacyRow> exportLegacyRows(String agentId) {
        List<OxMemoryOntology.LegacyRow> out = new ArrayList<>();
        try {
            Cursor c = db().rawQuery(
                    "SELECT kind,role,text,created_at,invalid_at,source,hash FROM memories "
                            + "WHERE agent_id=?", new String[]{agentId});
            try {
                while (c.moveToNext()) {
                    OxMemoryOntology.LegacyRow r = new OxMemoryOntology.LegacyRow();
                    r.kind = c.getString(0);
                    r.role = c.getString(1);
                    r.text = c.getString(2);
                    r.createdAt = c.getLong(3);
                    r.invalidAt = c.getLong(4);
                    r.source = c.getString(5);
                    r.hash = c.getString(6);
                    out.add(r);
                }
            } finally {
                c.close();
            }
        } catch (Throwable t) {
            FileLog.e("OxMemoryStore", t);
        }
        return out;
    }

    /** Replace the agent's derived index with entries from the markdown source.
     *  Forgetting-curve counters (access_count/stability/difficulty) reset to fixed
     *  defaults on rebuild — this behavior is documented in docs/qa-0.78.0.md
     *  (acceptance #5: data attribution is explicit, not silently lost). */
    synchronized static void upsertFromOntology(String agentId,
            List<OxMemoryOntology.Entry> entries) {
        try {
            db().delete("memories", "agent_id=?", new String[]{agentId});
            for (OxMemoryOntology.Entry e : entries) {
                ContentValues v = new ContentValues();
                v.put("agent_id", agentId);
                v.put("kind", e.type);
                v.put("role", "");
                v.put("text", e.content == null ? "" : e.content);
                v.put("created_at", e.createdAt);
                v.put("last_access", e.createdAt);
                v.put("access_count", 0);
                v.put("importance", 0.5);
                v.put("stability", 3.0);
                v.put("difficulty", 5.0);
                v.put("memory_type", "short");
                v.put("valid_from", e.createdAt);
                v.put("invalid_at", "superseded".equals(e.status) ? e.createdAt : 0L);
                v.put("subject", "");
                v.put("source", e.source);
                v.put("hash", e.contentHash);
                v.put("public_id", e.id);
                v.put("people", e.people);
                v.put("topics", e.topics);
                v.put("status", e.status);
                v.put("supersedes", e.supersedes);
                db().insertWithOnConflict("memories", null, v, SQLiteDatabase.CONFLICT_IGNORE);
            }
        } catch (Throwable t) {
            FileLog.e("OxMemoryStore", t);
        }
    }
}
