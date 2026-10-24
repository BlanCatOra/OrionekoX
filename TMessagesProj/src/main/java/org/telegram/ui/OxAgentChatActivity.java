package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ox.OxAgent;
import org.telegram.ox.OxAgentStore;
import org.telegram.ox.OxBuiltinTools;
import org.telegram.ox.OxChatEntry;
import org.telegram.ox.OxModelHub;
import org.telegram.ox.OxTool;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * OrionekoX agent chat with a full tool-calling loop, driven from the UI thread so
 * permission dialogs never block. Works identically against cloud and local OpenAI-compatible
 * backends; without a backend the agent answers in offline echo mode.
 */
@SuppressLint("ViewConstructor")
public class OxAgentChatActivity extends BaseFragment {

    private static final int MENU_SETTINGS = 1;
    private static final int MAX_TOOL_ROUNDS = 6;

    private String agentId;
    private OxAgent agent;
    private LinearLayout chatList;
    private ScrollView scrollView;
    private EditText inputField;
    private boolean generating;
    private boolean openSettingsOnStart;

    public OxAgentChatActivity(String id) {
        super();
        agentId = id;
    }

    public OxAgentChatActivity(String id, boolean openSettings) {
        super();
        agentId = id;
        openSettingsOnStart = openSettings;
    }

    public OxAgentChatActivity(Bundle args) {
        super(args);
        agentId = args != null ? args.getString("agentId") : null;
    }

    @Override
    public boolean onFragmentCreate() {
        agent = OxAgentStore.loadAgent(agentId);
        return agent != null && super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(agent.emoji + " " + agent.name);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_SETTINGS) {
                    showSettings();
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(MENU_SETTINGS, R.drawable.outline_profile_edit_24);

        FrameLayout rootLayout = new FrameLayout(context);

        chatList = new LinearLayout(context);
        chatList.setOrientation(LinearLayout.VERTICAL);
        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.addView(chatList, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rootLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout bottom = new LinearLayout(context);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        inputField = new EditText(context);
        inputField.setHint(LocaleController.getString(R.string.OxAgentInputHint));
        inputField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        inputField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        bottom.addView(inputField, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView send = new TextView(context);
        send.setText("➤");
        send.setTextSize(22);
        send.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10), AndroidUtilities.dp(14), AndroidUtilities.dp(10));
        send.setOnClickListener(v -> onSend());
        bottom.addView(send);

        rootLayout.addView(bottom, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 0, 0));
        fragmentView = rootLayout;

