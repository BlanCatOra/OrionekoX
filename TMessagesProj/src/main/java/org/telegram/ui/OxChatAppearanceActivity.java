package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ox.OxAgent;
import org.telegram.ox.OxAgentsBridge;
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

/** Custom vs Telegram-native appearance for local-agent conversations, global or per-agent. */
@SuppressLint("ViewConstructor")
public class OxChatAppearanceActivity extends BaseFragment {

    private static final int ID_MODE = 1;
    private static final int ID_BG_MODE = 2;
    private static final int ID_BG = 3;
    private static final int ID_BG2 = 4;
    private static final int ID_GRADIENT_DIR = 5;
    private static final int ID_BG_ALPHA = 6;
    private static final int ID_DIM = 7;
    private static final int ID_BG_IMAGE = 8;
    private static final int ID_BG_CLEAR = 9;
    private static final int ID_IN = 10;
    private static final int ID_OUT = 11;
    private static final int ID_STYLE = 12;
    private static final int ID_BORDER_COLOR = 13;
    private static final int ID_BORDER_WIDTH = 14;
    private static final int ID_TEXT_COLOR = 15;
    private static final int ID_ALPHA = 16;
    private static final int ID_RADIUS = 17;
    private static final int ID_FONT = 18;
    private static final int ID_FONT_FAMILY = 19;
    private static final int ID_CODE_FONT = 20;
    private static final int ID_FONT_IMPORT = 21;
    private static final int ID_CODE_FONT_IMPORT = 22;
    private static final int ID_IMPORT = 23;
    private static final int ID_EXPORT = 24;
    private static final int ID_SAVE = 25;
    private static final int ID_READABILITY = 26;
    private static final int ID_APPLY_ALL = 27;
    private static final int ID_RESET = 28;
    private static final int ID_NATIVE_THEME = 29;
    private static final int PRESET_BASE = 100;

    private static final int REQ_IMPORT = 501;
    private static final int REQ_BG = 502;
    private static final int REQ_FONT = 503;
    private static final int REQ_CODE_FONT = 504;
    private static final int REQ_EXPORT = 505;

    private static final int[] BG_COLORS = {
            0xFFF3F1F7, 0xFF101114, 0xFFFDF6E3, 0xFFE8F0FE, 0xFF1E1B2E, 0x00000000
    };
    private static final int[] BG2_COLORS = {
            0xFFE8E4F0, 0xFF1E1B2E, 0xFFFDF6E3, 0xFF7C5CFF, 0xFF2AABEE, 0xFF101114
    };
    private static final int[] IN_COLORS = {
            0xFFFFFFFF, 0xFFE8F5E9, 0xFFE3F2FD, 0xFFFFF3E0, 0xFFF3E5F5, 0xFF1F2430
    };
    private static final int[] OUT_COLORS = {
            0xFFEFFDDE, 0xFFDCEBFF, 0xFFFFE4EC, 0xFFFFF1D6, 0xFFF0E6FF, 0xFF2A3A2A
    };
    private static final int[] ALPHAS = {255, 220, 190, 160, 120};
    private static final int[] RADII = {6, 10, 14, 18, 22};
    private static final float[] FONTS = {0.9f, 1f, 1.1f, 1.2f, 1.3f};
    private static final int[] BG_ALPHAS = {255, 230, 205, 180, 150};
    private static final int[] DIMS = {0, 30, 60, 90, 120};
    private static final int[] BORDER_COLORS = {0, 0xFFFFFFFF, 0xFF111111, 0xFF7C5CFF, 0xFF2AABEE};
    private static final int[] BORDER_WIDTHS = {0, 1, 2, 3};
    private static final int[] TEXT_COLORS = {0, 0xFFFFFFFF, 0xFF111111, 0xFF7C5CFF};
    private static final int[] GRADIENT_DIRS = {0, 1, 2};

    public OxChatAppearanceActivity() {
    }

    public OxChatAppearanceActivity(long agentTgId) {
        this.agentTgId = agentTgId;
    }

