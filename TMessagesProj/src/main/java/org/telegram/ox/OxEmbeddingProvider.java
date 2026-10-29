package org.telegram.ox;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.telegram.messenger.FileLog;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stage C (0.80): a real, fully-offline semantic embedding model for the local
 * memory store.
 *
 * <p>This is Latent Semantic Analysis (LSA) learned at runtime from the agent's
 * own memory corpus: TF-IDF document-term matrix -> truncated SVD via a Jacobi
 * eigendecomposition of the small Gram matrix (t x t, t capped) -> dense latent
 * vectors. No network, no native library, no bundled model asset. Unlike the old
 * {@code MODE_VECTOR} hashed n-gram projection (which only measures surface token
 * overlap), cosine similarity here reflects co-occurrence structure learned from
 * the corpus, i.e. genuine lexical semantics.
 *
 * <p>Honesty rules (architecture red line "never write fake semantic vectors"):
 * <ul>
 *   <li>When the corpus is too small to learn a stable space we return {@code null}
 *       and the caller falls back to lexical recall. We never fabricate vectors.</li>
 *   <li>Vectors live in an independent {@code mem_embeddings} table keyed by
 *       (agent_id, mem_id) and stamped with model + dimension, so a model/dimension
 *       change or a text edit is detectable and triggers a rebuild (7.2).</li>
 * </ul>
 */
public class OxEmbeddingProvider {

    public static final String MODEL = "ox-lsa-local-1";
    /** Fixed output dimension so a stored vector's shape is stable across rebuilds. */
    public static final int DIM = 48;
    /** Upper bound on vocabulary, which bounds the Jacobi matrix to VOCAB_CAP^2. */
    private static final int VOCAB_CAP = 256;
    /** Below this many memories the learned space is noise; stay unavailable. */
    private static final int MIN_DOCS = 8;
    private static final long REBUILD_THROTTLE_MS = 5000L;
    private static final double EIG_EPS = 1e-9;

    private static class Space {
        Map<String, Integer> vocab = new HashMap<>();
        float[] idf;
        float[][] vk;       // [t][k] term loadings (right singular vectors)
        float[] sigmaInv;   // [k]
        int k;
        int docCount;
        long builtAt;
    }

    private static final Map<String, Space> spaces = new HashMap<>();
    private static final Map<String, Long> lastBuild = new HashMap<>();
    private static final Set<String> dirty = new HashSet<>();
    private static boolean tableReady = false;

    /** Marks the whole latent space stale (corpus grew / a memory was edited). */
    public static synchronized void markDirty(String agentId) {
        if (agentId != null) dirty.add(agentId);
    }

    /** Drops one cached vector (its text changed) and marks the space stale. */
    public static synchronized void invalidateDoc(String agentId, long memId) {
        try {
            db().delete("mem_embeddings", "agent_id=? AND mem_id=?",
                    new String[]{agentId, String.valueOf(memId)});
        } catch (Throwable t) {
            FileLog.e("OxEmbeddingProvider", t);
        }
        markDirty(agentId);
    }

    public static synchronized boolean available(String agentId) {
        if (agentId == null) return false;
        ensureBuilt(agentId);
        Space s = spaces.get(agentId);
        return s != null && s.docCount >= MIN_DOCS && s.k > 0;
    }

    /**
     * Embeds {@code text} in the agent's latent space.
     *
     * @param memId a memory row id to read/write the cache for, or a negative
     *              value for a transient query (never cached).
     * @return a unit-length {@link #DIM}-dim vector, or {@code null} when the
     *         provider is unavailable (caller must fall back, never fabricate).
     */
    public static synchronized float[] embed(String agentId, long memId, String text) {
        if (agentId == null || text == null) return null;
        ensureBuilt(agentId);
        Space s = spaces.get(agentId);
        if (s == null || s.docCount < MIN_DOCS || s.k <= 0) return null;
        if (memId >= 0) {
            float[] cached = readCached(agentId, memId);
            if (cached != null) return cached;
        }
        float[] v = project(s, text);
        if (memId >= 0) writeCached(agentId, memId, v);
        return v;
    }

