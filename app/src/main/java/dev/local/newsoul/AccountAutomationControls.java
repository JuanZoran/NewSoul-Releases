package dev.local.newsoul;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.util.concurrent.*;
import org.json.JSONObject;
import io.github.libxposed.api.*;

final class AccountAutomationControls {
    static final String SIGN = "account_auto_sign_v1", HIDE = "account_auto_hide_v1";
    static volatile String signStatus = "", hideStatus = "";
    static boolean ready;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Object WORK = new Object();
    private static boolean busy, pending;
    private static volatile long revision;
    private static long appliedRevision;
    private static volatile long pausedAt, epoch;
    private static long lastSignAttempt = -1, lastHideAttempt = -1, hiddenEpoch = -1;
    private static String activeAccount = "";

    static void install(XposedModule module) {
        try {
            module.hook(Instrumentation.class.getDeclaredMethod("callActivityOnResume", Activity.class))
                .setId("new-soul/account-resume").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (pausedAt > 0 && SystemClock.elapsedRealtime() - pausedAt > 30000) epoch++;
                    pausedAt = 0;
                    kick(); return result;
                });
            module.hook(Instrumentation.class.getDeclaredMethod("callActivityOnPause", Activity.class))
                .setId("new-soul/account-pause").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> { pausedAt = SystemClock.elapsedRealtime(); return chain.proceed(); });
            ready = true;
            module.log(android.util.Log.INFO, "SoulHook102", "account automation registered");
            DebugDiagnostics.registration("account.automation", null);
        } catch (Throwable error) { DebugDiagnostics.registration("account.automation", error); module.log(android.util.Log.ERROR, "SoulHook102", "account automation unavailable", error); }
    }
    static void kick() {
        Context context = HostAccountHttp.context();
        if (context == null || !HostAccountHttp.available()) return;
        SharedPreferences prefs = PluginPreferences.get(context);
        if (!prefs.getBoolean(SIGN, false) && !prefs.getBoolean(HIDE, false) && !SignInAutomation.enabled(prefs)) return;
        synchronized (WORK) {
            pending = true;
            if (busy) return;
            busy = true;
        }
        try { IO.execute(() -> drain(context, prefs)); }
        catch (RejectedExecutionException unavailable) {
            synchronized (WORK) { busy = false; pending = false; }
            DebugDiagnostics.failure("account.automation.queue", unavailable);
        }
    }
    private static void drain(Context context, SharedPreferences prefs) {
        boolean released = false;
        try {
            while (true) {
                synchronized (WORK) {
                    if (!pending) { busy = false; released = true; return; }
                    pending = false;
                    if (appliedRevision != revision) {
                        lastSignAttempt = lastHideAttempt = -1;
                        hiddenEpoch = -1;
                        appliedRevision = revision;
                        SignInAutomation.resetAttempts();
                    }
                }
                // A transport callback may kick this worker. Coalesce it into one fresh state check;
                // attempt timestamps still prevent that check from issuing another side effect.
                try { run(context, prefs); }
                catch (Exception error) { DebugDiagnostics.failure("account.automation.run", error); }
            }
        } finally {
            // Also release scheduling after an Error; do not silently swallow a fatal failure.
            if (!released) {
                boolean retry;
                synchronized (WORK) { busy = false; retry = pending; pending = false; }
                if (retry) kick();
            }
        }
    }
    private static void run(Context context, SharedPreferences prefs) {
        String account = HostAccountHttp.account();
        if (account.isEmpty()) return;
        if (!account.equals(activeAccount)) {
            activeAccount = account; lastSignAttempt = lastHideAttempt = -1; hiddenEpoch = -1;
            signStatus = hideStatus = "";
        }
        long now = SystemClock.elapsedRealtime();
        SharedPreferences state = context.getSharedPreferences("newsoul-account-automation-state", 0);
        String day = SignInTasks.day();
        if (prefs.getBoolean(SIGN, false) && (lastSignAttempt < 0 || now - lastSignAttempt > 300000)) {
            lastSignAttempt = now;
            long runGeneration = generation();
            java.util.function.BooleanSupplier valid = () -> prefs.getBoolean(SIGN, false)
                    && runGeneration == generation() && account.equals(HostAccountHttp.account()) && day.equals(SignInTasks.day());
            String key = account + ":verified-sign-v2";
            try {
                if (day.equals(state.getString(key, ""))) signStatus = "今日签到已完成";
                else {
                    signStatus = "正在检查";
                    SignInTasks.Result result = SignInTasks.coins((url, post, data) -> HostAccountHttp.request(account,
                            url, post, new HostRequestScope(30000, valid)));
                    if (valid.getAsBoolean()) {
                        signStatus = result.message;
                        if (result.complete) state.edit().putString(key, day).apply();
                    }
                }
            } catch (Exception error) {
                if (valid.getAsBoolean()) signStatus = "签到未完成：" + safeError(error);
                DebugDiagnostics.failure("account.sign", error);
            }
        }
        if (account.equals(HostAccountHttp.account()) && prefs.getBoolean(HIDE, false) && hiddenEpoch != epoch
                && (lastHideAttempt < 0 || now - lastHideAttempt > 60000)) {
            lastHideAttempt = now;
            long attemptEpoch = epoch;
            try {
                JSONObject json = HostAccountHttp.request(account,
                    "https://api-user.soulapp.cn/setting/visible/save?isVisible=0", true,
                    new HostRequestScope(30000, () -> prefs.getBoolean(HIDE, false)));
                if (json.optInt("code") != 10001 || !json.optBoolean("success"))
                    throw new IllegalStateException("服务返回 " + json.optInt("code"));
                hiddenEpoch = attemptEpoch; hideStatus = "已设为隐身";
            } catch (Exception error) {
                hideStatus = "隐身未完成：" + safeError(error);
                DebugDiagnostics.failure("account.hide", error);
            }
        }
        if (account.equals(HostAccountHttp.account())) SignInAutomation.run(context, prefs, account, generation());
    }
    private static String safeError(Exception error) {
        return error instanceof IllegalStateException ? error.getMessage() : "请求失败，稍后重试";
    }
    static void changed() {
        // Only called for account switches, not every host request.
        synchronized (WORK) { revision++; }
        kick();
    }
    static long generation() { return revision; }
}
