package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/** Terminal appearance, using the same native radio list as Telegram settings. */
@SuppressLint("ViewConstructor")
public class OxTerminalSettingsActivity extends BaseFragment {

    private static final int[] FONT_SIZES = new int[]{12, 14, 16, 18};
    private static final int ID_SCHEME = 1;
    private static final int ID_SIZE = 100;

    private UniversalRecyclerView listView;

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
        int scheme = prefs().getInt(OxTerminalActivity.PREF_SCHEME, 0);
        int fontSize = prefs().getInt(OxTerminalActivity.PREF_FONT_SIZE, 14);
        String[] schemes = new String[]{
                LocaleController.getString(R.string.OxTerminalSchemeOx),
                LocaleController.getString(R.string.OxTerminalSchemeClassic),
                LocaleController.getString(R.string.OxTerminalSchemeAmber),
                LocaleController.getString(R.string.OxTerminalSchemeLight),
                LocaleController.getString(R.string.OxTerminalSchemeUbuntu)
        };
        items.add(UItem.asHeader(LocaleController.getString(R.string.OxTerminalColor)));
        for (int i = 0; i < schemes.length; i++) {
            items.add(UItem.asRadio(ID_SCHEME + i, schemes[i]).setChecked(scheme == i));
        }
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.OxTerminalFontSize)));
        for (int i = 0; i < FONT_SIZES.length; i++) {
            items.add(UItem.asRadio(ID_SIZE + i, FONT_SIZES[i] + " sp").setChecked(fontSize == FONT_SIZES[i]));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.OxTerminalWorkingDirInfo)));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= ID_SCHEME && item.id < ID_SCHEME + 5) {
            prefs().edit().putInt(OxTerminalActivity.PREF_SCHEME, item.id - ID_SCHEME).apply();
            if (listView != null) listView.adapter.update(true);
        } else if (item.id >= ID_SIZE && item.id < ID_SIZE + FONT_SIZES.length) {
            prefs().edit().putInt(OxTerminalActivity.PREF_FONT_SIZE, FONT_SIZES[item.id - ID_SIZE]).apply();
            if (listView != null) listView.adapter.update(true);
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(OxTerminalActivity.PREFS, Context.MODE_PRIVATE);
    }
}
