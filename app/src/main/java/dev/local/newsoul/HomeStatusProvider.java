package dev.local.newsoul;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;

/** Only the module may start/read a check; only Soul's UID may answer it. No config IPC. */
public final class HomeStatusProvider extends ContentProvider {
    static final String HOST = "cn.soulapp.android";
    private final HomeCheck check = new HomeCheck();
    private Bundle result = new Bundle();
    @Override public boolean onCreate() { return true; }
    @Override public synchronized Bundle call(String method, String token, Bundle data) {
        int caller = Binder.getCallingUid();
        if ("answer".equals(method) || "probe".equals(method)) {
            try {
                if (caller != getContext().getPackageManager().getApplicationInfo(HOST, 0).uid)
                    throw new SecurityException("Only Soul may confirm runtime");
            } catch (android.content.pm.PackageManager.NameNotFoundException absent) {
                throw new SecurityException("Soul is unavailable");
            }
            if ("probe".equals(method)) {
                Bundle reply = new Bundle();
                reply.putBoolean("valid", data != null && check.valid(token,
                        SystemClock.elapsedRealtime(), data.getLong("code")));
                return reply;
            }
            if (data == null || !check.accept(token, SystemClock.elapsedRealtime(), data.getLong("code")))
                return Bundle.EMPTY;
            result = new Bundle(data);
            result.putBoolean("confirmed", true);
            result.putLong("time", System.currentTimeMillis());
            return Bundle.EMPTY;
        }
        if (caller != Process.myUid()) throw new SecurityException("Only the module may request status");
        if ("begin".equals(method)) {
            try {
                android.content.pm.PackageInfo info = getContext().getPackageManager()
                        .getPackageInfo(getContext().getPackageName(), 0);
                check.begin(token, SystemClock.elapsedRealtime(), info.versionCode);
                result = new Bundle();
            } catch (android.content.pm.PackageManager.NameNotFoundException impossible) {
                throw new IllegalStateException(impossible);
            }
        } else if (!"read".equals(method)) throw new IllegalArgumentException("Unknown status operation");
        return new Bundle(result);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
