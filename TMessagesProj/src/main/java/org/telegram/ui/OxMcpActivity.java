package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ox.OxMcpManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.List;

/** MCP servers, rendered with Telegram's native settings list. */
@SuppressLint("ViewConstructor")
public class OxMcpActivity extends BaseFragment {

    private static final int ID_MARKET = 1;
    private static final int ID_ADD = 2;
    private static final int ID_IMPORT = 3;
    private static final int SERVER_BASE = 100;

    private UniversalRecyclerView listView;

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
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        listView.adapter.setApplyBackground(false);
        listView.setSections();
        listView.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        listView.setClipToPadding(false);
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        fragmentView = root;
        return root;
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

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.OxMcp)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_MARKET, 0xFF9C6ADE, 0xFF7B4FD1,
                R.drawable.wallet_intro_store, LocaleController.getString(R.string.OxMcpMarket)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.OxMcpIntro)));

        List<OxMcpManager.McpServer> servers = OxMcpManager.getServers();
        if (servers.isEmpty()) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.OxMcpEmptyTitle) + "\n" + LocaleController.getString(R.string.OxMcpEmpty)));
        } else {
            items.add(UItem.asHeader(LocaleController.getString(R.string.OxMcp)));
            for (int i = 0; i < servers.size(); i++) {
                OxMcpManager.McpServer s = servers.get(i);
                String value;
                if (!s.enabled) {
                    value = LocaleController.getString(R.string.OxMcpDisabled);
                } else if (s.lastError != null) {
                    value = s.lastError;
                } else if (s.toolCount >= 0) {
                    value = LocaleController.formatString("OxMcpToolsFound", R.string.OxMcpToolsFound, s.toolCount);
                } else {
                    value = "";
                }
                items.add(SettingsActivity.SettingCell.Factory.of(SERVER_BASE + i, 0xFF1CA5ED, 0xFF1488E1,
                        R.drawable.msg_link, s.name, s.url, value));
            }
            items.add(UItem.asShadow(LocaleController.getString(R.string.OxMcpToggleHint)));
        }

        items.add(SettingsActivity.SettingCell.Factory.of(ID_ADD, 0, 0,
                0, "＋ " + LocaleController.getString(R.string.OxMcpAdd)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_IMPORT, 0xFF34C759, 0xFF2AA84A,
                R.drawable.msg_download, LocaleController.getString(R.string.OxMcpImport),
                LocaleController.getString(R.string.OxMcpImportHint)));
        items.add(UItem.asShadow(null));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_MARKET) {
            presentFragment(new OxMcpMarketActivity());
        } else if (item.id == ID_ADD) {
            showAddDialog();
        } else if (item.id == ID_IMPORT) {
            showImportDialog();
        } else if (item.id >= SERVER_BASE) {
            int index = item.id - SERVER_BASE;
            List<OxMcpManager.McpServer> servers = OxMcpManager.getServers();
            if (index >= 0 && index < servers.size()) {
                OxMcpManager.McpServer s = servers.get(index);
                s.enabled = !s.enabled;
                OxMcpManager.saveServer(s);
                if (s.enabled) refreshNow(s);
                rebuild();
            }
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= SERVER_BASE) {
            int index = item.id - SERVER_BASE;
            List<OxMcpManager.McpServer> servers = OxMcpManager.getServers();
            if (index >= 0 && index < servers.size()) {
                showServerMenu(servers.get(index));
                return true;
            }
        }
        return false;
    }

    private void showServerMenu(OxMcpManager.McpServer s) {
        if (getParentActivity() == null) return;
        ArrayList<CharSequence> items = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
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

    private void showImportDialog() {
        if (getParentActivity() == null) return;
        Context ctx = getParentActivity();
        EditText input = new EditText(ctx);
        input.setHint(LocaleController.getString(R.string.OxMcpImportHint));
        input.setMinLines(6);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        FrameLayout wrap = new FrameLayout(ctx);
        wrap.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        wrap.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        new AlertDialog.Builder(ctx)
                .setTitle(LocaleController.getString(R.string.OxMcpImport))
                .setView(wrap)
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setPositiveButton(LocaleController.getString(R.string.OxMcpImport), (d, w) -> importMcpJson(input.getText().toString()))
                .show();
    }

    private void importMcpJson(String raw) {
        int added = 0, skipped = 0, stdio = 0;
        try {
            org.json.JSONObject root = new org.json.JSONObject(raw);
            org.json.JSONObject servers = root.optJSONObject("mcpServers");
            if (servers == null) servers = root;
            java.util.Iterator<String> keys = servers.keys();
            while (keys.hasNext()) {
                String name = keys.next();
                org.json.JSONObject c = servers.optJSONObject(name);
                if (c == null) { skipped++; continue; }
                String type = c.optString("type", "").toLowerCase();
                String url = c.optString("url", c.optString("baseUrl", "")).trim();
                if (type.equals("stdio") || (type.isEmpty() && c.has("command") && url.isEmpty())) {
                    stdio++;
                    continue;
                }
                if (type.equals("sse") || !(url.startsWith("http://") || url.startsWith("https://"))) {
                    skipped++;
                    continue;
                }
                boolean exists = false;
                for (OxMcpManager.McpServer s : OxMcpManager.getServers()) {
                    if (s.url != null && s.url.equals(url)) { exists = true; break; }
                }
                if (exists) { skipped++; continue; }
                OxMcpManager.McpServer s = new OxMcpManager.McpServer(name, url);
                org.json.JSONObject headers = c.optJSONObject("headers");
                if (headers != null) s.headersJson = headers.toString();
                s.enabled = !c.optBoolean("disabled", false) && c.optBoolean("isActive", true);
                OxMcpManager.saveServer(s);
                added++;
            }
            String msg = LocaleController.formatString("OxMcpImportDone", R.string.OxMcpImportDone, added, skipped);
            if (stdio > 0) {
                msg += "\n" + LocaleController.formatString("OxMcpImportStdio", R.string.OxMcpImportStdio, stdio);
            }
            toast(msg);
            rebuild();
        } catch (Throwable t) {
            toast("⚠ " + t.getMessage());
        }
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

    private void rebuild() {
        if (listView != null) listView.adapter.update(true);
    }

    private void toast(String s) {
        if (getParentActivity() != null) {
            Toast.makeText(getParentActivity(), s, Toast.LENGTH_SHORT).show();
        }
    }
}
