package org.telegram.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.OpenableColumns;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
    private static final int ID_IMPORT = 3;
    private static final int FILE_BASE = 1000;
    private static final int REQ_IMPORT = 2001;
    private static final int REQ_EXPORT = 2002;
    private static final long MAX_IMPORT_BYTES = 20L * 1024 * 1024;

    private UniversalRecyclerView listView;
    private OxAgent selectedAgent;
    private File pendingExport;
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
        items.add(SettingsActivity.SettingCell.Factory.of(ID_IMPORT, 0, 0, 0,
                LocaleController.getString(R.string.OxWorkspaceImport),
                LocaleController.getString(R.string.OxWorkspaceImportHint)));

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
        if (item.id == ID_IMPORT) {
            if (selectedAgent != null) pickImportFile();
            return;
        }
        int index = item.id - FILE_BASE;
        if (index >= 0 && index < files.size()) preview(files.get(index));
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        int index = item.id - FILE_BASE;
        if (index >= 0 && index < files.size() && getParentActivity() != null) {
            final File f = files.get(index);
            CharSequence[] actions = {
                    LocaleController.getString(R.string.OxWorkspacePreview),
                    LocaleController.getString(R.string.OxWorkspaceRename),
                    LocaleController.getString(R.string.OxWorkspaceExport),
                    LocaleController.getString(R.string.Delete)
            };
            new AlertDialog.Builder(getParentActivity())
                    .setTitle(relative(f))
                    .setItems(actions, (d, which) -> {
                        if (which == 0) preview(f);
                        else if (which == 1) renameFile(f);
                        else if (which == 2) exportFile(f);
                        else confirmDelete(f);
                    })
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .show();
            return true;
        }
        return false;
    }

    private void renameFile(File f) {
        if (getParentActivity() == null || selectedAgent == null) return;
        android.widget.EditText edit = new android.widget.EditText(getParentActivity());
        edit.setText(relative(f));
        edit.setSingleLine(true);
        edit.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        new AlertDialog.Builder(getParentActivity())
                .setTitle(LocaleController.getString(R.string.OxWorkspaceRename))
                .setView(edit)
                .setPositiveButton(LocaleController.getString(R.string.OxWorkspaceSave), (d, w) -> {
                    String rel = edit.getText().toString().trim();
                    if (rel.isEmpty() || rel.startsWith("/") || rel.contains("..")) {
                        android.widget.Toast.makeText(getParentActivity(),
                                LocaleController.getString(R.string.OxWorkspaceInvalidName),
                                android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        File target = OxAgentStore.resolveWorkspace(selectedAgent.id, rel);
                        if (target.equals(f)) return;
                        if (target.exists()) {
                            android.widget.Toast.makeText(getParentActivity(),
                                    LocaleController.getString(R.string.OxWorkspaceRenameExists),
                                    android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        File parent = target.getParentFile();
                        if (parent != null) parent.mkdirs();
                        boolean moved = f.renameTo(target);
                        if (!moved) {
                            try (InputStream in = new FileInputStream(f);
                                 OutputStream out = new FileOutputStream(target)) {
                                byte[] buf = new byte[8192];
                                int n;
                                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                            }
                            f.delete();
                        }
                        android.widget.Toast.makeText(getParentActivity(),
                                LocaleController.formatString("OxWorkspaceRenamed",
                                        R.string.OxWorkspaceRenamed, target.getName()),
                                android.widget.Toast.LENGTH_SHORT).show();
                        rebuild();
                    } catch (Throwable t) {
                        android.widget.Toast.makeText(getParentActivity(),
                                LocaleController.getString(R.string.OxWorkspaceRenameFailed) + ": " + t.getMessage(),
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private void confirmDelete(File f) {
        if (getParentActivity() == null) return;
        new AlertDialog.Builder(getParentActivity())
                .setTitle(LocaleController.getString(R.string.Delete))
                .setMessage(relative(f))
                .setPositiveButton(LocaleController.getString(R.string.Delete), (d, w) -> {
                    deleteRecursive(f);
                    rebuild();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private void exportFile(File f) {
        if (getParentActivity() == null) return;
        pendingExport = f;
        try {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            String ext = dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
            String mime = ext.isEmpty() ? null
                    : android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            i.setType(mime == null ? "application/octet-stream" : mime);
            i.putExtra(Intent.EXTRA_TITLE, name);
            startActivityForResult(i, REQ_EXPORT);
        } catch (Throwable t) {
            pendingExport = null;
            android.widget.Toast.makeText(getParentActivity(), "error: " + t.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
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

    private void pickImportFile() {
        if (getParentActivity() == null) return;
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_IMPORT);
        } catch (Throwable t) {
            android.widget.Toast.makeText(getParentActivity(), "error: " + t.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            pendingExport = null;
            return;
        }
        if (requestCode == REQ_EXPORT) {
            final File source = pendingExport;
            pendingExport = null;
            final Uri dest = data.getData();
            final Context ctx = getParentActivity();
            if (source == null || dest == null || ctx == null) return;
            new Thread(() -> {
                String msg;
                try (InputStream in = new FileInputStream(source);
                     OutputStream out = ctx.getContentResolver().openOutputStream(dest)) {
                    if (out == null) throw new IOException("cannot open the destination");
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    msg = LocaleController.formatString("OxWorkspaceExported",
                            R.string.OxWorkspaceExported, source.getName());
                } catch (Throwable t) {
                    msg = LocaleController.getString(R.string.OxWorkspaceExportFailed) + ": " + t.getMessage();
                }
                final String result = msg;
                AndroidUtilities.runOnUIThread(() -> {
                    if (getParentActivity() != null) {
                        android.widget.Toast.makeText(getParentActivity(), result, android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            }, "ox-workspace-export").start();
            return;
        }
        if (requestCode != REQ_IMPORT) return;
        final Uri uri = data.getData();
        final OxAgent agent = selectedAgent;
        final Context ctx = getParentActivity();
        if (uri == null || agent == null || ctx == null) return;
        String name = "imported.bin";
        long size = -1;
        try (android.database.Cursor c = ctx.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int si = c.getColumnIndex(OpenableColumns.SIZE);
                if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni);
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si);
            }
        } catch (Throwable ignored) {
        }
        final String fileName = sanitizeFileName(name);
        if (size > MAX_IMPORT_BYTES) {
            android.widget.Toast.makeText(ctx,
                    LocaleController.getString(R.string.OxWorkspaceImportTooLarge),
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(() -> {
            String msg;
            try {
                File target = uniqueTarget(agent.id, fileName);
                File parent = target.getParentFile();
                if (parent != null) parent.mkdirs();
                InputStream in = ctx.getContentResolver().openInputStream(uri);
                if (in == null) throw new IOException("cannot open the selected file");
                OutputStream out = new FileOutputStream(target);
                byte[] buf = new byte[8192];
                long total = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_IMPORT_BYTES) {
                        in.close();
                        out.close();
                        target.delete();
                        throw new IOException("file is larger than 20 MB");
                    }
                    out.write(buf, 0, n);
                }
                in.close();
                out.close();
                msg = LocaleController.formatString("OxWorkspaceImported",
                        R.string.OxWorkspaceImported, target.getName());
            } catch (Throwable t) {
                msg = LocaleController.getString(R.string.OxWorkspaceImportFailed) + ": " + t.getMessage();
            }
            final String result = msg;
            AndroidUtilities.runOnUIThread(() -> {
                if (getParentActivity() != null) {
                    android.widget.Toast.makeText(getParentActivity(), result, android.widget.Toast.LENGTH_SHORT).show();
                }
                rebuild();
            });
        }, "ox-workspace-import").start();
    }

    private String sanitizeFileName(String raw) {
        String n = raw == null ? "" : raw.replace('\\', '/');
        int slash = n.lastIndexOf('/');
        if (slash >= 0) n = n.substring(slash + 1);
        n = n.replace("..", "_").trim();
        if (n.isEmpty() || ".".equals(n)) n = "imported.bin";
        return n;
    }

    private File uniqueTarget(String agentId, String name) throws IOException {
        File f = OxAgentStore.resolveWorkspace(agentId, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 100; i++) {
            File candidate = OxAgentStore.resolveWorkspace(agentId, base + " (" + i + ")" + ext);
            if (!candidate.exists()) return candidate;
        }
        throw new IOException("too many files with that name");
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
