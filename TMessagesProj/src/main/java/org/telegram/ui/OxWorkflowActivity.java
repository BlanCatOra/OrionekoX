package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ox.OxAgent;
import org.telegram.ox.OxAgentStore;
import org.telegram.ox.OxWorkflow;
import org.telegram.ox.OxWorkflowScheduler;
import org.telegram.ox.OxWorkflowService;
import org.telegram.ox.OxWorkflowStore;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Scheduled agent prompts, rendered with Telegram's native settings list. */
@SuppressLint("ViewConstructor")
public class OxWorkflowActivity extends BaseFragment {

    private static final int ID_ADD = 1;
    private static final int WORK_BASE = 1000;
    private static final int[] INTERVALS = {0, 15, 60, 360, 1440};

    private UniversalRecyclerView listView;
    private static java.lang.ref.WeakReference<OxWorkflowActivity> visible =
            new java.lang.ref.WeakReference<>(null);

    /** Called by the runner when a workflow finishes while this screen is open. */
    public static void notifyChanged() {
        final OxWorkflowActivity activity = visible.get();
        if (activity != null) {
            AndroidUtilities.runOnUIThread(activity::rebuild);
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(LocaleController.getString(R.string.OxWorkflows));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        listView.adapter.setApplyBackground(false);
        listView.setSections();
        listView.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        listView.setClipToPadding(false);
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        fragmentView = root;
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        visible = new java.lang.ref.WeakReference<>(this);
        rebuild();
    }

    @Override
    public void onPause() {
        if (visible.get() == this) visible = new java.lang.ref.WeakReference<>(null);
        super.onPause();
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        List<OxWorkflow> workflows = OxWorkflowStore.list();
        items.add(UItem.asHeader(LocaleController.getString(R.string.OxWorkflows)));
        if (workflows.isEmpty()) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.OxWorkflowsEmpty) + "\n"
                    + LocaleController.getString(R.string.OxWorkflowsInfo)));
        } else {
            for (int i = 0; i < workflows.size(); i++) {
                OxWorkflow w = workflows.get(i);
                items.add(SettingsActivity.SettingCell.Factory.of(WORK_BASE + i, 0xFF7C5CFF, 0xFF5B3FE0,
                        R.drawable.outline_message_time_24, w.name, subtitle(w), status(w)));
            }
            items.add(UItem.asShadow(LocaleController.getString(R.string.OxWorkflowTapHint)));
            if (listView != null && !OxWorkflowScheduler.isExactAllowed(listView.getContext())) {
                items.add(UItem.asShadow(LocaleController.getString(R.string.OxWorkflowInexactHint)));
            }
        }
        items.add(SettingsActivity.SettingCell.Factory.of(ID_ADD, 0, 0,
                0, "＋ " + LocaleController.getString(R.string.OxWorkflowNew)));
        items.add(UItem.asShadow(null));
    }

    private String subtitle(OxWorkflow w) {
        OxAgent agent = OxAgentStore.loadAgent(w.agentId);
        String agentName = agent == null ? LocaleController.getString(R.string.OxWorkflowAgentMissingShort)
                : agent.name;
        StringBuilder sb = new StringBuilder(agentName);
        sb.append(" · ").append(scheduleLabel(w));
        if (w.enabled && w.nextRunMs > 0) {
            sb.append(" · ").append(LocaleController.formatString("OxWorkflowNext",
                    R.string.OxWorkflowNext, formatTime(w.nextRunMs)));
        }
        if (w.lastResult != null && !w.lastResult.isEmpty()) {
            String last = w.lastResult.length() > 80 ? w.lastResult.substring(0, 80) + "…" : w.lastResult;
            sb.append('\n').append(LocaleController.formatString("OxWorkflowLast",
                    R.string.OxWorkflowLast, last));
        }
        return sb.toString();
    }

    private String status(OxWorkflow w) {
        if (!w.isDaily() && w.intervalMinutes <= 0) {
            return LocaleController.getString(R.string.OxWorkflowManual);
        }
        return LocaleController.getString(w.enabled ? R.string.OxWorkflowEnabled : R.string.OxWorkflowPaused);
    }

    private String scheduleLabel(OxWorkflow w) {
        if (w.isDaily()) {
            return LocaleController.formatString("OxWorkflowDailyAt", R.string.OxWorkflowDailyAt,
                    formatMinute(w.dailyMinute));
        }
        return intervalLabel(w.intervalMinutes);
    }

    private String formatMinute(int minuteOfDay) {
        return String.format(Locale.getDefault(), "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }

    private String intervalLabel(int minutes) {
        switch (minutes) {
            case 15: return LocaleController.getString(R.string.OxWorkflowEvery15);
            case 60: return LocaleController.getString(R.string.OxWorkflowHourly);
            case 360: return LocaleController.getString(R.string.OxWorkflowEvery6h);
            case 1440: return LocaleController.getString(R.string.OxWorkflowDaily);
            default: return LocaleController.getString(R.string.OxWorkflowManual);
        }
    }

    private String formatTime(long ms) {
        try {
            java.util.Calendar now = java.util.Calendar.getInstance();
            java.util.Calendar then = java.util.Calendar.getInstance();
            then.setTimeInMillis(ms);
            boolean today = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
                    && now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR);
            String pattern = today ? "HH:mm" : "MM-dd HH:mm";
            return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(ms));
        } catch (Throwable t) {
            return "";
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ADD) {
            showEditor(null);
            return;
        }
        if (item.id >= WORK_BASE) {
            int index = item.id - WORK_BASE;
            List<OxWorkflow> workflows = OxWorkflowStore.list();
            if (index >= 0 && index < workflows.size()) showEditor(workflows.get(index));
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id < WORK_BASE) return false;
        int index = item.id - WORK_BASE;
        List<OxWorkflow> workflows = OxWorkflowStore.list();
        if (index < 0 || index >= workflows.size()) return false;
        showMenu(workflows.get(index));
        return true;
    }

    private void showMenu(OxWorkflow w) {
        if (getParentActivity() == null) return;
        ArrayList<CharSequence> items = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        items.add(LocaleController.getString(R.string.OxWorkflowRunNow));
        actions.add(() -> runNow(w));
        items.add(LocaleController.getString(R.string.OxWorkflowDuplicate));
        actions.add(() -> duplicateWorkflow(w));
        items.add(LocaleController.getString(R.string.OxWorkflowHistory));
        actions.add(() -> showHistory(w));
        items.add(LocaleController.getString(R.string.Edit));
        actions.add(() -> showEditor(w));
        if (w.isDaily() || w.intervalMinutes > 0) {
            items.add(LocaleController.getString(w.enabled ? R.string.OxWorkflowPause : R.string.OxWorkflowEnable));
            actions.add(() -> {
                w.enabled = !w.enabled;
                w.nextRunMs = 0;
                OxWorkflowScheduler.schedule(w);
                rebuild();
            });
        }
        items.add(LocaleController.getString(R.string.Delete));
        actions.add(() -> {
            OxWorkflowScheduler.cancel(w.id);
            OxWorkflowStore.delete(w.id);
            rebuild();
        });
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(w.name);
        builder.setItems(items.toArray(new CharSequence[0]), (d, which) -> {
            if (which >= 0 && which < actions.size()) actions.get(which).run();
        });
        builder.show();
    }

    private void duplicateWorkflow(OxWorkflow w) {
        OxWorkflow copy = new OxWorkflow();
        copy.id = "wf" + UUID.randomUUID().toString().substring(0, 6);
        copy.name = w.name + " · " + LocaleController.getString(R.string.OxAgentCopySuffix);
        copy.agentId = w.agentId;
        copy.prompt = w.prompt;
        copy.intervalMinutes = w.intervalMinutes;
        copy.dailyMinute = w.dailyMinute;
        // Copies start paused so a duplicate does not silently double the API usage.
        copy.enabled = false;
        OxWorkflowStore.save(copy);
        OxWorkflowScheduler.schedule(copy);
        toast(LocaleController.formatString("OxWorkflowDuplicated",
                R.string.OxWorkflowDuplicated, copy.name));
        rebuild();
    }

    private void showHistory(OxWorkflow w) {
        if (getParentActivity() == null) return;
        if (w.runs == null || w.runs.length() == 0) {
            toast(LocaleController.getString(R.string.OxWorkflowHistoryEmpty));
            return;
        }
        ArrayList<CharSequence> items = new ArrayList<>();
        ArrayList<String> bodies = new ArrayList<>();
        for (int i = w.runs.length() - 1; i >= 0; i--) {
            org.json.JSONObject o = w.runs.optJSONObject(i);
            if (o == null) continue;
            long ts = o.optLong("ts");
            boolean ok = o.optBoolean("ok");
            String text = o.optString("text", "");
            String head = formatTimestamp(ts) + " · "
                    + LocaleController.getString(ok ? R.string.OxWorkflowRunOk : R.string.OxWorkflowRunFailed);
            String oneLine = text.replace('\n', ' ').replace('\r', ' ').trim();
            if (oneLine.length() > 60) oneLine = oneLine.substring(0, 60) + "…";
            items.add(head + " · " + oneLine);
            bodies.add(head + "\n\n" + text);
        }
        if (items.isEmpty()) {
            toast(LocaleController.getString(R.string.OxWorkflowHistoryEmpty));
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.OxWorkflowHistory));
        builder.setItems(items.toArray(new CharSequence[0]), (d, which) ->
                new AlertDialog.Builder(getParentActivity())
                        .setTitle(LocaleController.getString(R.string.OxWorkflowHistory))
                        .setMessage(bodies.get(which))
                        .setPositiveButton(LocaleController.getString(R.string.OK), null)
                        .show());
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    private String formatTimestamp(long ms) {
        try {
            return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(ms));
        } catch (Throwable t) {
            return "";
        }
    }

    private void runNow(OxWorkflow w) {        if (getParentActivity() == null) return;
        if (OxAgentStore.loadAgent(w.agentId) == null) {
            toast(LocaleController.getString(R.string.OxWorkflowAgentMissing));
            return;
        }
        try {
            Intent svc = new Intent(getParentActivity(), OxWorkflowService.class)
                    .setAction(OxWorkflowScheduler.ACTION_RUN)
                    .putExtra(OxWorkflowScheduler.EXTRA_ID, w.id);
            ContextCompat.startForegroundService(getParentActivity(), svc);
            toast(LocaleController.formatString("OxWorkflowRunning", R.string.OxWorkflowRunning, w.name));
            // The service writes lastResult when the reply lands; refresh twice so a
            // manual run visibly updates the row without leaving the screen.
            AndroidUtilities.runOnUIThread(this::rebuild, 2500);
            AndroidUtilities.runOnUIThread(this::rebuild, 7000);
        } catch (Throwable t) {
            toast(LocaleController.getString(R.string.OxWorkflowSendFailed));
        }
    }

    private void showEditor(OxWorkflow existing) {
        if (getParentActivity() == null) return;
        final List<OxAgent> agents = OxAgentStore.listAgents();
        if (agents.isEmpty()) {
            toast(LocaleController.getString(R.string.OxWorkflowNoAgents));
            return;
        }
        final boolean isNew = existing == null;
        final OxWorkflow draft = new OxWorkflow();
        if (!isNew) {
            draft.id = existing.id;
            draft.name = existing.name;
            draft.agentId = existing.agentId;
            draft.prompt = existing.prompt;
            draft.enabled = existing.enabled;
            draft.intervalMinutes = existing.intervalMinutes;
            draft.dailyMinute = existing.dailyMinute;
            draft.lastRunMs = existing.lastRunMs;
            draft.nextRunMs = existing.nextRunMs;
            draft.lastResult = existing.lastResult;
        }
        if (draft.agentId == null || draft.agentId.isEmpty()) draft.agentId = agents.get(0).id;

        Context ctx = getParentActivity();
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(24);
        wrap.setPadding(pad, AndroidUtilities.dp(4), pad, 0);

        final EditText name = new EditText(ctx);
        name.setHint(LocaleController.getString(R.string.OxWorkflowNameHint));
        name.setSingleLine(true);
        name.setText(draft.name);
        wrap.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final EditText prompt = new EditText(ctx);
        prompt.setHint(LocaleController.getString(R.string.OxWorkflowPromptHint));
        prompt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        prompt.setMinLines(3);
        prompt.setMaxLines(7);
        prompt.setGravity(Gravity.TOP | Gravity.LEFT);
        prompt.setText(draft.prompt);
        wrap.addView(prompt, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 4));

        final TextView agentRow = pickerRow(ctx,
                LocaleController.getString(R.string.OxWorkflowAgentLabel), agentName(draft.agentId));
        wrap.addView(agentRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        final TextView scheduleRow = pickerRow(ctx,
                LocaleController.getString(R.string.OxWorkflowScheduleLabel), scheduleLabel(draft));
        wrap.addView(scheduleRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(LocaleController.getString(isNew ? R.string.OxWorkflowNew : R.string.OxWorkflowEditTitle));
        builder.setView(wrap);
        builder.setPositiveButton(LocaleController.getString(R.string.Save), null);
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        final AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            String p = prompt.getText().toString().trim();
            if (n.isEmpty() || p.isEmpty()) {
                toast(LocaleController.getString(R.string.OxWorkflowMissingFields));
                return;
            }
            draft.name = n;
            draft.prompt = p;
            if (!isNew && (!draft.agentId.equals(existing.agentId) || !p.equals(existing.prompt))) {
                // The previous reply belongs to the old agent/prompt, not this configuration.
                draft.lastResult = "";
                draft.lastRunMs = 0;
            }
            if (isNew) {
                draft.id = "wf" + UUID.randomUUID().toString().substring(0, 6);
                draft.enabled = draft.isDaily() || draft.intervalMinutes > 0;
            } else if (!draft.isDaily() && draft.intervalMinutes <= 0) {
                draft.enabled = false;
            }
            draft.nextRunMs = 0;
            OxWorkflowStore.save(draft);
            OxWorkflowScheduler.schedule(draft);
            toast(LocaleController.getString(R.string.OxWorkflowSaved));
            dialog.dismiss();
            rebuild();
        });

        agentRow.setOnClickListener(v -> {
            ArrayList<CharSequence> items = new ArrayList<>();
            for (int i = 0; i < agents.size(); i++) {
                OxAgent a = agents.get(i);
                String label = a.remark == null || a.remark.trim().isEmpty()
                        ? a.name : a.name + " · " + a.remark;
                if (a.id.equals(draft.agentId)) label = "✓ " + label;
                items.add(label);
            }
            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(LocaleController.getString(R.string.OxWorkflowAgentLabel));
            b.setItems(items.toArray(new CharSequence[0]), (d, which) -> {
                draft.agentId = agents.get(which).id;
                agentRow.setText(LocaleController.getString(R.string.OxWorkflowAgentLabel)
                        + "\n" + agentName(draft.agentId));
            });
            b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            b.show();
        });
        scheduleRow.setOnClickListener(v -> {
            ArrayList<CharSequence> items = new ArrayList<>();
            for (int i = 0; i < INTERVALS.length; i++) {
                String label = intervalLabel(INTERVALS[i]);
                if (!draft.isDaily() && INTERVALS[i] == draft.intervalMinutes) label = "✓ " + label;
                items.add(label);
            }
            final int dailyIndex = items.size();
            String dailyLabel = LocaleController.getString(R.string.OxWorkflowDailyAtOption);
            if (draft.isDaily()) dailyLabel = "✓ " + dailyLabel;
            items.add(dailyLabel);
            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(LocaleController.getString(R.string.OxWorkflowScheduleLabel));
            b.setItems(items.toArray(new CharSequence[0]), (d, which) -> {
                if (which == dailyIndex) {
                    int initial = draft.isDaily() ? draft.dailyMinute : 9 * 60;
                    AlertsCreator.createTimePickerDialog(ctx,
                            LocaleController.getString(R.string.OxWorkflowPickTime),
                            initial, 0, 1439, time -> {
                                draft.dailyMinute = time;
                                draft.intervalMinutes = 0;
                                scheduleRow.setText(LocaleController.getString(R.string.OxWorkflowScheduleLabel)
                                        + "\n" + scheduleLabel(draft));
                            });
                    return;
                }
                draft.dailyMinute = -1;
                draft.intervalMinutes = INTERVALS[which];
                scheduleRow.setText(LocaleController.getString(R.string.OxWorkflowScheduleLabel)
                        + "\n" + scheduleLabel(draft));
            });
            b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            b.show();
        });
    }

    private String agentName(String agentId) {
        OxAgent a = OxAgentStore.loadAgent(agentId);
        return a == null ? LocaleController.getString(R.string.OxWorkflowAgentMissingShort) : a.name;
    }

    private TextView pickerRow(Context ctx, String label, String value) {
        TextView tv = new TextView(ctx);
        tv.setText(label + "\n" + value);
        tv.setTextSize(15);
        tv.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        tv.setLineSpacing(AndroidUtilities.dp(2), 1f);
        tv.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        tv.setBackground(Theme.getSelectorDrawable(false));
        return tv;
    }

    private void rebuild() {
        if (listView != null) listView.adapter.update(true);
    }

    private void toast(String s) {
        if (getParentActivity() != null) {
            Toast.makeText(getParentActivity(), s, Toast.LENGTH_SHORT).show();
        }
    }
}
