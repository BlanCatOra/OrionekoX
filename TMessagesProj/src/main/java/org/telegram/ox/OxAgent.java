package org.telegram.ox;

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
