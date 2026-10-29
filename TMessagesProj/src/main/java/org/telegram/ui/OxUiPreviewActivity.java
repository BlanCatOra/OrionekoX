package org.telegram.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ox.OxAgent;
import org.telegram.ox.OxAgentStore;
import org.telegram.ox.OxAgentsBridge;
import org.telegram.ox.OxChatEntry;
import org.telegram.ox.OxGroupStore;
import org.telegram.ox.OxHarnessAdapter;
import org.telegram.ox.OxMemoryStore;
import org.telegram.ox.OxMode;

import java.text.SimpleDateFormat;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * OxUI (preview): a deliberately separate front end, not built from Telegram's
 * cells or action bar. It reads the same local agent chat history and sends
 * through {@link OxAgentsBridge#sendFromOxUi}, so messages stay in sync with
 * the Telegram UI. The entry is a preview until feature parity is complete.
 */
public class OxUiPreviewActivity extends Activity {

    // Palette is resolved in onCreate from the system night mode; the fields
    // stay instance-scoped so the whole screen re-themes on recreation.
    private int BONE;
    private int PAPER;
    private int INK;
    private int MUTED;
    private int SIGNAL;
    private int LINE;
    private int CORAL;
    private int RIPPLE;
    private int MENU_PAPER;
    private int CLAY;
    private org.telegram.ox.OxUiSkin skin;
    private HorizontalScrollView railScroll;
    private LinearLayout footerRow;
    private LinearLayout composerWrap;
    private FrameLayout dockHost;
    private static final int SENDING_ALPHA = 190;

    private boolean darkPalette;

    private void resolvePalette() {
        if (org.telegram.ox.OxUiSkin.hasExplicitChoice()) {
            skin = org.telegram.ox.OxUiSkin.active();
        } else {
            String theme = org.telegram.ox.OxMode.getOxTheme();
            boolean night = (getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            boolean dark = org.telegram.ox.OxMode.THEME_DARK.equals(theme)
                    || (org.telegram.ox.OxMode.THEME_SYSTEM.equals(theme) && night);
            skin = org.telegram.ox.OxUiSkin.byId(dark ? "yesek" : "qingmo");
        }
        darkPalette = skin.isDark();
        BONE = skin.bone;
        PAPER = skin.paper;
        INK = skin.ink;
        MUTED = skin.muted;
        SIGNAL = skin.signal;
        CLAY = skin.clay;
        CORAL = skin.coral;
        MENU_PAPER = skin.menuPaper;
        LINE = (INK & 0x00FFFFFF) | 0x8C000000;
        RIPPLE = (INK & 0x00FFFFFF) | 0x18000000;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<OxAgent> agents = new ArrayList<>();
    private OxAgent selected;

    private LinearLayout agentRail;
    private LinearLayout messages;
    private ScrollView scroll;
    private EditText input;
    private TextView send;
    private TextView statusLine;
    private FrameLayout overlay;
    private FrameLayout menuHost;
    private String panelQuery = "";
    private Runnable pendingScan;
    private int scanGen;
    private LinearLayout chatSearchRow;
    private EditText chatSearchInput;
    private TextView chatSearchCount;
    private final List<Long> chatHits = new ArrayList<>();
    private int chatHitIndex = -1;
    private Runnable pendingChatSearch;
    private static class TermSession {
        String name;
        final List<String> log = new ArrayList<>();
        final List<String> history = new ArrayList<>();
        int historyPos = -1;
    }
    private final List<TermSession> termSessions = new ArrayList<>();
    private int activeTerm = 0;
    private LinearLayout terminalOutput;
    private ScrollView terminalScroll;
    private LinearLayout terminalTabs;

    private TermSession term() {
        if (termSessions.isEmpty()) termSessions.add(newSession());
        if (activeTerm < 0 || activeTerm >= termSessions.size()) activeTerm = 0;
        return termSessions.get(activeTerm);
    }

    private TermSession newSession() {
        TermSession ts = new TermSession();
        ts.name = "local " + (termSessions.size() + 1);
        ts.log.add("session " + ts.name + " · " + termSessions.size() + 1
                + " · local commands only");
        return ts;
    }

    private String pendingText;
    private String pendingAgentId;
    private long pendingAt;
    private int pendingBaseSize;
    private String lastSignature = "";
    private boolean firstRender = true;
    private boolean rootMode;
    private long selectedChatId;
    private String selectedGroupTitle = "";
    private String pendingImagePath;
    private boolean userJustSent;
    private String failedText;
    private String failedImagePath;
    private String failedTargetKey;
    private final List<GroupItem> groupItems = new ArrayList<>();
    private LinearLayout attachRow;
    private ImageView attachPreview;
    private TextView attachName;
    private TextView attachMeta;
    private LinearLayout replyRow;
    private TextView replyLabel;
    private String replyAgentId;
    private String replyAgentName;
    private String replyQuoteSender;
    private String replyQuoteText;

    private static class GroupItem {
        long chatId;
        String title;
        int agents;
    }

    private static final int REQ_PICK_IMAGE = 1001;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            refresh(false);
            handler.postDelayed(this, 1200);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        resolvePalette();
        Window w = getWindow();
        w.setStatusBarColor(BONE);
        w.setNavigationBarColor(BONE);
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            if (darkPalette) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (darkPalette && Build.VERSION.SDK_INT >= 26) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            w.getDecorView().setSystemUiVisibility(flags);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            w.setStatusBarColor(BONE);
            w.setNavigationBarColor(BONE);
        }
        rootMode = getIntent() != null && getIntent().getBooleanExtra("oxui_root", false);
        setContentView(buildRoot());
        applyShell();
        loadAgents();
        loadGroupsAsync();
        handler.post(poll);
    }

    @Override
    public void onBackPressed() {
        if (overlay != null && overlay.getVisibility() == View.VISIBLE) {
            hideOverlay();
            return;
        }
        if (menuHost != null && menuHost.getVisibility() == View.VISIBLE) {
            hideMessageMenu();
            return;
        }
        if (overlay != null && overlay.getVisibility() == View.VISIBLE) {
            hideOverlay();
            return;
        }
        if (rootMode) {
            moveTaskToBack(true);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** Respects the system animator scale for reduced-motion users. */
    private boolean animationsEnabled() {
        try {
            return android.provider.Settings.Global.getFloat(getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
        } catch (Throwable t) {
            return true;
        }
    }

    /** Type scale: every skin can scale the whole reading system by +/-8%. */
    private float ts(float base) {
        return base * (skin == null ? 1f : skin.typeScale);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private Typeface serif() {
        return Typeface.create("serif", Typeface.NORMAL);
    }

    private Typeface sans() {
        return Typeface.create("sans-serif", Typeface.NORMAL);
    }

    private Typeface mono() {
        return Typeface.create("monospace", Typeface.NORMAL);
    }

    private Typeface sansMedium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    private GradientDrawable plate(int color, float radius, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        float scaled = radius <= 0f ? 0f : radius * (skin == null ? 1f : skin.radiusScale);
        d.setCornerRadius(dp(scaled));
        if (stroke != 0) d.setStroke(Math.max(1, dp(strokeWidth)), stroke);
        return d;
    }

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BONE);
        root.setFitsSystemWindows(true);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(22), dp(16), dp(22), dp(12));

        TextView back = new TextView(this);
        back.setText("←");
        back.setTextSize(24);
        back.setTextColor(INK);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(36), dp(44)));

        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.VERTICAL);
        title.setPadding(dp(8), 0, 0, 0);
        TextView word = new TextView(this);
        word.setText("Orioneko");
        word.setTextSize(28);
        word.setTypeface(serif());
        word.setTextColor(INK);
        word.setLetterSpacing(-0.02f);
        title.addView(word);
        TextView sub = new TextView(this);
        sub.setText("Local agent studio");
        sub.setTextSize(11);
        sub.setTypeface(sans());
        sub.setTextColor(MUTED);
        sub.setLetterSpacing(0.08f);
        title.addView(sub);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView chatSearchButton = new TextView(this);
        chatSearchButton.setText("⌕");
        chatSearchButton.setTextSize(16);
        chatSearchButton.setTypeface(serif());
        chatSearchButton.setTextColor(INK);
        chatSearchButton.setGravity(Gravity.CENTER);
        chatSearchButton.setContentDescription("Search this chat");
        chatSearchButton.setBackground(plate(0, 18, LINE, 1));
        chatSearchButton.setOnClickListener(v -> toggleChatSearch());
        LinearLayout.LayoutParams cslp = new LinearLayout.LayoutParams(dp(38), dp(30));
        cslp.rightMargin = dp(6);
        header.addView(chatSearchButton, cslp);

        TextView agentsButton = new TextView(this);
        agentsButton.setText(LocaleController.getString(R.string.OxUiAgents));
        agentsButton.setTextSize(11);
        agentsButton.setTypeface(sans());
        agentsButton.setTextColor(INK);
        agentsButton.setGravity(Gravity.CENTER);
        agentsButton.setPadding(dp(11), dp(5), dp(11), dp(5));
        agentsButton.setBackground(plate(0, 18, LINE, 1));
        agentsButton.setOnClickListener(v -> showAgentPanel());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(30));
        alp.rightMargin = dp(6);
        header.addView(agentsButton, alp);

        // The chartreuse "Preview" dev badge was removed: the footer status
        // line already carries the preview notice, and a second signal-colored
        // element next to the send button diluted the single-accent rule.

        TextView telegram = new TextView(this);
        telegram.setText("Telegram UI");
        telegram.setTextSize(11);
        telegram.setTypeface(sans());
        telegram.setTextColor(INK);
        telegram.setGravity(Gravity.CENTER);
        telegram.setPadding(dp(11), dp(5), dp(11), dp(5));
        telegram.setBackground(plate(0, 18, LINE, 1));
        telegram.setOnClickListener(v -> openTelegramUi());
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(30));
        tlp.leftMargin = dp(8);
        header.addView(telegram, tlp);

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View rule = new View(this);
        rule.setBackgroundColor(LINE);
        root.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        // In-conversation search bar (v1.5): hidden until the header ⌕ toggles it.
        chatSearchRow = new LinearLayout(this);
        chatSearchRow.setOrientation(LinearLayout.HORIZONTAL);
        chatSearchRow.setGravity(Gravity.CENTER_VERTICAL);
        chatSearchRow.setPadding(dp(18), dp(8), dp(18), dp(6));
        chatSearchRow.setVisibility(View.GONE);
        chatSearchInput = new EditText(this);
        chatSearchInput.setSingleLine(true);
        chatSearchInput.setHint("Search this chat");
        chatSearchInput.setHintTextColor(MUTED);
        chatSearchInput.setTextColor(INK);
        chatSearchInput.setTextSize(14);
        chatSearchInput.setTypeface(sans());
        chatSearchInput.setBackground(plate(PAPER, 16, LINE, 1));
        chatSearchInput.setPadding(dp(12), dp(6), dp(12), dp(6));
        chatSearchInput.setContentDescription("Search this chat");
        chatSearchRow.addView(chatSearchInput, new LinearLayout.LayoutParams(0, dp(40), 1f));
        chatSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s2, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s2, int a, int b, int c) {
                scheduleChatSearch(s2 == null ? "" : s2.toString());
            }
            @Override public void afterTextChanged(Editable s2) {}
        });
        chatSearchCount = new TextView(this);
        chatSearchCount.setTextSize(11);
        chatSearchCount.setTypeface(mono());
        chatSearchCount.setTextColor(MUTED);
        chatSearchCount.setGravity(Gravity.CENTER);
        chatSearchCount.setPadding(dp(8), 0, dp(4), 0);
        chatSearchRow.addView(chatSearchCount, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView chatSearchPrev = new TextView(this);
        chatSearchPrev.setText("↑");
        chatSearchPrev.setTextSize(16);
        chatSearchPrev.setTextColor(INK);
        chatSearchPrev.setGravity(Gravity.CENTER);
        chatSearchPrev.setContentDescription("Previous match");
        chatSearchPrev.setOnClickListener(v -> navigateChatHit(-1));
        chatSearchRow.addView(chatSearchPrev, new LinearLayout.LayoutParams(dp(34), dp(40)));
        TextView chatSearchNext = new TextView(this);
        chatSearchNext.setText("↓");
        chatSearchNext.setTextSize(16);
        chatSearchNext.setTextColor(INK);
        chatSearchNext.setGravity(Gravity.CENTER);
        chatSearchNext.setContentDescription("Next match");
        chatSearchNext.setOnClickListener(v -> navigateChatHit(1));
        chatSearchRow.addView(chatSearchNext, new LinearLayout.LayoutParams(dp(34), dp(40)));
        TextView chatSearchClose = new TextView(this);
        chatSearchClose.setText("×");
        chatSearchClose.setTextSize(20);
        chatSearchClose.setTextColor(MUTED);
        chatSearchClose.setGravity(Gravity.CENTER);
        chatSearchClose.setContentDescription("Close search");
        chatSearchClose.setOnClickListener(v -> closeChatSearch());
        chatSearchRow.addView(chatSearchClose, new LinearLayout.LayoutParams(dp(34), dp(40)));
        root.addView(chatSearchRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        railScroll = new HorizontalScrollView(this);
        railScroll.setHorizontalScrollBarEnabled(false);
        railScroll.setClipToPadding(false);
        railScroll.setHorizontalFadingEdgeEnabled(true);
        railScroll.setFadingEdgeLength(dp(36));
        railScroll.setPadding(dp(18), dp(12), dp(18), dp(8));
        agentRail = new LinearLayout(this);
        agentRail.setOrientation(LinearLayout.HORIZONTAL);
        railScroll.addView(agentRail, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(railScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(22), dp(4), dp(22), dp(16));
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(messages, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        composerWrap = new LinearLayout(this);
        composerWrap.setOrientation(LinearLayout.VERTICAL);
        composerWrap.setPadding(dp(16), dp(6), dp(16), dp(10));

        replyRow = new LinearLayout(this);
        replyRow.setOrientation(LinearLayout.HORIZONTAL);
        replyRow.setGravity(Gravity.CENTER_VERTICAL);
        replyRow.setPadding(dp(6), dp(4), dp(6), dp(2));
        replyRow.setVisibility(View.GONE);
        TextView replyArrow = new TextView(this);
        replyArrow.setText("↳");
        replyArrow.setTextColor(CLAY);
        replyArrow.setTextSize(14);
        replyArrow.setTypeface(mono());
        replyArrow.setTextColor(CLAY);
        replyRow.addView(replyArrow);
        replyLabel = new TextView(this);
        replyLabel.setTextSize(12);
        replyLabel.setTypeface(sans());
        replyLabel.setTextColor(INK);
        replyLabel.setPadding(dp(8), 0, dp(8), 0);
        replyLabel.setSingleLine(true);
        replyLabel.setEllipsize(TextUtils.TruncateAt.END);
        replyRow.addView(replyLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView replyCancel = new TextView(this);
        replyCancel.setText("×");
        replyCancel.setContentDescription("Cancel reply");
        replyCancel.setTextSize(20);
        replyCancel.setTextColor(MUTED);
        replyCancel.setGravity(Gravity.CENTER);
        replyCancel.setOnClickListener(v -> clearReplyTarget());
        replyRow.addView(replyCancel, new LinearLayout.LayoutParams(dp(36), dp(36)));
        composerWrap.addView(replyRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        attachRow = new LinearLayout(this);
        attachRow.setOrientation(LinearLayout.HORIZONTAL);
        attachRow.setGravity(Gravity.CENTER_VERTICAL);
        attachRow.setPadding(dp(10), dp(8), dp(10), dp(8));
        attachRow.setBackground(plate(PAPER, 14, LINE, 1));
        attachRow.setVisibility(View.GONE);
        LinearLayout attachTexts = new LinearLayout(this);
        attachTexts.setOrientation(LinearLayout.VERTICAL);
        attachPreview = new ImageView(this);
        attachPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        attachPreview.setBackground(plate(BONE, 12, LINE, 1));
        attachPreview.setClipToOutline(true);
        attachRow.addView(attachPreview, new LinearLayout.LayoutParams(dp(52), dp(52)));
        attachName = new TextView(this);
        attachName.setTextSize(12);
        attachName.setTypeface(sans());
        attachName.setTextColor(INK);
        attachName.setSingleLine(true);
        attachName.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        attachName.setPadding(dp(10), 0, dp(8), 0);
        attachTexts.addView(attachName, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        attachMeta = new TextView(this);
        attachMeta.setTextSize(11);
        attachMeta.setTypeface(mono());
        attachMeta.setTextColor(MUTED);
        attachMeta.setSingleLine(true);
        attachMeta.setPadding(dp(10), dp(1), dp(8), 0);
        attachTexts.addView(attachMeta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        attachRow.addView(attachTexts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView attachCancel = new TextView(this);
        attachCancel.setText("×");
        attachCancel.setContentDescription("Remove attachment");
        attachCancel.setTextSize(20);
        attachCancel.setTextColor(MUTED);
        attachCancel.setGravity(Gravity.CENTER);
        attachCancel.setOnClickListener(v -> {
            pendingImagePath = null;
            attachRow.setVisibility(View.GONE);
            updateSendVisual();
        });
        attachRow.addView(attachCancel, new LinearLayout.LayoutParams(dp(36), dp(44)));
        composerWrap.addView(attachRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(6), dp(4), dp(6), dp(4));
        composer.setBackground(plate(PAPER, 22, LINE, 1));

        TextView attach = new TextView(this);
        attach.setText("＋");
        attach.setContentDescription("Attach image");
        attach.setTextSize(20);
        attach.setTypeface(sans());
        attach.setTextColor(INK);
        attach.setGravity(Gravity.CENTER);
        attach.setContentDescription(LocaleController.getString(R.string.OxUiAttach));
        attach.setOnClickListener(v -> pickImage());
        composer.addView(attach, new LinearLayout.LayoutParams(dp(40), dp(44)));

        input = new EditText(this);
        input.setHint(LocaleController.getString(R.string.OxUiHint));
        input.setHintTextColor(MUTED);
        input.setTextColor(INK);
        input.setTextSize(16);
        input.setTypeface(sans());
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(dp(12), dp(9), dp(8), dp(9));
        input.setMinHeight(dp(44));
        input.setMaxLines(5);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        composer.addView(input, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        send = new TextView(this);
        send.setText("↑");
        send.setContentDescription("Send message");
        send.setTextSize(20);
        send.setTypeface(sans());
        send.setTextColor(INK);
        send.setGravity(Gravity.CENTER);
        send.setBackground(plate(SIGNAL, 22, 0, 0));
        send.setAlpha(0.42f);
        send.setContentDescription("Send");
        send.setOnClickListener(v -> sendMessage());
        composer.addView(send, new LinearLayout.LayoutParams(dp(44), dp(44)));
        composerWrap.addView(composer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        footerRow = new LinearLayout(this);
        footerRow.setOrientation(LinearLayout.HORIZONTAL);
        footerRow.setGravity(Gravity.CENTER_VERTICAL);
        footerRow.setPadding(dp(8), dp(7), dp(8), 0);
        statusLine = new TextView(this);
        statusLine.setText(LocaleController.getString(R.string.OxUiFooter));
        statusLine.setTextSize(11);
        statusLine.setTypeface(sans());
        statusLine.setTextColor(MUTED);
        footerRow.addView(statusLine, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView open = new TextView(this);
        open.setText(LocaleController.getString(R.string.OxUiOpenTelegram));
        open.setTextSize(11);
        open.setTypeface(sans());
        open.setTextColor(INK);
        open.setOnClickListener(v -> openTelegramUi());
        footerRow.addView(open);
        composerWrap.addView(footerRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(composerWrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                updateSendVisual();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        send.setEnabled(false);
        updateSendVisual();

        attachDock(root);

        FrameLayout shell = new FrameLayout(this);
        shell.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        menuHost = new FrameLayout(this);
        menuHost.setVisibility(View.GONE);
        menuHost.setClickable(true);
        menuHost.setOnClickListener(v -> hideMessageMenu());
        shell.addView(menuHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay = new FrameLayout(this);
        overlay.setBackgroundColor(BONE);
        overlay.setVisibility(View.GONE);
        overlay.setClickable(true);
        shell.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return shell;
    }

    /** In-design message menu replacing the platform AlertDialog: a bone card
     *  anchored to the pressed row, dimmed host, click-outside dismiss. */
    private void showMessageMenu(View anchor, CharSequence[] labels, boolean[] accents,
            Runnable[] actions) {
        if (menuHost == null || labels.length != actions.length) return;
        menuHost.removeAllViews();
        menuHost.setVisibility(View.VISIBLE);
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int[] hostLoc = new int[2];
        menuHost.getLocationOnScreen(hostLoc);
        int anchorX = loc[0] - hostLoc[0];
        int anchorBottom = loc[1] - hostLoc[1] + anchor.getHeight();

        hideKeyboard();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(plate(MENU_PAPER, 16, LINE, 1));
        card.setElevation(dp(12));
        card.setClickable(true);
        int cardW = Math.min(dp(280),
                getResources().getDisplayMetrics().widthPixels - 2 * dp(16));
        for (int i = 0; i < labels.length; i++) {
            if (i > 0) {
                View divider = new View(this);
                divider.setBackgroundColor(LINE);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
                dlp.leftMargin = dp(16);
                dlp.rightMargin = dp(16);
                card.addView(divider, dlp);
            }
            final int index = i;
            LinearLayout rowView = new LinearLayout(this);
            rowView.setOrientation(LinearLayout.HORIZONTAL);
            rowView.setGravity(Gravity.CENTER_VERTICAL);
            rowView.setPadding(dp(18), dp(14), dp(18), dp(14));
            rowView.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(RIPPLE), null, null));
            if (accents[i]) {
                View dot = new View(this);
                dot.setBackground(plate(SIGNAL, 4, 0, 0));
                rowView.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));
                TextView spacer = new TextView(this);
                spacer.setMinWidth(dp(9));
                rowView.addView(spacer);
            }
            rowView.setContentDescription(labels[i]);
            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextSize(15);
            label.setTypeface(sansMedium());
            label.setTextColor(INK);
            rowView.addView(label, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            rowView.setOnClickListener(v -> {
                hideMessageMenu();
                actions[index].run();
            });
            card.addView(rowView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        int cardH = labels.length * dp(48) + Math.max(0, labels.length - 1) * dp(1);
        int hostH = menuHost.getHeight() > 0 ? menuHost.getHeight()
                : getResources().getDisplayMetrics().heightPixels;
        int top = anchorBottom + dp(8);
        if (top + cardH > hostH - dp(16)) {
            top = Math.max(dp(16), loc[1] - hostLoc[1] - cardH - dp(8));
        }
        int left = Math.max(dp(16), Math.min(anchorX,
                getResources().getDisplayMetrics().widthPixels - cardW - dp(16)));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(cardW,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = left;
        clp.topMargin = top;
        clp.gravity = Gravity.TOP | Gravity.START;
        menuHost.addView(card, clp);
    }

    private void hideMessageMenu() {
        if (menuHost != null) {
            menuHost.removeAllViews();
            menuHost.setVisibility(View.GONE);
        }
    }

    private void updateSendVisual() {
        if (send == null) return;
        CharSequence text = input == null ? "" : input.getText();
        boolean ready = (selected != null && selected.tgId != 0) || isGroupTarget();
        ready = ready && ((text != null && text.toString().trim().length() > 0)
                || pendingImagePath != null);
        send.setEnabled(ready);
        send.setAlpha(ready ? 1f : 0.9f);
        send.setTextColor(ready ? INK : MUTED);
        send.setBackground(plate(ready ? SIGNAL : PAPER, 22, ready ? 0 : LINE, 1));
    }

    private void pickImage() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent,
                    LocaleController.getString(R.string.OxUiAttach)), REQ_PICK_IMAGE);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.OxUiNoAgentTitle, Toast.LENGTH_SHORT).show();
        }
    }

    private String importPickedImage(android.net.Uri uri) {
        try {
            String ext = ".jpg";
            String mime = getContentResolver().getType(uri);
            if (mime != null) {
                if (mime.contains("png")) ext = ".png";
                else if (mime.contains("webp")) ext = ".webp";
                else if (mime.contains("gif")) ext = ".gif";
            }
            File dir = new File(getFilesDir(), "oxui_media");
            dir.mkdirs();
            File dest = new File(dir, "img_" + System.currentTimeMillis() + ext);
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                java.io.FileOutputStream out = new java.io.FileOutputStream(dest);
                byte[] buf = new byte[16384];
                int n;
                long total = 0;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    out.write(buf, 0, n);
                    if (total > 16L * 1024 * 1024) break;
                }
                out.close();
            }
            return dest.getAbsolutePath();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_IMAGE || resultCode != RESULT_OK || data == null
                || data.getData() == null) return;
        String path = importPickedImage(data.getData());
        if (path == null) {
            Toast.makeText(this, R.string.OxUiNoAgentTitle, Toast.LENGTH_SHORT).show();
            return;
        }
        pendingImagePath = path;
        attachRow.setVisibility(View.VISIBLE);
        Bitmap bmp = decodeScaled(path, dp(240));
        if (bmp != null) attachPreview.setImageBitmap(bmp);
        attachName.setText(new File(path).getName());
        attachMeta.setText(attachMetaText(path));
        updateSendVisual();
    }

    /** "WxH · KB" meta line for the attachment capsule. */
    private String attachMetaText(String path) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            long bytes = new File(path).length();
            if (o.outWidth <= 0) {
                return String.format(java.util.Locale.US, "%.0f KB", bytes / 1024f);
            }
            return String.format(java.util.Locale.US, "%dx%d · %.0f KB",
                    o.outWidth, o.outHeight, bytes / 1024f);
        } catch (Throwable t) {
            return null;
        }
    }

    private Bitmap decodeScaled(String path, int max) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int sample = 1;
            while (o.outWidth / sample > max * 2 || o.outHeight / sample > max * 2) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, o2);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Unified image card: image and caption in one ink card instead of two
     *  separate bubbles; height capped at 0.6 of the viewport with a
     *  center-crop so a tall screenshot cannot swallow the screen. Returns
     *  false when the bitmap cannot be decoded (caller falls back to text). */
    private boolean addUserImage(String imagePath, long ts, String caption, boolean sending) {
        Bitmap bmp = decodeScaled(imagePath, dp(300));
        if (bmp == null) return false;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(0, dp(12), 0, 0);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        // Center the image when the caption row is wider than it, instead of
        // leaving the picture flush against the card's left edge.
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(plate(INK, 17, 0, 0));
        card.setClipToOutline(true);
        ImageView view = new ImageView(this);
        view.setImageBitmap(bmp);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int maxW = (int) (getResources().getDisplayMetrics().widthPixels * 0.7f);
        int maxH = (int) (getResources().getDisplayMetrics().heightPixels * 0.6f);
        int bw = bmp.getWidth(), bh = bmp.getHeight();
        int w = Math.max(dp(120), Math.min(maxW, bw));
        int h = w * bh / bw;
        if (h > maxH) {
            h = maxH;
            w = Math.max(dp(120), Math.min(maxW, h * bw / bh));
        }
        view.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        card.addView(view);
        TextView imgLabel = new TextView(this);
        try {
            android.graphics.BitmapFactory.Options bo = new android.graphics.BitmapFactory.Options();
            bo.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(imagePath, bo);
            imgLabel.setText("IMG  ·  " + bo.outWidth + "×" + bo.outHeight);
        } catch (Throwable t) {
            imgLabel.setText("IMG");
        }
        imgLabel.setTextSize(10.5f);
        imgLabel.setTypeface(mono());
        imgLabel.setTextColor(0xCCF1EBDD);
        imgLabel.setLetterSpacing(0.06f);
        imgLabel.setPadding(dp(15), dp(7), dp(15), caption != null && !caption.trim().isEmpty() ? 0 : dp(9));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        card.addView(imgLabel, ilp);
        if (caption != null && !caption.trim().isEmpty()) {
            TextView cap = new TextView(this);
            cap.setText(styled(caption));
            cap.setTextSize(16);
            cap.setTypeface(sans());
            cap.setTextColor(BONE);
            cap.setLineSpacing(dp(2), 1.05f);
            cap.setPadding(dp(15), dp(9), dp(15), dp(12));
            card.addView(cap, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        card.setContentDescription(caption != null && !caption.trim().isEmpty()
                ? caption : "Image");
        if (sending) card.getBackground().setAlpha(SENDING_ALPHA);
        row.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setTag(ts);
        messages.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return true;
    }

    private void addFailed() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, 0);
        View bar = new View(this);
        bar.setBackgroundColor(CORAL);
        row.addView(bar, new LinearLayout.LayoutParams(dp(2), dp(34)));
        TextView text = new TextView(this);
        text.setText(LocaleController.getString(R.string.OxUiNotSent));
        text.setTextSize(12);
        text.setTypeface(sans());
        text.setTextColor(CORAL);
        text.setPadding(dp(10), 0, 0, 0);
        text.setOnClickListener(v -> {
            if (failedText == null && failedImagePath == null) return;
            pendingText = failedText;
            pendingImagePath = failedImagePath;
            pendingAgentId = failedTargetKey;
            pendingAt = System.currentTimeMillis();
            pendingBaseSize = history().size();
            userJustSent = true;
            failedText = null;
            failedImagePath = null;
            failedTargetKey = null;
            lastSignature = "";
            refresh(true);
            dispatchSend(pendingText, pendingImagePath);
        });
        row.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        messages.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void dispatchSend(String text, String imagePath) {
        if (isGroupTarget()) {
            if (imagePath != null) {
                ArrayList<SendMessagesHelper.SendingMediaInfo> infos = new ArrayList<>();
                SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
                info.path = imagePath;
                info.caption = text == null ? "" : text;
                infos.add(info);
                OxAgentsBridge.handleOutgoingGroupMedia(targetDialogId(), infos);
            } else if (replyAgentId != null) {
                OxAgentsBridge.sendLocalGroupTextTo(targetDialogId(), replyAgentId, text,
                        replyQuoteSender, replyQuoteText);
            } else {
                OxAgentsBridge.sendLocalGroupText(targetDialogId(), text, null);
            }
            return;
        }
        if (selected == null || selected.tgId == 0) return;
        if (imagePath != null) {
            ArrayList<SendMessagesHelper.SendingMediaInfo> infos = new ArrayList<>();
            SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
            info.path = imagePath;
            info.caption = text == null ? "" : text;
            infos.add(info);
            OxAgentsBridge.handleOutgoingMedia(selected.tgId, infos);
        } else {
            OxAgentsBridge.sendFromOxUi(selected, text);
        }
    }

    private String resolveAgentName(String agentId) {
        if (agentId == null) return null;
        for (OxAgent a : agents) {
            if (a.id.equals(agentId)) return a.name;
        }
        OxAgent stored = OxAgentStore.loadAgent(agentId);
        return stored == null ? null : stored.name;
    }

    private void showAssistantActions(View anchor, String text, String senderId) {
        if (isGroupTarget() && senderId != null) {
            String resolved = resolveAgentName(senderId);
            final String name = resolved == null ? senderId : resolved;
            showMessageMenu(anchor,
                    new CharSequence[]{
                            LocaleController.getString(R.string.OxUiCopy),
                            LocaleController.getString(R.string.OxUiReply) + " " + name
                    },
                    new boolean[]{false, true},
                    new Runnable[]{
                            () -> copy(text),
                            () -> {
                                replyAgentId = senderId;
                                replyAgentName = name;
                                replyQuoteSender = senderId;
                                String snap = text == null ? "" :
                                        text.replaceAll("\\s+", " ").trim();
                                if (snap.length() > 160) snap = snap.substring(0, 160) + "…";
                                replyQuoteText = snap;
                                showReplyRow();
                            }
                    });
            return;
        }
        copy(text);
    }

    private void showReplyRow() {
        if (replyRow == null) return;
        String chip = LocaleController.getString(R.string.OxUiReplyChip) + " " + replyAgentName;
        if (replyQuoteText != null && !replyQuoteText.isEmpty()) {
            String short_ = replyQuoteText.length() > 24
                    ? replyQuoteText.substring(0, 24) + "…" : replyQuoteText;
            chip += " · 「" + short_ + "」";
        }
        replyLabel.setText(chip);
        replyRow.setVisibility(View.VISIBLE);
    }

    private void clearReplyTarget() {
        replyAgentId = null;
        replyAgentName = null;
        replyQuoteSender = null;
        replyQuoteText = null;
        if (replyRow != null) replyRow.setVisibility(View.GONE);
    }

    /** Applies the shell visibility toggles (rail/footer). The dock itself is
     *  built into the root layout by attachDock() so its position reflows the
     *  conversation instead of covering it. */
    private void applyShell() {
        if (railScroll != null) {
            boolean showRail = skin == null || skin.showRail;
            boolean railNav = skin == null || "rail".equals(skin.navStyle);
            railScroll.setVisibility(showRail && railNav ? View.VISIBLE : View.GONE);
        }
        if (footerRow != null) {
            footerRow.setVisibility(skin == null || skin.showFooter ? View.VISIBLE : View.GONE);
        }
    }

    /** Inserts the navigation dock at the user-chosen position: top (under the
     *  header), aboveInput (over the composer) or bottom (screen bottom). */
    private void attachDock(LinearLayout root) {
        if (skin == null || "rail".equals(skin.navStyle)) return;
        boolean floating = "floating".equals(skin.navStyle);
        String pos = skin.dockPosition == null ? "aboveInput" : skin.dockPosition;
        LinearLayout dock = buildDock(floating);
        int index;
        if ("top".equals(pos)) {
            index = root.indexOfChild(railScroll);
            if (index < 0) index = root.getChildCount();
        } else if ("bottom".equals(pos)) {
            index = root.getChildCount();
        } else {
            index = root.indexOfChild(composerWrap);
            if (index < 0) index = root.getChildCount();
        }
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setPadding(floating ? dp(24) : 0, dp(6), floating ? dp(24) : 0, dp(6));
        holder.addView(dock, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(holder, index);
    }

    /** The dock itself: four actions with mono glyphs, material surface. */
    private LinearLayout buildDock(boolean floating) {
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setBackground(materialSurface(floating ? 18 : 0));
        if (floating) {
            dock.setElevation(dp(8));
        }
        String[][] items = {
                {"Agents", "☰"},
                {"Search", "⌕"},
                {"Terminal", "❯_"},
                {"Settings", "⚙"}
        };
        Runnable[] actions = {
                this::showAgentPanel,
                this::toggleChatSearch,
                this::openTerminal,
                this::showSettingsPanel
        };
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(8), dp(6), dp(8), dp(6));
            item.setContentDescription(items[i][0]);
            item.setBackground(new android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(RIPPLE), null, null));
            TextView glyph = new TextView(this);
            glyph.setText(items[i][1]);
            glyph.setTextSize(13);
            glyph.setTypeface(mono());
            glyph.setTextColor(INK);
            glyph.setGravity(Gravity.CENTER);
            item.addView(glyph);
            TextView label = new TextView(this);
            label.setText(items[i][0]);
            label.setTextSize(9.5f);
            label.setTypeface(mono());
            label.setTextColor(MUTED);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            item.addView(label);
            item.setOnClickListener(v -> actions[index].run());
            dock.addView(item, new LinearLayout.LayoutParams(0, dp(46), 1f));
        }
        return dock;
    }

    /** Material layer for the dock: solid paper, acrylic, mica or a liquid-glass
     *  imitation. All are honest approximations built from drawable layers. */
    private GradientDrawable materialSurface(float radius) {
        String mat = skin == null ? "solid" : skin.navMaterial;
        float r = radius <= 0 ? 0 : radius * (skin == null ? 1f : skin.radiusScale);
        GradientDrawable d;
        if ("acrylic".equals(mat)) {
            d = plate(PAPER & 0x00FFFFFF | 0xB8000000, r, LINE, 1);
        } else if ("mica".equals(mat)) {
            d = plate(PAPER & 0x00FFFFFF | 0xCC000000, r, 0, 0);
            d.setStroke(Math.max(1, dp(1)), LINE);
        } else if ("glass".equals(mat)) {
            d = plate(SIGNAL & 0x00FFFFFF | 0x1A000000, r, 0, 0);
            d.setStroke(Math.max(1, dp(1)), SIGNAL & 0x00FFFFFF | 0x66000000);
        } else {
            d = plate(PAPER, r, LINE, 1);
        }
        return d;
    }

    /** Terminal options, mirroring Termius's Font & colours / hotkeys / input
     *  settings but scoped to our local console. */
    private void openTerminalOptions() {
        if (overlay == null) return;
        overlay.removeAllViews();
        overlay.setVisibility(View.VISIBLE);
        if (animationsEnabled()) {
            overlay.setAlpha(0f);
            overlay.animate().alpha(1f).setDuration(140).start();
        } else {
            overlay.setAlpha(1f);
        }
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(BONE);
        panel.setPadding(dp(22), dp(18), dp(22), dp(18));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Terminal options");
        title.setTextSize(24);
        title.setTypeface(serif());
        title.setTextColor(INK);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(22);
        close.setTextColor(INK);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close");
        close.setOnClickListener(v -> hideOverlay());
        head.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));
        panel.addView(head);

        TextView themeLabel = new TextView(this);
        themeLabel.setText("FONT & COLOURS");
        themeLabel.setTextSize(11);
        themeLabel.setTypeface(mono());
        themeLabel.setTextColor(MUTED);
        themeLabel.setLetterSpacing(0.08f);
        themeLabel.setPadding(dp(4), dp(16), dp(4), dp(6));
        panel.addView(themeLabel);
        String curTheme = skin == null ? "dark" : skin.termTheme;
        String[][] themes = {
                {"Termius Dark", "dark"},
                {"Pure black", "black"},
                {"Solarized", "solarized"}
        };
        for (String[] t : themes) {
            boolean active = t[1].equals(curTheme);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(11), dp(4), dp(11));
            row.setBackground(new android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(RIPPLE), null, null));
            row.setContentDescription(t[0]);
            View dot = new View(this);
            dot.setBackground(active ? plate(SIGNAL, 6, 0, 0) : plate(0, 6, LINE, 1));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(12), dp(12));
            dlp.rightMargin = dp(14);
            row.addView(dot, dlp);
            TextView label = new TextView(this);
            label.setText(t[0]);
            label.setTextSize(14);
            label.setTypeface(sans());
            label.setTextColor(INK);
            row.addView(label, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (active) {
                TextView check = new TextView(this);
                check.setText("active");
                check.setTextSize(11);
                check.setTypeface(mono());
                check.setTextColor(MUTED);
                row.addView(check);
            }
            final String v = t[1];
            row.setOnClickListener(view -> {
                org.telegram.ox.OxUiSkin copy = copyActiveSkin();
                copy.termTheme = v;
                org.telegram.ox.OxUiSkin.apply(copy);
                skin = copy;
                hideOverlay();
                openTerminal();
            });
            panel.addView(row);
        }
        panel.addView(termToggleRow("Quick keys", skin == null || skin.termQuickKeys, true));
        panel.addView(termToggleRow("Autocomplete (Tab)", skin == null || skin.termAutocomplete, false));
        TextView note = new TextView(this);
        note.setText("Pinch to zoom · Aa cycles the size · Copy JSON in the skin editor exports these settings");
        note.setTextSize(11);
        note.setTypeface(sans());
        note.setTextColor(MUTED);
        note.setPadding(dp(4), dp(16), dp(4), 0);
        panel.addView(note);
        overlay.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private LinearLayout termToggleRow(String label, boolean on, boolean quickKeys) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(11), dp(4), dp(11));
        row.setBackground(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(RIPPLE), null, null));
        row.setContentDescription(label);
        View dot = new View(this);
        dot.setBackground(on ? plate(SIGNAL, 6, 0, 0) : plate(0, 6, LINE, 1));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(12), dp(12));
        dlp.rightMargin = dp(14);
        row.addView(dot, dlp);
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(14);
        text.setTypeface(sans());
        text.setTextColor(INK);
        row.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView state = new TextView(this);
        state.setText(on ? "on" : "off");
        state.setTextSize(11);
        state.setTypeface(mono());
        state.setTextColor(MUTED);
        row.addView(state);
        row.setOnClickListener(v -> {
            org.telegram.ox.OxUiSkin copy = copyActiveSkin();
            if (quickKeys) {
                copy.termQuickKeys = !on;
            } else {
                copy.termAutocomplete = !on;
            }
            org.telegram.ox.OxUiSkin.apply(copy);
            skin = copy;
            hideOverlay();
            openTerminal();
        });
        return row;
    }

    /** OxUI terminal: a local command console with Termius/Termux-inspired
     *  chrome — underline session tabs, a status line, grouped key caps and a
     *  calm mono reading rhythm. It is explicitly not a shell. */
    private void openTerminal() {
        if (overlay == null) return;
        overlay.removeAllViews();
        overlay.setVisibility(View.VISIBLE);
        if (animationsEnabled()) {
            overlay.setAlpha(0f);
            overlay.animate().alpha(1f).setDuration(160).start();
        } else {
            overlay.setAlpha(1f);
        }
        String termTheme = skin == null ? "dark" : skin.termTheme;
        final int termBg;
        final int termText;
        final int termDim;
        if ("black".equals(termTheme)) {
            termBg = 0xFF000000;
            termText = 0xFFE8E8E8;
            termDim = 0xFF707070;
        } else if ("solarized".equals(termTheme)) {
            termBg = 0xFF002B36;
            termText = 0xFF93A1A1;
            termDim = 0xFF586E75;
        } else {
            termBg = 0xFF0B1110;
            termText = 0xFFDCE6DE;
            termDim = 0xFF6E7F76;
        }
        final int keyBg = 0x12FFFFFF;

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(termBg);
        panel.setPadding(dp(16), dp(12), dp(16), dp(10));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("❯_ terminal");
        title.setTextSize(14);
        title.setTypeface(mono());
        title.setTextColor(termText);
        title.setLetterSpacing(0.02f);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView optionsBtn = new TextView(this);
        optionsBtn.setText("⋯");
        optionsBtn.setTextSize(16);
        optionsBtn.setTypeface(mono());
        optionsBtn.setTextColor(termDim);
        optionsBtn.setGravity(Gravity.CENTER);
        optionsBtn.setBackground(plate(0, 6, 0x22FFFFFF, 1));
        optionsBtn.setContentDescription("Terminal options");
        LinearLayout.LayoutParams obp = new LinearLayout.LayoutParams(dp(34), dp(26));
        obp.rightMargin = dp(8);
        optionsBtn.setOnClickListener(v -> openTerminalOptions());
        head.addView(optionsBtn, obp);
        TextView fontBtn = new TextView(this);
        fontBtn.setText("Aa");
        fontBtn.setTextSize(12);
        fontBtn.setTypeface(mono());
        fontBtn.setTextColor(termDim);
        fontBtn.setGravity(Gravity.CENTER);
        fontBtn.setBackground(plate(0, 6, 0x22FFFFFF, 1));
        fontBtn.setContentDescription("Cycle terminal font size");
        LinearLayout.LayoutParams fblp = new LinearLayout.LayoutParams(dp(34), dp(26));
        fblp.rightMargin = dp(8);
        head.addView(fontBtn, fblp);
        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(20);
        close.setTextColor(termDim);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close terminal");
        close.setOnClickListener(v -> hideOverlay());
        head.addView(close, new LinearLayout.LayoutParams(dp(30), dp(30)));
        panel.addView(head);

        // session tabs: underline style, active in signal
        HorizontalScrollView tabsScroll = new HorizontalScrollView(this);
        tabsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabsWrap = new LinearLayout(this);
        tabsWrap.setOrientation(LinearLayout.HORIZONTAL);
        tabsWrap.setPadding(0, dp(8), 0, 0);
        terminalTabs = new LinearLayout(this);
        terminalTabs.setOrientation(LinearLayout.HORIZONTAL);
        tabsWrap.addView(terminalTabs);
        tabsScroll.addView(tabsWrap, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(tabsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View rule = new View(this);
        rule.setBackgroundColor(0x1FFFFFFF);
        panel.addView(rule, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));

        // status line (Termius-like, honest about the local console)
        TextView status = new TextView(this);
        status.setText("LOCAL  ·  tty pseudo-session  ·  no shell");
        status.setTextSize(9.5f);
        status.setTypeface(mono());
        status.setTextColor(termDim);
        status.setLetterSpacing(0.08f);
        status.setPadding(0, dp(6), 0, dp(2));
        panel.addView(status);

        terminalScroll = new ScrollView(this);
        final android.view.ScaleGestureDetector scaleDetector =
                new android.view.ScaleGestureDetector(this,
                        new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            @Override
                            public boolean onScale(android.view.ScaleGestureDetector detector) {
                                float base = termScale();
                                float next = Math.max(0.8f, Math.min(1.4f,
                                        base * detector.getScaleFactor()));
                                if (Math.abs(next - base) < 0.005f) return true;
                                org.telegram.ox.OxUiSkin copy = copyActiveSkin();
                                copy.termScale = next;
                                org.telegram.ox.OxUiSkin.apply(copy);
                                skin = copy;
                                renderTerminalLog(0xFFDCE6DE, 0xFF6E7F76);
                                return true;
                            }
                        });
        terminalScroll.setOnTouchListener((v, ev) -> {
            scaleDetector.onTouchEvent(ev);
            return false;
        });
        terminalOutput = new LinearLayout(this);
        terminalOutput.setOrientation(LinearLayout.VERTICAL);
        terminalOutput.setPadding(0, dp(8), 0, dp(6));
        terminalScroll.addView(terminalOutput, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(terminalScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // quick keys: grouped, flat caps
        HorizontalScrollView keysScroll = new HorizontalScrollView(this);
        keysScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout keys = new LinearLayout(this);
        keys.setOrientation(LinearLayout.HORIZONTAL);
        keys.setGravity(Gravity.CENTER_VERTICAL);
        keys.setPadding(0, dp(6), 0, dp(6));
        final EditText[] inputRef = new EditText[1];
        String[][] groups = {
                {"Esc", "\u0000ESC", "^L", "\u0000CLEAR", "Tab", "\u0000TAB"},
                {"↑", "\u0000UP", "↓", "\u0000DOWN"},
                {"/", "/", "-", "-", "|", "|", ">", ">", "~", "~", ":", ":"}
        };
        for (String[] group : groups) {
            for (int i = 0; i < group.length; i += 2) {
                TextView chip = new TextView(this);
                chip.setText(group[i]);
                chip.setTextSize(12);
                chip.setTypeface(mono());
                chip.setTextColor(termText);
                chip.setGravity(Gravity.CENTER);
                chip.setBackground(plate(keyBg, 6, 0x14FFFFFF, 0));
                chip.setContentDescription("Key " + group[i]);
                LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(dp(40), dp(32));
                klp.rightMargin = dp(5);
                final String value = group[i + 1];
                chip.setOnClickListener(v -> handleQuickKey(value, inputRef[0]));
                keys.addView(chip, klp);
            }
            View spacer = new View(this);
            keys.addView(spacer, new LinearLayout.LayoutParams(dp(10), 1));
        }
        keysScroll.addView(keys, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (skin == null || skin.termQuickKeys) {
            panel.addView(keysScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setBackground(plate(0, 10, 0x2FC8EF6A, 1));
        inputRow.setPadding(dp(12), dp(5), dp(5), dp(5));
        TextView prompt = new TextView(this);
        prompt.setText("❯");
        prompt.setTextSize(14);
        prompt.setTypeface(mono());
        prompt.setTextColor(SIGNAL);
        inputRow.addView(prompt, new LinearLayout.LayoutParams(dp(18),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("help");
        input.setHintTextColor(termDim);
        input.setTextColor(termText);
        input.setTextSize(13.5f * termScale());
        input.setTypeface(mono());
        input.setBackground(null);
        input.setContentDescription("Terminal command");
        inputRef[0] = input;
        input.setOnEditorActionListener((v, actionId, event) -> {
            runTerminalCommand(input.getText().toString());
            input.setText("");
            input.requestFocus();
            return true;
        });
        inputRow.addView(input, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView run = new TextView(this);
        run.setText("↵");
        run.setTextSize(16);
        run.setTypeface(mono());
        run.setTextColor(0xFF0B1110);
        run.setGravity(Gravity.CENTER);
        run.setBackground(plate(SIGNAL, 7, 0, 0));
        run.setContentDescription("Run command");
        run.setOnClickListener(v -> {
            runTerminalCommand(input.getText().toString());
            input.setText("");
            input.requestFocus();
        });
        inputRow.addView(run, new LinearLayout.LayoutParams(dp(34), dp(32)));
        panel.addView(inputRow);

        TextView foot = new TextView(this);
        foot.setText("1 LOCAL  ·  UTF-8  ·  commands run on this device");
        foot.setTextSize(9);
        foot.setTypeface(mono());
        foot.setTextColor(0xFF4E5C55);
        foot.setLetterSpacing(0.06f);
        foot.setPadding(0, dp(6), 0, 0);
        panel.addView(foot);

        fontBtn.setOnClickListener(v -> {
            float[] steps = {0.9f, 1f, 1.1f};
            float cur = termScale();
            float next = steps[0];
            for (int i = 0; i < steps.length; i++) {
                if (Math.abs(steps[i] - cur) < 0.01f) {
                    next = steps[(i + 1) % steps.length];
                    break;
                }
            }
            org.telegram.ox.OxUiSkin copy = copyActiveSkin();
            copy.termScale = next;
            org.telegram.ox.OxUiSkin.apply(copy);
            skin = copy;
            input.setTextSize(13.5f * termScale());
            renderTerminalLog(termText, termDim);
        });

        if (termSessions.isEmpty()) termSessions.add(newSession());
        renderTerminalTabs(termText, termDim);
        renderTerminalLog(termText, termDim);

        overlay.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        input.postDelayed(() -> {
            input.requestFocus();
            try {
                InputMethodManager imm = (InputMethodManager)
                        getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, 0);
            } catch (Throwable ignored) {
            }
        }, animationsEnabled() ? 180 : 40);
    }

    private float termScale() {
        float v = skin == null ? 1f : skin.termScale;
        return v <= 0f ? 1f : v;
    }

    private void handleQuickKey(String value, EditText input) {
        if (input == null) return;
        TermSession ts = term();
        if ("\u0000ESC".equals(value)) {
            hideKeyboard();
            return;
        }
        if ("\u0000CLEAR".equals(value)) {
            runTerminalCommand("clear");
            return;
        }
        if ("\u0000TAB".equals(value)) {
            if (skin != null && !skin.termAutocomplete) {
                input.append("  ");
                return;
            }
            String cur = input.getText().toString();
            String[] cmds = {"help", "agents", "groups", "search ", "theme ", "version",
                    "telegram", "clear", "echo "};
            for (String c : cmds) {
                if (!cur.isEmpty() && c.startsWith(cur) && !c.equals(cur)) {
                    input.setText(c.trim());
                    input.setSelection(input.getText().length());
                    return;
                }
            }
            input.append("  ");
            return;
        }
        if ("\u0000UP".equals(value) || "\u0000DOWN".equals(value)) {
            if (ts.history.isEmpty()) return;
            boolean up = "\u0000UP".equals(value);
            if (ts.historyPos < 0) ts.historyPos = ts.history.size();
            ts.historyPos = up
                    ? Math.max(0, ts.historyPos - 1)
                    : Math.min(ts.history.size(), ts.historyPos + 1);
            String shown = ts.historyPos >= ts.history.size()
                    ? "" : ts.history.get(ts.historyPos);
            input.setText(shown);
            input.setSelection(shown.length());
            return;
        }
        input.append(value);
    }

    private void renderTerminalTabs(int textColor, int dimColor) {
        if (terminalTabs == null) return;
        terminalTabs.removeAllViews();
        for (int i = 0; i < termSessions.size(); i++) {
            final int index = i;
            boolean active = i == activeTerm;
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(10), dp(4), dp(10), dp(0));
            tab.setContentDescription("Terminal session " + (i + 1));
            TextView label = new TextView(this);
            label.setText(termSessions.get(i).name);
            label.setTextSize(12);
            label.setTypeface(mono());
            label.setTextColor(active ? 0xFFDCE6DE : dimColor);
            tab.addView(label);
            View underline = new View(this);
            underline.setBackgroundColor(active ? SIGNAL : 0x00000000);
            LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(2));
            ulp.topMargin = dp(4);
            tab.addView(underline, ulp);
            tab.setOnClickListener(v -> {
                activeTerm = index;
                renderTerminalTabs(textColor, dimColor);
                renderTerminalLog(textColor, dimColor);
            });
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.rightMargin = dp(14);
            terminalTabs.addView(tab, tlp);
        }
        if (termSessions.size() < 4) {
            TextView add = new TextView(this);
            add.setText("+");
            add.setTextSize(15);
            add.setTypeface(mono());
            add.setTextColor(dimColor);
            add.setGravity(Gravity.CENTER);
            add.setContentDescription("New terminal session");
            add.setOnClickListener(v -> {
                termSessions.add(newSession());
                activeTerm = termSessions.size() - 1;
                renderTerminalTabs(textColor, dimColor);
                renderTerminalLog(textColor, dimColor);
            });
            terminalTabs.addView(add, new LinearLayout.LayoutParams(dp(28), dp(30)));
        }
    }

    private void renderTerminalLog(int textColor, int dimColor) {
        if (terminalOutput == null) return;
        terminalOutput.removeAllViews();
        TermSession ts = term();
        boolean first = true;
        for (String line : ts.log) {
            TextView tv = new TextView(this);
            tv.setText(line);
            tv.setTextSize(13f * termScale());
            tv.setTypeface(mono());
            boolean isPrompt = line.startsWith("❯ ");
            boolean isDim = line.startsWith("#") || line.startsWith("  ")
                    || line.startsWith("session ");
            tv.setTextColor(isPrompt ? SIGNAL : (isDim ? dimColor : textColor));
            tv.setLineSpacing(dp(3), 1.06f);
            tv.setPadding(0, dp(first ? 0 : 2), 0, dp(isPrompt ? 2 : 0));
            first = false;
            terminalOutput.addView(tv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (terminalScroll != null) {
            terminalScroll.post(() -> terminalScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void terminalPrint(String line) {
        term().log.add(line);
    }

    private void runTerminalCommand(String raw) {
        String cmd = raw == null ? "" : raw.trim();
        if (cmd.isEmpty()) return;
        TermSession ts = term();
        ts.log.add("❯ " + cmd);
        if (ts.history.isEmpty() || !ts.history.get(ts.history.size() - 1).equals(cmd)) {
            ts.history.add(cmd);
        }
        ts.historyPos = -1;
        String[] parts = cmd.split("\\s+", 2);
        String head = parts[0].toLowerCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1].trim() : "";
        try {
            switch (head) {
                case "help":
                    terminalPrint("  help              this list");
                    terminalPrint("  agents            list local agents");
                    terminalPrint("  groups            list local groups");
                    terminalPrint("  search <query>    search agents, groups and messages");
                    terminalPrint("  theme <mode>      system | light | dark");
                    terminalPrint("  version           build info");
                    terminalPrint("  telegram          open the Telegram UI");
                    terminalPrint("  clear             clear this session");
                    terminalPrint("  echo <text>       echo text");
                    terminalPrint("  keys: ↑/↓ history · Tab completion · ^L clear");
                    break;
                case "agents":
                    if (agents.isEmpty()) terminalPrint("  (no agents)");
                    for (OxAgent a : agents) {
                        terminalPrint("  " + a.id + "  ·  " + (a.name == null ? "" : a.name)
                                + "  ·  " + (a.model == null || a.model.isEmpty() ? "offline" : a.model)
                                + "  ·  " + OxHarnessAdapter.label(a.harness));
                    }
                    break;
                case "groups":
                    if (groupItems.isEmpty()) terminalPrint("  (no local groups)");
                    for (GroupItem g : groupItems) {
                        terminalPrint("  " + g.chatId + "  ·  " + g.title + "  ·  " + g.agents + " agents");
                    }
                    break;
                case "search": {
                    if (arg.isEmpty()) { terminalPrint("  usage: search <query>"); break; }
                    List<SearchHit> hits = scanMessages(arg, new ArrayList<>(groupItems));
                    int agentHits = 0;
                    String ql = arg.toLowerCase(Locale.ROOT);
                    for (OxAgent a : agents) {
                        String n = a.name == null ? "" : a.name.toLowerCase(Locale.ROOT);
                        String r = a.remark == null ? "" : a.remark.toLowerCase(Locale.ROOT);
                        if (n.contains(ql) || r.contains(ql)) agentHits++;
                    }
                    terminalPrint("  " + agentHits + " agents · " + hits.size() + " messages");
                    int shown = 0;
                    for (SearchHit h : hits) {
                        if (shown++ >= 3) break;
                        terminalPrint("  # " + (h.isGroup ? h.group.title
                                : (h.agent.name == null ? "Agent" : h.agent.name))
                                + " · " + plainText(h.entry.content));
                    }
                    break;
                }
                case "theme": {
                    String v = arg.toLowerCase(Locale.ROOT);
                    if (!v.equals(org.telegram.ox.OxMode.THEME_LIGHT)
                            && !v.equals(org.telegram.ox.OxMode.THEME_DARK)
                            && !v.equals(org.telegram.ox.OxMode.THEME_SYSTEM)) {
                        terminalPrint("  usage: theme system | light | dark");
                        break;
                    }
                    org.telegram.ox.OxMode.setOxTheme(v);
                    terminalPrint("  theme = " + v + " · restarting surface…");
                    handler.postDelayed(() -> {
                        if (!isFinishing()) recreate();
                    }, 420);
                    break;
                }
                case "version":
                    terminalPrint("  OrionekoX alpha "
                            + org.telegram.messenger.BuildConfig.BUILD_VERSION_STRING
                            + " · OxUI preview · local-first");
                    break;
                case "telegram":
                    terminalPrint("  opening Telegram UI…");
                    openTelegramUi();
                    return;
                case "clear":
                    ts.log.clear();
                    renderTerminalLog(0xFFD8E4DC, 0xFF7F8F86);
                    return;
                case "echo":
                    terminalPrint("  " + arg);
                    break;
                default:
                    terminalPrint("  command not found: " + head + " (try `help`)");
            }
        } catch (Throwable t) {
            terminalPrint("  error: " + t.getMessage());
        }
        renderTerminalLog(0xFFD8E4DC, 0xFF7F8F86);
    }

    private void openTelegramUi() {
        if (rootMode) {
            OxMode.setInterfaceMode(OxMode.IFACE_TELEGRAM);
            Intent intent = new Intent(this, LaunchActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }
        finish();
    }

    private boolean isGroupTarget() {
        return selectedChatId != 0;
    }

    private long targetDialogId() {
        return selectedChatId == 0 ? (selected == null ? 0 : selected.tgId) : -selectedChatId;
    }

    private String targetKey() {
        return isGroupTarget() ? "g" + selectedChatId : (selected == null ? "" : selected.id);
    }

    private String targetName() {
        if (isGroupTarget()) return selectedGroupTitle;
        return selected == null || selected.name == null ? "Agent" : selected.name;
    }

    private void copy(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("OxUI", text));
            Toast.makeText(this, R.string.OxUiCopyDone, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    private void hideOverlay() {
        hideKeyboard();
        scanGen++;
        if (pendingScan != null) {
            handler.removeCallbacks(pendingScan);
            pendingScan = null;
        }
        if (overlay != null) {
            overlay.setVisibility(View.GONE);
            overlay.removeAllViews();
        }
    }

    private void showAgentPanel() {
        if (overlay == null) return;
        overlay.removeAllViews();
        overlay.setVisibility(View.VISIBLE);
        if (animationsEnabled()) {
            overlay.setAlpha(0f);
            overlay.animate().alpha(1f).setDuration(160).start();
        } else {
            overlay.setAlpha(1f);
        }

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(BONE);
        panel.setPadding(dp(22), dp(18), dp(22), dp(18));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText(LocaleController.getString(R.string.OxUiAgents));
        title.setTextSize(28);
        title.setTypeface(serif());
        title.setTextColor(INK);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(24);
        close.setTextColor(INK);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close");
        close.setOnClickListener(v -> hideOverlay());
        head.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        panel.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint(LocaleController.getString(R.string.OxUiSearchAll));
        search.setHintTextColor(MUTED);
        search.setTextColor(INK);
        search.setTextSize(14);
        search.setTypeface(sans());
        search.setBackground(plate(PAPER, 18, LINE, 1));
        search.setPadding(dp(14), dp(9), dp(14), dp(9));
        search.setText(panelQuery);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        slp.topMargin = dp(16);
        panel.addView(search, slp);

        ScrollView listScroll = new ScrollView(this);
        listScroll.setClipToPadding(false);
        listScroll.setPadding(0, dp(10), 0, 0);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(listScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        panel.addView(sectionRule(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, dp(12), 0, 0);
        TextView hint = new TextView(this);
        hint.setText(LocaleController.getString(R.string.OxUiAgentsHint));
        hint.setTextSize(11);
        hint.setTypeface(sans());
        hint.setTextColor(MUTED);
        footer.addView(hint, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView settingsLink = new TextView(this);
        settingsLink.setText("Settings");
        settingsLink.setTextSize(11);
        settingsLink.setTypeface(sans());
        settingsLink.setTextColor(INK);
        settingsLink.setContentDescription("Settings");
        settingsLink.setPadding(0, 0, dp(14), 0);
        settingsLink.setOnClickListener(v -> showSettingsPanel());
        footer.addView(settingsLink);
        TextView terminalLink = new TextView(this);
        terminalLink.setText("Terminal");
        terminalLink.setTextSize(11);
        terminalLink.setTypeface(sans());
        terminalLink.setTextColor(INK);
        terminalLink.setContentDescription("Open terminal");
        terminalLink.setPadding(0, 0, dp(14), 0);
        terminalLink.setOnClickListener(v -> openTerminal());
        footer.addView(terminalLink);
        TextView telegram = new TextView(this);
        telegram.setText(LocaleController.getString(R.string.OxUiOpenTelegram));
        telegram.setTextSize(11);
        telegram.setTypeface(sans());
        telegram.setTextColor(INK);
        telegram.setOnClickListener(v -> openTelegramUi());
        footer.addView(telegram);
        panel.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        overlay.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Runnable render = () -> {
            String q = search.getText().toString().trim();
            renderAgentList(list, q);
            scheduleMessageScan(list, q);
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                panelQuery = s.toString();
                render.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        render.run();
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            View focus = getCurrentFocus();
            if (imm != null && focus != null) {
                imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            }
        } catch (Throwable ignored) {
        }
    }

    private void toggleChatSearch() {
        if (chatSearchRow == null) return;
        if (chatSearchRow.getVisibility() == View.VISIBLE) {
            closeChatSearch();
            return;
        }
        chatSearchRow.setVisibility(View.VISIBLE);
        chatSearchInput.requestFocus();
        try {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(chatSearchInput, 0);
        } catch (Throwable ignored) {
        }
    }

    private void closeChatSearch() {
        if (chatSearchRow == null) return;
        chatSearchRow.setVisibility(View.GONE);
        chatSearchInput.setText("");
        chatHits.clear();
        chatHitIndex = -1;
        chatSearchCount.setText("");
        hideKeyboard();
    }

    /** Debounced scan of the CURRENT conversation; hits are entry timestamps. */
    private void scheduleChatSearch(final String query) {
        if (pendingChatSearch != null) handler.removeCallbacks(pendingChatSearch);
        final String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            chatHits.clear();
            chatHitIndex = -1;
            chatSearchCount.setText("");
            return;
        }
        pendingChatSearch = () -> {
            List<OxChatEntry> entries = history();
            List<Long> found = new ArrayList<>();
            for (OxChatEntry e : entries) {
                if (e == null || e.content == null) continue;
                if (!"user".equals(e.role) && !"assistant".equals(e.role)) continue;
                if (e.content.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))) {
                    found.add(e.ts);
                }
            }
            chatHits.clear();
            chatHits.addAll(found);
            chatHitIndex = chatHits.isEmpty() ? -1 : 0;
            chatSearchCount.setText(chatHits.isEmpty()
                    ? "0/0" : (chatHitIndex + 1) + "/" + chatHits.size());
            if (chatHitIndex >= 0) scrollToChatHit(chatHitIndex);
        };
        handler.postDelayed(pendingChatSearch, 150);
    }

    private void navigateChatHit(int delta) {
        if (chatHits.isEmpty()) return;
        chatHitIndex = (chatHitIndex + delta + chatHits.size()) % chatHits.size();
        chatSearchCount.setText((chatHitIndex + 1) + "/" + chatHits.size());
        scrollToChatHit(chatHitIndex);
    }

    private void scrollToChatHit(int index) {
        if (messages == null || index < 0 || index >= chatHits.size()) return;
        Long ts = chatHits.get(index);
        View target = null;
        for (int i = 0; i < messages.getChildCount(); i++) {
            View child = messages.getChildAt(i);
            if (ts.equals(child.getTag())) {
                target = child;
                break;
            }
        }
        if (target == null) return;
        final View hit = target;
        scroll.smoothScrollTo(0, Math.max(0, hit.getTop() - dp(80)));
        if (animationsEnabled()) {
            hit.setBackgroundColor(0x33C8EF6A);
            handler.postDelayed(() -> hit.setBackground(null), 900);
        }
    }

    private View sectionRule() {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        return v;
    }

    private void renderAgentList(LinearLayout list, String query) {
        list.removeAllViews();
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        int shown = 0;
        for (OxAgent a : agents) {
            String name = a.name == null ? "" : a.name;
            String remark = a.remark == null ? "" : a.remark;
            if (!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q)
                    && !remark.toLowerCase(Locale.ROOT).contains(q)) continue;
            shown++;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(13), dp(4), dp(13));
            row.setBackground(new android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(RIPPLE), null, null));
            TextView badge = new TextView(this);
            badge.setText(monogram(a));
            badge.setTextSize(16);
            badge.setTypeface(serif());
            badge.setTextColor(INK);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(plate(PAPER, 20, LINE, 1));
            row.addView(badge, new LinearLayout.LayoutParams(dp(38), dp(38)));
            LinearLayout text = new LinearLayout(this);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(dp(13), 0, dp(8), 0);
            TextView nameView = new TextView(this);
            nameView.setText(name);
            nameView.setTextSize(ts(16));
            nameView.setTypeface(sans());
            nameView.setTextColor(INK);
            text.addView(nameView);
            TextView sub = new TextView(this);
            sub.setText(agentSubtitle(a));
            sub.setTextSize(12);
            sub.setTypeface(sans());
            sub.setTextColor(MUTED);
            sub.setMaxLines(1);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(sub);
            row.addView(text, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            mlp.rightMargin = dp(6);
            row.addView(modelTag(a), mlp);
            TextView more = new TextView(this);
            more.setText("›");
            more.setTextSize(20);
            more.setTextColor(MUTED);
            more.setGravity(Gravity.CENTER);
            row.addView(more, new LinearLayout.LayoutParams(dp(28), dp(38)));
            row.setOnClickListener(v -> {
                selected = a;
                selectedChatId = 0;
                selectedGroupTitle = "";
                clearReplyTarget();
                pendingText = null;
                pendingAgentId = null;
                lastSignature = "";
                hideOverlay();
                renderAgents();
                refresh(true);
            });
            row.setOnLongClickListener(v -> {
                showAgentDetail(a);
                return true;
            });
            list.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            View line = sectionRule();
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1);
            llp.leftMargin = dp(51);
            list.addView(line, llp);
        }
        renderGroupList(list, query);
    }

    private void addNoResults(LinearLayout list) {
        TextView empty = new TextView(this);
        empty.setText(LocaleController.getString(R.string.OxUiNoResults));
        empty.setTextSize(14);
        empty.setTypeface(sans());
        empty.setTextColor(MUTED);
        empty.setPadding(dp(4), dp(24), dp(4), 0);
        empty.setTag("oxui_search");
        list.addView(empty);
    }

    private static class SearchHit {
        boolean isGroup;
        OxAgent agent;
        GroupItem group;
        OxChatEntry entry;
        String senderName;
    }

    /** Panel-level session search v1: scans local agent chats and group logs
     *  (on-device only, nothing leaves the device) off the UI thread. */
    private List<SearchHit> scanMessages(String query, List<GroupItem> groups) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<SearchHit> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        int maxPerTarget = 3, maxTotal = 12;
        for (OxAgent a : agents) {
            List<OxChatEntry> chat = OxAgentStore.loadChat(a.id);
            int per = 0;
            for (int i = chat.size() - 1; i >= 0 && per < maxPerTarget; i--) {
                OxChatEntry e = chat.get(i);
                if (e == null || e.content == null) continue;
                if (!"user".equals(e.role) && !"assistant".equals(e.role)) continue;
                if (e.content.toLowerCase(Locale.ROOT).contains(q)) {
                    SearchHit h = new SearchHit();
                    h.agent = a;
                    h.entry = e;
                    out.add(h);
                    per++;
                    if (out.size() >= maxTotal) return out;
                }
            }
        }
        for (GroupItem g : groups) {
            List<OxChatEntry> chat = OxGroupStore.loadChat(g.chatId);
            int per = 0;
            for (int i = chat.size() - 1; i >= 0 && per < maxPerTarget; i--) {
                OxChatEntry e = chat.get(i);
                if (e == null || e.content == null) continue;
                if (!"user".equals(e.role) && !"assistant".equals(e.role)) continue;
                if (e.content.toLowerCase(Locale.ROOT).contains(q)) {
                    SearchHit h = new SearchHit();
                    h.isGroup = true;
                    h.group = g;
                    h.entry = e;
                    if (e.sender != null) {
                        String s = resolveAgentName(e.sender);
                        h.senderName = s == null ? e.sender : s;
                    }
                    out.add(h);
                    per++;
                    if (out.size() >= maxTotal) return out;
                }
            }
        }
        return out;
    }

    private void scheduleMessageScan(LinearLayout list, String query) {
        final int gen = ++scanGen;
        if (pendingScan != null) handler.removeCallbacks(pendingScan);
        final String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            pendingScan = null;
            renderSearchSection(list, new ArrayList<>(), "");
            return;
        }
        final List<GroupItem> groups = new ArrayList<>(groupItems);
        Runnable scan = () -> {
            List<SearchHit> hits = scanMessages(q, groups);
            runOnUiThread(() -> {
                if (gen != scanGen || overlay == null
                        || overlay.getVisibility() != View.VISIBLE) return;
                renderSearchSection(list, hits, q);
            });
        };
        pendingScan = scan;
        handler.postDelayed(scan, 220);
    }

    private void renderSearchSection(LinearLayout list, List<SearchHit> hits, String query) {
        int i = list.getChildCount() - 1;
        while (i >= 0 && "oxui_search".equals(list.getChildAt(i).getTag())) {
            list.removeViewAt(i);
            i--;
        }
        if (hits.isEmpty()) {
            if (list.getChildCount() == 0) addNoResults(list);
            return;
        }
        TextView header = new TextView(this);
        header.setText(LocaleController.getString(R.string.OxUiSearchMessages));
        header.setTextSize(11);
        header.setTypeface(mono());
        header.setTextColor(MUTED);
        header.setLetterSpacing(0.06f);
        header.setPadding(dp(4), dp(18), dp(4), dp(6));
        header.setTag("oxui_search");
        list.addView(header);
        for (SearchHit h : hits) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(13), dp(4), dp(13));
            row.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(RIPPLE), null, null));
            TextView badge = new TextView(this);
            badge.setText(h.isGroup ? "👥" : monogram(h.agent));
            badge.setTextSize(h.isGroup ? 15 : 16);
            if (!h.isGroup) {
                badge.setTypeface(serif());
                badge.setTextColor(INK);
            }
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(plate(PAPER, 20, LINE, 1));
            row.addView(badge, new LinearLayout.LayoutParams(dp(38), dp(38)));
            LinearLayout text = new LinearLayout(this);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(dp(13), 0, dp(8), 0);
            TextView name = new TextView(this);
            String targetName = h.isGroup
                    ? (h.senderName == null ? h.group.title : h.group.title + " · " + h.senderName)
                    : (h.agent.name == null ? "Agent" : h.agent.name);
            name.setText(targetName);
            name.setTextSize(12);
            name.setTypeface(sansMedium());
            name.setTextColor(MUTED);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(name);
            TextView snippet = new TextView(this);
            snippet.setText(excerpt(plainText(h.entry.content), query));
            snippet.setTextSize(14);
            snippet.setTypeface(sans());
            snippet.setTextColor(INK);
            snippet.setLineSpacing(dp(2), 1.04f);
            snippet.setMaxLines(2);
            snippet.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(snippet);
            row.addView(text, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView stamp = new TextView(this);
            stamp.setText(time(h.entry.ts));
            stamp.setTextSize(11);
            stamp.setTypeface(mono());
            stamp.setTextColor(MUTED);
            stamp.setSingleLine(true);
            stamp.setPadding(dp(8), 0, 0, 0);
            row.addView(stamp);
            row.setOnClickListener(v -> {
                if (h.isGroup) {
                    selectGroup(h.group);
                } else {
                    selected = h.agent;
                    selectedChatId = 0;
                    selectedGroupTitle = "";
                    clearReplyTarget();
                    pendingText = null;
                    pendingAgentId = null;
                    lastSignature = "";
                    hideOverlay();
                    renderAgents();
                    refresh(true);
                }
            });
            row.setTag("oxui_search");
            list.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            View line = sectionRule();
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1);
            llp.leftMargin = dp(51);
            line.setTag("oxui_search");
            list.addView(line, llp);
        }
    }

    private CharSequence excerpt(String plain, String query) {
        if (plain == null) plain = "";
        String q = query == null ? "" : query;
        int idx = plain.toLowerCase(Locale.ROOT).indexOf(q.toLowerCase(Locale.ROOT));
        if (idx < 0) return plain;
        int start = Math.max(0, idx - 24);
        int end = Math.min(plain.length(), start + 90);
        String body = (start > 0 ? "…" : "") + plain.substring(start, end)
                + (end < plain.length() ? "…" : "");
        android.text.SpannableString ss = new android.text.SpannableString(body);
        int hi = body.toLowerCase(Locale.ROOT).indexOf(q.toLowerCase(Locale.ROOT));
        if (hi >= 0) {
            ss.setSpan(new android.text.style.BackgroundColorSpan(SIGNAL),
                    hi, hi + q.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new android.text.style.ForegroundColorSpan(INK),
                    hi, hi + q.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ss;
    }

    private String plainText(String content) {
        if (content == null) return "";
        String text = content
                .replaceAll("(?m)^(?:[-*#]|\\d\\.)\\s*", "")
                .replace("**", "").replace("`", "")
                .replace('\n', ' ')
                .replaceAll("\\s{2,}", " ").trim();
        return text.length() > 120 ? text.substring(0, 120) + "…" : text;
    }

    /** Single-glyph monogram: first visible char of the name (CJK-safe),
     *  falling back to the agent emoji, then a dot. */
    private String monogram(OxAgent a) {
        if (a != null && a.name != null) {
            String n = a.name.trim();
            if (!n.isEmpty()) {
                String first = n.substring(0, 1);
                if (!first.trim().isEmpty()) return first.toUpperCase(Locale.ROOT);
            }
        }
        if (a != null && a.emoji != null && !a.emoji.isEmpty()) return a.emoji;
        return "◐";
    }

    /** Right-aligned mono model tag shown on agent rows. */
    private TextView modelTag(OxAgent a) {
        TextView tag = new TextView(this);
        String model = a == null || a.model == null ? "" : a.model.trim();
        if (model.length() > 14) model = model.substring(0, 14) + "…";
        tag.setText(model);
        tag.setTextSize(10.5f);
        tag.setTypeface(mono());
        tag.setTextColor(MUTED);
        tag.setSingleLine(true);
        tag.setMaxWidth(dp(110));
        tag.setEllipsize(TextUtils.TruncateAt.END);
        tag.setGravity(Gravity.END);
        return tag;
    }

    private String agentSubtitle(OxAgent a) {
        String harness = OxHarnessAdapter.label(a.harness);
        String remark = a.remark == null ? "" : a.remark.trim();
        if (!remark.isEmpty()) return harness + "  ·  " + remark;
        java.util.List<OxChatEntry> chat = OxAgentStore.loadChat(a.id);
        if (!chat.isEmpty()) {
            OxChatEntry last = chat.get(chat.size() - 1);
            if (last != null && last.content != null && !last.content.isEmpty()) {
                String text = plainText(last.content);
                return harness + "  ·  " + (text.length() > 46 ? text.substring(0, 46) + "…" : text);
            }
        }
        return harness;
    }

    private int parseHexOr(String raw, int fallback) {
        try {
            String v = raw == null ? "" : raw.trim();
            if (v.isEmpty()) return fallback;
            if (!v.startsWith("#")) v = "#" + v;
            if (v.length() == 7) v = "#FF" + v.substring(1);
            if (v.length() != 9) return fallback;
            return (int) Long.parseLong(v.substring(1), 16);
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static String hex(int color) {
        return String.format(java.util.Locale.US, "#%06X", color & 0xFFFFFF);
    }

    /** Token-level skin editor: seven colors plus radius and type scale.
     *  Everything applies app-wide through the token system, so custom skins
     *  stay consistent across every OxUI surface. */
    private void showSkinEditor() {
        if (overlay == null) return;
        final org.telegram.ox.OxUiSkin base = skin == null
                ? org.telegram.ox.OxUiSkin.byId("qingmo") : skin;
        hideKeyboard();
        overlay.removeAllViews();
        overlay.setVisibility(View.VISIBLE);
        if (animationsEnabled()) {
            overlay.setAlpha(0f);
            overlay.animate().alpha(1f).setDuration(160).start();
        } else {
            overlay.setAlpha(1f);
        }

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(BONE);
        panel.setPadding(dp(22), dp(18), dp(22), dp(18));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Customize skin");
        title.setTextSize(28);
        title.setTypeface(serif());
        title.setTextColor(INK);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(24);
        close.setTextColor(INK);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close");
        close.setOnClickListener(v -> hideOverlay());
        head.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        panel.addView(head);

        ScrollView bodyScroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        bodyScroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(bodyScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final EditText boneIn = editField(body, "SURFACE / BONE", hex(base.bone), 46, false);
        final EditText paperIn = editField(body, "PAPER", hex(base.paper), 46, false);
        final EditText inkIn = editField(body, "INK", hex(base.ink), 46, false);
        final EditText mutedIn = editField(body, "MUTED", hex(base.muted), 46, false);
        final EditText signalIn = editField(body, "SIGNAL (ACTIONS)", hex(base.signal), 46, false);
        final EditText clayIn = editField(body, "CLAY (HUMAN VOICE)", hex(base.clay), 46, false);
        final EditText coralIn = editField(body, "CORAL (ERRORS)", hex(base.coral), 46, false);

        TextView radiusLabel = new TextView(this);
        radiusLabel.setText("RADIUS");
        radiusLabel.setTextSize(11);
        radiusLabel.setTypeface(mono());
        radiusLabel.setTextColor(MUTED);
        radiusLabel.setLetterSpacing(0.04f);
        radiusLabel.setPadding(dp(4), dp(16), dp(4), dp(6));
        body.addView(radiusLabel);
        final float[] radius = {base.radiusScale};
        body.addView(segment(new String[]{"Sharp", "Soft", "Round"},
                new float[]{0.5f, 1f, 1.6f}, radius, null), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        TextView typeLabel = new TextView(this);
        typeLabel.setText("TYPE SCALE");
        typeLabel.setTextSize(11);
        typeLabel.setTypeface(mono());
        typeLabel.setTextColor(MUTED);
        typeLabel.setLetterSpacing(0.04f);
        typeLabel.setPadding(dp(4), dp(16), dp(4), dp(6));
        body.addView(typeLabel);
        final float[] typeScale = {base.typeScale};
        body.addView(segment(new String[]{"Compact", "Normal", "Large"},
                new float[]{0.92f, 1f, 1.08f}, typeScale, null), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        TextView note = new TextView(this);
        note.setText("Tokens apply to every OxUI surface. Export the JSON to share or archive a look.");
        note.setTextSize(11);
        note.setTypeface(sans());
        note.setTextColor(MUTED);
        note.setPadding(dp(4), dp(14), dp(4), 0);
        body.addView(note);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(14), 0, 0);
        TextView copyJson = new TextView(this);
        copyJson.setText("Copy JSON");
        copyJson.setTextSize(13);
        copyJson.setTypeface(sans());
        copyJson.setTextColor(INK);
        copyJson.setGravity(Gravity.CENTER);
        copyJson.setBackground(plate(0, 18, LINE, 1));
        copyJson.setPadding(dp(14), dp(10), dp(14), dp(10));
        actions.addView(copyJson);
        TextView cancel = new TextView(this);
        cancel.setText("Cancel");
        cancel.setTextSize(13);
        cancel.setTypeface(sans());
        cancel.setTextColor(INK);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(plate(0, 18, LINE, 1));
        cancel.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(10);
        actions.addView(cancel, clp);
        cancel.setOnClickListener(v -> hideOverlay());
        TextView prev = new TextView(this);
        prev.setText("Preview");
        prev.setTextSize(13);
        prev.setTypeface(sansMedium());
        prev.setTextColor(BONE);
        prev.setGravity(Gravity.CENTER);
        prev.setBackground(plate(INK, 18, 0, 0));
        prev.setPadding(dp(16), dp(10), dp(16), dp(10));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.leftMargin = dp(10);
        actions.addView(prev, plp);
        TextView save = new TextView(this);
        save.setText("Save");
        save.setTextSize(13);
        save.setTypeface(sansMedium());
        save.setTextColor(BONE);
        save.setGravity(Gravity.CENTER);
        save.setBackground(plate(SIGNAL, 18, 0, 0));
        save.setPadding(dp(18), dp(10), dp(18), dp(10));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = dp(10);
        actions.addView(save, slp);
        panel.addView(actions);

        final Runnable buildAndApply = () -> {
            org.telegram.ox.OxUiSkin custom = new org.telegram.ox.OxUiSkin();
            custom.id = "custom";
            custom.name = "Custom";
            custom.bone = parseHexOr(boneIn.getText().toString(), base.bone);
            custom.paper = parseHexOr(paperIn.getText().toString(), base.paper);
            custom.ink = parseHexOr(inkIn.getText().toString(), base.ink);
            custom.muted = parseHexOr(mutedIn.getText().toString(), base.muted);
            custom.signal = parseHexOr(signalIn.getText().toString(), base.signal);
            custom.clay = parseHexOr(clayIn.getText().toString(), base.clay);
            custom.coral = parseHexOr(coralIn.getText().toString(), base.coral);
            custom.menuPaper = custom.bone;
            custom.radiusScale = radius[0];
            custom.typeScale = typeScale[0];
            org.telegram.ox.OxUiSkin.apply(custom);
        };
        prev.setOnClickListener(v -> {
            buildAndApply.run();
            recreate();
        });
        save.setOnClickListener(v -> {
            buildAndApply.run();
            recreate();
        });
        copyJson.setOnClickListener(v -> {
            org.telegram.ox.OxUiSkin custom = new org.telegram.ox.OxUiSkin();
            custom.id = "custom";
            custom.name = "Custom";
            custom.bone = parseHexOr(boneIn.getText().toString(), base.bone);
            custom.paper = parseHexOr(paperIn.getText().toString(), base.paper);
            custom.ink = parseHexOr(inkIn.getText().toString(), base.ink);
            custom.muted = parseHexOr(mutedIn.getText().toString(), base.muted);
            custom.signal = parseHexOr(signalIn.getText().toString(), base.signal);
            custom.clay = parseHexOr(clayIn.getText().toString(), base.clay);
            custom.coral = parseHexOr(coralIn.getText().toString(), base.coral);
            custom.menuPaper = custom.bone;
            custom.radiusScale = radius[0];
            custom.typeScale = typeScale[0];
            copy(custom.toJson().toString());
        });

        overlay.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (animationsEnabled()) {
            panel.setAlpha(0f);
            panel.animate().alpha(1f).setDuration(160).start();
        }
    }

    /** Small segmented control used by the skin editor. */
    private LinearLayout segment(String[] labels, float[] values, float[] target, Runnable onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(plate(PAPER, 12, LINE, 1));
        row.setPadding(dp(3), dp(3), dp(3), dp(3));
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            boolean active = Math.abs(values[i] - target[0]) < 0.001f;
            TextView btn = new TextView(this);
            btn.setText(labels[i]);
            btn.setTextSize(12);
            btn.setTypeface(active ? sansMedium() : sans());
            btn.setTextColor(active ? BONE : INK);
            btn.setGravity(Gravity.CENTER);
            btn.setBackground(active ? plate(INK, 10, 0, 0) : plate(0, 10, 0, 0));
            btn.setOnClickListener(v -> {
                target[0] = values[index];
                LinearLayout parent = (LinearLayout) v.getParent();
                for (int k = 0; k < parent.getChildCount(); k++) {
                    TextView child = (TextView) parent.getChildAt(k);
                    boolean on = k == index;
                    child.setTypeface(on ? sansMedium() : sans());
                    child.setTextColor(on ? BONE : INK);
                    child.setBackground(on ? plate(INK, 10, 0, 0) : plate(0, 10, 0, 0));
                }
                if (onChange != null) onChange.run();
            });
            row.addView(btn, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        return row;
    }

    private org.telegram.ox.OxUiSkin copyActiveSkin() {
        org.telegram.ox.OxUiSkin base = skin == null
                ? org.telegram.ox.OxUiSkin.byId("qingmo") : skin;
        org.telegram.ox.OxUiSkin copy = org.telegram.ox.OxUiSkin.fromJson(base.toJson());
        copy.id = "custom";
        copy.name = "Custom";
        return copy;
    }

    private void setSkinField(String field, String value) {
        org.telegram.ox.OxUiSkin copy = copyActiveSkin();
        if ("navStyle".equals(field)) copy.navStyle = value;
        if ("navMaterial".equals(field)) copy.navMaterial = value;
        if ("dockPosition".equals(field)) copy.dockPosition = value;
        if ("showRail".equals(field)) copy.showRail = Boolean.parseBoolean(value);
        if ("showFooter".equals(field)) copy.showFooter = Boolean.parseBoolean(value);
        org.telegram.ox.OxUiSkin.apply(copy);
        recreate();
    }

    /** A labelled row of radio choices for shell options. */
    private LinearLayout shellChoice(String label, String[][] options, String current,
            boolean firstOfGroup) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        TextView caption = new TextView(this);
        caption.setText(label);
        caption.setTextSize(11);
        caption.setTypeface(mono());
        caption.setTextColor(MUTED);
        caption.setPadding(dp(4), firstOfGroup ? dp(4) : dp(12), dp(4), dp(4));
        wrap.addView(caption);
        for (String[] opt : options) {
            boolean active = opt[1].equals(current);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(9), dp(4), dp(9));
            row.setBackground(new android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(RIPPLE), null, null));
            row.setContentDescription(opt[0]);
            View dot = new View(this);
            dot.setBackground(active ? plate(SIGNAL, 6, 0, 0) : plate(0, 6, LINE, 1));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(12), dp(12));
            dlp.rightMargin = dp(14);
            row.addView(dot, dlp);
            TextView text = new TextView(this);
            text.setText(opt[0]);
            text.setTextSize(14);
            text.setTypeface(sans());
            text.setTextColor(INK);
            row.addView(text, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (active) {
                TextView check = new TextView(this);
                check.setText("active");
                check.setTextSize(11);
                check.setTypeface(mono());
                check.setTextColor(MUTED);
                row.addView(check);
            }
            final String value = opt[1];
            row.setOnClickListener(v -> setSkinField(
                    "Navigation".equals(label) ? "navStyle" : "navMaterial", value));
            wrap.addView(row);
        }
        return wrap;
    }

    private LinearLayout shellToggle(String label, boolean on, String field) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(11), dp(4), dp(11));
        row.setBackground(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(RIPPLE), null, null));
        row.setContentDescription(label);
        View dot = new View(this);
        dot.setBackground(on ? plate(SIGNAL, 6, 0, 0) : plate(0, 6, LINE, 1));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(12), dp(12));
        dlp.rightMargin = dp(14);
        row.addView(dot, dlp);
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(14);
        text.setTypeface(sans());
        text.setTextColor(INK);
        row.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView state = new TextView(this);
        state.setText(on ? "on" : "off");
        state.setTextSize(11);
        state.setTypeface(mono());
        state.setTextColor(MUTED);
        row.addView(state);
        row.setOnClickListener(v -> setSkinField(field, String.valueOf(!on)));
        return row;
    }

    private void showSettingsPanel() {
        if (overlay == null) return;
        hideKeyboard();
        overlay.removeAllViews();
        overlay.setVisibility(View.VISIBLE);
        if (animationsEnabled()) {
            overlay.setAlpha(0f);
            overlay.animate().alpha(1f).setDuration(160).start();
        } else {
            overlay.setAlpha(1f);
        }

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(BONE);
        panel.setPadding(dp(22), dp(18), dp(22), dp(18));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextSize(28);
        title.setTypeface(serif());
        title.setTextColor(INK);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(24);
        close.setTextColor(INK);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close");
        close.setOnClickListener(v -> hideOverlay());
        head.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        panel.addView(head);

        TextView themeLabel = new TextView(this);
        themeLabel.setText("THEME");
        themeLabel.setTextSize(11);
        themeLabel.setTypeface(mono());
        themeLabel.setTextColor(MUTED);
        themeLabel.setLetterSpacing(0.08f);
        themeLabel.setPadding(dp(4), dp(18), dp(4), dp(6));
        panel.addView(themeLabel);

        String[] values = {org.telegram.ox.OxMode.THEME_SYSTEM,
                org.telegram.ox.OxMode.THEME_LIGHT, org.telegram.ox.OxMode.THEME_DARK};
        String[] labels = {"Follow system", "Light", "Dark"};
        String current = org.telegram.ox.OxMode.getOxTheme();
        for (int i = 0; i < values.length; i++) {
            final String value = values[i];
            boolean active = values[i].equals(current);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(13), dp(4), dp(13));
            row.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(RIPPLE), null, null));
            row.setContentDescription(labels[i]);
            View dot = new View(this);
            dot.setBackground(active ? plate(SIGNAL, 6, 0, 0) : plate(0, 6, LINE, 1));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(12), dp(12));
            dlp.rightMargin = dp(14);
            row.addView(dot, dlp);
            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextSize(14);
            label.setTypeface(sans());
            label.setTextColor(INK);
            row.addView(label, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (active) {
                TextView check = new TextView(this);
                check.setText("active");
                check.setTextSize(11);
                check.setTypeface(mono());
                check.setTextColor(MUTED);
                row.addView(check);
            }
            row.setOnClickListener(v -> {
                org.telegram.ox.OxMode.setOxTheme(value);
                if (org.telegram.ox.OxMode.THEME_SYSTEM.equals(value)) {
                    org.telegram.ox.OxUiSkin.clearChoice();
                } else {
                    org.telegram.ox.OxUiSkin.apply(org.telegram.ox.OxUiSkin.byId(
                            org.telegram.ox.OxMode.THEME_DARK.equals(value) ? "yesek" : "qingmo"));
                }
                recreate();
            });
            panel.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        TextView appearanceLabel = new TextView(this);
        appearanceLabel.setText("APPEARANCE");
        appearanceLabel.setTextSize(11);
        appearanceLabel.setTypeface(mono());
        appearanceLabel.setTextColor(MUTED);
        appearanceLabel.setLetterSpacing(0.08f);
        appearanceLabel.setPadding(dp(4), dp(18), dp(4), dp(6));
        panel.addView(appearanceLabel);
        final String currentSkinId = skin == null ? "qingmo" : skin.id;
        for (final org.telegram.ox.OxUiSkin preset : org.telegram.ox.OxUiSkin.presets()) {
            boolean activeSkin = preset.id.equals(currentSkinId);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(11), dp(4), dp(11));
            row.setBackground(new android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(RIPPLE), null, null));
            row.setContentDescription(preset.name);
            for (int c : new int[]{preset.bone, preset.ink, preset.signal, preset.clay}) {
                View dot = new View(this);
                dot.setBackground(plate(c, 6, LINE, 1));
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(12), dp(12));
                dlp.rightMargin = dp(5);
                row.addView(dot, dlp);
            }
            TextView label = new TextView(this);
            label.setText(preset.name);
            label.setTextSize(14);
            label.setTypeface(sans());
            label.setTextColor(INK);
            label.setPadding(dp(10), 0, 0, 0);
            row.addView(label, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (activeSkin) {
                TextView check = new TextView(this);
                check.setText("active");
                check.setTextSize(11);
                check.setTypeface(mono());
                check.setTextColor(MUTED);
                row.addView(check);
            }
            row.setOnClickListener(v -> {
                org.telegram.ox.OxUiSkin.apply(preset);
                recreate();
            });
            panel.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        LinearLayout customRow = new LinearLayout(this);
        customRow.setOrientation(LinearLayout.HORIZONTAL);
        customRow.setGravity(Gravity.CENTER_VERTICAL);
        customRow.setPadding(dp(4), dp(11), dp(4), dp(11));
        customRow.setBackground(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(RIPPLE), null, null));
        customRow.setContentDescription("Customize skin");
        TextView customLabel = new TextView(this);
        customLabel.setText("Customize…");
        customLabel.setTextSize(14);
        customLabel.setTypeface(sansMedium());
        customLabel.setTextColor(INK);
        customRow.addView(customLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView customHint = new TextView(this);
        customHint.setText("colors · radius · type");
        customHint.setTextSize(11);
        customHint.setTypeface(mono());
        customHint.setTextColor(MUTED);
        customRow.addView(customHint);
        customRow.setOnClickListener(v -> showSkinEditor());
        panel.addView(customRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView shellLabel = new TextView(this);
        shellLabel.setText("SHELL");
        shellLabel.setTextSize(11);
        shellLabel.setTypeface(mono());
        shellLabel.setTextColor(MUTED);
        shellLabel.setLetterSpacing(0.08f);
        shellLabel.setPadding(dp(4), dp(18), dp(4), dp(6));
        panel.addView(shellLabel);
        final String navStyle = skin == null ? "rail" : skin.navStyle;
        final String navMaterial = skin == null ? "solid" : skin.navMaterial;
        panel.addView(shellChoice("Navigation", new String[][]{
                {"Rail", "rail"}, {"Bottom bar", "bottom"}, {"Floating dock", "floating"}
        }, navStyle, true));
        final String dockPos = skin == null ? "aboveInput" : skin.dockPosition;
        panel.addView(shellChoice("Dock position", new String[][]{
                {"Top (under header)", "top"}, {"Above input", "aboveInput"},
                {"Bottom of screen", "bottom"}
        }, dockPos, false));
        panel.addView(shellChoice("Material", new String[][]{
                {"Solid", "solid"}, {"Acrylic", "acrylic"}, {"Mica", "mica"}, {"Glass", "glass"}
        }, navMaterial, false));
        panel.addView(shellToggle("Rail chips", skin == null || skin.showRail, "showRail"));
        panel.addView(shellToggle("Footer status", skin == null || skin.showFooter, "showFooter"));

        View rule = sectionRule();
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        rlp.topMargin = dp(18);
        panel.addView(rule, rlp);

        TextView aboutLabel = new TextView(this);
        aboutLabel.setText("ABOUT");
        aboutLabel.setTextSize(11);
        aboutLabel.setTypeface(mono());
        aboutLabel.setTextColor(MUTED);
        aboutLabel.setLetterSpacing(0.08f);
        aboutLabel.setPadding(dp(4), dp(16), dp(4), dp(6));
        panel.addView(aboutLabel);

        TextView version = new TextView(this);
        version.setText("OrionekoX alpha " + org.telegram.messenger.BuildConfig.BUILD_VERSION_STRING
                + "  ·  OxUI preview");
        version.setTextSize(12);
        version.setTypeface(mono());
        version.setTextColor(MUTED);
        version.setPadding(dp(4), 0, dp(4), dp(4));
        panel.addView(version);

        TextView privacy = new TextView(this);
        privacy.setText("Local-first: chats, memory and settings stay on this device.");
        privacy.setTextSize(12);
        privacy.setTypeface(sans());
        privacy.setTextColor(MUTED);
        privacy.setPadding(dp(4), dp(2), dp(4), 0);
        panel.addView(privacy);

        ScrollView settingsScroll = new ScrollView(this);
        settingsScroll.addView(panel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        overlay.addView(settingsScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (animationsEnabled()) {
            panel.setAlpha(0f);
            panel.animate().alpha(1f).setDuration(160).start();
        }
    }

    private EditText editField(LinearLayout panel, String label, String value, int heightDp,
            boolean multiline) {
        TextView caption = new TextView(this);
        caption.setText(label);
        caption.setTextSize(11);
        caption.setTypeface(mono());
        caption.setTextColor(MUTED);
        caption.setLetterSpacing(0.04f);
        caption.setPadding(dp(4), dp(14), dp(4), dp(5));
        panel.addView(caption);
        EditText input = new EditText(this);
        input.setText(value == null ? "" : value);
        input.setTextSize(14);
        input.setTypeface(sans());
        input.setTextColor(INK);
        input.setHintTextColor(MUTED);
        input.setBackground(plate(PAPER, 14, LINE, 1));
        input.setPadding(dp(12), dp(9), dp(12), dp(9));
        input.setSingleLine(!multiline);
        if (multiline) {
            input.setGravity(Gravity.TOP | Gravity.START);
            input.setMinLines(3);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp));
        panel.addView(input, lp);
        return input;
    }

    /** OxUI agent editor: name, remark, model, persona and skill association. */
    private void showAgentEdit(final OxAgent a) {
        if (overlay == null || a == null) return;
        hideKeyboard();
        overlay.removeAllViews();
        overlay.setVisibility(View.VISIBLE);
        if (animationsEnabled()) {
            overlay.setAlpha(0f);
            overlay.animate().alpha(1f).setDuration(160).start();
        } else {
            overlay.setAlpha(1f);
        }

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(BONE);
        panel.setPadding(dp(22), dp(18), dp(22), dp(18));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Edit agent");
        title.setTextSize(28);
        title.setTypeface(serif());
        title.setTextColor(INK);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(24);
        close.setTextColor(INK);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close");
        close.setOnClickListener(v -> hideOverlay());
        head.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        panel.addView(head);

        ScrollView bodyScroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        bodyScroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(bodyScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final EditText nameIn = editField(body, "NAME", a.name, 46, false);
        final EditText remarkIn = editField(body, "REMARK", a.remark, 46, false);
        final EditText modelIn = editField(body, "MODEL", a.model, 46, false);
        final EditText personaIn = editField(body, "PERSONA (AGENTS.MD)", a.agentsMd, 150, true);

        TextView skillsLabel = new TextView(this);
        skillsLabel.setText("SKILLS");
        skillsLabel.setTextSize(11);
        skillsLabel.setTypeface(mono());
        skillsLabel.setTextColor(MUTED);
        skillsLabel.setLetterSpacing(0.04f);
        skillsLabel.setPadding(dp(4), dp(16), dp(4), dp(6));
        body.addView(skillsLabel);

        final java.util.LinkedHashSet<String> selectedSkills = new java.util.LinkedHashSet<>();
        try {
            for (int i = 0; i < a.skillIds.length(); i++) {
                selectedSkills.add(a.skillIds.getString(i));
            }
        } catch (Throwable ignored) {
        }
        List<org.telegram.ox.OxSkillStore.Skill> skills = org.telegram.ox.OxSkillStore.list();
        if (skills.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("No SKILL.md packages found (Download/Operit/skills).");
            none.setTextSize(12);
            none.setTypeface(sans());
            none.setTextColor(MUTED);
            none.setPadding(dp(4), 0, dp(4), 0);
            body.addView(none);
        }
        for (final org.telegram.ox.OxSkillStore.Skill skill : skills) {
            final boolean[] on = {selectedSkills.contains(skill.id)};
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(11), dp(4), dp(11));
            row.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(RIPPLE), null, null));
            row.setContentDescription(skill.name);
            final View dot = new View(this);
            dot.setBackground(on[0] ? plate(SIGNAL, 6, 0, 0) : plate(0, 6, LINE, 1));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(12), dp(12));
            dlp.rightMargin = dp(14);
            row.addView(dot, dlp);
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView name = new TextView(this);
            name.setText(skill.name);
            name.setTextSize(14);
            name.setTypeface(sans());
            name.setTextColor(INK);
            texts.addView(name);
            if (skill.description != null && !skill.description.isEmpty()) {
                TextView desc = new TextView(this);
                desc.setText(skill.description);
                desc.setTextSize(11);
                desc.setTypeface(sans());
                desc.setTextColor(MUTED);
                desc.setMaxLines(2);
                desc.setEllipsize(TextUtils.TruncateAt.END);
                texts.addView(desc);
            }
            row.addView(texts, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.setOnClickListener(v -> {
                on[0] = !on[0];
                if (on[0]) {
                    selectedSkills.add(skill.id);
                } else {
                    selectedSkills.remove(skill.id);
                }
                dot.setBackground(on[0] ? plate(SIGNAL, 6, 0, 0) : plate(0, 6, LINE, 1));
            });
            body.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(14), 0, 0);
        TextView cancel = new TextView(this);
        cancel.setText("Cancel");
        cancel.setTextSize(14);
        cancel.setTypeface(sans());
        cancel.setTextColor(INK);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(plate(0, 18, LINE, 1));
        cancel.setPadding(dp(18), dp(10), dp(18), dp(10));
        cancel.setOnClickListener(v -> hideOverlay());
        actions.addView(cancel);
        TextView save = new TextView(this);
        save.setText("Save");
        save.setTextSize(14);
        save.setTypeface(sansMedium());
        save.setTextColor(BONE);
        save.setGravity(Gravity.CENTER);
        save.setBackground(plate(INK, 18, 0, 0));
        save.setPadding(dp(22), dp(10), dp(22), dp(10));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = dp(10);
        actions.addView(save, slp);
        panel.addView(actions);

        save.setOnClickListener(v -> {
            String name = nameIn.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                OxAgent fresh = OxAgentStore.loadAgent(a.id);
                if (fresh == null) fresh = a;
                fresh.name = name;
                fresh.remark = remarkIn.getText().toString().trim();
                fresh.model = modelIn.getText().toString().trim();
                fresh.agentsMd = personaIn.getText().toString();
                org.json.JSONArray ids = new org.json.JSONArray();
                for (String id : selectedSkills) ids.put(id);
                fresh.skillIds = ids;
                OxAgentStore.saveAgent(fresh);
                loadAgents();
                hideOverlay();
                refresh(true);
                Toast.makeText(this, "Agent saved", Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show();
            }
        });

        overlay.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (animationsEnabled()) {
            panel.setAlpha(0f);
            panel.animate().alpha(1f).setDuration(160).start();
        }
    }

    private void showAgentDetail(OxAgent a) {
        if (overlay == null) return;
        hideKeyboard();
        overlay.removeAllViews();
        overlay.setVisibility(View.VISIBLE);
        if (animationsEnabled()) {
            overlay.setAlpha(0f);
            overlay.animate().alpha(1f).setDuration(160).start();
        } else {
            overlay.setAlpha(1f);
        }

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.TOP);
        panel.setBackgroundColor(BONE);
        panel.setPadding(dp(22), dp(18), dp(22), dp(18));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("←");
        back.setTextSize(24);
        back.setTextColor(INK);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> showAgentPanel());
        head.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(24);
        close.setTextColor(INK);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> hideOverlay());
        head.addView(new View(this), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        panel.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        TextView badge = new TextView(this);
        badge.setText(a.emoji == null || a.emoji.isEmpty() ? "◐" : a.emoji);
        badge.setTextSize(25);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(plate(PAPER, 30, LINE, 1));
        panel.addView(badge, new LinearLayout.LayoutParams(dp(58), dp(58)));

        TextView name = new TextView(this);
        name.setText(a.name == null ? "Agent" : a.name);
        name.setTextSize(28);
        name.setTypeface(serif());
        name.setTextColor(INK);
        name.setPadding(0, dp(12), 0, 0);
        panel.addView(name);
        TextView meta = new TextView(this);
        meta.setText(OxHarnessAdapter.label(a.harness)
                + (a.harnessModel == null || a.harnessModel.isEmpty() ? "" : "  ·  " + a.harnessModel));
        meta.setTextSize(ts(11));
        meta.setTypeface(mono());
        meta.setTextColor(MUTED);
        meta.setPadding(0, dp(4), 0, 0);
        panel.addView(meta);
        if (a.remark != null && !a.remark.isEmpty()) {
            TextView remark = new TextView(this);
            remark.setText(a.remark);
            remark.setTextSize(14);
            remark.setTypeface(sans());
            remark.setTextColor(INK);
            remark.setLineSpacing(dp(3), 1f);
            remark.setPadding(0, dp(12), 0, 0);
            panel.addView(remark);
        }

        TextView memoryTitle = new TextView(this);
        memoryTitle.setText(LocaleController.getString(R.string.OxUiMemory) + "  ·  "
                + OxMemoryStore.stats(a.id));
        memoryTitle.setTextSize(11);
        memoryTitle.setTypeface(mono());
        memoryTitle.setTextColor(MUTED);
        memoryTitle.setPadding(0, dp(20), 0, 0);
        panel.addView(memoryTitle);
        TextView memory = new TextView(this);
        String recent = OxMemoryStore.recent(a.id, 8);
        memory.setText(recent.isEmpty()
                ? LocaleController.getString(R.string.OxUiNoMemory)
                : recent);
        memory.setTextSize(14);
        memory.setTypeface(sans());
        memory.setTextColor(INK);
        memory.setLineSpacing(dp(3), 1f);
        memory.setPadding(dp(12), dp(12), dp(12), dp(12));
        memory.setMaxLines(12);
        memory.setEllipsize(TextUtils.TruncateAt.END);
        memory.setBackground(plate(PAPER, 12, LINE, 1));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(8);
        panel.addView(memory, mlp);

        TextView chat = new TextView(this);
        chat.setText(LocaleController.getString(R.string.OxUiChat));
        chat.setTextSize(14);
        chat.setTypeface(sans());
        chat.setTextColor(BONE);
        chat.setGravity(Gravity.CENTER);
        chat.setPadding(dp(18), dp(11), dp(18), dp(11));
        chat.setBackground(plate(INK, 20, 0, 0));
        chat.setOnClickListener(v -> {
            selected = a;
            pendingText = null;
            pendingAgentId = null;
            lastSignature = "";
            hideOverlay();
            renderAgents();
            refresh(true);
        });
        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setPadding(0, dp(18), 0, 0);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsRow.addView(chat, clp);
        TextView edit = new TextView(this);
        edit.setText("Edit");
        edit.setTextSize(14);
        edit.setTypeface(sans());
        edit.setTextColor(INK);
        edit.setGravity(Gravity.CENTER);
        edit.setPadding(dp(18), dp(11), dp(18), dp(11));
        edit.setBackground(plate(0, 20, LINE, 1));
        edit.setContentDescription("Edit agent");
        final OxAgent editTarget = a;
        edit.setOnClickListener(v -> showAgentEdit(editTarget));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.leftMargin = dp(10);
        actionsRow.addView(edit, elp);
        panel.addView(actionsRow);

        overlay.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void loadGroupsAsync() {
        new Thread(() -> {
            List<GroupItem> out = new ArrayList<>();
            try {
                for (long chatId : OxGroupStore.chatIds()) {
                    if (!OxGroupStore.isLocallyCreated(chatId)) continue;
                    GroupItem item = new GroupItem();
                    item.chatId = chatId;
                    item.agents = OxGroupStore.agents(chatId).size();
                    TLRPC.Chat chat = MessagesController.getInstance(0).getChat(chatId);
                    if (chat == null || TextUtils.isEmpty(chat.title)) {
                        // Cold start: the chat cache loads asynchronously, so fall
                        // back to a blocking storage read on this worker thread.
                        chat = MessagesStorage.getInstance(0).getChatSync(chatId);
                    }
                    item.title = chat != null && !TextUtils.isEmpty(chat.title)
                            ? chat.title : ("Group " + chatId);
                    out.add(item);
                }
            } catch (Throwable ignored) {
            }
            List<GroupItem> finalOut = out;
            runOnUiThread(() -> {
                groupItems.clear();
                groupItems.addAll(finalOut);
            });
        }, "oxui-groups").start();
    }

    private void selectGroup(GroupItem item) {
        clearReplyTarget();
        selected = null;
        selectedChatId = item.chatId;
        selectedGroupTitle = item.title;
        pendingText = null;
        pendingAgentId = null;
        lastSignature = "";
        hideOverlay();
        renderAgents();
        refresh(true);
    }

    private void renderGroupList(LinearLayout list, String query) {
        if (groupItems.isEmpty()) return;
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<GroupItem> shown = new ArrayList<>();
        for (GroupItem item : groupItems) {
            String title = item.title == null ? "" : item.title;
            if (!q.isEmpty() && !title.toLowerCase(Locale.ROOT).contains(q)) continue;
            shown.add(item);
        }
        if (shown.isEmpty()) return;
        TextView header = new TextView(this);
        header.setText(LocaleController.getString(R.string.OxUiGroups));
        header.setTextSize(11);
        header.setTypeface(mono());
        header.setTextColor(MUTED);
        header.setLetterSpacing(0.08f);
        header.setPadding(dp(4), dp(18), dp(4), dp(6));
        list.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (GroupItem item : shown) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(13), dp(4), dp(13));
            row.setBackground(new android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(RIPPLE), null, null));
            TextView badge = new TextView(this);
            badge.setText("👥");
            badge.setTextSize(16);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(plate(PAPER, 20, LINE, 1));
            row.addView(badge, new LinearLayout.LayoutParams(dp(38), dp(38)));
            LinearLayout text = new LinearLayout(this);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(dp(13), 0, dp(8), 0);
            TextView name = new TextView(this);
            name.setText(item.title);
            name.setTextSize(16);
            name.setTypeface(sans());
            name.setTextColor(INK);
            text.addView(name);
            TextView sub = new TextView(this);
            sub.setText(LocaleController.formatString("OxUiGroupMembers",
                    R.string.OxUiGroupMembers, item.agents));
            sub.setTextSize(12);
            sub.setTypeface(sans());
            sub.setTextColor(MUTED);
            text.addView(sub);
            row.addView(text, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView more = new TextView(this);
            more.setText("›");
            more.setTextSize(20);
            more.setTextColor(MUTED);
            more.setGravity(Gravity.CENTER);
            row.addView(more, new LinearLayout.LayoutParams(dp(28), dp(38)));
            row.setOnClickListener(v -> selectGroup(item));
            list.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            View line = sectionRule();
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1);
            llp.leftMargin = dp(51);
            list.addView(line, llp);
        }
    }

    private void loadAgents() {
        agents.clear();
        for (OxAgent a : OxAgentStore.listAgents()) {
            if (a != null && a.tgId != 0) agents.add(a);
        }
        if (agents.isEmpty()) {
            for (OxAgent a : OxAgentStore.listAgents()) {
                if (a != null) agents.add(a);
            }
        }
        selected = null;
        for (OxAgent a : agents) {
            if (a.pinned) { selected = a; break; }
        }
        if (selected == null && !agents.isEmpty()) selected = agents.get(0);
        renderAgents();
        refresh(true);
    }

    private void renderAgents() {
        agentRail.removeAllViews();
        if (isGroupTarget()) {
            TextView groupChip = new TextView(this);
            groupChip.setText("👥  " + selectedGroupTitle);
            groupChip.setContentDescription(selectedGroupTitle);
            groupChip.setTextSize(12);
            groupChip.setTypeface(sansMedium());
            groupChip.setTextColor(BONE);
            groupChip.setGravity(Gravity.CENTER);
            groupChip.setMaxWidth(dp(190));
            groupChip.setSingleLine(true);
            groupChip.setEllipsize(TextUtils.TruncateAt.END);
            groupChip.setPadding(dp(14), dp(9), dp(14), dp(9));
            groupChip.setBackground(plate(INK, 20, 0, 0));
            groupChip.setOnClickListener(v -> showAgentPanel());
            agentRail.addView(groupChip, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        for (OxAgent a : agents) {
            boolean active = selected != null && a.id.equals(selected.id);
            TextView chip = new TextView(this);
            String label = (a.emoji == null || a.emoji.isEmpty() ? "" : a.emoji + "  ")
                    + (a.name == null ? "Agent" : a.name);
            chip.setText(label);
            chip.setContentDescription(a.name);
            chip.setTextSize(12);
            chip.setTypeface(active ? sansMedium() : sans());
            chip.setTextColor(active ? BONE : INK);
            chip.setGravity(Gravity.CENTER);
            chip.setMaxWidth(dp(190));
            chip.setSingleLine(true);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setPadding(dp(14), dp(9), dp(14), dp(9));
            chip.setBackground(active ? plate(INK, 20, 0, 0)
                    : plate(0, 20, LINE, 1));
            chip.setOnClickListener(v -> {
                selected = a;
                pendingText = null;
                pendingAgentId = null;
                lastSignature = "";
                renderAgents();
                refresh(true);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            agentRail.addView(chip, lp);
        }
        TextView terminalChip = new TextView(this);
        terminalChip.setText("❯_  Terminal");
        terminalChip.setContentDescription("Open terminal");
        terminalChip.setTextSize(12);
        terminalChip.setTypeface(mono());
        terminalChip.setTextColor(INK);
        terminalChip.setGravity(Gravity.CENTER);
        terminalChip.setPadding(dp(14), dp(9), dp(14), dp(9));
        terminalChip.setBackground(plate(0, 20, LINE, 1));
        terminalChip.setOnClickListener(v -> openTerminal());
        agentRail.addView(terminalChip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private List<OxChatEntry> history() {
        if (isGroupTarget()) return OxGroupStore.loadChat(selectedChatId);
        if (selected == null) return new ArrayList<>();
        return OxAgentStore.loadChat(selected.id);
    }

    private void refresh(boolean force) {
        if (selected == null && !isGroupTarget()) {
            renderEmpty(LocaleController.getString(R.string.OxUiNoAgentTitle),
                    LocaleController.getString(R.string.OxUiNoAgentBody));
            return;
        }
        List<OxChatEntry> chat = history();
        String key = targetKey();
        if (pendingText != null && pendingAgentId != null && pendingAgentId.equals(key)) {
            if (chat.size() > pendingBaseSize && containsUser(chat, pendingText, pendingBaseSize)) {
                pendingText = null;
                pendingAgentId = null;
                failedText = null;
                failedImagePath = null;
                failedTargetKey = null;
            } else if (System.currentTimeMillis() - pendingAt > 120_000) {
                failedText = pendingText;
                failedImagePath = pendingImagePath;
                failedTargetKey = key;
                pendingText = null;
                pendingAgentId = null;
                pendingImagePath = null;
                updateSendVisual();
            }
        }
        String sig = signature(chat) + "|"
                + (pendingText == null ? "" : "pending:" + pendingText)
                + "|" + (failedText == null || !key.equals(failedTargetKey) ? "" : "failed");
        if (!force && sig.equals(lastSignature)) return;
        lastSignature = sig;
        renderConversation(chat);
    }

    private boolean containsUser(List<OxChatEntry> chat, String text, int from) {
        for (int i = Math.max(0, from); i < chat.size(); i++) {
            OxChatEntry e = chat.get(i);
            if (e != null && "user".equals(e.role) && text.equals(e.content)) return true;
        }
        return false;
    }

    private String signature(List<OxChatEntry> chat) {
        if (chat.isEmpty()) return "0";
        OxChatEntry last = chat.get(chat.size() - 1);
        return chat.size() + ":" + (last == null ? "" : last.role)
                + ":" + (last == null || last.content == null ? 0 : last.content.hashCode())
                + ":" + (last == null || last.imagePath == null ? "" : last.imagePath);
    }

    private void renderEmpty(String title, String body) {
        messages.removeAllViews();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(54), 0, 0);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(28);
        t.setTypeface(serif());
        t.setTextColor(INK);
        t.setLineSpacing(dp(2), 1f);
        box.addView(t);
        TextView b = new TextView(this);
        b.setText(body);
        b.setTextSize(14);
        b.setTypeface(sans());
        b.setTextColor(MUTED);
        b.setLineSpacing(dp(4), 1.05f);
        b.setPadding(0, dp(12), dp(24), 0);
        box.addView(b);
        messages.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void renderConversation(List<OxChatEntry> chat) {
        if (menuHost != null && menuHost.getVisibility() == View.VISIBLE) hideMessageMenu();
        messages.removeAllViews();
        if (chat.isEmpty() && pendingText == null) {
            renderEmpty(LocaleController.getString(R.string.OxUiEmptyTitle),
                    LocaleController.getString(R.string.OxUiEmptyBody));
            return;
        }
        int start = Math.max(0, chat.size() - 80);
        for (int i = start; i < chat.size(); i++) {
            OxChatEntry e = chat.get(i);
            if (e == null) continue;
            boolean hasText = e.content != null && !e.content.trim().isEmpty();
            boolean hasImage = e.imagePath != null && new File(e.imagePath).isFile();
            if (!hasText && !hasImage) continue;
            if ("user".equals(e.role)) {
                addUser(e.content, e.ts, false, hasImage ? e.imagePath : null,
                        e.replyToSender, e.replyToText);
            } else if ("assistant".equals(e.role)) {
                addAssistant(e.content, e.ts, e.sender);
            } else if ("tool".equals(e.role)) {
                addTool(e.content);
            }
        }
        if (pendingText != null || pendingImagePath != null) {
            addUser(pendingText, System.currentTimeMillis(), true, pendingImagePath,
                    replyQuoteSender, replyQuoteText);
            addWorking();
        }
        if (failedText != null && targetKey().equals(failedTargetKey)) {
            addFailed();
        }
        final boolean animate = firstRender;
        firstRender = false;
        scroll.post(() -> {
            View content = scroll.getChildAt(0);
            boolean nearBottom = content == null || content.getHeight() - scroll.getHeight()
                    - scroll.getScrollY() < dp(120);
            if (nearBottom || userJustSent) {
                scroll.fullScroll(View.FOCUS_DOWN);
                // Re-align after the newly added children finish measuring;
                // the first pass can run against stale heights.
                messages.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            }
            userJustSent = false;
            if (animate && animationsEnabled()) {
                messages.setAlpha(0f);
                messages.setTranslationY(dp(10));
                messages.animate().alpha(1f).translationY(0f).setDuration(260).start();
            }
        });
    }

    private void addUser(String text, long ts, boolean sending, String imagePath,
            String quoteSender, String quoteText) {
        if (imagePath != null && addUserImage(imagePath, ts, text, sending)) return;
        if (text == null || text.trim().isEmpty()) return;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(0, dp(12), 0, 0);
        if (quoteSender != null && quoteText != null && !quoteText.isEmpty()) {
            String name = resolveAgentName(quoteSender);
            TextView quoteLine = new TextView(this);
            quoteLine.setText("↩ " + (name == null ? quoteSender : name) + ": " + quoteText);
            quoteLine.setTextSize(12);
            quoteLine.setTypeface(sans());
            quoteLine.setTextColor(MUTED);
            quoteLine.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.72f));
            quoteLine.setPadding(0, 0, 0, dp(3));
            LinearLayout quoteWrap = new LinearLayout(this);
            quoteWrap.setOrientation(LinearLayout.VERTICAL);
            quoteWrap.setGravity(Gravity.END);
            quoteWrap.addView(quoteLine);
            TextView bubble = new TextView(this);
            bubble.setText(styled(text));
            bubble.setTextSize(ts(16));
            bubble.setTypeface(sans());
            bubble.setTextColor(BONE);
            bubble.setLineSpacing(dp(2), 1.05f);
            bubble.setPadding(dp(15), dp(11), dp(15), dp(11));
            bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.82f));
            GradientDrawable bg = plate(CLAY, 17, 0, 0);
            if (sending) bg.setAlpha(SENDING_ALPHA);
            bubble.setBackground(bg);
            bubble.setTextIsSelectable(false);
            bubble.setOnLongClickListener(v -> {
                showMessageMenu(v,
                        new CharSequence[]{LocaleController.getString(R.string.OxUiCopy)},
                        new boolean[]{false},
                        new Runnable[]{() -> copy(text)});
                return true;
            });
            quoteWrap.addView(bubble, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (!sending) quoteWrap.addView(userTimestamp(ts), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(quoteWrap, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setTag(ts);
            messages.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        TextView bubble = new TextView(this);
        bubble.setText(styled(text));
        bubble.setTextSize(ts(16));
        bubble.setTypeface(sans());
        bubble.setTextColor(BONE);
        bubble.setLineSpacing(dp(2), 1.05f);
        bubble.setPadding(dp(15), dp(11), dp(15), dp(11));
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.82f));
        GradientDrawable bg = plate(CLAY, 17, 0, 0);
        if (sending) bg.setAlpha(SENDING_ALPHA);
        bubble.setBackground(bg);
        bubble.setTextIsSelectable(false);
        bubble.setOnLongClickListener(v -> {
            showMessageMenu(v,
                    new CharSequence[]{LocaleController.getString(R.string.OxUiCopy)},
                    new boolean[]{false},
                    new Runnable[]{() -> copy(text)});
            return true;
        });
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.END);
        wrap.addView(bubble, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView stamp = userTimestamp(ts);
        if (!sending) wrap.addView(stamp, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(wrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setTag(ts);
        messages.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Right-aligned mono timestamp under a user bubble (skipped while the
     *  send is still pending). */
    private TextView userTimestamp(long ts) {
        TextView tv = new TextView(this);
        tv.setText(time(ts));
        tv.setTextSize(11);
        tv.setTypeface(mono());
        tv.setTextColor(MUTED);
        tv.setGravity(Gravity.END);
        tv.setPadding(0, dp(3), dp(2), 0);
        return tv;
    }

    private void addAssistant(String text, long ts, String senderId) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(16), 0, 0);

        View accent = new View(this);
        accent.setBackgroundColor(SIGNAL);
        // Match_parent so the chartreuse rule spans the whole message on long
        // replies instead of stopping after the meta line.
        row.addView(accent, new LinearLayout.LayoutParams(dp(2),
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(13), 0, dp(8), 0);
        String senderName = senderId == null ? null : resolveAgentName(senderId);
        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        OxAgent senderAgent = senderId == null ? null : OxAgentStore.loadAgent(senderId);
        TextView senderGlyph = new TextView(this);
        senderGlyph.setText(monogram(senderAgent));
        senderGlyph.setTextSize(9);
        senderGlyph.setTypeface(serif());
        senderGlyph.setTextColor(INK);
        senderGlyph.setGravity(Gravity.CENTER);
        senderGlyph.setBackground(plate(PAPER, 9, LINE, 1));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(18), dp(18));
        glp.rightMargin = dp(6);
        metaRow.addView(senderGlyph, glp);
        TextView meta = new TextView(this);
        meta.setText((isGroupTarget() && senderName != null ? senderName : targetName())
                + "  ·  " + time(ts));
        meta.setTextSize(ts(11));
        meta.setTypeface(mono());
        meta.setTextColor(MUTED);
        meta.setLetterSpacing(0.02f);
        metaRow.addView(meta);
        body.addView(metaRow);
        TextView t = new TextView(this);
        t.setText(styled(text));
        t.setTextSize(ts(17));
        t.setTypeface(serif());
        t.setTextColor(INK);
        t.setLineSpacing(dp(4), 1.06f);
        t.setPadding(0, dp(3), 0, 0);
        t.setTextIsSelectable(false);
        t.setOnLongClickListener(v -> {
            showAssistantActions(v, text, senderId);
            return true;
        });
        body.addView(t);
        row.addView(body, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setTag(ts);
        messages.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addTool(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(11);
        t.setTypeface(mono());
        t.setTextColor(MUTED);
        t.setMaxLines(4);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        t.setBackground(plate(PAPER, 8, LINE, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        lp.leftMargin = dp(15);
        lp.rightMargin = dp(12);
        messages.addView(t, lp);
    }

    private void addWorking() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(15), dp(13), 0, 0);
        View dot = new View(this);
        dot.setBackground(plate(SIGNAL, 4, 0, 0));
        row.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));
        try {
            float scale = android.provider.Settings.Global.getFloat(getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            if (scale > 0) {
                ObjectAnimator breath = ObjectAnimator.ofFloat(dot, View.ALPHA, 0.25f, 1f);
                breath.setDuration(700);
                breath.setRepeatCount(ValueAnimator.INFINITE);
                breath.setRepeatMode(ValueAnimator.REVERSE);
                breath.start();
            }
        } catch (Throwable ignored) {
        }
        TextView t = new TextView(this);
        t.setText("  " + LocaleController.getString(R.string.OxUiThinking));
        t.setTextSize(12);
        t.setTypeface(sans());
        t.setTextColor(MUTED);
        row.addView(t);
        messages.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Converts the Markdown the agents already use into native spans. */
    private CharSequence styled(String text) {
        if (text == null) return "";
        String html = TextUtils.htmlEncode(text).replace("\n", "<br>");
        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        html = html.replaceAll("`([^`]+)`", "<tt>$1</tt>");
        html = html.replaceAll("(?m)^#{1,6}\\s*(.+)$", "<b>$1</b>");
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
    }

    private String time(long ts) {
        long value = ts > 0 ? ts : System.currentTimeMillis();
        if (value < 10_000_000_000L) value *= 1000L;
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(value));
    }

    private void sendMessage() {
        boolean targetReady = (selected != null && selected.tgId != 0) || isGroupTarget();
        if (!targetReady) {
            Toast.makeText(this, R.string.OxUiNoAgentTitle, Toast.LENGTH_SHORT).show();
            return;
        }
        String text = input.getText() == null ? "" : input.getText().toString().trim();
        if (text.isEmpty() && pendingImagePath == null) return;
        pendingText = text;
        pendingAgentId = targetKey();
        pendingAt = System.currentTimeMillis();
        pendingBaseSize = history().size();
        userJustSent = true;
        lastSignature = "";
        input.setText("");
        attachRow.setVisibility(View.GONE);
        refresh(true);
        dispatchSend(pendingText, pendingImagePath);
        pendingImagePath = null;
        // Clear only after dispatch: dispatchSend reads replyAgentId to route
        // the targeted reply through sendLocalGroupTextTo.
        clearReplyTarget();
        updateSendVisual();
    }
}
