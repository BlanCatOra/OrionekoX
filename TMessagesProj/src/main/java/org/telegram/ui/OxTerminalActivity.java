package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * A small local terminal. Runs /system/bin/sh as the app user (no root, no Telegram
 * connection). The shell is persistent, so cd/export survive between commands.
 * Layout follows Telegram's settings surfaces: themed colors and typography.
 */
@SuppressLint("ViewConstructor")
public class OxTerminalActivity extends BaseFragment {

    private TextView outputView;
    private ScrollView scrollView;
    private EditText inputField;
    private Process shell;
    private BufferedWriter shellIn;
    private volatile boolean running;
    private final StringBuilder pending = new StringBuilder();

    public OxTerminalActivity() {
        super();
    }

    public OxTerminalActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        startShell();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        stopShell();
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
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

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(12), AndroidUtilities.statusBarHeight + AndroidUtilities.dp(56),
                AndroidUtilities.dp(12), AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 8));
        root.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);

        TextView status = new TextView(context);
        status.setText("app shell · /system/bin/sh");
        status.setTextSize(12);
        status.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        toolbar.addView(status, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView clear = new TextView(context);
        clear.setText(LocaleController.getString(R.string.OxTerminalClear));
        clear.setTextSize(13);
        clear.setTypeface(AndroidUtilities.bold());
        clear.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        clear.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(6));
        clear.setOnClickListener(v -> {
            pending.setLength(0);
            if (outputView != null) outputView.setText("");
        });
        toolbar.addView(clear);

        TextView restart = new TextView(context);
        restart.setText(LocaleController.getString(R.string.OxTerminalRestart));
        restart.setTextSize(13);
        restart.setTypeface(AndroidUtilities.bold());
        restart.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        restart.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(6));
        restart.setOnClickListener(v -> {
            stopShell();
            startShell();
        });
        toolbar.addView(restart);
        content.addView(toolbar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(0xFF0E1116);
        panel.setCornerRadius(AndroidUtilities.dp(12));
        scrollView.setBackground(panel);

        outputView = new TextView(context);
        outputView.setTextSize(12);
        outputView.setTypeface(Typeface.MONOSPACE);
        outputView.setTextColor(0xFFD7E0EA);
        outputView.setTextIsSelectable(true);
        outputView.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        outputView.setText("Ox Terminal\n" + "----------------\n");
        scrollView.addView(outputView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f, 0, 6, 0, 6));

        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

        TextView prompt = new TextView(context);
        prompt.setText("❯");
        prompt.setTextSize(18);
        prompt.setTextColor(0xFF2E9E5B);
        prompt.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(6), 0);
        inputRow.addView(prompt);

        inputField = new EditText(context);
        inputField.setHint(LocaleController.getString(R.string.OxTerminalHint));
        inputField.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        inputField.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        inputField.setTextSize(14);
        inputField.setTypeface(Typeface.MONOSPACE);
        inputField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        inputField.setImeOptions(EditorInfo.IME_ACTION_SEND);
        inputField.setSingleLine(true);
        inputField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCommand();
                return true;
            }
            return false;
        });
        inputRow.addView(inputField, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView send = new TextView(context);
        send.setText("➤");
        send.setTextSize(20);
        send.setTextColor(0xFF2AABEE);
        send.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        send.setOnClickListener(v -> sendCommand());
        inputRow.addView(send);

        content.addView(inputRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        fragmentView = root;
        return root;
    }

    private void append(final String text) {
        if (outputView == null) return;
        AndroidUtilities.runOnUIThread(() -> {
            outputView.append(text);
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
            pending.setLength(0);
            if (outputView != null) outputView.setText("");
            return;
        }
        append("❯ " + cmd + "\n");
        if (!running || shellIn == null) {
            append("shell 未运行，请点击重启。\n");
            return;
        }
        try {
            shellIn.write(cmd);
            shellIn.write("\n");
            shellIn.flush();
        } catch (Throwable t) {
            append("写入失败: " + t.getMessage() + "\n");
        }
    }

    private void startShell() {
        if (running) return;
        running = true;
        try {
            File work = new File(ApplicationLoader.applicationContext.getFilesDir(), "oxterm");
            work.mkdirs();
            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh");
            pb.redirectErrorStream(true);
            pb.directory(work);
            shell = pb.start();
            shellIn = new BufferedWriter(new OutputStreamWriter(shell.getOutputStream()));
            final BufferedReader reader = new BufferedReader(new InputStreamReader(shell.getInputStream()));
            Thread t = new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        append(line + "\n");
                    }
                } catch (Throwable ignored) {
                }
                if (running) {
                    append("[shell 已退出]\n");
                }
            }, "ox-terminal-reader");
            t.setDaemon(true);
            t.start();
            append("工作目录: " + work.getAbsolutePath() + "\n");
            try {
                shellIn.write("id\n");
                shellIn.flush();
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            running = false;
            append("无法启动 shell: " + t.getMessage() + "\n");
        }
    }

    private void stopShell() {
        running = false;
        try {
            if (shellIn != null) shellIn.close();
        } catch (Throwable ignored) {
        }
        try {
            if (shell != null) shell.destroy();
        } catch (Throwable ignored) {
        }
        shell = null;
        shellIn = null;
    }
}
