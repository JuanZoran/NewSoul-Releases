package dev.local.newsoul;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.ref.WeakReference;
import java.util.function.BooleanSupplier;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** The existing manual-like controller, scoped to the host's moments fragment. */
final class ProfileMomentLikes {
    static boolean ready;
    private static final WeakHashMap<Object, Binding> bindings = new WeakHashMap<>();

    static void install(XposedModule module, ClassLoader loader) {
        ready = false;
        try {
            Class<?> page;
            try { page = loader.loadClass("cn.soulapp.android.square.api.user.UserHomePostFragment"); }
            catch (ClassNotFoundException absentModernPage) {
                installLegacy(module, loader);
                return;
            }
            Field viewModel = page.getDeclaredField("k");
            Class<?> model = loader.loadClass("cn.soulapp.android.square.api.user.viewmodel.UserHomePostViewModel");
            if (viewModel.getType() != model) throw new NoSuchFieldException("profile view model");
            viewModel.setAccessible(true);
            Method userId = model.getMethod("b");
            Method currentUser = loader.loadClass("cn.soulapp.android.client.component.middle.platform.utils.user.a")
                    .getMethod("x");
            installPage(module, page, fragment -> otherProfile(fragment, viewModel, userId, currentUser), "moments");
            ready = SquareLikeControls.ready && LoadedPostLikes.profileReady;
            DebugDiagnostics.registration("likes.profile.entry", null);
        } catch (Throwable error) { DebugDiagnostics.registration("likes.profile.entry", error); }
    }

    private static void installLegacy(XposedModule module, ClassLoader loader) throws Exception {
        Method currentUser = loader.loadClass("l9.a").getMethod("t");
        Class<?> user = loader.loadClass("com.soul.component.componentlib.service.user.bean.User");
        Field encryptedId = user.getField("userIdEcpt");
        Class<?> original = loader.loadClass("cn.soulapp.android.component.home.user.fragment.PageUserHomeFragment");
        Field originalUser = original.getDeclaredField("z0"); originalUser.setAccessible(true);
        if (originalUser.getType() != user) throw new NoSuchFieldException("old profile user");
        installPage(module, original, fragment -> otherLegacyProfile(fragment, originalUser, encryptedId, currentUser), "original");
        Class<?> meta = loader.loadClass("cn.soulapp.android.component.home.meta.user.MetaPageUserHomeFragment");
        Field metaUser = meta.getDeclaredField("y"); metaUser.setAccessible(true);
        if (metaUser.getType() != user) throw new NoSuchFieldException("meta profile user");
        installPage(module, meta, fragment -> otherLegacyProfile(fragment, metaUser, encryptedId, currentUser), "meta");
        ready = SquareLikeControls.ready && LoadedPostLikes.profileReady;
        DebugDiagnostics.registration("likes.profile.entry", null);
    }

