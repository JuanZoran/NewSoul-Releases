package dev.local.newsoul;

import android.app.Activity;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Soul's own PostHelper handles the request and updates Post only on success. */
final class LoadedPostLikes {
    static boolean ready, cityReady, profileReady;
    private static Class<?> profileAdapterClass;
    private static Class<?> profileLightAdapterClass;
    private static Method profileGetData;
    private static Method profileLightGetData;
    private static Class<?> recyclerClass, adapterClass, consumerClass;
    private static Class<?> cityAdapterClass;
    private static Method cityGetData;
    private static Constructor<?> helperConstructor;
    private static Method getAdapter, getData, like, subscribe, getChildHolder, holderPost, updateLikeState;
    private static Class<?> postHolderClass;
    private static LegacyFooter legacyFooter;
    // Prefetched rows can already be bound while absent from RecyclerView's children.
    private static final java.util.Map<Object, String> completed = new java.util.WeakHashMap<>();
    private static final java.util.Map<View, java.lang.ref.WeakReference<Object>> rendered = new java.util.WeakHashMap<>();

    static void install(ClassLoader loader, Class<?> postClass) {
        try {
            ready = cityReady = profileReady = false;
            legacyFooter = null;
            profileLightAdapterClass = null;
            profileLightGetData = null;
            boolean legacy = HostBindings.legacyChat(loader);
            recyclerClass = loader.loadClass("androidx.recyclerview.widget.RecyclerView");
            adapterClass = loader.loadClass("cn.soulapp.android.component.square.main.squarepost.SquareAdapter");
            consumerClass = loader.loadClass("io.reactivex.functions.Consumer");
            Class<?> helper = loader.loadClass("cn.soulapp.android.component.square.post."
                    + (legacy ? "q0" : "b1"));
            helperConstructor = helper.getConstructor(postClass);
            like = helper.getMethod("h", int.class);
            getAdapter = recyclerClass.getMethod("getAdapter");
            getData = adapterClass.getMethod("getData");
            try {
                getChildHolder = recyclerClass.getMethod("getChildViewHolder", View.class);
                if (legacy) {
                    legacyFooter = new LegacyFooter(loader, postClass);
                    updateLikeState = null;
                } else {
                    postHolderClass = loader.loadClass("cn.soulapp.android.square.post.viewholder.PostAttachmentViewHolder");
                    holderPost = postHolderClass.getMethod("getPost");
                    updateLikeState = postHolderClass.getMethod("updateLikeState", postClass);
                }
                DebugDiagnostics.registration("likes.footer.refresh", null);
            } catch (ReflectiveOperationException error) {
                updateLikeState = null;
                DebugDiagnostics.registration("likes.footer.refresh", error);
            }
            subscribe = like.getReturnType().getMethod("subscribe", consumerClass, consumerClass);
            ready = true;
            try {
                profileAdapterClass = loader.loadClass(legacy ? "cn.soulapp.android.component.home.user.adapter.l0"
                        : "cn.soulapp.android.component.square.post.SquareUserAdapter");
                profileGetData = profileAdapterClass.getMethod(legacy ? "getAllData" : "getData");
                if (legacy) {
                    profileLightAdapterClass = loader.loadClass("com.lufficc.lightadapter.LightAdapter");
                    profileLightGetData = profileLightAdapterClass.getMethod("j");
                }
                profileReady = true;
                DebugDiagnostics.registration("likes.profile.source", null);
            } catch (ReflectiveOperationException error) { DebugDiagnostics.registration("likes.profile.source", error); }
            try {
                cityAdapterClass = loader.loadClass(legacy ? "com.lufficc.lightadapter.LightAdapter"
                        : "cn.soulapp.android.component.square.location.PostLightAdapter");
                cityGetData = cityAdapterClass.getMethod(legacy ? "j" : "getDatas");
                cityReady = true;
                DebugDiagnostics.registration("likes.city.source", null);
            } catch (ReflectiveOperationException error) {
                DebugDiagnostics.registration("likes.city.source", error);
            }
        } catch (Throwable error) {
            android.util.Log.e("SoulHook102", "loaded post likes unavailable", error);
        }
    }

