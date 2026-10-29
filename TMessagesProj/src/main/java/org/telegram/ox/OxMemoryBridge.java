package org.telegram.ox;

/**
 * Harness Memory Bridge (stage B, app 0.79).
 *
 * External harnesses (Codex, etc.) used to receive NO local memory because
 * {@code appendMemoryContext} skipped the local path entirely for non-local
 * APIs. This bridge fixes that: on every harness turn it injects a fixed
 * window of the pinned CORE layers plus a small query-driven recall, and lists
 * the entry points (recall / memory_verify) the harness can call to pull more.
 *
 * Ownership rule: this bridge ONLY supplies memory. Context compaction is left
 * to the harness — callers still invoke {@code OxHarnessAdapter.maybeCompact};
 * we must not fight the harness's own context management.
 */
public final class OxMemoryBridge {

    private OxMemoryBridge() {}

    private static final String[] SECTIONS = {
        "CORE", "ABOUT_USER", "ABOUT_AGENT", "CURRENT"
    };

    /** Builds the harness-side memory context for a single turn. */
    public static String harnessContext(String agentId, String currentMessage) {
        if (agentId == null) return "";
        OxMemoryStore.ensureOntology(agentId);
        StringBuilder sb = new StringBuilder();

        // 1) Pinned always-read layers.
        for (String section : SECTIONS) {
            String body = OxMemoryStore.coreSection(agentId, section);
            if (body == null || body.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("# ").append(section).append("\n").append(body.trim());
        }

        // 2) Query-driven recall against the current message (honest lexical mode).
        String q = currentMessage == null ? "" : currentMessage.trim();
        if (!q.isEmpty()) {
            String recall = OxMemoryStore.recall(agentId, q, 4, OxMemoryStore.MODE_NORMAL);
            if (!recall.isEmpty()) {
                if (sb.length() > 0) sb.append("\n\n");
                sb.append("# 与当前消息相关的记忆（本地，按相关度）\n").append(recall);
            }
        }

        // 3) Entry points so the harness can pull more on demand.
        if (sb.length() > 0) {
            sb.append("\n\n# 记忆访问入口\n调用 recall(查询) 检索更多本地记忆；"
                    + "调用 memory_verify(声明) 核验某条事实是否在本地记忆中有证据；"
                    + "找不到时诚实说没有记录，不要把猜测说成事实。");
        }
        return sb.toString().trim();
    }
}
