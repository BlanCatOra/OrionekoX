package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ox.OxAgent;
import org.telegram.ox.OxAgentStore;
import org.telegram.ox.OxAgentsBridge;
import org.telegram.ox.OxMode;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BottomPagesView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.Switch;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Six-step first-run guide. Native Telegram intro language: white page, big title, dots,
 * one fixed primary button, motion borrowed from IntroActivity (ScaleStateListAnimator,
 * ViewPager parallax/fade, animated dashes). All layout slots are fixed so the primary
 * button never moves between steps.
 */
@SuppressLint("ViewConstructor")
public class OxOnboardingActivity extends BaseFragment {

    private static final int PAGES = 6;
    private static final int PERM_REQUEST = 7301;
    private static final int MENU_CLOSE = 1;

    private static final int IDX_OPEN = 0, IDX_LEGAL = 1, IDX_MODE = 2, IDX_AGENT = 3, IDX_PERM = 4, IDX_CONTAINER = 5;

    private static final int MODE_OX = OxMode.MODE_OX, MODE_LOGIN = OxMode.MODE_LOGIN;

    private ViewPager viewPager;
    private BottomPagesView bottomPages;
    private final View[] progressSegments = new View[PAGES];
    private TextView stepText;
    private TextView hintText;
    private TextView nextButton;
    private TextView exitButton;
    private TextView skipStepButton;

    private final List<View> pages = new ArrayList<>();
    private boolean wasDone;
    private boolean finished;

    private int selectedMode = MODE_OX;
    private boolean legalAccepted;
    private int startPage;

    // page 3
    private TextView agentSummary;
    private View containerOptionRow;
    private View containerOptionCard;
    private View containerOptionDivider;
    private final TextView[] modeChecks = new TextView[2];
    private final View[] modeRows = new View[2];
    private TextView modeWarning;

    // page 5
    private LinearLayout permList;
    private TextView permSummary;
    private final Map<String, Boolean> lastGranted = new HashMap<>();

    // page 6
    private Switch containerSwitch;
    private boolean updatingContainerSwitch;
    private TextView mirrorRow;
    private TextView versionRow;
    private final List<TextView> componentRows = new ArrayList<>();
    private TextView containerNote;

