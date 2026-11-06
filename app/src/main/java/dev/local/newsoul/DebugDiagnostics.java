package dev.local.newsoul;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;

/** Explicit, bounded diagnostic sessions. Never forwards detailed events to logcat. */
final class DebugDiagnostics {
    static final long DURATION = 30 * 60 * 1000L;
    static final ScheduledExecutorService IO = Executors.newSingleThreadScheduledExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<String> QUEUE = new ArrayList<>();
    private static final Map<String,String> REGISTRATION = new LinkedHashMap<>();
    private static final String RUN = UUID.randomUUID().toString();
    private static volatile long until, bytes;
    private static volatile String status = "未开启";
    private static Context context;
    private static SharedPreferences prefs;
    private static DebugStore store;
    private static String framework = "unknown";
    private static int dropped;
    private static long sequence;

    static void install(XposedModule module) {
        framework = module.getFrameworkName() + " / " + module.getFrameworkVersion() + " / API " + module.getApiVersion();
        try {
            module.hook(Application.class.getDeclaredMethod("onCreate"))
                    .setId("new-soul/debug-init")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        init((Context) chain.getThisObject());
                        return chain.proceed();
                    });
        } catch (Throwable error) { registration("debug.lifecycle", error); }
        DebugUi.install(module);
    }
    static synchronized void init(Context value) {
        if (context != null) return;
        context = value.getApplicationContext();
        if (context == null) return;
        prefs = context.getSharedPreferences("newsoul_debug", Context.MODE_PRIVATE);
        IO.execute(() -> {
            try {
                store = new DebugStore(new File(context.getNoBackupFilesDir(), "newsoul-debug"), DebugRecipient.key());
                bytes = store.size();
                long deadline = prefs.getLong("until", 0), now = System.currentTimeMillis();
                status = prefs.getString("status", "未开启");
                if (deadline > now && deadline - now <= DURATION && bytes < DebugStore.LIMIT) {
                    until = deadline; status = "正在记录"; snapshot("process_resume");
                } else if (deadline != 0) stop("已自动停止");
            } catch (Throwable error) { stop("记录不可用，请清除后重试"); }
        });
        IO.scheduleAtFixedRate(() -> {
            try {
                if (until != 0 && !active()) stop("已到时间上限，记录已停止");
                flush();
            } catch (Throwable error) { stop("记录失败，已停止"); }
        }, 1, 1, TimeUnit.SECONDS);
    }
    static boolean active() {
        long remaining = until - System.currentTimeMillis();
        return remaining > 0 && remaining <= DURATION;
    }
    static String status() {
        return (active() ? "正在记录 · 剩余 " + Math.max(1, (until - System.currentTimeMillis() + 59999) / 60000) + " 分钟" : status)
                + "\n记录大小 " + String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
    }
    static boolean hasRecords() { synchronized (QUEUE) { return bytes > 0 || !QUEUE.isEmpty(); } }
    static void setEnabled(boolean enabled, Runnable done) {
        IO.execute(() -> {
            try {
                if (enabled) {
                    if (store == null) store = new DebugStore(new File(context.getNoBackupFilesDir(), "newsoul-debug"), DebugRecipient.key());
                    if (store.size() >= DebugStore.LIMIT - 8192) { stop("记录已满，请先导出并清除"); }
                    else {
                        until = System.currentTimeMillis() + DURATION; status = "正在记录";
                        if (!prefs.edit().putLong("until", until).putString("status", status).commit()) throw new java.io.IOException();
                        snapshot("session_start"); flush();
                    }
                } else { stop("已停止记录"); flush(); }
            } catch (Throwable error) { stop("记录失败，已停止"); }
            MAIN.post(done);
        });
    }
    private static void stop(String reason) {
        synchronized (QUEUE) {
            boolean wasRunning = until != 0;
            until = 0; status = reason;
            if (wasRunning) enqueue("session_stop", reason);
        }
        if (prefs != null) prefs.edit().putLong("until", 0).putString("status", reason).commit();
    }
    static void clear(Runnable done) {
        IO.execute(() -> {
            stop("记录已清除");
            synchronized (QUEUE) { QUEUE.clear(); dropped = 0; }
            try {
                if (store == null) store = new DebugStore(new File(context.getNoBackupFilesDir(), "newsoul-debug"), DebugRecipient.key());
                store.clear(); bytes = 0;
            }
            catch (Throwable error) { status = "清除失败，请重试"; }
            MAIN.post(done);
        });
    }
    /** Must be called on IO; flush and snapshot are serialized with clear/start. */
    static byte[] export() throws Exception { flush(); if (store == null) throw new java.io.IOException(); return store.export(); }

    static void event(String point, String facts) { synchronized (QUEUE) { if (active()) enqueue(point, facts); } }
    private static void enqueue(String point, String facts) {
        synchronized (QUEUE) {
            if (QUEUE.size() >= 256) { dropped++; return; }
            try {
                JSONObject row = new JSONObject();
                row.put("time", System.currentTimeMillis()); row.put("run", RUN); row.put("sequence", ++sequence);
                row.put("point", point); row.put("facts", facts.length() > 6000 ? facts.substring(0, 6000) : facts);
                QUEUE.add(row.toString());
            } catch (Exception ignored) {}
        }
    }
    private static void flush() throws Exception {
        if (store == null) return;
        StringBuilder lines = new StringBuilder();
        synchronized (QUEUE) {
            if (dropped > 0) {
                if (QUEUE.size() == 256) QUEUE.remove(QUEUE.size() - 1);
                enqueue("records_dropped", "count=" + dropped); dropped = 0;
            }
            for (String row : QUEUE) lines.append(row).append('\n');
            QUEUE.clear();
        }
        if (lines.length() == 0) return;
        if (!store.append(lines.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            stop("记录已满，已停止");
            synchronized (QUEUE) { QUEUE.clear(); }
        }
        bytes = store.size();
    }
    static void registration(String point, Throwable error) {
        String value = error == null ? "registered" : errorDetails(error);
        setupFact(point, value);
    }
    static void setupFact(String point, String value) {
        synchronized (REGISTRATION) { REGISTRATION.put(point, value); }
        event("registration." + point, value);
    }
    static void failure(String point, Throwable error) { event(point, errorDetails(error)); }
    static void failure(String point, String facts, Throwable error) { event(point, facts + "\n" + errorDetails(error)); }
    private static String errorDetails(Throwable error) {
        StringBuilder text = new StringBuilder();
        // Deliberately omit exception messages, which may contain URLs, text or identifiers.
        for (int cause = 0; error != null && cause < 3; cause++, error = error.getCause()) {
            text.append(error.getClass().getName());
            StackTraceElement[] trace = error.getStackTrace();
            for (int i = 0; i < Math.min(trace.length, 6); i++) {
                text.append("\n at ").append(trace[i].getClassName()).append('.').append(trace[i].getMethodName())
                        .append(':').append(trace[i].getLineNumber());
            }
            text.append('\n');
        }
        return text.toString();
    }
    private static String version(String name) {
        try {
            android.content.pm.PackageInfo info = context.getPackageManager().getPackageInfo(name, 0);
            return info.versionName + " (" + info.versionCode + ")";
        } catch (Exception error) { return "unknown"; }
    }
    private static void snapshot(String reason) {
        event(reason, "format=1; host=" + version(context.getPackageName()) + "; module=" + version(VariantFeatures.PACKAGE_ID)
                + "; sdk=" + Build.VERSION.SDK_INT + "; android=" + Build.VERSION.RELEASE + "; model=" + Build.MANUFACTURER + "/" + Build.MODEL
                + "; framework=" + framework);
        synchronized (REGISTRATION) { for (Map.Entry<String,String> entry : REGISTRATION.entrySet()) event("registration." + entry.getKey(), entry.getValue()); }
        try {
            HookDiagnostics.Result report = HookDiagnostics.scan(context);
            for (String line : report.lines) event("compatibility", line);
        } catch (Throwable error) { failure("compatibility.failed", error); }
        event("ready", "repeat=" + ChatRepeatControls.ready + "; forward=" + ChatRepeatControls.forwardReady
                + "; library=" + VoiceLibraryControls.ready);
    }
}