    private UniversalRecyclerView listView;
    private long agentTgId;
    private String pendingExport;
    private String pendingImport;

    @Override
    public View createView(Context context) {
        if (getArguments() != null) {
            agentTgId = getArguments().getLong("agent_tg_id", 0);
        }
        if (agentTgId != 0) {
            OxChatTheme.openAgent(agentTgId);
        }
        String title = LocaleController.getString(R.string.OxChatAppearance);
        if (agentTgId != 0) {
            OxAgent a = OxAgentsBridge.agentByTgId(agentTgId);
            if (a != null && a.name != null && !a.name.isEmpty()) {
                title = LocaleController.getString(R.string.OxChatAgentEntry) + " · " + a.name;
            }
        }
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(title);
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
    public void onFragmentDestroy() {
        if (agentTgId != 0) OxChatTheme.closeAgent();
        super.onFragmentDestroy();
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        boolean custom = OxChatTheme.isCustom();
        int accent = getThemedColor(Theme.key_chats_actionBackground);
        int dark = getThemedColor(Theme.key_chats_actionPressedBackground);
        items.add(UItem.asHeader(agentTgId != 0
                ? LocaleController.getString(R.string.OxChatAgentEntry)
                : LocaleController.getString(R.string.OxChatAppearance)));
        if (agentTgId != 0) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.OxChatScopeAgent)));
        }
        items.add(SettingsActivity.SettingCell.Factory.of(ID_MODE, accent, dark, R.drawable.msg_theme,
                LocaleController.getString(R.string.OxChatMode),
                custom ? LocaleController.getString(R.string.OxChatModeCustom)
                        : LocaleController.getString(R.string.OxChatModeNative)));
        if (!custom) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.OxChatNativeHint)));
            items.add(SettingsActivity.SettingCell.Factory.of(ID_NATIVE_THEME, 0xFF2AABEE, 0xFF1488E1,
                    R.drawable.msg_theme, LocaleController.getString(R.string.OxChatNativeTheme),
                    LocaleController.getString(R.string.OxChatNativeThemeInfo)));
            items.add(SettingsActivity.SettingCell.Factory.of(ID_RESET, 0xFF8E8E93, 0xFF6E6E73,
                    R.drawable.msg_reset, LocaleController.getString(R.string.OxChatResetNative)));
            items.add(UItem.asShadow(null));
            return;
        }

        String bgMode = OxChatTheme.backgroundMode();
        items.add(SettingsActivity.SettingCell.Factory.of(ID_BG_MODE, accent, dark, R.drawable.msg_colors,
                LocaleController.getString(R.string.OxChatBgMode), bgModeLabel(bgMode)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_BG, OxChatTheme.background(),
                OxChatTheme.darken(OxChatTheme.background()), R.drawable.msg_colors,
                LocaleController.getString(R.string.OxChatBackground),
                OxChatTheme.background() == 0 ? LocaleController.getString(R.string.OxChatTransparent)
                        : OxChatTheme.hex(OxChatTheme.background())));
        if (OxChatTheme.BG_GRADIENT.equals(bgMode)) {
            items.add(SettingsActivity.SettingCell.Factory.of(ID_BG2, OxChatTheme.background2(),
                    OxChatTheme.darken(OxChatTheme.background2()), R.drawable.msg_colors,
                    LocaleController.getString(R.string.OxChatBgColor2), OxChatTheme.hex(OxChatTheme.background2())));
            items.add(SettingsActivity.SettingCell.Factory.of(ID_GRADIENT_DIR, accent, dark, R.drawable.msg_theme,
                    LocaleController.getString(R.string.OxChatGradientDir), gradientLabel(OxChatTheme.gradientIndex())));
        }
        items.add(SettingsActivity.SettingCell.Factory.of(ID_BG_ALPHA, accent, dark, R.drawable.msg_theme,
                LocaleController.getString(R.string.OxChatBgAlpha),
                Math.round(OxChatTheme.backgroundAlpha() * 100f / 255f) + "%"));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_DIM, accent, dark, R.drawable.msg_theme,
                LocaleController.getString(R.string.OxChatDim),
                Math.round(OxChatTheme.backgroundDim() * 100f / 255f) + "%"));
        String image = OxChatTheme.backgroundImage();
        items.add(SettingsActivity.SettingCell.Factory.of(ID_BG_IMAGE, 0xFF2AABEE, 0xFF1488E1,
                R.drawable.msg_download, LocaleController.getString(R.string.OxChatBackgroundImage),
                image == null || image.isEmpty() ? LocaleController.getString(R.string.OxChatNotSelected)
                        : new java.io.File(image).getName()));
        if (image != null && !image.isEmpty()) {
            items.add(SettingsActivity.SettingCell.Factory.of(ID_BG_CLEAR, 0xFF8E8E93, 0xFF6E6E73,
                    R.drawable.msg_delete, LocaleController.getString(R.string.OxChatBackgroundClear), null));
        }

        items.add(UItem.asHeader(LocaleController.getString(R.string.OxChatInBubble)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_IN, OxChatTheme.inBubble(),
                OxChatTheme.darken(OxChatTheme.inBubble()), R.drawable.msg_colors,
                LocaleController.getString(R.string.OxChatInBubble), OxChatTheme.hex(OxChatTheme.inBubble())));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_OUT, OxChatTheme.outBubble(),
                OxChatTheme.darken(OxChatTheme.outBubble()), R.drawable.msg_colors,
                LocaleController.getString(R.string.OxChatOutBubble), OxChatTheme.hex(OxChatTheme.outBubble())));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_STYLE, accent, dark, R.drawable.msg_theme,
                LocaleController.getString(R.string.OxChatBubbleStyle),
                OxChatTheme.STYLE_FROSTED.equals(OxChatTheme.bubbleStyle())
                        ? LocaleController.getString(R.string.OxChatStyleFrosted)
                        : LocaleController.getString(R.string.OxChatStyleSolid)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_BORDER_COLOR, accent, dark, R.drawable.msg_colors,
                LocaleController.getString(R.string.OxChatBorderColor),
                OxChatTheme.borderColor() == 0 ? LocaleController.getString(R.string.OxChatBorderNone)
                        : OxChatTheme.hex(OxChatTheme.borderColor())));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_BORDER_WIDTH, accent, dark, R.drawable.msg_theme,
                LocaleController.getString(R.string.OxChatBorderWidth),
                OxChatTheme.borderWidth() == 0 ? LocaleController.getString(R.string.OxChatBorderNone)
                        : OxChatTheme.borderWidth() + " dp"));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_TEXT_COLOR, accent, dark, R.drawable.msg_colors,
                LocaleController.getString(R.string.OxChatTextColor),
                OxChatTheme.textColor() == 0 ? LocaleController.getString(R.string.OxChatTextAuto)
                        : OxChatTheme.hex(OxChatTheme.textColor())));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_ALPHA, accent, dark, R.drawable.msg_theme,
                LocaleController.getString(R.string.OxChatOpacity),
                Math.round(OxChatTheme.bubbleAlpha() * 100f / 255f) + "%"));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_RADIUS, accent, dark, R.drawable.msg_theme,
                LocaleController.getString(R.string.OxChatRadius), OxChatTheme.cornerRadius() + " dp"));

        items.add(UItem.asHeader(LocaleController.getString(R.string.OxChatFontScale)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_FONT, accent, dark, R.drawable.msg_theme,
                LocaleController.getString(R.string.OxChatFontScale),
                String.format("%.1fx", OxChatTheme.fontScale())));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_FONT_FAMILY, accent, dark, R.drawable.msg_theme,
                LocaleController.getString(R.string.OxChatFontFamily), fontLabel(OxChatTheme.fontPath())));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_CODE_FONT, accent, dark, R.drawable.msg_theme,
                LocaleController.getString(R.string.OxChatCodeFont), codeFontLabel(OxChatTheme.codeFontPath())));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_FONT_IMPORT, 0xFF34C759, 0xFF2AA84A,
                R.drawable.msg_download, LocaleController.getString(R.string.OxChatImportFont), null));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_CODE_FONT_IMPORT, 0xFF34C759, 0xFF2AA84A,
                R.drawable.msg_download, LocaleController.getString(R.string.OxChatImportCodeFont), null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.OxChatImport)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_IMPORT, 0xFF34C759, 0xFF2AA84A,
                R.drawable.msg_download, LocaleController.getString(R.string.OxChatImport),
                LocaleController.getString(R.string.OxChatImportInfo)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_EXPORT, 0xFF2AABEE, 0xFF1488E1,
                R.drawable.msg_download, LocaleController.getString(R.string.OxChatExport), null));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_SAVE, 0xFF7C5CFF, 0xFF5B3FE0,
                R.drawable.msg_add, LocaleController.getString(R.string.OxChatSavePreset)));
        if (agentTgId != 0) {
            items.add(SettingsActivity.SettingCell.Factory.of(ID_APPLY_ALL, 0xFF7C5CFF, 0xFF5B3FE0,
                    R.drawable.msg_add, LocaleController.getString(R.string.OxChatApplyAll), null));
        }
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
        items.add(SettingsActivity.SettingCell.Factory.of(ID_READABILITY, accent, dark, R.drawable.msg_theme,
                LocaleController.getString(R.string.OxChatReadability), null));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_RESET, 0xFF8E8E93, 0xFF6E6E73,
                R.drawable.msg_reset, LocaleController.getString(R.string.OxChatResetNative)));
        items.add(UItem.asShadow(null));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_MODE) {
            if (OxChatTheme.isCustom()) OxChatTheme.setMode(OxChatTheme.MODE_NATIVE);
            else OxChatTheme.enableCustom();
            rebuild();
        } else if (item.id == ID_NATIVE_THEME) {
            presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
        } else if (item.id == ID_RESET) {
            OxChatTheme.resetNative();
            toast(LocaleController.getString(R.string.OxChatResetDone));
            rebuild();
        } else if (item.id == ID_BG_MODE) {
            String cur = OxChatTheme.backgroundMode();
            OxChatTheme.setBackgroundMode(OxChatTheme.BG_SOLID.equals(cur) ? OxChatTheme.BG_GRADIENT
                    : OxChatTheme.BG_GRADIENT.equals(cur) ? OxChatTheme.BG_IMAGE : OxChatTheme.BG_SOLID);
            rebuild();
        } else if (item.id == ID_BG) {
            OxChatTheme.setInt("background", next(BG_COLORS, OxChatTheme.background()));
            rebuild();
        } else if (item.id == ID_BG2) {
            OxChatTheme.setInt("background2", next(BG2_COLORS, OxChatTheme.background2()));
            rebuild();
        } else if (item.id == ID_GRADIENT_DIR) {
            OxChatTheme.setInt("gradient_index", next(GRADIENT_DIRS, OxChatTheme.gradientIndex()));
            rebuild();
        } else if (item.id == ID_BG_ALPHA) {
            OxChatTheme.setInt("background_alpha", next(BG_ALPHAS, OxChatTheme.backgroundAlpha()));
            rebuild();
        } else if (item.id == ID_DIM) {
            OxChatTheme.setInt("background_dim", next(DIMS, OxChatTheme.backgroundDim()));
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
        } else if (item.id == ID_STYLE) {
            OxChatTheme.setBubbleStyle(OxChatTheme.STYLE_FROSTED.equals(OxChatTheme.bubbleStyle())
                    ? OxChatTheme.STYLE_SOLID : OxChatTheme.STYLE_FROSTED);
            rebuild();
        } else if (item.id == ID_BORDER_COLOR) {
            OxChatTheme.setInt("border_color", next(BORDER_COLORS, OxChatTheme.borderColor()));
            rebuild();
        } else if (item.id == ID_BORDER_WIDTH) {
            OxChatTheme.setInt("border_width", next(BORDER_WIDTHS, OxChatTheme.borderWidth()));
            rebuild();
        } else if (item.id == ID_TEXT_COLOR) {
            OxChatTheme.setInt("text_color", next(TEXT_COLORS, OxChatTheme.textColor()));
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
        } else if (item.id == ID_FONT_FAMILY) {
            OxChatTheme.setString("font_path", nextFont(OxChatTheme.fontPath(), false));
            rebuild();
        } else if (item.id == ID_CODE_FONT) {
            OxChatTheme.setString("code_font_path", nextFont(OxChatTheme.codeFontPath(), true));
            rebuild();
        } else if (item.id == ID_FONT_IMPORT) {
            pickFont(true);
        } else if (item.id == ID_CODE_FONT_IMPORT) {
            pickFont(false);
        } else if (item.id == ID_IMPORT) {
            pickJson();
        } else if (item.id == ID_EXPORT) {
            exportTheme();
        } else if (item.id == ID_SAVE) {
            askPresetName();
        } else if (item.id == ID_APPLY_ALL) {
            OxChatTheme.applyCurrentToGlobal();
            toast(LocaleController.getString(R.string.OxChatApplyAllDone));
        } else if (item.id == ID_READABILITY) {
            showContrast();
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

    private void showContrast() {
        int bubble = OxChatTheme.inBubble();
        int text = OxChatTheme.textColor() != 0 ? OxChatTheme.textColor() : OxChatTheme.autoTextColor(bubble);
        double ratio = OxChatTheme.contrast(text, bubble);
        boolean ok = ratio >= 3.0;
        toast(LocaleController.formatString(ok ? "OxChatReadabilityOk" : "OxChatReadabilityWarn",
                ok ? R.string.OxChatReadabilityOk : R.string.OxChatReadabilityWarn, ratio));
    }

    private void exportTheme() {
        if (getParentActivity() == null) return;
        try {
            pendingExport = OxChatTheme.exportJson("OrionekoX appearance");
        } catch (Throwable t) {
            toast(LocaleController.formatString("OxChatImportFailed", R.string.OxChatImportFailed, t.getMessage()));
            return;
        }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "orioneko-theme.json");
        startActivityForResult(i, REQ_EXPORT);
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

    private void pickFont(boolean chat) {
        if (getParentActivity() == null) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, chat ? REQ_FONT : REQ_CODE_FONT);
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode != android.app.Activity.RESULT_OK) return;
        Uri uri = data == null ? null : data.getData();
        Context ctx = getParentActivity();
        if (uri == null || ctx == null) return;
        if (requestCode == REQ_IMPORT) {
            new Thread(() -> {
                try {
                    String raw = readText(ctx, uri);
                    final String rawFinal = raw;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (getParentActivity() == null) return;
                        try {
                            String summary = OxChatTheme.previewJson(rawFinal);
                            new AlertDialog.Builder(getParentActivity())
                                    .setTitle(LocaleController.getString(R.string.OxChatImportPreviewTitle))
                                    .setMessage(summary)
                                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                                    .setPositiveButton(LocaleController.getString(R.string.OxChatImportApply), (d, w) -> {
                                        try {
                                            OxChatTheme.importJson(rawFinal);
                                            toast(LocaleController.getString(R.string.OxChatImport));
                                            rebuild();
                                        } catch (Throwable t) {
                                            toast(LocaleController.formatString("OxChatImportFailed",
                                                    R.string.OxChatImportFailed, t.getMessage()));
                                        }
                                    })
                                    .show();
                        } catch (Throwable t) {
                            toast(LocaleController.formatString("OxChatImportFailed",
                                    R.string.OxChatImportFailed, t.getMessage()));
                        }
                    });
                } catch (Throwable t) {
                    AndroidUtilities.runOnUIThread(() -> toast(LocaleController.formatString(
                            "OxChatImportFailed", R.string.OxChatImportFailed, t.getMessage())));
                }
            }, "ox-chat-theme-read").start();
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
        } else if (requestCode == REQ_FONT || requestCode == REQ_CODE_FONT) {
            final boolean chat = requestCode == REQ_FONT;
            new Thread(() -> {
                String msg;
                try {
                    OxChatTheme.importFont(ctx.getContentResolver().openInputStream(uri), !chat);
                    msg = LocaleController.formatString("OxChatFontImportedDone",
                            R.string.OxChatFontImportedDone,
                            uri.getLastPathSegment() == null ? "font" : uri.getLastPathSegment());
                } catch (Throwable t) {
                    msg = LocaleController.formatString("OxChatImportFailed",
                            R.string.OxChatImportFailed, t.getMessage());
                }
                final String m = msg;
                AndroidUtilities.runOnUIThread(() -> {
                    toast(m);
                    rebuild();
                });
            }, "ox-chat-font").start();
        } else if (requestCode == REQ_EXPORT) {
            final String json = pendingExport;
            pendingExport = null;
            if (json == null) return;
            new Thread(() -> {
                String msg;
                try {
                    java.io.OutputStream out = ctx.getContentResolver().openOutputStream(uri);
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                    out.close();
                    msg = LocaleController.getString(R.string.OxChatExportDone);
                } catch (Throwable t) {
                    msg = LocaleController.formatString("OxChatImportFailed",
                            R.string.OxChatImportFailed, t.getMessage());
                }
                final String m = msg;
                AndroidUtilities.runOnUIThread(() -> toast(m));
            }, "ox-chat-export").start();
        }
    }

    private static String readText(Context ctx, Uri uri) throws Exception {
        InputStream in = ctx.getContentResolver().openInputStream(uri);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0 && bos.size() < 256 * 1024) {
            bos.write(buf, 0, Math.min(n, 256 * 1024 - bos.size()));
        }
        in.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private void rebuild() {
        if (listView != null) listView.adapter.update(true);
    }

    private void toast(String s) {
        if (getParentActivity() != null) {
            Toast.makeText(getParentActivity(), s, Toast.LENGTH_SHORT).show();
        }
    }

    private static String bgModeLabel(String mode) {
        if (OxChatTheme.BG_GRADIENT.equals(mode)) return LocaleController.getString(R.string.OxChatBgGradient);
        if (OxChatTheme.BG_IMAGE.equals(mode)) return LocaleController.getString(R.string.OxChatBgImageMode);
        return LocaleController.getString(R.string.OxChatBgSolid);
    }

    private static String gradientLabel(int index) {
        if (index == 1) return LocaleController.getString(R.string.OxChatGradientHorizontal);
        if (index == 2) return LocaleController.getString(R.string.OxChatGradientDiagonal);
        return LocaleController.getString(R.string.OxChatGradientVertical);
    }

    private static String fontLabel(String path) {
        if (path == null || path.isEmpty()) return LocaleController.getString(R.string.OxChatFontSystem);
        if ("serif".equals(path)) return LocaleController.getString(R.string.OxChatFontSerif);
        if ("monospace".equals(path)) return LocaleController.getString(R.string.OxChatFontMono);
        return LocaleController.getString(R.string.OxChatFontImported) + " · " + new java.io.File(path).getName();
    }

    private static String codeFontLabel(String path) {
        if (path == null || path.isEmpty()) return LocaleController.getString(R.string.OxChatFontSystem);
        if ("monospace".equals(path)) return LocaleController.getString(R.string.OxChatFontMono);
        return LocaleController.getString(R.string.OxChatFontImported) + " · " + new java.io.File(path).getName();
    }

    private static String nextFont(String path, boolean code) {
        if (path == null || path.isEmpty()) return code ? "monospace" : "serif";
        if (!code && "serif".equals(path)) return "monospace";
        return "";
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
