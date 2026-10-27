package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ox.OxSkillStore;
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

/** Read-only browser for Operit SKILL.md packages. */
@SuppressLint("ViewConstructor")
public class OxSkillsActivity extends BaseFragment {

    private static final int SKILL_BASE = 5000;
    private static final int ID_GRANT = 1;
    private static final int ID_IMPORT = 2;
    private static final int ID_EXPORT = 3;
    private static final int REQ_IMPORT = 311;
    private static final int REQ_EXPORT = 312;
    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(LocaleController.getString(R.string.OxSkillsTitle));
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

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        boolean granted = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
        items.add(UItem.asHeader(LocaleController.getString(R.string.OxSkillsTitle)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_IMPORT, 0xFF34C759, 0xFF2AA84A,
                R.drawable.msg_download, LocaleController.getString(R.string.OxSkillsImport),
                LocaleController.getString(R.string.OxSkillsImportInfo)));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_EXPORT, 0xFF7C5CFF, 0xFF5B3FE0,
                R.drawable.msg_filehq, LocaleController.getString(R.string.OxSkillsExport),
                LocaleController.getString(R.string.OxSkillsExportInfo)));
        if (!granted) {
            items.add(SettingsActivity.SettingCell.Factory.of(ID_GRANT, 0xFFE8A33D, 0xFFD98A1F,
                    R.drawable.settings_policy, LocaleController.getString(R.string.OxSkillsGrant),
                    LocaleController.getString(R.string.OxSkillsGrantInfo)));
            items.add(UItem.asShadow(LocaleController.getString(R.string.OxSkillsEmpty)));
            return;
        }
        List<OxSkillStore.Skill> skills = OxSkillStore.list();
        if (skills.isEmpty()) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.OxSkillsEmpty)));
            return;
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.OxSkillsInfo)));
        for (int i = 0; i < skills.size(); i++) {
            OxSkillStore.Skill s = skills.get(i);
            items.add(SettingsActivity.SettingCell.Factory.of(SKILL_BASE + i, 0xFF7C5CFF, 0xFF5B3FE0,
                    R.drawable.settings_folders, s.name,
                    s.description == null || s.description.isEmpty() ? s.id : s.description));
        }
        items.add(UItem.asShadow(null));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_IMPORT && getParentActivity() != null) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            try {
                startActivityForResult(intent, REQ_IMPORT);
            } catch (Throwable t) {
                toast(LocaleController.formatString("OxSkillsImportFailed", R.string.OxSkillsImportFailed, t.getMessage()));
            }
            return;
        }
        if (item.id == ID_EXPORT && getParentActivity() != null) {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_TITLE, "orioneko-skills.zip");
            try {
                startActivityForResult(intent, REQ_EXPORT);
            } catch (Throwable t) {
                toast(LocaleController.formatString("OxSkillsExportFailed",
                        R.string.OxSkillsExportFailed, t.getMessage()));
            }
            return;
        }
        if (item.id == ID_GRANT && getParentActivity() != null) {
            try {
                getParentActivity().startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getParentActivity().getPackageName())));
            } catch (Throwable ignored) {
            }
            return;
        }
        List<OxSkillStore.Skill> skills = OxSkillStore.list();
        int index = item.id - SKILL_BASE;
        if (index < 0 || index >= skills.size() || getParentActivity() == null) return;
        OxSkillStore.Skill s = skills.get(index);
        String content = OxSkillStore.read(s.name);
        ScrollView scroll = new ScrollView(getParentActivity());
        TextView tv = new TextView(getParentActivity());
        tv.setText(content);
        tv.setTextSize(13);
        tv.setTextIsSelectable(true);
        tv.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(12));
        scroll.addView(tv);
        new AlertDialog.Builder(getParentActivity())
                .setTitle(s.name)
                .setView(scroll)
                .setPositiveButton(LocaleController.getString(R.string.OK), null)
                .show();
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode != android.app.Activity.RESULT_OK || data == null) return;
        final android.net.Uri uri = data.getData();
        final android.content.Context ctx = getParentActivity();
        if (uri == null || ctx == null) return;
        if (requestCode == REQ_EXPORT) {
            new Thread(() -> {
                String msg;
                try {
                    int count = OxSkillStore.exportZip(ctx, uri);
                    msg = LocaleController.formatString("OxSkillsExported",
                            R.string.OxSkillsExported, count);
                } catch (Throwable t) {
                    String detail = t.getMessage();
                    if ("no skills to export".equals(detail)) {
                        msg = LocaleController.getString(R.string.OxSkillsExportEmpty);
                    } else {
                        msg = LocaleController.formatString("OxSkillsExportFailed",
                                R.string.OxSkillsExportFailed, detail == null ? "" : detail);
                    }
                }
                final String m = msg;
                AndroidUtilities.runOnUIThread(() -> {
                    toast(m);
                    if (listView != null) listView.adapter.update(true);
                });
            }, "ox-skill-export").start();
            return;
        }
        if (requestCode != REQ_IMPORT) return;
        new Thread(() -> {
            String msg;
            try {
                int count = OxSkillStore.importFromUri(ctx, uri);
                msg = LocaleController.formatString("OxSkillsImported", R.string.OxSkillsImported, count);
            } catch (Throwable t) {
                msg = LocaleController.formatString("OxSkillsImportFailed", R.string.OxSkillsImportFailed, t.getMessage());
            }
            final String m = msg;
            AndroidUtilities.runOnUIThread(() -> {
                toast(m);
                if (listView != null) listView.adapter.update(true);
            });
        }, "ox-skill-import").start();
    }

    private void toast(String s) {
        if (getParentActivity() != null) {
            android.widget.Toast.makeText(getParentActivity(), s, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        List<OxSkillStore.Skill> skills = OxSkillStore.list();
        int index = item.id - SKILL_BASE;
        if (index < 0 || index >= skills.size() || getParentActivity() == null) return false;
        final OxSkillStore.Skill s = skills.get(index);
        new AlertDialog.Builder(getParentActivity())
                .setTitle(s.name)
                .setMessage(LocaleController.getString(R.string.OxSkillDeleteMsg))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setPositiveButton(LocaleController.getString(R.string.Delete), (d, w) -> {
                    if (OxSkillStore.delete(s)) {
                        toast(LocaleController.formatString("OxSkillDeleted",
                                R.string.OxSkillDeleted, s.name));
                        if (listView != null) listView.adapter.update(true);
                    } else {
                        toast(LocaleController.getString(R.string.OxSkillDeleteReadOnly));
                    }
                })
                .show();
        return true;
    }
}
