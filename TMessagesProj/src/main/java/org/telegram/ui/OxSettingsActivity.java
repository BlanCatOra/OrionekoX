package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ox.OxAgentsBridge;
import org.telegram.ox.OxMode;
import org.telegram.ox.OxOperitImporter;
import org.telegram.ox.OxRikkaImporter;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/** Ox settings, built on Telegram's stock UniversalRecyclerView + SettingCell. */
@SuppressLint("ViewConstructor")
public class OxSettingsActivity extends BaseFragment {

    private static final int ID_LANGUAGE = 101;
    private static final int ID_PREMIUM = 102;
    private static final int ID_ONBOARDING = 103;
    private static final int ID_LICENSE = 104;
    private static final int ID_AGENTS = 105;
    private static final int ID_MCP = 106;
    private static final int ID_IMPORT = 107;
    private static final int ID_TOOLS = 108;
    private static final int ID_CONTAINER = 109;
    private static final int ID_TERMINAL_SETTINGS = 110;
    private static final int ID_TERMINAL_OPEN = 111;
    private static final int ID_REPOSITORY = 112;
    private static final int ID_SKILLS = 113;
    private static final int ID_WORKSPACE = 114;
    private static final int ID_WORKFLOWS = 115;
    private static final int ID_APPEARANCE = 116;
    private static final int ID_IMPORT_RIKKA = 117;
    private static final int REQ_RIKKA = 601;

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(LocaleController.getString(R.string.OxSettingsTitle));
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

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.OxSettingsSectionMode)));
        items.add(SettingsActivity.SettingCell.Factory.of(0, 0xFF419FD9, 0xFF2B7CB8,
                R.drawable.settings_account, currentMode()));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_LANGUAGE, 0xFF9C6ADE, 0xFF7B4FD1,
                R.drawable.settings_language, LocaleController.getString(R.string.OxSettingsLanguage), null, currentLanguage()));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.OxLocalPremium)));
        items.add(UItem.asCheck(ID_PREMIUM, LocaleController.getString(R.string.OxLocalPremium)).setChecked(OxMode.isLocalPremium()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.OxLocalPremiumInfo)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.OxSettingsSectionGuide)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_ONBOARDING, 0xFFE8A33D, 0xFFD98A1F,
                R.drawable.settings_ask, LocaleController.getString(R.string.OxSettingsRerun)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_LICENSE, 0xFF55CA47, 0xFF27B434,
                R.drawable.settings_policy, LocaleController.getString(R.string.OxSettingsLicense), null, "GPL-2.0"));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.OxSettingsSectionAgents)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_AGENTS, 0xFF7C5CFF, 0xFF5B3FE0,
                R.drawable.filled_bot_approve_24, LocaleController.getString(R.string.OxAgents),
                LocaleController.getString(R.string.OxAgentSettingsEntryInfo)));
        int oxAccent = org.telegram.ox.OxTheme.hasCustom()
                ? org.telegram.ox.OxTheme.accent() : 0xFF9C6ADE;
        int oxAccentDark = org.telegram.ox.OxTheme.hasCustom()
                ? org.telegram.ox.OxTheme.accentDark() : 0xFF7B4FD1;
        items.add(SettingsActivity.SettingCell.Factory.of(ID_APPEARANCE, oxAccent, oxAccentDark,
                R.drawable.msg_theme, LocaleController.getString(R.string.OxAppearance),
                LocaleController.getString(R.string.OxAppearanceEntryInfo)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_MCP, 0xFF1CA5ED, 0xFF1488E1,
                R.drawable.msg_link, LocaleController.getString(R.string.OxMcp)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_IMPORT, 0xFF55CA47, 0xFF27B434,
                R.drawable.settings_data, LocaleController.getString(R.string.OxImportOperit)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_IMPORT_RIKKA, 0xFF55CA47, 0xFF27B434,
                R.drawable.settings_data, LocaleController.getString(R.string.OxRikkaImport),
                LocaleController.getString(R.string.OxRikkaImportInfo)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_TOOLS, 0xFFE8A33D, 0xFFD98A1F,
                R.drawable.settings_privacy, LocaleController.getString(R.string.OxToolsTitle)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_SKILLS, 0xFF55CA47, 0xFF27B434,
                R.drawable.settings_folders, LocaleController.getString(R.string.OxSkillsTitle)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_WORKSPACE, 0xFF1CA5ED, 0xFF1488E1,
                R.drawable.ox_file_24, LocaleController.getString(R.string.OxWorkspace)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_WORKFLOWS, 0xFF7C5CFF, 0xFF5B3FE0,
                R.drawable.outline_message_time_24, LocaleController.getString(R.string.OxWorkflows),
                LocaleController.getString(R.string.OxWorkflowsEntryInfo)));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("OxLinux"));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_CONTAINER, 0xFF4FC3F7, 0xFF2196D6,
                R.drawable.settings_devices, LocaleController.getString(R.string.OxObContainerTitle),
                LocaleController.getString(R.string.OxObContainerEnableSub),
                OxMode.isContainerEnabled() ? LocaleController.getString(R.string.OxSettingsEnabled)
                        : LocaleController.getString(R.string.OxSettingsDisabled)));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.OxTerminal)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_TERMINAL_SETTINGS, 0, 0,
                0, LocaleController.getString(R.string.OxTerminalSettings),
                LocaleController.getString(R.string.OxTerminalSettingsInfo)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_TERMINAL_OPEN, 0xFF2E9E5B, 0xFF1E7A43,
                R.drawable.ox_terminal_24, LocaleController.getString(R.string.OxTerminalOpen)));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.OxSettingsSectionAbout)));
        items.add(SettingsActivity.SettingCell.Factory.of(0, 0, 0, 0,
                LocaleController.getString(R.string.OxSettingsVersion), null, BuildVars.BUILD_VERSION_STRING));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_REPOSITORY, 0, 0,
                0, LocaleController.getString(R.string.OxSettingsRepository), null, "BlanCatOra/OrionekoX"));
        items.add(UItem.asShadow(null));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_LANGUAGE:
                presentFragment(new LanguageSelectActivity());
                break;
            case ID_PREMIUM:
                OxMode.setLocalPremium(!OxMode.isLocalPremium());
                if (listView != null) listView.adapter.update(true);
                break;
            case ID_ONBOARDING:
                presentFragment(new OxOnboardingActivity());
                break;
            case ID_LICENSE:
                open("https://github.com/BlanCatOra/OrionekoX/blob/HEAD/docs/LICENSING.md");
                break;
            case ID_AGENTS:
                presentFragment(new OxAgentsActivity());
                break;
            case ID_APPEARANCE:
                presentFragment(new OxAppearanceActivity());
                break;
            case ID_MCP:
                presentFragment(new OxMcpActivity());
                break;
            case ID_IMPORT:
                startOperitImport();
                break;
            case ID_IMPORT_RIKKA:
                pickRikkaBackup();
                break;
            case ID_TOOLS:
                presentFragment(new OxToolsActivity());
                break;
            case ID_SKILLS:
                presentFragment(new OxSkillsActivity());
                break;
            case ID_WORKSPACE:
                presentFragment(new OxWorkspaceActivity());
                break;
            case ID_WORKFLOWS:
                presentFragment(new OxWorkflowActivity());
                break;
            case ID_CONTAINER:
                presentFragment(new OxOnboardingActivity().setStartPage(5));
                break;
            case ID_TERMINAL_SETTINGS:
                presentFragment(new OxTerminalSettingsActivity());
                break;
            case ID_TERMINAL_OPEN: {
                Bundle args = new Bundle();
                args.putBoolean("presented", true);
                presentFragment(new OxTerminalActivity(args));
                break;
            }
            case ID_REPOSITORY:
                open("https://github.com/BlanCatOra/OrionekoX");
                break;
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private String currentMode() {
        if (OxMode.isAgentOnly()) {
            return LocaleController.getString(R.string.OxSettingsModeOx);
        }
        if (UserConfig.getInstance(0).isClientActivated()) {
            return LocaleController.getString(R.string.OxSettingsModeTg);
        }
        return LocaleController.getString(R.string.OxSettingsModeNone);
    }

    private String currentLanguage() {
        LocaleController.LocaleInfo info = LocaleController.getInstance().getCurrentLocaleInfo();
        return info == null ? "" : info.name;
    }

    private void open(String url) {
        if (getParentActivity() == null) return;
        try {
            getParentActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable ignored) {
        }
    }

    private void pickRikkaBackup() {
        if (getParentActivity() == null) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_RIKKA);
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_RIKKA || resultCode != android.app.Activity.RESULT_OK) return;
        Uri uri = data == null ? null : data.getData();
        Context ctx = getParentActivity();
        if (uri == null || ctx == null) return;
        new Thread(() -> {
            OxRikkaImporter.Preview preview = null;
            String error = null;
            try {
                preview = OxRikkaImporter.preview(ctx, uri);
            } catch (Throwable t) {
                error = t.getMessage();
            }
            final OxRikkaImporter.Preview p = preview;
            final String err = error;
            AndroidUtilities.runOnUIThread(() -> {
                if (getParentActivity() == null) return;
                if (p == null) {
                    showRikkaError(err);
                    return;
                }
                new AlertDialog.Builder(getParentActivity())
                        .setTitle(LocaleController.getString(R.string.OxRikkaPreviewTitle))
                        .setMessage(rikkaPreviewText(p))
                        .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                        .setPositiveButton(LocaleController.getString(R.string.OxRikkaApply), (d, w) ->
                                importRikka(ctx, uri))
                        .show();
            });
        }, "ox-rikka-preview").start();
    }

    private void importRikka(Context ctx, Uri uri) {
        new Thread(() -> {
            try {
                final OxRikkaImporter.Result result = OxRikkaImporter.importFrom(ctx, uri);
                AndroidUtilities.runOnUIThread(() -> {
                    OxAgentsBridge.syncAgents(true);
                    NotificationCenter.getInstance(0).postNotificationName(NotificationCenter.dialogsNeedReload);
                    if (getParentActivity() == null) return;
                    new AlertDialog.Builder(getParentActivity())
                            .setTitle(LocaleController.getString(R.string.OxRikkaImport))
                            .setMessage(rikkaResultText(result))
                            .setPositiveButton(LocaleController.getString(R.string.OK), null)
                            .show();
                });
            } catch (Throwable t) {
                AndroidUtilities.runOnUIThread(() -> showRikkaError(t.getMessage()));
            }
        }, "ox-rikka-import").start();
    }

    private String rikkaPreviewText(OxRikkaImporter.Preview p) {
        StringBuilder sb = new StringBuilder(LocaleController.formatString(
                "OxRikkaPreview", R.string.OxRikkaPreview,
                p.assistants, p.providers, p.mcp, p.skills));
        if (!p.names.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < p.names.size(); i++) {
                if (i > 0) names.append("、");
                names.append(p.names.get(i));
            }
            sb.append("\n").append(LocaleController.formatString(
                    "OxRikkaNames", R.string.OxRikkaNames, names.toString()));
        }
        if (!p.skillNames.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < p.skillNames.size(); i++) {
                if (i > 0) names.append("、");
                names.append(p.skillNames.get(i));
            }
            sb.append("\n").append(LocaleController.formatString(
                    "OxRikkaSkillNames", R.string.OxRikkaSkillNames, names.toString()));
        }
        if (p.conversations > 0) {
            sb.append("\n").append(LocaleController.formatString(
                    "OxRikkaHistory", R.string.OxRikkaHistory, p.conversations, p.messageNodes));
        }
        if (p.existingAssistants > 0 || p.existingMcp > 0 || p.existingSkills > 0) {
            sb.append("\n").append(LocaleController.formatString(
                    "OxRikkaExisting", R.string.OxRikkaExisting,
                    p.existingAssistants, p.existingMcp, p.existingSkills));
        }
        for (String n : p.notes) sb.append("\n• ").append(n);
        return sb.toString();
    }

    private String rikkaResultText(OxRikkaImporter.Result r) {
        if (r.agentsCreated == 0 && r.agentsUpdated == 0 && r.mcpAdded == 0 && r.skills == 0) {
            return LocaleController.getString(R.string.OxRikkaNothing);
        }
        StringBuilder sb = new StringBuilder(LocaleController.formatString(
                "OxRikkaDone", R.string.OxRikkaDone,
                r.agentsCreated, r.agentsUpdated, r.mcpAdded, r.mcpSkipped, r.skills));
        for (String n : r.notes) sb.append("\n• ").append(n);
        return sb.toString();
    }

    private void showRikkaError(String message) {
        if (getParentActivity() == null) return;
        String m = message == null ? "" : message;
        if ("not a RikkaHub settings file".equals(m)) {
            m = LocaleController.getString(R.string.OxRikkaInvalid);
        } else if ("settings.json missing".equals(m)) {
            m = LocaleController.getString(R.string.OxRikkaMissingSettings);
        } else if ("cannot open backup".equals(m)) {
            m = LocaleController.getString(R.string.OxRikkaOpenFailed);
        }
        new AlertDialog.Builder(getParentActivity())
                .setTitle(LocaleController.getString(R.string.OxRikkaImport))
                .setMessage(LocaleController.formatString(
                        "OxRikkaFailed", R.string.OxRikkaFailed, m))
                .setPositiveButton(LocaleController.getString(R.string.OK), null)
                .show();
    }

    private void startOperitImport() {
        if (getParentActivity() == null) return;
        if (!OxOperitImporter.storageReady()) {
            new AlertDialog.Builder(getParentActivity())
                    .setTitle(LocaleController.getString(R.string.OxImportStorageTitle))
                    .setMessage(LocaleController.getString(R.string.OxImportStorageText))
                    .setPositiveButton(LocaleController.getString(R.string.OxImportGrant), (d, w) -> {
                        try {
                            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:" + getParentActivity().getPackageName()));
                            getParentActivity().startActivity(i);
                        } catch (Throwable ignored) {
                        }
                    })
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .show();
            return;
        }
        android.widget.Toast.makeText(getParentActivity(), LocaleController.getString(R.string.OxImportRunning),
                android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final OxOperitImporter.Result result = OxOperitImporter.importAll();
            AndroidUtilities.runOnUIThread(() -> {
                if (getParentActivity() == null) return;
                OxAgentsBridge.syncAgents(true);
                for (String id : result.importedAgentIds) {
                    OxAgentsBridge.replayImportedHistory(id);
                }
                NotificationCenter.getInstance(0).postNotificationName(NotificationCenter.dialogsNeedReload);
                new AlertDialog.Builder(getParentActivity())
                        .setTitle(LocaleController.getString(R.string.OxImportDone))
                        .setMessage(result.summary())
                        .setPositiveButton(LocaleController.getString(R.string.OK), null)
                        .show();
            });
        }, "ox-operit-import").start();
    }
}
