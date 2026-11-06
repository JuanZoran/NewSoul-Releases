package dev.local.newsoul;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import android.content.SharedPreferences;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;

/** API 102 demonstration: a lifecycle hook restricted to Soul's main process. */
public final class SoulHook extends XposedModule {
    private static final String TARGET = "cn.soulapp.android";
    private static final String TAG = "SoulHook102";
    private final AtomicBoolean toastShown = new AtomicBoolean();
    private boolean targetProcess;
    private boolean installed;
    static boolean settingsHookReady;
    private String moduleApk;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        targetProcess = !param.isSystemServer() && TARGET.equals(param.getProcessName());
        if (targetProcess) {
            info("module loaded; framework=" + getFrameworkName()
                    + "; version=" + getFrameworkVersion() + "; API=" + getApiVersion());
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!targetProcess || installed || !param.isFirstPackage()
                || !TARGET.equals(param.getPackageName())) return;
        // System properties are shared across module class loaders within this process.
        synchronized (System.getProperties()) {
            String owner=System.getProperty("newsoul.active.module");
            if(owner!=null){info("Skipping duplicate module; active="+owner);return;}
            System.setProperty("newsoul.active.module",VariantFeatures.PACKAGE_ID);
        }
        info("active variant="+VariantFeatures.PACKAGE_ID);
        moduleApk = getModuleApplicationInfo().sourceDir;
        HostSymbols.initialize(param.getApplicationInfo(), getModuleApplicationInfo(), param.getClassLoader());
        DebugDiagnostics.install(this);
        SplashBlocker.install(this, param.getClassLoader());
        AdControls.install(this, param.getClassLoader());
        ChatGames.install(this, param.getClassLoader());
        ChatRecallControls.install(this, param.getClassLoader());
        InteractionPrivacyControls.install(this, param.getClassLoader());
        AutoReadControls.install(this, param.getClassLoader());
        OwnRecallControls.install(this, param.getClassLoader());
        ChatExperienceControls.install(this, param.getClassLoader());
        HostAccountHttp.install(this, param.getClassLoader());
        HostWelfareHttp.install(this, param.getClassLoader());
        AccountAutomationControls.install(this);
        ForegroundNotificationControls.install(this);
        ChatTimeControls.install(this, param.getClassLoader());
        SoulUpdateControls.install(this, param.getClassLoader());
        TeenagePopupControls.install(this, param.getClassLoader());
        ChatRepeatControls.install(this, param.getClassLoader());
        ChatGateControls.install(this, param.getClassLoader());
        SocialScoreControls.install(this, param.getClassLoader());
        PrivacyPhotoControls.install(this, param.getClassLoader());
        MediaDownloadControls.install(this, param.getClassLoader());
        PostBatchShareControls.install(this, param.getClassLoader());
        PostCopyControls.install(this, param.getClassLoader());
        PostShareControls.install(this, param.getClassLoader());
        ProfileBookmarkControls.install(this, param.getClassLoader());
        FollowDisguiseControls.install(this, param.getClassLoader());
        SquareIpLocation.install(param.getClassLoader());
        SquareFeedFilter.install(this, param.getClassLoader());
        SquareTimeOrder.install(this, param.getClassLoader());
        SquareLikeControls.install(this, param.getClassLoader());
        ProfileMomentLikes.install(this, param.getClassLoader());
        LocationOverrideControls.install(this, param.getClassLoader());
        LocationCityEntry.install(this, param.getClassLoader());
        ConfigTransferUi.install(this);
        BottomSettingsEntry.install(this, param.getClassLoader());
        VariantFeatures.install(this, param.getClassLoader());
        try {
            // A framework lifecycle method avoids guessing Soul's obfuscated business names.
            Method method = Activity.class.getDeclaredMethod("onResume");
            hook(method)
                    .setId("soul-demo/activity-onResume") // Added in API 102.
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        // Call the original chain exactly once. Its exceptions propagate normally.
                        Object result = chain.proceed();
                        Activity activity = (Activity) chain.getThisObject();
                        if (TARGET.equals(activity.getPackageName())) {
                            HomeRuntimeBridge.observe(activity, moduleApk);
                            DebugDiagnostics.init(activity);
                            DebugDiagnostics.event("page.resume", activity.getClass().getName());
                            VariantFeatures.observe(activity);
                            HookDiagnostics.checkOnStart(activity);
                            SharedPreferences prefs = PluginPreferences.get(activity);
                            if (prefs.getBoolean("page_log", true)) {
                                info("onResume: " + activity.getClass().getName());
                            }
                            if (prefs.getBoolean("startup_toast", true)
                                    && toastShown.compareAndSet(false, true)) {
                                // Activity.onResume runs on the UI thread.
                                Toast.makeText(activity, VariantFeatures.LABEL + " 已生效",
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                        return result;
                    });
            installed = true;
            info("hook installed: android.app.Activity.onResume");
            installSettingsHook(param.getClassLoader());
        } catch (Throwable error) {
            Log.e(TAG, "hook installation failed", error);
            log(Log.ERROR, TAG, "hook installation failed", error);
        }
    }

    private void installSettingsHook(ClassLoader loader) {
        try {
            Class<?> h5 = loader.loadClass("cn.soulapp.android.h5.activity.H5Activity");
            // Verified in Soul 6.35.0: private m1(boolean) configures its WebView
            // clients and loads the H5 URL. This is Soul's own private method.
            boolean legacy = HostBindings.legacyChat(loader);
            Method loadPage = legacy ? h5.getDeclaredMethod("onResume") : h5.getDeclaredMethod("m1", boolean.class);
            if (!legacy && !java.lang.reflect.Modifier.isPrivate(loadPage.getModifiers())) {
                throw new IllegalStateException("Unexpected H5Activity.m1 signature");
            }
            hook(loadPage).setId("soul-demo/private-h5-m1")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Activity activity = (Activity) chain.getThisObject();
                        activity.getWindow().getDecorView().post(() -> {
                            if (!activity.isFinishing() && !activity.isDestroyed()) {
                                SettingsUi.observeSettingsPage(activity);
                            }
                        });
                        info("private hook hit: H5Activity.m1(boolean)");
                        return result;
                    });
            info("private hook installed: H5Activity.m1(boolean)");
            settingsHookReady = true;
        } catch (Throwable error) {
            Log.e(TAG, "Soul settings hook unavailable; check app version", error);
            log(Log.ERROR, TAG, "Soul settings hook unavailable; check app version", error);
        }
    }

    private void info(String message) {
        Log.i(TAG, message);
        log(Log.INFO, TAG, message);
    }
}
