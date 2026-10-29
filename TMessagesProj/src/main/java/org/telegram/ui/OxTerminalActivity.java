package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ox.OxAgent;
import org.telegram.ox.OxAgentStore;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

/**
 * Local terminal with several independent shells ("TTY" sessions). Each shell is
 * persistent, starts in shared storage so ordinary commands such as ls work, and
 * keeps its own scrollback when the user switches sessions.
 */
@SuppressLint("ViewConstructor")
public class OxTerminalActivity extends BaseFragment {

    private static final int SESSION_COUNT = 3;
    public static final String PREFS = "ox_terminal";
    public static final String PREF_SCHEME = "scheme";
    public static final String PREF_FONT_SIZE = "font_size";

    private final Session[] sessions = new Session[SESSION_COUNT];
    private int currentSession;
    private int sessionGeneration;

    private TextView outputView;
    private ScrollView scrollView;
    private EditText inputField;
    private TextView statusView;
    private LinearLayout tabStrip;
    private final TextView[] tabViews = new TextView[SESSION_COUNT];
    private LinearLayout inputRow;
    private TextView promptView;
    private TextView sendView;

    private int schemeBg = 0xFF0E1116;
    private int schemeFg = 0xFFD7E0EA;
    private int schemeAccent = 0xFF2E9E5B;

    /** Carried ANSI style state so coloring continues correctly across streamed chunks. */
    private final AnsiState ansiState = new AnsiState();

    private final SharedPreferences.OnSharedPreferenceChangeListener appearanceListener =
            (prefs, key) -> {
                if (PREF_SCHEME.equals(key) || PREF_FONT_SIZE.equals(key)) {
                    AndroidUtilities.runOnUIThread(this::applyAppearance);
                }
            };

    private static class Session {
        Process shell;
        BufferedWriter shellIn;
        final StringBuilder buffer = new StringBuilder();
        boolean running;
        int generation;
        boolean started;
    }

    public OxTerminalActivity() {
        super();
    }

