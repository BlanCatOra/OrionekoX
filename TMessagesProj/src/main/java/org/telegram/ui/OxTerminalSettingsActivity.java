package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

/** Terminal appearance: palette and monospace size, applied live by the terminal tab. */
@SuppressLint("ViewConstructor")
public class OxTerminalSettingsActivity extends BaseFragment {

    private static final int[] FONT_SIZES = new int[]{12, 14, 16, 18};
    private final ArrayList<TextSettingsCell> schemeCells = new ArrayList<>();
    private final ArrayList<TextSettingsCell> sizeCells = new ArrayList<>();

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(LocaleController.getString(R.string.OxTerminalSettings));
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

        content.addView(header(context, LocaleController.getString(R.string.OxTerminalColor)));
        LinearLayout schemeGroup = group(context);
        int scheme = prefs().getInt(OxTerminalActivity.PREF_SCHEME, 0);
        String[] schemes = new String[]{
                LocaleController.getString(R.string.OxTerminalSchemeOx),
                LocaleController.getString(R.string.OxTerminalSchemeClassic),
                LocaleController.getString(R.string.OxTerminalSchemeAmber),
                LocaleController.getString(R.string.OxTerminalSchemeLight),
                LocaleController.getString(R.string.OxTerminalSchemeUbuntu)
        };
        for (int i = 0; i < schemes.length; i++) {
            final int index = i;
            TextSettingsCell cell = new TextSettingsCell(context);
            cell.setTextAndValue(schemes[i], scheme == i ? "✓" : "", i < schemes.length - 1);
            cell.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            cell.setOnClickListener(v -> selectScheme(index));
            schemeCells.add(cell);
            schemeGroup.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        content.addView(schemeGroup, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        content.addView(header(context, LocaleController.getString(R.string.OxTerminalFontSize)));
        LinearLayout sizeGroup = group(context);
        int fontSize = prefs().getInt(OxTerminalActivity.PREF_FONT_SIZE, 14);
        for (int i = 0; i < FONT_SIZES.length; i++) {
            final int size = FONT_SIZES[i];
            TextSettingsCell cell = new TextSettingsCell(context);
            cell.setTextAndValue(String.valueOf(size) + " sp", fontSize == size ? "✓" : "", i < FONT_SIZES.length - 1);
            cell.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            cell.setOnClickListener(v -> selectFontSize(size));
            sizeCells.add(cell);
            sizeGroup.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        content.addView(sizeGroup, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView info = new TextView(context);
        info.setText(LocaleController.getString(R.string.OxTerminalWorkingDirInfo));
        info.setTextSize(13);
        info.setLineSpacing(AndroidUtilities.dp(2), 1f);
        info.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        info.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(9), AndroidUtilities.dp(21), AndroidUtilities.dp(10));
        content.addView(info, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        return fragmentView;
    }

    private SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(OxTerminalActivity.PREFS, Context.MODE_PRIVATE);
    }

    private void selectScheme(int scheme) {
        prefs().edit().putInt(OxTerminalActivity.PREF_SCHEME, scheme).apply();
        for (int i = 0; i < schemeCells.size(); i++) {
            TextSettingsCell cell = schemeCells.get(i);
            cell.setTextAndValue(cell.getTextView().getText().toString(), i == scheme ? "✓" : "", i < schemeCells.size() - 1);
        }
    }

    private void selectFontSize(int size) {
        prefs().edit().putInt(OxTerminalActivity.PREF_FONT_SIZE, size).apply();
        for (int i = 0; i < sizeCells.size(); i++) {
            TextSettingsCell cell = sizeCells.get(i);
            cell.setTextAndValue(cell.getTextView().getText().toString(), FONT_SIZES[i] == size ? "✓" : "", i < sizeCells.size() - 1);
        }
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
}
