package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ox.OxChatTheme;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
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

/** Custom vs Telegram-native appearance for local-agent conversations. */
@SuppressLint("ViewConstructor")
public class OxChatAppearanceActivity extends BaseFragment {

    private static final int ID_MODE = 1;
    private static final int ID_BG = 2;
    private static final int ID_BG_IMAGE = 3;
    private static final int ID_IN = 4;
    private static final int ID_OUT = 5;
    private static final int ID_ALPHA = 6;
    private static final int ID_RADIUS = 7;
    private static final int ID_FONT = 8;
    private static final int ID_IMPORT = 9;
    private static final int ID_SAVE = 10;
    private static final int ID_RESET = 11;
    private static final int ID_BG_CLEAR = 12;
    private static final int PRESET_BASE = 100;
    private static final int REQ_IMPORT = 501;
    private static final int REQ_BG = 502;

    private static final int[] BG_COLORS = {
            0xFFF3F1F7, 0xFF101114, 0xFFFDF6E3, 0xFFE8F0FE, 0xFF1E1B2E, 0x00000000
    };
    private static final int[] IN_COLORS = {
            0xFFFFFFFF, 0xFFE8F5E9, 0xFFE3F2FD, 0xFFFFF3E0, 0xFFF3E5F5, 0xFF1F2430
    };
    private static final int[] OUT_COLORS = {
            0xFFEFFDDE, 0xFFDCEBFF, 0xFFFFE4EC, 0xFFFFF1D6, 0xFFF0E6FF, 0xFF2A3A2A
    };
    private static final int[] ALPHAS = {255, 220, 190, 160, 120};
    private static final int[] RADII = {6, 10, 14, 18, 22};
    private static final float[] FONTS = {0.9f, 1f, 1.1f, 1.2f};

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(LocaleController.getString(R.string.OxChatAppearance));
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
        boolean custom = OxChatTheme.isCustom();
        int accent = getThemedColor(Theme.key_chats_actionBackground);
        int dark = getThemedColor(Theme.key_chats_actionPressedBackground);
        items.add(UItem.asHeader(LocaleController.getString(R.string.OxChatAppearance)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_MODE, accent, dark, R.drawable.msg_theme,
                LocaleController.getString(R.string.OxChatMode),
                custom ? LocaleController.getString(R.string.OxChatModeCustom)
                        : LocaleController.getString(R.string.OxChatModeNative)));
        if (!custom) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.OxChatNativeHint)));
            items.add(SettingsActivity.SettingCell.Factory.of(ID_RESET, 0xFF8E8E93, 0xFF6E6E73,
                    R.drawable.msg_reset, LocaleController.getString(R.string.OxChatResetNative)));
            items.add(UItem.asShadow(null));
            return;
        }

        items.add(SettingsActivity.SettingCell.Factory.of(ID_BG, OxChatTheme.background(),
                OxChatTheme.darken(OxChatTheme.background()), R.drawable.msg_colors,
                LocaleController.getString(R.string.OxChatBackground),
                OxChatTheme.background() == 0 ? "透明" : OxChatTheme.hex(OxChatTheme.background())));
        String image = OxChatTheme.backgroundImage();
        items.add(SettingsActivity.SettingCell.Factory.of(ID_BG_IMAGE, 0xFF2AABEE, 0xFF1488E1,
                R.drawable.msg_download, LocaleController.getString(R.string.OxChatBackgroundImage),
                image == null || image.isEmpty() ? LocaleController.getString(R.string.OxChatNotSelected) : new java.io.File(image).getName()));
        if (image != null && !image.isEmpty()) {
            items.add(SettingsActivity.SettingCell.Factory.of(ID_BG_CLEAR, 0xFF8E8E93, 0xFF6E6E73,
                    R.drawable.msg_delete, LocaleController.getString(R.string.OxChatBackgroundClear), null));
        }
        items.add(SettingsActivity.SettingCell.Factory.of(ID_IN, OxChatTheme.inBubble(),
                OxChatTheme.darken(OxChatTheme.inBubble()), R.drawable.msg_colors,
                LocaleController.getString(R.string.OxChatInBubble),
                OxChatTheme.hex(OxChatTheme.inBubble())));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_OUT, OxChatTheme.outBubble(),
                OxChatTheme.darken(OxChatTheme.outBubble()), R.drawable.msg_colors,
                LocaleController.getString(R.string.OxChatOutBubble),
                OxChatTheme.hex(OxChatTheme.outBubble())));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_ALPHA, accent, dark, R.drawable.msg_theme,
                LocaleController.getString(R.string.OxChatOpacity),
                Math.round(OxChatTheme.bubbleAlpha() * 100f / 255f) + "%"));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_RADIUS, accent, dark, R.drawable.msg_theme,
                LocaleController.getString(R.string.OxChatRadius), OxChatTheme.cornerRadius() + " dp"));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_FONT, accent, dark, R.drawable.msg_theme,
                LocaleController.getString(R.string.OxChatFontScale),
                String.format("%.1fx", OxChatTheme.fontScale())));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_IMPORT, 0xFF34C759, 0xFF2AA84A,
                R.drawable.msg_download, LocaleController.getString(R.string.OxChatImport),
                LocaleController.getString(R.string.OxChatImportInfo)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_SAVE, 0xFF7C5CFF, 0xFF5B3FE0,
                R.drawable.msg_add, LocaleController.getString(R.string.OxChatSavePreset)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.OxChatApplyHint)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.OxChatPresets)));
        ArrayList<String> presets = new ArrayList<>(OxChatTheme.presets());
        if (presets.isEmpty()) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.OxChatNoPresets)));
        } else {
            for (int i = 0; i < presets.size(); i++) {
                items.add(SettingsActivity.SettingCell.Factory.of(PRESET_BASE + i,
                        0xFF7C5CFF, 0xFF5B3FE0, R.drawable.msg_theme,
                        presets.get(i), LocaleController.getString(R.string.OxChatPresetHint)));
            }
        }
        items.add(SettingsActivity.SettingCell.Factory.of(ID_RESET, 0xFF8E8E93, 0xFF6E6E73,
                R.drawable.msg_reset, LocaleController.getString(R.string.OxChatResetNative)));
        items.add(UItem.asShadow(null));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_MODE) {
            if (OxChatTheme.isCustom()) {
                OxChatTheme.setMode(OxChatTheme.MODE_NATIVE);
            } else {
                OxChatTheme.enableCustom();
            }
            rebuild();
        } else if (item.id == ID_RESET) {
            OxChatTheme.resetNative();
            toast(LocaleController.getString(R.string.OxChatResetDone));
            rebuild();
        } else if (item.id == ID_BG) {
            OxChatTheme.setInt("background", next(BG_COLORS, OxChatTheme.background()));
            rebuild();
        } else if (item.id == ID_BG_IMAGE) {
            pickImage();
        } else if (item.id == ID_BG_CLEAR) {
            OxChatTheme.clearBackgroundImage();
            rebuild();
        } else if (item.id == ID_IN) {
            OxChatTheme.setInt("in_bubble", next(IN_COLORS, OxChatTheme.inBubble()));
            rebuild();
        } else if (item.id == ID_OUT) {
            OxChatTheme.setInt("out_bubble", next(OUT_COLORS, OxChatTheme.outBubble()));
            rebuild();
        } else if (item.id == ID_ALPHA) {
            OxChatTheme.setInt("bubble_alpha", nextInt(ALPHAS, OxChatTheme.bubbleAlpha()));
            rebuild();
        } else if (item.id == ID_RADIUS) {
            OxChatTheme.setInt("corner_radius", nextInt(RADII, OxChatTheme.cornerRadius()));
            rebuild();
        } else if (item.id == ID_FONT) {
            OxChatTheme.setFloat("font_scale", nextFloat(FONTS, OxChatTheme.fontScale()));
            rebuild();
        } else if (item.id == ID_IMPORT) {
            pickJson();
        } else if (item.id == ID_SAVE) {
            askPresetName();
        } else if (item.id >= PRESET_BASE) {
            ArrayList<String> presets = new ArrayList<>(OxChatTheme.presets());
            int index = item.id - PRESET_BASE;
            if (index >= 0 && index < presets.size()) {
                try {
                    OxChatTheme.applyPreset(presets.get(index));
                    toast(LocaleController.formatString("OxChatPresetApplied",
                            R.string.OxChatPresetApplied, presets.get(index)));
                    rebuild();
                } catch (Throwable t) {
                    toast(LocaleController.formatString("OxChatImportFailed",
                            R.string.OxChatImportFailed, t.getMessage()));
                }
            }
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= PRESET_BASE) {
            ArrayList<String> presets = new ArrayList<>(OxChatTheme.presets());
            int index = item.id - PRESET_BASE;
            if (index >= 0 && index < presets.size() && getParentActivity() != null) {
                String name = presets.get(index);
                new AlertDialog.Builder(getParentActivity())
                        .setTitle(name)
                        .setMessage(LocaleController.getString(R.string.OxChatDeletePresetMsg))
                        .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                        .setPositiveButton(LocaleController.getString(R.string.Delete), (d, w) -> {
                            OxChatTheme.deletePreset(name);
                            toast(LocaleController.formatString("OxChatPresetDeleted",
                                    R.string.OxChatPresetDeleted, name));
                            rebuild();
                        })
                        .show();
                return true;
            }
        }
        return false;
    }

    private void askPresetName() {
        if (getParentActivity() == null) return;
        EditText input = new EditText(getParentActivity());
        input.setHint(LocaleController.getString(R.string.OxChatPresetNameHint));
        input.setSingleLine(true);
        FrameLayout wrap = new FrameLayout(getParentActivity());
        wrap.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        wrap.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        new AlertDialog.Builder(getParentActivity())
                .setTitle(LocaleController.getString(R.string.OxChatSavePreset))
                .setView(wrap)
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setPositiveButton(LocaleController.getString(R.string.Save), (d, w) -> {
                    try {
                        String name = input.getText().toString().trim();
                        OxChatTheme.savePreset(name);
                        toast(LocaleController.formatString("OxChatPresetSaved",
                                R.string.OxChatPresetSaved, name));
                        rebuild();
                    } catch (Throwable t) {
                        toast(LocaleController.formatString("OxChatImportFailed",
                                R.string.OxChatImportFailed, t.getMessage()));
                    }
                })
                .show();
    }

    private void pickJson() {
        if (getParentActivity() == null) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_IMPORT);
    }

    private void pickImage() {
        if (getParentActivity() == null) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, REQ_BG);
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode != android.app.Activity.RESULT_OK || data == null) return;
        Uri uri = data.getData();
        Context ctx = getParentActivity();
        if (uri == null || ctx == null) return;
        if (requestCode == REQ_IMPORT) {
            new Thread(() -> {
                String msg;
                try {
                    InputStream in = ctx.getContentResolver().openInputStream(uri);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0 && bos.size() < 256 * 1024) {
                        bos.write(buf, 0, Math.min(n, 256 * 1024 - bos.size()));
                    }
                    in.close();
                    String name = OxChatTheme.importJson(new String(bos.toByteArray(), StandardCharsets.UTF_8));
                    msg = LocaleController.formatString("OxChatImported",
                            R.string.OxChatImported, name == null || name.isEmpty() ? "JSON" : name);
                } catch (Throwable t) {
                    msg = LocaleController.formatString("OxChatImportFailed",
                            R.string.OxChatImportFailed, t.getMessage());
                }
                final String m = msg;
                AndroidUtilities.runOnUIThread(() -> {
                    toast(m);
                    rebuild();
                });
            }, "ox-chat-theme-import").start();
        } else if (requestCode == REQ_BG) {
            new Thread(() -> {
                String msg;
                try {
                    OxChatTheme.setBackgroundImage(ctx.getContentResolver().openInputStream(uri));
                    msg = LocaleController.getString(R.string.OxChatBgPicked);
                } catch (Throwable t) {
                    msg = LocaleController.formatString("OxChatImportFailed",
                            R.string.OxChatImportFailed, t.getMessage());
                }
                final String m = msg;
                AndroidUtilities.runOnUIThread(() -> {
                    toast(m);
                    rebuild();
                });
            }, "ox-chat-bg").start();
        }
    }

    private void rebuild() {
        if (listView != null) listView.adapter.update(true);
    }

    private void toast(String s) {
        if (getParentActivity() != null) {
            Toast.makeText(getParentActivity(), s, Toast.LENGTH_SHORT).show();
        }
    }

    private static int next(int[] values, int current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) return values[(i + 1) % values.length];
        }
        return values[0];
    }

    private static int nextInt(int[] values, int current) {
        return next(values, current);
    }

    private static float nextFloat(float[] values, float current) {
        for (int i = 0; i < values.length; i++) {
            if (Math.abs(values[i] - current) < 0.001f) return values[(i + 1) % values.length];
        }
        return values[0];
    }
}