    public OxTerminalActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        prefs().registerOnSharedPreferenceChangeListener(appearanceListener);
        startSession(currentSession, true);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        prefs().unregisterOnSharedPreferenceChangeListener(appearanceListener);
        for (int i = 0; i < SESSION_COUNT; i++) {
            stopSession(i);
        }
        super.onFragmentDestroy();
    }

    private SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override
    public void onResume() {
        super.onResume();
        applyAppearance();
    }

    @Override
    public View createView(Context context) {
        if (getParentActivity() != null) {
            getParentActivity().getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(LocaleController.getString(R.string.OxTerminal));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        final boolean presented = getArguments() != null && getArguments().getBoolean("presented", false);
        final int baseTopPadding = presented ? 0 : AndroidUtilities.statusBarHeight + AndroidUtilities.dp(56);
        final int closedBottomPadding = (presented ? AndroidUtilities.dp(8)
                : AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 8))
                + AndroidUtilities.navigationBarHeight;

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(12), baseTopPadding, AndroidUtilities.dp(12), closedBottomPadding);
        root.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom;
            int nav = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom;
            int bottom = ime > 0 ? ime + AndroidUtilities.dp(6)
                    : (presented ? AndroidUtilities.dp(8)
                    : AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 8)) + nav;
            content.setPadding(AndroidUtilities.dp(12), baseTopPadding, AndroidUtilities.dp(12), bottom);
            return insets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(root);

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);

        statusView = new TextView(context);
        statusView.setText("sh · " + workDir().getAbsolutePath());
        statusView.setTextSize(11);
        statusView.setSingleLine(true);
        statusView.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        statusView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        toolbar.addView(statusView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        android.widget.ImageView keyboard = new android.widget.ImageView(context);
        keyboard.setImageResource(R.drawable.ox_keyboard_24);
        keyboard.setColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        keyboard.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6));
        keyboard.setContentDescription(LocaleController.getString(R.string.OxTerminalKeyboard));
        keyboard.setOnClickListener(v -> toggleKeyboard());
        toolbar.addView(keyboard, LayoutHelper.createLinear(40, 34));
        TextView clear = toolbarButton(context, LocaleController.getString(R.string.OxTerminalClear), v -> clearCurrentSession());
        toolbar.addView(clear);
        TextView restart = toolbarButton(context, LocaleController.getString(R.string.OxTerminalRestart), v -> restartCurrentSession());
        toolbar.addView(restart);
        content.addView(toolbar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Browser-style TTY tabs: tap a tab to switch directly instead of cycling.
        tabStrip = new LinearLayout(context);
        tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        tabStrip.setPadding(0, AndroidUtilities.dp(2), 0, AndroidUtilities.dp(6));
        for (int i = 0; i < SESSION_COUNT; i++) {
            final int index = i;
            TextView tab = new TextView(context);
            tab.setText("TTY " + (i + 1));
            tab.setTextSize(12);
            tab.setTypeface(AndroidUtilities.bold());
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6), 0);
            tab.setOnClickListener(v -> switchSession(index));
            tab.setOnLongClickListener(v -> { showAssignAgent(index); return true; });
            tabViews[i] = tab;
            tabStrip.addView(tab, LayoutHelper.createLinear(0, 34, 1f, 2, 0, 2, 0));
        }
        content.addView(tabStrip, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        updateTabStrip();

        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);

        outputView = new TextView(context);
        outputView.setTypeface(Typeface.MONOSPACE);
        outputView.setTextIsSelectable(true);
        outputView.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        scrollView.addView(outputView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scrollView.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN && inputField != null && isImeVisible()) {
                AndroidUtilities.hideKeyboard(inputField);
                inputField.clearFocus();
            }
            return false;
        });
        content.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f, 0, 6, 0, 6));

        inputRow = new LinearLayout(context);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);

        promptView = new TextView(context);
        promptView.setText("❯");
        promptView.setTextSize(18);
        promptView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(6), 0);
        inputRow.addView(promptView);

        inputField = new EditText(context);
        inputField.setHint(LocaleController.getString(R.string.OxTerminalHint));
        inputField.setTextSize(14);
        inputField.setTypeface(Typeface.MONOSPACE);
        inputField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        inputField.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        inputField.setSingleLine(true);
        inputField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCommand();
                return true;
            }
            return false;
        });
        inputRow.addView(inputField, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        sendView = new TextView(context);
        sendView.setText("➤");
        sendView.setTextSize(20);
        sendView.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        sendView.setOnClickListener(v -> sendCommand());
        inputRow.addView(sendView);

        android.widget.ImageView keyboardInline = new android.widget.ImageView(context);
        keyboardInline.setImageResource(R.drawable.ox_keyboard_24);
        keyboardInline.setColorFilter(schemeAccent);
        keyboardInline.setPadding(AndroidUtilities.dp(9), AndroidUtilities.dp(9), AndroidUtilities.dp(9), AndroidUtilities.dp(9));
        keyboardInline.setContentDescription(LocaleController.getString(R.string.OxTerminalKeyboard));
        keyboardInline.setOnClickListener(v -> toggleKeyboard());
        inputRow.addView(keyboardInline, LayoutHelper.createLinear(42, 42, Gravity.CENTER_VERTICAL));

        content.addView(inputRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        fragmentView = root;
        applyAppearance();
        refreshOutputFromSession();
        return root;
    }

    private TextView toolbarButton(Context context, String text, View.OnClickListener listener) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setTextSize(12);
        button.setTypeface(AndroidUtilities.bold());
        button.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        button.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6));
        button.setOnClickListener(listener);
        return button;
    }

    private void applyAppearance() {
        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int scheme = prefs.getInt(PREF_SCHEME, 0);
        int fontSize = prefs.getInt(PREF_FONT_SIZE, 14);
        switch (scheme) {
            case 1: schemeBg = 0xFF000000; schemeFg = 0xFFF2F2F2; schemeAccent = 0xFF4CD964; break;
            case 2: schemeBg = 0xFF120D00; schemeFg = 0xFFFFB000; schemeAccent = 0xFFFFC94D; break;
            case 3: schemeBg = 0xFFF7F4EE; schemeFg = 0xFF1F2328; schemeAccent = 0xFF1A73E8; break;
            case 4: schemeBg = 0xFF2C001E; schemeFg = 0xFFF2EAF0; schemeAccent = 0xFFE95420; break;
            default:
                schemeBg = 0xFF0E1116;
                schemeFg = 0xFFD7E0EA;
                schemeAccent = org.telegram.ox.OxTheme.hasCustom()
                        ? org.telegram.ox.OxTheme.accent() : 0xFF2E9E5B;
                break;
        }
        if (scrollView != null) {
            GradientDrawable panel = new GradientDrawable();
            panel.setColor(schemeBg);
            panel.setCornerRadius(AndroidUtilities.dp(12));
            scrollView.setBackground(panel);
        }
        if (outputView != null) {
            outputView.setTextColor(schemeFg);
            outputView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
        }
        if (inputRow != null) {
            inputRow.setBackgroundColor(schemeBg);
        }
        if (inputField != null) {
            inputField.setTextColor(schemeFg);
            inputField.setHintTextColor((schemeFg & 0x00FFFFFF) | 0x66000000);
            inputField.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
        }
        if (promptView != null) {
            promptView.setTextColor(schemeAccent);
        }
        if (sendView != null) {
            sendView.setTextColor(schemeAccent);
        }
    }

    private boolean isImeVisible() {
        try {
            androidx.core.view.WindowInsetsCompat insets =
                    androidx.core.view.ViewCompat.getRootWindowInsets(fragmentView);
            return insets != null && insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void toggleKeyboard() {
        if (inputField == null) return;
        if (inputField.hasFocus() || isImeVisible()) {
            AndroidUtilities.hideKeyboard(inputField);
            inputField.clearFocus();
        } else {
            inputField.requestFocus();
            AndroidUtilities.showKeyboard(inputField);
        }
    }

    private File workDir() {
        try {
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloads != null && downloads.isDirectory() && downloads.listFiles() != null) {
                return downloads;
            }
        } catch (Throwable ignored) {
        }
        try {
            File external = ApplicationLoader.applicationContext.getExternalFilesDir(null);
            if (external != null) {
                external.mkdirs();
                return external;
            }
        } catch (Throwable ignored) {
        }
        File files = ApplicationLoader.applicationContext.getFilesDir();
        files.mkdirs();
        return files;
    }

    private void appendToSession(int index, String text) {
        Session session = sessions[index];
        if (session == null) return;
        synchronized (session.buffer) {
            session.buffer.append(text);
        }
        if (index == currentSession) {
            appendToView(text);
        }
    }

    private void appendToView(String text) {
        if (outputView == null) return;
        AndroidUtilities.runOnUIThread(() -> {
            if (outputView == null) return;
            outputView.append(ansiToSpannable(text, ansiState));
            if (scrollView != null) {
                scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            }
        });
    }

    private void sendCommand() {
        if (inputField == null) return;
        String cmd = inputField.getText().toString().trim();
        if (cmd.isEmpty()) return;
        inputField.setText("");
        if ("clear".equals(cmd)) {
            clearCurrentSession();
            return;
        }
        if ("help".equals(cmd)) {
            appendToSession(currentSession, LocaleController.getString(R.string.OxTerminalHelp) + "\n");
            return;
        }
        Session session = sessions[currentSession];
        if (session == null || !session.running || session.shellIn == null) {
            startSession(currentSession, false);
            session = sessions[currentSession];
        }
        appendToSession(currentSession, "❯ " + cmd + "\n");
        if (session == null || !session.running || session.shellIn == null) {
            appendToSession(currentSession, "shell 未运行，请点击重启。\n");
            return;
        }
        try {
            session.shellIn.write(cmd);
            session.shellIn.write("\n");
            session.shellIn.flush();
        } catch (Throwable t) {
            appendToSession(currentSession, "写入失败: " + t.getMessage() + "\n");
        }
    }

    private void startSession(int index, boolean clearBuffer) {
        Session session = sessions[index];
        if (session == null) {
            session = new Session();
            sessions[index] = session;
        }
        stopSession(index);
        synchronized (session.buffer) {
            if (clearBuffer || session.buffer.length() == 0) {
                session.buffer.setLength(0);
            }
        }
        if (clearBuffer) {
            ansiState.reset();
        }
        final int generation = ++sessionGeneration;
        session.generation = generation;
        session.started = true;
        try {
            File work = workDir();
            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh");
            pb.redirectErrorStream(true);
            pb.directory(work);
            pb.environment().put("PATH", "/system/bin:/system/xbin:/vendor/bin");
            pb.environment().put("HOME", work.getAbsolutePath());
            pb.environment().put("PWD", work.getAbsolutePath());
            pb.environment().put("TERM", "xterm-256color");
            Process process = pb.start();
            session.shell = process;
            session.shellIn = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            session.running = true;
            final Session readSession = session;
            Thread readerThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (readSession.generation != generation) return;
                        appendToSession(index, line + "\n");
                    }
                } catch (Throwable ignored) {
                }
                if (readSession.generation == generation) {
                    readSession.running = false;
                    appendToSession(index, "[shell 已退出]\n");
                }
            }, "ox-terminal-" + index);
            readerThread.setDaemon(true);
            readerThread.start();
            if (clearBuffer) {
                appendToSession(index, "Ox Terminal · TTY " + (index + 1) + "\n");
                appendToSession(index, "工作目录: " + work.getAbsolutePath() + "\n");
                appendToSession(index, "输入 help 查看常用命令，点按键盘图标可收起键盘。\n\n");
            }
            try {
                session.shellIn.write("pwd\n");
                session.shellIn.flush();
            } catch (Throwable ignored) {
            }
            if (index == currentSession) {
                AndroidUtilities.runOnUIThread(this::refreshOutputFromSession);
            }
        } catch (Throwable t) {
            session.running = false;
            appendToSession(index, "无法启动 shell: " + t.getMessage() + "\n");
        }
    }

    private void stopSession(int index) {
        Session session = sessions[index];
        if (session == null) return;
        session.generation = -1;
        session.running = false;
        try {
            if (session.shellIn != null) session.shellIn.close();
        } catch (Throwable ignored) {
        }
        try {
            if (session.shell != null) session.shell.destroy();
        } catch (Throwable ignored) {
        }
        session.shell = null;
        session.shellIn = null;
    }

    private void clearCurrentSession() {
        Session session = sessions[currentSession];
        if (session != null) {
            synchronized (session.buffer) {
                session.buffer.setLength(0);
            }
        }
        ansiState.reset();
        if (outputView != null) outputView.setText("");
        updateTabStrip();
    }

    private void restartCurrentSession() {
        if (outputView != null) outputView.setText("");
        startSession(currentSession, true);
    }

    private void switchSession(int index) {
        if (index == currentSession) return;
        currentSession = index;
        if (outputView != null) outputView.setText("");
        if (inputField != null) inputField.setText("");
        updateTabStrip();
        Session session = sessions[index];
        if (session == null || !session.started) {
            startSession(index, true);
        } else {
            if (!session.running) startSession(index, false);
            refreshOutputFromSession();
        }
    }

    private void showAssignAgent(int index) {
        if (getParentActivity() == null) return;
        java.util.List<OxAgent> agents = OxAgentStore.listAgents();
        String current = prefs().getString("tty_agent_" + index, "");
        ArrayList<CharSequence> names = new ArrayList<>();
        ArrayList<String> ids = new ArrayList<>();
        for (OxAgent a : agents) {
            names.add(a.name + (a.id.equals(current) ? " ✓" : ""));
            ids.add(a.id);
        }
        names.add(LocaleController.getString(R.string.OxTerminalUnassign));
        ids.add("");
        new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                .setTitle(LocaleController.getString(R.string.OxTerminalAssignAgent))
                .setItems(names.toArray(new CharSequence[0]), (d, which) -> {
                    String id = ids.get(which);
                    prefs().edit().putString("tty_agent_" + index, id).apply();
                    updateTabStrip();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private void updateTabStrip() {
        if (tabViews[0] == null) return;
        int selectedBg = getThemedColor(Theme.key_featuredStickers_addButton);
        int selectedText = getThemedColor(Theme.key_featuredStickers_buttonText);
        int normalBg = getThemedColor(Theme.key_windowBackgroundWhite);
        int normalText = getThemedColor(Theme.key_windowBackgroundWhiteBlackText);
        for (int i = 0; i < SESSION_COUNT; i++) {
            TextView tab = tabViews[i];
            if (tab == null) continue;
            String assigned = prefs().getString("tty_agent_" + i, "");
            OxAgent agent = assigned == null || assigned.isEmpty() ? null : OxAgentStore.loadAgent(assigned);
            tab.setText("TTY " + (i + 1) + (agent != null ? " · " + agent.name : ""));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(AndroidUtilities.dp(9));
            bg.setColor(i == currentSession ? selectedBg : normalBg);
            tab.setBackground(bg);
            tab.setTextColor(i == currentSession ? selectedText : normalText);
        }
    }

    private void refreshOutputFromSession() {
        Session session = sessions[currentSession];
        if (session == null || outputView == null) return;
        String text;
        synchronized (session.buffer) {
            text = session.buffer.toString();
        }
        AnsiState fresh = new AnsiState();
        outputView.setText(ansiToSpannable(text, fresh));
        ansiState.reset();
        ansiState.fg = fresh.fg;
        ansiState.bg = fresh.bg;
        ansiState.bold = fresh.bold;
        ansiState.underline = fresh.underline;
        if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    /** 16-colour ANSI palette (xterm / Windows Terminal dark set), indices 0-15. */
    private static final int[] ANSI_16 = {
            0xFF0C0C0C, 0xFFC50F1F, 0xFF13A10E, 0xFFC19C00, // 0-3  black red green yellow
            0xFF0037DA, 0xFF881798, 0xFF3A96DD, 0xFFCCCCCC, // 4-7  blue magenta cyan white
            0xFF767676, 0xFFE74856, 0xFF16C60C, 0xFFF9F1A5, // 8-11 bright black red green yellow
            0xFF3B78FF, 0xFFB4009E, 0xFF61D6D6, 0xFFF2F2F2  // 12-15 bright blue magenta cyan white
    };

    private static final class AnsiState {
        int fg = -1;
        int bg = -1;
        boolean bold;
        boolean underline;
        void reset() {
            fg = -1;
            bg = -1;
            bold = false;
            underline = false;
        }
    }

    private void flushAnsiRun(SpannableStringBuilder ssb, StringBuilder run,
                              int fg, int bg, boolean bold, boolean underline) {
        if (run.length() == 0) return;
        int start = ssb.length();
        ssb.append(run);
        int end = ssb.length();
        if (fg != -1) {
            ssb.setSpan(new ForegroundColorSpan(fg), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (bg != -1) {
            ssb.setSpan(new BackgroundColorSpan(bg), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (bold) {
            ssb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (underline) {
            ssb.setSpan(new UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        run.setLength(0);
    }

    /**
     * Convert raw shell output into a {@link SpannableStringBuilder}, honouring the
     * 16-colour ANSI SGR palette (foreground/background, bold, underline) and
     * stripping control sequences (cursor moves, erase, OSC titles) that would
     * otherwise render as literal garbage inside the TextView.
     */
    private SpannableStringBuilder ansiToSpannable(String raw, AnsiState state) {
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        int n = raw == null ? 0 : raw.length();
        StringBuilder run = new StringBuilder();
        int runFg = state.fg;
        int runBg = state.bg;
        boolean runBold = state.bold;
        boolean runUnderline = state.underline;
        int i = 0;
        while (i < n) {
            char c = raw.charAt(i);
            if (c == 0x1B) { // ESC
                flushAnsiRun(ssb, run, runFg, runBg, runBold, runUnderline);
                if (i + 1 < n && raw.charAt(i + 1) == '[') { // CSI
                    int j = i + 2;
                    StringBuilder params = new StringBuilder();
                    while (j < n) {
                        char d = raw.charAt(j);
                        if ((d >= '0' && d <= '9') || d == ';' || d == ':') {
                            params.append(d);
                            j++;
                        } else {
                            break;
                        }
                    }
                    if (j < n) {
                        char fin = raw.charAt(j);
                        if (fin == 'm') {
                            applyAnsiSgr(params.toString(), state);
                        }
                        i = j + 1; // any other CSI sequence is stripped, never rendered
                    } else {
                        i = j;
                    }
                    runFg = state.fg;
                    runBg = state.bg;
                    runBold = state.bold;
                    runUnderline = state.underline;
                    continue;
                } else if (i + 1 < n && raw.charAt(i + 1) == ']') { // OSC (e.g. window title)
                    int j = i + 2;
                    while (j < n && raw.charAt(j) != 0x07) {
                        if (raw.charAt(j) == 0x1B && j + 1 < n && raw.charAt(j + 1) == '\\') {
                            j += 2;
                            break;
                        }
                        j++;
                    }
                    i = (j < n) ? j + 1 : j;
                    continue;
                } else {
                    i++; // lone ESC
                    continue;
                }
            }
            run.append(c);
            i++;
        }
        flushAnsiRun(ssb, run, runFg, runBg, runBold, runUnderline);
        return ssb;
    }

    private void applyAnsiSgr(String params, AnsiState state) {
        if (params.isEmpty()) {
            state.reset();
            return;
        }
        String[] parts = params.split(";");
        for (int k = 0; k < parts.length; k++) {
            String p = parts[k];
            if (p.isEmpty()) p = "0";
            int code;
            try {
                code = Integer.parseInt(p);
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (code == 0) {
                state.reset();
            } else if (code == 1) {
                state.bold = true;
            } else if (code == 4) {
                state.underline = true;
            } else if (code == 22) {
                state.bold = false;
            } else if (code == 24) {
                state.underline = false;
            } else if (code >= 30 && code <= 37) {
                state.fg = ANSI_16[code - 30];
            } else if (code >= 40 && code <= 47) {
                state.bg = ANSI_16[code - 40];
            } else if (code >= 90 && code <= 97) {
                state.fg = ANSI_16[code - 90 + 8];
            } else if (code >= 100 && code <= 107) {
                state.bg = ANSI_16[code - 100 + 8];
            } else if (code == 39) {
                state.fg = -1;
            } else if (code == 49) {
                state.bg = -1;
            } else if (code == 38 || code == 48) {
                // Extended colour (38;5;n or 38;2;r;g;b): accepted and skipped so the
                // following text keeps the previous style instead of rendering garbage.
                if (k + 1 < parts.length) {
                    int mode;
                    try {
                        mode = Integer.parseInt(parts[k + 1]);
                    } catch (NumberFormatException ignored) {
                        mode = -1;
                    }
                    if (mode == 5 && k + 2 < parts.length) {
                        k += 2;
                    } else if (mode == 2 && k + 4 < parts.length) {
                        k += 4;
                    }
                }
            }
        }
    }
}
