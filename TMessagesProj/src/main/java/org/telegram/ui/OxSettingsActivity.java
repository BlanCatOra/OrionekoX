package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ox.OxMode;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/** Ox settings: re-run the onboarding guide, see the current mode, reach the Ox surfaces. */
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
        ScrollView scroll = new ScrollView(context);
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);

        boolean ox = OxMode.isAgentOnly();
        String mode = LocaleController.getString(ox ? R.string.OxSettingsModeOx : R.string.OxSettingsModeTg);
        if (!ox && UserConfig.getActivatedAccountsCount() == 0) {
            mode = LocaleController.getString(R.string.OxSettingsModeOx);
        }
        addRow(context, col, LocaleController.formatString("OxSettingsMode", R.string.OxSettingsMode, mode), null, false);
        addRow(context, col, LocaleController.getString(R.string.OxSettingsRerun), () ->
                presentFragment(new OxOnboardingActivity()), true);
        addRow(context, col, LocaleController.getString(R.string.OxAgents), () ->
                presentFragment(new OxAgentsActivity()), true);
        addRow(context, col, LocaleController.getString(R.string.OxMcp), () ->
                presentFragment(new OxMcpActivity()), true);
        addRow(context, col, LocaleController.getString(R.string.OxObContainerTitle), () -> {
            // jump into the wizard at the container step
            OxOnboardingActivity act = new OxOnboardingActivity();
            presentFragment(act, true);
        }, true);

        scroll.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = root;
        return fragmentView;
    }

    private void addRow(Context context, LinearLayout parent, String text, Runnable action, boolean tappable) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(16));
        boolean ox = OxMode.isAgentOnly();
        if (tappable) {
            tv.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            tv.setBackground(Theme.getSelectorDrawable(false));
            tv.setOnClickListener(v -> action.run());
        } else {
            tv.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        }
        parent.addView(tv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        View divider = new View(context);
        divider.setBackgroundColor(getThemedColor(Theme.key_divider));
        parent.addView(divider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 20, 0, 20, 0));
    }
}
