package org.telegram.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;

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
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Permission centre and per-agent tool policies, native settings list. */
@SuppressLint("ViewConstructor")
public class OxToolsActivity extends BaseFragment {

    private static final int PERM_REQUEST = 7501;
    private static final int ID_AGENT = 20;
    private static final int PERM_BASE = 100;
    private static final int TOOL_BASE = 1000;

    private UniversalRecyclerView listView;
    private OxAgent selectedAgent;
    private final ArrayList<OxTool> toolList = new ArrayList<>();

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
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        listView.adapter.setApplyBackground(false);
        listView.setSections();
        listView.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        listView.setClipToPadding(false);
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        fragmentView = root;

        List<OxAgent> agents = OxAgentStore.listAgents();
        if (!agents.isEmpty()) selectedAgent = agents.get(0);
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        rebuild();
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.OxToolsPermissions)));
        addPermission(items, 0, LocaleController.getString(R.string.OxObPermNormal), normalPermStatus());
        addPermission(items, 1, LocaleController.getString(R.string.OxObPermAccessibility), accessibilityStatus());
        addPermission(items, 2, LocaleController.getString(R.string.OxToolsAdb), adbStatus());
        addPermission(items, 3, LocaleController.getString(R.string.OxObPermShizuku), shizukuStatus());
        addPermission(items, 4, LocaleController.getString(R.string.OxObPermAdmin), adminStatus());
        addPermission(items, 5, LocaleController.getString(R.string.OxObPermRoot), rootStatus());
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.OxToolsAgentTools)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_AGENT, 0xFF7C5CFF, 0xFF5B3FE0,
                R.drawable.filled_bot_approve_24, LocaleController.getString(R.string.OxToolsAgent),
                null, selectedAgent == null ? LocaleController.getString(R.string.OxToolsNoAgent) : selectedAgent.name));
        items.add(UItem.asShadow(LocaleController.getString(R.string.OxToolsInfo)));

        toolList.clear();
        Map<String, OxTool> tools = new LinkedHashMap<>();
        for (OxTool t : OxBuiltinTools.ALL.values()) tools.put(t.name(), t);
        for (OxTool t : OxMcpManager.tools()) tools.put(t.name(), t);
        if (selectedAgent != null) {
            for (OxTool t : tools.values()) {
                toolList.add(t);
                items.add(SettingsActivity.SettingCell.Factory.of(TOOL_BASE + toolList.size() - 1, 0, 0, 0,
                        t.name(), toolDesc(t), policyLabel(t)));
            }
        }
        items.add(UItem.asShadow(null));
    }

    private void addPermission(ArrayList<UItem> items, int index, String title, String[] status) {
        items.add(SettingsActivity.SettingCell.Factory.of(PERM_BASE + index, 0, 0, 0, title, null, status[0]));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_AGENT) {
            showAgentPicker();
        } else if (item.id >= PERM_BASE && item.id < PERM_BASE + 6) {
            int index = item.id - PERM_BASE;
            String[] status = index == 0 ? normalPermStatus()
                    : index == 1 ? accessibilityStatus()
                    : index == 2 ? adbStatus()
                    : index == 3 ? shizukuStatus()
                    : index == 4 ? adminStatus()
                    : rootStatus();
            if ("1".equals(status[1])) {
                toast(LocaleController.getString(R.string.OxObPermGranted));
            } else {
                runPermissionAction(index);
            }
        } else if (item.id >= TOOL_BASE && item.id - TOOL_BASE < toolList.size()) {
            cyclePolicy(toolList.get(item.id - TOOL_BASE));
            rebuild();
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void runPermissionAction(int index) {
        if (getParentActivity() == null) return;
        switch (index) {
            case 0:
                if (Build.VERSION.SDK_INT >= 33
                        && getParentActivity().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    getParentActivity().requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERM_REQUEST);
                } else {
                    toast(LocaleController.getString(R.string.OxObPermGranted));
                }
                break;
            case 1:
                try {
                    getParentActivity().startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } catch (Throwable ignored) {
                }
                break;
            case 2:
                try {
                    getParentActivity().startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                } catch (Throwable ignored) {
                }
                break;
            case 3:
                try {
                    Intent i = getParentActivity().getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                    if (i != null) getParentActivity().startActivity(i);
                    else toast(LocaleController.getString(R.string.OxObPermNotInstalled));
                } catch (Throwable ignored) {
                }
                break;
            case 4:
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
                break;
            case 5:
                toast(LocaleController.getString(R.string.OxObPermRootHint));
                break;
        }
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

    private void rebuild() {
        if (listView != null) listView.adapter.update(true);
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
            case "list_skills": id = R.string.OxToolDescListSkills; break;
            case "read_skill": id = R.string.OxToolDescReadSkill; break;
            case "ui_dump": id = R.string.OxToolDescUiDump; break;
            case "ui_tap": id = R.string.OxToolDescUiTap; break;
            case "ui_tap_text": id = R.string.OxToolDescUiTapText; break;
            case "ui_find_text": id = R.string.OxToolDescUiFindText; break;
            case "ui_scroll_tap_text": id = R.string.OxToolDescUiScrollTapText; break;
            case "ui_swipe": id = R.string.OxToolDescUiSwipe; break;
            case "ui_key": id = R.string.OxToolDescUiKey; break;
            case "ui_screenshot": id = R.string.OxToolDescUiScreenshot; break;
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
                || getParentActivity().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
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
