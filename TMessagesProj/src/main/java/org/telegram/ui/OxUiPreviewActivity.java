package org.telegram.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ox.OxAgent;
import org.telegram.ox.OxAgentStore;
import org.telegram.ox.OxAgentsBridge;
import org.telegram.ox.OxChatEntry;
import org.telegram.ox.OxMode;

import java.text.SimpleDateFormat;
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

    private static final int BONE = 0xFFF1EBDD;
    private static final int PAPER = 0xFFE5DCCB;
    private static final int INK = 0xFF14211C;
    private static final int MUTED = 0xFF69766E;
    private static final int SIGNAL = 0xFFC8EF6A;
    private static final int LINE = 0x3314211C;
    private static final int CORAL = 0xFFCE6549;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<OxAgent> agents = new ArrayList<>();
    private OxAgent selected;

    private LinearLayout agentRail;
    private LinearLayout messages;
    private ScrollView scroll;
    private EditText input;
    private TextView send;
    private TextView statusLine;

    private String pendingText;
    private String pendingAgentId;
    private long pendingAt;
    private int pendingBaseSize;
    private String lastSignature = "";
    private boolean firstRender = true;

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
        Window w = getWindow();
        w.setStatusBarColor(BONE);
        w.setNavigationBarColor(BONE);
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            w.getDecorView().setSystemUiVisibility(flags);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            w.setStatusBarColor(BONE);
            w.setNavigationBarColor(BONE);
        }
        setContentView(buildRoot());
        loadAgents();
        handler.post(poll);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
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
        word.setTextSize(27);
        word.setTypeface(serif());
        word.setTextColor(INK);
        word.setLetterSpacing(-0.02f);
        title.addView(word);
        TextView sub = new TextView(this);
        sub.setText("Local agent studio");
        sub.setTextSize(10.5f);
        sub.setTypeface(sans());
        sub.setTextColor(MUTED);
        sub.setLetterSpacing(0.08f);
        title.addView(sub);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView preview = new TextView(this);
        preview.setText("Preview");
        preview.setTextSize(10.5f);
        preview.setTypeface(mono());
        preview.setTextColor(INK);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(dp(10), dp(5), dp(10), dp(5));
        preview.setBackground(plate(SIGNAL, 20, 0, 0));
        header.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));

        TextView telegram = new TextView(this);
        telegram.setText("Telegram UI");
        telegram.setTextSize(11);
        telegram.setTypeface(sans());
        telegram.setTextColor(INK);
        telegram.setGravity(Gravity.CENTER);
        telegram.setPadding(dp(11), dp(5), dp(11), dp(5));
        telegram.setBackground(plate(0, 18, LINE, 1));
        telegram.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(30));
        tlp.leftMargin = dp(8);
        header.addView(telegram, tlp);

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View rule = new View(this);
        rule.setBackgroundColor(LINE);
        root.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        HorizontalScrollView railScroll = new HorizontalScrollView(this);
        railScroll.setHorizontalScrollBarEnabled(false);
        railScroll.setClipToPadding(false);
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

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(6), dp(4), dp(6), dp(4));
        composer.setBackground(plate(PAPER, 22, LINE, 1));

        input = new EditText(this);
        input.setHint(LocaleController.getString(R.string.OxUiHint));
        input.setHintTextColor(MUTED);
        input.setTextColor(INK);
        input.setTextSize(15.5f);
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
        send.setTextSize(21);
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
        statusLine.setTextSize(10.5f);
        statusLine.setTypeface(sans());
        statusLine.setTextColor(MUTED);
        footer.addView(statusLine, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView open = new TextView(this);
        open.setText(LocaleController.getString(R.string.OxUiOpenTelegram));
        open.setTextSize(10.5f);
        open.setTypeface(sans());
        open.setTextColor(INK);
        open.setOnClickListener(v -> finish());
        footer.addView(open);
        composerWrap.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(composerWrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                boolean ready = selected != null && selected.tgId != 0 && s.toString().trim().length() > 0;
                send.setAlpha(ready ? 1f : 0.42f);
                send.setEnabled(ready);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        send.setEnabled(false);
        return root;
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
        for (OxAgent a : agents) {
            boolean active = selected != null && a.id.equals(selected.id);
            TextView chip = new TextView(this);
            String label = (a.emoji == null || a.emoji.isEmpty() ? "" : a.emoji + "  ")
                    + (a.name == null ? "Agent" : a.name);
            chip.setText(label);
            chip.setTextSize(13);
            chip.setTypeface(active ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : sans());
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
        if (selected == null) return new ArrayList<>();
        return OxAgentStore.loadChat(selected.id);
    }

    private void refresh(boolean force) {
        if (selected == null) {
            renderEmpty(LocaleController.getString(R.string.OxUiNoAgentTitle),
                    LocaleController.getString(R.string.OxUiNoAgentBody));
            return;
        }
        List<OxChatEntry> chat = history();
        if (pendingText != null && pendingAgentId != null
                && pendingAgentId.equals(selected.id)) {
            if (chat.size() > pendingBaseSize && containsUser(chat, pendingText, pendingBaseSize)) {
                pendingText = null;
                pendingAgentId = null;
            } else if (System.currentTimeMillis() - pendingAt > 120_000) {
                pendingText = null;
                pendingAgentId = null;
            }
        }
        String sig = signature(chat) + "|"
                + (pendingText == null ? "" : "pending:" + pendingText);
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
                + ":" + (last == null || last.content == null ? 0 : last.content.hashCode());
    }

    private void renderEmpty(String title, String body) {
        messages.removeAllViews();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(54), 0, 0);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(26);
        t.setTypeface(serif());
        t.setTextColor(INK);
        t.setLineSpacing(dp(2), 1f);
        box.addView(t);
        TextView b = new TextView(this);
        b.setText(body);
        b.setTextSize(14.5f);
        b.setTypeface(sans());
        b.setTextColor(MUTED);
        b.setLineSpacing(dp(4), 1.05f);
        b.setPadding(0, dp(12), dp(24), 0);
        box.addView(b);
        messages.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void renderConversation(List<OxChatEntry> chat) {
        messages.removeAllViews();
        if (chat.isEmpty() && pendingText == null) {
            renderEmpty(LocaleController.getString(R.string.OxUiEmptyTitle),
                    LocaleController.getString(R.string.OxUiEmptyBody));
            return;
        }
        int start = Math.max(0, chat.size() - 80);
        for (int i = start; i < chat.size(); i++) {
            OxChatEntry e = chat.get(i);
            if (e == null || e.content == null || e.content.trim().isEmpty()) continue;
            if ("user".equals(e.role)) {
                addUser(e.content, e.ts, false);
            } else if ("assistant".equals(e.role)) {
                addAssistant(e.content, e.ts);
            } else if ("tool".equals(e.role)) {
                addTool(e.content);
            }
        }
        if (pendingText != null) {
            addUser(pendingText, System.currentTimeMillis(), true);
            addWorking();
        }
        final boolean animate = firstRender;
        firstRender = false;
        scroll.post(() -> {
            scroll.fullScroll(View.FOCUS_DOWN);
            if (animate) {
                messages.setAlpha(0f);
                messages.setTranslationY(dp(10));
                messages.animate().alpha(1f).translationY(0f).setDuration(260).start();
            }
        });
    }

    private void addUser(String text, long ts, boolean sending) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(0, dp(12), 0, 0);
        TextView bubble = new TextView(this);
        bubble.setText(styled(text));
        bubble.setTextSize(15.5f);
        bubble.setTypeface(sans());
        bubble.setTextColor(BONE);
        bubble.setLineSpacing(dp(2), 1.05f);
        bubble.setPadding(dp(15), dp(11), dp(15), dp(11));
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.82f));
        GradientDrawable bg = plate(INK, 17, 0, 0);
        if (sending) bg.setAlpha(190);
        bubble.setBackground(bg);
        bubble.setTextIsSelectable(true);
        row.addView(bubble, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        messages.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addAssistant(String text, long ts) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(16), 0, 0);

        View accent = new View(this);
        accent.setBackgroundColor(SIGNAL);
        row.addView(accent, new LinearLayout.LayoutParams(dp(2), dp(46)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(13), 0, dp(8), 0);
        TextView meta = new TextView(this);
        meta.setText((selected == null || selected.name == null ? "Agent" : selected.name)
                + "  ·  " + time(ts));
        meta.setTextSize(10.5f);
        meta.setTypeface(mono());
        meta.setTextColor(MUTED);
        meta.setLetterSpacing(0.02f);
        body.addView(meta);
        TextView t = new TextView(this);
        t.setText(styled(text));
        t.setTextSize(16.2f);
        t.setTypeface(serif());
        t.setTextColor(INK);
        t.setLineSpacing(dp(4), 1.06f);
        t.setPadding(0, dp(3), 0, 0);
        t.setTextIsSelectable(true);
        body.addView(t);
        row.addView(body, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        messages.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addTool(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(11.2f);
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
        TextView t = new TextView(this);
        t.setText("  " + LocaleController.getString(R.string.OxUiThinking));
        t.setTextSize(11.5f);
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
        if (selected == null) {
            Toast.makeText(this, R.string.OxUiNoAgentTitle, Toast.LENGTH_SHORT).show();
            return;
        }
        if (selected.tgId == 0) {
            Toast.makeText(this, R.string.OxUiNoAgentBody, Toast.LENGTH_SHORT).show();
            return;
        }
        String text = input.getText() == null ? "" : input.getText().toString().trim();
        if (text.isEmpty()) return;
        pendingText = text;
        pendingAgentId = selected.id;
        pendingAt = System.currentTimeMillis();
        pendingBaseSize = history().size();
        lastSignature = "";
        input.setText("");
        refresh(true);
        OxAgentsBridge.sendFromOxUi(selected, text);
    }
}
