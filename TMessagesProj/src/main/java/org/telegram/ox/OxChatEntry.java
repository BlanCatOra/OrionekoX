package org.telegram.ox;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

// One turn in an agent conversation. Roles: "system" (AGENTS.md), "user", "assistant".
public class OxChatEntry {

    public String role;
    public String content;
    public long ts;

    public OxChatEntry(String role, String content, long ts) {
        this.role = role;
        this.content = content;
        this.ts = ts;
    }

    public static List<OxChatEntry> fromJsonArray(JSONArray a) throws JSONException {
        List<OxChatEntry> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            out.add(new OxChatEntry(o.optString("role", "user"), o.optString("content"), o.optLong("ts")));
        }
        return out;
    }

    public static JSONArray toJsonArray(List<OxChatEntry> list) throws JSONException {
        JSONArray a = new JSONArray();
        for (OxChatEntry e : list) {
            JSONObject o = new JSONObject();
            o.put("role", e.role);
            o.put("content", e.content);
            o.put("ts", e.ts);
            a.put(o);
        }
        return a;
    }
}