    static final class Source {
        final View view;
        final Object adapter;
        Source(View view, Object adapter) { this.view = view; this.adapter = adapter; }
        List<?> data() throws ReflectiveOperationException {
            Method reader = profileAdapterClass != null && profileAdapterClass.isInstance(adapter) ? profileGetData
                    : profileLightAdapterClass != null && profileLightAdapterClass.isInstance(adapter) ? profileLightGetData
                    : cityAdapterClass != null && cityAdapterClass.isInstance(adapter) ? cityGetData : getData;
            return new ArrayList<>((List<?>) reader.invoke(adapter));
        }
        boolean contains(Object post) throws ReflectiveOperationException {
            for (Object item : data()) if (item == post) return true;
            return false;
        }
        void refresh(Object post) {
            // A full adapter notification rebinds images and restores/re-hides filtered rows.
            // Update only a still-bound footer; offscreen rows read the updated model on bind.
            if (!canRefresh() || !(view instanceof ViewGroup) || !view.isAttachedToWindow()) return;
            try {
                if (getAdapter.invoke(view) != adapter || !contains(post)) return;
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View child = group.getChildAt(i);
                    if (!child.isShown()) continue;
                    if (refreshChild(view, child, post)) {
                        rendered.put(child, new java.lang.ref.WeakReference<>(post));
                    }
                }
            } catch (ReflectiveOperationException error) {
                // Never fall back to whole-list/whole-row rebinds merely for a heart update.
                android.util.Log.w("SoulHook102", "loaded like footer refresh failed", error);
            }
        }

