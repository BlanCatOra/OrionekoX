package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
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
import org.telegram.ox.OxMcpManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.List;

/**
 * Remote MCP server management (Operit/Operit2 plugin-ecosystem compatible transport).
 * Servers listed here contribute their tools to every agent through the unified
 * OxMcpManager.tools() registry, gated by the same ask/auto/deny permission flow.
 */
@SuppressLint("ViewConstructor")
public class OxMcpActivity extends BaseFragment {

    private LinearLayout listContainer;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(LocaleController.getString(R.string.OxMcp));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        ActionBarMenu menu = actionBar.createMenu();

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
        // auto-refresh enabled servers in the background so tools stay current
        for (OxMcpManager.McpServer s : OxMcpManager.getServers()) {
            if (s.enabled) {
                new Thread(() -> {
                    OxMcpManager.refreshServer(s);
                    AndroidUtilities.runOnUIThread(this::rebuild);
                }, "ox-mcp-ui-refresh").start();
            }
        }
    }

    private void rebuild() {
        if (fragmentView == null) return;
        listContainer.removeAllViews();
        Context context = listContainer.getContext();

        TextView intro = new TextView(context);
        String introText = LocaleController.getString(R.string.OxMcpIntro);
        int count = OxMcpManager.mcpToolCount();
        if (count > 0) {
            introText += "\n\n" + LocaleController.formatString("OxMcpToolsAvailable", R.string.OxMcpToolsAvailable, count);
        }
        intro.setText(introText);
        intro.setTextSize(14);
        intro.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        intro.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(4));
        listContainer.addView(intro);

        List<OxMcpManager.McpServer> servers = OxMcpManager.getServers();
        if (servers.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(LocaleController.getString(R.string.OxMcpEmpty));
            empty.setTextSize(14);
            empty.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            empty.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(4));
            listContainer.addView(empty);
        }
        for (OxMcpManager.McpServer s : servers) {
            listContainer.addView(row(context, s));
        }

        TextView add = new TextView(context);
        add.setText("＋ " + LocaleController.getString(R.string.OxMcpAdd));
        add.setTextSize(16);
        add.setTypeface(Typeface.DEFAULT_BOLD);
        add.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        add.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(18), AndroidUtilities.dp(16), AndroidUtilities.dp(18));
        add.setOnClickListener(v -> showAddDialog());
        listContainer.addView(add);
    }

    private View row(Context context, OxMcpManager.McpServer s) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));

        TextView emoji = new TextView(context);
        emoji.setText(s.enabled ? "🔌" : "⏸");
        emoji.setTextSize(24);
        row.addView(emoji, LayoutHelper.createLinear(44, 44, Gravity.CENTER_VERTICAL));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(context);
        name.setText(s.name);
        name.setTextSize(17);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textCol.addView(name);

        TextView status = new TextView(context);
        status.setTextSize(13);
        status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        String line;
        if (!s.enabled) {
            line = LocaleController.getString(R.string.OxMcpDisabled);
        } else if (s.lastError != null) {
            line = "⚠ " + s.lastError;
        } else if (s.toolCount >= 0) {
            line = s.url + " · " + LocaleController.formatString("OxMcpToolsFound", R.string.OxMcpToolsFound, s.toolCount);
        } else {
            line = s.url;
        }
        status.setText(line);
        textCol.addView(status);
        row.addView(textCol, LayoutHelper.createLinear(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.setBackground(Theme.getSelectorDrawable(false));
        row.setOnClickListener(v -> {
            s.enabled = !s.enabled;
            OxMcpManager.saveServer(s);
            if (s.enabled) {
                new Thread(() -> {
                    OxMcpManager.refreshServer(s);
                    AndroidUtilities.runOnUIThread(this::rebuild);
                }, "ox-mcp-ui-refresh").start();
            }
            rebuild();
        });
        row.setOnLongClickListener(v -> {
            showServerMenu(s);
            return true;
        });
        return row;
    }

    private void showServerMenu(OxMcpManager.McpServer s) {
        if (getParentActivity() == null) return;
        java.util.ArrayList<CharSequence> items = new java.util.ArrayList<>();
        java.util.ArrayList<Runnable> actions = new java.util.ArrayList<>();
        items.add(LocaleController.getString(R.string.OxMcpRefresh));
        actions.add(() -> refreshNow(s));
        items.add(LocaleController.getString(R.string.Delete));
        actions.add(() -> {
            OxMcpManager.deleteServer(s.id);
            rebuild();
        });
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(s.name);
        builder.setItems(items.toArray(new CharSequence[0]), (d, which) -> {
            if (which >= 0 && which < actions.size()) actions.get(which).run();
        });
        builder.show();
    }

    private void refreshNow(OxMcpManager.McpServer s) {
        toast(LocaleController.getString(R.string.OxMcpRefreshing));
        new Thread(() -> {
            String result = OxMcpManager.refreshServer(s);
            AndroidUtilities.runOnUIThread(() -> {
                toast(result);
                rebuild();
            });
        }, "ox-mcp-ui-refresh").start();
    }

    private void showAddDialog() {
        if (getParentActivity() == null) return;
        Context ctx = getParentActivity();
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(16);
        wrap.setPadding(pad, pad / 2, pad, 0);
        final EditText name = new EditText(ctx);
        name.setHint(LocaleController.getString(R.string.OxMcpNameHint));
        wrap.addView(name, LayoutHelper.createLinear(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 8, 0, 8));
        final EditText url = new EditText(ctx);
        url.setHint(LocaleController.getString(R.string.OxMcpUrlHint));
        wrap.addView(url, LayoutHelper.createLinear(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 8, 0, 8));

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(LocaleController.getString(R.string.OxMcpAdd));
        builder.setView(wrap);
        builder.setPositiveButton(LocaleController.getString(R.string.Add), (dialog, which) -> {
            String n = name.getText().toString().trim();
            String u = url.getText().toString().trim();
            if (n.isEmpty() || !(u.startsWith("http://") || u.startsWith("https://"))) return;
            OxMcpManager.McpServer s = new OxMcpManager.McpServer(n, u);
            OxMcpManager.saveServer(s);
            rebuild();
            refreshNow(s);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    private void toast(String s) {
        if (getParentActivity() != null) {
            android.widget.Toast.makeText(getParentActivity(), s, android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
