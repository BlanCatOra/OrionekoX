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
import org.telegram.ox.OxHarnessAdapter;
import org.telegram.ox.OxHarnessTransport;
import org.telegram.ox.OxMode;
import org.telegram.ox.OxModelHub;
import org.telegram.ox.OxProviderPreset;
import org.telegram.ox.OxSkillStore;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.EditTextCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
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
    private EditTextCell temperatureCell;
    private EditTextCell topPCell;
    private EditTextCell maxTokensCell;
    private EditTextCell contextCell;
    private EditTextCell memoryThresholdCell;
    private TextSettingsCell memoryModeRow;
    private TextSettingsCell harnessRow;
    private EditTextCell harnessSessionCell;
    private EditTextCell harnessCompactCell;
    private EditTextCell harnessEndpointCell;
    private EditTextCell harnessTokenCell;
    private EditTextCell harnessCwdCell;
    private EditTextCell harnessModelCell;
    private EditTextCell harnessAgentCell;
    private TextSettingsCell harnessContextRow;
    private TextSettingsCell harnessMirrorRow;
    private TextSettingsCell harnessTestRow;
    private boolean harnessMirror;
    private String harness = "api";
    private EditTextCell presenceCell;
    private EditTextCell frequencyCell;
    private EditTextCell topKCell;
    private EditTextCell seedCell;
    private EditTextCell stopCell;
    private EditTextCell extraBodyCell;
    private TextSettingsCell reasoningRow;
    private TextSettingsCell skillsRow;
    private TextSettingsCell toolChoiceRow;
    private TextSettingsCell parallelRow;
    private String reasoningEffort = "auto";
    private String toolChoice = "auto";
    private String memoryMode = "normal";
    private boolean parallelTools = true;
    private ImageView providerIcon;
    private TextView providerName;
    private TextView providerSub;
    private LinearLayout headersList;
    private TextSettingsCell keyUrlRow;
    private TextSettingsCell modelSuggestRow;
    private TextSettingsCell fetchModelsRow;
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
        memoryMode = agent == null || agent.memoryMode == null || agent.memoryMode.isEmpty()
                ? "normal" : agent.memoryMode;
        harness = agent == null || agent.harness == null || agent.harness.isEmpty()
                ? "api" : agent.harness;
        harnessMirror = agent != null && agent.harnessMirror;
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(LocaleController.getString(isNew ? R.string.OxAgentNewTitle : R.string.OxAgentSettings));
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

        reasoningEffort = agent.reasoningEffort == null || agent.reasoningEffort.isEmpty() ? "auto" : agent.reasoningEffort;
        toolChoice = agent.toolChoice == null || agent.toolChoice.isEmpty() ? "auto" : agent.toolChoice;
        parallelTools = agent.parallelToolCalls;

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
        LinearLayout avatarCard = section(context);
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
        avatarBg.setCornerRadius(AndroidUtilities.dp(16));
        avatarCard.setBackground(avatarBg);
        avatarCard.setClipToOutline(true);
        avatarCard.addView(avatarBlock, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(avatarCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 12, 12, 0));

        // basic
        content.addView(sectionHeader(context, LocaleController.getString(R.string.OxAgentSectionBasic)));
        LinearLayout basic = section(context);
        nameCell = addEditCell(context, basic, LocaleController.getString(R.string.OxAgentNameHint2), agent.name, false, true);
        nameCell.editText.setTypeface(AndroidUtilities.bold());
        remarkCell = addEditCell(context, basic, LocaleController.getString(R.string.OxAgentRemark), agent.remark, false, false);
        content.addView(basic);

        // skills
        content.addView(sectionHeader(context, LocaleController.getString(R.string.OxAgentSkills)));
        LinearLayout skillsGroup = section(context);
        skillsRow = new TextSettingsCell(context);
        skillsRow.setTextAndValue(LocaleController.getString(R.string.OxAgentSkills), skillsSummary(), false);
        skillsRow.setTextValueColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        skillsRow.setOnClickListener(v -> showSkillsPicker());
        skillsGroup.addView(skillsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(skillsGroup);

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
        baseUrlCell = addEditCell(context, providerGroup, LocaleController.getString(R.string.OxAgentEndpointLabel), agent.baseUrl, false, true);
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

        fetchModelsRow = new TextSettingsCell(context);
        fetchModelsRow.setText(LocaleController.getString(R.string.OxAgentFetchModels), true);
        fetchModelsRow.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        fetchModelsRow.setOnClickListener(v -> fetchModels());
        providerGroup.addView(fetchModelsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

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

        content.addView(sectionHeader(context, LocaleController.getString(R.string.OxAgentHarness)));
        LinearLayout harnessGroup = section(context);
        harnessGroup.addView(sectionLabel(context, LocaleController.getString(R.string.OxAgentHarnessInfo)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        harnessRow = new TextSettingsCell(context);
        harnessRow.setTextAndValue(LocaleController.getString(R.string.OxAgentHarness), OxHarnessAdapter.label(harness), false);
        harnessRow.setOnClickListener(v -> showHarnessPicker());
        harnessGroup.addView(harnessRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        harnessSessionCell = addEditCell(context, harnessGroup,
                LocaleController.getString(R.string.OxAgentHarnessSessionHint),
                agent.harnessSession, false, true);
        harnessCompactCell = addEditCell(context, harnessGroup,
                LocaleController.getString(R.string.OxAgentHarnessCompactHint),
                agent.harnessCompactCommand, true, false);
        harnessGroup.addView(sectionLabel(context,
                        LocaleController.getString(R.string.OxAgentHarnessEndpoint)),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        harnessEndpointCell = addEditCell(context, harnessGroup,
                LocaleController.getString(R.string.OxAgentHarnessEndpointHint),
                agent.harnessEndpoint, false, true);
        harnessGroup.addView(sectionLabel(context,
                        LocaleController.getString(R.string.OxAgentHarnessToken)),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        harnessTokenCell = addEditCell(context, harnessGroup,
                LocaleController.getString(R.string.OxAgentHarnessTokenHint),
                agent.harnessToken, false, true);
        harnessTokenCell.editText.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        harnessGroup.addView(sectionLabel(context,
                        LocaleController.getString(R.string.OxAgentHarnessCwd)),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        harnessCwdCell = addEditCell(context, harnessGroup,
                LocaleController.getString(R.string.OxAgentHarnessCwdHint),
                agent.harnessCwd, false, true);
        harnessGroup.addView(sectionLabel(context,
                        LocaleController.getString(R.string.OxAgentHarnessModel)),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        harnessModelCell = addEditCell(context, harnessGroup,
                LocaleController.getString(R.string.OxAgentHarnessModelHint),
                agent.harnessModel, false, true);
        harnessGroup.addView(sectionLabel(context,
                        LocaleController.getString(R.string.OxAgentHarnessAgent)),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        harnessAgentCell = addEditCell(context, harnessGroup,
                LocaleController.getString(R.string.OxAgentHarnessAgentHint),
                agent.harnessAgent, false, false);
        harnessContextRow = new TextSettingsCell(context);
        harnessContextRow.setTextAndValue(
                LocaleController.getString(R.string.OxAgentHarnessContext),
                LocaleController.getString(R.string.OxAgentHarnessContextIdle), false);
        harnessContextRow.setTextValueColor(
                getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        harnessContextRow.setOnClickListener(v -> refreshHarnessContext());
        harnessGroup.addView(harnessContextRow,
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        harnessMirrorRow = new TextSettingsCell(context);
        updateHarnessMirrorRow();
        harnessMirrorRow.setOnClickListener(v -> {
            harnessMirror = !harnessMirror;
            updateHarnessMirrorRow();
        });
        harnessGroup.addView(harnessMirrorRow,
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        harnessTestRow = new TextSettingsCell(context);
        harnessTestRow.setTextAndValue(LocaleController.getString(R.string.OxAgentHarnessTest),
                LocaleController.getString(R.string.OxAgentHarnessTestIdle), false);
        harnessTestRow.setTextValueColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        harnessTestRow.setOnClickListener(v -> testHarnessConnection());
        harnessGroup.addView(harnessTestRow,
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(harnessGroup);
        updateHarnessVisibility();

        LinearLayout advancedHeaderCard = section(context);
        TextSettingsCell advancedToggle = new TextSettingsCell(context);
        advancedToggle.setTextAndValue(LocaleController.getString(R.string.OxAgentAdvanced),
                LocaleController.getString(R.string.OxAgentTemperature) + " " + agent.temperature + " · Top P " + agent.topP, true);
        advancedHeaderCard.addView(advancedToggle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(advancedHeaderCard);
        LinearLayout advanced = section(context);
        advanced.addView(sectionLabel(context, LocaleController.getString(R.string.OxAgentTemperature)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        temperatureCell = addEditCell(context, advanced, "0.0 – 2.0", String.valueOf(agent.temperature), false, true);
        temperatureCell.editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        advanced.addView(sectionLabel(context, LocaleController.getString(R.string.OxAgentTopP)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        topPCell = addEditCell(context, advanced, "0.0 – 1.0", String.valueOf(agent.topP), false, true);
        topPCell.editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        advanced.addView(sectionLabel(context, LocaleController.getString(R.string.OxAgentMaxTokens)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        maxTokensCell = addEditCell(context, advanced, LocaleController.getString(R.string.OxAgentAutoHint), agent.maxTokens > 0 ? String.valueOf(agent.maxTokens) : "", false, true);
        maxTokensCell.editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        advanced.addView(sectionLabel(context, LocaleController.getString(R.string.OxAgentContextLength)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        contextCell = addEditCell(context, advanced, LocaleController.getString(R.string.OxAgentAutoHint), agent.contextLength > 0 ? String.valueOf(agent.contextLength) : "", false, true);
        contextCell.editText.setInputType(InputType.TYPE_CLASS_NUMBER);

        advanced.addView(sectionLabel(context, LocaleController.getString(R.string.OxAgentMemoryMode)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        memoryModeRow = new TextSettingsCell(context);
        memoryModeRow.setTextAndValue(LocaleController.getString(R.string.OxAgentMemoryMode), memoryModeLabel(), false);
        memoryModeRow.setOnClickListener(v -> {
            memoryMode = nextMemoryMode(memoryMode);
            memoryModeRow.setTextAndValue(LocaleController.getString(R.string.OxAgentMemoryMode), memoryModeLabel(), false);
        });
        advanced.addView(memoryModeRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        advanced.addView(sectionLabel(context, LocaleController.getString(R.string.OxAgentMemoryThreshold)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        memoryThresholdCell = addEditCell(context, advanced, LocaleController.getString(R.string.OxAgentMemoryThresholdHint), String.valueOf(Math.max(10, agent.memoryThreshold)), false, true);
        memoryThresholdCell.editText.setInputType(InputType.TYPE_CLASS_NUMBER);

        advanced.addView(sectionLabel(context, LocaleController.getString(R.string.OxAgentPresencePenalty)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        presenceCell = addEditCell(context, advanced, "0.0 – 2.0", agent.presencePenalty != 0 ? String.valueOf(agent.presencePenalty) : "", false, true);
        presenceCell.editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        advanced.addView(sectionLabel(context, LocaleController.getString(R.string.OxAgentFrequencyPenalty)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        frequencyCell = addEditCell(context, advanced, "0.0 – 2.0", agent.frequencyPenalty != 0 ? String.valueOf(agent.frequencyPenalty) : "", false, true);
        frequencyCell.editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        advanced.addView(sectionLabel(context, LocaleController.getString(R.string.OxAgentTopK)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        topKCell = addEditCell(context, advanced, LocaleController.getString(R.string.OxAgentAutoHint), agent.topK > 0 ? String.valueOf(agent.topK) : "", false, true);
        topKCell.editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        advanced.addView(sectionLabel(context, LocaleController.getString(R.string.OxAgentSeed)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        seedCell = addEditCell(context, advanced, LocaleController.getString(R.string.OxAgentSeedHint), agent.seed >= 0 ? String.valueOf(agent.seed) : "", false, true);
        seedCell.editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        advanced.addView(sectionLabel(context, LocaleController.getString(R.string.OxAgentStop)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        stopCell = addEditCell(context, advanced, LocaleController.getString(R.string.OxAgentStopHint), agent.stop, false, true);

        reasoningRow = new TextSettingsCell(context);
        reasoningRow.setTextAndValue(LocaleController.getString(R.string.OxAgentReasoningEffort), reasoningLabel(), true);
        reasoningRow.setOnClickListener(v -> {
            reasoningEffort = nextReasoning(reasoningEffort);
            reasoningRow.setTextAndValue(LocaleController.getString(R.string.OxAgentReasoningEffort), reasoningLabel(), true);
        });
        advanced.addView(reasoningRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        toolChoiceRow = new TextSettingsCell(context);
        toolChoiceRow.setTextAndValue(LocaleController.getString(R.string.OxAgentToolChoice), toolChoiceLabel(), true);
        toolChoiceRow.setOnClickListener(v -> {
            toolChoice = nextToolChoice(toolChoice);
            toolChoiceRow.setTextAndValue(LocaleController.getString(R.string.OxAgentToolChoice), toolChoiceLabel(), true);
        });
        advanced.addView(toolChoiceRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        parallelRow = new TextSettingsCell(context);
        parallelRow.setTextAndValue(LocaleController.getString(R.string.OxAgentParallelTools), LocaleController.getString(parallelTools ? R.string.OxAgentOn : R.string.OxAgentOff), true);
        parallelRow.setOnClickListener(v -> {
            parallelTools = !parallelTools;
            parallelRow.setTextAndValue(LocaleController.getString(R.string.OxAgentParallelTools), LocaleController.getString(parallelTools ? R.string.OxAgentOn : R.string.OxAgentOff), true);
        });
        advanced.addView(parallelRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        advanced.addView(sectionLabel(context, LocaleController.getString(R.string.OxAgentExtraBody)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        extraBodyCell = new EditTextCell(context, "{\"min_p\": 0.1}", true);
        extraBodyCell.setText(agent.extraBody == null ? "" : agent.extraBody);
        extraBodyCell.editText.setTextSize(15);
        extraBodyCell.setDivider(false);
        advanced.addView(extraBodyCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 100));
        advanced.setVisibility(View.GONE);
        content.addView(advanced);
        final LinearLayout advancedRef = advanced;
        advancedToggle.setOnClickListener(v -> {
            boolean show = advancedRef.getVisibility() != View.VISIBLE;
            if (show) {
                advancedRef.setVisibility(View.VISIBLE);
                advancedRef.setAlpha(0f);
                advancedRef.setTranslationY(-AndroidUtilities.dp(4));
                advancedRef.animate().alpha(1f).translationY(0f).setDuration(180)
                        .setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
                advancedToggle.setTextAndValue(LocaleController.getString(R.string.OxAgentAdvanced),
                        LocaleController.getString(R.string.OxAgentCollapse), true);
            } else {
                advancedRef.animate().alpha(0f).translationY(-AndroidUtilities.dp(4)).setDuration(140)
                        .withEndAction(() -> advancedRef.setVisibility(View.GONE)).start();
                advancedToggle.setTextAndValue(LocaleController.getString(R.string.OxAgentAdvanced),
                        advancedSummary(), true);
            }
        });

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
        mdGroup.addView(mdCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 150));
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

    private void applyProvider(OxProviderPreset p) {
        agent.providerId = p.id;
        if (p.baseUrl != null && !p.baseUrl.isEmpty()) {
            baseUrlCell.setText(p.baseUrl);
            agent.baseUrl = p.baseUrl;
        }
        if (p.defaultModel != null && !p.defaultModel.isEmpty()) {
            modelCell.setText(p.defaultModel);
            agent.model = p.defaultModel;
        }
        if (p.headers != null && p.headers.length > 0) {
            JSONArray headers = agent.headers == null ? new JSONArray() : agent.headers;
            for (String[] pair : p.headers) {
                boolean exists = false;
                for (int i = 0; i < headers.length(); i++) {
                    JSONArray existing = headers.optJSONArray(i);
                    if (existing != null && pair[0].equalsIgnoreCase(existing.optString(0, ""))) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    headers.put(new JSONArray().put(pair[0]).put(pair[1]));
                }
            }
            agent.headers = headers;
            rebuildHeaders();
        }
        updateProviderInfo();
    }

    private void showHarnessPicker() {
        if (getParentActivity() == null) return;
        final java.util.List<String> ids = new java.util.ArrayList<>(OxHarnessAdapter.ids());
        CharSequence[] labels = new CharSequence[ids.size()];
        for (int i = 0; i < ids.size(); i++) labels[i] = OxHarnessAdapter.label(ids.get(i));
        new AlertDialog.Builder(getParentActivity())
                .setTitle(LocaleController.getString(R.string.OxAgentHarness))
                .setItems(labels, (d, which) -> {
                    String next = ids.get(which);
                    if (!next.equals(harness)) {
                        // A native session is harness-specific: reusing an
                        // OpenClaw bridge session for CommandCode (or vice
                        // versa) would resume the wrong transcript.
                        harness = next;
                        if (harnessSessionCell != null) harnessSessionCell.setText("");
                    }
                    harnessRow.setTextAndValue(LocaleController.getString(R.string.OxAgentHarness),
                            OxHarnessAdapter.label(harness), false);
                    updateHarnessVisibility();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private void updateHarnessVisibility() {
        boolean api = OxHarnessAdapter.API.equals(harness);
        boolean bridge = OxHarnessTransport.supports(harness);
        int sessionVisible = api ? View.GONE : View.VISIBLE;
        int bridgeVisible = bridge ? View.VISIBLE : View.GONE;
        if (harnessSessionCell != null) harnessSessionCell.setVisibility(sessionVisible);
        if (harnessCompactCell != null) {
            harnessCompactCell.setVisibility(api || bridge ? View.GONE : View.VISIBLE);
        }
        if (harnessEndpointCell != null) harnessEndpointCell.setVisibility(bridgeVisible);
        if (harnessTokenCell != null) harnessTokenCell.setVisibility(bridgeVisible);
        if (harnessCwdCell != null) harnessCwdCell.setVisibility(bridgeVisible);
        if (harnessModelCell != null) harnessModelCell.setVisibility(bridgeVisible);
        if (harnessAgentCell != null) harnessAgentCell.setVisibility(bridgeVisible);
        if (harnessContextRow != null) harnessContextRow.setVisibility(bridgeVisible);
        if (harnessMirrorRow != null) harnessMirrorRow.setVisibility(bridgeVisible);
        if (harnessTestRow != null) harnessTestRow.setVisibility(bridgeVisible);
    }

    private void updateHarnessMirrorRow() {
        if (harnessMirrorRow == null) return;
        harnessMirrorRow.setTextAndValue(
                LocaleController.getString(R.string.OxAgentHarnessMirror),
                LocaleController.getString(harnessMirror
                        ? R.string.OxAgentOn : R.string.OxAgentOff), false);
        harnessMirrorRow.setTextValueColor(getThemedColor(harnessMirror
                ? Theme.key_chats_actionBackground : Theme.key_windowBackgroundWhiteGrayText));
    }

    private void refreshHarnessContext() {
        if (harnessContextRow == null) return;
        harnessContextRow.setTextAndValue(
                LocaleController.getString(R.string.OxAgentHarnessContext),
                LocaleController.getString(R.string.OxAgentHarnessContextLoading), false);
        harnessContextRow.setTextValueColor(
                getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        OxHarnessTransport.context(buildFromFields(), (ok, message) ->
                AndroidUtilities.runOnUIThread(() -> {
                    if (harnessContextRow == null || getParentActivity() == null) return;
                    harnessContextRow.setTextAndValue(
                            LocaleController.getString(R.string.OxAgentHarnessContext),
                            message, false);
                    harnessContextRow.setTextValueColor(getThemedColor(ok
                            ? Theme.key_chats_actionBackground : Theme.key_text_RedRegular));
                }));
    }

    private void testHarnessConnection() {
        if (harnessTestRow == null) return;
        harnessTestRow.setTextAndValue(LocaleController.getString(R.string.OxAgentHarnessTest),
                LocaleController.getString(R.string.OxAgentHarnessTesting), false);
        harnessTestRow.setTextValueColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        OxHarnessTransport.health(buildFromFields(), (ok, message) ->
                AndroidUtilities.runOnUIThread(() -> {
                    if (harnessTestRow == null || getParentActivity() == null) return;
                    harnessTestRow.setTextAndValue(
                            LocaleController.getString(R.string.OxAgentHarnessTest),
                            ok ? LocaleController.getString(R.string.OxAgentHarnessTestOk)
                               : LocaleController.getString(R.string.OxAgentHarnessTestFail),
                            false);
                    harnessTestRow.setTextValueColor(getThemedColor(ok
                            ? Theme.key_chats_actionBackground : Theme.key_text_RedRegular));
                    Toast.makeText(getParentActivity(), message, Toast.LENGTH_LONG).show();
                }));
    }

    private void showProviderPicker() {
        if (getParentActivity() == null) return;
        final Context ctx = getParentActivity();
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));

        TextView title = new TextView(ctx);
        title.setText(LocaleController.getString(R.string.OxAgentChooseProvider));
        title.setTextSize(17);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        title.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(16), AndroidUtilities.dp(21), AndroidUtilities.dp(8));
        content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        EditText search = new EditText(ctx);
        search.setHint(LocaleController.getString(R.string.OxAgentSearchProviders));
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        search.setHintTextColor(getThemedColor(Theme.key_dialogTextGray3));
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(getThemedColor(Theme.key_windowBackgroundGray));
        searchBg.setCornerRadius(AndroidUtilities.dp(12));
        search.setBackground(searchBg);
        search.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(8));
        content.addView(search, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 21, 4, 21, 8));

        ScrollView scroll = new ScrollView(ctx);
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final BottomSheet[] sheetHolder = new BottomSheet[1];
        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            list.removeAllViews();
            String q = search.getText().toString().trim().toLowerCase();
            java.util.List<OxProviderPreset> shown = new java.util.ArrayList<>();
            for (OxProviderPreset p : OxProviderPreset.ALL) {
                if (q.isEmpty() || p.name.toLowerCase().contains(q)
                        || p.id.toLowerCase().contains(q)
                        || (p.baseUrl != null && p.baseUrl.toLowerCase().contains(q))) {
                    shown.add(p);
                }
            }
            for (int i = 0; i < shown.size(); i++) {
                final OxProviderPreset p = shown.get(i);
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(AndroidUtilities.dp(56));
                row.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(9), AndroidUtilities.dp(18), AndroidUtilities.dp(9));
                row.setBackground(Theme.getSelectorDrawable(false));
                ImageView icon = new ImageView(ctx);
                icon.setImageResource(p.iconRes);
                row.addView(icon, LayoutHelper.createLinear(30, 30, Gravity.CENTER_VERTICAL));
                LinearLayout texts = new LinearLayout(ctx);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(8), 0);
                TextView name = new TextView(ctx);
                name.setText(p.name);
                name.setTextSize(16);
                name.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
                texts.addView(name);
                TextView sub = new TextView(ctx);
                sub.setText(p.defaultModel == null || p.defaultModel.isEmpty() ? p.baseUrl : p.defaultModel);
                sub.setTextSize(12);
                sub.setTextColor(getThemedColor(Theme.key_dialogTextGray3));
                texts.addView(sub);
                row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
                if (p.id.equals(agent.providerId)) {
                    TextView check = new TextView(ctx);
                    check.setText("✓");
                    check.setTextSize(18);
                    check.setTypeface(AndroidUtilities.bold());
                    check.setTextColor(getThemedColor(Theme.key_chats_actionBackground));
                    row.addView(check, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
                }
                row.setOnClickListener(v -> {
                    applyProvider(p);
                    if (sheetHolder[0] != null) sheetHolder[0].dismiss();
                });
                list.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                if (i < shown.size() - 1) {
                    View divider = new View(ctx);
                    divider.setBackgroundColor(getThemedColor(Theme.key_divider));
                    LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1);
                    lp.leftMargin = AndroidUtilities.dp(62);
                    list.addView(divider, lp);
                }
            }
        };
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) { render[0].run(); }
        });
        render[0].run();

        int heightPx = Math.max(AndroidUtilities.dp(260), (int) (AndroidUtilities.displaySize.y * 0.5f) - AndroidUtilities.dp(120));
        content.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, heightPx));
        BottomSheet.Builder builder = new BottomSheet.Builder(ctx);
        builder.setCustomView(content);
        sheetHolder[0] = builder.show();
    }

    private void fetchModels() {
        if (fetchModelsRow == null) return;
        fetchModelsRow.setTextAndValue(LocaleController.getString(R.string.OxAgentFetchModels),
                LocaleController.getString(R.string.OxAgentFetching), false);
        OxModelHub.fetchModels(buildFromFields(), (ids, error) -> {
            if (getParentActivity() == null || fetchModelsRow == null) return;
            if (error != null || ids == null || ids.isEmpty()) {
                fetchModelsRow.setTextAndValue(LocaleController.getString(R.string.OxAgentFetchModels),
                        LocaleController.getString(R.string.OxAgentNoModels), false);
                toast(error != null ? error : LocaleController.getString(R.string.OxAgentNoModels));
                return;
            }
            fetchModelsRow.setTextAndValue(LocaleController.getString(R.string.OxAgentFetchModels),
                    ids.size() + " models", false);
            final CharSequence[] items = new CharSequence[ids.size()];
            for (int i = 0; i < ids.size(); i++) items[i] = ids.get(i);
            new AlertDialog.Builder(getParentActivity())
                    .setItems(items, (d, which) -> {
                        modelCell.setText(ids.get(which));
                        agent.model = ids.get(which);
                        updateProviderInfo();
                    })
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .show();
        });
    }

    private void toast(String s) {
        if (getParentActivity() != null) {
            Toast.makeText(getParentActivity(), s, Toast.LENGTH_SHORT).show();
        }
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

    private String skillsSummary() {
        int n = agent != null && agent.skillIds != null ? agent.skillIds.length() : 0;
        return n <= 0 ? LocaleController.getString(R.string.OxAgentSkillsNone)
                : LocaleController.formatString("OxAgentSkillsCount", R.string.OxAgentSkillsCount, n);
    }

    private void showSkillsPicker() {
        if (getParentActivity() == null || agent == null) return;
        java.util.List<OxSkillStore.Skill> skills = OxSkillStore.list();
        if (skills.isEmpty()) {
            Toast.makeText(getParentActivity(), LocaleController.getString(R.string.OxSkillsEmpty), Toast.LENGTH_LONG).show();
            return;
        }
        final android.widget.CheckBox[] boxes = new android.widget.CheckBox[skills.size()];
        LinearLayout list = new LinearLayout(getParentActivity());
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(20);
        list.setPadding(pad, AndroidUtilities.dp(8), pad, 0);
        for (int i = 0; i < skills.size(); i++) {
            OxSkillStore.Skill s = skills.get(i);
            android.widget.CheckBox box = new android.widget.CheckBox(getParentActivity());
            box.setText(s.name + (s.description == null || s.description.isEmpty() ? "" : " — " + s.description));
            box.setTextSize(14);
            box.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            for (int j = 0; agent.skillIds != null && j < agent.skillIds.length(); j++) {
                String id = agent.skillIds.optString(j, "");
                if (id.equals(s.id) || id.equals(s.name)) box.setChecked(true);
            }
            boxes[i] = box;
            list.addView(box, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        ScrollView scroll = new ScrollView(getParentActivity());
        scroll.addView(list);
        new AlertDialog.Builder(getParentActivity())
                .setTitle(LocaleController.getString(R.string.OxAgentSkills))
                .setView(scroll)
                .setPositiveButton(LocaleController.getString(R.string.OxWorkspaceSave), (d, w) -> {
                    org.json.JSONArray ids = new org.json.JSONArray();
                    for (int i = 0; i < boxes.length; i++) {
                        if (boxes[i].isChecked()) ids.put(skills.get(i).id);
                    }
                    agent.skillIds = ids;
                    skillsRow.setTextAndValue(LocaleController.getString(R.string.OxAgentSkills), skillsSummary(), false);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private String advancedSummary() {
        String temp = temperatureCell == null ? String.valueOf(agent.temperature) : temperatureCell.getText().toString().trim();
        String topP = topPCell == null ? String.valueOf(agent.topP) : topPCell.getText().toString().trim();
        if (temp.isEmpty()) temp = String.valueOf(agent.temperature);
        if (topP.isEmpty()) topP = String.valueOf(agent.topP);
        return LocaleController.getString(R.string.OxAgentTemperature) + " " + temp + " · Top P " + topP;
    }

    private String memoryModeLabel() {
        if ("off".equals(memoryMode)) return LocaleController.getString(R.string.OxAgentMemoryModeOff);
        if ("vector".equals(memoryMode)) return LocaleController.getString(R.string.OxAgentMemoryModeVector);
        return LocaleController.getString(R.string.OxAgentMemoryModeNormal);
    }

    private String nextMemoryMode(String value) {
        if ("off".equals(value)) return "normal";
        if ("normal".equals(value)) return "vector";
        return "off";
    }

    private String reasoningLabel() {
        switch (reasoningEffort) {
            case "low": return LocaleController.getString(R.string.OxAgentReasoningLow);
            case "medium": return LocaleController.getString(R.string.OxAgentReasoningMedium);
            case "high": return LocaleController.getString(R.string.OxAgentReasoningHigh);
            default: return LocaleController.getString(R.string.OxAgentReasoningAuto);
        }
    }

    private String nextReasoning(String value) {
        if ("auto".equals(value)) return "low";
        if ("low".equals(value)) return "medium";
        if ("medium".equals(value)) return "high";
        return "auto";
    }

    private String toolChoiceLabel() {
        switch (toolChoice) {
            case "required": return LocaleController.getString(R.string.OxAgentToolRequired);
            case "none": return LocaleController.getString(R.string.OxAgentToolNone);
            default: return LocaleController.getString(R.string.OxAgentToolAuto);
        }
    }

    private String nextToolChoice(String value) {
        if ("auto".equals(value)) return "required";
        if ("required".equals(value)) return "none";
        return "auto";
    }

    private int parseSeed(EditTextCell cell) {
        if (cell == null) return -1;
        String s = cell.getText().toString().trim();
        if (s.isEmpty()) return -1;
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return -1;
        }
    }

    private double parseDouble(EditTextCell cell, double fallback, double min, double max) {
        if (cell == null) return fallback;
        try {
            double v = Double.parseDouble(cell.getText().toString().trim());
            return Math.max(min, Math.min(max, v));
        } catch (Exception e) {
            return fallback;
        }
    }

    private int parseInt(EditTextCell cell, int fallback, int max) {
        if (cell == null) return fallback;
        try {
            long v = Long.parseLong(cell.getText().toString().trim());
            return (int) Math.max(0, Math.min(max, v));
        } catch (Exception e) {
            return fallback;
        }
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
        a.temperature = parseDouble(temperatureCell, 0.7, 0, 2);
        a.topP = parseDouble(topPCell, 1.0, 0, 1);
        a.maxTokens = parseInt(maxTokensCell, 0, 10_000_000);
        a.contextLength = parseInt(contextCell, 0, 10_000_000);
        a.memoryMode = memoryMode;
        a.memoryThreshold = Math.max(10, Math.min(200, parseInt(memoryThresholdCell, 45, 200)));
        a.memoryRecall = agent.memoryRecall > 0 ? agent.memoryRecall : 6;
        a.harness = harness;
        a.harnessSession = harnessSessionCell == null ? "" : harnessSessionCell.getText().toString().trim();
        a.harnessCompactCommand = harnessCompactCell == null ? "" : harnessCompactCell.getText().toString().trim();
        a.harnessEndpoint = harnessEndpointCell == null ? "" : harnessEndpointCell.getText().toString().trim();
        a.harnessToken = harnessTokenCell == null ? "" : harnessTokenCell.getText().toString().trim();
        a.harnessCwd = harnessCwdCell == null ? "" : harnessCwdCell.getText().toString().trim();
        a.harnessModel = harnessModelCell == null ? "" : harnessModelCell.getText().toString().trim();
        a.harnessAgent = harnessAgentCell == null ? "" : harnessAgentCell.getText().toString().trim();
        a.harnessMirror = harnessMirror;
        a.presencePenalty = parseDouble(presenceCell, 0, 0, 2);
        a.frequencyPenalty = parseDouble(frequencyCell, 0, 0, 2);
        a.topK = parseInt(topKCell, 0, 1000);
        a.seed = parseSeed(seedCell);
        a.stop = stopCell.getText().toString().trim();
        a.reasoningEffort = reasoningEffort;
        a.toolChoice = toolChoice;
        a.parallelToolCalls = parallelTools;
        a.extraBody = extraBodyCell.getText().toString().trim();
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
        String extra = extraBodyCell == null ? "" : extraBodyCell.getText().toString().trim();
        if (!extra.isEmpty()) {
            try {
                new org.json.JSONObject(extra);
            } catch (Exception e) {
                toast(LocaleController.getString(R.string.OxAgentInvalidJson));
                return;
            }
        }
        OxAgent next = buildFromFields();
        next.skillIds = agent != null && agent.skillIds != null ? agent.skillIds : new org.json.JSONArray();
        final String oldName = agent == null ? null : agent.name;
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
        OxAgentsBridge.onAgentRenamed(next, oldName);
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
