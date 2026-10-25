package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ox.OxMcpManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Operit's MCP catalogue plus a small set of public remote (Streamable HTTP)
 * servers. Operit's own catalogue is mostly local stdio plugins that need a
 * Python/Node runtime; those entries are browsable and their config can be
 * copied, while remote entries install directly into OxMcpManager.
 */
@SuppressLint("ViewConstructor")
public class OxMcpMarketActivity extends BaseFragment {

    private static final String CATALOG_URL =
            "https://static.operit.app/market/v2/lists/type/mcp/updated/page-1.json";

    private static class Entry {
        String title;
        String description;
        String repo;
        String config;
        boolean remote;
        String remoteUrl;
        String remoteName;
        String headersJson = "";
    }

    private LinearLayout listContainer;
    private volatile boolean loading;
    private String loadError;
    private final List<Entry> catalog = new ArrayList<>();

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(LocaleController.getString(R.string.OxMcpMarket));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, 0, 0, AndroidUtilities.dp(24));
        scroll.addView(listContainer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = root;
        rebuild();
        loadCatalog();
        return root;
    }

    private void loadCatalog() {
        if (loading) return;
        loading = true;
        loadError = null;
        rebuild();
        new Thread(() -> {
            String error = null;
            ArrayList<Entry> parsed = new ArrayList<>();
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(CATALOG_URL).openConnection();
                conn.setConnectTimeout(15_000);
                conn.setReadTimeout(25_000);
                conn.setRequestProperty("User-Agent", "OrionekoX");
                int code = conn.getResponseCode();
                if (code != 200) throw new IllegalStateException("HTTP " + code);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                conn.disconnect();
                JSONObject root = new JSONObject(sb.toString());
                JSONArray items = root.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject it = items.optJSONObject(i);
                        if (it == null) continue;
                        Entry e = new Entry();
                        e.title = it.optString("title", "MCP");
                        e.description = it.optString("description", "");
                        JSONObject source = it.optJSONObject("source");
                        e.repo = source == null ? "" : source.optString("url", "");
                        JSONArray versions = it.optJSONArray("versions");
                        JSONObject ver = versions != null && versions.length() > 0 ? versions.optJSONObject(0) : null;
                        e.config = ver == null ? "" : ver.optString("installConfig", "");
                        parseRemote(e);
                        parsed.add(e);
                    }
                }
            } catch (Throwable t) {
                error = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            }
            final String finalError = error;
            AndroidUtilities.runOnUIThread(() -> {
                loading = false;
                loadError = finalError;
                catalog.clear();
                catalog.addAll(parsed);
                rebuild();
            });
        }, "ox-mcp-market").start();
    }

    private void parseRemote(Entry e) {
        try {
            if (e.config == null || e.config.trim().isEmpty() || !e.config.trim().startsWith("{")) return;
            JSONObject root = new JSONObject(e.config);
            JSONObject servers = root.optJSONObject("mcpServers");
            if (servers == null) return;
            java.util.Iterator<String> keys = servers.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject s = servers.optJSONObject(key);
                if (s == null) continue;
                String url = s.optString("url", s.optString("serverUrl", s.optString("endpoint", "")));
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    e.remote = true;
                    e.remoteUrl = url;
                    e.remoteName = s.optString("name", key);
                    JSONObject headers = s.optJSONObject("headers");
                    if (headers != null) e.headersJson = headers.toString();
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void rebuild() {
        if (listContainer == null) return;
        listContainer.removeAllViews();
        Context context = listContainer.getContext();

        TextView intro = new TextView(context);
        intro.setText(LocaleController.getString(R.string.OxMcpMarketInfo));
        intro.setTextSize(13);
        intro.setLineSpacing(AndroidUtilities.dp(3), 1f);
        intro.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        intro.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(12), AndroidUtilities.dp(21), AndroidUtilities.dp(10));
        listContainer.addView(intro, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        listContainer.addView(header(context, LocaleController.getString(R.string.OxMcpMarketRemote)));
        LinearLayout remote = group(context);
        addRemoteRow(context, remote, "DeepWiki", "https://mcp.deepwiki.com/mcp",
                "DeepWiki · repository documentation search", false);
        addRemoteRow(context, remote, "Context7", "https://mcp.context7.com/mcp",
                "Context7 · up-to-date library documentation", false);
        listContainer.addView(remote, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        listContainer.addView(header(context, LocaleController.getString(R.string.OxMcpMarketOperit)));
        if (loading) {
            listContainer.addView(note(context, LocaleController.getString(R.string.OxMcpMarketLoading)));
            return;
        }
        if (loadError != null) {
            listContainer.addView(note(context, LocaleController.formatString("OxMcpMarketError", R.string.OxMcpMarketError, loadError)));
            TextView retry = new TextView(context);
            retry.setText(LocaleController.getString(R.string.OxMcpMarketRetry));
            retry.setTextSize(15);
            retry.setTypeface(AndroidUtilities.bold());
            retry.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
            retry.setGravity(Gravity.CENTER);
            retry.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));
            retry.setOnClickListener(v -> loadCatalog());
            listContainer.addView(retry, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            return;
        }
        LinearLayout list = group(context);
        for (int i = 0; i < catalog.size(); i++) {
            Entry e = catalog.get(i);
            list.addView(entryRow(context, e, i < catalog.size() - 1), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        listContainer.addView(list, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private void addRemoteRow(Context context, LinearLayout group, String name, String url, String desc, boolean installed) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(10), AndroidUtilities.dp(21), AndroidUtilities.dp(10));
        row.setBackground(Theme.getSelectorDrawable(false));
        TextView title = new TextView(context);
        title.setText(name);
        title.setTextSize(16);
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        row.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextView sub = new TextView(context);
        sub.setText(desc + "\n" + url);
        sub.setTextSize(12);
        sub.setLineSpacing(AndroidUtilities.dp(2), 1f);
        sub.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        row.addView(sub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        row.setOnClickListener(v -> {
            OxMcpManager.McpServer s = new OxMcpManager.McpServer(name, url);
            OxMcpManager.saveServer(s);
            toast(LocaleController.formatString("OxMcpMarketInstalled", R.string.OxMcpMarketInstalled, name));
            new Thread(() -> {
                OxMcpManager.refreshServer(s);
                AndroidUtilities.runOnUIThread(this::rebuild);
            }, "ox-mcp-market-install").start();
        });
        group.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private View entryRow(Context context, Entry e, boolean divider) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(10), AndroidUtilities.dp(21), AndroidUtilities.dp(10));
        row.setBackground(Theme.getSelectorDrawable(false));
        TextView title = new TextView(context);
        title.setText(e.title);
        title.setTextSize(16);
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        row.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextView sub = new TextView(context);
        sub.setText(e.description);
        sub.setTextSize(12);
        sub.setMaxLines(2);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sub.setLineSpacing(AndroidUtilities.dp(2), 1f);
        sub.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        sub.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        row.addView(sub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextView tag = new TextView(context);
        tag.setText(e.remote ? LocaleController.getString(R.string.OxMcpMarketInstall) : LocaleController.getString(R.string.OxMcpMarketLocal));
        tag.setTextSize(11);
        tag.setTypeface(AndroidUtilities.bold());
        tag.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        tag.setPadding(0, AndroidUtilities.dp(4), 0, 0);
        row.addView(tag, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        row.setOnClickListener(v -> showEntry(e));
        if (divider) {
            View line = new View(context);
            line.setBackgroundColor(getThemedColor(Theme.key_divider));
            LinearLayout wrap = new LinearLayout(context);
            wrap.setOrientation(LinearLayout.VERTICAL);
            wrap.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            wrap.addView(line, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 0, 0, 0, 0));
            return wrap;
        }
        return row;
    }

    private void showEntry(Entry e) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(e.title);
        StringBuilder msg = new StringBuilder();
        if (e.description != null && !e.description.isEmpty()) msg.append(e.description).append("\n\n");
        if (e.repo != null && !e.repo.isEmpty()) msg.append(e.repo).append("\n\n");
        msg.append(e.remote ? LocaleController.getString(R.string.OxMcpMarketRemoteHint)
                : LocaleController.getString(R.string.OxMcpMarketLocalUnsupported));
        b.setMessage(msg.toString());
        if (e.remote) {
            b.setPositiveButton(LocaleController.getString(R.string.OxMcpMarketInstall), (d, w) -> install(e));
        } else if (e.config != null && !e.config.isEmpty()) {
            b.setPositiveButton(LocaleController.getString(R.string.OxMcpMarketCopy), (d, w) -> {
                AndroidUtilities.addToClipboard(e.config);
                toast(LocaleController.getString(R.string.OxMcpMarketCopied));
            });
        }
        if (e.repo != null && e.repo.startsWith("http")) {
            b.setNeutralButton(LocaleController.getString(R.string.OxMcpMarketOpenRepo), (d, w) -> {
                try {
                    getParentActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(e.repo)));
                } catch (Throwable ignored) {
                }
            });
        }
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        b.show();
    }

    private void install(Entry e) {
        OxMcpManager.McpServer s = new OxMcpManager.McpServer(
                e.remoteName == null || e.remoteName.isEmpty() ? e.title : e.remoteName, e.remoteUrl);
        s.headersJson = e.headersJson == null ? "" : e.headersJson;
        OxMcpManager.saveServer(s);
        toast(LocaleController.formatString("OxMcpMarketInstalled", R.string.OxMcpMarketInstalled, s.name));
        new Thread(() -> {
            OxMcpManager.refreshServer(s);
            AndroidUtilities.runOnUIThread(this::rebuild);
        }, "ox-mcp-market-install").start();
    }

    private TextView header(Context context, String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        tv.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(14), AndroidUtilities.dp(21), AndroidUtilities.dp(5));
        return tv;
    }

    private LinearLayout group(Context context) {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        return l;
    }

    private TextView note(Context context, String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setLineSpacing(AndroidUtilities.dp(2), 1f);
        tv.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        tv.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(10), AndroidUtilities.dp(21), AndroidUtilities.dp(10));
        return tv;
    }

    private void toast(String s) {
        if (getParentActivity() != null) {
            Toast.makeText(getParentActivity(), s, Toast.LENGTH_SHORT).show();
        }
    }
}