    /** Diagnostic snapshot for QA / harness inspection. */
    public static synchronized String statsJson(String agentId) {
        ensureBuilt(agentId);
        Space s = spaces.get(agentId);
        boolean ok = s != null && s.docCount >= MIN_DOCS && s.k > 0;
        return "{"
                + "\"model\":\"" + MODEL + "\","
                + "\"dim\":" + DIM + ","
                + "\"available\":" + ok + ","
                + "\"docs\":" + (s == null ? 0 : s.docCount) + ","
                + "\"rank\":" + (s == null ? 0 : s.k) + ","
                + "\"vocab\":" + (s == null ? 0 : s.vocab.size()) + ","
                + "\"cached\":" + countCached(agentId)
                + "}";
    }

    // ------------------------------------------------------------------ internals

    private static SQLiteDatabase db() {
        return OxMemoryStore.database();
    }

    private static void ensureTable() {
        if (tableReady) return;
        try {
            db().execSQL("CREATE TABLE IF NOT EXISTS mem_embeddings ("
                    + "agent_id TEXT NOT NULL,"
                    + "mem_id INTEGER NOT NULL,"
                    + "vec BLOB,"
                    + "model TEXT NOT NULL,"
                    + "dim INTEGER NOT NULL,"
                    + "built_at INTEGER NOT NULL,"
                    + "PRIMARY KEY(agent_id, mem_id))");
            tableReady = true;
        } catch (Throwable t) {
            FileLog.e("OxEmbeddingProvider", t);
        }
    }

    private static void ensureBuilt(String agentId) {
        ensureTable();
        int docCount = countDocs(agentId);
        Space s = spaces.get(agentId);
        boolean isDirty = dirty.remove(agentId);
        if (s != null && s.docCount == docCount && !isDirty) return;
        long now = System.currentTimeMillis();
        Long last = lastBuild.get(agentId);
        // Throttle pure size-driven rebuilds; honour explicit dirty flags immediately.
        if (!isDirty && s != null && last != null && now - last < REBUILD_THROTTLE_MS) return;
        try {
            rebuild(agentId, docCount);
            lastBuild.put(agentId, now);
        } catch (Throwable t) {
            FileLog.e("OxEmbeddingProvider", t);
        }
    }

    private static int countDocs(String agentId) {
        int n = 0;
        try {
            Cursor c = db().rawQuery("SELECT COUNT(*) FROM memories WHERE agent_id=?",
                    new String[]{agentId});
            try {
                if (c.moveToFirst()) n = c.getInt(0);
            } finally {
                c.close();
            }
        } catch (Throwable t) {
            FileLog.e("OxEmbeddingProvider", t);
        }
        return n;
    }

