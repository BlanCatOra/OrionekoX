package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ox.OxTheme;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Ox accent / Kelivo-RikkaHub theme JSON import. Telegram's theme is not modified. */
@SuppressLint("ViewConstructor")
public class OxAppearanceActivity extends BaseFragment {

    private static final int ID_PREVIEW = 1;
    private static final int ID_IMPORT = 2;
    private static final int ID_RESET = 3;
    private static final int ID_CHAT_APPEARANCE = 4;
    private static final int PRESET_BASE = 100;
    private static final int REQ_IMPORT = 401;

    private static final int[] PRESET_COLORS = {
            0xFF6750A4, 0xFF2AABEE, 0xFF34C759, 0xFFF39C12, 0xFFE91E63, 0xFF00BCD4
    };
    private static final String[] PRESET_NAMES = {
            "Material 紫", "海洋蓝", "森林绿", "琥珀", "樱花粉", "青色"
    };

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(LocaleController.getString(R.string.OxAppearance));
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
        int accent = OxTheme.hasCustom() ? OxTheme.accent() : getThemedColor(Theme.key_chats_actionBackground);
        int dark = OxTheme.hasCustom() ? OxTheme.accentDark() : getThemedColor(Theme.key_chats_actionPressedBackground);
        items.add(UItem.asHeader(LocaleController.getString(R.string.OxAppearance)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_PREVIEW, accent, dark,
                R.drawable.msg_theme, LocaleController.getString(R.string.OxAppearanceCurrent),
                OxTheme.hasCustom()
                        ? OxTheme.name() + " · " + OxTheme.hex()
                        : LocaleController.getString(R.string.OxAppearanceDefault)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_IMPORT, accent, dark,
                R.drawable.msg_download, LocaleController.getString(R.string.OxAppearanceImport),
                LocaleController.getString(R.string.OxAppearanceImportInfo)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.OxAppearanceIntro)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_CHAT_APPEARANCE, accent, dark,
                R.drawable.msg_colors, LocaleController.getString(R.string.OxChatAppearance),
                LocaleController.getString(R.string.OxChatEntryInfo)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.OxChatApplyHint)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.OxAppearancePresets)));
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            int color = PRESET_COLORS[i];
            items.add(SettingsActivity.SettingCell.Factory.of(PRESET_BASE + i,
                    color, OxTheme.darkenForUi(color), R.drawable.msg_palette,
                    PRESET_NAMES[i], String.format("#%06X", color & 0xFFFFFF)));
        }
        items.add(SettingsActivity.SettingCell.Factory.of(ID_RESET, 0xFF8E8E93, 0xFF6E6E73,
                R.drawable.msg_reset, LocaleController.getString(R.string.OxAppearanceReset)));
        items.add(UItem.asShadow(null));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_IMPORT && getParentActivity() != null) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            try {
                startActivityForResult(intent, REQ_IMPORT);
            } catch (Throwable t) {
                toast(LocaleController.formatString("OxAppearanceImportFailed",
                        R.string.OxAppearanceImportFailed, t.getMessage()));
            }
            return;
        }
        if (item.id == ID_CHAT_APPEARANCE) {
            presentFragment(new OxChatAppearanceActivity());
            return;
        }
        if (item.id == ID_RESET) {
            OxTheme.clear();
            toast(LocaleController.getString(R.string.OxAppearanceResetDone));
            rebuild();
            return;
        }
        int preset = item.id - PRESET_BASE;
        if (preset >= 0 && preset < PRESET_COLORS.length) {
            OxTheme.applyPreset(PRESET_COLORS[preset], PRESET_NAMES[preset]);
            toast(LocaleController.formatString("OxAppearanceImported",
                    R.string.OxAppearanceImported, PRESET_NAMES[preset]));
            rebuild();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_IMPORT || resultCode != android.app.Activity.RESULT_OK || data == null) return;
        final Uri uri = data.getData();
        final Context ctx = getParentActivity();
        if (uri == null || ctx == null) return;
        new Thread(() -> {
            String msg;
            try {
                InputStream in = ctx.getContentResolver().openInputStream(uri);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0 && bos.size() < 64 * 1024) {
                    bos.write(buf, 0, Math.min(n, 64 * 1024 - bos.size()));
                }
                in.close();
                OxTheme.importJson(new String(bos.toByteArray(), StandardCharsets.UTF_8));
                msg = LocaleController.formatString("OxAppearanceImported",
                        R.string.OxAppearanceImported,
                        OxTheme.name().isEmpty() ? OxTheme.hex() : OxTheme.name());
            } catch (Throwable t) {
                msg = LocaleController.formatString("OxAppearanceImportFailed",
                        R.string.OxAppearanceImportFailed, t.getMessage());
            }
            final String m = msg;
            AndroidUtilities.runOnUIThread(() -> {
                toast(m);
                rebuild();
            });
        }, "ox-theme-import").start();
    }

    private void rebuild() {
        if (listView != null) listView.adapter.update(true);
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void toast(String s) {
        if (getParentActivity() != null) {
            Toast.makeText(getParentActivity(), s, Toast.LENGTH_SHORT).show();
        }
    }
}
