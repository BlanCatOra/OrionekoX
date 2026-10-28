package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ox.OxAgent;
import org.telegram.ox.OxAgentArchive;
import org.telegram.ox.OxAgentStore;
import org.telegram.ox.OxAgentsBridge;
import org.telegram.ox.OxKelivoExporter;
import org.telegram.ox.OxMode;
import org.telegram.ox.OxRikkaImporter;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.util.List;
import java.util.UUID;

/** Agent list: Telegram-style contact rows, one section, clear add/import actions. */
@SuppressLint("ViewConstructor")
public class OxAgentsActivity extends BaseFragment {

    private static final int REQ_EXPORT = 1001;
    private static final int REQ_IMPORT = 1002;
    private static final int REQ_CARD = 1003;
    private static final int REQ_EXPORT_KELIVO = 1004;
    private static final int MENU_MCP = 1;
    private static final int MENU_SETTINGS = 2;
    private static final int MENU_LOGIN = 3;

    private LinearLayout listContainer;
    private String pendingExportId;
    private String pendingExportKelivoId;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(LocaleController.getString(R.string.OxAgents));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_MCP) {
                    presentFragment(new OxMcpActivity());
                } else if (id == MENU_SETTINGS) {
                    presentFragment(new OxSettingsActivity());
                } else if (id == MENU_LOGIN) {
                    confirmSwitchToLogin();
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(MENU_MCP, R.drawable.msg_link);
        menu.addItem(MENU_SETTINGS, R.drawable.msg_settings_old);
        if (OxMode.isAgentOnly()) {
            menu.addItem(MENU_LOGIN, R.drawable.outline_add_account);
        }

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
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        rebuild();
        if (OxMode.isAgentOnly()) {
            OxAgentsBridge.syncAgents(false);
        }
    }

    private void rebuild() {
        if (fragmentView == null) return;
        listContainer.removeAllViews();
        Context context = listContainer.getContext();

        TextView intro = new TextView(context);
        intro.setText(LocaleController.getString(R.string.OxAgentsIntro));
        intro.setTextSize(13);
        intro.setLineSpacing(AndroidUtilities.dp(3), 1f);
        intro.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        intro.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(12), AndroidUtilities.dp(21), AndroidUtilities.dp(10));
        listContainer.addView(intro, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        List<OxAgent> agents = OxAgentStore.listAgents();
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        for (int i = 0; i < agents.size(); i++) {
            OxAgent agent = agents.get(i);
            group.addView(agentRow(context, agent, i < agents.size() - 1), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        listContainer.addView(group, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        TextSettingsCell add = new TextSettingsCell(context);
        add.setText("＋ " + LocaleController.getString(R.string.OxAgentNew), true);
        add.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        add.setOnClickListener(v -> presentFragment(OxAgentProfileActivity.newDraft(OxAgentProfileActivity.TEMPLATE_API)));
        actions.addView(add, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextSettingsCell imp = new TextSettingsCell(context);
        imp.setText(LocaleController.getString(R.string.OxImportAgent), false);
        imp.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        imp.setOnClickListener(v -> importFromOxAgent());
        actions.addView(imp, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextSettingsCell card = new TextSettingsCell(context);
        card.setText(LocaleController.getString(R.string.OxImportCard), false);
        card.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        card.setOnClickListener(v -> importCharacterCard());
        actions.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0);
        listContainer.addView(actions, lp);
    }

    private View agentRow(Context context, OxAgent agent, boolean divider) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        row.setBackground(Theme.getSelectorDrawable(false));

        FrameLayout avatarWrap = new FrameLayout(context);
        GradientDrawable fallback = new GradientDrawable();
        fallback.setShape(GradientDrawable.OVAL);
        fallback.setColor(avatarColor(agent));
        avatarWrap.setBackground(fallback);
        TextView initial = new TextView(context);
        initial.setText(initial(agent));
        initial.setTextSize(20);
        initial.setTypeface(AndroidUtilities.bold());
        initial.setTextColor(0xffffffff);
        initial.setGravity(Gravity.CENTER);
        avatarWrap.addView(initial, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        if (agent.avatarPath != null && !agent.avatarPath.isEmpty() && new File(agent.avatarPath).exists()) {
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
                Bitmap bmp = BitmapFactory.decodeFile(agent.avatarPath, opts);
                if (bmp != null) {
                    ImageView iv = new ImageView(context);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setImageBitmap(bmp);
                    iv.setClipToOutline(true);
                    iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                        @Override
                        public void getOutline(View view, android.graphics.Outline outline) {
                            outline.setOval(0, 0, view.getWidth(), view.getHeight());
                        }
                    });
                    avatarWrap.addView(iv, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
        row.addView(avatarWrap, LayoutHelper.createLinear(50, 50, Gravity.CENTER_VERTICAL));

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(6), 0);
        LinearLayout nameRow = new LinearLayout(context);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(context);
        name.setText(agent.name);
        name.setTextSize(16);
        name.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameRow.addView(name, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        if (agent.builtIn) {
            TextView badge = new TextView(context);
            badge.setText(LocaleController.getString(R.string.OxAgentBadgeLocal));
            badge.setTextSize(9);
            badge.setTypeface(AndroidUtilities.bold());
            badge.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            badge.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(1), AndroidUtilities.dp(6), AndroidUtilities.dp(1));
            GradientDrawable b = new GradientDrawable();
            b.setCornerRadius(AndroidUtilities.dp(4));
            b.setColor(getThemedColor(Theme.key_windowBackgroundGray));
            badge.setBackground(b);
            nameRow.addView(badge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));
        }
        text.addView(nameRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextView status = new TextView(context);
        status.setText(agent.remark != null && !agent.remark.isEmpty() ? agent.remark : agent.statusLine());
        status.setTextSize(13);
        status.setMaxLines(1);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        status.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        text.addView(status, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        row.addView(text, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        if (agent.pinned) {
            ImageView pin = new ImageView(context);
            pin.setImageResource(R.drawable.chats_pin);
            pin.setColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            row.addView(pin, LayoutHelper.createLinear(18, 18, Gravity.CENTER_VERTICAL));
        }

        row.setOnClickListener(v -> {
            OxAgent a = agent;
            if (a.tgId == 0) {
                OxAgentsBridge.syncAgents(false);
                a = OxAgentStore.loadAgent(a.id);
            }
            final OxAgent open = a;
            if (open != null && open.tgId != 0) {
                OxAgentsBridge.ensureNativeDialog(open, () -> presentFragment(ChatActivity.of(open.tgId)));
            } else {
                presentFragment(new OxAgentProfileActivity(agent.id));
            }
        });
        row.setOnLongClickListener(v -> {
            showAgentMenu(agent);
            return true;
        });
        if (divider) {
            LinearLayout outer = new LinearLayout(context);
            outer.setOrientation(LinearLayout.VERTICAL);
            outer.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            View line = new View(context);
            line.setBackgroundColor(getThemedColor(Theme.key_divider));
            LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1);
            lp.leftMargin = AndroidUtilities.dp(74);
            outer.addView(line, lp);
            return outer;
        }
        return row;
    }

    private int avatarColor(OxAgent agent) {
        int[] colors = {0xff2AABEE, 0xff34C759, 0xff8E44AD, 0xffF39C12, 0xffE91E63, 0xff00BCD4};
        int h = agent.name == null ? 0 : agent.name.hashCode();
        return colors[(h & 0x7fffffff) % colors.length];
    }

    private String initial(OxAgent agent) {
        String n = agent.name == null ? "?" : agent.name.trim();
        return n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase();
    }

    private void duplicateAgent(OxAgent agent) {
        try {
            JSONObject o = agent.toJson();
            o.put("id", "dup" + UUID.randomUUID().toString().substring(0, 6));
            o.put("name", agent.name + " · " + LocaleController.getString(R.string.OxAgentCopySuffix));
            o.put("built_in", false);
            o.put("tg_id", 0);
            o.put("pinned", false);
            o.put("avatar_path", "");
            OxAgent copy = OxAgent.fromJson(o);
            OxAgentStore.saveAgent(copy);
            String memory = OxAgentStore.loadMemory(agent.id, 200000);
            if (!memory.isEmpty()) OxAgentStore.saveMemory(copy.id, memory);
            OxAgentsBridge.syncAgents(false);
            toast(LocaleController.formatString("OxAgentDuplicated", R.string.OxAgentDuplicated, copy.name));
            rebuild();
        } catch (Throwable t) {
            toast("⚠ " + t.getMessage());
        }
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
        items.add(LocaleController.getString(R.string.OxDuplicateAgent));
        actions.add(() -> duplicateAgent(agent));
        items.add(LocaleController.getString(R.string.OxEditAgent));
        actions.add(() -> presentFragment(new OxAgentProfileActivity(agent.id)));
        items.add(LocaleController.getString(R.string.OxMemory));
        actions.add(() -> presentFragment(new OxAgentMemoryActivity(agent.id)));
        items.add(LocaleController.getString(R.string.OxChatAgentEntry));
        actions.add(() -> {
            presentFragment(new OxChatAppearanceActivity(agent.tgId));
        });
        items.add(LocaleController.getString(R.string.OxExportAgent));
        actions.add(() -> exportOxAgent(agent));
        if (!agent.builtIn) {
            items.add(LocaleController.getString(R.string.OxKelivoExportAgent));
            actions.add(() -> exportAgentToChatbox(agent));
            items.add(LocaleController.getString(R.string.Delete));
            actions.add(() -> showDeleteDialog(agent));
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(agent.name);
        builder.setItems(items.toArray(new CharSequence[0]), (d, which) -> {
            if (which >= 0 && which < actions.size()) actions.get(which).run();
        });
        builder.show();
    }

    private void confirmSwitchToLogin() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(LocaleController.getString(R.string.OxObSwitchTitle));
        b.setMessage(LocaleController.getString(R.string.OxObSwitchWarnLogin));
        b.setPositiveButton(LocaleController.getString(R.string.OxObSwitchLogin), (d, w) -> {
            OxMode.setPendingMode(OxMode.PENDING_LOGIN);
            OxAgentsBridge.exitAgentOnly();
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        b.show();
    }

    private void importCharacterCard() {
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_CARD);
        } catch (Exception e) {
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

    private void exportAgentToChatbox(OxAgent agent) {
        if (getParentActivity() == null) return;
        new AlertDialog.Builder(getParentActivity())
                .setTitle(LocaleController.getString(R.string.OxKelivoExport))
                .setMessage(LocaleController.getString(R.string.OxKelivoExportAgentConfirm))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setPositiveButton(LocaleController.getString(R.string.OxKelivoExportAction),
                        (d, w) -> pickAgentChatboxTarget(agent))
                .show();
    }

    private void pickAgentChatboxTarget(OxAgent agent) {
        if (getParentActivity() == null) return;
        pendingExportKelivoId = agent.id;
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            i.setType("application/zip");
            i.putExtra(android.content.Intent.EXTRA_TITLE,
                    sanitizeFileName(agent.name) + "-chatbox-backup.zip");
            startActivityForResult(i, REQ_EXPORT_KELIVO);
        } catch (Exception e) {
            pendingExportKelivoId = null;
            toast("⚠ " + e);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, android.content.Intent data) {
        if (resultCode != android.app.Activity.RESULT_OK || data == null) {
            pendingExportId = null;
            pendingExportKelivoId = null;
            return;
        }
        android.content.Context ctx = getParentActivity();
        if (ctx == null) {
            pendingExportId = null;
            pendingExportKelivoId = null;
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
                    String memory = OxAgentStore.loadMemory(id, 200000);
                    java.io.OutputStream os = ctx.getContentResolver().openOutputStream(uri);
                    int files = OxAgentArchive.write(os, a, memory);
                    os.close();
                    msg = files > 0
                            ? LocaleController.formatString("OxAgentExportedFiles",
                                    R.string.OxAgentExportedFiles, a.name, files)
                            : LocaleController.formatString("OxAgentExported", R.string.OxAgentExported, a.name);
                } catch (Exception e) {
                    msg = "⚠ " + e;
                }
                final String m = msg;
                AndroidUtilities.runOnUIThread(() -> toast(m));
            }, "ox-export").start();
        } else if (requestCode == REQ_EXPORT_KELIVO && pendingExportKelivoId != null) {
            final String id = pendingExportKelivoId;
            pendingExportKelivoId = null;
            final android.net.Uri uri = data.getData();
            new Thread(() -> {
                String msg;
                try {
                    OxAgent a = OxAgentStore.loadAgent(id);
                    if (a == null || uri == null) throw new IllegalStateException("agent or uri gone");
                    OxKelivoExporter.Result r = OxKelivoExporter.exportTo(
                            ctx, uri, java.util.Collections.singletonList(id));
                    msg = LocaleController.formatString("OxKelivoExportAgentDone",
                            R.string.OxKelivoExportAgentDone, a.name, r.memories,
                            r.messages, r.resources);
                } catch (Throwable t) {
                    msg = LocaleController.formatString("OxKelivoExportFailed",
                            R.string.OxKelivoExportFailed,
                            t.getMessage() == null ? "" : t.getMessage());
                }
                final String m = msg;
                AndroidUtilities.runOnUIThread(() -> toast(m));
            }, "ox-kelivo-agent-export").start();
        } else if (requestCode == REQ_CARD) {
            final android.net.Uri uri = data.getData();
            new Thread(() -> {
                String msg;
                try {
                    if (uri == null) throw new IllegalStateException("no file");
                    OxRikkaImporter.Preview preview = OxRikkaImporter.preview(ctx, uri);
                    if (!preview.characterCard) throw new IllegalArgumentException("not a card");
                    OxRikkaImporter.Result result = OxRikkaImporter.importFrom(ctx, uri);
                    if (result.importedAgentIds.isEmpty()) {
                        throw new IllegalStateException("no agent");
                    }
                    OxAgent imported = OxAgentStore.loadAgent(result.importedAgentIds.get(0));
                    String name = imported == null ? "" : imported.name;
                    msg = LocaleController.formatString(
                            "OxAgentCardImported", R.string.OxAgentCardImported, name);
                } catch (Throwable t) {
                    String detail = t.getMessage();
                    if ("not a card".equals(detail)) {
                        detail = LocaleController.getString(R.string.OxRikkaInvalidCard);
                    }
                    msg = LocaleController.formatString("OxAgentCardFailed",
                            R.string.OxAgentCardFailed, detail == null ? "" : detail);
                }
                final String m = msg;
                AndroidUtilities.runOnUIThread(() -> {
                    toast(m);
                    OxAgentsBridge.syncAgents(true);
                    rebuild();
                });
            }, "ox-card-import").start();
        } else if (requestCode == REQ_IMPORT) {
            final android.net.Uri uri = data.getData();
            new Thread(() -> {
                String msg;
                try {
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    java.io.InputStream is = ctx.getContentResolver().openInputStream(uri);
                    byte[] buf = new byte[8192];
                    int n;
                    long total = 0;
                    while ((n = is.read(buf)) > 0) {
                        total += n;
                        if (total > 64L * 1024 * 1024) throw new IllegalStateException("archive too large");
                        bos.write(buf, 0, n);
                    }
                    is.close();
                    OxAgentArchive.ReadResult res = OxAgentArchive.read(bos.toByteArray());
                    JSONObject o = res.agent;
                    String name = o.optString("name", "").trim();
                    if (name.isEmpty()) throw new org.json.JSONException("not a .oxagent file");
                    OxAgent a = OxAgent.fromJson(o);
                    a.name = name;
                    a.builtIn = false;
                    // Imported copies must get their own synthetic user/dialog; keeping
                    // the archived tg_id collides with the original agent.
                    a.tgId = 0;
                    if (a.id == null || a.id.isEmpty() || OxAgentStore.loadAgent(a.id) != null) {
                        a.id = "imp" + UUID.randomUUID().toString().substring(0, 6);
                    }
                    boolean nameTaken = false;
                    for (OxAgent existing : OxAgentStore.listAgents()) {
                        if (!existing.id.equals(a.id) && existing.name != null && existing.name.equals(a.name)) {
                            nameTaken = true;
                            break;
                        }
                    }
                    if (nameTaken) {
                        a.name = a.name + " · " + LocaleController.getString(R.string.OxAgentCopySuffix);
                    }
                    if (a.avatarPath != null && !a.avatarPath.isEmpty() && !new File(a.avatarPath).isFile()) {
                        a.avatarPath = "";
                    }
                    OxAgentStore.saveAgent(a);
                    String memory = o.optString("memory", "");
                    if (!memory.isEmpty()) OxAgentStore.saveMemory(a.id, memory);
                    OxAgentArchive.restoreWorkspace(a.id, res);
                    File avatar = OxAgentArchive.restoreAvatar(a.id, res);
                    if (avatar != null) {
                        a.avatarPath = avatar.getAbsolutePath();
                        OxAgentStore.saveAgent(a);
                    }
                    msg = res.workspace.isEmpty()
                            ? LocaleController.formatString("OxAgentImported", R.string.OxAgentImported, a.name)
                            : LocaleController.formatString("OxAgentImportedFiles",
                                    R.string.OxAgentImportedFiles, a.name, res.workspace.size());
                    if ((a.apiKey == null || a.apiKey.trim().isEmpty())
                            && a.baseUrl != null && !a.baseUrl.trim().isEmpty()) {
                        msg = msg + "\n" + LocaleController.getString(R.string.OxAgentImportNoKey);
                    }
                } catch (Exception e) {
                    msg = LocaleController.getString(R.string.OxAgentImportFailed);
                }
                final String m = msg;
                AndroidUtilities.runOnUIThread(() -> {
                    toast(m);
                    OxAgentsBridge.syncAgents(false);
                    rebuild();
                });
            }, "ox-import").start();
        }
    }

    private static String sanitizeFileName(String s) {
        if (s == null) return "agent";
        String cleaned = s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return cleaned.isEmpty() ? "agent" : cleaned;
    }

    private void toast(String s) {
        if (getParentActivity() != null) {
            Toast.makeText(getParentActivity(), s, Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeleteDialog(OxAgent agent) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.OxAgentDeleteTitle));
        builder.setMessage(LocaleController.getString(R.string.OxAgentDeleteText));
        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
            OxAgentsBridge.onAgentDeleted(agent.tgId);
            OxAgentStore.deleteAgent(agent.id);
            rebuild();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }
}
