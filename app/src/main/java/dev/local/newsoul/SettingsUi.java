package dev.local.newsoul;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.PopupWindow;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;

import android.widget.TextView;
import java.util.WeakHashMap;

/** Views run in Soul's process and use an isolated preference filename. */
final class SettingsUi {
    private static final String TAG = "SoulHook102";
    private static final String ENTRY_TAG = "dev.local.newsoul.settings.entry.v2";
    // Values deliberately do not hold Activities, so weak keys can be collected.
    private static final WeakHashMap<Activity, Boolean> OBSERVED = new WeakHashMap<>();
    static final int ACCENT = Color.rgb(0, 181, 162);
    private static final int TAB_TEXT_SP = 11;

    static void observeSettingsPage(Activity activity) {
        try {
            if (OBSERVED.containsKey(activity)) return;
            View titleView = find(activity, "topic_title");
            View rootView = find(activity, "rlWebRoot");
            View content = find(activity, "contentLayout");
            View titleBar = find(activity, "titleLay");
            if (!(titleView instanceof TextView) || !(rootView instanceof RelativeLayout)
                    || content == null || titleBar == null
                    || content.getParent() != rootView
                    || !(content.getLayoutParams() instanceof RelativeLayout.LayoutParams)) {
                Log.w(TAG, "Settings layout does not match; leaving page unchanged");
                return;
            }
            OBSERVED.put(activity, true);
            TextView title = (TextView) titleView;
            RelativeLayout root = (RelativeLayout) rootView;
            RelativeLayout.LayoutParams original = new RelativeLayout.LayoutParams(
                    (RelativeLayout.LayoutParams) content.getLayoutParams());
            boolean[] entryDark = {false};
            SettingsThemeSync.observe(activity, content, title);
            Runnable update = () -> {
                try {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    View old = root.findViewWithTag(ENTRY_TAG);
                    // This H5 container hosts many pages. Only the settings root gets the entry.
                    if (!"设置".contentEquals(title.getText())) {
                        if (old != null) {
                            root.removeView(old);
                            content.setLayoutParams(new RelativeLayout.LayoutParams(original));
                        }
                        return;
                    }
                    boolean dark = Color.luminance(title.getCurrentTextColor()) > 0.45;
                    if (old != null && entryDark[0] == dark) return;
                    if (old != null) root.removeView(old);
                    entryDark[0] = dark;
                    FrameLayout entry = entry(activity, dark);
                    entry.setId(View.generateViewId());
                    entry.setTag(ENTRY_TAG);
                    RelativeLayout.LayoutParams entryParams = new RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 66));
                    entryParams.addRule(RelativeLayout.BELOW, titleBar.getId());
                    root.addView(entry, entryParams);
                    RelativeLayout.LayoutParams shifted = new RelativeLayout.LayoutParams(original);
                    shifted.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
                    shifted.addRule(RelativeLayout.BELOW, entry.getId());
                    content.setLayoutParams(shifted);
                    Log.i(TAG, "settings entry inserted; trigger=private H5Activity.m1");
                } catch (Throwable error) {
                    Log.e(TAG, "Settings entry failed", error);
                }
            };
            title.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                public void afterTextChanged(Editable s) { update.run(); }
            });
            root.getViewTreeObserver().addOnPreDrawListener(() -> {
                update.run();
                return true;
            });
            update.run();
        } catch (Throwable error) {
            Log.e(TAG, "Settings observation failed", error);
        }
    }

    private static FrameLayout entry(Activity activity, boolean dark) {
        // Match the current Soul settings list: one line, outline icon, muted chevron.
        int surface = dark ? Color.rgb(18, 18, 31) : Color.WHITE;
        int textColor = readableText(dark ? Color.rgb(104, 104, 129) : Color.rgb(51, 51, 51),
                dark, surface);
        int arrowColor = dark ? Color.rgb(53, 53, 71) : Color.rgb(191, 191, 191);
        FrameLayout container = new FrameLayout(activity);
        container.setBackgroundColor(surface);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 22), 0, dp(activity, 22), 0);
        row.addView(new LineIcon(activity, textColor, false),
                new LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 28)));
        TextView heading = label(activity, VariantFeatures.LABEL, 17, textColor);
        heading.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
        labelParams.leftMargin = dp(activity, 18);
        row.addView(heading, labelParams);
        row.addView(new LineIcon(activity, arrowColor, true),
                new LinearLayout.LayoutParams(dp(activity, 12), dp(activity, 20)));
        container.addView(row, new FrameLayout.LayoutParams(-1, -1));
        View divider = new View(activity);
        divider.setBackgroundColor(dark ? Color.rgb(36, 36, 52) : Color.rgb(240, 240, 240));
        FrameLayout.LayoutParams dividerParams = new FrameLayout.LayoutParams(-1, 1, Gravity.BOTTOM);
        dividerParams.leftMargin = dividerParams.rightMargin = dp(activity, 22);
        container.addView(divider, dividerParams);
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        container.setContentDescription("New Soul");
        container.setOnClickListener(v -> showSettings(activity, dark));
        buttonSemantics(container);
        return container;
    }

    static void showSettings(Activity activity, boolean dark) {
        try {
            new SegmentedSettings(activity, dark).show();
            Log.i(TAG, "settings panel opened");
        } catch (Throwable error) { Log.e(TAG, "Settings panel failed", error); }
    }

    static void addInfoActionPair(MenuPanel panel, LinearLayout body, TextView left, TextView right) {
        LinearLayout row = new LinearLayout(panel.context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        styleInfoAction(panel, left);
        styleInfoAction(panel, right);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        View divider = new View(panel.context);
        divider.setBackgroundColor(panel.dark ? 0xff3a3a4a : 0xffdcdce2);
        divider.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(divider, new LinearLayout.LayoutParams(dp(panel.context, 1), dp(panel.context, 28)));
        row.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    static void styleInfoAction(MenuPanel panel, TextView action) {
        panel.appearance.apply(action, UiAppearance.Role.ACTION);
        action.setMinHeight(dp(panel.context, 52));
        action.setGravity(Gravity.CENTER);
        action.setSingleLine(false);
        action.setEllipsize(null);
        action.setPadding(action.getPaddingLeft(), dp(panel.context, 8),
                action.getPaddingRight(), dp(panel.context, 8));
    }

    static void showHookDiagnostics(Activity activity, boolean dark, boolean automatic) {
        SettingsOtherPage.showHookDiagnostics(activity, dark, automatic);
    }

    static void showHookDiagnostics(Activity activity, boolean dark,
            boolean automatic, String previousVersion) {
        SettingsOtherPage.showHookDiagnostics(activity, dark, automatic, previousVersion);
    }

    /** One dialog owns every category, so switching never returns focus to the feed. */
    private static final class SegmentedSettings {
        private static final String[] NAMES = {"聊天", "广场", "主页", "广告", "其他"};
        private final MenuPanel shell;
        private final MenuPanel[] pages = new MenuPanel[NAMES.length];
        private final ScrollView[] scrolls = new ScrollView[NAMES.length];
        private final TextView[] options = new TextView[NAMES.length];
        private final MenuPager viewport;
        private final android.widget.HorizontalScrollView tabs;
        private final LinearLayout segments;
        private final GlassTabTrack track;
        private int selected = -1;
        private int progressFrom, progressTo;
        private float progressFraction;
        private boolean progressMoving;
        private SettingsSearch search;
        private final java.util.List<SettingsSearch.Entry> searchEntries = new java.util.ArrayList<>();

        SegmentedSettings(Activity activity, boolean dark) {
            shell = new MenuPanel(activity, dark);
            shell.help = "长按 Soul 底部导航图标可打开插件设置";
            shell.appearance.categorySurface = true;
            shell.appearance.navigationTrack = true;
            Context context = shell.context;
            viewport = new MenuPager(context, NAMES.length, new MenuPager.Listener() {
                @Override public ViewGroup page(int index) { return ensurePage(index); }
                @Override public void progress(int from, int to, float fraction, boolean moving) {
                    for (MenuPanel page : pages) if (page != null)
                        for (LocalReveal reveal : page.localReveals)
                            if (reveal.animator != null) reveal.finish();
                    progressFrom = from;
                    progressTo = to;
                    progressFraction = fraction;
                    progressMoving = moving;
                    moveIndicator();
                }
                @Override public void selected(int index) { categorySelected(index); }
            });
            tabs = new android.widget.HorizontalScrollView(context);
            tabs.setHorizontalScrollBarEnabled(false);
            tabs.setFillViewport(true);
            track = new GlassTabTrack(context, shell.appearance);
            track.setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4));
            segments = new LinearLayout(context) {
                @Override protected void onMeasure(int widthSpec, int heightSpec) {
                    // Equal segments when they fit; at large font scales preserve text and scroll.
                    int cell = dp(context, 48);
                    for (TextView option : options) if (option != null)
                        cell = Math.max(cell, (int) Math.ceil(option.getPaint().measureText(option.getText().toString()))
                                + dp(context, 24));
                    int width = cell * NAMES.length;
                    if (View.MeasureSpec.getMode(widthSpec) != View.MeasureSpec.UNSPECIFIED)
                        width = Math.max(width, View.MeasureSpec.getSize(widthSpec));
                    for (int i = 0; i < getChildCount(); i++) {
                        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) getChildAt(i).getLayoutParams();
                        params.width = width / NAMES.length + (i < width % NAMES.length ? 1 : 0);
                    }
                    super.onMeasure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), heightSpec);
                }
            };
            segments.setGravity(Gravity.CENTER_VERTICAL);
            singleChoiceGroup(segments, null, NAMES.length);
            segments.setContentDescription("设置分类");
            for (int i = 0; i < NAMES.length; i++) {
                final int index = i;
                TextView option = label(context, NAMES[i], TAB_TEXT_SP, shell.secondary);
                options[i] = option;
                option.setCompoundDrawables(null, SettingsCardIcon.drawable(context,NAMES[i],shell.secondary), null,null);
                option.setCompoundDrawablePadding(dp(context,3));
                option.setSingleLine(true);
                option.setGravity(Gravity.CENTER);
                option.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
                option.setMinHeight(dp(context, 58));
                singleChoiceOption(option, i);
                option.setOnClickListener(v -> select(index, true));
                segments.addView(option, new LinearLayout.LayoutParams(-2, -2));
            }
            track.addView(segments, new FrameLayout.LayoutParams(-1, -2));
            tabs.addView(track, new FrameLayout.LayoutParams(-2, -2));
            segments.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> moveIndicator());
        }

        void show() {
            LinearLayout content = new LinearLayout(shell.context) {
                @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
                    shell.exitInputsOnTouch(event);
                    return super.dispatchTouchEvent(event);
                }
                @Override public void onWindowFocusChanged(boolean hasWindowFocus) {
                    super.onWindowFocusChanged(hasWindowFocus);
                    if (hasWindowFocus) shell.refreshPreferenceViews();
                }
                @Override protected void onMeasure(int widthSpec, int heightSpec) {
                    int available = availableHeight(shell.activity);
                    int preferred = Math.max(dp(shell.context, 112),
                            Math.min(dp(shell.context, 620), Math.round(available * .86f) - dp(shell.context, 132)));
                    if (View.MeasureSpec.getMode(heightSpec) != View.MeasureSpec.UNSPECIFIED)
                        preferred = Math.min(preferred, View.MeasureSpec.getSize(heightSpec));
                    super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(preferred, View.MeasureSpec.EXACTLY));
                }
            };
            content.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(-1, -2);
            tabParams.leftMargin = tabParams.rightMargin = dp(shell.context, 16);
            tabParams.topMargin = dp(shell.context, 4);
            tabParams.bottomMargin = dp(shell.context, 8);
            search = new SettingsSearch(shell, tabs, viewport, new SettingsSearch.Host() {
                public void prepare() {
                    if (!searchEntries.isEmpty()) return;
                    for (int i=0;i<NAMES.length;i++) {
                        ensurePage(i);
                        indexSettings(pages[i].body, i, NAMES[i]);
                    }
                }
                public java.util.List<SettingsSearch.Entry> entries() { return searchEntries; }
                public void select(int category) { SegmentedSettings.this.select(category,true); }
                public View tab(int category) { return options[category]; }
                public ScrollView scroll(int category) { return scrolls[category]; }
                public void searchProgress(float progress) {
                    segments.setTranslationX(-segments.getWidth()*progress);
                    segments.setAlpha(1-progress);
                    track.setLensAlpha(1-progress);
                    if (selected>=0) {
                        View option=options[selected];
                        track.selection(segments.getLeft()+option.getLeft(), option.getWidth(),false);
                    }
                }
                public void lens(int category,boolean moving) {
                    View tab=options[category];
                    track.selection(segments.getLeft()+tab.getLeft(),tab.getWidth(),moving);
                }
            });
            content.addView(search.header, tabParams);
            content.addView(search.body, new LinearLayout.LayoutParams(-1, 0, 1));
            select(0, false);
            shell.showContent(VariantFeatures.LABEL, "完成", content);
            search.attach();
            for (MenuPanel page : shell.cachedPages) page.dialog = shell.dialog;
            int width = shell.activity.getResources().getDisplayMetrics().widthPixels;
            if (android.os.Build.VERSION.SDK_INT >= 30)
                width = shell.activity.getWindowManager().getCurrentWindowMetrics().getBounds().width();
            shell.dialog.getWindow().setLayout(Math.min(dp(shell.context, 560), width - dp(shell.context, 24)), -2);
            updateHelp();
        }

        private void indexSettings(View view, int category, String path) {
            // Index setting controls and named actions, not the options inside a selector.
            if(view instanceof SegmentedChoice) return;
            String group = pages[category].sectionNames.get(view);
            if (group != null) path = NAMES[category] + " · " + group;
            if (view instanceof MenuSwitch || view instanceof CheckBox) {
                CharSequence label = view instanceof CheckBox && ((CheckBox)view).getText().length() > 0
                        ? ((CheckBox)view).getText() : view.getContentDescription();
                if (label!=null && label.length()>0) {
                    String title=label.toString().replaceAll("[（，].*未就绪.*$", "");
                    searchEntries.add(new SettingsSearch.Entry(title,path,category,view));
                }
                return;
            } else if(view instanceof TextView && !(view instanceof android.widget.EditText)
                    && view.hasOnClickListeners() && view.isFocusable()
                    && !(view.getParent() instanceof SwitchRow)) {
                String title=((TextView)view).getText().toString().trim();
                if(title.length()>1) searchEntries.add(new SettingsSearch.Entry(title,path,category,view));
            }
            if(view instanceof ViewGroup) {
                ViewGroup groupView=(ViewGroup)view;
                for(int i=0;i<groupView.getChildCount();i++) indexSettings(groupView.getChildAt(i),category,path);
            }
        }

        private void select(int index, boolean animate) {
            viewport.select(index, animate);
        }

        private ViewGroup ensurePage(int index) {
            MenuPanel page = pages[index];
            if (page == null) {
                page = new MenuPanel(shell.activity, shell.dark);
                page.embeddedCategory = true;
                page.appearance.categorySurface = true;
                page.body.setClipChildren(false);
                page.body.setClipToPadding(false);
                page.preferenceOwner = shell;
                page.dialog = shell.dialog;
                pages[index] = page;
                shell.cachedPages.add(page);
                switch (index) {
                    case 0: SettingsChatPage.populate(page); break;
                    case 1: SettingsSquarePage.populate(page); break;
                    case 2: SettingsHomePage.populate(page); break;
                    case 3: SettingsAdPage.populate(page); break;
                    default: SettingsOtherPage.populate(page); break;
                }
                ScrollView scroll = new ScrollView(shell.context);
                scroll.setFillViewport(false);
                scroll.setClipToPadding(false);
                scroll.addView(page.body);
                scrolls[index] = scroll;
                page.refreshPreferenceViews();
            }
            return scrolls[index];
        }

        private void categorySelected(int index) {
            // Semantic selection/help refresh only after settling, never on animation frames.
            selected = index;
            pages[index].refreshPreferenceViews();
            for (int i = 0; i < options.length; i++) {
                options[i].setSelected(i == index);
                options[i].setTextColor(i == index
                        ? readableText(shell.dark ? ACCENT : 0xff00796b, shell.dark,
                                shell.dark ? 0xff214542 : 0xffd9f3ed)
                        : shell.secondary);
                options[i].getCompoundDrawables()[1].setTint(options[i].getCurrentTextColor());
                options[i].setTypeface(Typeface.DEFAULT, i == index ? Typeface.BOLD : Typeface.NORMAL);
            }
            updateHelp();
        }

        private void updateHelp() {
            if (shell.titleRow == null || selected < 0) return;
            if (shell.titleHelp != null) shell.titleRow.removeView(shell.titleHelp);
            MenuPanel page = pages[selected];
            android.text.SpannableStringBuilder help = new android.text.SpannableStringBuilder(shell.help);
            if (page.help != null) help.append("\n\n").append(NAMES[selected]).append("：\n").append(page.help);
            View hint = helpButton(shell, "插件设置", help);
            shell.titleHelp = hint;
            shell.titleRow.addView(hint);
        }

        private void moveIndicator() {
            if (search != null && search.isSearchSurfaceVisible()) {
                return;
            }
            View first = options[progressFrom], last = options[progressTo];
            if (first == null || last == null || first.getWidth() == 0) return;
            float left = first.getLeft() + (last.getLeft() - first.getLeft()) * progressFraction;
            int width = Math.round(first.getWidth() + (last.getWidth() - first.getWidth()) * progressFraction);
            // The glass lens shares page progress, including reversals and interrupted gestures.
            track.selection(segments.getLeft() + left, width, progressMoving);
            int selectedColor = readableText(shell.dark ? ACCENT : 0xff00796b, shell.dark,
                    shell.dark ? 0xff214542 : 0xffd9f3ed);
            for (int i = 0; i < options.length; i++) {
                float emphasis = i == progressFrom ? 1 - progressFraction : 0;
                if (i == progressTo) emphasis += progressFraction;
                options[i].setTextColor(Color.rgb(
                        Math.round(Color.red(shell.secondary) + (Color.red(selectedColor) - Color.red(shell.secondary)) * emphasis),
                        Math.round(Color.green(shell.secondary) + (Color.green(selectedColor) - Color.green(shell.secondary)) * emphasis),
                        Math.round(Color.blue(shell.secondary) + (Color.blue(selectedColor) - Color.blue(shell.secondary)) * emphasis)));
                options[i].getCompoundDrawables()[1].setTint(options[i].getCurrentTextColor());
            }
            int right = Math.round(left) + width + dp(shell.context, 8);
            if (left < tabs.getScrollX()) tabs.scrollTo(Math.round(left), 0);
            else if (right > tabs.getScrollX() + tabs.getWidth()) tabs.scrollTo(right - tabs.getWidth(), 0);
        }

        private static int availableHeight(Activity activity) {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.view.WindowMetrics metrics = activity.getWindowManager().getCurrentWindowMetrics();
                android.graphics.Insets bars = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                        android.view.WindowInsets.Type.systemBars());
                return metrics.getBounds().height() - bars.top - bars.bottom;
            }
            return activity.getResources().getDisplayMetrics().heightPixels;
        }
    }

    static Typeface brandTypeface(Context context) {
        try {
            Context host = "cn.soulapp.android".equals(context.getPackageName()) ? context
                    : context.createPackageContext("cn.soulapp.android", 0);
            return new Typeface.Builder(host.getAssets(), "fonts/tivf-bold-round.ttf")
                    .setWeight(500).build();
        } catch (Exception unavailable) {
            return Typeface.create("sans-serif-rounded", Typeface.NORMAL);
        }
    }

    static TextView donationInvitation(MenuPanel panel, int gravity) {
        TextView view = new TitleDonation(panel);
        view.setGravity(gravity | Gravity.CENTER_VERTICAL);
        return view;
    }

    /** Shared title-side caption styling; callers provide text and link actions. */
    static void styleTitleCaption(TextView view, MenuPanel panel, int gravity) {
        view.setTextSize(TAB_TEXT_SP);
        view.setTextColor(panel.secondary);
        view.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        view.setIncludeFontPadding(false);
        view.setGravity(gravity | Gravity.CENTER_VERTICAL);
        view.setMaxLines(Integer.MAX_VALUE);
        view.setEllipsize(null);
        view.setMinHeight(dp(panel.context, 48));
        view.setPadding(0, 0, dp(panel.context, 2), 0);
    }

    /** The invitation shares the category-label size and reserves italic ink at line ends. */
    private static final class TitleDonation extends TextView {
        private static final String FIRST = "免费模块开发不易, 如果对你有帮助";
        private static final String SECOND = "欢迎点我进行捐赠";
        TitleDonation(MenuPanel panel) {
            super(panel.context);
            // TextView clips at the line's advance width. Blank advance after italic glyphs
            // keeps their overhang inside the text layout, including at the right-aligned edge.
            android.text.SpannableString invitation = new android.text.SpannableString(
                    FIRST + "\u00a0\u00a0\n" + SECOND + "\u00a0\u00a0");
            int start = invitation.toString().indexOf("点我进行捐赠");
            invitation.setSpan(new android.text.style.ForegroundColorSpan(panel.accentText),
                    start, start + "点我进行捐赠".length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            setText(invitation);
            styleTitleCaption(this, panel, Gravity.END);
            buttonSemantics(this);
            setContentDescription(FIRST + "，" + SECOND + "，打开支持作者");
            setOnClickListener(v -> DonationUi.show(panel.activity, panel.dark));
        }

    }

    /** All module menu pages share one visual shell, including future settings. */
    static final class MenuPanel {
        final Activity activity;
        final boolean dark;
        final UiAppearance appearance;
        final AlertDialog.Builder builder;
        AlertDialog dialog;
        private LinearLayout titleRow;
        private View titleHelp;
        final Context context;
        final LinearLayout body;
        final int foreground, secondary, accentText;
        final java.util.Map<LinearLayout, AvailabilityGroup> availability = new java.util.IdentityHashMap<>();
        private final java.util.List<Runnable> preferenceRefreshers = new java.util.ArrayList<>();
        private final java.util.List<android.widget.EditText> exitInputs = new java.util.ArrayList<>();
        final java.util.Map<View,String> sectionNames = new java.util.IdentityHashMap<>();
        private final java.util.List<LocalReveal> localReveals = new java.util.ArrayList<>();
        private MenuPanel preferenceOwner;
        private final java.util.List<MenuPanel> cachedPages = new java.util.ArrayList<>();
        private boolean embeddedCategory;
        private java.util.function.BooleanSupplier draftDirty;
        private MenuPanel discardPrompt;
        CharSequence help;
        MenuPanel(Activity activity, boolean dark) {
            this.activity = activity; this.dark = dark;
            foreground = dark ? 0xffe6e6ef : Color.rgb(51, 51, 51);
            int surface = dark ? 0xff282838 : 0xffededed;
            int nativeSurface = activity.getResources().getIdentifier("color_s_04", "color", activity.getPackageName());
            if (nativeSurface != 0) try {
                surface = activity.getResources().getColor(nativeSurface, activity.getTheme());
            } catch (RuntimeException ignored) {}
            int panelSurface = dark ? UiAppearance.DARK_SETTINGS_PANEL : Color.WHITE;
            secondary = readableText(dark ? 0xff828296 : 0xff666673, dark, panelSurface, surface);
            accentText = readableText(dark ? ACCENT : 0xff00796b, dark, panelSurface, surface);
            builder = new AlertDialog.Builder(UiAppearance.menuContext(activity, dark), dark
                    ? android.R.style.Theme_Material_Dialog_Alert
                    : android.R.style.Theme_Material_Light_Dialog_Alert);
            context = builder.getContext();
            appearance = UiAppearance.of(context, dark);
            body = new LinearLayout(context);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setTag(this);
            body.setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 16), dp(activity, 16));
        }

        /** Registered inline editors commit through their own focus-loss listener. */
        void saveInputOnExit(android.widget.EditText input) {
            exitInputs.add(input);
            // A touch outside the editor can move focus here instead of refocusing the input.
            body.setFocusableInTouchMode(true);
        }

        private void exitInputsOnTouch(android.view.MotionEvent event) {
            if (event.getActionMasked() != android.view.MotionEvent.ACTION_DOWN) return;
            for (android.widget.EditText input : exitInputs) {
                android.graphics.Rect bounds = new android.graphics.Rect();
                if (input.hasFocus() && (!input.getGlobalVisibleRect(bounds)
                        || !bounds.contains((int) event.getRawX(), (int) event.getRawY())))
                    input.clearFocus();
            }
            for (MenuPanel page : cachedPages) page.exitInputsOnTouch(event);
        }

        /** Anchored actions share the panel palette, spacing and interaction feedback. */
        void showActionMenu(View anchor, String[] actions, boolean enabled,
                java.util.function.IntConsumer onSelect) {
            if (!anchor.isAttachedToWindow() || !anchor.isShown()
                    || activity.isFinishing() || activity.isDestroyed()) return;
            LinearLayout items = new LinearLayout(context);
            items.setOrientation(LinearLayout.VERTICAL);
            items.setPadding(0, dp(context, 4), 0, dp(context, 4));
            ScrollView scroll = new ScrollView(context);
            appearance.apply(scroll, UiAppearance.Role.PANEL, 14);
            scroll.setClipToOutline(true);
            scroll.addView(items);
            PopupWindow popup = new PopupWindow(context);
            popup.setContentView(scroll);
            popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            popup.setFocusable(true);
            popup.setOutsideTouchable(true);
            popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
            popup.setElevation(dp(context, dark ? 4 : 8));
            int desiredWidth = dp(context, 176);
            for (int i = 0; i < actions.length; i++) {
                final int index = i;
                TextView option = label(context, actions[i], 16, enabled ? foreground : secondary);
                option.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                option.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
                option.setMinHeight(dp(context, 48));
                appearance.apply(option, UiAppearance.Role.ICON);
                option.setEnabled(enabled);
                buttonSemantics(option);
                option.setOnClickListener(v -> {
                    popup.dismiss();
                    if (anchor.isAttachedToWindow() && anchor.isShown()
                            && !activity.isFinishing() && !activity.isDestroyed()) onSelect.accept(index);
                });
                desiredWidth = Math.max(desiredWidth,
                        (int) Math.ceil(option.getPaint().measureText(actions[i])) + dp(context, 32));
                items.addView(option, new LinearLayout.LayoutParams(-1, -2));
                if (i < actions.length - 1) {
                    View divider = new View(context);
                    divider.setBackgroundColor(dark ? 0xff363646 : 0xffe9e9ee);
                    LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, dp(context, 1));
                    line.leftMargin = line.rightMargin = dp(context, 16);
                    items.addView(divider, line);
                }
            }
            android.graphics.Rect frame = new android.graphics.Rect();
            anchor.getWindowVisibleDisplayFrame(frame);
            int availableWidth = Math.min(anchor.getRootView().getWidth(), frame.width());
            int width = Math.min(desiredWidth, Math.max(dp(context, 48), availableWidth - dp(context, 24)));
            items.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            popup.setWidth(width);
            popup.setHeight(Math.min(items.getMeasuredHeight(), Math.max(dp(context, 48), frame.height() - dp(context, 24))));
            View.OnAttachStateChangeListener lifetime = new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) {}
                @Override public void onViewDetachedFromWindow(View view) { popup.dismiss(); }
            };
            anchor.addOnAttachStateChangeListener(lifetime);
            popup.setOnDismissListener(() -> anchor.removeOnAttachStateChangeListener(lifetime));
            try { popup.showAsDropDown(anchor, 0, dp(context, 4), Gravity.END); }
            catch (RuntimeException error) {
                anchor.removeOnAttachStateChangeListener(lifetime);
                throw error;
            }
        }

        /** Editing pages opt in before show; programmatic dismiss after a save stays immediate. */
        void protectDraft(java.util.function.BooleanSupplier dirty) {
            if (dialog != null) throw new IllegalStateException("Protect the draft before showing it");
            draftDirty = java.util.Objects.requireNonNull(dirty);
            builder.setNegativeButton("取消", (d, which) -> {});
        }

        /** Register read-only state updates for cached pages and configuration imports. */
        void addPreferenceRefresher(Runnable refresh) {
            preferenceRefreshers.add(refresh);
        }

        /** Re-read imported values in place, preserving scroll and avoiding preference writes. */
        void refreshPreferenceViews() {
            if (preferenceOwner != null) {
                preferenceOwner.refreshPreferenceViews();
                return;
            }
            refreshOwnPreferenceViews();
            for (MenuPanel page : cachedPages) page.refreshOwnPreferenceViews();
        }

        private void refreshOwnPreferenceViews() {
            for (LocalReveal reveal : localReveals) reveal.finish();
            for (Runnable refresh : preferenceRefreshers) refresh.run();
        }

        void show(String heading, String action) {
            ScrollView scroll = new ScrollView(context);
            scroll.addView(body);
            showContent(heading, action, scroll);
        }

        private void showContent(String heading, String action, View content) {
            LinearLayout title = new LinearLayout(context);
            titleRow = title;
            title.setGravity(Gravity.CENTER_VERTICAL);
            int titleInset = dp(activity, cachedPages.isEmpty() ? 24 : 16);
            title.setPadding(titleInset, dp(activity, 14), titleInset, dp(activity, 8));
            TextView headingView = label(context, heading, 18, foreground);
            if (cachedPages.isEmpty()) {
                headingView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            } else {
                // Reuse Soul's rounded Latin face only for the main settings brand title.
                headingView.setTypeface(brandTypeface(activity));
            }
            if (android.os.Build.VERSION.SDK_INT >= 28) headingView.setAccessibilityHeading(true);
            LinearLayout.LayoutParams brandBounds = new LinearLayout.LayoutParams(0,-2,1);
            if (!cachedPages.isEmpty()) {
                android.text.SpannableString brand = new android.text.SpannableString("New Soul");
                brand.setSpan(new android.text.style.ForegroundColorSpan(accentText),4,8,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                headingView.setText(brand);
                headingView.setTextSize(22);
                headingView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                brandBounds = new LinearLayout.LayoutParams(-2, -2);
            }
            if (!cachedPages.isEmpty() && VariantFeatures.hasAi()) {
                FrameLayout brand = new FrameLayout(context);
                // Reserve the shared mark's bounds without moving the wordmark off the left inset.
                headingView.setPadding(0, dp(context, 4), dp(context, 12), dp(context, 4));
                brand.addView(headingView, new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.CENTER_VERTICAL));
                PluginActionMark.set(headingView, true);
                title.addView(brand, brandBounds);
            } else {
                title.addView(headingView, brandBounds);
            }
            if (!cachedPages.isEmpty()) {
                TextView donation = donationInvitation(this, Gravity.END);
                LinearLayout.LayoutParams donationBounds = new LinearLayout.LayoutParams(0, -2, 1);
                donationBounds.leftMargin = dp(context, 8);
                title.addView(donation, donationBounds);
            }
            if (help != null) {
                titleHelp = helpButton(this, heading, help);
                title.addView(titleHelp);
            }
            // Reserve the category help height even on pages without a help button.
            title.setMinimumHeight(dp(context, cachedPages.isEmpty() ? 58 : 70));
            dialog = builder.setCustomTitle(title).setView(content)
                    .setPositiveButton(action, (d, which) -> {}).create();
            // Suppress the theme's window motion before WindowManager starts the entrance.
            // FrostedMenuEffect supplies the in-place fade after attachment.
            if (android.os.Build.VERSION.SDK_INT >= 31
                    && PluginPreferences.get(activity).getBoolean(FrostedMenuEffect.ENABLED,
                            ConfigDefaults.booleanValue(FrostedMenuEffect.ENABLED))) {
                android.view.WindowManager manager = activity.getSystemService(android.view.WindowManager.class);
                if (manager != null && manager.isCrossWindowBlurEnabled())
                    dialog.getWindow().setWindowAnimations(0);
            }
            dialog.show();
            if (draftDirty != null) installDraftProtection();
            appearance.window(dialog.getWindow(), UiAppearance.Role.PANEL);
            FrostedMenuEffect.attach(dialog.getWindow(), appearance, PluginPreferences.get(activity));
            for (int button : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
                TextView control = dialog.getButton(button);
                if (control != null) {
                    appearance.apply(control, UiAppearance.Role.ACTION);
                    control.setTextColor(accentText);
                    control.setMinHeight(dp(context, 48));
                    control.setMinWidth(dp(context, 48));
                }
            }
        }

        private void installDraftProtection() {
            // Outside touches never discard an editor. Explicit Cancel and Back share one path.
            dialog.setCanceledOnTouchOutside(false);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> requestDraftExit());
            dialog.setOnKeyListener((d, key, event) -> {
                if (key != android.view.KeyEvent.KEYCODE_BACK
                        && key != android.view.KeyEvent.KEYCODE_ESCAPE) return false;
                if (event.getAction() == android.view.KeyEvent.ACTION_UP && !event.isCanceled())
                    onEditorBack();
                return true;
            });
            if (android.os.Build.VERSION.SDK_INT >= 33) installPredictiveDraftBack();
        }

        private void installPredictiveDraftBack() {
            android.window.OnBackInvokedDispatcher dispatcher = dialog.getOnBackInvokedDispatcher();
            android.window.OnBackInvokedCallback callback = this::onEditorBack;
            // Keep normal priority so the IME can handle Back before this editor.
            dispatcher.registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            dialog.getWindow().getDecorView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) {}
                @Override public void onViewDetachedFromWindow(View view) {
                    dispatcher.unregisterOnBackInvokedCallback(callback);
                    view.removeOnAttachStateChangeListener(this);
                }
            });
        }

        private void onEditorBack() {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                View decor = dialog.getWindow().getDecorView();
                android.view.WindowInsets insets = decor.getRootWindowInsets();
                if (insets != null && insets.isVisible(android.view.WindowInsets.Type.ime())) {
                    android.view.WindowInsetsController controller = decor.getWindowInsetsController();
                    if (controller != null) {
                        controller.hide(android.view.WindowInsets.Type.ime());
                        return;
                    }
                }
            }
            // On older Android versions the IME consumes its Back before Dialog dispatch.
            requestDraftExit();
        }

        private void requestDraftExit() {
            if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
            if (!draftDirty.getAsBoolean()) { dialog.dismiss(); return; }
            if (discardPrompt != null && discardPrompt.dialog.isShowing()) return;
            View previousFocus = dialog.getCurrentFocus();
            MenuPanel prompt = new MenuPanel(activity, dark);
            discardPrompt = prompt;
            prompt.body.addView(label(prompt.context,
                    "有尚未保存的修改\n放弃后这些修改不会生效", 15, prompt.foreground));
            prompt.builder.setNegativeButton("放弃修改", (d, which) -> dialog.dismiss());
            prompt.show("放弃未保存的修改？", "继续编辑");
            prompt.dialog.setCanceledOnTouchOutside(false);
            prompt.dialog.setOnDismissListener(d -> {
                discardPrompt = null;
                if (dialog.isShowing() && previousFocus != null) previousFocus.requestFocus();
            });
        }

        /** Shared bottom-panel variant for chat tools; settings keep the normal menu style. */
        void showBottom(String heading, View footer) {
            showBottom(heading, footer, false);
        }

        /** AI subpages share the native recommendation palette and typography. */
        void showAiBottom(String heading, View footer) {
            showBottom(heading, footer, true);
        }

        private void showBottom(String heading, View footer, boolean ai) {
            LinearLayout header = new LinearLayout(context);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(activity, 16), dp(activity, 4), dp(activity, 8), 0);
            if (ai) {
                android.widget.ImageView icon = new android.widget.ImageView(context);
                icon.setImageDrawable(VariantFeatures.hostDrawable(this, "c_ct_input_menu_chat_aigc"));
                header.addView(icon, new LinearLayout.LayoutParams(dp(context,18), dp(context,18)));
            }
            TextView title = label(context, heading, ai ? 14 : 16,
                    ai ? (dark ? 0xffb7a1ec : 0xff483d88) : foreground);
            title.setTypeface(null, ai ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
            if(ai) title.setPadding(dp(context,6),0,0,0);
            title.setMinHeight(dp(activity, 48));
            header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            title.setGravity(Gravity.CENTER_VERTICAL);
            if (help != null) header.addView(helpButton(this, heading, help));
            TextView close = label(context, "×", 24, secondary);
            close.setGravity(Gravity.CENTER);
            close.setContentDescription("关闭面板");
            buttonSemantics(close);
            header.addView(close, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
            appearance.apply(close, UiAppearance.Role.ICON);
            close.setOnClickListener(v -> dialog.dismiss());
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            ScrollView scroll = new ScrollView(context);
            scroll.setFillViewport(false);
            scroll.setClipToPadding(false);
            body.setPadding(dp(activity, 16), dp(activity, 4), dp(activity, 16), dp(activity, 12));
            scroll.addView(body);
            content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            if (footer != null) content.addView(footer, new LinearLayout.LayoutParams(-1, -2));
            dialog = builder.setCustomTitle(header).create();
            dialog.setView(content, 0, 0, 0, 0);
            dialog.show();
            styleBottom(490);
        }

        /** Compact chat content also uses the shared dialog shell when no host dock is available. */
        void showBottomContent(View content, int preferredHeightDp) {
            dialog = builder.create();
            dialog.setView(content, 0, 0, 0, 0);
            dialog.show();
            styleBottom(preferredHeightDp);
        }

        private void styleBottom(int preferredHeightDp) {
            android.view.Window window = dialog.getWindow();
            appearance.window(window, UiAppearance.Role.SHEET);
            window.setGravity(Gravity.BOTTOM);
            window.setDimAmount(.20f);
            int available = activity.getResources().getDisplayMetrics().heightPixels;
            // Stable window bounds: hiding the IME is asynchronous. A visible-frame read
            // here could permanently size the sheet to a keyboard-reduced tiny height.
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.view.WindowMetrics metrics = activity.getWindowManager().getCurrentWindowMetrics();
                android.graphics.Insets bars = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                        android.view.WindowInsets.Type.systemBars());
                available = metrics.getBounds().height() - bars.top - bars.bottom;
            }
            int height = Math.min(dp(activity, preferredHeightDp), Math.round(available * .64f));
            window.setLayout(-1, height);
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            window.setNavigationBarColor(appearance.panelColor());
        }
    }

    /** Related settings share one card; headings and spacing provide its internal hierarchy. */
    static LinearLayout section(MenuPanel panel, String text) {
        return section(panel, text, null);
    }

    static LinearLayout section(MenuPanel panel, String text, String help) {
        return section(panel, text, help, null);
    }

    static LinearLayout section(MenuPanel panel, String text, String help,
            java.util.function.Consumer<View> groupMenu) {
        LinearLayout group = new LinearLayout(panel.context);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(dp(panel.context, 12), dp(panel.context, 4), dp(panel.context, 12), dp(panel.context, 6));
        panel.appearance.apply(group, panel.embeddedCategory ? UiAppearance.Role.CONTENT : UiAppearance.Role.CARD, 12);
        if(panel.embeddedCategory) group.setPadding(0,0,0,0);
        TextView title = label(panel.context, text, 15, panel.foreground);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        if (android.os.Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        LinearLayout heading = new LinearLayout(panel.context);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setMinimumHeight(dp(panel.context, 48));
        title.setPadding(0, dp(panel.context, 8), 0, dp(panel.context, 8));
        title.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(0, 0, 0, 0);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        if (help != null) heading.addView(helpButton(panel, text, help));
        if (groupMenu != null) {
            TextView more = label(panel.context, "⋮", 24, panel.secondary);
            more.setGravity(Gravity.CENTER);
            more.setContentDescription(text + "，更多操作");
            more.setLayoutParams(new LinearLayout.LayoutParams(dp(panel.context, 48), dp(panel.context, 48)));
            panel.appearance.apply(more, UiAppearance.Role.ICON);
            more.setOnClickListener(v -> groupMenu.accept(more));
            buttonSemantics(more);
            heading.addView(more);
            title.setMinHeight(dp(panel.context, 48));
            title.setGravity(Gravity.CENTER_VERTICAL);
            title.setOnLongClickListener(v -> { groupMenu.accept(more); return true; });
            if (android.os.Build.VERSION.SDK_INT >= 26) more.setTooltipText(text + "，更多操作");
        }
        if (text != null) group.addView(heading);
        View divider = new View(panel.context);
        divider.setBackgroundColor(panel.dark ? 0xff3a3a4a : 0xffdcdce2);
        if (text != null && !panel.embeddedCategory)
            group.addView(divider, new LinearLayout.LayoutParams(-1, dp(panel.context, 1)));
        LinearLayout content = new LinearLayout(panel.context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setTag(panel);
        content.setPadding(0, dp(panel.context, text == null ? 0 : 4), 0, 0);
        if(panel.embeddedCategory) {
            content.setPadding(dp(panel.context,12),dp(panel.context,6),dp(panel.context,12),dp(panel.context,6));
            panel.appearance.apply(content,UiAppearance.Role.CARD,14);
            panel.appearance.settingsCardDepth(content);
        }
        group.addView(content, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        if (panel.body.getChildCount() > 0) params.topMargin = dp(panel.context, 16);
        panel.body.addView(group, params);
        if (text != null) panel.sectionNames.put(group,text);
        return content;
    }

    /** Shared light surface for saved-record lists; callers retain their text and interaction. */
    static void addListItem(MenuPanel panel, LinearLayout parent, TextView row) {
        panel.appearance.apply(row, UiAppearance.Role.LIST_ITEM);
        row.setMinHeight(dp(panel.context, 56));
        row.setPadding(dp(panel.context, 16), dp(panel.context, 14),
                dp(panel.context, 16), dp(panel.context, 14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.leftMargin = params.rightMargin = dp(panel.context, 2);
        params.topMargin = dp(panel.context, parent.getChildCount() == 0 ? 2 : 8);
        params.bottomMargin = dp(panel.context, 2);
        parent.addView(row, params);
    }

    static void applyCapsuleStyle(TextView option, MenuPanel panel) {
        panel.appearance.apply(option, UiAppearance.Role.CHOICE);
    }

    private static final class LineIcon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean arrow;
        LineIcon(Context context, int color, boolean arrow) {
            super(context);
            this.arrow = arrow;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            canvas.scale(getWidth() / 28f, getHeight() / 28f);
            paint.setStrokeWidth(arrow ? 3f : 1.7f);
            if (arrow) {
                Path path = new Path();
                path.moveTo(7, 5); path.lineTo(21, 14); path.lineTo(7, 23);
                canvas.drawPath(path, paint);
            } else {
                canvas.drawRoundRect(3, 3, 25, 25, 4, 4, paint);
                canvas.drawLine(8, 8, 8, 10, paint);
                canvas.drawCircle(8, 13, 2.4f, paint);
                canvas.drawLine(8, 16, 8, 20, paint);
                canvas.drawLine(18, 8, 18, 12, paint);
                canvas.drawCircle(18, 15, 2.4f, paint);
                canvas.drawLine(18, 18, 18, 20, paint);
            }
            canvas.restore();
        }
    }

    static View helpButton(MenuPanel panel, String title, CharSequence message) {
        return hintButton(panel, title, message, false);
    }

    static View helpButton(MenuPanel panel, String title, CharSequence message, boolean preferAbove) {
        return hintButton(panel, title, message, false, preferAbove);
    }

    static View reminderButton(MenuPanel panel, String title, CharSequence message) {
        return hintButton(panel, title, message, true);
    }

    private static View hintButton(MenuPanel panel, String title, CharSequence message, boolean reminder) {
        return hintButton(panel, title, message, reminder, false);
    }

    private static View hintButton(MenuPanel panel, String title, CharSequence message,
            boolean reminder, boolean preferAbove) {
        android.widget.ImageButton button = new android.widget.ImageButton(panel.context);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(panel.context, 48), dp(panel.context, 48)));
        button.setPadding(dp(panel.context, 15), dp(panel.context, 15), dp(panel.context, 15), dp(panel.context, 15));
        button.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        int icon = panel.activity.getResources().getIdentifier(reminder ? "c_ct_icon_alert" : "c_ic_icon_question", "drawable", panel.activity.getPackageName());
        if (icon != 0) button.setImageDrawable(panel.activity.getDrawable(icon));
        else button.setImageResource(reminder ? android.R.drawable.ic_dialog_alert : android.R.drawable.ic_menu_help);
        if (!reminder) button.setImageTintList(ColorStateList.valueOf(panel.secondary));
        panel.appearance.apply(button, UiAppearance.Role.ICON);
        button.setContentDescription(title + (reminder ? "，查看提醒" : "，查看说明"));
        HelpBubble.bind(panel, button, message, preferAbove);
        return button;
    }

    /** Hook readiness may change after a category is cached; never change its saved value. */
    static void bindAvailability(MenuPanel panel, MenuSwitch toggle, java.util.function.BooleanSupplier ready) {
        Runnable refresh = () -> toggle.setEnabled(ready.getAsBoolean());
        refresh.run();
        panel.addPreferenceRefresher(refresh);
    }

    static MenuSwitch addSwitch(LinearLayout body, SharedPreferences prefs,
            String key, String title, String description, int foreground, int secondary) {
        return addSwitch(body, prefs, key, title, description, foreground, secondary, false);
    }

    static MenuSwitch addSwitch(LinearLayout body, SharedPreferences prefs,
            String key, String title, String description, int foreground, int secondary, boolean preferHelpAbove) {
        Context context = body.getContext();
        MenuSwitch toggle = new MenuSwitch((MenuPanel) body.getTag());
        toggle.setContentDescription(title);
        toggle.setPadding(dp(context, 4), 0, 0, 0);
        toggle.setMinWidth(dp(context, 48));
        toggle.setMinHeight(dp(context, 48));
        boolean defaultChecked = prefs != null && ConfigDefaults.booleanValue(key);
        toggle.setChecked(prefs != null && prefs.getBoolean(key, defaultChecked));
        MenuPanel panel = (MenuPanel) body.getTag();
        LinearLayout row = new SwitchRow(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(context, 52));
        TextView name = label(context, title + (description.contains("未就绪") ? "（未就绪）" : ""), 16, foreground);
        name.setCompoundDrawablesRelative(SettingsCardIcon.drawable(context,title,secondary),null,null,null);
        name.setCompoundDrawablePadding(dp(context,12));
        name.setMinHeight(dp(context, 48));
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        name.setFocusable(false);
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        name.setOnClickListener(v -> { if (toggle.isEnabled()) toggle.toggle(); });
        name.setFocusable(false);
        row.setOnClickListener(v -> { if (toggle.isEnabled()) toggle.toggle(); });
        row.setFocusable(false);
        // Children consume their own presses; the parent only draws for its exposed gaps.
        pressFeedback(name);
        pressFeedback(row);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        String help = description.replace("当前版本未就绪", "").replace("未就绪", "")
                .replaceAll("[\\s·]+$", "").trim();
        if (!help.isEmpty()) row.addView(helpButton(panel, title, help, preferHelpAbove));
        row.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
        body.addView(row, new LinearLayout.LayoutParams(-1, -2));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (prefs == null) return;
            if (toggle.readingPreferences) return;
            PreferenceChanges.putBoolean(prefs, key, checked);
            Log.i(TAG, "setting changed: " + key + "=" + checked);
        });
        if (prefs != null) panel.preferenceRefreshers.add(() -> toggle.setCheckedFromPreferences(
                prefs.getBoolean(key, defaultChecked)));
        toggle.observe(() -> {
            name.setEnabled(toggle.isEnabled());
            name.setClickable(toggle.isEnabled());
            row.setEnabled(toggle.isEnabled());
            row.setClickable(toggle.isEnabled());
            name.setText(title + (prefs == null || toggle.isEnabled() ? "" : "（未就绪）"));
            toggle.setContentDescription(title + (prefs == null || toggle.isEnabled() ? "" : "，未就绪，原因尚未确认"));
        });
        if (prefs == null) return toggle;
        AvailabilityGroup availability = panel.availability.get(body);
        if (availability == null) {
            availability = new AvailabilityGroup(panel, body);
            panel.availability.put(body, availability);
        }
        availability.toggles.add(toggle);
        toggle.observe(availability::refresh);
        return toggle;
    }

    static void bindExclusiveSwitchRows(MenuPanel panel, SharedPreferences prefs,
            MenuSwitch autoRead, MenuSwitch blockReceipt, String autoKey, String blockKey) {
        View autoRow = (View) autoRead.getParent(), blockRow = (View) blockReceipt.getParent();
        LinearLayout body = (LinearLayout) autoRow.getParent();
        int autoIndex = body.indexOfChild(autoRow);
        body.removeView(autoRow);
        LocalReveal autoReveal = new LocalReveal(panel.context, autoRow, 240);
        body.addView(autoReveal, autoIndex, new LinearLayout.LayoutParams(-1, -2));
        int blockIndex = body.indexOfChild(blockRow);
        body.removeView(blockRow);
        LocalReveal blockReveal = new LocalReveal(panel.context, blockRow, 240);
        body.addView(blockReveal, blockIndex, new LinearLayout.LayoutParams(-1, -2));
        panel.localReveals.add(autoReveal);
        panel.localReveals.add(blockReveal);
        boolean[] updating = {false};
        Runnable refresh = () -> {
            if (updating[0]) return;
            updating[0] = true;
            try {
                boolean block = prefs.getBoolean(blockKey, false);
                boolean auto = !block && prefs.getBoolean(autoKey, false);
                MenuSwitch trigger = autoRead.userChange ? autoRead
                        : blockReceipt.userChange ? blockReceipt : null;
                autoRead.setCheckedFromPreferences(auto);
                blockReceipt.setCheckedFromPreferences(block);
                autoReveal.setExpanded(!block, trigger);
                blockReveal.setExpanded(!auto, trigger);
            } finally { updating[0] = false; }
        };
        autoRead.observe(refresh);
        blockReceipt.observe(refresh);
        panel.addPreferenceRefresher(refresh);
    }

    /** Keep the icon and label readable when an inline editor cannot fit beside them. */
    private static final class SwitchRow extends LinearLayout {
        private boolean wrapped;
        private int firstHeight;
        SwitchRow(Context context) { super(context); }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            wrapped=false;
            super.onMeasure(widthSpec,heightSpec);
            if(getChildCount()<3 || !(getChildAt(0) instanceof TextView)) return;
            TextView name=(TextView)getChildAt(0);
            int minimum=(int)Math.ceil(name.getTextSize()*4)+name.getCompoundPaddingLeft()+name.getCompoundPaddingRight();
            if(name.getMeasuredWidth()>=minimum) return;
            boolean extra=false;
            for(int i=1;i<getChildCount()-1;i++) extra |= getChildAt(i).getVisibility()!=GONE;
            if(!extra) return;
            wrapped=true;
            View toggle=getChildAt(getChildCount()-1);
            int width=getMeasuredWidth();
            name.measure(MeasureSpec.makeMeasureSpec(Math.max(0,width-toggle.getMeasuredWidth()),MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
            firstHeight=Math.max(name.getMeasuredHeight(),toggle.getMeasuredHeight());
            int secondHeight=0;
            for(int i=1;i<getChildCount()-1;i++) {
                View child=getChildAt(i);
                if(child.getVisibility()==GONE) continue;
                LayoutParams bounds=(LayoutParams)child.getLayoutParams();
                child.measure(MeasureSpec.makeMeasureSpec(Math.max(0,width-bounds.leftMargin-bounds.rightMargin),MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
                secondHeight+=child.getMeasuredHeight();
            }
            setMeasuredDimension(width,Math.max(getMinimumHeight(),firstHeight+secondHeight));
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
            if(!wrapped) { super.onLayout(changed,l,t,r,b); return; }
            View name=getChildAt(0),toggle=getChildAt(getChildCount()-1);
            int width=r-l;
            int top=(firstHeight-name.getMeasuredHeight())/2;
            name.layout(0,top,name.getMeasuredWidth(),top+name.getMeasuredHeight());
            top=(firstHeight-toggle.getMeasuredHeight())/2;
            toggle.layout(width-toggle.getMeasuredWidth(),top,width,top+toggle.getMeasuredHeight());
            top=firstHeight;
            for(int i=1;i<getChildCount()-1;i++) {
                View child=getChildAt(i);
                if(child.getVisibility()==GONE) continue;
                LayoutParams bounds=(LayoutParams)child.getLayoutParams();
                int left=Math.max(bounds.leftMargin,width-bounds.rightMargin-child.getMeasuredWidth());
                child.layout(left,top,left+child.getMeasuredWidth(),top+child.getMeasuredHeight());
                top+=child.getMeasuredHeight();
            }
        }
    }

    /** Register a conditional row with its owning panel for animation cleanup. */
    static LocalReveal revealRow(MenuPanel panel, View content, long durationMillis) {
        LocalReveal reveal = new LocalReveal(panel.context, content, durationMillis);
        panel.localReveals.add(reveal);
        return reveal;
    }

    /** Hosts Soul's actual animated control; this shell only binds plugin state and touch bounds. */
    static final class MenuSwitch extends FrameLayout implements android.widget.Checkable {
        interface OnCheckedChangeListener { void onCheckedChanged(MenuSwitch button, boolean checked); }
        private final java.util.List<Runnable> observers = new java.util.ArrayList<>();
        private final CompoundButton control;
        private final java.lang.reflect.Method immediate;
        private OnCheckedChangeListener listener;
        private boolean readingPreferences;
        private boolean userChange;
        boolean wasUserChanged() { return userChange; }
        MenuSwitch(MenuPanel panel) {
            super(panel.context);
            CompoundButton candidate;
            java.lang.reflect.Method setter = null;
            try {
                Class<?> type = Class.forName("cn.android.lib.soul_view.switchcompat.SoulSwitchCompat",
                        true, panel.activity.getClassLoader());
                setter = type.getMethod("setCheckedImmediatelyNoEvent", boolean.class);
                candidate = (CompoundButton) type.getConstructor(Context.class).newInstance(panel.activity);
                type.getMethod("setTintColor", int.class).invoke(candidate, ACCENT);
            } catch (ReflectiveOperationException | LinkageError | ClassCastException error) {
                android.util.Log.w(TAG, "Host switch unavailable; using system fallback", error);
                android.widget.Switch fallback = new android.widget.Switch(panel.context);
                panel.appearance.switchControl(fallback);
                candidate = fallback;
                setter = null;
            }
            control = candidate;
            immediate = setter;
            setMinimumWidth(dp(panel.context, 48));
            setMinimumHeight(dp(panel.context, 48));
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(control, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            setOnClickListener(v -> toggle());
            control.setOnCheckedChangeListener((button, checked) -> {
                if (listener != null) listener.onCheckedChanged(this, checked);
                refresh();
            });
        }
        void setMinWidth(int width) { setMinimumWidth(width); }
        void setMinHeight(int height) { setMinimumHeight(height); }
        void observe(Runnable observer) { observers.add(observer); observer.run(); }
        void setOnCheckedChangeListener(OnCheckedChangeListener value) { listener = value; }
        @Override public boolean isChecked() { return control != null && control.isChecked(); }
        @Override public void setChecked(boolean checked) {
            if (control == null) return;
            boolean animate = isAttachedToWindow() && isLaidOut() && isShown()
                    && android.animation.ValueAnimator.areAnimatorsEnabled();
            if (animate) control.setChecked(checked);
            else {
                boolean changed = isChecked() != checked;
                setImmediately(checked);
                if (changed && listener != null) listener.onCheckedChanged(this, checked);
            }
            refresh();
        }
        private void setImmediately(boolean checked) {
            if (immediate != null) {
                try { immediate.invoke(control, checked); return; }
                catch (ReflectiveOperationException error) {
                    android.util.Log.w(TAG, "Host switch immediate state unavailable", error);
                }
            }
            // Fallback may emit a callback; suppress it because the caller dispatches once.
            control.setOnCheckedChangeListener(null);
            try { control.setChecked(checked); control.jumpDrawablesToCurrentState(); }
            finally { control.setOnCheckedChangeListener((button, value) -> {
                if (listener != null) listener.onCheckedChanged(this, value);
                refresh();
            }); }
        }
        @Override public void toggle() {
            if (!isEnabled()) return;
            boolean previous = userChange;
            userChange = true;
            try { setChecked(!isChecked()); } finally { userChange = previous; }
        }
        @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
            boolean previous = userChange;
            userChange = true;
            try { return super.dispatchTouchEvent(event); } finally { userChange = previous; }
        }
        void setCheckedFromPreferences(boolean checked) {
            boolean previous = readingPreferences;
            readingPreferences = true;
            try { setChecked(checked); }
            finally { readingPreferences = previous; }
        }
        private void refresh() {
            if (observers != null) for (Runnable observer : observers) observer.run();
        }
        @Override public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            if (control != null) control.setEnabled(enabled);
            refresh();
        }
        @Override public void setContentDescription(CharSequence description) {
            super.setContentDescription(description);
            if (control != null) control.setContentDescription(description);
        }
        @Override protected void onDetachedFromWindow() {
            setImmediately(isChecked());
            super.onDetachedFromWindow();
        }
    }

    /** A single conditional row; never installs a global layout transition. */
    static final class LocalReveal extends FrameLayout {
        private final long durationMillis;
        private android.animation.ValueAnimator animator;
        private boolean expanded, initialized;
        private float fraction;
        LocalReveal(Context context, View content) {
            this(context, content, 160);
        }
        LocalReveal(Context context, View content, long durationMillis) {
            super(context);
            this.durationMillis = durationMillis;
            ViewGroup.MarginLayoutParams previous = (ViewGroup.MarginLayoutParams) content.getLayoutParams();
            addView(content, new FrameLayout.LayoutParams(previous));
            setClipChildren(true);
        }
        void setExpanded(boolean value, View trigger) {
            if (initialized && expanded == value) {
                if (trigger == null) finish();
                return;
            }
            boolean animate = initialized && trigger != null && isAttachedToWindow()
                    && isShown() && hasWindowFocus() && getWidth() > 0
                    && android.animation.ValueAnimator.areAnimatorsEnabled();
            // A GONE row can still animate in when its parent is visible.
            if (initialized && value && trigger != null && getParent() instanceof View) {
                View parent = (View) getParent();
                animate = parent.isShown() && parent.isAttachedToWindow() && parent.hasWindowFocus()
                        && parent.getWidth() > 0 && android.animation.ValueAnimator.areAnimatorsEnabled();
            }
            initialized = true;
            if (!value && expanded) {
                long now = android.os.SystemClock.uptimeMillis();
                android.view.MotionEvent cancel = android.view.MotionEvent.obtain(now, now,
                        android.view.MotionEvent.ACTION_CANCEL, 0, 0, 0);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
            }
            expanded = value;
            setDescendantFocusability(value ? FOCUS_AFTER_DESCENDANTS : FOCUS_BLOCK_DESCENDANTS);
            if (!value) {
                boolean inputFocus = hasFocus();
                boolean accessibilityFocus = clearAccessibilityFocus(this);
                clearFocus();
                if (trigger != null) {
                    if (inputFocus) trigger.requestFocus();
                    if (accessibilityFocus) trigger.performAccessibilityAction(
                            android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
                }
            }
            setImportantForAccessibility(value ? IMPORTANT_FOR_ACCESSIBILITY_AUTO
                    : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            cancelAnimator();
            if (!animate) { finish(); return; }
            setVisibility(VISIBLE);
            android.animation.ValueAnimator next = android.animation.ValueAnimator.ofFloat(fraction, value ? 1f : 0f);
            animator = next;
            next.setDuration(durationMillis);
            next.setInterpolator(new android.view.animation.DecelerateInterpolator());
            next.addUpdateListener(a -> {
                fraction = (float) a.getAnimatedValue();
                setAlpha(fraction);
                requestLayout();
            });
            next.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) {
                    if (animator == a) { animator = null; finish(); }
                }
            });
            next.start();
        }
        private static boolean clearAccessibilityFocus(View view) {
            boolean focused = view.isAccessibilityFocused();
            if (focused) view.performAccessibilityAction(
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null);
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++)
                    focused |= clearAccessibilityFocus(group.getChildAt(i));
            }
            return focused;
        }
        private void cancelAnimator() {
            android.animation.ValueAnimator previous = animator;
            animator = null;
            if (previous != null) previous.cancel();
        }
        void finish() {
            cancelAnimator();
            fraction = expanded ? 1f : 0f;
            setAlpha(fraction);
            setVisibility(expanded ? VISIBLE : GONE);
            requestLayout();
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, heightSpec);
            setMeasuredDimension(getMeasuredWidth(), Math.round(getMeasuredHeight() * fraction));
        }
        @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
            return !expanded || super.dispatchTouchEvent(event);
        }
        @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
            return !expanded || super.dispatchKeyEvent(event);
        }
        @Override public void onWindowFocusChanged(boolean focused) {
            super.onWindowFocusChanged(focused);
            finish();
        }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (oldw > 0 && w != oldw && animator != null) finish();
        }
        @Override protected void onDetachedFromWindow() {
            finish();
            super.onDetachedFromWindow();
        }
    }

    private static boolean canAnnounce(View view) {
        if (!view.isAttachedToWindow() || !view.isShown()) return false;
        for (View current = view; current != null; current = current.getParent() instanceof View
                ? (View) current.getParent() : null) {
            if (current.getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
                return false;
        }
        return true;
    }

    static void setLiveSummary(TextView view, String text) {
        if (android.text.TextUtils.equals(view.getText(), text)) return;
        view.setAccessibilityLiveRegion(canAnnounce(view)
                && view.getText().length() > 0 ? View.ACCESSIBILITY_LIVE_REGION_POLITE
                : View.ACCESSIBILITY_LIVE_REGION_NONE);
        view.setText(text);
    }

    static final class AvailabilityGroup {
        final java.util.List<View> toggles = new java.util.ArrayList<>();
        final java.util.List<View> withInlineStatus = new java.util.ArrayList<>();
        final java.util.List<java.util.function.BooleanSupplier> partial = new java.util.ArrayList<>();
        final TextView action;
        final TextView status;
        private final Runnable update = this::refreshNow;
        AvailabilityGroup(MenuPanel panel, LinearLayout body) {
            status = addSummary(body, "部分功能未就绪，原因尚未确认");
            status.setVisibility(View.GONE);
            action = addSummary(body, "功能兼容性检测");
            action.setTextColor(panel.accentText);
            action.setMinHeight(dp(panel.context, 48));
            action.setGravity(Gravity.CENTER_VERTICAL);
            action.setOnClickListener(v -> showHookDiagnostics(panel.activity, panel.dark, false));
            buttonSemantics(action);
            action.setVisibility(View.GONE);
        }
        void refresh() {
            status.removeCallbacks(update);
            if (status.isAttachedToWindow()) status.post(update);
            else refreshNow();
        }
        private void refreshNow() {
            boolean unavailable = false;
            boolean unexplained = false;
            for (View toggle : toggles) {
                unavailable |= !toggle.isEnabled();
                unexplained |= !toggle.isEnabled() && !withInlineStatus.contains(toggle);
            }
            boolean partlyAvailable = false;
            for (java.util.function.BooleanSupplier value : partial) partlyAvailable |= value.getAsBoolean();
            int visibility = unavailable || partlyAvailable ? View.VISIBLE : View.GONE;
            if (status.getVisibility() == (unexplained ? View.VISIBLE : View.GONE)
                    && action.getVisibility() == visibility) return;
            View parent = (View) status.getParent();
            status.setAccessibilityLiveRegion(canAnnounce(parent)
                    ? View.ACCESSIBILITY_LIVE_REGION_POLITE : View.ACCESSIBILITY_LIVE_REGION_NONE);
            status.setVisibility(unexplained ? View.VISIBLE : View.GONE);
            action.setVisibility(visibility);
        }
    }

    static TextView addSummary(LinearLayout body, String text) {
        MenuPanel panel = (MenuPanel) body.getTag();
        TextView summary = label(body.getContext(), text, 13, panel.secondary);
        summary.setPadding(0, dp(body.getContext(), 4), 0, dp(body.getContext(), 8));
        summary.setLineSpacing(dp(body.getContext(), 2), 1f);
        body.addView(summary, new LinearLayout.LayoutParams(-1, -2));
        return summary;
    }

    static void bindSwitchContent(MenuSwitch toggle, View content) {
        ((MenuSwitch) toggle).observe(() -> content.setVisibility(
                toggle.isChecked() && toggle.isEnabled() ? View.VISIBLE : View.GONE));
    }

    /** Content-sized editor alongside the title and switch, using the common visibility rule. */
    static void attachSwitchEditor(MenuSwitch toggle, MenuPanel panel, View editor) {
        LinearLayout row = (LinearLayout) toggle.getParent();
        LinearLayout.LayoutParams bounds = new LinearLayout.LayoutParams(-2, -2);
        bounds.leftMargin = dp(panel.context, 4);
        bounds.rightMargin = dp(panel.context, 4);
        row.addView(editor, row.indexOfChild(toggle), bounds);
        bindSwitchContent(toggle, editor);
    }

    /** Inline previews share the switch visibility and stay immediately beneath its row. */
    static void attachSwitchContentBelow(MenuSwitch toggle, View content) {
        View row = (View) toggle.getParent();
        LinearLayout body = (LinearLayout) row.getParent();
        body.addView(content, body.indexOfChild(row) + 1, new LinearLayout.LayoutParams(-1, -2));
        bindSwitchContent(toggle, content);
    }

    static void attachSwitchPreview(MenuSwitch toggle, MenuPanel panel, Runnable action) {
        attachSwitchAction(toggle, panel, "预览", "预览" + toggle.getContentDescription(), action);
    }

    /** Shared enabled-only inline action used by previews and format editors. */
    static TextView attachSwitchAction(MenuSwitch toggle, MenuPanel panel, String title,
            String description, Runnable action) {
        LinearLayout row = (LinearLayout) toggle.getParent();
        TextView preview = label(panel.context, title, 14, panel.accentText);
        preview.setMinWidth(dp(panel.context, 48));
        preview.setMinHeight(dp(panel.context, 48));
        preview.setGravity(Gravity.CENTER);
        preview.setContentDescription(description);
        panel.appearance.apply(preview, UiAppearance.Role.INLINE_ACTION);
        preview.setOnClickListener(v -> {
            if (toggle.isChecked() && toggle.isEnabled()) action.run();
        });
        buttonSemantics(preview);
        LinearLayout.LayoutParams previewBounds = new LinearLayout.LayoutParams(-2, -2);
        previewBounds.leftMargin = dp(panel.context, 8);
        previewBounds.rightMargin = dp(panel.context, 4);
        row.addView(preview, row.indexOfChild(toggle), previewBounds);
        bindSwitchContent(toggle, preview);
        return preview;
    }

    /** Preserve the host drawable while adding one consistent action role and bounded feedback. */
    static void buttonSemantics(View view) {
        view.setFocusable(true);
        pressFeedback(view);
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityEvent(View host,
                    android.view.accessibility.AccessibilityEvent event) {
                super.onInitializeAccessibilityEvent(host, event);
                event.setClassName(android.widget.Button.class.getName());
            }
            @Override public void onInitializeAccessibilityNodeInfo(View host,
                    android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Button.class.getName());
            }
        });
    }

    static void singleChoiceGroup(LinearLayout choices, TextView heading, int count) {
        choices.setId(View.generateViewId());
        if (heading != null) heading.setLabelFor(choices.getId());
        choices.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        choices.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host,
                    android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.RadioGroup.class.getName());
                info.setCollectionInfo(android.view.accessibility.AccessibilityNodeInfo.CollectionInfo.obtain(
                        1, count, false,
                        android.view.accessibility.AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_SINGLE));
            }
        });
    }

    static void singleChoiceOption(TextView option, int index) {
        option.setFocusable(true);
        pressFeedback(option);
        option.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityEvent(View host,
                    android.view.accessibility.AccessibilityEvent event) {
                super.onInitializeAccessibilityEvent(host, event);
                event.setClassName(android.widget.RadioButton.class.getName());
                event.setChecked(host.isSelected());
            }
            @Override public void onInitializeAccessibilityNodeInfo(View host,
                    android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.RadioButton.class.getName());
                info.setCheckable(true);
                info.setChecked(host.isSelected());
                info.setCollectionItemInfo(android.view.accessibility.AccessibilityNodeInfo.CollectionItemInfo.obtain(
                        0, 1, index, 1, false, host.isSelected()));
            }
        });
    }

    private static void pressFeedback(View view) {
        // A foreground ripple does not replace Soul's stateful pill/card backgrounds.
        if (view.getForeground() != null
                || view.getBackground() instanceof android.graphics.drawable.RippleDrawable) return;
        android.util.TypedValue feedback = new android.util.TypedValue();
        if (view.getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, feedback, true) && feedback.resourceId != 0)
            view.setForeground(view.getContext().getDrawable(feedback.resourceId));
    }

    private static View find(Activity activity, String name) {
        int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
        return id == 0 ? null : activity.findViewById(id);
    }

    static TextView label(Context context, String value, int sp, int color) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        return text;
    }

    /** Text keeps the Soul hue while meeting body-text contrast on both menu surfaces. */
    private static int readableText(int color, boolean dark, int... surfaces) {
        int target = dark ? Color.WHITE : Color.BLACK;
        for (int step = 0; step <= 100; step++) {
            float amount = step / 100f;
            int candidate = Color.rgb(
                    Math.round(Color.red(color) + (Color.red(target) - Color.red(color)) * amount),
                    Math.round(Color.green(color) + (Color.green(target) - Color.green(color)) * amount),
                    Math.round(Color.blue(color) + (Color.blue(target) - Color.blue(color)) * amount));
            boolean readable = true;
            for (int surface : surfaces) {
                double a = Color.luminance(candidate), b = Color.luminance(surface);
                if ((Math.max(a, b) + .05) / (Math.min(a, b) + .05) < 4.5) readable = false;
            }
            if (readable) return candidate;
        }
        return target;
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
