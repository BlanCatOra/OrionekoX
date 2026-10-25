package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ox.OxMcpManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

import java.util.List;

/** MCP servers, rendered with Telegram settings cells. */
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

        FrameLayout rootLayout = new FrameLayout(context);
        rootLayout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, 0, 0, AndroidUtilities.dp(24));
        scrollView.addView(listContainer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rootLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = rootLayout;
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        rebuild();
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
        intro.setTextSize(13);
        intro.setLineSpacing(AndroidUtilities.dp(3), 1f);
        intro.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        intro.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(12), AndroidUtilities.dp(21), AndroidUtilities.dp(10));
        listContainer.addView(intro, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        List<OxMcpManager.McpServer> servers = OxMcpManager.getServers();
        if (servers.isEmpty()) {
            LinearLayout empty = new LinearLayout(context);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(32), AndroidUtilities.dp(32), AndroidUtilities.dp(18));
            FrameLayout iconWrap = new FrameLayout(context);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(getThemedColor(Theme.key_windowBackgroundGray));
            iconWrap.setBackground(circle);
            ImageView icon = new ImageView(context);
            icon.setImageResource(R.drawable.msg_link);
            icon.setColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            iconWrap.addView(icon, LayoutHelper.createFrame(28, 28, Gravity.CENTER));
            empty.addView(iconWrap, LayoutHelper.createLinear(64, 64, Gravity.CENTER_HORIZONTAL));
            TextView title = new TextView(context);
            title.setText(LocaleController.getString(R.string.OxMcpEmptyTitle));
            title.setTextSize(17);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, AndroidUtilities.dp(14), 0, 0);
            empty.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            TextView sub = new TextView(context);
            sub.setText(LocaleController.getString(R.string.OxMcpEmpty));
            sub.setTextSize(13);
            sub.setLineSpacing(AndroidUtilities.dp(3), 1f);
            sub.setGravity(Gravity.CENTER);
            sub.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            sub.setPadding(0, AndroidUtilities.dp(6), 0, 0);
            empty.addView(sub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            listContainer.addView(empty, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else {
            LinearLayout group = new LinearLayout(context);
            group.setOrientation(LinearLayout.VERTICAL);
            group.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            for (int i = 0; i < servers.size(); i++) {
                OxMcpManager.McpServer s = servers.get(i);
                TextSettingsCell cell = new TextSettingsCell(context);
                String value;
                if (!s.enabled) {
                    value = LocaleController.getString(R.string.OxMcpDisabled);
                } else if (s.lastError != null) {
                    value = s.lastError;
                    cell.setTextValueColor(getThemedColor(Theme.key_text_RedRegular));
                } else if (s.toolCount >= 0) {
                    value = LocaleController.formatString("OxMcpToolsFound", R.string.OxMcpToolsFound, s.toolCount);
                } else {
                    value = s.url;
                }
                cell.setTextAndValue(s.name, value, i < servers.size() - 1);
                cell.setOnClickListener(v -> {
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
                cell.setOnLongClickListener(v -> {
                    showServerMenu(s);
                    return true;
                });
                group.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
            listContainer.addView(group, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextView hint = new TextView(context);
            hint.setText(LocaleController.getString(R.string.OxMcpToggleHint));
            hint.setTextSize(12);
            hint.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            hint.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(8), AndroidUtilities.dp(21), 0);
            listContainer.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        LinearLayout addGroup = new LinearLayout(context);
        addGroup.setOrientation(LinearLayout.VERTICAL);
        addGroup.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        TextSettingsCell add = new TextSettingsCell(context);
        add.setText("＋ " + LocaleController.getString(R.string.OxMcpAdd), false);
        add.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        add.setOnClickListener(v -> showAddDialog());
        addGroup.addView(add, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0);
        listContainer.addView(addGroup, lp);
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
        int pad = AndroidUtilities.dp(24);
        wrap.setPadding(pad, AndroidUtilities.dp(8), pad, 0);
        final EditText name = new EditText(ctx);
        name.setHint(LocaleController.getString(R.string.OxMcpNameHint));
        name.setSingleLine(true);
        wrap.addView(name, LayoutHelper.createLinear(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 6, 0, 6));
        final EditText url = new EditText(ctx);
        url.setHint(LocaleController.getString(R.string.OxMcpUrlHint));
        url.setSingleLine(true);
        wrap.addView(url, LayoutHelper.createLinear(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 6, 0, 6));
        final EditText headers = new EditText(ctx);
        headers.setHint(LocaleController.getString(R.string.OxMcpHeadersHint));
        headers.setMinLines(2);
        wrap.addView(headers, LayoutHelper.createLinear(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 6, 0, 6));

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(LocaleController.getString(R.string.OxMcpAdd));
        builder.setView(wrap);
        builder.setPositiveButton(LocaleController.getString(R.string.Add), (dialog, which) -> {
            String n = name.getText().toString().trim();
            String u = url.getText().toString().trim();
            if (n.isEmpty() || !(u.startsWith("http://") || u.startsWith("https://"))) {
                toast(LocaleController.getString(R.string.OxMcpInvalid));
                return;
            }
            OxMcpManager.McpServer s = new OxMcpManager.McpServer(n, u);
            String h = headers.getText().toString().trim();
            if (!h.isEmpty()) {
                try {
                    new org.json.JSONObject(h);
                    s.headersJson = h;
                } catch (Exception ignore) {
                    toast(LocaleController.getString(R.string.OxMcpInvalidHeaders));
                    return;
                }
            }
            OxMcpManager.saveServer(s);
            rebuild();
            refreshNow(s);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    private void toast(String s) {
        if (getParentActivity() != null) {
            Toast.makeText(getParentActivity(), s, Toast.LENGTH_SHORT).show();
        }
    }
}
