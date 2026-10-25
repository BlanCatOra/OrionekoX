package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ox.OxAgent;
import org.telegram.ox.OxAgentStore;
import org.telegram.ox.OxAgentsBridge;
import org.telegram.ox.OxMode;
import org.telegram.ox.OxModelHub;
import org.telegram.ox.OxProviderPreset;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.EditTextCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

/** Agent editor: provider presets with icons, masked key, custom headers, test connection. */
public class OxAgentProfileActivity extends BaseFragment {

    public static final int TEMPLATE_API = 0;
    public static final int TEMPLATE_LAN = 1;
    public static final int TEMPLATE_CONTAINER = 2;

    private static final int MENU_SAVE = 1;
    private static final int REQ_AVATAR = 2001;

    private OxAgent agent;
    private final String agentId;
    private final boolean isNew;

    private ImageView avatarPreview;
    private String pendingAvatarPath;
    private boolean avatarCleared;

    private EditTextCell nameCell;
    private EditTextCell remarkCell;
    private EditTextCell baseUrlCell;
    private EditTextCell apiKeyCell;
    private EditTextCell modelCell;
    private EditTextCell mdCell;
    private ImageView providerIcon;
    private TextView providerName;
    private TextView providerSub;
    private LinearLayout headersList;
    private TextSettingsCell keyUrlRow;
    private TextSettingsCell modelSuggestRow;
    private TextSettingsCell testRow;
    private ImageView keyEye;
    private boolean keyVisible;

    public OxAgentProfileActivity(String id) {
        super();
        agentId = id;
        isNew = false;
    }

    private OxAgentProfileActivity(OxAgent draft, boolean create) {
        super();
        agent = draft;
        agentId = draft.id;
        isNew = create;
    }

    /** Opens the editor with an unsaved draft; cancelling leaves no agent behind. */
    public static OxAgentProfileActivity newDraft(int template) {
        OxProviderPreset p;
        String name;
        if (template == TEMPLATE_LAN) {
            p = OxProviderPreset.byId("ollama");
            name = LocaleController.getString(R.string.OxAgentDefaultLanName);
        } else if (template == TEMPLATE_CONTAINER) {
            p = OxProviderPreset.byId("opencode");
            name = LocaleController.getString(R.string.OxAgentDefaultContainerName);
        } else {
            p = OxProviderPreset.byId("deepseek");
            name = LocaleController.getString(R.string.OxAgentDefaultApiName);
        }
        OxAgent a = new OxAgent();
        a.id = UUID.randomUUID().toString().substring(0, 8);
        a.name = name;
        a.emoji = "🤖";
        a.providerId = p.id;
        a.baseUrl = p.baseUrl;
        a.model = p.defaultModel;
        a.agentsMd = "# " + name + "\n\nYou are a helpful assistant.";
        return new OxAgentProfileActivity(a, true);
    }