        void refreshCompleted() {
            if (completed.isEmpty() || !canRefresh() || !(view instanceof ViewGroup)
                    || !view.isAttachedToWindow()) return;
            try {
                if (getAdapter.invoke(view) != adapter) return;
                java.util.Set<Object> current = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                current.addAll(data());
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View child = group.getChildAt(i);
                    if (!child.isShown()) continue;
                    Object holder = getChildHolder.invoke(view, child);
                    Object post = legacyFooter != null ? legacyFooter.post(child, holder)
                            : postHolderClass.isInstance(holder) ? holderPost.invoke(holder) : null;
                    java.lang.ref.WeakReference<Object> previous = rendered.get(child);
                    if (!current.contains(post) || !SquareLikeControls.currentAccount().equals(completed.get(post))) {
                        rendered.remove(child);
                        continue;
                    }
                    if (previous != null && previous.get() == post) continue;
                    if (refreshChild(view, child, post)) rendered.put(child, new java.lang.ref.WeakReference<>(post));
                }
            } catch (ReflectiveOperationException error) {
                android.util.Log.w("SoulHook102", "loaded like cached footer refresh failed", error);
            }
        }
    }

    static Source find(View root) { return find(root, false); }

    static Source find(View root, boolean city) {
        return find(root, city, false);
    }

    static Source find(View root, boolean city, boolean profile) {
        if (profile && !profileReady) return null;
        if (city && !cityReady) return null;
        if (!ready || root == null || !root.isShown()) return null;
        List<Source> sources = new ArrayList<>();
        collect(root, sources, city, profile);
        Source best = null;
        long bestArea = 0;
        for (Source source : sources) {
            Rect rect = new Rect();
            if (!source.view.getGlobalVisibleRect(rect)) continue;
            long area = (long) rect.width() * rect.height();
            if (area > bestArea) { bestArea = area; best = source; }
        }
        return best;
    }

    private static void collect(View view, List<Source> result, boolean city, boolean profile) {
        Rect bounds = new Rect();
        if (!view.isShown() || !view.getGlobalVisibleRect(bounds)) return;
        if (recyclerClass.isInstance(view)) {
            try {
                Object adapter = getAdapter.invoke(view);
                if ((profile ? profileAdapterClass.isInstance(adapter)
                        || profileLightAdapterClass != null && profileLightAdapterClass.isInstance(adapter)
                        : (city ? cityAdapterClass : adapterClass).isInstance(adapter))) {
                    result.add(new Source(view, adapter));
                    return;
                }
            } catch (ReflectiveOperationException ignored) {}
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), result, city, profile);
        }
    }

    private static boolean canRefresh() { return updateLikeState != null || legacyFooter != null; }

    private static boolean refreshChild(View recycler, View child, Object post) throws ReflectiveOperationException {
        Object holder = getChildHolder.invoke(recycler, child);
        if (legacyFooter != null) return legacyFooter.refresh(child, holder, post);
        if (!postHolderClass.isInstance(holder) || holderPost.invoke(holder) != post) return false;
        updateLikeState.invoke(holder, post);
        return true;
    }

    /** 4.88 uses PostVH in square/city and a text heart in both old profile layouts. */
    private static final class LegacyFooter {
        final Class<?> holderType, modelType;
        final Field post, config, liked;
        final Method footer, footerPost, bindFooter, position, count;
        LegacyFooter(ClassLoader loader, Class<?> postClass) throws ReflectiveOperationException {
            modelType = postClass;
            holderType = loader.loadClass("cn.soulapp.android.component.square.main.PostVH");
            post = holderType.getDeclaredField("post"); post.setAccessible(true);
            config = holderType.getField("config");
            footer = config.getType().getMethod("k");
            footerPost = footer.getReturnType().getMethod("getPost");
            bindFooter = footer.getReturnType().getMethod("onBindViewHolder", int.class, postClass);
            position = holderType.getMethod("getLayoutPositionProxy");
            liked = postClass.getField("liked");
            count = postClass.getMethod("g");
        }
        Object post(View row, Object holder) throws ReflectiveOperationException {
            if (holderType.isInstance(holder)) return post.get(holder);
            int key = id(row, "key_item_post", "id");
            if (key == 0) return null;
            Object value = row.getTag(key);
            if (modelType.isInstance(value)) return value;
            if (row instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
                Object child = post(group.getChildAt(i), null);
                if (child != null) return child;
            }
            return null;
        }
        boolean refresh(View row, Object holder, Object expected) throws ReflectiveOperationException {
            if (expected == null || post(row, holder) != expected) return false;
            if (holderType.isInstance(holder)) {
                Object renderer = footer.invoke(config.get(holder));
                if (renderer == null || footerPost.invoke(renderer) != expected) return false;
                // This binds only the footer; no adapter notification or image rebind.
                bindFooter.invoke(renderer, ((Number) position.invoke(holder)).intValue(), expected);
                return true;
            }
            int like = id(row, "tvLike", "id");
            View value = like == 0 ? null : row.findViewById(like);
            if (!(value instanceof android.widget.TextView text)) return false;
            int icon = id(row, liked.getBoolean(expected) ? "icon_user_home_like_selected" : "icon_user_home_like", "drawable");
            if (icon == 0) return false;
            Object label = count.invoke(expected);
            text.setText(label instanceof String && !((String) label).isEmpty() ? (String) label : "点赞");
            text.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
            return true;
        }
        private static int id(View row, String name, String type) {
            return row.getResources().getIdentifier(name, type, row.getContext().getPackageName());
        }
    }

    interface Completion { void complete(boolean success); }

    static void send(Activity activity, Source source, Object post, Completion completion)
            throws ReflectiveOperationException {
        send(activity, source, post, SquareLikeControls.currentAccount(), completion);
    }

    static void send(Activity activity, Source source, Object post, String account, Completion completion)
            throws ReflectiveOperationException {
        requireAccount(account);
        Object helper = helperConstructor.newInstance(post);
        // Caller has rechecked hasDeclare on the main thread immediately before this.
        // Type 0 is the ordinary heart, matching an unliked Post's original click.
        requireAccount(account);
        Object single = like.invoke(helper, 0);
        java.util.concurrent.atomic.AtomicBoolean terminal = new java.util.concurrent.atomic.AtomicBoolean();
        Object success = consumer(activity, () -> {
            if (!terminal.compareAndSet(false, true)) return;
            completed.put(post, account);
            // A second successful change to the same model must refresh cached rows again.
            rendered.entrySet().removeIf(entry -> entry.getValue().get() == post);
            if (account.equals(SquareLikeControls.currentAccount())) source.refresh(post);
            completion.complete(true);
        });
        Object failure = consumer(activity, () -> {
            if (!terminal.compareAndSet(false, true)) return;
            if (account.equals(SquareLikeControls.currentAccount())) source.refresh(post);
            completion.complete(false);
        });
        requireAccount(account);
        subscribe.invoke(single, success, failure);
    }

    private static void requireAccount(String account) throws ReflectiveOperationException {
        if (account == null || account.isEmpty() || !account.equals(SquareLikeControls.currentAccount()))
            throw new ReflectiveOperationException("like account changed or unavailable");
    }

    private static Object consumer(Activity activity, Runnable action) {
        return Proxy.newProxyInstance(consumerClass.getClassLoader(), new Class<?>[]{consumerClass},
                (proxy, method, args) -> {
                    if (method.getName().equals("accept")) {
                        activity.runOnUiThread(action);
                        return null;
                    }
                    if (method.getName().equals("toString")) return "NewSoulLoadedLikeCallback";
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("equals")) return proxy == args[0];
                    return null;
                });
    }
}
