package org.telegram.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ox.OxAgent;
import org.telegram.ox.OxAgentStore;
import org.telegram.ox.OxAgentsBridge;
import org.telegram.ox.OxChatEntry;
import org.telegram.ox.OxGroupStore;
import org.telegram.ox.OxHarnessAdapter;
import org.telegram.ox.OxMemoryStore;
import org.telegram.ox.OxMode;

import java.text.SimpleDateFormat;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * OxUI (preview): a deliberately separate front end, not built from Telegram's
 * cells or action bar. It reads the same local agent chat history and sends
 * through {@link OxAgentsBridge#sendFromOxUi}, so messages stay in sync with
 * the Telegram UI. The entry is a preview until feature parity is complete.
 */
public class OxUiPreviewActivity extends Activity {

    private static final int BONE = 0xFFF1EBDD;
    private static final int PAPER = 0xFFE5DCCB;
    private static final int INK = 0xFF14211C;
    private static final int MUTED = 0xFF566259;
    private static final int SIGNAL = 0xFFC8EF6A;
    private static final int LINE = 0x8C14211C;
    private static final int CORAL = 0xFFCE6549;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<OxAgent> agents = new ArrayList<>();
    private OxAgent selected;

    private LinearLayout agentRail;
    private LinearLayout messages;
    private ScrollView scroll;
    private EditText input;
    private TextView send;
    private TextView statusLine;
    private FrameLayout overlay;
    private String panelQuery = "";

    private String pendingText;
    private String pendingAgentId;
    private long pendingAt;
    private int pendingBaseSize;
    private String lastSignature = "";
    private boolean firstRender = true;
    private boolean rootMode;
    private long selectedChatId;
    private String selectedGroupTitle = "";
    private String pendingImagePath;
    private boolean userJustSent;
    private String failedText;
    private String failedImagePath;
    private String failedTargetKey;
    private final List<GroupItem> groupItems = new ArrayList<>();
    private LinearLayout attachRow;
    private ImageView attachPreview;
    private TextView attachName;

    private static class GroupItem {
        long chatId;
        String title;
        int agents;
    }

    private static final int REQ_PICK_IMAGE = 1001;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            refresh(false);
            handler.postDelayed(this, 1200);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.setStatusBarColor(BONE);
        w.setNavigationBarColor(BONE);
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            w.getDecorView().setSystemUiVisibility(flags);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            w.setStatusBarColor(BONE);
            w.setNavigationBarColor(BONE);
        }
        rootMode = getIntent() != null && getIntent().getBooleanExtra("oxui_root", false);
        setContentView(buildRoot());
        loadAgents();
        loadGroupsAsync();
        handler.post(poll);
    }

    @Override
    public void onBackPressed() {
        if (overlay != null && overlay.getVisibility() == View.VISIBLE) {
            hideOverlay();
            return;
        }
        if (rootMode) {
            moveTaskToBack(true);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private Typeface serif() {
        return Typeface.create("serif", Typeface.NORMAL);
    }

    private Typeface sans() {
        return Typeface.create("sans-serif", Typeface.NORMAL);
    }

    private Typeface mono() {
        return Typeface.create("monospace", Typeface.NORMAL);
    }

    private GradientDrawable plate(int color, float radius, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(Math.max(1, dp(strokeWidth)), stroke);
        return d;
    }

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BONE);
        root.setFitsSystemWindows(true);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(22), dp(16), dp(22), dp(12));

        TextView back = new TextView(this);
        back.setText("←");
        back.setTextSize(24);
        back.setTextColor(INK);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(36), dp(44)));

        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.VERTICAL);
        title.setPadding(dp(8), 0, 0, 0);
        TextView word = new TextView(this);
        word.setText("Orioneko");
        word.setTextSize(27);
        word.setTypeface(serif());
        word.setTextColor(INK);
        word.setLetterSpacing(-0.02f);
        title.addView(word);
        TextView sub = new TextView(this);
        sub.setText("Local agent studio");
        sub.setTextSize(10.5f);
        sub.setTypeface(sans());
        sub.setTextColor(MUTED);
        sub.setLetterSpacing(0.08f);
        title.addView(sub);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView agentsButton = new TextView(this);
        agentsButton.setText(LocaleController.getString(R.string.OxUiAgents));
        agentsButton.setTextSize(11);
        agentsButton.setTypeface(sans());
        agentsButton.setTextColor(INK);
        agentsButton.setGravity(Gravity.CENTER);
        agentsButton.setPadding(dp(8), dp(5), dp(8), dp(5));
        agentsButton.setOnClickListener(v -> showAgentPanel());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(30));
        alp.rightMargin = dp(6);
        header.addView(agentsButton, alp);

        TextView preview = new TextView(this);
        preview.setText("Preview");
        preview.setTextSize(10.5f);
        preview.setTypeface(mono());
        preview.setTextColor(INK);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(dp(10), dp(5), dp(10), dp(5));
        preview.setBackground(plate(SIGNAL, 20, 0, 0));
        header.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));

        TextView telegram = new TextView(this);
        telegram.setText("Telegram UI");
        telegram.setTextSize(11);
        telegram.setTypeface(sans());
        telegram.setTextColor(INK);
        telegram.setGravity(Gravity.CENTER);
        telegram.setPadding(dp(11), dp(5), dp(11), dp(5));
        telegram.setBackground(plate(0, 18, LINE, 1));
        telegram.setOnClickListener(v -> openTelegramUi());
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(30));
        tlp.leftMargin = dp(8);
        header.addView(telegram, tlp);

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View rule = new View(this);
        rule.setBackgroundColor(LINE);
        root.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        HorizontalScrollView railScroll = new HorizontalScrollView(this);
        railScroll.setHorizontalScrollBarEnabled(false);
        railScroll.setClipToPadding(false);
        railScroll.setPadding(dp(18), dp(12), dp(18), dp(8));
        agentRail = new LinearLayout(this);
        agentRail.setOrientation(LinearLayout.HORIZONTAL);
        railScroll.addView(agentRail, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(railScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(22), dp(4), dp(22), dp(16));
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(messages, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout composerWrap = new LinearLayout(this);
        composerWrap.setOrientation(LinearLayout.VERTICAL);
        composerWrap.setPadding(dp(16), dp(6), dp(16), dp(10));

        attachRow = new LinearLayout(this);
        attachRow.setOrientation(LinearLayout.HORIZONTAL);
        attachRow.setGravity(Gravity.CENTER_VERTICAL);
        attachRow.setPadding(dp(6), dp(4), dp(6), dp(8));
        attachRow.setVisibility(View.GONE);
        attachPreview = new ImageView(this);
        attachPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        attachPreview.setBackground(plate(PAPER, 12, LINE, 1));
        attachPreview.setClipToOutline(true);
        attachRow.addView(attachPreview, new LinearLayout.LayoutParams(dp(52), dp(52)));
        attachName = new TextView(this);
        attachName.setTextSize(12);
        attachName.setTypeface(sans());
        attachName.setTextColor(INK);
        attachName.setSingleLine(true);
        attachName.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        attachName.setPadding(dp(10), 0, dp(8), 0);
        attachRow.addView(attachName, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView attachCancel = new TextView(this);
        attachCancel.setText("×");
        attachCancel.setTextSize(20);
        attachCancel.setTextColor(MUTED);
        attachCancel.setGravity(Gravity.CENTER);
        attachCancel.setOnClickListener(v -> {
            pendingImagePath = null;
            attachRow.setVisibility(View.GONE);
            updateSendVisual();
        });
        attachRow.addView(attachCancel, new LinearLayout.LayoutParams(dp(36), dp(44)));
        composerWrap.addView(attachRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(6), dp(4), dp(6), dp(4));
        composer.setBackground(plate(PAPER, 22, LINE, 1));

        TextView attach = new TextView(this);
        attach.setText("＋");
        attach.setTextSize(19);
        attach.setTypeface(sans());
        attach.setTextColor(INK);
        attach.setGravity(Gravity.CENTER);
        attach.setContentDescription(LocaleController.getString(R.string.OxUiAttach));
        attach.setOnClickListener(v -> pickImage());
        composer.addView(attach, new LinearLayout.LayoutParams(dp(40), dp(44)));

        input = new EditText(this);
        input.setHint(LocaleController.getString(R.string.OxUiHint));
        input.setHintTextColor(MUTED);
        input.setTextColor(INK);
        input.setTextSize(15.5f);
        input.setTypeface(sans());
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(dp(12), dp(9), dp(8), dp(9));
        input.setMinHeight(dp(44));
        input.setMaxLines(5);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        composer.addView(input, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        send = new TextView(this);
        send.setText("↑");
        send.setTextSize(21);
        send.setTypeface(sans());
        send.setTextColor(INK);
        send.setGravity(Gravity.CENTER);
        send.setBackground(plate(SIGNAL, 22, 0, 0));
        send.setAlpha(0.42f);
        send.setContentDescription("Send");
        send.setOnClickListener(v -> sendMessage());
        composer.addView(send, new LinearLayout.LayoutParams(dp(44), dp(44)));
        composerWrap.addView(composer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(8), dp(7), dp(8), 0);
        statusLine = new TextView(this);
        statusLine.setText(LocaleController.getString(R.string.OxUiFooter));
        statusLine.setTextSize(10.5f);
        statusLine.setTypeface(sans());
        statusLine.setTextColor(MUTED);
        footer.addView(statusLine, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView open = new TextView(this);
        open.setText(LocaleController.getString(R.string.OxUiOpenTelegram));
        open.setTextSize(10.5f);
        open.setTypeface(sans());
        open.setTextColor(INK);
        open.setOnClickListener(v -> openTelegramUi());
        footer.addView(open);
        composerWrap.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(composerWrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                updateSendVisual();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        send.setEnabled(false);
        updateSendVisual();

        FrameLayout shell = new FrameLayout(this);
        shell.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay = new FrameLayout(this);
        overlay.setBackgroundColor(BONE);
        overlay.setVisibility(View.GONE);
        overlay.setClickable(true);
        shell.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return shell;
    }

    private void updateSendVisual() {
        if (send == null) return;
        CharSequence text = input == null ? "" : input.getText();
        boolean ready = (selected != null && selected.tgId != 0) || isGroupTarget();
        ready = ready && ((text != null && text.toString().trim().length() > 0)
                || pendingImagePath != null);
        send.setEnabled(ready);
        send.setAlpha(ready ? 1f : 0.9f);
        send.setTextColor(ready ? INK : MUTED);
        send.setBackground(plate(ready ? SIGNAL : PAPER, 22, ready ? 0 : LINE, 1));
    }

    private void pickImage() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent,
                    LocaleController.getString(R.string.OxUiAttach)), REQ_PICK_IMAGE);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.OxUiNoAgentTitle, Toast.LENGTH_SHORT).show();
        }
    }

    private String importPickedImage(android.net.Uri uri) {
        try {
            String ext = ".jpg";
            String mime = getContentResolver().getType(uri);
            if (mime != null) {
                if (mime.contains("png")) ext = ".png";
                else if (mime.contains("webp")) ext = ".webp";
                else if (mime.contains("gif")) ext = ".gif";
            }
            File dir = new File(getFilesDir(), "oxui_media");
            dir.mkdirs();
            File dest = new File(dir, "img_" + System.currentTimeMillis() + ext);
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                java.io.FileOutputStream out = new java.io.FileOutputStream(dest);
                byte[] buf = new byte[16384];
                int n;
                long total = 0;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    out.write(buf, 0, n);
                    if (total > 16L * 1024 * 1024) break;
                }
                out.close();
            }
            return dest.getAbsolutePath();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_IMAGE || resultCode != RESULT_OK || data == null
                || data.getData() == null) return;
        String path = importPickedImage(data.getData());
        if (path == null) {
            Toast.makeText(this, R.string.OxUiNoAgentTitle, Toast.LENGTH_SHORT).show();
            return;
        }
        pendingImagePath = path;
        attachRow.setVisibility(View.VISIBLE);
        Bitmap bmp = decodeScaled(path, dp(240));
        if (bmp != null) attachPreview.setImageBitmap(bmp);
        attachName.setText(new File(path).getName());
        updateSendVisual();
    }

    private Bitmap decodeScaled(String path, int max) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int sample = 1;
            while (o.outWidth / sample > max * 2 || o.outHeight / sample > max * 2) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, o2);
        } catch (Throwable t) {
            return null;
        }
    }

    private void addUserImage(String imagePath, long ts) {
        Bitmap bmp = decodeScaled(imagePath, dp(300));
        if (bmp == null) return;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(0, dp(12), 0, 0);
        ImageView view = new ImageView(this);
        view.setImageBitmap(bmp);
        view.setAdjustViewBounds(true);
        view.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.7f));
        view.setBackground(plate(PAPER, 16, LINE, 1));
        view.setClipToOutline(true);
        row.addView(view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        messages.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addFailed() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, 0);
        View bar = new View(this);
        bar.setBackgroundColor(CORAL);
        row.addView(bar, new LinearLayout.LayoutParams(dp(2), dp(34)));
        TextView text = new TextView(this);
        text.setText(LocaleController.getString(R.string.OxUiNotSent));
        text.setTextSize(12.5f);
        text.setTypeface(sans());
        text.setTextColor(CORAL);
        text.setPadding(dp(10), 0, 0, 0);
        text.setOnClickListener(v -> {
            if (failedText == null && failedImagePath == null) return;
            pendingText = failedText;
            pendingImagePath = failedImagePath;
            pendingAgentId = failedTargetKey;
            pendingAt = System.currentTimeMillis();
            pendingBaseSize = history().size();
            userJustSent = true;
            failedText = null;
            failedImagePath = null;
            failedTargetKey = null;
            lastSignature = "";
            refresh(true);
            dispatchSend(pendingText, pendingImagePath);
        });
        row.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        messages.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void dispatchSend(String text, String imagePath) {
        if (isGroupTarget()) {
            if (imagePath != null) {
                ArrayList<SendMessagesHelper.SendingMediaInfo> infos = new ArrayList<>();
                SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
                info.path = imagePath;
                info.caption = text == null ? "" : text;
                infos.add(info);
                OxAgentsBridge.handleOutgoingGroupMedia(targetDialogId(), infos);
            } else {
                OxAgentsBridge.sendLocalGroupText(targetDialogId(), text, null);
            }
            return;
        }
        if (selected == null || selected.tgId == 0) return;
        if (imagePath != null) {
            ArrayList<SendMessagesHelper.SendingMediaInfo> infos = new ArrayList<>();
            SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
            info.path = imagePath;
            info.caption = text == null ? "" : text;
            infos.add(info);
            OxAgentsBridge.handleOutgoingMedia(selected.tgId, infos);
        } else {
            OxAgentsBridge.sendFromOxUi(selected, text);
        }
    }

    private void openTelegramUi() {
        if (rootMode) {
            OxMode.setInterfaceMode(OxMode.IFACE_TELEGRAM);
            Intent intent = new Intent(this, LaunchActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }
        finish();
    }

    private boolean isGroupTarget() {
        return selectedChatId != 0;
    }

    private long targetDialogId() {
        return selectedChatId == 0 ? (selected == null ? 0 : selected.tgId) : -selectedChatId;
    }

    private String targetKey() {
        return isGroupTarget() ? "g" + selectedChatId : (selected == null ? "" : selected.id);
    }

    private String targetName() {
        if (isGroupTarget()) return selectedGroupTitle;
        return selected == null || selected.name == null ? "Agent" : selected.name;
    }

    private void copy(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("OxUI", text));
            Toast.makeText(this, R.string.OxUiCopyDone, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    private void hideOverlay() {
        hideKeyboard();
        if (overlay != null) {
            overlay.setVisibility(View.GONE);
            overlay.removeAllViews();
        }
    }

    private void showAgentPanel() {
        if (overlay == null) return;
        overlay.removeAllViews();
        overlay.setVisibility(View.VISIBLE);
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(160).start();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(BONE);
        panel.setPadding(dp(22), dp(18), dp(22), dp(18));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText(LocaleController.getString(R.string.OxUiAgents));
        title.setTextSize(30);
        title.setTypeface(serif());
        title.setTextColor(INK);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(26);
        close.setTextColor(INK);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close");
        close.setOnClickListener(v -> hideOverlay());
        head.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        panel.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint(LocaleController.getString(R.string.OxUiSearchAgents));
        search.setHintTextColor(MUTED);
        search.setTextColor(INK);
        search.setTextSize(14.5f);
        search.setTypeface(sans());
        search.setBackground(plate(PAPER, 18, LINE, 1));
        search.setPadding(dp(14), dp(9), dp(14), dp(9));
        search.setText(panelQuery);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        slp.topMargin = dp(16);
        panel.addView(search, slp);

        ScrollView listScroll = new ScrollView(this);
        listScroll.setClipToPadding(false);
        listScroll.setPadding(0, dp(10), 0, 0);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(listScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        panel.addView(sectionRule(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, dp(12), 0, 0);
        TextView hint = new TextView(this);
        hint.setText(LocaleController.getString(R.string.OxUiAgentsHint));
        hint.setTextSize(11);
        hint.setTypeface(sans());
        hint.setTextColor(MUTED);
        footer.addView(hint, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView telegram = new TextView(this);
        telegram.setText(LocaleController.getString(R.string.OxUiOpenTelegram));
        telegram.setTextSize(11.5f);
        telegram.setTypeface(sans());
        telegram.setTextColor(INK);
        telegram.setOnClickListener(v -> openTelegramUi());
        footer.addView(telegram);
        panel.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        overlay.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Runnable render = () -> renderAgentList(list, search.getText().toString().trim());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                panelQuery = s.toString();
                render.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        render.run();
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            View focus = getCurrentFocus();
            if (imm != null && focus != null) {
                imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            }
        } catch (Throwable ignored) {
        }
    }

    private View sectionRule() {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        return v;
    }

    private void renderAgentList(LinearLayout list, String query) {
        list.removeAllViews();
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        int shown = 0;
        for (OxAgent a : agents) {
            String name = a.name == null ? "" : a.name;
            String remark = a.remark == null ? "" : a.remark;
            if (!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q)
                    && !remark.toLowerCase(Locale.ROOT).contains(q)) continue;
            shown++;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(13), dp(4), dp(13));
            row.setBackground(new android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(0x1814211C), null, null));
            TextView badge = new TextView(this);
            badge.setText(a.emoji == null || a.emoji.isEmpty() ? "◐" : a.emoji);
            badge.setTextSize(18);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(plate(PAPER, 20, LINE, 1));
            row.addView(badge, new LinearLayout.LayoutParams(dp(38), dp(38)));
            LinearLayout text = new LinearLayout(this);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(dp(13), 0, dp(8), 0);
            TextView nameView = new TextView(this);
            nameView.setText(name);
            nameView.setTextSize(16);
            nameView.setTypeface(sans());
            nameView.setTextColor(INK);
            text.addView(nameView);
            TextView sub = new TextView(this);
            sub.setText(agentSubtitle(a));
            sub.setTextSize(11.5f);
            sub.setTypeface(sans());
            sub.setTextColor(MUTED);
            sub.setMaxLines(1);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(sub);
            row.addView(text, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView more = new TextView(this);
            more.setText("›");
            more.setTextSize(20);
            more.setTextColor(MUTED);
            more.setGravity(Gravity.CENTER);
            row.addView(more, new LinearLayout.LayoutParams(dp(28), dp(38)));
            row.setOnClickListener(v -> {
                selected = a;
                pendingText = null;
                pendingAgentId = null;
                lastSignature = "";
                hideOverlay();
                renderAgents();
                refresh(true);
            });
            row.setOnLongClickListener(v -> {
                showAgentDetail(a);
                return true;
            });
            list.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            View line = sectionRule();
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1);
            llp.leftMargin = dp(51);
            list.addView(line, llp);
        }
        renderGroupList(list, query);
        if (shown == 0 && groupItems.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(LocaleController.getString(R.string.OxUiNoResults));
            empty.setTextSize(13.5f);
            empty.setTypeface(sans());
            empty.setTextColor(MUTED);
            empty.setPadding(dp(4), dp(24), dp(4), 0);
            list.addView(empty);
        }
    }

    private String agentSubtitle(OxAgent a) {
        String harness = OxHarnessAdapter.label(a.harness);
        String remark = a.remark == null ? "" : a.remark.trim();
        if (!remark.isEmpty()) return harness + "  ·  " + remark;
        java.util.List<OxChatEntry> chat = OxAgentStore.loadChat(a.id);
        if (!chat.isEmpty()) {
            OxChatEntry last = chat.get(chat.size() - 1);
            if (last != null && last.content != null && !last.content.isEmpty()) {
                String text = last.content.replace('\n', ' ')
                        .replace("**", "").replace("`", "").trim();
                return harness + "  ·  " + (text.length() > 46 ? text.substring(0, 46) + "…" : text);
            }
        }
        return harness;
    }

    private void showAgentDetail(OxAgent a) {
        if (overlay == null) return;
        hideKeyboard();
        overlay.removeAllViews();
        overlay.setVisibility(View.VISIBLE);
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(160).start();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.TOP);
        panel.setBackgroundColor(BONE);
        panel.setPadding(dp(22), dp(18), dp(22), dp(18));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("←");
        back.setTextSize(24);
        back.setTextColor(INK);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> showAgentPanel());
        head.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(26);
        close.setTextColor(INK);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> hideOverlay());
        head.addView(new View(this), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        panel.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        TextView badge = new TextView(this);
        badge.setText(a.emoji == null || a.emoji.isEmpty() ? "◐" : a.emoji);
        badge.setTextSize(25);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(plate(PAPER, 30, LINE, 1));
        panel.addView(badge, new LinearLayout.LayoutParams(dp(58), dp(58)));

        TextView name = new TextView(this);
        name.setText(a.name == null ? "Agent" : a.name);
        name.setTextSize(29);
        name.setTypeface(serif());
        name.setTextColor(INK);
        name.setPadding(0, dp(12), 0, 0);
        panel.addView(name);
        TextView meta = new TextView(this);
        meta.setText(OxHarnessAdapter.label(a.harness)
                + (a.harnessModel == null || a.harnessModel.isEmpty() ? "" : "  ·  " + a.harnessModel));
        meta.setTextSize(11.5f);
        meta.setTypeface(mono());
        meta.setTextColor(MUTED);
        meta.setPadding(0, dp(4), 0, 0);
        panel.addView(meta);
        if (a.remark != null && !a.remark.isEmpty()) {
            TextView remark = new TextView(this);
            remark.setText(a.remark);
            remark.setTextSize(13.5f);
            remark.setTypeface(sans());
            remark.setTextColor(INK);
            remark.setLineSpacing(dp(3), 1f);
            remark.setPadding(0, dp(12), 0, 0);
            panel.addView(remark);
        }

        TextView memoryTitle = new TextView(this);
        memoryTitle.setText(LocaleController.getString(R.string.OxUiMemory) + "  ·  "
                + OxMemoryStore.stats(a.id));
        memoryTitle.setTextSize(11);
        memoryTitle.setTypeface(mono());
        memoryTitle.setTextColor(MUTED);
        memoryTitle.setPadding(0, dp(20), 0, 0);
        panel.addView(memoryTitle);
        TextView memory = new TextView(this);
        String recent = OxMemoryStore.recent(a.id, 8);
        memory.setText(recent.isEmpty()
                ? LocaleController.getString(R.string.OxUiNoMemory)
                : recent);
        memory.setTextSize(12.5f);
        memory.setTypeface(sans());
        memory.setTextColor(INK);
        memory.setLineSpacing(dp(3), 1f);
        memory.setPadding(dp(12), dp(12), dp(12), dp(12));
        memory.setMaxLines(12);
        memory.setEllipsize(TextUtils.TruncateAt.END);
        memory.setBackground(plate(PAPER, 12, LINE, 1));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(8);
        panel.addView(memory, mlp);

        TextView chat = new TextView(this);
        chat.setText(LocaleController.getString(R.string.OxUiChat));
        chat.setTextSize(13.5f);
        chat.setTypeface(sans());
        chat.setTextColor(BONE);
        chat.setGravity(Gravity.CENTER);
        chat.setPadding(dp(18), dp(11), dp(18), dp(11));
        chat.setBackground(plate(INK, 20, 0, 0));
        chat.setOnClickListener(v -> {
            selected = a;
            pendingText = null;
            pendingAgentId = null;
            lastSignature = "";
            hideOverlay();
            renderAgents();
            refresh(true);
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(18);
        panel.addView(chat, clp);

        overlay.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void loadGroupsAsync() {
        new Thread(() -> {
            List<GroupItem> out = new ArrayList<>();
            try {
                for (long chatId : OxGroupStore.chatIds()) {
                    if (!OxGroupStore.isLocallyCreated(chatId)) continue;
                    GroupItem item = new GroupItem();
                    item.chatId = chatId;
                    item.agents = OxGroupStore.agents(chatId).size();
                    TLRPC.Chat chat = MessagesController.getInstance(0).getChat(chatId);
                    item.title = chat != null && !TextUtils.isEmpty(chat.title)
                            ? chat.title : ("Group " + chatId);
                    out.add(item);
                }
            } catch (Throwable ignored) {
            }
            List<GroupItem> finalOut = out;
            runOnUiThread(() -> {
                groupItems.clear();
                groupItems.addAll(finalOut);
            });
        }, "oxui-groups").start();
    }

    private void selectGroup(GroupItem item) {
        selected = null;
        selectedChatId = item.chatId;
        selectedGroupTitle = item.title;
        pendingText = null;
        pendingAgentId = null;
        lastSignature = "";
        hideOverlay();
        renderAgents();
        refresh(true);
    }

    private void renderGroupList(LinearLayout list, String query) {
        list.removeAllViews();
        if (groupItems.isEmpty()) return;
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<GroupItem> shown = new ArrayList<>();
        for (GroupItem item : groupItems) {
            String title = item.title == null ? "" : item.title;
            if (!q.isEmpty() && !title.toLowerCase(Locale.ROOT).contains(q)) continue;
            shown.add(item);
        }
        if (shown.isEmpty()) return;
        TextView header = new TextView(this);
        header.setText(LocaleController.getString(R.string.OxUiGroups));
        header.setTextSize(11);
        header.setTypeface(mono());
        header.setTextColor(MUTED);
        header.setLetterSpacing(0.06f);
        header.setPadding(dp(4), dp(18), dp(4), dp(6));
        list.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (GroupItem item : shown) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(13), dp(4), dp(13));
            row.setBackground(new android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(0x1814211C), null, null));
            TextView badge = new TextView(this);
            badge.setText("👥");
            badge.setTextSize(16);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(plate(PAPER, 20, LINE, 1));
            row.addView(badge, new LinearLayout.LayoutParams(dp(38), dp(38)));
            LinearLayout text = new LinearLayout(this);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(dp(13), 0, dp(8), 0);
            TextView name = new TextView(this);
            name.setText(item.title);
            name.setTextSize(16);
            name.setTypeface(sans());
            name.setTextColor(INK);
            text.addView(name);
            TextView sub = new TextView(this);
            sub.setText(LocaleController.formatString("OxUiGroupMembers",
                    R.string.OxUiGroupMembers, item.agents));
            sub.setTextSize(11.5f);
            sub.setTypeface(sans());
            sub.setTextColor(MUTED);
            text.addView(sub);
            row.addView(text, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView more = new TextView(this);
            more.setText("›");
            more.setTextSize(20);
            more.setTextColor(MUTED);
            more.setGravity(Gravity.CENTER);
            row.addView(more, new LinearLayout.LayoutParams(dp(28), dp(38)));
            row.setOnClickListener(v -> selectGroup(item));
            list.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            View line = sectionRule();
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1);
            llp.leftMargin = dp(51);
            list.addView(line, llp);
        }
    }

    private void loadAgents() {
        agents.clear();
        for (OxAgent a : OxAgentStore.listAgents()) {
            if (a != null && a.tgId != 0) agents.add(a);
        }
        if (agents.isEmpty()) {
            for (OxAgent a : OxAgentStore.listAgents()) {
                if (a != null) agents.add(a);
            }
        }
        selected = null;
        for (OxAgent a : agents) {
            if (a.pinned) { selected = a; break; }
        }
        if (selected == null && !agents.isEmpty()) selected = agents.get(0);
        renderAgents();
        refresh(true);
    }

    private void renderAgents() {
        agentRail.removeAllViews();
        for (OxAgent a : agents) {
            boolean active = selected != null && a.id.equals(selected.id);
            TextView chip = new TextView(this);
            String label = (a.emoji == null || a.emoji.isEmpty() ? "" : a.emoji + "  ")
                    + (a.name == null ? "Agent" : a.name);
            chip.setText(label);
            chip.setTextSize(13);
            chip.setTypeface(active ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : sans());
            chip.setTextColor(active ? BONE : INK);
            chip.setGravity(Gravity.CENTER);
            chip.setMaxWidth(dp(190));
            chip.setSingleLine(true);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setPadding(dp(14), dp(9), dp(14), dp(9));
            chip.setBackground(active ? plate(INK, 20, 0, 0)
                    : plate(0, 20, LINE, 1));
            chip.setOnClickListener(v -> {
                selected = a;
                pendingText = null;
                pendingAgentId = null;
                lastSignature = "";
                renderAgents();
                refresh(true);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            agentRail.addView(chip, lp);
        }
    }

    private List<OxChatEntry> history() {
        if (isGroupTarget()) return OxGroupStore.loadChat(selectedChatId);
        if (selected == null) return new ArrayList<>();
        return OxAgentStore.loadChat(selected.id);
    }

    private void refresh(boolean force) {
        if (selected == null && !isGroupTarget()) {
            renderEmpty(LocaleController.getString(R.string.OxUiNoAgentTitle),
                    LocaleController.getString(R.string.OxUiNoAgentBody));
            return;
        }
        List<OxChatEntry> chat = history();
        String key = targetKey();
        if (pendingText != null && pendingAgentId != null && pendingAgentId.equals(key)) {
            if (chat.size() > pendingBaseSize && containsUser(chat, pendingText, pendingBaseSize)) {
                pendingText = null;
                pendingAgentId = null;
                failedText = null;
                failedImagePath = null;
                failedTargetKey = null;
            } else if (System.currentTimeMillis() - pendingAt > 120_000) {
                failedText = pendingText;
                failedImagePath = pendingImagePath;
                failedTargetKey = key;
                pendingText = null;
                pendingAgentId = null;
                pendingImagePath = null;
                updateSendVisual();
            }
        }
        String sig = signature(chat) + "|"
                + (pendingText == null ? "" : "pending:" + pendingText)
                + "|" + (failedText == null || !key.equals(failedTargetKey) ? "" : "failed");
        if (!force && sig.equals(lastSignature)) return;
        lastSignature = sig;
        renderConversation(chat);
    }

    private boolean containsUser(List<OxChatEntry> chat, String text, int from) {
        for (int i = Math.max(0, from); i < chat.size(); i++) {
            OxChatEntry e = chat.get(i);
            if (e != null && "user".equals(e.role) && text.equals(e.content)) return true;
        }
        return false;
    }

    private String signature(List<OxChatEntry> chat) {
        if (chat.isEmpty()) return "0";
        OxChatEntry last = chat.get(chat.size() - 1);
        return chat.size() + ":" + (last == null ? "" : last.role)
                + ":" + (last == null || last.content == null ? 0 : last.content.hashCode())
                + ":" + (last == null || last.imagePath == null ? "" : last.imagePath);
    }

    private void renderEmpty(String title, String body) {
        messages.removeAllViews();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(54), 0, 0);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(26);
        t.setTypeface(serif());
        t.setTextColor(INK);
        t.setLineSpacing(dp(2), 1f);
        box.addView(t);
        TextView b = new TextView(this);
        b.setText(body);
        b.setTextSize(14.5f);
        b.setTypeface(sans());
        b.setTextColor(MUTED);
        b.setLineSpacing(dp(4), 1.05f);
        b.setPadding(0, dp(12), dp(24), 0);
        box.addView(b);
        messages.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void renderConversation(List<OxChatEntry> chat) {
        messages.removeAllViews();
        if (chat.isEmpty() && pendingText == null) {
            renderEmpty(LocaleController.getString(R.string.OxUiEmptyTitle),
                    LocaleController.getString(R.string.OxUiEmptyBody));
            return;
        }
        int start = Math.max(0, chat.size() - 80);
        for (int i = start; i < chat.size(); i++) {
            OxChatEntry e = chat.get(i);
            if (e == null) continue;
            boolean hasText = e.content != null && !e.content.trim().isEmpty();
            boolean hasImage = e.imagePath != null && new File(e.imagePath).isFile();
            if (!hasText && !hasImage) continue;
            if ("user".equals(e.role)) {
                addUser(e.content, e.ts, false, hasImage ? e.imagePath : null);
            } else if ("assistant".equals(e.role)) {
                addAssistant(e.content, e.ts);
            } else if ("tool".equals(e.role)) {
                addTool(e.content);
            }
        }
        if (pendingText != null || pendingImagePath != null) {
            addUser(pendingText, System.currentTimeMillis(), true, pendingImagePath);
            addWorking();
        }
        if (failedText != null && targetKey().equals(failedTargetKey)) {
            addFailed();
        }
        final boolean animate = firstRender;
        firstRender = false;
        scroll.post(() -> {
            View content = scroll.getChildAt(0);
            boolean nearBottom = content == null || content.getHeight() - scroll.getHeight()
                    - scroll.getScrollY() < dp(120);
            if (nearBottom || userJustSent) {
                scroll.fullScroll(View.FOCUS_DOWN);
            }
            userJustSent = false;
            if (animate) {
                messages.setAlpha(0f);
                messages.setTranslationY(dp(10));
                messages.animate().alpha(1f).translationY(0f).setDuration(260).start();
            }
        });
    }

    private void addUser(String text, long ts, boolean sending, String imagePath) {
        if (imagePath != null) addUserImage(imagePath, ts);
        if (text == null || text.trim().isEmpty()) return;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(0, dp(12), 0, 0);
        TextView bubble = new TextView(this);
        bubble.setText(styled(text));
        bubble.setTextSize(15.5f);
        bubble.setTypeface(sans());
        bubble.setTextColor(BONE);
        bubble.setLineSpacing(dp(2), 1.05f);
        bubble.setPadding(dp(15), dp(11), dp(15), dp(11));
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.82f));
        GradientDrawable bg = plate(INK, 17, 0, 0);
        if (sending) bg.setAlpha(190);
        bubble.setBackground(bg);
        bubble.setTextIsSelectable(false);
        bubble.setOnLongClickListener(v -> {
            copy(text);
            return true;
        });
        row.addView(bubble, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        messages.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addAssistant(String text, long ts) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(16), 0, 0);

        View accent = new View(this);
        accent.setBackgroundColor(SIGNAL);
        row.addView(accent, new LinearLayout.LayoutParams(dp(2), dp(46)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(13), 0, dp(8), 0);
        TextView meta = new TextView(this);
        meta.setText(targetName() + "  ·  " + time(ts));
        meta.setTextSize(10.5f);
        meta.setTypeface(mono());
        meta.setTextColor(MUTED);
        meta.setLetterSpacing(0.02f);
        body.addView(meta);
        TextView t = new TextView(this);
        t.setText(styled(text));
        t.setTextSize(16.2f);
        t.setTypeface(serif());
        t.setTextColor(INK);
        t.setLineSpacing(dp(4), 1.06f);
        t.setPadding(0, dp(3), 0, 0);
        t.setTextIsSelectable(false);
        t.setOnLongClickListener(v -> {
            copy(text);
            return true;
        });
        body.addView(t);
        row.addView(body, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        messages.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addTool(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(11.2f);
        t.setTypeface(mono());
        t.setTextColor(MUTED);
        t.setMaxLines(4);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        t.setBackground(plate(PAPER, 8, LINE, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        lp.leftMargin = dp(15);
        lp.rightMargin = dp(12);
        messages.addView(t, lp);
    }

    private void addWorking() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(15), dp(13), 0, 0);
        View dot = new View(this);
        dot.setBackground(plate(SIGNAL, 4, 0, 0));
        row.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));
        try {
            float scale = android.provider.Settings.Global.getFloat(getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            if (scale > 0) {
                ObjectAnimator breath = ObjectAnimator.ofFloat(dot, View.ALPHA, 0.25f, 1f);
                breath.setDuration(700);
                breath.setRepeatCount(ValueAnimator.INFINITE);
                breath.setRepeatMode(ValueAnimator.REVERSE);
                breath.start();
            }
        } catch (Throwable ignored) {
        }
        TextView t = new TextView(this);
        t.setText("  " + LocaleController.getString(R.string.OxUiThinking));
        t.setTextSize(11.5f);
        t.setTypeface(sans());
        t.setTextColor(MUTED);
        row.addView(t);
        messages.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Converts the Markdown the agents already use into native spans. */
    private CharSequence styled(String text) {
        if (text == null) return "";
        String html = TextUtils.htmlEncode(text).replace("\n", "<br>");
        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        html = html.replaceAll("`([^`]+)`", "<tt>$1</tt>");
        html = html.replaceAll("(?m)^#{1,6}\\s*(.+)$", "<b>$1</b>");
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
    }

    private String time(long ts) {
        long value = ts > 0 ? ts : System.currentTimeMillis();
        if (value < 10_000_000_000L) value *= 1000L;
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(value));
    }

    private void sendMessage() {
        boolean targetReady = (selected != null && selected.tgId != 0) || isGroupTarget();
        if (!targetReady) {
            Toast.makeText(this, R.string.OxUiNoAgentTitle, Toast.LENGTH_SHORT).show();
            return;
        }
        String text = input.getText() == null ? "" : input.getText().toString().trim();
        if (text.isEmpty() && pendingImagePath == null) return;
        pendingText = text;
        pendingAgentId = targetKey();
        pendingAt = System.currentTimeMillis();
        pendingBaseSize = history().size();
        userJustSent = true;
        lastSignature = "";
        input.setText("");
        attachRow.setVisibility(View.GONE);
        refresh(true);
        dispatchSend(pendingText, pendingImagePath);
        pendingImagePath = null;
        updateSendVisual();
    }
}
