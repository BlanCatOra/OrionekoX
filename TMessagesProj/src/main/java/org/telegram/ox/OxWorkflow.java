package org.telegram.ox;

import org.json.JSONObject;

/** A scheduled prompt that is sent to a local agent on the user's behalf. */
public class OxWorkflow {

    public String id = "";
    public String name = "";
    public String agentId = "";
    public String prompt = "";
    public boolean enabled;
    /** 0 = manual only; otherwise the repeat interval in minutes. */
    public int intervalMinutes;
    /** Minutes after midnight for a daily wall-clock run, or -1 when not used. */
    public int dailyMinute = -1;
    public long lastRunMs;
    public long nextRunMs;
    public String lastResult = "";
    /** Newest-last run log, capped at MAX_RUNS entries: {ts, ok, text}. */
    public org.json.JSONArray runs = new org.json.JSONArray();

    private static final int MAX_RUNS = 10;

    public OxWorkflow() {
    }

    public OxWorkflow(String id, String name, String agentId, String prompt) {
        this.id = id;
        this.name = name;
        this.agentId = agentId;
        this.prompt = prompt;
    }

    public boolean isScheduled() {
        return enabled && (intervalMinutes > 0 || dailyMinute >= 0);
    }

    public boolean isDaily() {
        return dailyMinute >= 0;
    }

    public void addRun(long ts, boolean ok, String text) {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("ts", ts);
            o.put("ok", ok);
            o.put("text", text == null ? "" : text);
            runs.put(o);
            while (runs.length() > MAX_RUNS) runs.remove(0);
        } catch (Exception ignored) {
        }
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("name", name);
            o.put("agent_id", agentId);
            o.put("prompt", prompt);
            o.put("enabled", enabled);
            o.put("interval_minutes", intervalMinutes);
            o.put("daily_minute", dailyMinute);
            o.put("last_run", lastRunMs);
            o.put("next_run", nextRunMs);
            o.put("last_result", lastResult == null ? "" : lastResult);
            o.put("runs", runs == null ? new org.json.JSONArray() : runs);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static OxWorkflow fromJson(JSONObject o) {
        OxWorkflow w = new OxWorkflow();
        w.id = o.optString("id", "");
        w.name = o.optString("name", "");
        w.agentId = o.optString("agent_id", "");
        w.prompt = o.optString("prompt", "");
        w.enabled = o.optBoolean("enabled", false);
        w.intervalMinutes = o.optInt("interval_minutes", 0);
        w.dailyMinute = o.optInt("daily_minute", -1);
        if (w.dailyMinute < 0 || w.dailyMinute > 1439) w.dailyMinute = -1;
        w.lastRunMs = o.optLong("last_run", 0);
        w.nextRunMs = o.optLong("next_run", 0);
        w.lastResult = o.optString("last_result", "");
        w.runs = o.optJSONArray("runs");
        if (w.runs == null) w.runs = new org.json.JSONArray();
        return w;
    }
}