        rebuildChat();
        if (openSettingsOnStart) {
            openSettingsOnStart = false;
            AndroidUtilities.runOnUIThread(this::showSettings, 300);
        }
        return fragmentView;
    }

    private void rebuildChat() {
        if (chatList == null) return;
        chatList.removeAllViews();
        List<OxChatEntry> chat = OxAgentStore.loadChat(agentId);
        if (chat.isEmpty()) {
            addBubble("assistant", LocaleController.getString(R.string.OxAgentGreeting));
        } else {
            for (OxChatEntry e : chat) {
                if ("tool".equals(e.role)) {
                    addToolBubble(e.content, true);
                } else {
                    addBubble(e.role, e.content);
                }
            }
        }
        scrollToBottom();
    }

    // ---------------- send + tool loop ----------------

    private void onSend() {
        String text = inputField.getText().toString().trim();
        if (text.isEmpty() || generating) return;
        inputField.setText("");
        generating = true;
        addBubble("user", text);

        if (agent.isEchoMode()) {
            echoReply(text);
            return;
        }
        try {
            JSONArray messages = baseMessages();
            messages.put(new JSONObject().put("role", "user").put("content", text));
            runRound(messages, 0, text, new ArrayList<>());
        } catch (Exception e) {
            finishTurn(text, "⚠ " + e.getMessage(), new ArrayList<>());
        }
    }

    private JSONArray baseMessages() throws Exception {
        JSONArray messages = new JSONArray();
        if (agent.agentsMd != null && !agent.agentsMd.trim().isEmpty()) {
            messages.put(new JSONObject().put("role", "system").put("content", agent.agentsMd));
        }
        List<OxChatEntry> history = OxAgentStore.loadChat(agentId);
        int from = Math.max(0, history.size() - 20);
        for (int i = from; i < history.size(); i++) {
            OxChatEntry e = history.get(i);
            if ("user".equals(e.role) || "assistant".equals(e.role)) {
                messages.put(new JSONObject().put("role", e.role).put("content", e.content));
            }
        }
        return messages;
    }

    private void runRound(final JSONArray messages, final int depth, final String userText, final List<OxChatEntry> acc) {
        if (depth >= MAX_TOOL_ROUNDS) {
            finishTurn(userText, "⚠ 工具调用轮数达上限（" + MAX_TOOL_ROUNDS + "）", acc);
            return;
        }
        final TextView bubble = addBubble("assistant", "…");
        OxModelHub.completeRound(agent, messages, true, new OxModelHub.RoundCallback() {
            @Override
            public void onDelta(String piece) {
                appendToBubble(bubble, piece);
                scrollToBottom();
            }

            @Override
            public void onRound(final OxModelHub.Round round) {
                if (!round.hasToolCalls()) {
                    appendToBubble(bubble, round.content.isEmpty() ? "（空响应）" : "");
                    finishTurn(userText, bubble.getTag() == null ? round.content : (String) bubble.getTag(), acc);
                    return;
                }
                // model asked for tools - execute in order, then loop
                executeTools(round, messages, depth, userText, acc, bubble);
            }

            @Override
            public void onError(String message) {
                appendToBubble(bubble, "⚠ " + message);
                finishTurn(userText, "⚠ " + message, acc);
            }
        });
    }

    private void executeTools(final OxModelHub.Round round, final JSONArray messages, final int depth,
                              final String userText, final List<OxChatEntry> acc, final TextView staleBubble) {
        staleBubble.setText("🔧 …");
        // record the assistant tool_calls message for the next request
        try {
            JSONArray calls = new JSONArray();
            for (OxModelHub.ToolCall c : round.toolCalls) {
                JSONObject fn = new JSONObject();
                fn.put("name", c.name);
                fn.put("arguments", c.arguments.isEmpty() ? "{}" : new JSONObject(c.arguments));
                calls.put(new JSONObject().put("id", c.id).put("type", "function").put("function", fn));
            }
            JSONObject assistantMsg = new JSONObject();
            assistantMsg.put("role", "assistant");
            if (round.content != null && !round.content.isEmpty()) assistantMsg.put("content", round.content);
            assistantMsg.put("tool_calls", calls);
            messages.put(assistantMsg);
        } catch (Exception ignored) {
        }
        executeToolAt(0, round.toolCalls, messages, depth, userText, acc);
    }

    private void executeToolAt(final int i, final List<OxModelHub.ToolCall> calls, final JSONArray messages,
                               final int depth, final String userText, final List<OxChatEntry> acc) {
        if (i >= calls.size()) {
            runRound(messages, depth + 1, userText, acc);
            return;
        }
        final OxModelHub.ToolCall call = calls.get(i);
        final OxTool tool = OxBuiltinTools.ALL.get(call.name);
        if (tool == null) {
            toolResult(call, "error: unknown tool '" + call.name + "'", messages, depth, userText, acc, i, calls);
            return;
        }
        String policy = agent.toolPolicy(tool);
        if (OxTool.POLICY_DENY.equals(policy)) {
            toolResult(call, "denied: tool policy is 'deny' for this agent", messages, depth, userText, acc, i, calls);
            return;
        }
        final String argsPretty = call.arguments == null || call.arguments.trim().isEmpty() ? "{}" : call.arguments;
        Runnable run = () -> addToolBubble("🔧 " + call.name + "(" + argsPretty + ")", false);
        if (OxTool.POLICY_ASK.equals(policy)) {
            confirmTool(call, granted -> {
                if (!granted) {
                    toolResult(call, "denied by user", messages, depth, userText, acc, i, calls);
                } else {
                    run.run();
                    execTool(call, tool, messages, depth, userText, acc, i, calls);
                }
            });
        } else {
            run.run();
            execTool(call, tool, messages, depth, userText, acc, i, calls);
        }
    }

    private interface PermissionSink { void onDecision(boolean granted); }

    private void confirmTool(final OxModelHub.ToolCall call, final PermissionSink sink) {
        if (getParentActivity() == null) { sink.onDecision(false); return; }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.OxToolPermissionTitle));
        builder.setMessage(LocaleController.getString(R.string.OxToolPermissionText) + "\n\n🔧 " + call.name + "(" + call.arguments + ")");
        builder.setPositiveButton(LocaleController.getString(R.string.Allow), (d, w) -> sink.onDecision(true));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (d, w) -> sink.onDecision(false));
        builder.show();
    }

    private void execTool(final OxModelHub.ToolCall call, final OxTool tool, final JSONArray messages,
                          final int depth, final String userText, final List<OxChatEntry> acc,
                          final int i, final List<OxModelHub.ToolCall> calls) {
        new Thread(() -> {
            String result;
            try {
                JSONObject args = call.arguments == null || call.arguments.trim().isEmpty()
                        ? new JSONObject() : new JSONObject(call.arguments);
                result = tool.execute(args);
            } catch (Exception e) {
                result = "error: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
            final String r = result;
            AndroidUtilities.runOnUIThread(() -> {
                addToolBubble("↳ " + r, true);
                toolResult(call, r, messages, depth, userText, acc, i, calls);
            });
        }, "ox-tool").start();
    }

    private void toolResult(OxModelHub.ToolCall call, String result, JSONArray messages, int depth,
                            String userText, List<OxChatEntry> acc, int i, List<OxModelHub.ToolCall> calls) {
        try {
            JSONObject m = new JSONObject();
            m.put("role", "tool");
            m.put("tool_call_id", call.id);
            m.put("content", result);
            messages.put(m);
        } catch (Exception ignored) {
        }
        acc.add(new OxChatEntry("tool", "🔧 " + call.name + " → " + result, System.currentTimeMillis()));
        executeToolAt(i + 1, calls, messages, depth, userText, acc);
    }

    private void echoReply(final String userText) {
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("⚡ 离线回声模式（未配置模型 API Key）\n\n");
            sb.append("你说：").append(userText).append('\n');
            if (agent.agentsMd != null && !agent.agentsMd.trim().isEmpty()) {
                String firstLine = agent.agentsMd.split("\n")[0].replaceFirst("^#+\\s*", "");
                sb.append("\n我的人设（AGENTS.md）：").append(firstLine);
            }
            sb.append("\n\n👉 打开右上角菜单 → 设置，填写 Base URL / API Key / 模型后即可接入真实模型与工具调用。");
            final String full = sb.toString();
            AndroidUtilities.runOnUIThread(() -> {
                TextView bubble = addBubble("assistant", full);
                finishTurn(userText, full, new ArrayList<>());
            });
        }).start();
    }

    private void finishTurn(String userText, String reply, List<OxChatEntry> toolAcc) {
        generating = false;
        List<OxChatEntry> chat = OxAgentStore.loadChat(agentId);
        if (chat.isEmpty()) chat = new ArrayList<>();
        chat.add(new OxChatEntry("user", userText, System.currentTimeMillis()));
        chat.addAll(toolAcc);
        chat.add(new OxChatEntry("assistant", reply == null ? "" : reply, System.currentTimeMillis()));
        OxAgentStore.saveChat(agentId, chat);
    }

    // ---------------- bubbles ----------------

    private void appendToBubble(TextView bubble, String piece) {
        String cur = bubble.getTag() == null ? "" : (String) bubble.getTag();
        String next = cur + piece;
        bubble.setTag(next);
        if (!piece.isEmpty() && !"…".equals(cur)) bubble.setText(next);
        else if (!piece.isEmpty()) bubble.setText(next);
        else bubble.setText(cur.isEmpty() ? "…" : cur);
    }

    private TextView addBubble(String role, String text) {
        Context context = chatList.getContext();
        boolean mine = "user".equals(role);

        TextView bubble = new TextView(context);
        bubble.setText(text);
        bubble.setTextSize(16);
        bubble.setTag(text);
        bubble.setTextColor(mine ? Color.WHITE : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(14));
        bg.setColor(mine ? Theme.getColor(Theme.key_chat_outBubble) : Theme.getColor(Theme.key_chat_inBubble));
        bubble.setBackground(bg);

        int pad = AndroidUtilities.dp(12);
        bubble.setPadding(pad, pad / 2 + AndroidUtilities.dp(4), pad, pad / 2 + AndroidUtilities.dp(4));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(mine ? Gravity.END : Gravity.START);
        row.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(4), AndroidUtilities.dp(10), AndroidUtilities.dp(4));
        row.addView(bubble, new LinearLayout.LayoutParams(Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(120), ViewGroup.LayoutParams.WRAP_CONTENT), ViewGroup.LayoutParams.WRAP_CONTENT));

        chatList.addView(row);
        return bubble;
    }

    private void addToolBubble(String text, boolean result) {
        Context context = chatList.getContext();
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        tv.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(2), AndroidUtilities.dp(14), AndroidUtilities.dp(2));
        chatList.addView(tv);
        scrollToBottom();
    }

    private void scrollToBottom() {
        if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        }
    }

    // ---------------- settings ----------------

    private void showSettings() {
        if (getParentActivity() == null || agent == null) return;
        Context context = getParentActivity();
        LinearLayout wrap = new LinearLayout(context);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(16);
        wrap.setPadding(pad, pad / 2, pad, 0);

        final EditText name = field(context, wrap, LocaleController.getString(R.string.OxAgentNameHint2), agent.name);
        final EditText emoji = field(context, wrap, "Emoji", agent.emoji);
        final EditText baseUrl = field(context, wrap, "Base URL (OpenAI 兼容，云端如 https://api.deepseek.com；本地如 http://127.0.0.1:11434/v1)", agent.baseUrl);
        final EditText apiKey = field(context, wrap, "API Key（本地后端可留空）", agent.apiKey);
        final EditText model = field(context, wrap, "模型 (如 deepseek-chat / qwen2.5:7b)", agent.model);

        TextView mdLabel = new TextView(context);
        mdLabel.setText("AGENTS.md（人设/规则）");
        mdLabel.setTextSize(13);
        mdLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        wrap.addView(mdLabel);
        final EditText md = new EditText(context);
        md.setText(agent.agentsMd);
        md.setMinLines(6);
        md.setGravity(Gravity.TOP);
        md.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        wrap.addView(md, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(140)));

        StringBuilder toolsInfo = new StringBuilder("工具策略：get_time/device_info/get_me=自动；http_get=每次询问（可在 agent.json tools_policy 调整）");
        TextView tLabel = new TextView(context);
        tLabel.setText(toolsInfo);
        tLabel.setTextSize(12);
        tLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        wrap.addView(tLabel);

        ScrollView scroll = new ScrollView(context);
        scroll.addView(wrap);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.OxAgentSettings));
        builder.setView(scroll);
        builder.setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> {
            agent.name = name.getText().toString().trim();
            agent.emoji = emoji.getText().toString().trim().isEmpty() ? "🤖" : emoji.getText().toString().trim();
            agent.baseUrl = baseUrl.getText().toString().trim();
            agent.apiKey = apiKey.getText().toString().trim();
            agent.model = model.getText().toString().trim();
            agent.agentsMd = md.getText().toString();
            OxAgentStore.saveAgent(agent);
            if (actionBar != null) {
                actionBar.setTitle(agent.emoji + " " + agent.name);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    private EditText field(Context context, LinearLayout parent, String hint, String value) {
        EditText et = new EditText(context);
        et.setHint(hint);
        et.setText(value);
        et.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        et.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        parent.addView(et, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 8));
        return et;
    }
}
