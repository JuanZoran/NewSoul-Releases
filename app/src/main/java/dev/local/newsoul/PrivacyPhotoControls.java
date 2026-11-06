package dev.local.newsoul;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.app.DownloadManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Receiver-side controls for privacy media and legacy flash previews. */
final class PrivacyPhotoControls {
    static final String ALLOW_DOWNLOAD = "privacy_allow_download";
    static final String ALLOW_SCREENSHOT = "privacy_allow_screenshot";
    static final String ALLOW_REPEAT = "privacy_allow_repeat";
    static boolean previewReady;
    static boolean privacyRepeatReady;
    static boolean snapRepeatReady;
    static boolean previewSupported = true, snapSupported = true;

    private static final String PREVIEW =
            "cn.soulapp.android.component.chat.snapchat.PrivacyProtectPreviewActivity";
    private static final String SNAP_PREVIEW =
            "cn.soulapp.android.component.chat.snapchat.SnapChatActivity";
    private static final String DOWNLOAD_TAG = "new.soul.privacy.photo.download";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static XposedModule module;
    private static ClassLoader soulLoader;

    static void install(XposedModule m, ClassLoader loader) {
        module = m;
        soulLoader = loader;
        previewReady = privacyRepeatReady = snapRepeatReady = false;
        previewSupported = exists(loader, PREVIEW);
        snapSupported = exists(loader, SNAP_PREVIEW);
        if (!previewSupported && !snapSupported) return;
        if (previewSupported) {
        try {
            Class<?> preview = loader.loadClass(PREVIEW);
            Method init = preview.getDeclaredMethod("init", Bundle.class);
            m.hook(init).setId("new-soul/privacy-photo-preview")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Activity activity = (Activity) chain.getThisObject();
                        SharedPreferences prefs = PluginPreferences.get(activity);
                        if (repeatEnabled(activity)) {
                            // Zero is not a valid limited-view value in Soul. It keeps the
                            // preview in the ordinary, non-consuming display branch.
                            activity.getIntent().putExtra("limitViewTimes", 0);
                        }
                        Object result = chain.proceed();
                        if (prefs.getBoolean(ALLOW_SCREENSHOT, false)) {
                            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                        }
                        int mediaType = activity.getIntent().getIntExtra("type", -1);
                        if (prefs.getBoolean(ALLOW_DOWNLOAD, false) && isSupportedMedia(activity)) {
                            scheduleDownloadButton(activity, isPhoto(activity));
                        }
                        info("privacy photo preview: download="
                                + prefs.getBoolean(ALLOW_DOWNLOAD, false)
                                + " screenshot=" + prefs.getBoolean(ALLOW_SCREENSHOT, false)
                                + " repeat=" + prefs.getBoolean(ALLOW_REPEAT, false)
                                + " type=" + mediaType);
                        return result;
                    });
            previewReady = true;
            info("privacy photo controls installed");
        } catch (Throwable error) {
            module.log(Log.ERROR, "SoulHook102", "privacy photo controls unavailable", error);
        }
        }
        // These are separate routes. SnapChatActivity never reads limitViewTimes.
        privacyRepeatReady = previewSupported && installRepeatGuard(loader, PREVIEW, "G", "privacy");
        snapRepeatReady = snapSupported && installRepeatGuard(loader, SNAP_PREVIEW, "C", "snap");
    }

    private static boolean exists(ClassLoader loader, String name) {
        try { loader.loadClass(name); return true; }
        catch (ClassNotFoundException absent) { return false; }
    }

    private static boolean repeatEnabled(Activity activity) {
        return !activity.getIntent().getBooleanExtra("isSender", false)
                && PluginPreferences.get(activity).getBoolean(ALLOW_REPEAT, false);
    }

    private static boolean installRepeatGuard(ClassLoader loader, String className,
            String methodName, String route) {
        try {
            Class<?> preview = loader.loadClass(className);
            Method report = preview.getDeclaredMethod(methodName);
            if (report.getReturnType() != void.class
                    || java.lang.reflect.Modifier.isStatic(report.getModifiers())) {
                throw new NoSuchMethodException("Unexpected repeat guard signature: " + report);
            }
            module.hook(report).setId("new-soul/privacy-repeat-" + route)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Activity activity = (Activity) chain.getThisObject();
                        if (!repeatEnabled(activity)) return chain.proceed();
                        // Privacy G schedules the local counter write and read report;
                        // legacy snap C directly reports read on timeout/background.
                        // Keep finish(), onStop() and bitmap/player cleanup intact.
                        info("privacy repeat blocked consume: route=" + route);
                        return null;
                    });
            // The gates are private and may be inlined into lifecycle methods,
            // synthetic accessors or animation callbacks in an already compiled app.
            deoptimizePreview(preview);
            for (Class<?> callback : preview.getDeclaredClasses()) {
                deoptimizePreview(callback);
            }
            if ("snap".equals(route)) {
                module.hook(preview.getDeclaredMethod("init", Bundle.class))
                        .setId("new-soul/privacy-repeat-snap-preview")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Activity activity = (Activity) chain.getThisObject();
                            info("privacy snap preview: repeat=" + repeatEnabled(activity)
                                    + " type=" + activity.getIntent().getIntExtra("type", -1));
                            return chain.proceed();
                        });
            }
            info("privacy repeat guard installed: route=" + route);
            return true;
        } catch (Throwable error) {
            module.log(Log.ERROR, "SoulHook102", "privacy repeat guard unavailable: " + route, error);
            return false;
        }
    }

    private static void deoptimizePreview(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if (!java.lang.reflect.Modifier.isAbstract(modifiers)
                    && !java.lang.reflect.Modifier.isNative(modifiers)) {
                module.deoptimize(method);
            }
        }
    }

    private static boolean isPhoto(Activity activity) {
        int type = activity.getIntent().getIntExtra("type", -1);
        // Soul 6.35.0 routes image variants 1 and 3 through the image preview.
        return type == 1 || type == 3;
    }

    private static boolean isSupportedMedia(Activity activity) {
        int type = activity.getIntent().getIntExtra("type", -1);
        return type >= 1 && type <= 4;
    }

    private static void scheduleDownloadButton(Activity activity, boolean photo) {
        View decor = activity.getWindow().getDecorView();
        // Soul's video player finishes attaching its playback surface after init().
        // Re-raise the action once that asynchronous layout work has completed.
        for (long delay : new long[]{0L, 250L, 750L, 1500L}) {
            decor.postDelayed(() -> addDownloadButton(activity, photo), delay);
        }
    }

    private static void addDownloadButton(Activity activity, boolean photo) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;
        View existing = decor.findViewWithTag(DOWNLOAD_TAG);
        if (existing != null) {
            existing.bringToFront();
            existing.setElevation(dp(activity, 32));
            existing.setVisibility(View.VISIBLE);
            return;
        }
        TextView button = new TextView(activity);
        button.setTag(DOWNLOAD_TAG);
        button.setContentDescription(photo ? "保存原图" : "保存视频");
        button.setText(photo ? "保存原图" : "保存视频");
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        UiAppearance.of(activity, true).apply(button, UiAppearance.Role.MEDIA_ACTION, 22);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 44),
                Gravity.END | Gravity.BOTTOM);
        params.rightMargin = dp(activity, 20);
        params.bottomMargin = dp(activity, 76);
        activity.addContentView(button, params);
        PluginActionMark.set(button, true);
        button.setElevation(dp(activity, 32));
        button.bringToFront();
        button.setOnClickListener(v -> {
            if (photo) downloadPhoto(activity, button);
            else downloadVideo(activity, button);
        });
        info("privacy save action attached: " + (photo ? "photo" : "video"));
    }

    private static void downloadPhoto(Activity activity, TextView button) {
        String url = activity.getIntent().getStringExtra("url");
        if (url == null || url.isEmpty()) {
            Toast.makeText(activity, "原图地址不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        button.setEnabled(false);
        button.setText("保存中…");
        PhotoResourceLease lease = null;
        View.OnAttachStateChangeListener attachment = null;
        try {
            // Application scope prevents Glide's Activity teardown from returning a
            // bitmap to its pool while our worker is still compressing that bitmap.
            Context application = activity.getApplicationContext();
            Class<?> glide = soulLoader.loadClass("com.bumptech.glide.Glide");
            Object manager = glide.getMethod("with", Context.class).invoke(null, application);
            Class<?> targetType = soulLoader.loadClass("com.bumptech.glide.request.target.Target");
            Method clear = manager.getClass().getMethod("clear", targetType);
            Object request = manager.getClass().getMethod("asBitmap").invoke(manager);
            request = findMethod(request.getClass(), "load", Object.class).invoke(request, url);
            Object target = request.getClass().getMethod("submit").invoke(request);
            if (!(target instanceof Future<?>)) {
                clearPhotoTarget(manager, clear, target);
                throw new IllegalStateException("Glide target is not Future");
            }
            lease = new PhotoResourceLease((Future<?>) target,
                    () -> MAIN.post(() -> clearPhotoTarget(manager, clear, target)));
            PhotoResourceLease owned = lease;
            WeakReference<Activity> owner = new WeakReference<>(activity);
            WeakReference<TextView> action = new WeakReference<>(button);
            attachment = new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) { }
                @Override public void onViewDetachedFromWindow(View view) {
                    view.removeOnAttachStateChangeListener(this);
                    owned.cancel();
                }
            };
            View.OnAttachStateChangeListener listener = attachment;
            button.addOnAttachStateChangeListener(listener);
            // Capture the application context and weak UI references, never the Activity.
            new Thread(() -> {
                String result = "保存失败，请稍后重试";
                try {
                    Object resource = owned.await(30, TimeUnit.SECONDS);
                    if (!(resource instanceof Bitmap)) throw new IllegalStateException("Resource is not bitmap");
                    if (photoUiAlive(owner, action)) {
                        Uri saved = saveBitmap(application, (Bitmap) resource);
                        result = saved == null ? "保存失败" : "已保存到 Pictures/New Soul";
                        info("privacy photo saved: " + saved);
                    }
                } catch (CancellationException ignored) {
                    // The owning preview left; there is no remaining UI to update.
                } catch (Throwable error) {
                    module.log(Log.ERROR, "SoulHook102", "privacy photo download failed", error);
                } finally {
                    // Glide owns this bitmap. Clear only after compression; never recycle it.
                    owned.close();
                    String message = result;
                    MAIN.post(() -> {
                        TextView view = action.get();
                        if (view != null) view.removeOnAttachStateChangeListener(listener);
                        if (!photoUiAlive(owner, action)) return;
                        Activity live = owner.get();
                        if (live == null) return;
                        view.setEnabled(true);
                        view.setText("保存原图");
                        Toast.makeText(live, message, Toast.LENGTH_SHORT).show();
                    });
                }
            }, "NewSoul-privacy-photo-save").start();
        } catch (Throwable error) {
            if (attachment != null) button.removeOnAttachStateChangeListener(attachment);
            if (lease != null) lease.close();
            module.log(Log.ERROR, "SoulHook102", "privacy photo download setup failed", error);
            if (!activity.isFinishing() && !activity.isDestroyed() && button.isAttachedToWindow()) {
                button.setEnabled(true);
                button.setText("保存原图");
                Toast.makeText(activity, "保存功能当前不可用", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static boolean photoUiAlive(WeakReference<Activity> owner, WeakReference<TextView> action) {
        Activity activity = owner.get();
        TextView button = action.get();
        return activity != null && !activity.isFinishing() && !activity.isDestroyed()
                && button != null && button.isAttachedToWindow();
    }

    private static void clearPhotoTarget(Object manager, Method clear, Object target) {
        try { clear.invoke(manager, target); }
        catch (Throwable error) {
            module.log(Log.ERROR, "SoulHook102", "privacy photo target cleanup failed", error);
        }
    }

    private static void downloadVideo(Activity activity, TextView button) {
        String url = activity.getIntent().getStringExtra("url");
        if (url == null || url.isEmpty()) {
            Toast.makeText(activity, "视频地址不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String fileName = "NewSoul_" + System.currentTimeMillis() + videoExtension(url);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                    .setTitle("New Soul 隐私视频")
                    .setDescription("正在保存视频")
                    .setMimeType("video/mp4")
                    .setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES,
                            "New Soul/" + fileName);
            DownloadManager manager = (DownloadManager)
                    activity.getSystemService(android.content.Context.DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException("DownloadManager unavailable");
            long id = manager.enqueue(request);
            Toast.makeText(activity, "已开始保存到 Movies/New Soul", Toast.LENGTH_SHORT).show();
            info("privacy video download enqueued: id=" + id);
        } catch (Throwable error) {
            module.log(Log.ERROR, "SoulHook102", "privacy video download failed", error);
            Toast.makeText(activity, "视频保存失败，请稍后重试", Toast.LENGTH_SHORT).show();
        }
    }

    private static String videoExtension(String url) {
        String clean = url;
        int query = clean.indexOf('?');
        if (query >= 0) clean = clean.substring(0, query);
        int slash = clean.lastIndexOf('/');
        int dot = clean.lastIndexOf('.');
        if (dot > slash && clean.length() - dot <= 6) {
            String extension = clean.substring(dot).toLowerCase(java.util.Locale.ROOT);
            if (extension.matches("\\.(mp4|mov|m4v|webm)")) return extension;
        }
        return ".mp4";
    }

    private static Uri saveBitmap(Context context, Bitmap bitmap) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME,
                "NewSoul_" + System.currentTimeMillis() + ".png");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/New Soul");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return null;
        try {
            // Closing the stream and publishing the row are both part of the transaction.
            try (OutputStream output = resolver.openOutputStream(uri, "w")) {
                if (output == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw new IllegalStateException("Bitmap compression failed");
                }
            }
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            if (resolver.update(uri, values, null, null) != 1) {
                throw new IllegalStateException("Saved photo could not be published");
            }
            return uri;
        } catch (Exception | Error error) {
            try { resolver.delete(uri, null, null); }
            catch (Exception | Error rollbackError) { error.addSuppressed(rollbackError); }
            throw error;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?> parameter)
            throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameter);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static void info(String text) {
        Log.i("SoulHook102", text);
        module.log(Log.INFO, "SoulHook102", text);
    }
}
