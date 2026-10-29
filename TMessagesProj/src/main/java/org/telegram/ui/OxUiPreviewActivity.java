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
    private static final int SENDING_ALPHA = 190;

    private boolean darkPalette;

    private void resolvePalette() {
        String theme = org.telegram.ox.OxMode.getOxTheme();
        boolean dark;
        if (org.telegram.ox.OxMode.THEME_LIGHT.equals(theme)) {
            dark = false;
        } else if (org.telegram.ox.OxMode.THEME_DARK.equals(theme)) {
            dark = true;
        } else {
            dark = (getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }
        darkPalette = dark;
        if (dark) {
            BONE = 0xFF141917;
            PAPER = 0xFF1E2521;
            INK = 0xFFEDE6D6;
            MUTED = 0xFF9AA69D;
            SIGNAL = 0xFFC8EF6A;
            LINE = 0x8CEDE6D6;
            CORAL = 0xFFE07A62;
            RIPPLE = 0x18EDE6D6;
            MENU_PAPER = 0xFF202724;
        } else {
            BONE = 0xFFF1EBDD;
            PAPER = 0xFFE5DCCB;
            INK = 0xFF14211C;
            MUTED = 0xFF566259;
            SIGNAL = 0xFFC8EF6A;
            LINE = 0x8C14211C;
            CORAL = 0xFFCE6549;
            RIPPLE = 0x1814211C;
            MENU_PAPER = 0xFFF7F3EA;
        }
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
        d.setCornerRadius(dp(radius));
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

        HorizontalScrollView railScroll = new HorizontalScrollView(this);
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

        LinearLayout composerWrap = new LinearLayout(this);
        composerWrap.setOrientation(LinearLayout.VERTICAL);
        composerWrap.setPadding(dp(16), dp(6), dp(16), dp(10));

        replyRow = new LinearLayout(this);
        replyRow.setOrientation(LinearLayout.HORIZONTAL);
        replyRow.setGravity(Gravity.CENTER_VERTICAL);
        replyRow.setPadding(dp(6), dp(4), dp(6), dp(2));
        replyRow.setVisibility(View.GONE);
        TextView replyArrow = new TextView(this);
        replyArrow.setText("↳");
        replyArrow.setTextSize(14);
        replyArrow.setTypeface(mono());
        replyArrow.setTextColor(MUTED);
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

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(8), dp(7), dp(8), 0);
        statusLine = new TextView(this);
        statusLine.setText(LocaleController.getString(R.string.OxUiFooter));
        statusLine.setTextSize(11);
        statusLine.setTypeface(sans());
        statusLine.setTextColor(MUTED);
        footer.addView(statusLine, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView open = new TextView(this);
        open.setText(LocaleController.getString(R.string.OxUiOpenTelegram));
        open.setTextSize(11);
        open.setTypeface(sans());
        open.setTextColor(INK);
        open.setOnClickListener(v -> openTelegramUi());
        footer.addView(open);
        composerWrap.addView(footer, new LinearLayout.LayoutParams(
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
            badge.setText(a.emoji == null || a.emoji.isEmpty() ? "◐" : a.emoji);
            badge.setTextSize(18);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(plate(PAPER, 20, LINE, 1));
            row.addView(badge, new LinearLayout.LayoutParams(dp(38), dp(38)));
            LinearLayout text = new LinearLayout(this);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(dp(13), 0, dp(8), 0);
            TextView nameView = new TextView(this);
            nameView.setText(name);
            nameView.setTextSize(16);
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
            badge.setText(h.isGroup ? "👥" : (h.agent.emoji == null || h.agent.emoji.isEmpty() ? "◐" : h.agent.emoji));
            badge.setTextSize(h.isGroup ? 16 : 18);
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
                recreate();
            });
            panel.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

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

        overlay.addView(panel, new FrameLayout.LayoutParams(
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
        meta.setTextSize(11);
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
            bubble.setTextSize(16);
            bubble.setTypeface(sans());
            bubble.setTextColor(BONE);
            bubble.setLineSpacing(dp(2), 1.05f);
            bubble.setPadding(dp(15), dp(11), dp(15), dp(11));
            bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.82f));
            GradientDrawable bg = plate(INK, 17, 0, 0);
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
        bubble.setTextSize(16);
        bubble.setTypeface(sans());
        bubble.setTextColor(BONE);
        bubble.setLineSpacing(dp(2), 1.05f);
        bubble.setPadding(dp(15), dp(11), dp(15), dp(11));
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.82f));
        GradientDrawable bg = plate(INK, 17, 0, 0);
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
        TextView meta = new TextView(this);
        meta.setText((isGroupTarget() && senderName != null ? senderName : targetName())
                + "  ·  " + time(ts));
        meta.setTextSize(11);
        meta.setTypeface(mono());
        meta.setTextColor(MUTED);
        meta.setLetterSpacing(0.02f);
        body.addView(meta);
        TextView t = new TextView(this);
        t.setText(styled(text));
        t.setTextSize(17);
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
