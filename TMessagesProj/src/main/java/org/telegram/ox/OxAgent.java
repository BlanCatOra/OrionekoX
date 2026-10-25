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
    public String avatarPath; // master copy of the custom avatar image (may be null)
    public String providerId = "custom"; // OxProviderPreset id, for the editor only
    /** Custom request headers: [["X-Header","value"], ...]. Applied after defaults. */
    public JSONArray headers = new JSONArray();
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
        a.avatarPath = o.optString("avatar_path", "");
        a.providerId = o.optString("provider_id", "custom");
        JSONArray h = o.optJSONArray("headers");
        a.headers = h == null ? new JSONArray() : h;
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
        o.put("avatar_path", avatarPath == null ? "" : avatarPath);
        o.put("provider_id", providerId == null ? "custom" : providerId);
        o.put("headers", headers == null ? new JSONArray() : headers);
        o.put("tools_policy", toolsPolicy);
        return o;
    }

    public String toolPolicy(OxTool tool) {
        String p = toolsPolicy.optString(tool.name(), null);
        return p == null || p.isEmpty() ? tool.defaultPolicy() : p;
    }

    // Echo mode only when no backend at all - local backends (Ollama & friends) need no key.
    public boolean isEchoMode() {
        return baseUrl == null || baseUrl.trim().isEmpty();
    }

    public String statusLine() {
        if (isEchoMode()) {
            return "offline · echo mode";
        }
        return model == null || model.isEmpty() ? baseUrl : model + " · " + baseUrl;
    }
}