    @Override
    public boolean onFragmentCreate() {
        if (agent == null) {
            agent = OxAgentStore.loadAgent(agentId);
        }
        if (agent != null && (agent.providerId == null || "custom".equals(agent.providerId))) {
            String base = agent.baseUrl == null ? "" : agent.baseUrl;
            for (OxProviderPreset p : OxProviderPreset.ALL) {
                if (!p.baseUrl.isEmpty() && base.startsWith(p.baseUrl)) {
                    agent.providerId = p.id;
                    break;
                }
            }
        }
        return agent != null && super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(isNew ? R.string.OxAgentNewTitle : R.string.OxAgentProfile));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_SAVE) {
                    onSave();
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItemWithWidth(MENU_SAVE, R.drawable.ic_ab_done, AndroidUtilities.dp(56));

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, AndroidUtilities.dp(28));
        scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = root;

        // avatar header
        LinearLayout avatarBlock = new LinearLayout(context);
        avatarBlock.setOrientation(LinearLayout.VERTICAL);
        avatarBlock.setGravity(Gravity.CENTER_HORIZONTAL);
        avatarBlock.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        avatarBlock.setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14));
        FrameLayout avatarWrap = new FrameLayout(context);
        avatarPreview = new ImageView(context);
        avatarPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarPreview.setClipToOutline(true);
        avatarPreview.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        avatarWrap.addView(avatarPreview, LayoutHelper.createFrame(84, 84, Gravity.CENTER));
        avatarBlock.addView(avatarWrap, LayoutHelper.createLinear(84, 84, Gravity.CENTER_HORIZONTAL));
        TextView avatarHint = new TextView(context);
        avatarHint.setText(LocaleController.getString(R.string.OxAgentAvatarPick));
        avatarHint.setTextSize(13);
        avatarHint.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4));
        avatarHint.setGravity(Gravity.CENTER);
        avatarHint.setPadding(0, AndroidUtilities.dp(8), 0, 0);
        avatarBlock.addView(avatarHint);
        avatarBlock.setOnClickListener(v -> openAvatarPicker());
        content.addView(avatarBlock, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // basic
        content.addView(sectionHeader(context, LocaleController.getString(R.string.OxAgentSectionBasic)));
        LinearLayout basic = section(context);
        nameCell = addEditCell(context, basic, LocaleController.getString(R.string.OxAgentNameHint2), agent.name, false, true);
        nameCell.editText.setTypeface(AndroidUtilities.bold());
        remarkCell = addEditCell(context, basic, LocaleController.getString(R.string.OxAgentRemark), agent.remark, false, false);
        content.addView(basic);

        // provider
        content.addView(sectionHeader(context, LocaleController.getString(R.string.OxAgentSectionProvider)));
        LinearLayout providerGroup = section(context);
        LinearLayout providerRow = new LinearLayout(context);
        providerRow.setOrientation(LinearLayout.HORIZONTAL);
        providerRow.setGravity(Gravity.CENTER_VERTICAL);
        providerRow.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(11), AndroidUtilities.dp(16), AndroidUtilities.dp(11));
        providerRow.setBackground(Theme.getSelectorDrawable(false));
        FrameLayout providerIconWrap = new FrameLayout(context);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.OVAL);
        iconBg.setColor(getThemedColor(Theme.key_windowBackgroundGray));
        providerIconWrap.setBackground(iconBg);
        providerIcon = new ImageView(context);
        providerIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        providerIconWrap.addView(providerIcon, LayoutHelper.createFrame(22, 22, Gravity.CENTER));
        providerRow.addView(providerIconWrap, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL));
        LinearLayout providerText = new LinearLayout(context);
        providerText.setOrientation(LinearLayout.VERTICAL);
        providerText.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(8), 0);
        providerName = new TextView(context);
        providerName.setTextSize(16);
        providerName.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        providerText.addView(providerName);
        providerSub = new TextView(context);
        providerSub.setTextSize(12);
        providerSub.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        providerText.addView(providerSub);
        providerRow.addView(providerText, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        TextView providerChevron = new TextView(context);
        providerChevron.setText("›");
        providerChevron.setTextSize(22);
        providerChevron.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        providerRow.addView(providerChevron);
        providerRow.setOnClickListener(v -> showProviderPicker());
        providerGroup.addView(providerRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        providerGroup.addView(sectionLabel(context, LocaleController.getString(R.string.OxAgentEndpointLabel)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        baseUrlCell = addEditCell(context, providerGroup, "Base URL", agent.baseUrl, false, true);
        baseUrlCell.editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        baseUrlCell.editText.setTextSize(15);

        providerGroup.addView(sectionLabel(context, "API Key"), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        apiKeyCell = addEditCell(context, providerGroup, LocaleController.getString(R.string.OxAgentApiKeyHint), agent.apiKey, false, true);
        apiKeyCell.editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        apiKeyCell.editText.setTextSize(15);
        apiKeyCell.editText.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(15), AndroidUtilities.dp(58), AndroidUtilities.dp(15));
        keyEye = new ImageView(context);
        keyEye.setImageResource(R.drawable.ox_eye);
        keyEye.setColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        keyEye.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        keyEye.setAlpha(0.55f);
        keyEye.setOnClickListener(v -> {
            keyVisible = !keyVisible;
            apiKeyCell.editText.setInputType(InputType.TYPE_CLASS_TEXT | (keyVisible ? 0 : InputType.TYPE_TEXT_VARIATION_PASSWORD) | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            apiKeyCell.editText.setSelection(apiKeyCell.editText.getText().length());
            keyEye.setAlpha(keyVisible ? 1f : 0.55f);
        });
        apiKeyCell.addView(keyEye, LayoutHelper.createFrame(44, 44, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

        providerGroup.addView(sectionLabel(context, LocaleController.getString(R.string.OxAgentModelLabel)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        modelCell = addEditCell(context, providerGroup, LocaleController.getString(R.string.OxAgentModel), agent.model, false, false);
        modelCell.editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        modelSuggestRow = new TextSettingsCell(context);
        modelSuggestRow.setTextAndValue(LocaleController.getString(R.string.OxAgentPickModel), "", true);
        modelSuggestRow.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        modelSuggestRow.setOnClickListener(v -> showModelPicker());
        providerGroup.addView(modelSuggestRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        keyUrlRow = new TextSettingsCell(context);
        keyUrlRow.setText(LocaleController.getString(R.string.OxAgentGetKey), true);
        keyUrlRow.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        keyUrlRow.setOnClickListener(v -> {
            OxProviderPreset p = OxProviderPreset.byId(agent.providerId);
            if (getParentActivity() != null && p.keyUrl != null && !p.keyUrl.isEmpty()) {
                try {
                    getParentActivity().startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(p.keyUrl)));
                } catch (Throwable ignored) {
                }
            }
        });
        providerGroup.addView(keyUrlRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        testRow = new TextSettingsCell(context);
        testRow.setTextAndValue(LocaleController.getString(R.string.OxAgentTest), LocaleController.getString(R.string.OxAgentTestIdle), false);
        testRow.setTextValueColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        testRow.setOnClickListener(v -> testConnection());
        providerGroup.addView(testRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(providerGroup);

        // headers
        content.addView(sectionHeader(context, LocaleController.getString(R.string.OxAgentHeaders)));
        LinearLayout headerGroup = section(context);
        headersList = new LinearLayout(context);
        headersList.setOrientation(LinearLayout.VERTICAL);
        headerGroup.addView(headersList, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextSettingsCell addHeader = new TextSettingsCell(context);
        addHeader.setText("＋ " + LocaleController.getString(R.string.OxAgentHeaderAdd), true);
        addHeader.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        addHeader.setOnClickListener(v -> editHeader(-1));
        headerGroup.addView(addHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(headerGroup);
        rebuildHeaders();

        // AGENTS.md
        content.addView(sectionHeader(context, "AGENTS.md"));
        LinearLayout mdGroup = section(context);
        mdCell = new EditTextCell(context, "AGENTS.md", true);
        mdCell.setText(agent.agentsMd == null ? "" : agent.agentsMd);
        mdCell.setDivider(false);
        mdCell.editText.setTextSize(15);
        mdGroup.addView(mdCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(150)));
        content.addView(mdGroup);

        if (!isNew && !agent.builtIn) {
            content.addView(sectionHeader(context, ""));
            LinearLayout danger = section(context);
            TextSettingsCell delete = new TextSettingsCell(context);
            delete.setText(LocaleController.getString(R.string.OxAgentDeleteTitle), false);
            delete.setTextColor(getThemedColor(Theme.key_text_RedRegular));
            delete.setOnClickListener(v -> confirmDelete());
            danger.addView(delete, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            content.addView(danger);
        }

        updateProviderInfo();
        renderAvatar();
        return fragmentView;
    }

    private TextView sectionLabel(Context context, String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        tv.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(10), AndroidUtilities.dp(21), AndroidUtilities.dp(2));
        return tv;
    }

    private TextView sectionHeader(Context context, String text) {
        TextView tv = new TextView(context);
        tv.setText(text == null ? "" : text);
        tv.setTextSize(13);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        tv.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(14), AndroidUtilities.dp(21), AndroidUtilities.dp(5));
        return tv;
    }

    private LinearLayout section(Context context) {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        l.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return l;
    }

    private EditTextCell addEditCell(Context context, LinearLayout parent, String hint, String value,
                                     boolean multiline, boolean divider) {
        EditTextCell cell = new EditTextCell(context, hint, multiline);
        cell.setText(value == null ? "" : value);
        cell.editText.setTextSize(17);
        cell.setDivider(divider);
        parent.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return cell;
    }

    private void updateProviderInfo() {
        OxProviderPreset p = OxProviderPreset.byId(agent.providerId);
        if (providerIcon != null) {
            providerIcon.setImageResource(p.iconRes);
        }
        if (providerName != null) {
            providerName.setText(p.name);
        }
        if (providerSub != null) {
            String sub = p.defaultModel == null || p.defaultModel.isEmpty() ? p.baseUrl : p.defaultModel;
            providerSub.setText(sub);
        }
        if (keyUrlRow != null) {
            keyUrlRow.setVisibility(p.keyUrl == null || p.keyUrl.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (modelSuggestRow != null) {
            modelSuggestRow.setVisibility(p.models.length == 0 ? View.GONE : View.VISIBLE);
            if (p.models.length > 0) {
                modelSuggestRow.setTextAndValue(LocaleController.getString(R.string.OxAgentPickModel), p.models[0], true);
            }
        }
    }

    private void showProviderPicker() {
        if (getParentActivity() == null) return;
        OxProviderPreset[] list = OxProviderPreset.ALL.toArray(new OxProviderPreset[0]);
        CharSequence[] names = new CharSequence[list.length];
        int[] icons = new int[list.length];
        for (int i = 0; i < list.length; i++) {
            names[i] = list[i].name;
            icons[i] = list[i].iconRes;
        }
        BottomSheet.Builder b = new BottomSheet.Builder(getParentActivity());
        b.setItems(names, icons, (dialog, which) -> {
            OxProviderPreset p = list[which];
            agent.providerId = p.id;
            if (p.baseUrl != null && !p.baseUrl.isEmpty()) {
                baseUrlCell.setText(p.baseUrl);
                agent.baseUrl = p.baseUrl;
            }
            if (p.defaultModel != null && !p.defaultModel.isEmpty()) {
                modelCell.setText(p.defaultModel);
                agent.model = p.defaultModel;
            }
            if (p.models.length == 0) {
                // local/custom provider: keep whatever the user typed
            }
            updateProviderInfo();
        });
        b.show();
    }

    private void showModelPicker() {
        if (getParentActivity() == null) return;
        OxProviderPreset p = OxProviderPreset.byId(agent.providerId);
        if (p.models.length == 0) {
            modelCell.editText.requestFocus();
            return;
        }
        CharSequence[] names = new CharSequence[p.models.length];
        for (int i = 0; i < p.models.length; i++) names[i] = p.models[i];
        new AlertDialog.Builder(getParentActivity())
                .setItems(names, (d, which) -> {
                    modelCell.setText(p.models[which]);
                    agent.model = p.models[which];
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private OxAgent buildFromFields() {
        OxAgent a = new OxAgent();
        a.id = agent.id;
        a.name = nameCell.getText().toString().trim();
        a.remark = remarkCell.getText().toString().trim();
        a.emoji = agent.emoji;
        a.providerId = agent.providerId;
        a.baseUrl = baseUrlCell.getText().toString().trim();
        a.apiKey = apiKeyCell.getText().toString().trim();
        a.model = modelCell.getText().toString().trim();
        a.agentsMd = mdCell.getText().toString();
        a.builtIn = agent.builtIn;
        a.pinned = agent.pinned;
        a.tgId = agent.tgId;
        a.avatarPath = agent.avatarPath;
        a.toolsPolicy = agent.toolsPolicy;
        a.headers = agent.headers;
        return a;
    }

    private void testConnection() {
        if (testRow == null) return;
        testRow.setTextAndValue(LocaleController.getString(R.string.OxAgentTest), LocaleController.getString(R.string.OxAgentTesting), false);
        testRow.setTextValueColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        OxModelHub.test(buildFromFields(), (ok, message) -> {
            if (testRow == null || getParentActivity() == null) return;
            testRow.setTextAndValue(LocaleController.getString(R.string.OxAgentTest),
                    (ok ? LocaleController.getString(R.string.OxAgentTestOk) : LocaleController.getString(R.string.OxAgentTestFail)) + " · " + message, false);
            testRow.setTextValueColor(getThemedColor(ok ? Theme.key_chats_actionBackground : Theme.key_text_RedRegular));
        });
    }

    // ---------------- headers ----------------

    private void rebuildHeaders() {
        if (headersList == null) return;
        headersList.removeAllViews();
        JSONArray headers = agent.headers == null ? new JSONArray() : agent.headers;
        for (int i = 0; i < headers.length(); i++) {
            JSONArray pair = headers.optJSONArray(i);
            if (pair == null || pair.length() < 2) continue;
            final int index = i;
            String name = pair.optString(0, "");
            String value = pair.optString(1, "");
            boolean secret = name.toLowerCase().contains("key")
                    || name.toLowerCase().contains("auth")
                    || name.toLowerCase().contains("token");
            TextSettingsCell row = new TextSettingsCell(headersList.getContext());
            row.setTextAndValue(name, secret ? "••••••" : value, true);
            row.setOnClickListener(v -> editHeader(index));
            row.setOnLongClickListener(v -> {
                confirmRemoveHeader(index);
                return true;
            });
            headersList.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
    }

    private void editHeader(int index) {
        if (getParentActivity() == null) return;
        JSONArray headers = agent.headers == null ? new JSONArray() : agent.headers;
        String oldName = "";
        String oldValue = "";
        if (index >= 0 && index < headers.length()) {
            JSONArray pair = headers.optJSONArray(index);
            if (pair != null) {
                oldName = pair.optString(0, "");
                oldValue = pair.optString(1, "");
            }
        }
        Context ctx = getParentActivity();
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), 0);
        EditText name = new EditText(ctx);
        name.setHint(LocaleController.getString(R.string.OxAgentHeaderNameHint));
        name.setText(oldName);
        name.setSingleLine(true);
        name.setTextSize(16);
        box.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        EditText value = new EditText(ctx);
        value.setHint(LocaleController.getString(R.string.OxAgentHeaderValueHint));
        value.setText(oldValue);
        value.setSingleLine(true);
        value.setTextSize(16);
        box.addView(value, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(LocaleController.getString(R.string.OxAgentHeaderAdd));
        b.setView(box);
        b.setPositiveButton(LocaleController.getString(R.string.Save), (d, w) -> {
            String n = name.getText().toString().trim();
            String v = value.getText().toString();
            if (n.isEmpty() || n.contains("\r") || n.contains("\n") || v.contains("\r") || v.contains("\n")) {
                Toast.makeText(ctx, LocaleController.getString(R.string.OxAgentHeaderInvalid), Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                JSONArray next = new JSONArray();
                for (int i = 0; i < headers.length(); i++) {
                    if (i != index) next.put(headers.getJSONArray(i));
                }
                next.put(new JSONArray().put(n).put(v));
                agent.headers = next;
                rebuildHeaders();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        if (index >= 0) {
            b.setNeutralButton(LocaleController.getString(R.string.Delete), (d, w) -> confirmRemoveHeader(index));
        }
        b.show();
    }

    private void confirmRemoveHeader(int index) {
        JSONArray headers = agent.headers == null ? new JSONArray() : agent.headers;
        if (index < 0 || index >= headers.length()) return;
        JSONArray pair = headers.optJSONArray(index);
        String name = pair == null ? "" : pair.optString(0, "");
        new AlertDialog.Builder(getParentActivity())
                .setTitle(name)
                .setMessage(LocaleController.getString(R.string.OxAgentHeaderRemove))
                .setPositiveButton(LocaleController.getString(R.string.Delete), (d, w) -> {
                    try {
                        JSONArray next = new JSONArray();
                        for (int i = 0; i < headers.length(); i++) {
                            if (i != index) next.put(headers.getJSONArray(i));
                        }
                        agent.headers = next;
                        rebuildHeaders();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    // ---------------- avatar / save ----------------

    private void renderAvatar() {
        try {
            String path = pendingAvatarPath != null ? pendingAvatarPath : agent.avatarPath;
            if (path == null || path.isEmpty() || !new File(path).exists()) {
                avatarPreview.setImageDrawable(null);
                GradientDrawable circle = new GradientDrawable();
                circle.setShape(GradientDrawable.OVAL);
                circle.setColor(getThemedColor(Theme.key_windowBackgroundGray));
                avatarPreview.setBackground(circle);
                return;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 2;
            Bitmap bmp = BitmapFactory.decodeFile(path, opts);
            if (bmp != null) {
                avatarPreview.setBackground(null);
                avatarPreview.setImageBitmap(bmp);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void openAvatarPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, REQ_AVATAR);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (requestCode != REQ_AVATAR || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        try {
            InputStream in = getParentActivity().getContentResolver().openInputStream(data.getData());
            File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "oxavatars");
            dir.mkdirs();
            File tmp = new File(dir, "pick_" + agent.id + ".jpg");
            FileOutputStream out = new FileOutputStream(tmp);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close();
            in.close();
            pendingAvatarPath = tmp.getAbsolutePath();
            avatarCleared = false;
            renderAvatar();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void onSave() {
        if (nameCell == null) return;
        String name = nameCell.getText().toString().trim();
        if (name.isEmpty()) {
            nameCell.editText.requestFocus();
            return;
        }
        OxAgent next = buildFromFields();
        next.name = name;
        next.remark = remarkCell.getText().toString().trim();
        next.baseUrl = baseUrlCell.getText().toString().trim();
        next.apiKey = apiKeyCell.getText().toString().trim();
        next.model = modelCell.getText().toString().trim();
        next.agentsMd = mdCell.getText().toString();
        if (avatarCleared) {
            if (next.avatarPath != null) new File(next.avatarPath).delete();
            next.avatarPath = null;
        } else if (pendingAvatarPath != null) {
            File master = new File(new File(ApplicationLoader.applicationContext.getFilesDir(), "oxavatars"), next.id + ".jpg");
            try {
                FileInputStream in = new FileInputStream(pendingAvatarPath);
                FileOutputStream out = new FileOutputStream(master);
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close();
                in.close();
                next.avatarPath = master.getAbsolutePath();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        OxAgentStore.saveAgent(next);
        agent = next;
        OxAgentsBridge.syncAgents(false);
        if (getParentActivity() != null && OxMode.isAgentOnly()) {
            NotificationCenter.getInstance(0).postNotificationName(NotificationCenter.updateInterfaces,
                    org.telegram.messenger.MessagesController.UPDATE_MASK_NAME
                            | org.telegram.messenger.MessagesController.UPDATE_MASK_AVATAR);
        }
        finishFragment();
    }

    private void confirmDelete() {
        if (getParentActivity() == null) return;
        new AlertDialog.Builder(getParentActivity())
                .setTitle(LocaleController.getString(R.string.OxAgentDeleteTitle))
                .setMessage(LocaleController.formatString("OxDeleteAgentConfirm", R.string.OxDeleteAgentConfirm, agent.name))
                .setPositiveButton(LocaleController.getString(R.string.Delete), (d, w) -> {
                    OxAgentsBridge.onAgentDeleted(agent.tgId);
                    OxAgentStore.deleteAgent(agent.id);
                    finishFragment();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }
}