    private static void installPage(XposedModule module, Class<?> page,
            java.util.function.Predicate<Object> access, String scope) throws Exception {
        Method getView = page.getMethod("getView"), getActivity = page.getMethod("getActivity");
        for (String name : new String[]{"onResume", "onPause", "onDestroyView"}) {
            Method method = name.equals("onDestroyView") ? page.getMethod(name) : page.getDeclaredMethod(name);
            module.hook(method).setId("new-soul/profile-" + scope + "-" + name)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        if (name.equals("onDestroyView")) {
                            try { return chain.proceed(); }
                            finally {
                                if (page.isInstance(chain.getThisObject())) destroyBinding(chain.getThisObject());
                            }
                        }
                        Object result = chain.proceed();
                        if (!ready) return result;
                        Object fragment = chain.getThisObject();
                        Binding binding = bindings.get(fragment);
                        if (name.equals("onPause")) {
                            if (binding != null) binding.pause();
                            return result;
                        }
                        View root = (View) getView.invoke(fragment);
                        Activity activity = (Activity) getActivity.invoke(fragment);
                        if (root == null || activity == null) return result;
                        if (binding != null && binding.root != root) { binding.remove(); binding = null; }
                        if (binding == null || binding.removed) {
                            View content = activity.findViewById(android.R.id.content);
                            if (!(content instanceof FrameLayout)) return result;
                            WeakReference<Object> owner = new WeakReference<>(fragment);
                            binding = new Binding(activity, root, (FrameLayout) content,
                                    () -> access.test(owner.get()));
                            bindings.put(fragment, binding);
                        }
                        binding.resume();
                        return result;
                    });
            module.deoptimize(method);
        }
    }

    static boolean otherLegacyProfile(Object fragment, Field user, Field encryptedId, Method currentUser) {
        try {
            if (fragment == null) return false;
            Object target = user.get(fragment);
            if (target == null) return false;
            String targetId = (String) encryptedId.get(target);
            String account = (String) currentUser.invoke(null);
            return targetId != null && !targetId.trim().isEmpty() && account != null
                    && !account.trim().isEmpty() && !targetId.equals(account);
        } catch (ReflectiveOperationException | RuntimeException error) { return false; }
    }

    static void destroyBinding(Object fragment) {
        Binding binding = bindings.remove(fragment);
        if (binding != null) binding.remove();
    }

    static boolean otherProfile(Object fragment, Field viewModel, Method userId, Method currentUser) {
        try {
            if (fragment == null) return false;
            Object model = viewModel.get(fragment);
            if (model == null) return false;
            String target = (String) userId.invoke(model);
            String account = (String) currentUser.invoke(null);
            // Same encrypted IDs used by UserHomeBaseViewModel.c(); unknown is hidden.
            return target != null && !target.trim().isEmpty() && account != null
                    && !account.trim().isEmpty() && !target.equals(account);
        } catch (ReflectiveOperationException | RuntimeException error) { return false; }
    }

    private static final class Binding {
        final View root;
        final FrameLayout parent;
        final SquareLikeControls.Controller controller;
        ViewTreeObserver observer;
        final ViewTreeObserver.OnPreDrawListener position;
        final Handler main = new Handler(Looper.getMainLooper());
        final Runnable mount;
        final View.OnAttachStateChangeListener attachment;
        boolean resumed;
        boolean removed;
        Binding(Activity activity, View root, FrameLayout parent) {
            this(activity, root, parent, () -> true);
        }
        Binding(Activity activity, View root, FrameLayout parent, BooleanSupplier profileAccess) {
            this.root = root; this.parent = parent;
            controller = new SquareLikeControls.Controller(activity, root, true);
            controller.profileAccess = profileAccess;
            int size = Math.round(68 * activity.getResources().getDisplayMetrics().density);
            int bottomGap = Math.round(24 * activity.getResources().getDisplayMetrics().density);
            int bottomActionId = controller.id("bottom_action_user_home");
            // Fragment lifecycle can run inside the host's layout traversal. Never
            // change its content children synchronously from lifecycle callbacks.
            position = () -> {
                Rect bounds = new Rect(), container = new Rect();
                if (root.getGlobalVisibleRect(bounds) && parent.getGlobalVisibleRect(container)) {
                    // Anchor to the stationary activity/action bar, not the
                    // moments root, which moves with the host's bottom sheet.
                    int bottom = container.bottom;
                    View actions = bottomActionId == 0 ? null : activity.findViewById(bottomActionId);
                    Rect actionBounds = new Rect();
                    if (actions != null && actions.isShown() && actions.getGlobalVisibleRect(actionBounds))
                        bottom = Math.min(bottom, actionBounds.top);
                    controller.button.setX(container.width() - size);
                    controller.button.setY(bottom - size - bottomGap - container.top);
                    if (bounds.width() < size || bounds.height() < size) controller.button.setVisibility(View.GONE);
                } else controller.button.setVisibility(View.GONE);
                return true;
            };
            mount = () -> {
                if (removed || !resumed || !root.isAttachedToWindow()
                        || activity.isFinishing() || activity.isDestroyed()) return;
                if (controller.button.getParent() == null)
                    parent.addView(controller.button, new FrameLayout.LayoutParams(size, size));
                if (observer == null || !observer.isAlive()) {
                    observer = root.getViewTreeObserver();
                    observer.addOnPreDrawListener(position);
                }
                controller.resume();
            };
            attachment = new View.OnAttachStateChangeListener() {
                public void onViewAttachedToWindow(View view) {
                    if (resumed && !removed) resume();
                }
                public void onViewDetachedFromWindow(View view) {
                    // The host can reparent this root without pausing the Fragment.
                    // Suspend the overlay, retaining the resumed intent for reattach.
                    unmount();
                }
            };
            root.addOnAttachStateChangeListener(attachment);
        }
        void resume() {
            if (removed) return;
            resumed = true;
            main.removeCallbacks(mount);
            main.post(mount);
        }
        void pause() {
            resumed = false;
            main.removeCallbacks(mount);
            controller.pause();
        }
        void remove() {
            if (removed) return;
            removed = true;
            pause();
            root.removeOnAttachStateChangeListener(attachment);
            unmount();
        }
        void unmount() {
            main.removeCallbacks(mount);
            controller.pause();
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(position);
            observer = null;
            // Detach may be dispatched while FrameLayout still uses its original
            // child count. Immediate removal shifts that array and can crash it.
            main.post(() -> {
                if (controller.button.getParent() instanceof ViewGroup group) group.removeView(controller.button);
            });
        }
    }

}
