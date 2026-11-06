package dev.local.newsoul;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/** Shares the account automation worker; each account/day/task owns its status and mutation journal. */
final class SignInAutomation {
    private static final ConcurrentHashMap<String, String> STATUS = new ConcurrentHashMap<>();
    private static final java.util.Map<String, Long> ATTEMPTS = new java.util.HashMap<>();
    static boolean enabled(SharedPreferences prefs) {
        for (String[] item : SignInTasks.ITEMS) if (prefs.getBoolean(item[0], false)) return true;
        return false;
    }
    static String status(String key) { return STATUS.getOrDefault(HostAccountHttp.account() + ":" + SignInTasks.day() + ":" + key, ""); }
    static void resetAttempts() { ATTEMPTS.clear(); }
    static void run(Context context, SharedPreferences prefs, String account, long generation) {
        if (!HostWelfareHttp.ready) return;
        SharedPreferences state = context.getSharedPreferences("newsoul-account-automation-state", 0);
        String day = SignInTasks.day();
        STATUS.keySet().removeIf(k -> !k.startsWith(account + ":" + day + ":"));
        for (String[] item : SignInTasks.ITEMS) {
            String key = item[0], owner = account + ":" + day + ":" + key;
            BooleanSupplier valid = () -> generation == AccountAutomationControls.generation()
                    && prefs.getBoolean(key, false) && HostAccountHttp.available()
                    && account.equals(HostAccountHttp.account()) && day.equals(SignInTasks.day());
            if (!valid.getAsBoolean()) continue;
            if (day.equals(state.getString(account + ":complete:" + key, ""))) {
                STATUS.putIfAbsent(owner, key.equals(SignInTasks.PLAY) ? "今日已签到" : "今日已抽奖");
                AvatarRewardNotifications.deliver(context, state, account, key, day, valid);
                continue;
            }
            long now = SystemClock.elapsedRealtime(), last = ATTEMPTS.getOrDefault(owner, -1L);
            if (last >= 0 && now - last < 300000) continue;
            ATTEMPTS.put(owner, now);
            if (ATTEMPTS.size() > 128) ATTEMPTS.keySet().removeIf(k -> !k.startsWith(account + ":" + day + ":"));
            STATUS.put(owner, "正在检查");
            HostWelfareHttp.beginTask();
            try {
                SignInTasks.Journal journal = new SignInTasks.Journal() {
                    public boolean pending(String operation) { return day.equals(state.getString(account + ":pending:" + key + ":" + operation, "")); }
                    public void begin(String operation) throws Exception {
                        if (!valid.getAsBoolean()) throw new IllegalStateException("请求状态已变化");
                        if (!state.edit().putString(account + ":pending:" + key + ":" + operation, day).commit())
                            throw new IllegalStateException("无法保存任务状态，请重试");
                    }
                };
                SignInTasks.Transport transport = (url, post, data) -> HostWelfareHttp.request(account, url, post, data, valid);
                SignInTasks.Result result = SignInTasks.run(key, transport, journal, day);
                if (!valid.getAsBoolean()) continue;
                if (result.complete) {
                    AvatarRewardNotifications.resolve(result, transport);
                    if (!valid.getAsBoolean()) continue;
                    if (!AvatarRewardNotifications.complete(state, account, key, day, result))
                        throw new IllegalStateException("无法保存任务状态，请重试");
                    AvatarRewardNotifications.deliver(context, state, account, key, day, valid);
                }
                STATUS.put(owner, result.message);
            } catch (Exception error) {
                if (!valid.getAsBoolean()) { STATUS.remove(owner); continue; }
                STATUS.put(owner, "未完成：" + (error instanceof IllegalStateException ? error.getMessage() : "请求失败，请在 Soul 中检查后重试"));
                HostWelfareHttp.taskFailure(key, error);
                DebugDiagnostics.failure("account.welfare." + key, error);
            }
        }
    }
    private SignInAutomation() {}
}
