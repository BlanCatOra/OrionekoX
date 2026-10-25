package org.telegram.ox;

import org.json.JSONObject;

// A callable tool exposed to agents in OpenAI function-calling format.
// Tools run inside the app process; M3 extends this with Shizuku/root channels and MCP.
public abstract class OxTool {

    public static final String POLICY_AUTO = "auto";   // run without asking
    public static final String POLICY_ASK = "ask";     // confirm with the user each time
    public static final String POLICY_DENY = "deny";

    public abstract String name();
    public abstract String description();
    public abstract String defaultPolicy();
    /** JSON Schema for the parameters object (OpenAI "parameters" format). */
    public abstract JSONObject parameters();

    /**
     * Execute the tool. May block (worker thread). Return a human/model readable result string.
     * Throw on failure - the message becomes the tool result so the model can react.
     */
    public abstract String execute(JSONObject args) throws Exception;

    /** Agent-aware execution for tools that need a workspace/memory scope. */
    public String executeWithAgent(JSONObject args, OxAgent agent) throws Exception {
        return execute(args);
    }
}
