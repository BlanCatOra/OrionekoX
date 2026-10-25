package org.telegram.ox;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.List;

/** Persists workflows in a private preference file shared by the UI and the scheduler. */
public class OxWorkflowStore {

    private static final String TAG = "OxWorkflowStore";
    private static final String PREFS = "ox_workflows";
    private static final String KEY = "items";

    private static SharedPreferences prefs() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) {
            throw new IllegalStateException("application context is not ready");
        }
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized List<OxWorkflow> list() {
        ArrayList<OxWorkflow> out = new ArrayList<>();
        try {
            String raw = prefs().getString(KEY, "");
            if (raw == null || raw.isEmpty()) return out;
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                OxWorkflow w = OxWorkflow.fromJson(o);
                if (w.id == null || w.id.isEmpty()) continue;
                out.add(w);
            }
        } catch (Exception e) {
            FileLog.e(TAG + " list failed", e);
        }
        return out;
    }

    public static synchronized OxWorkflow get(String id) {
        if (id == null) return null;
        for (OxWorkflow w : list()) {
            if (id.equals(w.id)) return w;
        }
        return null;
    }

    public static synchronized void save(OxWorkflow workflow) {
        if (workflow == null || workflow.id == null || workflow.id.isEmpty()) return;
        List<OxWorkflow> all = list();
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(workflow.id)) {
                all.set(i, workflow);
                replaced = true;
                break;
            }
        }
        if (!replaced) all.add(workflow);
        write(all);
    }

    public static synchronized void delete(String id) {
        if (id == null) return;
        List<OxWorkflow> all = list();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).id.equals(id)) all.remove(i);
        }
        write(all);
    }

    private static void write(List<OxWorkflow> all) {
        try {
            JSONArray arr = new JSONArray();
            for (OxWorkflow w : all) arr.put(w.toJson());
            prefs().edit().putString(KEY, arr.toString()).apply();
        } catch (Exception e) {
            FileLog.e(TAG + " write failed", e);
        }
    }
}
