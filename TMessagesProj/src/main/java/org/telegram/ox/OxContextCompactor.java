package org.telegram.ox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Offline context compaction inspired by established schemes:
 * - LangChain SummaryBufferMemory: rolling summary + recent buffer.
 * - MemGPT: recursive summaries of evicted messages.
 * - Selective Context: keep the highest-information units under a budget.
 * - TextRank: sentence graph + PageRank for extractive selection.
 *
 * It does not need a model or network; LLMLingua-style token compression would
 * require a bundled BERT/7B model and is intentionally not used here.
 */
public class OxContextCompactor {

    private static final int MAX_SENTENCES = 80;
    private static final double DAMPING = 0.85;
    private static final int ITERATIONS = 20;

    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int cjk = 0;
        int latin = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCjk(c)) cjk++;
            else if (Character.isLetterOrDigit(c)) latin++;
        }
        return cjk + (latin + 3) / 4 + 1;
    }

    public static int estimateTokens(List<OxChatEntry> entries) {
        int n = 0;
        if (entries == null) return 0;
        for (OxChatEntry e : entries) {
            if (e == null || e.content == null) continue;
            n += estimateTokens(e.content) + 3;
        }
        return n;
    }

    /** How many trailing entries fit into a token budget. */
    public static int keepByTokens(List<OxChatEntry> entries, int budgetTokens) {
        if (entries == null || entries.isEmpty()) return 0;
        int used = 0;
        int i = entries.size() - 1;
        for (; i >= 0; i--) {
            OxChatEntry e = entries.get(i);
            int cost = e == null || e.content == null ? 0 : estimateTokens(e.content) + 3;
            if (used + cost > budgetTokens && i < entries.size() - 1) break;
            used += cost;
        }
        return entries.size() - 1 - i;
    }

    /** Builds a rolling extractive summary from the old summary + new batch. */
    public static String summarize(List<OxChatEntry> batch, String previousSummary,
            int maxTokens) {
        StringBuilder raw = new StringBuilder();
        if (previousSummary != null && !previousSummary.trim().isEmpty()) {
            raw.append("此前摘要：").append(previousSummary.trim()).append('\n');
        }
        if (batch != null) {
            for (OxChatEntry e : batch) {
                if (e == null || e.content == null || e.content.trim().isEmpty()) continue;
                raw.append("user".equals(e.role) ? "用户：" : "助手：")
                        .append(e.content.trim().replace('\n', ' ')).append('\n');
            }
        }
        return compress(raw.toString(), maxTokens);
    }

    /** TextRank sentence selection under a token budget. */
    public static String compress(String text, int maxTokens) {
        if (text == null || text.trim().isEmpty()) return "";
        if (estimateTokens(text) <= maxTokens) return text.trim();
        List<String> sentences = dedupe(sentences(text));
        if (sentences.size() <= 2) return clip(text, maxTokens * 4);
        if (sentences.size() > MAX_SENTENCES) {
            sentences = new ArrayList<>(sentences.subList(sentences.size() - MAX_SENTENCES,
                    sentences.size()));
        }
        List<Set<String>> tokens = new ArrayList<>();
        for (String s : sentences) tokens.add(new HashSet<>(OxMemoryStore.tokenize(s)));
        int n = sentences.size();
        double[][] sim = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double v = similarity(tokens.get(i), tokens.get(j));
                sim[i][j] = v;
                sim[j][i] = v;
            }
        }
        double[] score = new double[n];
        for (int i = 0; i < n; i++) score[i] = 1.0 / n;
        for (int it = 0; it < ITERATIONS; it++) {
            double[] next = new double[n];
            for (int i = 0; i < n; i++) next[i] = (1 - DAMPING) / n;
            for (int i = 0; i < n; i++) {
                double sum = 0;
                for (int j = 0; j < n; j++) sum += sim[i][j];
                if (sum <= 0) continue;
                for (int j = 0; j < n; j++) {
                    if (sim[i][j] > 0) next[j] += DAMPING * score[i] * sim[i][j] / sum;
                }
            }
            score = next;
        }
        final double[] ranked = score;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) order.add(i);
        order.sort((a, b) -> Double.compare(ranked[b], ranked[a]));
        List<Integer> chosen = new ArrayList<>();
        int used = 0;
        for (int idx : order) {
            int cost = estimateTokens(sentences.get(idx));
            if (used + cost > maxTokens && !chosen.isEmpty()) continue;
            double maxSim = 0;
            for (int picked : chosen) {
                double simToChosen = similarity(tokens.get(idx), tokens.get(picked));
                if (simToChosen > maxSim) maxSim = simToChosen;
            }
            if (maxSim > 0.55) continue;
            chosen.add(idx);
            used += cost;
            if (used >= maxTokens) break;
        }
        if (chosen.isEmpty()) return clip(text, maxTokens * 4);
        chosen.sort(Integer::compareTo);
        StringBuilder sb = new StringBuilder();
        for (int idx : chosen) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(sentences.get(idx).trim());
        }
        return sb.toString().trim();
    }

    private static List<String> dedupe(List<String> sentences) {
        List<String> out = new ArrayList<>();
        List<Set<String>> tokenSets = new ArrayList<>();
        for (String s : sentences) {
            Set<String> tokens = new HashSet<>(OxMemoryStore.tokenize(s));
            boolean duplicate = false;
            for (Set<String> old : tokenSets) {
                if (similarity(tokens, old) > 0.65) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                out.add(s);
                tokenSets.add(tokens);
            }
        }
        return out;
    }

    private static double similarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        int hit = 0;
        for (String t : a) if (b.contains(t)) hit++;
        return hit / Math.sqrt((double) a.size() * b.size());
    }

    private static List<String> sentences(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(c);
            if (c == '.' || c == '!' || c == '?' || c == '。' || c == '！'
                    || c == '？' || c == ';' || c == '；' || c == '\n') {
                String s = sb.toString().trim();
                if (s.length() >= 4) out.add(s);
                sb.setLength(0);
            }
        }
        String tail = sb.toString().trim();
        if (tail.length() >= 4) out.add(tail);
        return out;
    }

    private static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF)
                || (c >= 0xF900 && c <= 0xFAFF);
    }

    private static String clip(String s, int maxChars) {
        if (s == null) return "";
        if (s.length() <= maxChars) return s;
        int end = maxChars;
        if (end > 0 && Character.isHighSurrogate(s.charAt(end - 1))) end--;
        return s.substring(0, end) + "…";
    }
}
