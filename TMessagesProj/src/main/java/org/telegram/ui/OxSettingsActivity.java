package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ox.OxMode;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

/** Ox settings, built from the same cells as Telegram's own settings pages. */
@SuppressLint("ViewConstructor")
public class OxSettingsActivity extends BaseFragment {

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
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, AndroidUtilities.dp(24));
        scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = root;

        content.addView(header(context, LocaleController.getString(R.string.OxSettingsSectionMode)));
        LinearLayout modeGroup = group(context);
        modeGroup.addView(cell(context, LocaleController.getString(R.string.OxSettingsModeLabel), currentMode(), true), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        modeGroup.addView(cell(context, LocaleController.getString(R.string.OxSettingsLanguage), currentLanguage(), true, v ->
                presentFragment(new LanguageSelectActivity())), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(modeGroup, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        content.addView(header(context, LocaleController.getString(R.string.OxSettingsSectionGuide)));
        LinearLayout guideGroup = group(context);
        guideGroup.addView(cell(context, LocaleController.getString(R.string.OxSettingsRerun), "", true, v ->
                presentFragment(new OxOnboardingActivity())), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        guideGroup.addView(cell(context, LocaleController.getString(R.string.OxSettingsLicense), "GPL-2.0", true, v ->
                open("https://github.com/BlanCatOra/OrionekoX/blob/HEAD/docs/LICENSING.md")), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(guideGroup, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        content.addView(header(context, LocaleController.getString(R.string.OxSettingsSectionAgents)));
        LinearLayout agentGroup = group(context);
        agentGroup.addView(cell(context, LocaleController.getString(R.string.OxAgents), "", true, v ->
                presentFragment(new OxAgentsActivity())), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        agentGroup.addView(cell(context, LocaleController.getString(R.string.OxMcp), "", true, v ->
                presentFragment(new OxMcpActivity())), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        agentGroup.addView(cell(context, LocaleController.getString(R.string.OxToolsTitle), "", true, v ->
                presentFragment(new OxToolsActivity())), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(agentGroup, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        content.addView(header(context, "OxLinux"));
        LinearLayout containerGroup = group(context);
        containerGroup.addView(cell(context, LocaleController.getString(R.string.OxObContainerTitle),
                OxMode.isContainerEnabled() ? LocaleController.getString(R.string.OxSettingsEnabled)
                        : LocaleController.getString(R.string.OxSettingsDisabled), true, v -> {
                    presentFragment(new OxOnboardingActivity().setStartPage(5));
                }), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(containerGroup, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        content.addView(header(context, LocaleController.getString(R.string.OxSettingsSectionAbout)));
        LinearLayout about = group(context);
        about.addView(cell(context, LocaleController.getString(R.string.OxSettingsVersion), BuildVars.BUILD_VERSION_STRING, true), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        about.addView(cell(context, LocaleController.getString(R.string.OxSettingsRepository), "BlanCatOra/OrionekoX", true, v ->
                open("https://github.com/BlanCatOra/OrionekoX")), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(about, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        return fragmentView;
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

    private TextView header(Context context, String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        tv.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(14), AndroidUtilities.dp(21), AndroidUtilities.dp(5));
        return tv;
    }

    private LinearLayout group(Context context) {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        return l;
    }

    private TextSettingsCell cell(Context context, String text, String value, boolean divider) {
        TextSettingsCell cell = new TextSettingsCell(context);
        cell.setTextAndValue(text, value, divider);
        return cell;
    }

    private TextSettingsCell cell(Context context, String text, String value, boolean divider, View.OnClickListener onClick) {
        TextSettingsCell cell = cell(context, text, value, divider);
        cell.setOnClickListener(onClick);
        return cell;
    }

    private void open(String url) {
        if (getParentActivity() == null) return;
        try {
            getParentActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable ignored) {
        }
    }
}
