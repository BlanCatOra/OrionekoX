package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ox.OxMode;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BottomPagesView;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Six-step first-run guide, Operit-style but drawn with Telegram's own design language:
 * the same ViewPager + BottomPagesView dots + rounded bottom action as IntroActivity.
 * Re-runnable anytime from Ox Settings.
 */
@SuppressLint("ViewConstructor")
public class OxOnboardingActivity extends BaseFragment {

    private static final int PAGES = 6;
    private static final int PERM_REQUEST = 7301;

    private ViewPager viewPager;
    private BottomPagesView bottomPages;
    private TextView stepText;
    private TextView nextButton;
    private TextView skipButton;
    private TextView skipHint;

    private boolean choseLogin;
    private boolean finished;

    private final List<View> pages = new ArrayList<>();
    // page 5 permission status views
    private LinearLayout permList;

    private static final int IDX_OPEN = 0, IDX_LEGAL = 1, IDX_LOGIN = 2, IDX_AGENT = 3, IDX_PERM = 4, IDX_CONTAINER = 5;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle("");
        actionBar.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        actionBar.setCastShadows(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (viewPager.getCurrentItem() > 0) viewPager.setCurrentItem(viewPager.getCurrentItem() - 1, true);
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

        viewPager = new ViewPager(context);
        viewPager.setAdapter(new PagerAdapter() {
            @Override public int getCount() { return PAGES; }
            @Override public boolean isViewFromObject(View view, Object object) { return view == object; }
            @Override public Object instantiateItem(ViewGroup container, int position) {
                View v = pages.get(position);
                container.addView(v);
                return v;
            }
            @Override public void destroyItem(ViewGroup container, int position, Object object) {
                container.removeView((View) object);
            }
        });
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                bottomPages.setPageOffset(position, positionOffset);
            }
            @Override public void onPageSelected(int position) {
                updateChrome(position);
            }
            @Override public void onPageScrollStateChanged(int state) {
            }
        });
        root.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // step indicator + dots
        LinearLayout chrome = new LinearLayout(context);
        chrome.setOrientation(LinearLayout.VERTICAL);
        chrome.setGravity(Gravity.CENTER_HORIZONTAL);

        bottomPages = new BottomPagesView(context, viewPager, PAGES);
        chrome.addView(bottomPages, LayoutHelper.createLinear(66, 5, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 8));

        stepText = new TextView(context);
        stepText.setTextSize(13);
        stepText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        chrome.addView(stepText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        root.addView(chrome, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 64, 0, 0));

        // bottom action cluster
        LinearLayout bottom = new LinearLayout(context);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);

        nextButton = new TextView(context);
        nextButton.setGravity(Gravity.CENTER);
        nextButton.setTypeface(AndroidUtilities.bold());
        nextButton.setTextSize(15);
        nextButton.setTextColor(0xffffffff);
        nextButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(24),
                getThemedColor(Theme.key_chats_actionBackground), getThemedColor(Theme.key_chats_actionPressedBackground)));
        nextButton.setOnClickListener(v -> onNext());
        bottom.addView(nextButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 0, 0, 8));

        skipButton = new TextView(context);
        skipButton.setGravity(Gravity.CENTER);
        skipButton.setTextSize(14);
        skipButton.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        skipButton.setOnClickListener(v -> finishOnboarding(false));
        bottom.addView(skipButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32, Gravity.CENTER_HORIZONTAL));

        skipHint = new TextView(context);
        skipHint.setGravity(Gravity.CENTER);
        skipHint.setTextSize(12);
        skipHint.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        bottom.addView(skipHint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

        root.addView(bottom, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 24, 0, 24, 20));

        buildPages(context);
        fragmentView = root;
        updateChrome(0);
        return fragmentView;
    }

    // ---------------- pages ----------------

    private View pageWrap(Context context, String title, String body, View extra) {
        ScrollView scroll = new ScrollView(context);
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(AndroidUtilities.dp(26), AndroidUtilities.dp(116), AndroidUtilities.dp(26), AndroidUtilities.dp(200));

        TextView t = new TextView(context);
        t.setText(title);
        t.setTextSize(23);
        t.setTypeface(AndroidUtilities.bold());
        t.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        col.addView(t);

        TextView b = new TextView(context);
        b.setText(body);
        b.setTextSize(15);
        b.setLineSpacing(AndroidUtilities.dp(4), 1f);
        b.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        b.setPadding(0, AndroidUtilities.dp(14), 0, 0);
        col.addView(b);

        if (extra != null) {
            col.addView(extra, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 22, 0, 0));
        }
        scroll.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setFillViewport(true);
        return scroll;
    }

    private TextView optionRow(Context context, String text, Runnable action) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        tv.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),
                getThemedColor(Theme.key_windowBackgroundWhite), getThemedColor(Theme.key_listSelector)));
        tv.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        tv.setOnClickListener(v -> action.run());
        return tv;
    }

    private void buildPages(Context context) {
        // 1. open source + AI coding
        LinearLayout extra1 = new LinearLayout(context);
        extra1.setOrientation(LinearLayout.VERTICAL);
        TextView repo = optionRow(context, LocaleController.getString(R.string.OxObRepoHint), () -> {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/BlanCatOra/OrionekoX"));
                getParentActivity().startActivity(i);
            } catch (Throwable ignored) {
            }
        });
        extra1.addView(repo);
        TextView ai = new TextView(context);
        ai.setText(LocaleController.getString(R.string.OxObAiNote));
        ai.setTextSize(14);
        ai.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        extra1.addView(ai, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 0));
        pages.add(pageWrap(context, LocaleController.getString(R.string.OxObOpenTitle),
                LocaleController.getString(R.string.OxObOpenBody), extra1));

        // 2. legal
        pages.add(pageWrap(context, LocaleController.getString(R.string.OxObLegalTitle),
                LocaleController.getString(R.string.OxObLegalBody), null));

        // 3. login question
        LinearLayout extra3 = new LinearLayout(context);
        extra3.setOrientation(LinearLayout.VERTICAL);
        TextView go = optionRow(context, LocaleController.getString(R.string.OxObLoginGo), () -> {
            choseLogin = true;
            finishOnboarding(true);
        });
        extra3.addView(go);
        TextView later = optionRow(context, LocaleController.getString(R.string.OxObLoginLater), () -> finishOnboarding(false));
        extra3.addView(later, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));
        TextView hint = new TextView(context);
        hint.setText(LocaleController.getString(R.string.OxObLoginHint));
        hint.setTextSize(13);
        hint.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        extra3.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));
        pages.add(pageWrap(context, LocaleController.getString(R.string.OxObLoginTitle), "", extra3));

        // 4. configure agent
        LinearLayout extra4 = new LinearLayout(context);
        extra4.setOrientation(LinearLayout.VERTICAL);
        extra4.addView(optionRow(context, LocaleController.getString(R.string.OxObAgentApi), () -> {
            org.telegram.ox.OxAgent a = new org.telegram.ox.OxAgent();
            a.id = java.util.UUID.randomUUID().toString().substring(0, 8);
            a.name = "API Agent";
            a.emoji = "🤖";
            a.baseUrl = "https://api.deepseek.com";
            a.model = "deepseek-chat";
            a.agentsMd = "# API Agent\n\nYou are a helpful assistant.";
            org.telegram.ox.OxAgentStore.saveAgent(a);
            presentFragment(new OxAgentProfileActivity(a.id));
        }));
        extra4.addView(optionRow(context, LocaleController.getString(R.string.OxObAgentLinux), () ->
                viewPager.setCurrentItem(IDX_CONTAINER, true)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));
        extra4.addView(optionRow(context, LocaleController.getString(R.string.OxObAgentLan), () -> {
            org.telegram.ox.OxAgent a = new org.telegram.ox.OxAgent();
            a.id = java.util.UUID.randomUUID().toString().substring(0, 8);
            a.name = "LAN Agent";
            a.emoji = "🛰";
            a.baseUrl = "http://192.168.1.2:11434/v1";
            a.model = "qwen2.5:7b";
            a.agentsMd = "# LAN Agent\n\nAn agent running on another device on your LAN.";
            org.telegram.ox.OxAgentStore.saveAgent(a);
            presentFragment(new OxAgentProfileActivity(a.id));
        }), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));
        TextView sub = new TextView(context);
        sub.setText(LocaleController.getString(R.string.OxObAgentSubtitle));
        sub.setTextSize(13);
        sub.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        extra4.addView(sub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));
        pages.add(pageWrap(context, LocaleController.getString(R.string.OxObAgentTitle), "", extra4));

        // 5. permissions
        LinearLayout extra5 = new LinearLayout(context);
        extra5.setOrientation(LinearLayout.VERTICAL);
        TextView sub5 = new TextView(context);
        sub5.setText(LocaleController.getString(R.string.OxObPermSubtitle));
        sub5.setTextSize(13);
        sub5.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        extra5.addView(sub5, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));
        permList = new LinearLayout(context);
        permList.setOrientation(LinearLayout.VERTICAL);
        extra5.addView(permList);
        pages.add(pageWrap(context, LocaleController.getString(R.string.OxObPermTitle), "", extra5));

        // 6. container
        LinearLayout extra6 = new LinearLayout(context);
        extra6.setOrientation(LinearLayout.VERTICAL);
        java.util.List<String> mirrorSink = new java.util.ArrayList<String>() {
            @Override public boolean add(String v) { OxMode.setContainerMirror(v); return true; }
        };
        extra6.addView(configRow(context, LocaleController.getString(R.string.OxObContainerMirror), OxMode.getContainerMirror(),
                new String[]{"official", "tuna", "ustc", "aliyun"},
                new String[]{"Ubuntu 官方", "清华 TUNA", "中科大 USTC", "阿里云"},
                mirrorSink, () -> { }));
        java.util.List<String> versionSink = new java.util.ArrayList<String>() {
            @Override public boolean add(String v) { OxMode.setContainerVersion(v); return true; }
        };
        extra6.addView(configRow(context, LocaleController.getString(R.string.OxObContainerVersion), OxMode.getContainerVersion(),
                new String[]{"22.04", "24.04"}, new String[]{"Ubuntu 22.04 LTS", "Ubuntu 24.04 LTS"},
                versionSink, () -> { }), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));
        extra6.addView(componentsRow(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));
        TextView note = new TextView(context);
        note.setText(LocaleController.getString(R.string.OxObContainerNote));
        note.setTextSize(12);
        note.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        extra6.addView(note, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));
        containerExtra = extra6;
        pages.add(pageWrap(context, LocaleController.getString(R.string.OxObContainerTitle), "", extra6));
    }

    private LinearLayout containerExtra;

    private void rebuildContainerPage(Context context, LinearLayout ignored) {
        // status label refresh only; row labels update through display closures
        if (permList != null) {
            // also refresh permission states when page 6 is touched
        }
    }

    private TextView configRow(Context context, String label, String currentValue,
                               String[] values, String[] names, java.util.List<String> valueSink, Runnable onChange) {
        // valueSink receives the chosen value; caller persists it
        TextView tv = new TextView(context);
        tv.setTextSize(15);
        tv.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        tv.setBackground(Theme.getSelectorDrawable(false));
        tv.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(14), AndroidUtilities.dp(4), AndroidUtilities.dp(14));
        final String[] current = {currentValue};
        final Runnable refresh = () -> {
            String name = current[0];
            for (int i = 0; i < values.length; i++) if (values[i].equals(current[0])) name = names[i];
            tv.setText(label + " · " + name);
        };
        refresh.run();
        tv.setOnClickListener(v -> {
            if (getParentActivity() == null) return;
            new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                    .setItems(names, (d, which) -> {
                        current[0] = values[which];
                        if (!valueSink.isEmpty()) {
                            valueSink.clear();
                            valueSink.add(values[which]);
                        }
                        refresh.run();
                        onChange.run();
                    })
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .show();
        });
        return tv;
    }

    private static final String[][] COMPONENTS = {
            {"nodejs", "Node.js"},
            {"python3", "Python 3"},
            {"git", "Git"},
            {"curl", "curl"},
            {"tools", "常用命令行工具"},
    };

    private View componentsRow(Context context) {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context);
        title.setText(LocaleController.getString(R.string.OxObContainerComponents));
        title.setTextSize(13);
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        col.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));
        for (String[] comp : COMPONENTS) {
            final String key = comp[0];
            TextView row = new TextView(context);
            row.setTextSize(15);
            row.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(11), AndroidUtilities.dp(4), AndroidUtilities.dp(11));
            row.setBackground(Theme.getSelectorDrawable(false));
            final Runnable[] refresh = new Runnable[1];
            refresh[0] = () -> {
                String csv = OxMode.getContainerComponents();
                boolean on = ("," + csv + ",").contains("," + key + ",");
                row.setText((on ? "✅  " : "⬜  ") + comp[1]);
            };
            refresh[0].run();
            row.setOnClickListener(v -> {
                String csv = OxMode.getContainerComponents();
                boolean on = ("," + csv + ",").contains("," + key + ",");
                List<String> list = new ArrayList<>();
                for (String p : csv.split(",")) if (!p.isEmpty() && !p.equals(key)) list.add(p);
                if (!on) list.add(key);
                OxMode.setContainerComponents(String.join(",", list));
                refresh[0].run();
            });
            col.addView(row);
        }
        return col;
    }

    // ---------------- permissions (page 5) ----------------

    @Override
    public void onResume() {
        super.onResume();
        if (permList != null) rebuildPermissions();
    }

    private void rebuildPermissions() {
        if (permList == null) return;
        Context context = permList.getContext();
        permList.removeAllViews();
        addPermRow(context, LocaleController.getString(R.string.OxObPermNormal), normalPermStatus(), () -> {
            if (getParentActivity() != null && Build.VERSION.SDK_INT >= 33
                    && getParentActivity().checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                getParentActivity().requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, PERM_REQUEST);
            } else {
                toastPermissionPath();
            }
        });
        addPermRow(context, LocaleController.getString(R.string.OxObPermAccessibility), accessibilityStatus(), () -> {
            try {
                Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                getParentActivity().startActivity(i);
            } catch (Throwable ignored) {
            }
        });
        addPermRow(context, LocaleController.getString(R.string.OxObPermAdb), adbWirelessStatus(), () -> {
            try {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                getParentActivity().startActivity(i);
            } catch (Throwable ignored) {
            }
        });
        addPermRow(context, LocaleController.getString(R.string.OxObPermShizuku), shizukuStatus(), () -> {
            try {
                Intent i = getParentActivity().getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                if (i != null) getParentActivity().startActivity(i);
                else toast("Shizuku: " + LocaleController.getString(R.string.OxObPermNotInstalled));
            } catch (Throwable ignored) {
            }
        });
        addPermRow(context, LocaleController.getString(R.string.OxObPermAdmin), adminStatus(), () -> {
            try {
                android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager)
                        getParentActivity().getSystemService(Context.DEVICE_POLICY_SERVICE);
                android.content.ComponentName cn = new android.content.ComponentName(
                        getParentActivity(), org.telegram.ox.OxDeviceAdminReceiver.class);
                if (dpm != null && !dpm.isAdminActive(cn)) {
                    Intent i = new Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    i.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn);
                    getParentActivity().startActivity(i);
                }
            } catch (Throwable ignored) {
            }
        });
        addPermRow(context, LocaleController.getString(R.string.OxObPermRoot), rootStatus(), this::toastPermissionPath);
    }

    private void addPermRow(Context context, String name, String[] status, Runnable onTap) {
        // status = {text, granted(0/1), detected(0/1)}
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Theme.getSelectorDrawable(false));
        row.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(12));

        TextView title = new TextView(context);
        title.setText(name);
        title.setTextSize(15);
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        row.addView(title, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView st = new TextView(context);
        st.setText(status[0]);
        st.setTextSize(13);
        st.setTypeface(AndroidUtilities.bold());
        int color = "1".equals(status[1]) ? 0xff34c759
                : ("1".equals(status[2]) ? getThemedColor(Theme.key_windowBackgroundWhiteGrayText) : 0xff9e9e9e);
        st.setTextColor(color);
        row.addView(st);

        row.setOnClickListener(v -> {
            if ("1".equals(status[1])) {
                Toast toast = Toast.makeText(getParentActivity(), LocaleController.getString(R.string.OxObPermGranted), Toast.LENGTH_SHORT);
                toast.show();
            } else {
                onTap.run();
            }
        });
        permList.addView(row);
    }

    private String[] normalPermStatus() {
        if (getParentActivity() == null) return new String[]{LocaleController.getString(R.string.OxObPermMissing), "0", "1"};
        boolean ok = Build.VERSION.SDK_INT < 33
                || getParentActivity().checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
        return new String[]{LocaleController.getString(ok ? R.string.OxObPermGranted : R.string.OxObPermMissing), ok ? "1" : "0", "1"};
    }

    private String[] accessibilityStatus() {
        try {
            String enabled = Settings.Secure.getString(getParentActivity().getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            boolean ok = enabled != null && enabled.contains(getParentActivity().getPackageName());
            return new String[]{LocaleController.getString(ok ? R.string.OxObPermGranted : R.string.OxObPermEnableInSystem), ok ? "1" : "0", "1"};
        } catch (Throwable t) {
            return new String[]{LocaleController.getString(R.string.OxObPermNotDetected), "0", "0"};
        }
    }

    private String[] adbWirelessStatus() {
        try {
            boolean adb = Settings.Global.getInt(getParentActivity().getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
            String wifi = Settings.Global.getString(getParentActivity().getContentResolver(), "adb_wifi_enabled");
            boolean wireless = "1".equals(wifi);
            if (wireless) return new String[]{"Wi-Fi ADB · " + LocaleController.getString(R.string.OxObPermGranted), "1", "1"};
            if (adb) return new String[]{"USB ADB · " + LocaleController.getString(R.string.OxObPermGranted), "1", "1"};
            return new String[]{LocaleController.getString(R.string.OxObPermEnableInSystem), "0", "1"};
        } catch (Throwable t) {
            return new String[]{LocaleController.getString(R.string.OxObPermNotDetected), "0", "0"};
        }
    }

    private String[] shizukuStatus() {
        try {
            getParentActivity().getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
            return new String[]{LocaleController.getString(R.string.OxObPermMissing), "0", "1"};
        } catch (Throwable t) {
            return new String[]{LocaleController.getString(R.string.OxObPermNotInstalled), "0", "0"};
        }
    }

    private String[] adminStatus() {
        try {
            android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager)
                    getParentActivity().getSystemService(Context.DEVICE_POLICY_SERVICE);
            boolean ok = dpm != null && dpm.isAdminActive(new android.content.ComponentName(
                    getParentActivity(), org.telegram.ox.OxDeviceAdminReceiver.class));
            return new String[]{LocaleController.getString(ok ? R.string.OxObPermGranted : R.string.OxObPermMissing), ok ? "1" : "0", "1"};
        } catch (Throwable t) {
            return new String[]{LocaleController.getString(R.string.OxObPermNotDetected), "0", "0"};
        }
    }

    private String[] rootStatus() {
        String[] paths = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/magisk/.core/bin/su"};
        for (String p : paths) {
            if (new File(p).exists()) return new String[]{LocaleController.getString(R.string.OxObPermGranted), "1", "1"};
        }
        return new String[]{LocaleController.getString(R.string.OxObPermNotDetected), "0", "0"};
    }

    private void toastPermissionPath() {
        toast(LocaleController.getString(R.string.OxObPermEnableInSystem));
    }

    private void toast(String s) {
        if (getParentActivity() != null) Toast.makeText(getParentActivity(), s, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERM_REQUEST) {
            rebuildPermissions();
        }
    }

    // ---------------- chrome ----------------

    private void updateChrome(int position) {
        if (stepText == null) return;
        boolean last = position == PAGES - 1;
        int left = PAGES - position;
        stepText.setText(last
                ? LocaleController.formatString("OxObStepLast", R.string.OxObStepLast, position + 1, PAGES)
                : LocaleController.formatString("OxObStep", R.string.OxObStep, position + 1, PAGES, left - 1));
        nextButton.setText(LocaleController.getString(position == PAGES - 1 ? R.string.OxObStart : R.string.OxObNext));
        skipButton.setText(position == IDX_LOGIN ? "" : LocaleController.getString(R.string.OxObSkip));
        skipButton.setVisibility(position == IDX_LOGIN || last ? View.GONE : View.VISIBLE);
        skipHint.setText(LocaleController.getString(R.string.OxObSkipHint));
        if (position == IDX_PERM) rebuildPermissions();
        if (actionBar != null) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        }
    }

    private void onNext() {
        int cur = viewPager.getCurrentItem();
        if (cur < PAGES - 1) {
            viewPager.setCurrentItem(cur + 1, true);
        } else {
            finishOnboarding(choseLogin);
        }
    }

    private void finishOnboarding(boolean goLogin) {
        if (finished) return;
        finished = true;
        OxMode.setOnboardingDone(true);
        if (goLogin) {
            presentFragment(new LoginActivity(), true);
        } else {
            OxMode.setAgentOnly(true);
            try {
                org.telegram.ox.OxAgentsBridge.ensureLocalAccount();
            } catch (Throwable ignored) {
            }
            presentFragment(new DialogsActivity(null), true);
        }
    }
}