    @Override
    public boolean onFragmentCreate() {
        wasDone = OxMode.isOnboardingDone();
        legalAccepted = OxMode.isLegalAccepted();
        if (getArguments() != null) {
            startPage = getArguments().getInt("start_page", 0);
        }
        int saved = OxMode.getOnboardingMode();
        if (OxMode.isAgentOnly()) {
            selectedMode = MODE_OX;
        } else if (UserConfig.getInstance(0).isClientActivated()) {
            selectedMode = MODE_LOGIN;
        } else {
            selectedMode = saved == MODE_LOGIN ? MODE_LOGIN : MODE_OX;
        }
        return super.onFragmentCreate();
    }

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
                    if (viewPager.getCurrentItem() > 0) {
                        viewPager.setCurrentItem(viewPager.getCurrentItem() - 1, true);
                    } else {
                        confirmExit();
                    }
                } else if (id == MENU_CLOSE) {
                    confirmExit();
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItemWithWidth(MENU_CLOSE, R.drawable.ic_ab_close, AndroidUtilities.dp(56));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

        viewPager = new ViewPager(context);
        viewPager.setOffscreenPageLimit(2);
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
            }
            @Override public void onPageSelected(int position) {
                updateChrome(position);
                animatePageIn(pages.get(position));
            }
            @Override public void onPageScrollStateChanged(int state) {
            }
        });
        viewPager.setPageTransformer(false, (page, position) -> {
            page.setAlpha(1f - Math.min(0.45f, Math.abs(position) * 0.45f));
            page.setTranslationX(-position * AndroidUtilities.dp(26));
            page.setTranslationY(Math.abs(position) * AndroidUtilities.dp(6));
        });
        // Fixed progress header: six segments + the exact step counter. It sits above
        // the pager so the page content is never covered by chrome.
        LinearLayout progressHeader = new LinearLayout(context);
        progressHeader.setOrientation(LinearLayout.VERTICAL);
        progressHeader.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        progressHeader.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10), AndroidUtilities.dp(20), AndroidUtilities.dp(10));
        LinearLayout segments = new LinearLayout(context);
        segments.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < PAGES; i++) {
            View seg = new View(context);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(AndroidUtilities.dp(2));
            bg.setColor(getThemedColor(Theme.key_divider));
            seg.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, AndroidUtilities.dp(3), 1f);
            lp.leftMargin = AndroidUtilities.dp(i == 0 ? 0 : 3);
            lp.rightMargin = AndroidUtilities.dp(i == PAGES - 1 ? 0 : 3);
            segments.addView(seg, lp);
            progressSegments[i] = seg;
        }
        progressHeader.addView(segments, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 3));
        stepText = new TextView(context);
        stepText.setTextSize(12);
        stepText.setGravity(Gravity.CENTER);
        stepText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        stepText.setPadding(0, AndroidUtilities.dp(8), 0, 0);
        progressHeader.addView(stepText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(progressHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        root.addView(viewPager, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));


        // Fixed bottom action area: hint (fixed), secondary left/right slots (fixed),
        // primary button (fixed). Nothing uses GONE, so positions never change.
        FrameLayout bottomBar = new FrameLayout(context);
        bottomBar.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        bottomBar.setElevation(AndroidUtilities.dp(8));
        View divider = new View(context);
        divider.setBackgroundColor(getThemedColor(Theme.key_divider));
        bottomBar.addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.TOP));

        LinearLayout bottomCol = new LinearLayout(context);
        bottomCol.setOrientation(LinearLayout.VERTICAL);
        int nav = Math.max(AndroidUtilities.navigationBarHeight, AndroidUtilities.dp(8));
        bottomCol.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(10), AndroidUtilities.dp(24), nav);

        hintText = new TextView(context);
        hintText.setTextSize(12);
        hintText.setGravity(Gravity.CENTER);
        hintText.setLineSpacing(AndroidUtilities.dp(2), 1f);
        hintText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        hintText.setText(LocaleController.getString(R.string.OxObSkipHint));
        hintText.setMaxLines(2);
        bottomCol.addView(hintText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 32));

        FrameLayout secondary = new FrameLayout(context);
        exitButton = new TextView(context);
        exitButton.setTextSize(14);
        exitButton.setGravity(Gravity.CENTER);
        exitButton.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        exitButton.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(12), 0);
        exitButton.setText(LocaleController.getString(R.string.OxObExit));
        exitButton.setOnClickListener(v -> confirmExit());
        secondary.addView(exitButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT,
                Gravity.LEFT | Gravity.CENTER_VERTICAL));

        skipStepButton = new TextView(context);
        skipStepButton.setTextSize(14);
        skipStepButton.setGravity(Gravity.CENTER);
        skipStepButton.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        skipStepButton.setTypeface(AndroidUtilities.bold());
        skipStepButton.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(4), 0);
        skipStepButton.setText(LocaleController.getString(R.string.OxObSkipStep));
        skipStepButton.setOnClickListener(v -> {
            if (viewPager.getCurrentItem() >= PAGES - 1) {
                finishOnboarding();
            } else {
                viewPager.setCurrentItem(viewPager.getCurrentItem() + 1, true);
            }
        });
        secondary.addView(skipStepButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT,
                Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        bottomCol.addView(secondary, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 34));

        nextButton = new TextView(context);
        nextButton.setGravity(Gravity.CENTER);
        nextButton.setTypeface(AndroidUtilities.bold());
        nextButton.setTextSize(15);
        nextButton.setTextColor(0xffffffff);
        GradientDrawable buttonBackground = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{getThemedColor(Theme.key_featuredStickers_addButton), getThemedColor(Theme.key_featuredStickers_addButton2)});
        buttonBackground.setCornerRadius(AndroidUtilities.dp(26));
        nextButton.setBackground(buttonBackground);
        ScaleStateListAnimator.apply(nextButton, .02f, 1.2f);
        nextButton.setOnClickListener(v -> onNext());
        bottomCol.addView(nextButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52, 0, 10, 0, 0));

        bottomBar.addView(bottomCol, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
        View scrollFade = new View(context);
        GradientDrawable fadeBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00EFEFF4, 0xFFFFFFFF});
        scrollFade.setBackground(fadeBg);
        root.addView(scrollFade, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 14));
        root.addView(bottomBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        buildPages(context);
        fragmentView = root;
        updateChrome(0);
        if (startPage > 0 && startPage < PAGES) {
            final int target = startPage;
            viewPager.post(() -> viewPager.setCurrentItem(target, false));
        }
        return fragmentView;
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (viewPager != null && viewPager.getCurrentItem() > 0) {
            viewPager.setCurrentItem(viewPager.getCurrentItem() - 1, true);
            return false;
        }
        if (!wasDone) {
            confirmExit();
            return false;
        }
        return true;
    }

    // ---------------- pages ----------------

    private View pageWrap(Context context, int iconRes, String title, String body, View extra) {
        return pageWrap(context, iconRes, getThemedColor(Theme.key_chats_actionBackground), title, body, extra);
    }

    private View pageWrap(Context context, int iconRes, int iconTint, String title, String body, View extra) {
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(14), AndroidUtilities.dp(20), AndroidUtilities.dp(24));

        if (iconRes != 0) {
            FrameLayout hero = new FrameLayout(context);
            hero.setTag("ox_hero");
            View glow = new View(context);
            GradientDrawable glowBg = new GradientDrawable();
            glowBg.setShape(GradientDrawable.OVAL);
            glowBg.setColor(iconRes == R.drawable.ox_brand
                    ? 0x66F3E9D2
                    : Color.argb(24, Color.red(iconTint), Color.green(iconTint), Color.blue(iconTint)));
            glow.setBackground(glowBg);
            hero.addView(glow, LayoutHelper.createFrame(116, 116, Gravity.CENTER));

            FrameLayout iconWrap = new FrameLayout(context);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
            iconWrap.setBackground(circle);
            iconWrap.setElevation(AndroidUtilities.dp(2));
            ImageView icon = new ImageView(context);
            icon.setImageResource(iconRes);
            if (iconRes == R.drawable.ox_brand) {
                icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                icon.setClipToOutline(true);
                icon.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline outline) {
                        outline.setOval(0, 0, view.getWidth(), view.getHeight());
                    }
                });
                iconWrap.addView(icon, LayoutHelper.createFrame(84, 84, Gravity.CENTER));
                hero.addView(iconWrap, LayoutHelper.createFrame(84, 84, Gravity.CENTER));
            } else {
                icon.setColorFilter(iconTint);
                iconWrap.addView(icon, LayoutHelper.createFrame(32, 32, Gravity.CENTER));
                hero.addView(iconWrap, LayoutHelper.createFrame(68, 68, Gravity.CENTER));
            }
            col.addView(hero, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 128));
        }

        TextView t = new TextView(context);
        t.setText(title);
        t.setTextSize(28);
        t.setTypeface(AndroidUtilities.bold());
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(AndroidUtilities.dp(3), 1f);
        t.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        col.addView(t, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                0, iconRes != 0 ? 12 : 8, 0, 0));

        TextView b = new TextView(context);
        b.setText(body);
        b.setTextSize(15);
        b.setLineSpacing(AndroidUtilities.dp(6), 1f);
        b.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        b.setPadding(0, AndroidUtilities.dp(12), 0, 0);
        col.addView(b, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (extra != null) {
            col.addView(extra, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 0));
        }
        scroll.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setFillViewport(true);
        return scroll;
    }

    private TextView pill(Context context, CharSequence text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
        bg.setCornerRadius(AndroidUtilities.dp(14));
        tv.setBackground(bg);
        tv.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
        return tv;
    }

    private LinearLayout sectionList(Context context) {
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
        bg.setCornerRadius(AndroidUtilities.dp(16));
        list.setBackground(bg);
        list.setClipToOutline(true);
        list.setElevation(AndroidUtilities.dp(1));
        list.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        return list;
    }

    private View divider(Context context) {
        View v = new View(context);
        v.setBackgroundColor(getThemedColor(Theme.key_divider));
        LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1);
        lp.leftMargin = AndroidUtilities.dp(74);
        v.setLayoutParams(lp);
        return v;
    }

    private void buildPages(Context context) {
        // 1. open source
        LinearLayout extra1 = new LinearLayout(context);
        extra1.setOrientation(LinearLayout.VERTICAL);
        LinearLayout repoCard = sectionList(context);
        repoCard.addView(optionRow(context, R.drawable.msg_link, 0xff2AABEE,
                        LocaleController.getString(R.string.OxObRepoTitle),
                        LocaleController.getString(R.string.OxObRepoOpening), false, () -> {
                            try {
                                if (getParentActivity() != null) getParentActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/BlanCatOra/OrionekoX")));
                            } catch (Throwable ignored) {
                            }
                        }),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        extra1.addView(repoCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextView ai = new TextView(context);
        ai.setText(LocaleController.getString(R.string.OxObAiNote));
        ai.setTextSize(13);
        ai.setLineSpacing(AndroidUtilities.dp(4), 1f);
        ai.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        ai.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(16), AndroidUtilities.dp(6), 0);
        extra1.addView(ai, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        pages.add(pageWrap(context, R.drawable.ox_brand, 0xff3A3A3C, LocaleController.getString(R.string.OxObOpenTitle),
                LocaleController.getString(R.string.OxObOpenBody), extra1));

        // 2. legal
        LinearLayout extra2 = new LinearLayout(context);
        extra2.setOrientation(LinearLayout.VERTICAL);
        LinearLayout legalCard = sectionList(context);
        TextView legalBody = new TextView(context);
        legalBody.setText(LocaleController.getString(R.string.OxObLegalBody));
        legalBody.setTextSize(14);
        legalBody.setLineSpacing(AndroidUtilities.dp(5), 1f);
        legalBody.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        legalBody.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        legalCard.addView(legalBody, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        extra2.addView(legalCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout consentCard = sectionList(context);
        LinearLayout consent = new LinearLayout(context);
        consent.setOrientation(LinearLayout.HORIZONTAL);
        consent.setGravity(Gravity.CENTER_VERTICAL);
        consent.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        consent.setBackground(Theme.getSelectorDrawable(false));
        final TextView consentCheck = makeCheck(context);
        consent.addView(consentCheck, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL));
        TextView consentText = new TextView(context);
        consentText.setText(LocaleController.getString(R.string.OxObLegalAgree));
        consentText.setTextSize(15);
        consentText.setPadding(AndroidUtilities.dp(12), 0, 0, 0);
        consentText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        consent.addView(consentText, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        setCheck(consentCheck, legalAccepted, false);
        consent.setOnClickListener(v -> {
            legalAccepted = !legalAccepted;
            OxMode.setLegalAccepted(legalAccepted);
            setCheck(consentCheck, legalAccepted, true);
            updateNextState();
        });
        consentCard.addView(consent, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        extra2.addView(consentCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));
        pages.add(pageWrap(context, R.drawable.msg_info, LocaleController.getString(R.string.OxObLegalTitle), "", extra2));

        // 3. start mode
        LinearLayout extra3 = new LinearLayout(context);
        extra3.setOrientation(LinearLayout.VERTICAL);
        LinearLayout modeCard = sectionList(context);
        modeRows[0] = optionRow(context, R.drawable.msg_language, 0xff2AABEE,
                LocaleController.getString(R.string.OxObModeTgTitle),
                LocaleController.getString(R.string.OxObModeTgSub), true, () -> selectMode(MODE_LOGIN));
        View r0 = modeRows[0];
        View r1 = optionRow(context, R.drawable.msg_online, 0xff34C759,
                LocaleController.getString(R.string.OxObModeOxTitle),
                LocaleController.getString(R.string.OxObModeOxSub), true, () -> selectMode(MODE_OX));
        modeRows[1] = r1;
        modeCard.addView(r0, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        modeCard.addView(divider(context));
        modeCard.addView(r1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        extra3.addView(modeCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        modeWarning = new TextView(context);
        modeWarning.setTextSize(13);
        modeWarning.setLineSpacing(AndroidUtilities.dp(4), 1f);
        modeWarning.setTextColor(getThemedColor(Theme.key_text_RedRegular));
        modeWarning.setVisibility(View.GONE);
        modeWarning.setPadding(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6), 0);
        extra3.addView(modeWarning, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                0, 14, 0, 0));
        TextView modeHint = new TextView(context);
        modeHint.setText(LocaleController.getString(R.string.OxObLoginHint));
        modeHint.setTextSize(13);
        modeHint.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        modeHint.setPadding(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6), 0);
        extra3.addView(modeHint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                0, 12, 0, 0));
        pages.add(pageWrap(context, R.drawable.msg_language, LocaleController.getString(R.string.OxObLoginTitle), "", extra3));
        updateModeChecks(false);

        // 4. agent setup
        LinearLayout extra4 = new LinearLayout(context);
        extra4.setOrientation(LinearLayout.VERTICAL);
        agentSummary = pill(context, "");
        extra4.addView(agentSummary, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT));
        LinearLayout apiCard = sectionList(context);
        apiCard.addView(optionRow(context, R.drawable.msg_link, 0xff2AABEE,
                LocaleController.getString(R.string.OxObAgentApiTitle),
                LocaleController.getString(R.string.OxObAgentApiSub), false, () ->
                        presentFragment(OxAgentProfileActivity.newDraft(OxAgentProfileActivity.TEMPLATE_API))),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        extra4.addView(apiCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        containerOptionCard = sectionList(context);
        containerOptionRow = optionRow(context, R.drawable.msg_download, 0xff8E44AD,
                LocaleController.getString(R.string.OxObAgentLinuxTitle),
                LocaleController.getString(R.string.OxObAgentLinuxSub), false, () -> {
                    OxMode.setContainerEnabled(true);
                    updateContainerState();
                    updateModeChecks(true);
                    toast(LocaleController.getString(R.string.OxObContainerEnabled));
                });
        ((LinearLayout) containerOptionCard).addView(containerOptionRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        extra4.addView(containerOptionCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        pages.add(pageWrap(context, R.drawable.input_ai_star, LocaleController.getString(R.string.OxObAgentTitle), "", extra4));
        updateAgentOptions();

        // 5. permissions
        LinearLayout extra5 = new LinearLayout(context);
        extra5.setOrientation(LinearLayout.VERTICAL);
        permSummary = pill(context, "");
        extra5.addView(permSummary, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT));
        TextView sub5 = new TextView(context);
        sub5.setText(LocaleController.getString(R.string.OxObPermSubtitle));
        sub5.setTextSize(13);
        sub5.setLineSpacing(AndroidUtilities.dp(3), 1f);
        sub5.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        sub5.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(6), 0);
        extra5.addView(sub5, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        permList = new LinearLayout(context);
        permList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout permCard = sectionList(context);
        permCard.addView(permList, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        extra5.addView(permCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));
        pages.add(pageWrap(context, R.drawable.msg_permissions, LocaleController.getString(R.string.OxObPermTitle), "", extra5));

        // 6. container
        LinearLayout extra6 = new LinearLayout(context);
        extra6.setOrientation(LinearLayout.VERTICAL);

        LinearLayout enableCard = sectionList(context);
        LinearLayout enableRow = new LinearLayout(context);
        enableRow.setOrientation(LinearLayout.HORIZONTAL);
        enableRow.setGravity(Gravity.CENTER_VERTICAL);
        enableRow.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        enableRow.setBackground(Theme.getSelectorDrawable(false));
        LinearLayout enableText = new LinearLayout(context);
        enableText.setOrientation(LinearLayout.VERTICAL);
        TextView enableTitle = new TextView(context);
        enableTitle.setText(LocaleController.getString(R.string.OxObContainerEnable));
        enableTitle.setTextSize(16);
        enableTitle.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        enableText.addView(enableTitle);
        TextView enableSub = new TextView(context);
        enableSub.setText(LocaleController.getString(R.string.OxObContainerEnableSub));
        enableSub.setTextSize(13);
        enableSub.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        enableText.addView(enableSub);
        enableRow.addView(enableText, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        containerSwitch = new Switch(context);
        containerSwitch.setChecked(OxMode.isContainerEnabled(), false);
        containerSwitch.setOnCheckedChangeListener((view, checked) -> {
            if (updatingContainerSwitch) return;
            OxMode.setContainerEnabled(checked);
            updateContainerState();
        });
        enableRow.addView(containerSwitch, LayoutHelper.createLinear(48, 32, Gravity.CENTER_VERTICAL));
        enableRow.setOnClickListener(v -> containerSwitch.setChecked(!containerSwitch.isChecked(), true));
        enableCard.addView(enableRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        extra6.addView(enableCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout configCard = sectionList(context);
        mirrorRow = settingRow(context, LocaleController.getString(R.string.OxObContainerMirror), mirrorName(), () -> {
            if (getParentActivity() == null) return;
            new AlertDialog.Builder(getParentActivity())
                    .setItems(new CharSequence[]{"Ubuntu 官方", "清华 TUNA", "中科大 USTC", "阿里云"},
                            (d, which) -> {
                                OxMode.setContainerMirror(new String[]{"official", "tuna", "ustc", "aliyun"}[which]);
                                mirrorRow.setText(mirrorName());
                            })
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .show();
        });
        configCard.addView(mirrorRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        configCard.addView(divider(context));
        versionRow = settingRow(context, LocaleController.getString(R.string.OxObContainerVersion), OxMode.getContainerVersion(), () -> {
            if (getParentActivity() == null) return;
            new AlertDialog.Builder(getParentActivity())
                    .setItems(new CharSequence[]{"Ubuntu 22.04 LTS", "Ubuntu 24.04 LTS"}, (d, which) -> {
                        String v = which == 0 ? "22.04" : "24.04";
                        OxMode.setContainerVersion(v);
                        versionRow.setText(v);
                    })
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .show();
        });
        configCard.addView(versionRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        extra6.addView(configCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        LinearLayout compCard = sectionList(context);
        TextView compTitle = new TextView(context);
        compTitle.setText(LocaleController.getString(R.string.OxObContainerComponents));
        compTitle.setTextSize(12);
        compTitle.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        compTitle.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(4));
        compCard.addView(compTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        String[][] comps = {{"nodejs", "Node.js"}, {"python3", "Python 3"}, {"git", "Git"}, {"curl", "curl"}, {"tools", "命令行工具"}};
        for (String[] c : comps) {
            final String key = c[0];
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
            row.setBackground(Theme.getSelectorDrawable(false));
            TextView name = new TextView(context);
            name.setText(c[1]);
            name.setTextSize(15);
            name.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            row.addView(name, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            TextView check = makeCheck(context);
            row.addView(check, LayoutHelper.createLinear(22, 22, Gravity.CENTER_VERTICAL));
            final TextView checkRef = check;
            final Runnable refresh = () -> setCheck(checkRef, componentOn(key), false);
            refresh.run();
            row.setOnClickListener(v -> {
                setComponent(key, !componentOn(key));
                setCheck(checkRef, componentOn(key), true);
            });
            componentRows.add(check);
            compCard.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        extra6.addView(compCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        containerNote = new TextView(context);
        containerNote.setTextSize(12);
        containerNote.setLineSpacing(AndroidUtilities.dp(3), 1f);
        containerNote.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        containerNote.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(14), AndroidUtilities.dp(6), 0);
        containerNote.setText(LocaleController.getString(R.string.OxObContainerNote));
        extra6.addView(containerNote, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        pages.add(pageWrap(context, R.drawable.msg_download, LocaleController.getString(R.string.OxObContainerTitle), "", extra6));

        updateContainerState();
        updateAgentSummary();
    }

    private String mirrorName() {
        String m = OxMode.getContainerMirror();
        switch (m) {
            case "tuna": return "清华 TUNA";
            case "ustc": return "中科大 USTC";
            case "aliyun": return "阿里云";
            default: return "Ubuntu 官方";
        }
    }

    private TextView settingRow(Context context, String label, String value, Runnable onClick) {
        TextView tv = new TextView(context);
        tv.setTextSize(15);
        tv.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(15), AndroidUtilities.dp(16), AndroidUtilities.dp(15));
        tv.setBackground(Theme.getSelectorDrawable(false));
        tv.setText(label + "  ·  " + value);
        tv.setOnClickListener(v -> onClick.run());
        return tv;
    }

    private LinearLayout optionRow(Context context, int iconRes, int color, CharSequence title, CharSequence subtitle,
                                   boolean withCheck, Runnable onClick) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(AndroidUtilities.dp(64));
        row.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        row.setBackground(Theme.getSelectorDrawable(false));

        FrameLayout iconWrap = new FrameLayout(context);
        GradientDrawable circle = new GradientDrawable();
        circle.setCornerRadius(AndroidUtilities.dp(14));
        circle.setColor(Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)));
        iconWrap.setBackground(circle);
        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(color);
        iconWrap.addView(icon, LayoutHelper.createFrame(22, 22, Gravity.CENTER));
        row.addView(iconWrap, LayoutHelper.createLinear(44, 44, Gravity.CENTER_VERTICAL));

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(8), 0);
        TextView t = new TextView(context);
        t.setText(title);
        t.setTextSize(16);
        t.setTypeface(AndroidUtilities.bold());
        t.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        text.addView(t);
        if (subtitle != null && subtitle.length() > 0) {
            TextView s = new TextView(context);
            s.setText(subtitle);
            s.setTextSize(13);
            s.setLineSpacing(AndroidUtilities.dp(2), 1f);
            s.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            s.setPadding(0, AndroidUtilities.dp(3), 0, 0);
            text.addView(s);
        }
        row.addView(text, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        if (withCheck) {
            TextView check = makeCheck(context);
            row.addView(check, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL));
            row.setTag(check);
        } else {
            TextView chevron = new TextView(context);
            chevron.setText("›");
            chevron.setTextSize(22);
            chevron.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            row.addView(chevron, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        }
        row.setOnClickListener(v -> onClick.run());
        return row;
    }

    private TextView makeCheck(Context context) {
        TextView v = new TextView(context);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(14);
        v.setTypeface(AndroidUtilities.bold());
        v.setTextColor(0xffffffff);
        return v;
    }

    private void setCheck(TextView v, boolean checked, boolean animate) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        if (checked) {
            bg.setColor(getThemedColor(Theme.key_chats_actionBackground));
        } else {
            bg.setColor(0x00000000);
            bg.setStroke(AndroidUtilities.dp(1.5f), getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        }
        v.setBackground(bg);
        v.setText(checked ? "✓" : "");
        if (animate) {
            v.setScaleX(0.7f);
            v.setScaleY(0.7f);
            v.animate().scaleX(1f).scaleY(1f).setDuration(180)
                    .setInterpolator(new OvershootInterpolator()).start();
        }
    }

    private void selectMode(int mode) {
        selectedMode = mode;
        OxMode.setOnboardingMode(mode);
        updateModeChecks(true);
        updateNextState();
        updateModeWarning();
        updateAgentOptions();
    }

    private void updateModeChecks(boolean animate) {
        for (int i = 0; i < modeRows.length; i++) {
            View row = modeRows[i];
            if (row == null || !(row.getTag() instanceof TextView)) continue;
            int mode = i == 0 ? MODE_LOGIN : MODE_OX;
            setCheck((TextView) row.getTag(), selectedMode == mode, animate);
        }
    }

    private void updateModeWarning() {
        if (modeWarning == null) return;
        boolean loggedIn = UserConfig.getInstance(0).isClientActivated() && !OxMode.isAgentOnly();
        boolean ox = OxMode.isAgentOnly();
        String text = null;
        if (loggedIn && selectedMode == MODE_OX) {
            text = LocaleController.getString(R.string.OxObSwitchWarnOx);
        } else if (ox && selectedMode == MODE_LOGIN) {
            text = LocaleController.getString(R.string.OxObSwitchWarnLogin);
        }
        modeWarning.setVisibility(text == null ? View.GONE : View.VISIBLE);
        if (text != null) {
            modeWarning.setText(text);
        }
    }

    // ---------------- permissions (page 5) ----------------

    private void updateAgentOptions() {
        boolean login = selectedMode == MODE_LOGIN;
        if (containerOptionCard != null) {
            containerOptionCard.setVisibility(login ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (permList != null) rebuildPermissions();
        updateAgentSummary();
        updateContainerState();
        updateModeWarning();
    }

    private void rebuildPermissions() {
        if (permList == null) return;
        Context context = permList.getContext();
        permList.removeAllViews();
        int granted = 0;
        int total = 6;
        String[][] rows = {
                {LocaleController.getString(R.string.OxObPermNormal), null},
                {LocaleController.getString(R.string.OxObPermAccessibility), null},
                {LocaleController.getString(R.string.OxObPermAdb), null},
                {LocaleController.getString(R.string.OxObPermShizuku), null},
                {LocaleController.getString(R.string.OxObPermAdmin), null},
                {LocaleController.getString(R.string.OxObPermRoot), null},
        };
        String[][] states = {
                normalPermStatus(), accessibilityStatus(), adbWirelessStatus(),
                shizukuStatus(), adminStatus(), rootStatus()
        };
        for (int i = 0; i < rows.length; i++) {
            final int index = i;
            String name = rows[i][0];
            String[] status = states[i];
            if ("1".equals(status[1])) granted++;
            if (i > 0) permList.addView(divider(context));
            boolean was = Boolean.TRUE.equals(lastGranted.get(name));
            boolean now = "1".equals(status[1]);
            lastGranted.put(name, now);
            View row = permRow(context, name, status, was != now, () -> {
                switch (index) {
                    case 0:
                        if (getParentActivity() != null && Build.VERSION.SDK_INT >= 33
                                && getParentActivity().checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                            getParentActivity().requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, PERM_REQUEST);
                        } else {
                            toastPermissionPath();
                        }
                        break;
                    case 1:
                        try {
                            if (getParentActivity() != null) getParentActivity().startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        } catch (Throwable ignored) {
                        }
                        break;
                    case 2:
                        try {
                            if (getParentActivity() != null) getParentActivity().startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                        } catch (Throwable ignored) {
                        }
                        break;
                    case 3:
                        try {
                            Intent launch = getParentActivity().getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                            if (launch != null && getParentActivity() != null) getParentActivity().startActivity(launch);
                            else toastPermissionPath();
                        } catch (Throwable ignored) {
                        }
                        break;
                    case 4:
                        try {
                            android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager)
                                    getParentActivity().getSystemService(Context.DEVICE_POLICY_SERVICE);
                            android.content.ComponentName cn = new android.content.ComponentName(
                                    getParentActivity(), org.telegram.ox.OxDeviceAdminReceiver.class);
                            if (dpm != null && !dpm.isAdminActive(cn)) {
                                Intent admin = new Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                                admin.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn);
                                if (getParentActivity() != null) getParentActivity().startActivity(admin);
                            }
                        } catch (Throwable ignored) {
                        }
                        break;
                    case 5:
                        toast(LocaleController.getString(R.string.OxObPermRootHint));
                        break;
                }
            });
            permList.addView(row);
        }
        permSummary.setText(LocaleController.formatString("OxObPermCount", R.string.OxObPermCount, granted, total));
        permSummary.setTextColor(granted == total ? 0xff34C759 : getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
    }

    private View permRow(Context context, String name, String[] status, boolean animate, Runnable onTap) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Theme.getSelectorDrawable(false));
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));

        boolean granted = "1".equals(status[1]);
        boolean detected = "1".equals(status[2]);
        TextView statusIcon = new TextView(context);
        statusIcon.setGravity(Gravity.CENTER);
        statusIcon.setTextSize(13);
        statusIcon.setTypeface(AndroidUtilities.bold());
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        if (granted) {
            circle.setColor(0xff34C759);
            statusIcon.setTextColor(0xffffffff);
            statusIcon.setText("✓");
        } else if (detected) {
            circle.setColor(0x00000000);
            circle.setStroke(AndroidUtilities.dp(1.5f), getThemedColor(Theme.key_windowBackgroundWhiteHintText));
            statusIcon.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            statusIcon.setText("·");
        } else {
            circle.setColor(getThemedColor(Theme.key_windowBackgroundGray));
            statusIcon.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
            statusIcon.setText("–");
        }
        statusIcon.setBackground(circle);
        row.addView(statusIcon, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL));

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(8), 0);
        TextView title = new TextView(context);
        title.setText(name);
        title.setTextSize(15);
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        text.addView(title);
        TextView st = new TextView(context);
        st.setText(status[0]);
        st.setTextSize(13);
        st.setTextColor(granted ? 0xff34C759 : getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        text.addView(st);
        row.addView(text, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView chevron = new TextView(context);
        chevron.setText("›");
        chevron.setTextSize(20);
        chevron.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        chevron.setVisibility(granted ? View.INVISIBLE : View.VISIBLE);
        row.addView(chevron, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        row.setOnClickListener(v -> {
            if (granted) {
                Toast.makeText(getParentActivity(), LocaleController.getString(R.string.OxObPermGranted), Toast.LENGTH_SHORT).show();
            } else {
                onTap.run();
            }
        });
        if (animate) {
            statusIcon.setScaleX(0.6f);
            statusIcon.setScaleY(0.6f);
            statusIcon.animate().scaleX(1f).scaleY(1f).setDuration(220)
                    .setInterpolator(new OvershootInterpolator()).start();
        }
        return row;
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
            if (wireless) return new String[]{LocaleController.getString(R.string.OxObPermAdbWifi), "1", "1"};
            if (adb) return new String[]{LocaleController.getString(R.string.OxObPermAdbUsb), "1", "1"};
            return new String[]{LocaleController.getString(R.string.OxObPermEnableInSystem), "0", "1"};
        } catch (Throwable t) {
            return new String[]{LocaleController.getString(R.string.OxObPermNotDetected), "0", "0"};
        }
    }

    private String[] shizukuStatus() {
        try {
            getParentActivity().getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
            return new String[]{LocaleController.getString(R.string.OxObPermInstalled), "0", "1"};
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
            if (new File(p).exists()) return new String[]{LocaleController.getString(R.string.OxObPermDetected), "1", "1"};
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

    // ---------------- container ----------------

    private boolean componentOn(String key) {
        return ("," + OxMode.getContainerComponents() + ",").contains("," + key + ",");
    }

    private void setComponent(String key, boolean on) {
        List<String> list = new ArrayList<>();
        for (String p : OxMode.getContainerComponents().split(",")) {
            if (!p.isEmpty() && !p.equals(key)) list.add(p);
        }
        if (on) list.add(key);
        OxMode.setContainerComponents(String.join(",", list));
    }

    private void updateContainerState() {
        if (containerSwitch == null) return;
        boolean on = OxMode.isContainerEnabled();
        if (containerSwitch.isChecked() != on) {
            updatingContainerSwitch = true;
            containerSwitch.setChecked(on, true);
            updatingContainerSwitch = false;
        }
        if (containerNote != null) {
            containerNote.setAlpha(on ? 1f : 0.65f);
        }
    }

    private void updateAgentSummary() {
        if (agentSummary == null) return;
        int total = OxAgentStore.listAgents().size();
        int builtIn = 0;
        for (OxAgent a : OxAgentStore.listAgents()) {
            if (a.builtIn) builtIn++;
        }
        agentSummary.setText(LocaleController.formatString("OxObAgentConfigured", R.string.OxObAgentConfigured, total, builtIn));
    }

    // ---------------- chrome ----------------

    private void animatePageIn(View page) {
        if (page == null) return;
        page.setAlpha(0.6f);
        page.setTranslationY(AndroidUtilities.dp(8));
        page.animate().alpha(1f).translationY(0f).setDuration(220)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
        View hero = page.findViewWithTag("ox_hero");
        if (hero != null) {
            hero.setScaleX(0.92f);
            hero.setScaleY(0.92f);
            hero.setAlpha(0.7f);
            hero.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(260)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
        }
    }

    private void updateChrome(int position) {
        if (stepText == null) return;
        int accent = getThemedColor(Theme.key_chats_actionBackground);
        int accentSoft = Color.argb(110, Color.red(accent), Color.green(accent), Color.blue(accent));
        for (int i = 0; i < progressSegments.length; i++) {
            View seg = progressSegments[i];
            if (seg == null) continue;
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(AndroidUtilities.dp(2));
            bg.setColor(i < position ? accentSoft : (i == position ? accent : getThemedColor(Theme.key_divider)));
            seg.setBackground(bg);
            seg.animate().cancel();
            seg.animate().scaleY(i == position ? 1.8f : 1f).setDuration(160)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
        }
        boolean last = position == PAGES - 1;
        String text = last
                ? LocaleController.formatString("OxObStepLast", R.string.OxObStepLast, position + 1, PAGES)
                : LocaleController.formatString("OxObStep", R.string.OxObStep, position + 1, PAGES, PAGES - position - 1);
        if (!text.contentEquals(stepText.getText())) {
            stepText.animate().cancel();
            stepText.animate().alpha(0f).setDuration(70).withEndAction(() -> {
                stepText.setText(text);
                stepText.animate().alpha(1f).setDuration(130).start();
            }).start();
        }
        boolean skippable = position >= IDX_AGENT;
        skipStepButton.setVisibility(skippable ? View.VISIBLE : View.INVISIBLE);
        hintText.setVisibility(skippable ? View.VISIBLE : View.INVISIBLE);
        hintText.setText(LocaleController.getString(R.string.OxObSkipHint));
        if (last) {
            nextButton.setText(LocaleController.getString(selectedMode == MODE_LOGIN ? R.string.OxObStartLogin : R.string.OxObStart));
        } else {
            nextButton.setText(LocaleController.getString(R.string.OxObNext));
        }
        updateNextState();
        if (position == IDX_PERM) rebuildPermissions();
    }

    private boolean nextEnabled() {
        return viewPager.getCurrentItem() != IDX_LEGAL || legalAccepted;
    }

    private void updateNextState() {
        if (nextButton == null) return;
        boolean enabled = nextEnabled();
        nextButton.setAlpha(enabled ? 1f : 0.45f);
        if (viewPager != null && viewPager.getCurrentItem() == PAGES - 1) {
            nextButton.setText(LocaleController.getString(selectedMode == MODE_LOGIN ? R.string.OxObStartLogin : R.string.OxObStart));
        }
    }

    private void onNext() {
        if (!nextEnabled()) {
            if (modeWarning != null) {
                // shake the consent page's primary action: clear affordance, no silent failure
                nextButton.animate().translationX(AndroidUtilities.dp(6)).setDuration(60)
                        .withEndAction(() -> nextButton.animate().translationX(-AndroidUtilities.dp(6)).setDuration(60)
                                .withEndAction(() -> nextButton.animate().translationX(0).setDuration(60).start()).start()).start();
            }
            toast(LocaleController.getString(R.string.OxObLegalRequired));
            return;
        }
        int cur = viewPager.getCurrentItem();
        if (cur < PAGES - 1) {
            viewPager.setCurrentItem(cur + 1, true);
        } else {
            finishOnboarding();
        }
    }

    // ---------------- finish / exit ----------------

    private void finishOnboarding() {
        if (finished) return;
        int current = OxMode.isAgentOnly() ? MODE_OX : (UserConfig.getInstance(0).isClientActivated() ? MODE_LOGIN : MODE_OX);
        if (wasDone) {
            if (selectedMode == current) {
                finished = true;
                finishFragment();
            } else {
                confirmModeSwitch();
            }
            return;
        }
        finished = true;
        OxMode.setOnboardingDone(true);
        OxMode.setOnboardingMode(selectedMode);
        if (selectedMode == MODE_LOGIN) {
            presentFragment(new LoginActivity(), true);
        } else {
            OxMode.setAgentOnly(true);
            try {
                OxAgentsBridge.ensureLocalAccount();
            } catch (Throwable ignored) {
            }
            presentFragment(new MainTabsActivity(), true);
        }
    }

    private void confirmModeSwitch() {
        if (getParentActivity() == null) return;
        boolean toLogin = selectedMode == MODE_LOGIN;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(LocaleController.getString(R.string.OxObSwitchTitle));
        b.setMessage(LocaleController.getString(toLogin ? R.string.OxObSwitchWarnLogin : R.string.OxObSwitchWarnOx));
        b.setPositiveButton(LocaleController.getString(toLogin ? R.string.OxObSwitchLogin : R.string.OxObSwitchOx), (d, w) -> {
            finished = true;
            OxMode.setOnboardingDone(true);
            OxMode.setOnboardingMode(selectedMode);
            if (toLogin) {
                OxMode.setPendingMode(OxMode.PENDING_LOGIN);
                OxAgentsBridge.exitAgentOnly();
            } else {
                OxMode.setPendingMode(OxMode.PENDING_OX);
                MessagesController.getInstance(0).performLogout(0);
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = b.show();
        View positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (positive instanceof TextView) {
            ((TextView) positive).setTextColor(getThemedColor(Theme.key_text_RedRegular));
        }
    }

    private void confirmExit() {
        if (getParentActivity() == null) return;
        if (wasDone) {
            finishFragment();
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(LocaleController.getString(R.string.OxObExitTitle));
        b.setMessage(LocaleController.getString(R.string.OxObExitText));
        b.setPositiveButton(LocaleController.getString(R.string.OxObExitConfirm), (d, w) -> {
            if (finished) return;
            finished = true;
            OxMode.setOnboardingDone(true);
            OxMode.setOnboardingMode(MODE_OX);
            OxMode.setAgentOnly(true);
            try {
                OxAgentsBridge.ensureLocalAccount();
            } catch (Throwable ignored) {
            }
            presentFragment(new MainTabsActivity(), true);
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        b.show();
    }

    /** Opens the guide at a specific step (used by Ox Settings > container). */
    public OxOnboardingActivity setStartPage(int page) {
        android.os.Bundle b = new android.os.Bundle();
        b.putInt("start_page", page);
        arguments = b;
        return this;
    }
}
