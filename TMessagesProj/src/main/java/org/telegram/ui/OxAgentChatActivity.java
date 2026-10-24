package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
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

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ox.OxAgent;
import org.telegram.ox.OxAgentStore;
import org.telegram.ox.OxChatEntry;
import org.telegram.ox.OxModelHub;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

// OrionekoX M2 slice: local agent chat, Telegram-themed. Fully local - nothing leaves the
// device except the configured model API call. No MTProto traffic is produced.
@SuppressLint("ViewConstructor")
public class OxAgentChatActivity extends BaseFragment {

    private static final int MENU_SETTINGS = 1;

    private String agentId;
    private OxAgent agent;
    private LinearLayout chatList;
    private ScrollView scrollView;
    private EditText inputField;
    private TextView pendingBubble;
    private boolean generating;

    public OxAgentChatActivity(String id) {
        super();
        agentId = id;
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
        ActionBarMenuItem item = menu.addItem(MENU_SETTINGS, R.drawable.outline_profile_edit_24);

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
                addBubble(e.role, e.content);
            }
        }
        scrollToBottom();
    }

    private void onSend() {
        String text = inputField.getText().toString().trim();
        if (text.isEmpty() || generating) return;
        inputField.setText("");
        generating = true;
        addBubble("user", text);

        pendingBubble = addBubble("assistant", "…");
        OxModelHub.chat(agent, text, new OxModelHub.Callback() {
            @Override
            public void onDelta(String piece) {
                if (pendingBubble != null) {
                    String cur = (String) pendingBubble.getTag();
                    pendingBubble.setText(cur == null ? piece : cur + piece);
                    pendingBubble.setTag(cur == null ? piece : cur + piece);
                    scrollToBottom();
                }
            }

            @Override
            public void onDone(String full) {
                finishTurn(text, full);
            }

            @Override
            public void onError(String message) {
                finishTurn(text, "⚠ " + message);
            }
        });
    }

    private void finishTurn(String userText, String reply) {
        generating = false;
        pendingBubble = null;
        List<OxChatEntry> chat = OxAgentStore.loadChat(agentId);
        if (chat.isEmpty()) {
            chat = new ArrayList<>();
        }
        chat.add(new OxChatEntry("user", userText, System.currentTimeMillis()));
        chat.add(new OxChatEntry("assistant", reply, System.currentTimeMillis()));
        OxAgentStore.saveChat(agentId, chat);
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

    private void scrollToBottom() {
        if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        }
    }

    private void showSettings() {
        if (getParentActivity() == null || agent == null) return;
        Context context = getParentActivity();
        LinearLayout wrap = new LinearLayout(context);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(16);
        wrap.setPadding(pad, pad / 2, pad, 0);

        final EditText name = field(context, wrap, LocaleController.getString(R.string.OxAgentNameHint2), agent.name);
        final EditText emoji = field(context, wrap, "Emoji", agent.emoji);
        final EditText baseUrl = field(context, wrap, "Base URL (OpenAI 兼容，如 https://api.deepseek.com)", agent.baseUrl);
        final EditText apiKey = field(context, wrap, "API Key（留空=离线回声模式）", agent.apiKey);
        final EditText model = field(context, wrap, "模型 (如 deepseek-chat)", agent.model);

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
