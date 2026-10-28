package org.telegram.ox;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// OrionekoX agent definition - the machine-readable half of the .oxagent pair
// (AGENTS.md holds the persona/rules, this holds wiring). See docs: agent.json spec v1.
public class OxAgent {

    public static final String SPEC = "1";

    public String id;
    public String name;
    public String emoji;
    public String agentsMd;
    public String baseUrl;   // OpenAI-compatible endpoint root, e.g. https://api.deepseek.com - empty => offline echo mode
    public String apiKey;    // TODO(M3): move into encrypted Vault
    public String model;
    public boolean builtIn;
    public boolean pinned;    // sticks to the top of the agents block in the chat list
    public long tgId;         // synthetic Telegram user id when running in local-account mode (0 = not yet assigned)
    public String remark;     // note line, mirrored into the synthetic user's last name
    public String firstMes = ""; // optional card greeting; used instead of the generic hello
    public String avatarPath; // master copy of the custom avatar image (may be null)
    public String providerId = "custom"; // OxProviderPreset id, for the editor only
    /** Custom request headers: [["X-Header","value"], ...]. Applied after defaults. */
    public JSONArray headers = new JSONArray();
    /** Operit SKILL.md ids enabled for this agent (folder names under Download/Operit/skills). */
    public JSONArray skillIds = new JSONArray();
    public double temperature = 0.7;
    public double topP = 1.0;
    public int maxTokens = 0;       // 0 = let the provider decide
    public int contextLength = 0;   // 0 = auto (roughly last 20 messages)
    public double presencePenalty = 0;
    public double frequencyPenalty = 0;
    public int topK = 0;            // 0 = omit
    public int seed = -1;           // -1 = omit / random
    public String stop = "";        // comma-separated stop sequences
    public String reasoningEffort = "auto"; // auto | low | medium | high
    public String toolChoice = "auto";      // auto | required | none
    public boolean parallelToolCalls = true;
    public String extraBody = "";   // JSON object merged into the request body
    /** off | normal | vector - local memory library recall mode. */
    public String memoryMode = "normal";
    /** Number of chat entries before older context is compressed into memory. */
    public int memoryThreshold = 45;
    /** Maximum number of recalled memory entries injected per turn. */
    public int memoryRecall = 6;
    /** api | oxlinux | claude | opencode | commandcode | openclaw | hermes |
     *  codebuddy | dsh | pi | device | generic. Only api uses our local compact. */
    public String harness = "api";
    /** Session id understood by the external harness (for /compact or resume). */
    public String harnessSession = "";
    /** Optional compact command template; {session} and {cwd} are replaced. */
    public String harnessCompactCommand = "";
    /** Host bridge base URL, e.g. http://10.0.2.2:8765 (external harnesses only). */
    public String harnessEndpoint = "";
    /** Bearer token for the host bridge; wrapped by OxVault at rest. */
    public String harnessToken = "";
    /** Working directory the harness should run in on the bridge host. */
    public String harnessCwd = "";
    /** Optional provider/model override interpreted by the harness itself. */
    public String harnessModel = "";
    /** OpenClaw agent id (optional; other harnesses ignore it). */
    public String harnessAgent = "";
    // tool name -> "auto" | "ask" | "deny"; missing entries fall back to the tool default
    public JSONObject toolsPolicy = new JSONObject();