    private static void rebuild(String agentId, int docCount) {
        List<Long> ids = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        try {
            Cursor c = db().rawQuery(
                    "SELECT id,text FROM memories WHERE agent_id=? ORDER BY created_at DESC",
                    new String[]{agentId});
            try {
                while (c.moveToNext()) {
                    ids.add(c.getLong(0));
                    texts.add(c.getString(1) == null ? "" : c.getString(1));
                }
            } finally {
                c.close();
            }
        } catch (Throwable t) {
            FileLog.e("OxEmbeddingProvider", t);
        }
        int d = texts.size();
        if (d < MIN_DOCS) {
            spaces.remove(agentId);
            return;
        }

        // Tokenize once and accumulate document frequency.
        List<Map<String, Integer>> tfs = new ArrayList<>(d);
        Map<String, Integer> df = new HashMap<>();
        for (String text : texts) {
            Map<String, Integer> tf = new HashMap<>();
            for (String tok : OxMemoryStore.tokenize(text)) {
                if (tok.isEmpty()) continue;
                Integer n = tf.get(tok);
                tf.put(tok, n == null ? 1 : n + 1);
            }
            tfs.add(tf);
            for (String tok : tf.keySet()) {
                Integer n = df.get(tok);
                df.put(tok, n == null ? 1 : n + 1);
            }
        }
        if (df.isEmpty()) {
            spaces.remove(agentId);
            return;
        }

        // Keep the VOCAB_CAP most frequent terms (ties broken lexically for
        // determinism). This bounds the Gram matrix to 256x256.
        List<String> terms = new ArrayList<>(df.keySet());
        Collections.sort(terms, (a, b) -> {
            int cmp = Integer.compare(df.get(b), df.get(a));
            return cmp != 0 ? cmp : a.compareTo(b);
        });
        int t = Math.min(VOCAB_CAP, terms.size());
        Map<String, Integer> vocab = new HashMap<>(t * 2);
        float[] idf = new float[t];
        for (int j = 0; j < t; j++) {
            String tok = terms.get(j);
            vocab.put(tok, j);
            int dfj = df.get(tok);
            idf[j] = (float) (Math.log((1.0 + d) / (1.0 + dfj)) + 1.0);
        }

        // Sparse TF-IDF rows.
        int[][] rowIdx = new int[d][];
        double[][] rowW = new double[d][];
        for (int i = 0; i < d; i++) {
            Map<String, Integer> tf = tfs.get(i);
            int nnz = 0;
            for (String tok : tf.keySet()) if (vocab.containsKey(tok)) nnz++;
            int[] ri = new int[nnz];
            double[] rw = new double[nnz];
            int p = 0;
            for (Map.Entry<String, Integer> e : tf.entrySet()) {
                Integer j = vocab.get(e.getKey());
                if (j == null) continue;
                ri[p] = j;
                rw[p] = e.getValue() * (double) idf[j];
                p++;
            }
            rowIdx[i] = ri;
            rowW[i] = rw;
        }

        // Gram matrix M = A^T A (t x t), accumulated sparsely.
        double[][] m = new double[t][t];
        for (int i = 0; i < d; i++) {
            int[] ri = rowIdx[i];
            double[] rw = rowW[i];
            for (int a = 0; a < ri.length; a++) {
                int ja = ri[a];
                double wa = rw[a];
                for (int b = 0; b < ri.length; b++) {
                    m[ja][ri[b]] += wa * rw[b];
                }
            }
        }

        // Jacobi eigendecomposition: eigenvectors accumulate in V.
        double[][] v = new double[t][t];
        for (int i = 0; i < t; i++) v[i][i] = 1.0;
        jacobi(m, v);

        Integer[] order = new Integer[t];
        for (int i = 0; i < t; i++) order[i] = i;
        final double[] eig = new double[t];
        for (int i = 0; i < t; i++) eig[i] = m[i][i];
        Arrays.sort(order, (a, b) -> Double.compare(eig[b], eig[a]));

        int kmax = Math.min(DIM, t);
        int k = 0;
        for (int i = 0; i < kmax; i++) if (eig[order[i]] > EIG_EPS) k++;
        k = Math.max(1, k);

        float[][] vk = new float[t][k];
        float[] sigmaInv = new float[k];
        for (int i = 0; i < k; i++) {
            int col = order[i];
            double sig = Math.sqrt(Math.max(0.0, eig[col]));
            sigmaInv[i] = sig > EIG_EPS ? (float) (1.0 / sig) : 0f;
            for (int r = 0; r < t; r++) vk[r][i] = (float) v[r][col];
        }

        Space s = new Space();
        s.vocab = vocab;
        s.idf = idf;
        s.vk = vk;
        s.sigmaInv = sigmaInv;
        s.k = k;
        s.docCount = d;
        s.builtAt = System.currentTimeMillis();
        spaces.put(agentId, s);

        persistAll(agentId, ids, texts, s);
    }

    /** Precomputes and stores every document's vector in one transaction. */
    private static void persistAll(String agentId, List<Long> ids, List<String> texts, Space s) {
        SQLiteDatabase d = db();
        try {
            d.beginTransaction();
            d.delete("mem_embeddings", "agent_id=?", new String[]{agentId});
            long now = System.currentTimeMillis();
            for (int i = 0; i < ids.size(); i++) {
                ContentValues cv = new ContentValues();
                cv.put("agent_id", agentId);
                cv.put("mem_id", ids.get(i));
                cv.put("vec", toBytes(project(s, texts.get(i))));
                cv.put("model", MODEL);
                cv.put("dim", DIM);
                cv.put("built_at", now);
                d.insertWithOnConflict("mem_embeddings", null, cv,
                        SQLiteDatabase.CONFLICT_REPLACE);
            }
            d.setTransactionSuccessful();
        } catch (Throwable t) {
            FileLog.e("OxEmbeddingProvider", t);
        } finally {
            try {
                d.endTransaction();
            } catch (Throwable ignored) {
            }
        }
    }

