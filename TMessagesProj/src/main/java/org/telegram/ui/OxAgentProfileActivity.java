package org.telegram.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ox.OxAgent;
import org.telegram.ox.OxAgentStore;
import org.telegram.ox.OxAgentsBridge;
import org.telegram.ox.OxMode;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Agent profile ("add friend"-style editor, mirroring the private Orioneko repo's
 * friend form): avatar, name, remark plus the full model wiring. Saving re-mirrors
 * the synthetic Telegram contact so the stock UI (dialogs, chat header, profile)
 * picks the changes up immediately.
 */
@SuppressLint("ViewConstructor")
public class OxAgentProfileActivity extends BaseFragment {

    private static final int MENU_SAVE = 1;
    private static final int REQ_AVATAR = 2001;

    private OxAgent agent;
    private final String agentId;

    private ImageView avatarPreview;
    private String pendingAvatarPath;   // picked image, saved on Save
    private boolean avatarCleared;
    private EditText firstNameField;
    private EditText remarkField;
    private EditText baseUrlField;
    private EditText apiKeyField;
    private EditText modelField;
    private EditText mdField;

    public OxAgentProfileActivity(String id) {
        super();
        agentId = id;
    }

    @Override
    public boolean onFragmentCreate() {
        agent = OxAgentStore.loadAgent(agentId);
        return agent != null && super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.OxAgentProfile));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == MENU_SAVE) onSave();
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(MENU_SAVE, LocaleController.getString(R.string.Save).toUpperCase());

        LinearLayout scrollChild = new LinearLayout(context);
        scrollChild.setOrientation(LinearLayout.VERTICAL);
        int white = getThemedColor(Theme.key_windowBackgroundWhite);
        scrollChild.setBackgroundColor(white);

        // avatar
        FrameLayout avatarWrap = new FrameLayout(context);
        avatarWrap.setBackgroundColor(white);
        avatarPreview = new ImageView(context);
        avatarPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarPreview.setClipToOutline(true);
        avatarPreview.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(android.view.View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        avatarWrap.addView(avatarPreview, LayoutHelper.createFrame(AndroidUtilities.dp(84), AndroidUtilities.dp(84), Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, AndroidUtilities.dp(16), 0, 0));
        TextView avatarHint = new TextView(context);
        avatarHint.setText(LocaleController.getString(R.string.OxAgentAvatarPick));
        avatarHint.setTextSize(13);
        avatarHint.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4));
        avatarHint.setGravity(Gravity.CENTER);
        avatarWrap.addView(avatarHint, LayoutHelper.createFrame(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, AndroidUtilities.dp(12)));
        avatarWrap.setOnClickListener(v -> openAvatarPicker());
        scrollChild.addView(avatarWrap, LayoutHelper.createLinear(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        firstNameField = addField(context, scrollChild, LocaleController.getString(R.string.OxAgentNameHint2), agent.name, true);
        remarkField = addField(context, scrollChild, LocaleController.getString(R.string.OxAgentRemark), agent.remark, false);

        addShadow(context, scrollChild, LocaleController.getString(R.string.OxEndpointPresets));
        TextView presetCell = addButton(context, scrollChild, "🌐 " + LocaleController.getString(R.string.OxMcpPresetChoose), v -> showPresetChooser());
        baseUrlField = addField(context, scrollChild, "Base URL", agent.baseUrl, false);
        apiKeyField = addField(context, scrollChild, "API Key", agent.apiKey, false);
        modelField = addField(context, scrollChild, LocaleController.getString(R.string.OxAgentModel), agent.model, false);

        addShadow(context, scrollChild, "AGENTS.md");
        mdField = new EditText(context);
        mdField.setText(agent.agentsMd == null ? "" : agent.agentsMd);
        mdField.setMinLines(6);
        mdField.setGravity(Gravity.TOP);
        mdField.setBackground(null);
        mdField.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        mdField.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        scrollChild.addView(mdField, LayoutHelper.createLinear(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(160)));
        addShadow(context, scrollChild, null);

        if (!agent.builtIn) {
            TextView delete = addButton(context, scrollChild, LocaleController.getString(R.string.OxAgentDeleteTitle), v -> confirmDelete());
            delete.setTextColor(getThemedColor(Theme.key_text_RedRegular));
        }

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.addView(scrollChild, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        frame.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = frame;

        renderAvatar();
        return fragmentView;
    }

    private EditText addField(Context context, LinearLayout parent, String hint, String value, boolean bold) {
        EditText et = new EditText(context);
        et.setHint(hint);
        et.setText(value == null ? "" : value);
        et.setTextSize(17);
        if (bold) et.setTypeface(AndroidUtilities.bold());
        et.setBackground(null);
        et.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        et.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        et.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        parent.addView(et, LayoutHelper.createLinear(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View divider = new View(context);
        divider.setBackgroundColor(getThemedColor(Theme.key_divider));
        parent.addView(divider, LayoutHelper.createLinear(ViewGroup.LayoutParams.MATCH_PARENT, 1, 16, 0, 16, 0));
        return et;
    }

    private TextView addButton(Context context, LinearLayout parent, String text, View.OnClickListener l) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        tv.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        tv.setBackground(Theme.getSelectorDrawable(false));
        tv.setOnClickListener(l);
        parent.addView(tv, LayoutHelper.createLinear(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View divider = new View(context);
        divider.setBackgroundColor(getThemedColor(Theme.key_divider));
        parent.addView(divider, LayoutHelper.createLinear(ViewGroup.LayoutParams.MATCH_PARENT, 1, 16, 0, 16, 0));
        return tv;
    }

    private void addShadow(Context context, LinearLayout parent, String text) {
        TextView tv = new TextView(context);
        tv.setText(text == null ? "" : text.toUpperCase());
        if (text != null) {
            tv.setTextSize(13);
            tv.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText6));
            tv.setTypeface(AndroidUtilities.bold());
            tv.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(4));
        } else {
            tv.setVisibility(View.INVISIBLE);
            tv.setPadding(0, AndroidUtilities.dp(6), 0, 0);
        }
        parent.addView(tv, LayoutHelper.createLinear(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void renderAvatar() {
        try {
            String path = pendingAvatarPath != null ? pendingAvatarPath : agent.avatarPath;
            if (path == null || path.isEmpty() || !new File(path).exists()) {
                avatarPreview.setImageDrawable(null);
                avatarPreview.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
                return;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 2;
            Bitmap bmp = BitmapFactory.decodeFile(path, opts);
            if (bmp != null) {
                avatarPreview.setImageBitmap(bmp);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void openAvatarPicker() {
        try {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, REQ_AVATAR);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (requestCode != REQ_AVATAR || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        try {
            InputStream in = getParentActivity().getContentResolver().openInputStream(data.getData());
            File dir = new File(ApplicationLoaderDirs(), "oxavatars");
            dir.mkdirs();
            File tmp = new File(dir, "pick_" + agent.id + ".jpg");
            FileOutputStream out = new FileOutputStream(tmp);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close();
            in.close();
            pendingAvatarPath = tmp.getAbsolutePath();
            avatarCleared = false;
            renderAvatar();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private File ApplicationLoaderDirs() {
        return new File(org.telegram.messenger.ApplicationLoader.applicationContext.getFilesDir(), "oxavatars");
    }

    private void showPresetChooser() {
        if (getParentActivity() == null) return;
        final String[][] presets = {
            {"DeepSeek (cloud)", "https://api.deepseek.com", "deepseek-chat"},
            {"Ollama (local/LAN device)", "http://127.0.0.1:11434/v1", "qwen2.5:7b"},
            {"LM Studio (local/LAN device)", "http://127.0.0.1:1234/v1", ""},
            {"opencode serve (local/LAN device)", "http://127.0.0.1:4010/v1", ""},
        };
        CharSequence[] names = new CharSequence[presets.length];
        for (int i = 0; i < presets.length; i++) names[i] = presets[i][0];
        new AlertDialog.Builder(getParentActivity())
                .setItems(names, (d, which) -> {
                    baseUrlField.setText(presets[which][1]);
                    if (!presets[which][2].isEmpty()) modelField.setText(presets[which][2]);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private void onSave() {
        String name = firstNameField.getText().toString().trim();
        if (name.isEmpty()) {
            firstNameField.requestFocus();
            return;
        }
        agent.name = name;
        agent.remark = remarkField.getText().toString().trim();
        agent.baseUrl = baseUrlField.getText().toString().trim();
        agent.apiKey = apiKeyField.getText().toString().trim();
        agent.model = modelField.getText().toString().trim();
        agent.agentsMd = mdField.getText().toString();
        if (avatarCleared) {
            if (agent.avatarPath != null) new File(agent.avatarPath).delete();
            agent.avatarPath = null;
        } else if (pendingAvatarPath != null) {
            File master = new File(new File(org.telegram.messenger.ApplicationLoader.applicationContext.getFilesDir(), "oxavatars"), agent.id + ".jpg");
            try {
                java.io.FileInputStream in = new java.io.FileInputStream(pendingAvatarPath);
                java.io.FileOutputStream out = new java.io.FileOutputStream(master);
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close();
                in.close();
                agent.avatarPath = master.getAbsolutePath();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        OxAgentStore.saveAgent(agent);
        // refresh the stock UI: new names/remark flow through the synthetic user; avatar files rewritten
        OxAgentsBridge.syncAgents(false);
        if (getParentActivity() != null && OxMode.isAgentOnly()) {
            NotificationCenter.getInstance(0).postNotificationName(NotificationCenter.updateInterfaces, org.telegram.messenger.MessagesController.UPDATE_MASK_NAME | org.telegram.messenger.MessagesController.UPDATE_MASK_AVATAR);
        }
        finishFragment();
    }

    private void confirmDelete() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.OxAgentDeleteTitle));
        builder.setMessage(LocaleController.formatString("OxDeleteAgentConfirm", R.string.OxDeleteAgentConfirm, agent.name));
        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (d, w) -> {
            OxAgentsBridge.onAgentDeleted(agent.tgId);
            OxAgentStore.deleteAgent(agent.id);
            finishFragment();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }
}
