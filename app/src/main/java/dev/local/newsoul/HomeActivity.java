package dev.local.newsoul;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.Date;
import java.util.UUID;

/** Launcher-only overview. Functional settings remain in Soul's own storage and UI. */
public final class HomeActivity extends Activity {
    private static final String GITHUB = "https://github.com/JuanZoran/NewSoul-Releases";
    private static final String TELEGRAM = "https://t.me/+3Hl2lJ1X-oZlZTZl";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private HomeViews views;
    private boolean dark, resumed;
    private TextView status, confirmation, retry, restart;
    private android.widget.Switch showIcon;
    private int checkGeneration;
    private boolean changingIcon, restarting;
    private final Runnable frameworkChanged = () -> runOnUiThread(this::check);
    private ImageView statusIcon;
    private LinearLayout statusCard;
    private Uri endpoint;
    private String installedHost;
    private HomeVersionStrip versionStrip;
    private Runnable detailPoll;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        views = new HomeViews(this,dark);
        endpoint = Uri.parse("content://" + getPackageName() + ".runtime");
        getWindow().setStatusBarColor(dark ? 0xff171b26 : 0xfff8fafc);
        getWindow().setNavigationBarColor(dark ? 0xff171b26 : 0xfff8fafc);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this);scroll.setFillViewport(true);views.appearance.homeBackdrop(scroll);
        // Target 35 is edge-to-edge; consume actual system/cutout insets without assuming status height.
        scroll.setOnApplyWindowInsetsListener((v,insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.displayCutout());
                v.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        HomeElasticLayout content = new HomeElasticLayout(this);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        header(content);
        LinearLayout running = views.card("运行状态");
        statusCard = running;
        views.appearance.homeStatus(running, UiAppearance.HomeState.PENDING);
        statusIcon = new ImageView(this);
        LinearLayout.LayoutParams iconBounds = new LinearLayout.LayoutParams(views.dp(46), views.dp(46));
        iconBounds.gravity = Gravity.CENTER_HORIZONTAL;
        iconBounds.topMargin = views.dp(10);
        running.addView(statusIcon, iconBounds);
        status = views.text("检查中", 27, views.foreground, true);
        status.setGravity(Gravity.CENTER);
        views.add(running, status, 6);
        confirmation = views.text("", 14, views.secondary, false);
        confirmation.setGravity(Gravity.CENTER);
        views.add(running, confirmation, 4);
        views.add(content, running, 20);
        compatibility(content);
        restart = views.action("打开 Soul", -1, true, this::openSoul);
        restart.setTextSize(16);
        restart.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        restart.setMinHeight(views.dp(56));
        restart.setPadding(views.dp(10), views.dp(6), views.dp(10), views.dp(6));
        LinearLayout restartGroup = views.column();
        views.add(restartGroup, restart, 0);
        TextView restartHint = views.text("一键强行停止并启动Soul", 10, views.secondary, false);
        restartHint.setGravity(Gravity.CENTER);
        views.add(restartGroup, restartHint, 5);
        views.add(content, restartGroup, 22);
        views.pair(content, views.action("检测详情", -1, false, this::details),
                views.action("检查更新", -1, false, () -> UpdateCheckUi.show(this, dark)), 12);
        views.add(content, views.link("GitHub", "JuanZoran/NewSoul-Releases", HomeGlyph.GITHUB,
                () -> open(GITHUB)), 18);
        views.add(content, views.link("Telegram", TELEGRAM, HomeGlyph.TELEGRAM,
                () -> open(TELEGRAM)), 10);
        showIcon = new android.widget.Switch(this);
        showIcon.setText("显示桌面图标");
        showIcon.setTextSize(16);
        showIcon.setTextColor(views.foreground);
        showIcon.setMinHeight(views.dp(52));
        views.appearance.compactSwitchControl(showIcon);
        refreshIcon();
        showIcon.setOnCheckedChangeListener((button, checked) -> {
            if (changingIcon) return;
            if (checked) { setIconHidden(false); return; }
            refreshIcon();
            SettingsUi.MenuPanel panel = new SettingsUi.MenuPanel(this, dark);
            panel.body.addView(SettingsUi.label(panel.context,
                    "隐藏后可从 LSPosed 模块页面打开应用", 14, panel.foreground));
            panel.builder.setNegativeButton("隐藏", (dialog, which) -> setIconHidden(true));
            panel.show("隐藏桌面图标", "取消");
        });
        views.add(content, showIcon, 12);
        retry = views.quiet("重新检查", HomeGlyph.REFRESH, this::check);
        views.add(content, retry, 10);
        // Preserve spacing within action/link groups, expand only their boundaries.
        for (int index : new int[]{1, 2, 3, 5, 7, 8})
            content.expandBefore(content.getChildAt(index));
        setContentView(scroll);
    }
    private void header(LinearLayout parent) {
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon=new ImageView(this);int resource=getResources().getIdentifier("ic_launcher","mipmap",getPackageName());
        icon.setImageResource(resource);icon.setContentDescription("NewSoul");
        header.addView(icon,new LinearLayout.LayoutParams(views.dp(36),views.dp(36)));
        LinearLayout brand=views.column();
        SpannableString name=new SpannableString("NewSoul");
        name.setSpan(new ForegroundColorSpan(SettingsUi.ACCENT),3,7,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView title=views.text("",22,views.foreground,true);title.setTypeface(SettingsUi.brandTypeface(this));title.setText(name);brand.addView(title);
        String version="";try {version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
        catch(Exception ignored) {} brand.addView(views.text(version,14,views.secondary,false));
        LinearLayout.LayoutParams brandSize=new LinearLayout.LayoutParams(-2,-2);brandSize.leftMargin=views.dp(8);
        header.addView(brand,brandSize);
        SettingsUi.MenuPanel panel=new SettingsUi.MenuPanel(this,dark);
        TextView donation = SettingsUi.donationInvitation(panel, Gravity.END);
        LinearLayout.LayoutParams donationBounds = new LinearLayout.LayoutParams(0, -2, 1);
        donationBounds.leftMargin = views.dp(8);
        header.addView(donation, donationBounds);
        View help=SettingsUi.helpButton(panel,"NewSoul","长按 Soul 底部导航图标可打开插件设置");
        if(help instanceof ImageButton) ((ImageButton)help).setImageDrawable(new HomeGlyph(HomeGlyph.HELP,views.secondary));
        header.addView(help);views.add(parent,header,0);
    }
    private void compatibility(LinearLayout parent) {
        refreshCompatibility();
        SettingsUi.MenuPanel panel = new SettingsUi.MenuPanel(this, dark);
        LinearLayout section = views.column();
        section.setPadding(views.dp(14), views.dp(8), views.dp(14), views.dp(12));
        views.appearance.homeSurface(section, false, false);
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(views.text("版本适配", 16, views.foreground, true));
        String copy = "如需适配其他版本，附任意金额捐赠截图联系我(TG)";
        SpannableString message = new SpannableString(copy + "\u00a0\u00a0");
        helpLink(message, "捐赠", panel.accentText, () -> DonationUi.show(this, dark));
        helpLink(message, "联系我", panel.accentText,
                () -> open("https://t.me/JuanZoranNewSoulReleaseBot"));
        TextView caption = new TextView(this);
        SettingsUi.styleTitleCaption(caption, panel, Gravity.END);
        caption.setText(message);
        caption.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        LinearLayout.LayoutParams captionBounds = new LinearLayout.LayoutParams(0, -2, 1);
        captionBounds.leftMargin = views.dp(8);
        heading.addView(caption, captionBounds);
        section.addView(heading);
        versionStrip = new HomeVersionStrip(views, HomeCheck.VERSIONS);
        versionStrip.select(installedHost);
        views.add(section, versionStrip, 2);
        views.add(parent, section, 18);
    }
    private static void helpLink(SpannableString text, String label, int color, Runnable action) {
        int start = text.toString().indexOf(label);
        if (start < 0) return;
        text.setSpan(new android.text.style.ClickableSpan() {
            @Override public void onClick(View view) { action.run(); }
            @Override public void updateDrawState(android.text.TextPaint paint) {
                paint.setColor(color);
                paint.setUnderlineText(false);
            }
        }, start, start + label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    private void refreshCompatibility() {
        try { installedHost = getPackageManager().getPackageInfo(HomeStatusProvider.HOST, 0).versionName; }
        catch (Exception absent) { installedHost = null; }
        if (versionStrip != null) versionStrip.select(installedHost);
    }
    private android.content.ComponentName launcher() {
        return new android.content.ComponentName(getPackageName(), "dev.local.newsoul.Launcher");
    }
    private void refreshIcon() {
        changingIcon = true;
        showIcon.setChecked(getPackageManager().getComponentEnabledSetting(launcher())
                == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        changingIcon = false;
    }
    private void setIconHidden(boolean hidden) {
        try {
            HomeApplication.setVisible(this, !hidden);
        } catch (RuntimeException error) {
            Toast.makeText(this, "无法修改桌面图标，请重试", Toast.LENGTH_LONG).show();
        }
        refreshIcon();
    }
    @Override public void onResume() {
        super.onResume(); resumed = true; refreshIcon();
        HomeFramework.observe(frameworkChanged); check();
        // The dialog survives a trip to the background; its original deadline does too.
        if (detailPoll != null) handler.post(detailPoll);
    }
    @Override public void onPause() {
        resumed = false; checkGeneration++; handler.removeCallbacksAndMessages(null); super.onPause();
    }
    @Override public void onDestroy() {
        cancelDetails();
        super.onDestroy();
    }
    private void displayStatus(String title, String next, UiAppearance.HomeState state) {
        SettingsUi.setLiveSummary(status, title);
        confirmation.setVisibility(next.isEmpty() ? View.GONE : View.VISIBLE);
        SettingsUi.setLiveSummary(confirmation, next);
        int color = views.appearance.homeStatus(statusCard, state);
        status.setTextColor(color);
        statusIcon.setImageDrawable(new HomeGlyph(state == UiAppearance.HomeState.ACTIVE
                ? HomeGlyph.CHECK : HomeGlyph.HELP, color));
        retry.setEnabled(true); retry.setAlpha(1);
    }
    private void check() {
        if (!resumed) return;
        refreshCompatibility();
        final int generation = ++checkGeneration;
        displayStatus("检查中", "", UiAppearance.HomeState.PENDING);
        retry.setEnabled(false); retry.setAlpha(.5f);
        if (installedHost == null) {
            displayStatus("未安装 Soul", "请先安装 Soul", UiAppearance.HomeState.FAILED);
            return;
        }
        io.github.libxposed.service.XposedService service = HomeFramework.service;
        if (service == null) {
            handler.postDelayed(() -> {
                if (resumed && generation == checkGeneration)
                    displayStatus("未确认激活", "请检查 LSPosed 模块开关", UiAppearance.HomeState.PENDING);
            }, 1200);
            return;
        }
        new Thread(() -> {
            String title, next;
            boolean active = false;
            try {
                boolean scoped = service.getScope().contains(HomeStatusProvider.HOST);
                active = scoped;
                title = scoped ? "已激活" : "未勾选 Soul";
                next = scoped ? "" : "请在 LSPosed 作用域中勾选 Soul";
                if (installedHost == null) { title = "未安装 Soul"; next = "请先安装 Soul"; active = false; }
            } catch (RuntimeException unavailable) {
                title = "检测失败"; next = "请重新检查或确认 LSPosed 是否正常运行";
            }
            final String resultTitle = title, resultNext = next;
            final boolean resultActive = active;
            handler.post(() -> {
                if (resumed && generation == checkGeneration)
                    displayStatus(resultTitle, resultNext, resultActive
                            ? UiAppearance.HomeState.ACTIVE : UiAppearance.HomeState.FAILED);
            });
        }, "newsoul-framework-check").start();
    }
    private void cancelDetails() {
        if (detailPoll != null) handler.removeCallbacks(detailPoll);
        detailPoll = null;
    }
    private void details() {
        cancelDetails();
        SettingsUi.MenuPanel panel = new SettingsUi.MenuPanel(this, dark);
        TextView result = SettingsUi.label(panel.context, "正在确认 Soul 内运行状态", 14, panel.foreground);
        panel.body.addView(result);
        panel.show("检测详情", "返回");
        try {
            String token = UUID.randomUUID().toString();
            getContentResolver().call(endpoint, "begin", token, null);
            sendBroadcast(new Intent(getPackageName() + ".CHECK_RUNTIME")
                    .setPackage(HomeStatusProvider.HOST).putExtra("token", token));
            long until = android.os.SystemClock.elapsedRealtime() + HomeCheck.TIMEOUT;
            Runnable poll = new Runnable() {
                public void run() {
                    if (detailPoll != this || !resumed) return;
                    if (panel.dialog == null || !panel.dialog.isShowing()) { cancelDetails(); return; }
                    try {
                        Bundle response = getContentResolver().call(endpoint, "read", null, null);
                        if (response != null && response.getBoolean("confirmed")) {
                            SettingsUi.setLiveSummary(result, response.getString("report"));
                            cancelDetails(); return;
                        }
                    } catch (RuntimeException unavailable) {
                        SettingsUi.setLiveSummary(result, "无法确认 Soul 内运行状态\n请打开 Soul 后重试");
                        cancelDetails(); return;
                    }
                    if (android.os.SystemClock.elapsedRealtime() >= until) {
                        SettingsUi.setLiveSummary(result, "尚未确认 Soul 内运行状态\n请打开 Soul 后重试");
                        cancelDetails(); return;
                    }
                    handler.postDelayed(this, 250);
                }
            };
            detailPoll = poll;
            panel.dialog.setOnDismissListener(dialog -> {
                if (detailPoll == poll) cancelDetails();
            });
            handler.postDelayed(poll, 200);
        } catch (RuntimeException unavailable) {
            cancelDetails();
            SettingsUi.setLiveSummary(result, "无法确认 Soul 内运行状态\n请打开 Soul 后重试");
        }
    }
    private void openSoul() {
        if (restarting) return;
        Intent intent = getPackageManager().getLaunchIntentForPackage(HomeStatusProvider.HOST);
        if (intent == null) { Toast.makeText(this, "未安装 Soul", Toast.LENGTH_SHORT).show(); return; }
        restarting = true;
        restart.setEnabled(false); restart.setAlpha(.5f);
        Toast.makeText(this, "正在重启 Soul，首次使用需授予 Root 权限", Toast.LENGTH_LONG).show();
        new Thread(() -> {
            boolean stopped;
            try { stopped = HomeRestart.stop(android.os.Process.myUid() / 100000); }
            catch (Exception error) { android.util.Log.w("NewSoulHome", "Root restart unavailable", error); stopped = false; }
            final boolean success = stopped;
            runOnUiThread(() -> {
                restarting = false;
                restart.setEnabled(true); restart.setAlpha(1);
                if (!success) {
                    Toast.makeText(this, "强行停止失败，请授予 Root 权限后重试", Toast.LENGTH_LONG).show();
                    return;
                }
                try { startActivity(intent); }
                catch (RuntimeException error) {
                    Toast.makeText(this, "Soul 已停止，启动失败，请从桌面打开", Toast.LENGTH_LONG).show();
                }
            });
        }, "newsoul-restart").start();
    }
    private void open(String address) {
        try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(address)));}
        catch(RuntimeException error) {Toast.makeText(this,"无法打开链接，请安装浏览器",Toast.LENGTH_SHORT).show();}
    }
}