    public static OxAgent fromJson(JSONObject o) throws JSONException {
        OxAgent a = new OxAgent();
        a.id = o.optString("id");
        a.name = o.optString("name");
        a.emoji = o.optString("emoji", "🤖");
        a.agentsMd = o.optString("agents_md", "");
        a.baseUrl = o.optString("base_url", "");
        a.apiKey = o.optString("api_key", "");
        a.model = o.optString("model", "");
        a.builtIn = o.optBoolean("built_in", false);
        a.pinned = o.optBoolean("pinned", false);
        a.tgId = o.optLong("tg_id", 0);
        a.remark = o.optString("remark", "");
        a.firstMes = o.optString("first_mes", "");
        a.avatarPath = o.optString("avatar_path", "");
        a.providerId = o.optString("provider_id", "custom");
        JSONArray h = o.optJSONArray("headers");
        a.headers = h == null ? new JSONArray() : h;
        JSONArray skills = o.optJSONArray("skill_ids");
        a.skillIds = skills == null ? new JSONArray() : skills;
        a.temperature = o.optDouble("temperature", 0.7);
        a.topP = o.optDouble("top_p", 1.0);
        a.maxTokens = o.optInt("max_tokens", 0);
        a.contextLength = o.optInt("context_length", 0);
        a.presencePenalty = o.optDouble("presence_penalty", 0);
        a.frequencyPenalty = o.optDouble("frequency_penalty", 0);
        a.topK = o.optInt("top_k", 0);
        a.seed = o.optInt("seed", -1);
        a.stop = o.optString("stop", "");
        a.reasoningEffort = o.optString("reasoning_effort", "auto");
        a.toolChoice = o.optString("tool_choice", "auto");
        a.parallelToolCalls = o.optBoolean("parallel_tool_calls", true);
        a.extraBody = o.optString("extra_body", "");
        a.memoryMode = o.optString("memory_mode", "normal");
        a.memoryThreshold = o.optInt("memory_threshold", 45);
        a.memoryRecall = o.optInt("memory_recall", 6);
        a.harness = o.optString("harness", "api");
        a.harnessSession = o.optString("harness_session", "");
        a.harnessCompactCommand = o.optString("harness_compact_command", "");
        a.harnessEndpoint = o.optString("harness_endpoint", "");
        a.harnessToken = o.optString("harness_token", "");
        a.harnessCwd = o.optString("harness_cwd", "");
        a.harnessModel = o.optString("harness_model", "");
        a.harnessAgent = o.optString("harness_agent", "");
        JSONObject tp = o.optJSONObject("tools_policy");
        a.toolsPolicy = tp == null ? new JSONObject() : tp;
        return a;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("spec", SPEC);
        o.put("id", id);
        o.put("name", name);
        o.put("emoji", emoji);
        o.put("agents_md", agentsMd);
        o.put("base_url", baseUrl);
        o.put("api_key", apiKey);
        o.put("model", model);
        o.put("built_in", builtIn);
        o.put("pinned", pinned);
        o.put("tg_id", tgId);
        o.put("remark", remark == null ? "" : remark);
        o.put("first_mes", firstMes == null ? "" : firstMes);
        o.put("avatar_path", avatarPath == null ? "" : avatarPath);
        o.put("provider_id", providerId == null ? "custom" : providerId);
        o.put("headers", headers == null ? new JSONArray() : headers);
        o.put("skill_ids", skillIds == null ? new JSONArray() : skillIds);
        o.put("temperature", temperature);
        o.put("top_p", topP);
        o.put("max_tokens", maxTokens);
        o.put("context_length", contextLength);
        o.put("presence_penalty", presencePenalty);
        o.put("frequency_penalty", frequencyPenalty);
        o.put("top_k", topK);
        o.put("seed", seed);
        o.put("stop", stop == null ? "" : stop);
        o.put("reasoning_effort", reasoningEffort == null ? "auto" : reasoningEffort);
        o.put("tool_choice", toolChoice == null ? "auto" : toolChoice);
        o.put("parallel_tool_calls", parallelToolCalls);
        o.put("extra_body", extraBody == null ? "" : extraBody);
        o.put("memory_mode", memoryMode == null ? "normal" : memoryMode);
        o.put("memory_threshold", memoryThreshold);
        o.put("memory_recall", memoryRecall);
        o.put("harness", harness == null ? "api" : harness);
        o.put("harness_session", harnessSession == null ? "" : harnessSession);
        o.put("harness_compact_command",
                harnessCompactCommand == null ? "" : harnessCompactCommand);
        o.put("harness_endpoint", harnessEndpoint == null ? "" : harnessEndpoint);
        o.put("harness_token", harnessToken == null ? "" : harnessToken);
        o.put("harness_cwd", harnessCwd == null ? "" : harnessCwd);
        o.put("harness_model", harnessModel == null ? "" : harnessModel);
        o.put("harness_agent", harnessAgent == null ? "" : harnessAgent);
        o.put("tools_policy", toolsPolicy);
        return o;
    }

    public String toolPolicy(OxTool tool) {
        String p = toolsPolicy.optString(tool.name(), null);
        return p == null || p.isEmpty() ? tool.defaultPolicy() : p;
    }

    // Echo mode only when no backend at all. External harnesses deliberately
    // have no baseUrl - the host bridge owns the model - so they must never be
    // treated as offline echo mode.
    public boolean isEchoMode() {
        if (!OxHarnessAdapter.isLocalApi(this)) return false;
        return baseUrl == null || baseUrl.trim().isEmpty();
    }

    public String statusLine() {
        if (!OxHarnessAdapter.isLocalApi(this)) {
            String line = OxHarnessAdapter.label(harness);
            if (harnessModel != null && !harnessModel.isEmpty()) line += " · " + harnessModel;
            if (harnessSession != null && !harnessSession.isEmpty()) line += " · " + harnessSession;
            return line;
        }
        if (isEchoMode()) {
            return "offline · echo mode";
        }
        return model == null || model.isEmpty() ? baseUrl : model + " · " + baseUrl;
    }
}
