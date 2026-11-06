package dev.local.newsoul;

import android.app.Activity;
import android.view.View;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.*;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Binds only the semantic same-city tab, regardless of its displayed city name. */
final class LocationCityEntry {
    static boolean ready;
    private static boolean legacy;
    private static final Map<View, WeakReference<Object>> bound = new WeakHashMap<>();
    static void install(XposedModule module, ClassLoader loader) {
        ready = false;
        try { legacy = HostBindings.legacyChat(loader); }
        catch (Throwable error) { DebugDiagnostics.registration("location.city.host", error); return; }
        for (String name : new String[]{"cn.soulapp.android.component.square.main.SquareFragment",
                "cn.soulapp.android.component.square.main.newui.SquareFragmentV2"}) {
            try {
                Class<?> fragment = loader.loadClass(name);
                fragment.getDeclaredField(legacy ? "j" : "n"); fragment.getDeclaredField(legacy ? "k" : "o");
                if (!legacy) loader.loadClass("cn.soulapp.android.component.square.bean.SquareMainTab").getMethod("isSameCityTab");
                for (String lifecycle : new String[]{legacy ? "U0" : "initTabLayout", "onResume"}) {
                    Method method = fragment.getDeclaredMethod(lifecycle);
                    module.hook(method).setId("new-soul/location-city-" + fragment.getSimpleName() + "-" + lifecycle)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                                Object result = chain.proceed();
                                try { bind(chain.getThisObject()); }
                                catch (Throwable error) { DebugDiagnostics.failure("location.city.bind_failed", error); }
                                return result;
                            });
                }
                // initTabLayout is private and can be inlined into initialization/update callers.
                for (Method method : fragment.getDeclaredMethods()) module.deoptimize(method);
                ready = true;
                DebugDiagnostics.registration("location.city." + fragment.getSimpleName(), null);
            } catch (Throwable error) {
                DebugDiagnostics.registration("location.city." + name.substring(name.lastIndexOf('.') + 1), error);
            }
        }
    }
    private static void bind(Object fragment) throws Exception {
        Object tabs = LocationOverridePicker.field(fragment, legacy ? "k" : "o");
        Object adapter = LocationOverridePicker.field(fragment, legacy ? "j" : "n");
        if (tabs == null || adapter == null) return;
        int count = Math.min((int) call(tabs, "getTabCount"), (int) call(adapter, "getCount"));
        Set<View> wanted = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < count; i++) {
            Object model = model(adapter, i);
            if (!sameCity(model)) continue;
            Object tab = tabs.getClass().getMethod("getTabAt", int.class).invoke(tabs, i);
            View custom = tab == null ? null : (View) call(tab, legacy ? "d" : "getCustomView");
            if (custom == null) continue;
            // Use Soul's existing click receiver. Making only its child label long-clickable
            // would consume short taps before they reach the native parent's click listener.
            wanted.add(custom);
            WeakReference<Object> owner = new WeakReference<>(fragment);
            final int position = i;
            custom.setOnLongClickListener(view -> {
                Object current = owner.get();
                if (current == null || !view.isAttachedToWindow()) return false;
                try {
                    Object nowAdapter = LocationOverridePicker.field(current, legacy ? "j" : "n");
                    Object nowModel = model(nowAdapter, position);
                    if (!sameCity(nowModel)
                            || !(boolean) call(current, "isResumed")) return false;
                    Object nowTabs = LocationOverridePicker.field(current, legacy ? "k" : "o");
                    Object nowTab = nowTabs.getClass().getMethod("getTabAt", int.class).invoke(nowTabs, position);
                    View nowCustom = nowTab == null ? null : (View) call(nowTab, legacy ? "d" : "getCustomView");
                    if (nowCustom != view) return false;
                    Activity activity = (Activity) call(current, "getActivity");
                    if (activity == null || activity.isFinishing() || activity.isDestroyed()) return false;
                    LocationOverrideUi.show(activity, BottomSettingsEntry.dark(activity));
                    DebugDiagnostics.event("location.city.open", "route=long_press");
                    return true;
                } catch (Throwable error) { DebugDiagnostics.failure("location.city.open_failed", error); return false; }
            });
            bound.put(custom, owner);
        }
        Iterator<Map.Entry<View, WeakReference<Object>>> iterator = bound.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<View, WeakReference<Object>> entry = iterator.next();
            Object owner = entry.getValue().get();
            if (owner == null || owner == fragment && !wanted.contains(entry.getKey())) {
                entry.getKey().setOnLongClickListener(null);
                entry.getKey().setLongClickable(false);
                iterator.remove();
            }
        }
        DebugDiagnostics.event("location.city.bound", "count=" + wanted.size());
    }
    private static Object model(Object adapter, int position) throws Exception {
        // Old n1.getItem reads the already-created child fragment, without instantiation.
        return adapter.getClass().getMethod(legacy ? "getItem" : "b", int.class).invoke(adapter, position);
    }
    private static boolean sameCity(Object model) throws Exception {
        if (model == null) return false;
        if (!legacy) return (boolean) call(model, "isSameCityTab");
        String name=model.getClass().getName();
        return name.equals("cn.soulapp.android.component.square.location.LocationSquareFragment")
                || name.equals("cn.soulapp.android.component.square.location.LocationSquareV2Fragment");
    }
    private static Object call(Object object, String name) throws Exception { return object.getClass().getMethod(name).invoke(object); }
}
