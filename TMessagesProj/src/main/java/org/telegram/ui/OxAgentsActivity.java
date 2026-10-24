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

import org.json.JSONObject;
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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

// OrionekoX M2 slice: agent list ("以 bot 形态" 的管理入口). Chat itself lives in OxAgentChatActivity;
// the pseudo-dialog chat-list integration is the next slice.
@SuppressLint("ViewConstructor")
public class OxAgentsActivity extends BaseFragment {

    private static final int REQ_EXPORT = 1001;
    private static final int REQ_IMPORT = 1002;
    private static final int MENU_MCP = 1;
    private static final int MENU_LOGIN = 2;
    private static final int MENU_SETTINGS = 3;

    private LinearLayout listContainer;
    private String pendingExportId;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(LocaleController.getString(R.string.OxAgents));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == MENU_MCP) presentFragment(new OxMcpActivity());
                else if (id == MENU_SETTINGS) presentFragment(new OxSettingsActivity());
                else if (id == MENU_LOGIN) {
                    if (getParentActivity() == null) return;
                    AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
                    b.setTitle(LocaleController.getString(R.string.OxLoginTelegram));
                    b.setMessage(LocaleController.getString(R.string.OxLoginTelegramText));
                    b.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) ->
                            org.telegram.ox.OxAgentsBridge.exitAgentOnly());
                    b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                    b.show();
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(MENU_MCP, R.drawable.filled_bot_approve_24);
        menu.addItem(MENU_SETTINGS, R.drawable.msg_settings_old);
        if (org.telegram.ox.OxMode.isAgentOnly()) {
            menu.addItem(MENU_LOGIN, R.drawable.outline_add_account);
        }

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
        // local-account mode: materialize agents as contacts/dialogs
        if (org.telegram.ox.OxMode.isAgentOnly()) {
            org.telegram.ox.OxAgentsBridge.syncAgents(false);
        }
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

        TextView imp = new TextView(context);
        imp.setText("📥 " + LocaleController.getString(R.string.OxImportAgent));
        imp.setTextSize(16);
        imp.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        imp.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(4), AndroidUtilities.dp(16), AndroidUtilities.dp(18));
        imp.setOnClickListener(v -> importFromOxAgent());
        listContainer.addView(imp);
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
        name.setText(agent.pinned ? "📌 " + agent.name : agent.name);
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
        row.setOnClickListener(v -> {
            if (agent.tgId != 0 && org.telegram.ox.OxMode.isAgentOnly()) {
                presentFragment(org.telegram.ui.ChatActivity.of(agent.tgId));
            } else {
                presentFragment(new OxAgentChatActivity(agent.id));
            }
        });
        row.setOnLongClickListener(v -> {
            showAgentMenu(agent);
            return true;
        });
        return row;
    }

    private void showAgentMenu(OxAgent agent) {
        if (getParentActivity() == null) return;
        java.util.ArrayList<CharSequence> items = new java.util.ArrayList<>();
        java.util.ArrayList<Runnable> actions = new java.util.ArrayList<>();
        items.add(LocaleController.getString(agent.pinned ? R.string.OxUnpinAgent : R.string.OxPinAgent));
        actions.add(() -> {
            agent.pinned = !agent.pinned;
            OxAgentStore.saveAgent(agent);
            rebuild();
        });
        items.add(LocaleController.getString(R.string.OxEditAgent));
        actions.add(() -> presentFragment(new OxAgentProfileActivity(agent.id)));
        items.add(LocaleController.getString(R.string.OxExportAgent));
        actions.add(() -> exportOxAgent(agent));
        if (!agent.builtIn) {
            items.add(LocaleController.getString(R.string.Delete));
            actions.add(() -> showDeleteDialog(agent));
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(agent.emoji + " " + agent.name);
        builder.setItems(items.toArray(new CharSequence[0]), (d, which) -> {
            if (which >= 0 && which < actions.size()) actions.get(which).run();
        });
        builder.show();
    }

    // ---------------- .oxagent import/export (API key is never exported) ----------------

    private void exportOxAgent(OxAgent agent) {
        if (getParentActivity() == null) return;
        pendingExportId = agent.id;
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            i.setType("application/octet-stream");
            i.putExtra(android.content.Intent.EXTRA_TITLE, sanitizeFileName(agent.name) + ".oxagent");
            startActivityForResult(i, REQ_EXPORT);
        } catch (Exception e) {
            pendingExportId = null;
            toast("⚠ " + e);
        }
    }

    private void importFromOxAgent() {
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_IMPORT);
        } catch (Exception e) {
            toast("⚠ " + e);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, android.content.Intent data) {
        if (resultCode != android.app.Activity.RESULT_OK || data == null) {
            pendingExportId = null;
            return;
        }
        android.content.Context ctx = getParentActivity();
        if (ctx == null) {
            pendingExportId = null;
            return;
        }
        if (requestCode == REQ_EXPORT && pendingExportId != null) {
            final String id = pendingExportId;
            pendingExportId = null;
            final android.net.Uri uri = data.getData();
            new Thread(() -> {
                String msg;
                try {
                    OxAgent a = OxAgentStore.loadAgent(id);
                    if (a == null || uri == null) throw new IllegalStateException("agent or uri gone");
                    JSONObject o = a.toJson();
                    o.put("format", "oxagent");
                    o.put("api_key", "");
                    o.put("built_in", false);
                    java.io.OutputStream os = ctx.getContentResolver().openOutputStream(uri);
                    os.write(o.toString().getBytes(StandardCharsets.UTF_8));
                    os.close();
                    msg = LocaleController.formatString("OxAgentExported", R.string.OxAgentExported, a.name);
                } catch (Exception e) {
                    msg = "⚠ " + e;
                }
                final String m = msg;
                AndroidUtilities.runOnUIThread(() -> toast(m));
            }, "ox-export").start();
        } else if (requestCode == REQ_IMPORT) {
            final android.net.Uri uri = data.getData();
            new Thread(() -> {
                String msg;
                try {
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    java.io.InputStream is = ctx.getContentResolver().openInputStream(uri);
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                    is.close();
                    JSONObject o = new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8));
                    String name = o.optString("name", "").trim();
                    if (name.isEmpty() || uri == null) throw new org.json.JSONException("not a .oxagent file");
                    OxAgent a = OxAgent.fromJson(o);
                    a.name = name;
                    a.builtIn = false;
                    if (a.id == null || a.id.isEmpty() || OxAgentStore.loadAgent(a.id) != null) {
                        a.id = "imp" + UUID.randomUUID().toString().substring(0, 6);
                    }
                    OxAgentStore.saveAgent(a);
                    msg = LocaleController.formatString("OxAgentImported", R.string.OxAgentImported, a.name);
                } catch (Exception e) {
                    msg = LocaleController.getString(R.string.OxAgentImportFailed);
                }
                final String m = msg;
                AndroidUtilities.runOnUIThread(() -> {
                    toast(m);
                    rebuild();
                });
            }, "ox-import").start();
        }
    }

    private static String sanitizeFileName(String s) {
        return s == null ? "agent" : s.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
    }

    private void toast(String s) {
        if (getParentActivity() != null) {
            android.widget.Toast.makeText(getParentActivity(), s, android.widget.Toast.LENGTH_SHORT).show();
        }
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
            org.telegram.ox.OxAgentsBridge.onAgentDeleted(agent.tgId);
            OxAgentStore.deleteAgent(agent.id);
            rebuild();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }
}