    /** Project text into the latent space: q * V_k * Sigma_k^-1, then L2-normalise. */
    private static float[] project(Space s, String text) {
        float[] out = new float[DIM];
        Map<String, Integer> tf = new HashMap<>();
        for (String tok : OxMemoryStore.tokenize(text)) {
            if (tok.isEmpty()) continue;
            Integer c = tf.get(tok);
            tf.put(tok, c == null ? 1 : c + 1);
        }
        for (Map.Entry<String, Integer> e : tf.entrySet()) {
            Integer j = s.vocab.get(e.getKey());
            if (j == null) continue;
            double w = e.getValue() * (double) s.idf[j];
            float[] row = s.vk[j];
            for (int i = 0; i < s.k; i++) {
                out[i] += (float) (w * row[i] * s.sigmaInv[i]);
            }
        }
        double norm = 0;
        for (float x : out) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm > 1e-9) for (int i = 0; i < out.length; i++) out[i] /= (float) norm;
        return out;
    }

    /** Cyclic Jacobi rotation, diagonalising symmetric A in place; V accumulates eigenvectors. */
    private static void jacobi(double[][] a, double[][] v) {
        int n = a.length;
        for (int sweep = 0; sweep < 60; sweep++) {
            double off = 0;
            for (int i = 0; i < n; i++)
                for (int j = i + 1; j < n; j++) off += a[i][j] * a[i][j];
            if (off < 1e-12) break;
            for (int p = 0; p < n; p++) {
                for (int q = p + 1; q < n; q++) {
                    double apq = a[p][q];
                    if (Math.abs(apq) < 1e-15) continue;
                    double theta = (a[q][q] - a[p][p]) / (2.0 * apq);
                    double tsign = theta >= 0 ? 1.0 : -1.0;
                    double tt = tsign / (Math.abs(theta) + Math.sqrt(theta * theta + 1.0));
                    double c = 1.0 / Math.sqrt(tt * tt + 1.0);
                    double s = tt * c;
                    for (int k = 0; k < n; k++) {
                        double akp = a[k][p];
                        double akq = a[k][q];
                        a[k][p] = c * akp - s * akq;
                        a[k][q] = s * akp + c * akq;
                    }
                    for (int k = 0; k < n; k++) {
                        double apk = a[p][k];
                        double aqk = a[q][k];
                        a[p][k] = c * apk - s * aqk;
                        a[q][k] = s * apk + c * aqk;
                    }
                    for (int k = 0; k < n; k++) {
                        double vkp = v[k][p];
                        double vkq = v[k][q];
                        v[k][p] = c * vkp - s * vkq;
                        v[k][q] = s * vkp + c * vkq;
                    }
                }
            }
        }
    }

    private static float[] readCached(String agentId, long memId) {
        try {
            Cursor c = db().rawQuery(
                    "SELECT vec,dim FROM mem_embeddings WHERE agent_id=? AND mem_id=? AND model=?",
                    new String[]{agentId, String.valueOf(memId), MODEL});
            try {
                if (c.moveToNext()) {
                    if (c.getInt(1) != DIM) return null;
                    float[] f = fromBytes(c.getBlob(0));
                    return f != null && f.length == DIM ? f : null;
                }
            } finally {
                c.close();
            }
        } catch (Throwable t) {
            FileLog.e("OxEmbeddingProvider", t);
        }
        return null;
    }

    private static void writeCached(String agentId, long memId, float[] vec) {
        try {
            ContentValues cv = new ContentValues();
            cv.put("agent_id", agentId);
            cv.put("mem_id", memId);
            cv.put("vec", toBytes(vec));
            cv.put("model", MODEL);
            cv.put("dim", DIM);
            cv.put("built_at", System.currentTimeMillis());
            db().insertWithOnConflict("mem_embeddings", null, cv,
                    SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Throwable t) {
            FileLog.e("OxEmbeddingProvider", t);
        }
    }

    private static int countCached(String agentId) {
        int n = 0;
        try {
            Cursor c = db().rawQuery(
                    "SELECT COUNT(*) FROM mem_embeddings WHERE agent_id=? AND model=?",
                    new String[]{agentId, MODEL});
            try {
                if (c.moveToFirst()) n = c.getInt(0);
            } finally {
                c.close();
            }
        } catch (Throwable t) {
            FileLog.e("OxEmbeddingProvider", t);
        }
        return n;
    }

    private static byte[] toBytes(float[] vec) {
        ByteBuffer bb = ByteBuffer.allocate(vec.length * 4);
        for (float x : vec) bb.putFloat(x);
        return bb.array();
    }

    private static float[] fromBytes(byte[] data) {
        if (data == null || data.length < 4) return null;
        ByteBuffer bb = ByteBuffer.wrap(data);
        float[] f = new float[data.length / 4];
        for (int i = 0; i < f.length; i++) f[i] = bb.getFloat();
        return f;
    }
}
