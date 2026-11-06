package dev.local.newsoul;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only scan in the hooked host process, with no activity retained or preference export. */
final class HomeRuntimeBridge {
    private static final AtomicBoolean registered = new AtomicBoolean();
    static void observe(Context host, String moduleApk) {
        if (!registered.compareAndSet(false, true)) return;
        Context context = host.getApplicationContext();
        try {
            PackageInfo loaded = context.getPackageManager().getPackageArchiveInfo(moduleApk, 0);
            if (loaded == null) { registered.set(false); return; }
            final int code = loaded.versionCode;
            AtomicBoolean busy = new AtomicBoolean();
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context ignored, Intent request) {
                    String token = request.getStringExtra("token");
                    if (token == null || token.length() != 36 || !busy.compareAndSet(false, true)) return;
                    PendingResult pending = goAsync();
                    new Thread(() -> {
                        try {
                            Uri endpoint = Uri.parse("content://" + VariantFeatures.PACKAGE_ID + ".runtime");
                            Bundle response = new Bundle();
                            response.putLong("code", code);
                            Bundle challenge = context.getContentResolver().call(endpoint, "probe", token, response);
                            if (challenge == null || !challenge.getBoolean("valid")) return;
                            HookDiagnostics.Result scan = HookDiagnostics.scan(context);
                            response.putString("report", scan.report());
                            context.getContentResolver().call(endpoint, "answer", token, response);
                        } catch (Throwable error) {
                            android.util.Log.w("SoulHook102", "Home runtime check unavailable", error);
                        } finally { busy.set(false); pending.finish(); }
                    }, "newsoul-home-check").start();
                }
            };
            IntentFilter filter = new IntentFilter(VariantFeatures.PACKAGE_ID + ".CHECK_RUNTIME");
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            else context.registerReceiver(receiver, filter);
        } catch (Throwable error) {
            registered.set(false);
            android.util.Log.w("SoulHook102", "Home runtime bridge unavailable", error);
        }
    }
}
