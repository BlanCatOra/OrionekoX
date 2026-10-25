package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ox.OxAgent;
import org.telegram.ox.OxAgentStore;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Read-only browser for an agent's private workspace, with text preview and delete. */
@SuppressLint("ViewConstructor")
public class OxWorkspaceActivity extends BaseFragment {

    private static final int ID_AGENT = 1;
    private static final int ID_NEW = 2;
    private static final int FILE_BASE = 1000;

    private UniversalRecyclerView listView;
    private OxAgent selectedAgent;
    private final ArrayList<File> files = new ArrayList<>();

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(LocaleController.getString(R.string.OxWorkspace));
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
        List<OxAgent> agents = OxAgentStore.listAgents();
        if (!agents.isEmpty()) selectedAgent = agents.get(0);
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        rebuild();
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.OxWorkspace)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_AGENT, 0xFF7C5CFF, 0xFF5B3FE0,
                R.drawable.filled_bot_approve_24, LocaleController.getString(R.string.OxToolsAgent),
                null, selectedAgent == null ? LocaleController.getString(R.string.OxToolsNoAgent) : selectedAgent.name));
        items.add(UItem.asShadow(LocaleController.getString(R.string.OxWorkspaceInfo)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_NEW, 0, 0, 0,
                "＋ " + LocaleController.getString(R.string.OxWorkspaceNew)));

        files.clear();
        if (selectedAgent != null) {
            scan(OxAgentStore.workspaceDir(selectedAgent.id), files, 0);
            files.sort(Comparator.comparingLong(File::lastModified).reversed());
        }
        if (files.isEmpty()) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.OxWorkspaceEmpty)));
            return;
        }
        items.add(UItem.asHeader(LocaleController.getString(R.string.OxWorkspaceFiles)));
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        for (int i = 0; i < files.size(); i++) {
            File f = files.get(i);
            String rel = relative(f);
            String sub = humanSize(f.length()) + " · " + fmt.format(new Date(f.lastModified()));
            items.add(SettingsActivity.SettingCell.Factory.of(FILE_BASE + i, 0xFF1CA5ED, 0xFF1488E1,
                    R.drawable.ox_file_24, rel, sub));
        }
        items.add(UItem.asShadow(null));
    }

    private void scan(File dir, List<File> out, int depth) {
        if (dir == null || !dir.isDirectory() || depth > 8 || out.size() > 300) return;
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) scan(f, out, depth + 1);
            else out.add(f);
        }
    }

    private String relative(File f) {
        try {
            String base = OxAgentStore.workspaceDir(selectedAgent.id).getCanonicalPath();
            String path = f.getCanonicalPath();
            return path.startsWith(base + File.separator) ? path.substring(base.length() + 1) : f.getName();
        } catch (Throwable t) {
            return f.getName();
        }
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024f);
        return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_AGENT) {
            showAgentPicker();
            return;
        }
        if (item.id == ID_NEW) {
            if (selectedAgent != null) showNewFileDialog();
            return;
        }
        int index = item.id - FILE_BASE;
        if (index >= 0 && index < files.size()) preview(files.get(index));
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        int index = item.id - FILE_BASE;
        if (index >= 0 && index < files.size() && getParentActivity() != null) {
            File f = files.get(index);
            new AlertDialog.Builder(getParentActivity())
                    .setTitle(LocaleController.getString(R.string.Delete))
                    .setMessage(relative(f))
                    .setPositiveButton(LocaleController.getString(R.string.Delete), (d, w) -> {
                        deleteRecursive(f);
                        rebuild();
                    })
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .show();
            return true;
        }
        return false;
    }

    private void preview(File f) {
        if (getParentActivity() == null) return;
        if (!isText(f) || f.length() > 300 * 1024) {
            new AlertDialog.Builder(getParentActivity())
                    .setTitle(f.getName())
                    .setMessage(relative(f) + "\n" + humanSize(f.length()))
                    .setPositiveButton(LocaleController.getString(R.string.OK), null)
                    .show();
            return;
        }
        String text;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) Math.min(f.length(), 20000)];
            int n = in.read(buf);
            text = new String(buf, 0, Math.max(0, n), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            text = "error: " + t.getMessage();
        }
        final String initial = text;
        ScrollView scroll = new ScrollView(getParentActivity());
        TextView tv = new TextView(getParentActivity());
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextIsSelectable(true);
        tv.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(12));
        scroll.addView(tv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        new AlertDialog.Builder(getParentActivity())
                .setTitle(relative(f))
                .setView(scroll)
                .setPositiveButton(LocaleController.getString(R.string.OxWorkspaceEdit), (d, w) -> showEditDialog(f, initial))
                .setNeutralButton(LocaleController.getString(R.string.OxWorkspaceCopyPath), (d, w) -> {
                    AndroidUtilities.addToClipboard(f.getAbsolutePath());
                    android.widget.Toast.makeText(getParentActivity(), f.getAbsolutePath(), android.widget.Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(LocaleController.getString(R.string.Close), null)
                .show();
    }

    private void showEditDialog(File f, String initial) {
        if (getParentActivity() == null) return;
        android.widget.EditText edit = new android.widget.EditText(getParentActivity());
        edit.setText(initial == null ? "" : initial);
        edit.setTextSize(13);
        edit.setTypeface(android.graphics.Typeface.MONOSPACE);
        edit.setMinLines(6);
        edit.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        edit.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        ScrollView scroll = new ScrollView(getParentActivity());
        scroll.addView(edit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        new AlertDialog.Builder(getParentActivity())
                .setTitle(relative(f))
                .setView(scroll)
                .setPositiveButton(LocaleController.getString(R.string.OxWorkspaceSave), (d, w) -> {
                    try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
                        out.write(edit.getText().toString().getBytes(StandardCharsets.UTF_8));
                    } catch (Throwable t) {
                        android.widget.Toast.makeText(getParentActivity(), "error: " + t.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                    }
                    rebuild();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private void showNewFileDialog() {
        if (getParentActivity() == null) return;
        android.widget.LinearLayout wrap = new android.widget.LinearLayout(getParentActivity());
        wrap.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(24);
        wrap.setPadding(pad, AndroidUtilities.dp(8), pad, 0);
        android.widget.EditText name = new android.widget.EditText(getParentActivity());
        name.setHint(LocaleController.getString(R.string.OxWorkspaceNewHint));
        name.setSingleLine(true);
        wrap.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 6));
        android.widget.EditText content = new android.widget.EditText(getParentActivity());
        content.setHint(LocaleController.getString(R.string.OxWorkspaceNewContent));
        content.setMinLines(5);
        content.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        content.setTypeface(android.graphics.Typeface.MONOSPACE);
        wrap.addView(content, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 6));
        new AlertDialog.Builder(getParentActivity())
                .setTitle(LocaleController.getString(R.string.OxWorkspaceNew))
                .setView(wrap)
                .setPositiveButton(LocaleController.getString(R.string.OxWorkspaceSave), (d, w) -> {
                    String rel = name.getText().toString().trim();
                    if (rel.isEmpty() || rel.startsWith("/") || rel.contains("..")) {
                        android.widget.Toast.makeText(getParentActivity(), LocaleController.getString(R.string.OxWorkspaceInvalidName), android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        File target = OxAgentStore.resolveWorkspace(selectedAgent.id, rel);
                        File parent = target.getParentFile();
                        if (parent != null) parent.mkdirs();
                        try (java.io.FileOutputStream out = new java.io.FileOutputStream(target)) {
                            out.write(content.getText().toString().getBytes(StandardCharsets.UTF_8));
                        }
                        rebuild();
                    } catch (Throwable t) {
                        android.widget.Toast.makeText(getParentActivity(), "error: " + t.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private boolean isText(File f) {
        String n = f.getName().toLowerCase();
        int dot = n.lastIndexOf('.');
        if (dot < 0) return true;
        String ext = n.substring(dot + 1);
        switch (ext) {
            case "txt": case "md": case "json": case "js": case "ts": case "xml": case "html":
            case "css": case "log": case "yml": case "yaml": case "csv": case "sh": case "py":
            case "java": case "kt": case "c": case "h": case "cpp": case "ini": case "conf":
            case "toml": case "properties": case "svg":
                return true;
            default:
                return false;
        }
    }

    private void deleteRecursive(File f) {
        try {
            if (f.isDirectory()) {
                File[] list = f.listFiles();
                if (list != null) for (File c : list) deleteRecursive(c);
            }
            f.delete();
        } catch (Throwable ignored) {
        }
    }

    private void showAgentPicker() {
        List<OxAgent> agents = OxAgentStore.listAgents();
        if (agents.isEmpty() || getParentActivity() == null) return;
        CharSequence[] names = new CharSequence[agents.size()];
        for (int i = 0; i < agents.size(); i++) names[i] = agents.get(i).name;
        new AlertDialog.Builder(getParentActivity())
                .setItems(names, (d, which) -> {
                    selectedAgent = agents.get(which);
                    rebuild();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private void rebuild() {
        if (listView != null) listView.adapter.update(true);
    }
}
