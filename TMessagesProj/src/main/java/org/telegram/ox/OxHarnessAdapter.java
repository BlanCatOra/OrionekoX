package org.telegram.ox;

import org.telegram.messenger.FileLog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes context work to the owner of each agent. API agents use OrionekoX's
 * local compact/memory; OxLinux and external harnesses keep their own context
 * and must run their own /compact. This class only invokes a configured command
 * through the OxLinux container; other transports (SSH/ACP/Gateway) are not
 * fabricated here and report "transport-not-connected".
 */
public class OxHarnessAdapter {

    public static final String API = "api";
    public static final String OXLINUX = "oxlinux";
    public static final String CLAUDE = "claude";
    public static final String OPENCODE = "opencode";
    public static final String COMMANDCODE = "commandcode";
    public static final String OPENCLAW = "openclaw";
    public static final String HERMES = "hermes";
    public static final String CODEBUDDY = "codebuddy";
    public static final String DSH = "dsh";
    public static final String PI = "pi";
    public static final String DEVICE = "device";
    public static final String GENERIC = "generic";

    private static final Map<String, Capabilities> CAPS = new LinkedHashMap<>();
    private static final Map<String, Integer> LAST_COMPACT = new ConcurrentHashMap<>();

    public static class Capabilities {
        public final String id;
        public final String label;
        public final boolean localCompact;
        public final boolean harnessCompact;
        public final boolean resume;
        public final String toolProtocol;
        public final boolean multimodal;
        public final boolean nonTextEvents;
        public final String note;

        Capabilities(String id, String label, boolean localCompact, boolean harnessCompact,
                boolean resume, String toolProtocol, boolean multimodal,
                boolean nonTextEvents, String note) {
            this.id = id;
            this.label = label;
            this.localCompact = localCompact;
            this.harnessCompact = harnessCompact;
            this.resume = resume;
            this.toolProtocol = toolProtocol;
            this.multimodal = multimodal;
            this.nonTextEvents = nonTextEvents;
            this.note = note;
        }
    }

    static {
        add(new Capabilities(API, "API 直连", true, false, false,
                "OpenAI tools", true, false, "本地 compact 与记忆库；图片以 inline base64 发送"));
        add(new Capabilities(OXLINUX, "OxLinux", false, true, true,
                "harness 自身", true, true, "容器内常驻 /bin/sh 会话；compact 模板由用户配置"));
        add(new Capabilities(CLAUDE, "Claude Code", false, true, true,
                "MCP / ACP", true, true, "桥接用 print-session；支持 /compact，Provider 可能拒绝鉴权"));
        add(new Capabilities(OPENCODE, "OpenCode", false, true, true,
                "MCP / OpenAPI", true, true, "bridging run --session；compact 走 HTTP session API（已验证）"));
        add(new Capabilities(COMMANDCODE, "CommandCode", false, true, true,
                "MCP / ACP", true, true, "NDJSON + --resume；原生 compact 走交互 PTY（已验证）"));
        add(new Capabilities(OPENCLAW, "OpenClaw", false, true, true,
                "Gateway / ACP", true, true, "Gateway session + sessions.compact（已验证）"));
        add(new Capabilities(HERMES, "Hermes", false, false, false,
                "待确认", false, true, "需自定义 compact 命令"));
        add(new Capabilities(CODEBUDDY, "CodeBuddy", false, false, false,
                "待确认", false, true, "需自定义 compact 命令"));
        add(new Capabilities(DSH, "DeepSeek Harness", false, false, false,
                "待确认", false, true, "需自定义 compact 命令"));
        add(new Capabilities(PI, "Pi", false, false, false,
                "待确认", false, true, "需自定义 compact 命令"));
        add(new Capabilities(DEVICE, "其他设备 Agent", false, true, true,
                "SSH/自定义", true, true, "需要设备传输层；当前只保留命令模板"));
        add(new Capabilities(GENERIC, "通用 Harness", false, false, false,
                "自定义", false, true, "用户提供 compact 命令"));
    }

    private static void add(Capabilities c) {
        CAPS.put(c.id, c);
    }

    public static boolean isLocalApi(OxAgent agent) {
        return agent == null || agent.harness == null || agent.harness.isEmpty()
                || API.equals(agent.harness);
    }

    public static String label(String id) {
        Capabilities c = CAPS.get(id == null ? API : id);
        return c == null ? (id == null ? "API 直连" : id) : c.label;
    }

    public static Capabilities capabilities(String id) {
        return CAPS.get(id == null ? API : id);
    }

    public static Set<String> ids() {
        return CAPS.keySet();
    }

    /** No default compact template is shipped. The four bridged harnesses run
     *  their own compact through the bridge; OxLinux/generic users must enter a
     *  command that they verified on their device. */
    public static String defaultCompactCommand(String harness) {
        return "";
    }

    /** Runs once per threshold window, off the chat thread. */
    public static void maybeCompact(final OxAgent agent, final List<OxChatEntry> history,
            int threshold) {
        if (agent == null || isLocalApi(agent) || history == null) return;
        if (history.size() <= Math.max(1, threshold)) return;
        int last = LAST_COMPACT.getOrDefault(agent.id, 0);
        if (history.size() - last < Math.max(4, threshold / 2)) return;
        LAST_COMPACT.put(agent.id, history.size());
        new Thread(() -> {
            String r = requestCompact(agent);
            FileLog.d("OxHarnessAdapter compact " + agent.harness + " -> " + r);
        }, "ox-harness-compact").start();
    }

    public static String requestCompact(OxAgent agent) {
        if (agent == null || isLocalApi(agent)) return "local-compact";
        // A configured bridge owns the native compact for its four harnesses;
        // no local command template is involved.
        if (OxHarnessTransport.supports(agent.harness)
                && OxHarnessTransport.isConfigured(agent)) {
            return OxHarnessTransport.compact(agent);
        }
        String cmd = agent.harnessCompactCommand == null
                ? "" : agent.harnessCompactCommand.trim();
        if (cmd.isEmpty()) cmd = defaultCompactCommand(agent.harness);
        if (cmd.isEmpty()) return "no compact command configured";
        String session = agent.harnessSession == null ? "" : agent.harnessSession;
        cmd = cmd.replace("{session}", shellQuote(session)).replace("{cwd}", ".");
        if (OXLINUX.equals(agent.harness)) {
            return OxContainerManager.runPersistent(cmd, 120_000);
        }
        return "transport-not-connected";
    }

    private static String shellQuote(String s) {
        if (s == null || s.isEmpty()) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
