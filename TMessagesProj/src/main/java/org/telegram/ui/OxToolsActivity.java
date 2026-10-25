package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ox.OxAgent;
import org.telegram.ox.OxAgentStore;
import org.telegram.ox.OxBuiltinTools;
import org.telegram.ox.OxMcpManager;
import org.telegram.ox.OxTool;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Human-readable permission centre: Android grants + per-agent tool policies. */
@SuppressLint("ViewConstructor")
public class OxToolsActivity extends BaseFragment {

    private static final int PERM_REQUEST = 7501;

    private LinearLayout content;
    private OxAgent selectedAgent;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(LocaleController.getString(R.string.OxToolsTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, AndroidUtilities.dp(24));
        scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = root;

        List<OxAgent> agents = OxAgentStore.listAgents();
        if (!agents.isEmpty()) selectedAgent = agents.get(0);
        rebuild();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        rebuild();
    }

    private void rebuild() {
        if (content == null) return;
        content.removeAllViews();
        Context ctx = content.getContext();

        content.addView(header(ctx, LocaleController.getString(R.string.OxToolsPermissions)));
        LinearLayout permGroup = group(ctx);
        addPermissionRow(ctx, permGroup, LocaleController.getString(R.string.OxObPermNormal), normalPermStatus(), () -> {
            if (getParentActivity() != null && Build.VERSION.SDK_INT >= 33
                    && getParentActivity().checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                getParentActivity().requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, PERM_REQUEST);
            } else {
                toast(LocaleController.getString(R.string.OxObPermGranted));
            }
        });
        addPermissionRow(ctx, permGroup, LocaleController.getString(R.string.OxObPermAccessibility), accessibilityStatus(), () -> {
            try {
                if (getParentActivity() != null) getParentActivity().startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Throwable ignored) {
            }
        });
        addPermissionRow(ctx, permGroup, LocaleController.getString(R.string.OxToolsAdb), adbStatus(), () -> {
            try {
                if (getParentActivity() != null) getParentActivity().startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            } catch (Throwable ignored) {
            }
        });
        addPermissionRow(ctx, permGroup, LocaleController.getString(R.string.OxObPermShizuku), shizukuStatus(), () -> {
            try {
                Intent i = getParentActivity() == null ? null : getParentActivity().getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                if (i != null) getParentActivity().startActivity(i);
                else toast(LocaleController.getString(R.string.OxObPermNotInstalled));
            } catch (Throwable ignored) {
            }
        });
        addPermissionRow(ctx, permGroup, LocaleController.getString(R.string.OxObPermAdmin), adminStatus(), () -> {
            try {
                android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager)
                        getParentActivity().getSystemService(Context.DEVICE_POLICY_SERVICE);
                android.content.ComponentName cn = new android.content.ComponentName(
                        getParentActivity(), org.telegram.ox.OxDeviceAdminReceiver.class);
                if (dpm != null && !dpm.isAdminActive(cn)) {
                    Intent i = new Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    i.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn);
                    getParentActivity().startActivity(i);
                }
            } catch (Throwable ignored) {
            }
        });
        addPermissionRow(ctx, permGroup, LocaleController.getString(R.string.OxObPermRoot), rootStatus(), () ->
                toast(LocaleController.getString(R.string.OxObPermRootHint)));
        content.addView(permGroup, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        content.addView(header(ctx, LocaleController.getString(R.string.OxToolsAgentTools)));
        LinearLayout toolGroup = group(ctx);
        TextSettingsCell selector = new TextSettingsCell(ctx);
        selector.setTextAndValue(LocaleController.getString(R.string.OxToolsAgent),
                selectedAgent == null ? LocaleController.getString(R.string.OxToolsNoAgent) : selectedAgent.name, true);
        selector.setOnClickListener(v -> showAgentPicker());
        toolGroup.addView(selector, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView info = new TextView(ctx);
        info.setText(LocaleController.getString(R.string.OxToolsInfo));
        info.setTextSize(12);
        info.setLineSpacing(AndroidUtilities.dp(3), 1f);
        info.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        info.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(8), AndroidUtilities.dp(21), AndroidUtilities.dp(8));
        content.addView(info, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        Map<String, OxTool> tools = new LinkedHashMap<>();
        for (OxTool t : OxBuiltinTools.ALL.values()) tools.put(t.name(), t);
        for (OxTool t : OxMcpManager.tools()) tools.put(t.name(), t);
        if (selectedAgent != null && !tools.isEmpty()) {
            int i = 0;
            for (OxTool t : tools.values()) {
                toolGroup.addView(toolRow(ctx, t, i < tools.size() - 1), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                i++;
            }
        }
        content.addView(toolGroup, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private void showAgentPicker() {
        if (getParentActivity() == null) return;
        List<OxAgent> agents = OxAgentStore.listAgents();
        if (agents.isEmpty()) {
            toast(LocaleController.getString(R.string.OxToolsNoAgent));
            return;
        }
        CharSequence[] names = new CharSequence[agents.size()];
        for (int i = 0; i < agents.size(); i++) names[i] = agents.get(i).name;
        new AlertDialog.Builder(getParentActivity())
                .setItems(names, (d, which) -> {
                    selectedAgent = agents.get(which);
                    rebuild();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private TextView header(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        tv.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(14), AndroidUtilities.dp(21), AndroidUtilities.dp(5));
        return tv;
    }

    private LinearLayout group(Context ctx) {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        return l;
    }

    private void addPermissionRow(Context ctx, LinearLayout parent, String name, String[] status, Runnable onTap) {
        boolean granted = "1".equals(status[1]);
        TextSettingsCell cell = new TextSettingsCell(ctx);
        cell.setTextAndValue(name, status[0], true);
        cell.setTextValueColor(getThemedColor(granted ? Theme.key_chats_actionBackground : Theme.key_windowBackgroundWhiteGrayText));
        cell.setOnClickListener(v -> {
            if (granted) toast(LocaleController.getString(R.string.OxObPermGranted));
            else onTap.run();
        });
        parent.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private View toolRow(Context ctx, OxTool tool, boolean divider) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(10), AndroidUtilities.dp(21), AndroidUtilities.dp(10));
        row.setBackground(Theme.getSelectorDrawable(false));

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(ctx);
        title.setText(tool.name());
        title.setTextSize(15);
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        texts.addView(title);
        TextView desc = new TextView(ctx);
        desc.setText(toolDesc(tool));
        desc.setTextSize(12);
        desc.setMaxLines(2);
        desc.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        texts.addView(desc);
        row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView policy = new TextView(ctx);
        policy.setText(policyLabel(tool));
        policy.setTextSize(13);
        policy.setTypeface(AndroidUtilities.bold());
        policy.setTextColor(policyColor(tool));
        row.addView(policy, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        row.setOnClickListener(v -> {
            cyclePolicy(tool);
            policy.setText(policyLabel(tool));
            policy.setTextColor(policyColor(tool));
        });
        if (!divider) {
            return row;
        }
        LinearLayout outer = new LinearLayout(ctx);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        View line = new View(ctx);
        line.setBackgroundColor(getThemedColor(Theme.key_divider));
        LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1);
        lp.leftMargin = AndroidUtilities.dp(21);
        outer.addView(line, lp);
        return outer;
    }

    private String toolDesc(OxTool tool) {
        String n = tool.name();
        int id;
        switch (n) {
            case "get_time": id = R.string.OxToolDescGetTime; break;
            case "device_info": id = R.string.OxToolDescDeviceInfo; break;
            case "get_me": id = R.string.OxToolDescGetMe; break;
            case "tg_unread_summary": id = R.string.OxToolDescTgUnread; break;
            case "tg_list_chats": id = R.string.OxToolDescTgListChats; break;
            case "tg_read_chat": id = R.string.OxToolDescTgReadChat; break;
            case "http_get": id = R.string.OxToolDescHttpGet; break;
            case "shell_in_container": id = R.string.OxToolDescShellContainer; break;
            case "container_status": id = R.string.OxToolDescContainerStatus; break;
            case "remember": id = R.string.OxToolDescRemember; break;
            case "recall": id = R.string.OxToolDescRecall; break;
            case "list_dir": id = R.string.OxToolDescListDir; break;
            case "read_file": id = R.string.OxToolDescReadFile; break;
            case "write_file": id = R.string.OxToolDescWriteFile; break;
            case "notify": id = R.string.OxToolDescNotify; break;
            case "open_url": id = R.string.OxToolDescOpenUrl; break;
            default: return tool.description();
        }
        return LocaleController.getString(id);
    }

    private String policyLabel(OxTool tool) {
        if (selectedAgent == null) return LocaleController.getString(R.string.OxToolsPolicyDefault);
        String override = selectedAgent.toolsPolicy == null ? "" : selectedAgent.toolsPolicy.optString(tool.name(), "");
        String p = override.isEmpty() ? tool.defaultPolicy() : override;
        String label = policyName(p);
        return override.isEmpty() ? LocaleController.getString(R.string.OxToolsPolicyDefault) + " · " + label : label;
    }

    private int policyColor(OxTool tool) {
        if (selectedAgent == null) return getThemedColor(Theme.key_windowBackgroundWhiteGrayText);
        String override = selectedAgent.toolsPolicy == null ? "" : selectedAgent.toolsPolicy.optString(tool.name(), "");
        String p = override.isEmpty() ? tool.defaultPolicy() : override;
        if (OxTool.POLICY_DENY.equals(p)) return getThemedColor(Theme.key_text_RedRegular);
        if (OxTool.POLICY_ASK.equals(p)) return 0xfff39c12;
        return getThemedColor(Theme.key_chats_actionBackground);
    }

    private String policyName(String p) {
        if (OxTool.POLICY_DENY.equals(p)) return LocaleController.getString(R.string.OxToolsPolicyDeny);
        if (OxTool.POLICY_ASK.equals(p)) return LocaleController.getString(R.string.OxToolsPolicyAsk);
        return LocaleController.getString(R.string.OxToolsPolicyAuto);
    }

    private void cyclePolicy(OxTool tool) {
        if (selectedAgent == null) return;
        JSONObject policies = selectedAgent.toolsPolicy == null ? new JSONObject() : selectedAgent.toolsPolicy;
        String cur = policies.optString(tool.name(), "");
        String next;
        if (cur.isEmpty()) next = OxTool.POLICY_AUTO;
        else if (OxTool.POLICY_AUTO.equals(cur)) next = OxTool.POLICY_ASK;
        else if (OxTool.POLICY_ASK.equals(cur)) next = OxTool.POLICY_DENY;
        else next = "";
        try {
            if (next.isEmpty()) policies.remove(tool.name());
            else policies.put(tool.name(), next);
            selectedAgent.toolsPolicy = policies;
            OxAgentStore.saveAgent(selectedAgent);
        } catch (Exception ignored) {
        }
    }

    private String[] normalPermStatus() {
        if (getParentActivity() == null) return new String[]{LocaleController.getString(R.string.OxObPermMissing), "0"};
        boolean ok = Build.VERSION.SDK_INT < 33
                || getParentActivity().checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
        return new String[]{LocaleController.getString(ok ? R.string.OxObPermGranted : R.string.OxObPermMissing), ok ? "1" : "0"};
    }

    private String[] accessibilityStatus() {
        try {
            String enabled = Settings.Secure.getString(getParentActivity().getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            boolean ok = enabled != null && enabled.contains(getParentActivity().getPackageName());
            return new String[]{LocaleController.getString(ok ? R.string.OxObPermGranted : R.string.OxObPermEnableInSystem), ok ? "1" : "0"};
        } catch (Throwable t) {
            return new String[]{LocaleController.getString(R.string.OxObPermNotDetected), "0"};
        }
    }

    private String[] adbStatus() {
        try {
            boolean adb = Settings.Global.getInt(getParentActivity().getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
            String wifi = Settings.Global.getString(getParentActivity().getContentResolver(), "adb_wifi_enabled");
            if ("1".equals(wifi)) return new String[]{LocaleController.getString(R.string.OxObPermAdbWifi), "1"};
            if (adb) return new String[]{LocaleController.getString(R.string.OxObPermAdbUsb), "1"};
            return new String[]{LocaleController.getString(R.string.OxObPermEnableInSystem), "0"};
        } catch (Throwable t) {
            return new String[]{LocaleController.getString(R.string.OxObPermNotDetected), "0"};
        }
    }

    private String[] shizukuStatus() {
        try {
            getParentActivity().getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
            return new String[]{LocaleController.getString(R.string.OxObPermInstalled), "0"};
        } catch (Throwable t) {
            return new String[]{LocaleController.getString(R.string.OxObPermNotInstalled), "0"};
        }
    }

    private String[] adminStatus() {
        try {
            android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager)
                    getParentActivity().getSystemService(Context.DEVICE_POLICY_SERVICE);
            boolean ok = dpm != null && dpm.isAdminActive(new android.content.ComponentName(
                    getParentActivity(), org.telegram.ox.OxDeviceAdminReceiver.class));
            return new String[]{LocaleController.getString(ok ? R.string.OxObPermGranted : R.string.OxObPermMissing), ok ? "1" : "0"};
        } catch (Throwable t) {
            return new String[]{LocaleController.getString(R.string.OxObPermNotDetected), "0"};
        }
    }

    private String[] rootStatus() {
        String[] paths = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/magisk/.core/bin/su"};
        for (String p : paths) {
            if (new File(p).exists()) return new String[]{LocaleController.getString(R.string.OxObPermDetected), "1"};
        }
        return new String[]{LocaleController.getString(R.string.OxObPermNotDetected), "0"};
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERM_REQUEST) rebuild();
    }

    private void toast(String s) {
        if (getParentActivity() != null) Toast.makeText(getParentActivity(), s, Toast.LENGTH_SHORT).show();
    }
}
