package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ox.OxAgent;
import org.telegram.ox.OxAgentStore;
import org.telegram.ox.OxMemoryStore;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

/** View and edit one agent's long-term memory file. */
@SuppressLint("ViewConstructor")
public class OxAgentMemoryActivity extends BaseFragment {

    private static final int MENU_SAVE = 1;
    private static final int MENU_CLEAR = 2;

    private final String agentId;
    private OxAgent agent;
    private EditText editText;
    private TextSettingsCell libraryRow;

    public OxAgentMemoryActivity(String agentId) {
        super();
        this.agentId = agentId;
    }

    public OxAgentMemoryActivity(Bundle args) {
        super(args);
        this.agentId = args == null ? "" : args.getString("agent_id", "");
    }

    @Override
    public boolean onFragmentCreate() {
        agent = OxAgentStore.loadAgent(agentId);
        return agent != null && super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(LocaleController.getString(R.string.OxMemory));
        actionBar.setSubtitle(agent == null ? "" : agent.name);
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItemWithWidth(MENU_SAVE, R.drawable.ic_ab_done, AndroidUtilities.dp(56));
        menu.addItem(MENU_CLEAR, R.drawable.msg_delete);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == MENU_SAVE) save();
                else if (id == MENU_CLEAR) clear();
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));

        libraryRow = new TextSettingsCell(context);
        libraryRow.setTextAndValue(LocaleController.getString(R.string.OxMemoryLibrary),
                OxMemoryStore.stats(agentId), true);
        libraryRow.setOnClickListener(v -> showLibrary());
        content.addView(libraryRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextSettingsCell clearLibrary = new TextSettingsCell(context);
        clearLibrary.setText(LocaleController.getString(R.string.OxMemoryLibraryClear), true);
        clearLibrary.setOnClickListener(v -> clearLibrary());
        content.addView(clearLibrary, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
        bg.setCornerRadius(AndroidUtilities.dp(12));
        card.setBackground(bg);
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10), AndroidUtilities.dp(14), AndroidUtilities.dp(10));

        TextView info = new TextView(context);
        info.setText(LocaleController.getString(R.string.OxMemoryInfo));
        info.setTextSize(12);
        info.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        info.setLineSpacing(AndroidUtilities.dp(2), 1f);
        card.addView(info, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        editText = new EditText(context);
        editText.setText(OxAgentStore.loadMemory(agentId, 200000));
        editText.setHint(LocaleController.getString(R.string.OxMemoryEmpty));
        editText.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setTextSize(14);
        editText.setTypeface(Typeface.MONOSPACE);
        editText.setGravity(Gravity.TOP | Gravity.START);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setMinLines(10);
        editText.setBackground(null);
        editText.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));
        card.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        content.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        root.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = root;
        return root;
    }

    private void showLibrary() {
        if (getParentActivity() == null) return;
        String text = OxMemoryStore.recent(agentId, 20);
        if (text.isEmpty()) text = LocaleController.getString(R.string.OxMemoryLibraryEmpty);
        new AlertDialog.Builder(getParentActivity())
                .setTitle(LocaleController.getString(R.string.OxMemoryLibrary))
                .setMessage(text)
                .setPositiveButton(LocaleController.getString(R.string.OK), null)
                .show();
    }

    private void clearLibrary() {
        if (getParentActivity() == null) return;
        new AlertDialog.Builder(getParentActivity())
                .setTitle(LocaleController.getString(R.string.OxMemoryLibraryClear))
                .setMessage(LocaleController.getString(R.string.OxMemoryLibraryClearConfirm))
                .setPositiveButton(LocaleController.getString(R.string.OxMemoryLibraryClear), (d, w) -> {
                    OxMemoryStore.clear(agentId);
                    if (libraryRow != null) {
                        libraryRow.setTextAndValue(LocaleController.getString(R.string.OxMemoryLibrary),
                                OxMemoryStore.stats(agentId), true);
                    }
                    Toast.makeText(getParentActivity(),
                            LocaleController.getString(R.string.OxMemorySaved), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private void save() {
        if (editText == null) return;
        OxAgentStore.saveMemory(agentId, editText.getText().toString());
        Toast.makeText(getParentActivity(), LocaleController.getString(R.string.OxMemorySaved), Toast.LENGTH_SHORT).show();
        finishFragment();
    }

    private void clear() {
        if (getParentActivity() == null) return;
        new AlertDialog.Builder(getParentActivity())
                .setTitle(LocaleController.getString(R.string.OxMemoryClear))
                .setMessage(LocaleController.getString(R.string.OxMemoryClearConfirm))
                .setPositiveButton(LocaleController.getString(R.string.OxMemoryClear), (d, w) -> {
                    OxAgentStore.saveMemory(agentId, "");
                    if (editText != null) editText.setText("");
                    Toast.makeText(getParentActivity(), LocaleController.getString(R.string.OxMemorySaved), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }
}
