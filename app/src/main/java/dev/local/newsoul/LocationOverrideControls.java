package dev.local.newsoul;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.*;
import java.util.*;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Scoped to Soul's main process by SoulHook. No Android/system-wide GPS hooks or host-cache writes. */
final class LocationOverrideControls {
    static boolean ready;
    private static Method context;
    private Constructor<?> location;
    private Class<?> dataClass;
    private Method located;
    private boolean legacy;
    private final Map<Object, List<Runnable>> callbacks = new IdentityHashMap<>();
    private Handler main;

    static void install(XposedModule module, ClassLoader loader) {
        LocationOverrideControls controls = new LocationOverrideControls();
        try {
            context = HostSymbols.method(HostDexRules.CONTEXT, loader);
            controls.legacy = HostBindings.legacyChat(loader);
            controls.main = new Handler(Looper.getMainLooper());
            controls.dataClass = loader.loadClass("cn.soulapp.android.lib.location.model.LocationData");
            controls.location = controls.dataClass.getConstructor(String.class, String.class);
            Class<?> listener = loader.loadClass("cn.soulapp.android.lib.location.LocationListener");
            controls.located = listener.getMethod("onLocated", int.class, controls.dataClass);
            // Verify all output fields before enabling any override.
            controls.newLocation(new LocationOverridePoint(31.2304, 121.4737, "probe", "probe", "", "", "", ""));
            Class<?> option = controls.legacy ? null : loader.loadClass("cn.soulapp.android.lib.location.model.LocationOption");
            String[] services = controls.legacy
                    ? new String[]{"cn.soulapp.android.lib.location.bdmap.BDMapLocationServiceImpl"}
                    : new String[]{"cn.soulapp.android.lib.location.bdmap.BDMapLocationServiceImpl", "cn.soulapp.android.lib.lbs.bd.SoulBaiduLocationClient"};
            for (String name : services) {
                Class<?> owner = loader.loadClass(name);
                for (String getter : new String[]{"getLastLocation", "getInnerLastLocation", "getMLastLocation"}) {
                    Method method = owner.getDeclaredMethod(getter);
                    controls.hookGetter(module, method);
                }
                if (!controls.legacy) controls.hookRequest(module, owner.getDeclaredMethod("getLocationWithCache", listener), 0);
                boolean client = name.endsWith("SoulBaiduLocationClient");
                controls.hookRequest(module, client ? owner.getDeclaredMethod("startLocation", option, listener)
                        : owner.getDeclaredMethod("startLocation", listener), client ? 1 : 0);
                if (client) {
                    controls.hookRequest(module, owner.getDeclaredMethod("startLocation", option, listener, boolean.class), 1);
                    controls.hookRequest(module, owner.getDeclaredMethod("callbackCachedLocation", listener), 0);
                }
                Method dispatch = owner.getDeclaredMethod("callback2Listener", int.class, controls.dataClass);
                module.hook(dispatch).setId("new-soul/location-dispatch-" + owner.getSimpleName())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            LocationOverridePoint point = activePoint();
                            if (point == null) return chain.proceed();
                            Object replacement = controls.newLocation(point);
                            DebugDiagnostics.event("location.override.callback", "route=dispatch");
                            return chain.proceed(new Object[]{0, replacement});
                        });
                Method stop = owner.getDeclaredMethod("stopLocation", listener);
                module.hook(stop).setId("new-soul/location-stop-" + owner.getSimpleName())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            controls.cancel(chain.getArg(0));
                            return chain.proceed();
                        });
                // Internal helpers may be inlined in these methods, including old cached-location paths.
                for (Method method : owner.getDeclaredMethods()) module.deoptimize(method);
            }
            ready = true;
            DebugDiagnostics.registration("location.override", null);
        } catch (Throwable error) {
            ready = false;
            DebugDiagnostics.registration("location.override", error);
        }
        LocationOverridePicker.install(module, loader);
    }
    static SharedPreferences preferences() throws Exception {
        Context value = (Context) context.invoke(null);
        return value == null ? null : PluginPreferences.get(value);
    }
    static LocationOverridePoint saved(SharedPreferences prefs) {
        if (prefs == null) return null;
        try { return LocationOverridePoint.decode(prefs.getString(LocationOverridePoint.POINT, "")); }
        catch (Exception invalid) { return null; }
    }
    static LocationOverridePoint activePoint() {
        if (!ready) return null;
        try {
            SharedPreferences prefs = preferences();
            Map<String, ?> state = prefs == null ? Collections.emptyMap() : prefs.getAll();
            if (!Boolean.TRUE.equals(state.get(LocationOverridePoint.ENABLED))) return null;
            Object raw = state.get(LocationOverridePoint.POINT);
            return raw instanceof String ? LocationOverridePoint.decode((String) raw) : null;
        } catch (Exception unavailable) { return null; }
    }
    private Object newLocation(LocationOverridePoint point) throws Exception {
        Object data = location.newInstance(Double.toString(point.latitude), Double.toString(point.longitude));
        for (String[] value : new String[][]{{"City", point.city}, {"Province", point.province},
                {"Country", point.country}, {"AdCode", point.adCode}, {"CityCode", point.cityCode},
                {"DistrictName", ""}})
            dataClass.getMethod("set" + value[0], String.class).invoke(data, value[1]);
        dataClass.getMethod("setLocationWhere", int.class).invoke(data, point.locationWhere());
        if (!legacy) {
            dataClass.getMethod("setCountryCode", String.class).invoke(data, "");
            dataClass.getMethod("setCacheTime", long.class).invoke(data, System.currentTimeMillis());
            dataClass.getMethod("setHorizontalAccuracy", float.class).invoke(data, 30f);
        }
        return data;
    }
    private void hookGetter(XposedModule module, Method method) {
        module.hook(method).setId("new-soul/location-" + method.getDeclaringClass().getSimpleName() + "-" + method.getName())
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    LocationOverridePoint point = activePoint();
                    return point == null ? chain.proceed() : newLocation(point);
                });
    }
    private void hookRequest(XposedModule module, Method method, int listenerIndex) {
        method.setAccessible(true);
        module.hook(method).setId("new-soul/location-request-" + method.getDeclaringClass().getSimpleName()
                + "-" + method.getName() + "-" + method.getParameterCount())
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    LocationOverridePoint point = activePoint();
                    if (point == null) return chain.proceed();
                    Object listener = chain.getArg(listenerIndex);
                    if (listener == null) return null;
                    Object receiver = chain.getThisObject();
                    Object[] args = chain.getArgs().toArray();
                    // Construct now so a binding error leaves the original host request available.
                    newLocation(point);
                    Runnable task = new Runnable() {
                        @Override public void run() {
                            synchronized (callbacks) {
                                List<Runnable> queued = callbacks.get(listener);
                                if (queued == null || !queued.remove(this)) return;
                                if (queued.isEmpty()) callbacks.remove(listener);
                            }
                            try {
                                LocationOverridePoint current = activePoint();
                                if (current == null) {
                                    // No host request ran before queuing. Disabled meanwhile: resume its real path once.
                                    method.invoke(receiver, args);
                                } else {
                                    located.invoke(listener, 0, newLocation(current));
                                    DebugDiagnostics.event("location.override.callback", "route=request");
                                }
                            } catch (Throwable error) { DebugDiagnostics.failure("location.override.callback_failed", error); }
                        }
                    };
                    synchronized (callbacks) { callbacks.computeIfAbsent(listener, ignored -> new ArrayList<>()).add(task); }
                    if (!main.post(task)) {
                        cancel(listener);
                        return chain.proceed();
                    }
                    return null;
                });
    }
    private void cancel(Object listener) {
        synchronized (callbacks) {
            List<Runnable> queued = callbacks.remove(listener);
            if (queued != null) for (Runnable task : queued) main.removeCallbacks(task);
        }
    }
}
