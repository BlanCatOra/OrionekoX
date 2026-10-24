package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ox.OxAgent;
import org.telegram.ox.OxAgentStore;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.List;
import java.util.UUID;

// OrionekoX M2 slice: agent list ("以 bot 形态" 的管理入口). Chat itself lives in OxAgentChatActivity;
// the pseudo-dialog chat-list integration is the next slice.
@SuppressLint("ViewConstructor")
public class OxAgentsActivity extends BaseFragment {

    private LinearLayout listContainer;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(LocaleController.getString(R.string.OxAgents));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout rootLayout = new FrameLayout(context);
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listContainer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rootLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = rootLayout;
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        rebuild();
    }

    private void rebuild() {
        if (fragmentView == null) return;
        listContainer.removeAllViews();
        Context context = listContainer.getContext();

        TextView intro = new TextView(context);
        intro.setText(LocaleController.getString(R.string.OxAgentsIntro));
        intro.setTextSize(14);
        intro.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        intro.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(4));
        listContainer.addView(intro);

        List<OxAgent> agents = OxAgentStore.listAgents();
        for (OxAgent agent : agents) {
            listContainer.addView(row(context, agent));
        }

        TextView add = new TextView(context);
        add.setText("＋ " + LocaleController.getString(R.string.OxAgentNew));
        add.setTextSize(16);
        add.setTypeface(Typeface.DEFAULT_BOLD);
        add.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        add.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(18), AndroidUtilities.dp(16), AndroidUtilities.dp(18));
        add.setOnClickListener(v -> showCreateDialog());
        listContainer.addView(add);
    }

    private View row(Context context, OxAgent agent) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));

        TextView emoji = new TextView(context);
        emoji.setText(agent.emoji);
        emoji.setTextSize(28);
        row.addView(emoji, LayoutHelper.createLinear(48, 48, Gravity.CENTER_VERTICAL));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(context);
        name.setText(agent.name);
        name.setTextSize(17);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textCol.addView(name);

        TextView status = new TextView(context);
        status.setText(agent.statusLine());
        status.setTextSize(13);
        status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        textCol.addView(status);

        row.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        row.setBackground(Theme.getSelectorDrawable(false));
        row.setOnClickListener(v -> presentFragment(new OxAgentChatActivity(agent.id)));
        row.setOnLongClickListener(v -> {
            if (!agent.builtIn) showDeleteDialog(agent);
            return true;
        });
        return row;
    }

    private void showCreateDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.OxAgentNew));
        final android.widget.EditText input = new android.widget.EditText(getParentActivity());
        input.setHint(LocaleController.getString(R.string.OxAgentNameHint2));
        builder.setView(input);
        builder.setPositiveButton(LocaleController.getString(R.string.Done), (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) return;
            OxAgent a = new OxAgent();
            a.id = UUID.randomUUID().toString().substring(0, 8);
            a.name = name;
            a.emoji = "🤖";
            a.agentsMd = "# " + name + "\n\n(在聊天页右上角菜单里编辑这个人设与模型配置)";
            OxAgentStore.saveAgent(a);
            rebuild();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    private void showDeleteDialog(OxAgent agent) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.OxAgentDeleteTitle));
        builder.setMessage(LocaleController.getString(R.string.OxAgentDeleteText));
        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
            OxAgentStore.deleteAgent(agent.id);
            rebuild();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }
}
