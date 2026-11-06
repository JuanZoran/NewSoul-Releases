package dev.local.newsoul;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;
import java.lang.reflect.*;
import java.util.UUID;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Reuses Soul's POI search, but owns only a token-marked picker and never emits MetaSelectCityEvent. */
final class LocationOverridePicker {
    static boolean ready;
    private static final String EXTRA = "dev.local.newsoul.location_picker_token";
    private static Class<?> picker;
    private static Method account;
    private static String token, owner;
    private static boolean legacy;

    static void install(XposedModule module, ClassLoader loader) {
        try {
            legacy = HostBindings.legacyChat(loader);
            picker = loader.loadClass(legacy ? "cn.soulapp.android.component.publish.ui.position.NewPoiActivity" : "cn.soulapp.android.component.publish.ui.position.NewPoiActivityB");
            Class<?> presenter = loader.loadClass(legacy ? "cn.soulapp.android.component.publish.ui.position.a" : "cn.soulapp.android.component.publish.ui.position.b");
            Class<?> poi = loader.loadClass(legacy ? "com.baidu.mapapi.search.core.PoiInfo" : "cn.soulapp.android.lib.common.location.bean.Poi");
            account = HostBindings.account(loader);
            for (String[] pair : legacy ? new String[][]{{"PoiChatAdapter", "d"}, {"PoiChatSearchAdapter", "d"}}
                    : new String[][]{{"PoiChatAdapterB", "e"}, {"PoiChatSearchAdapterB", "d"}}) {
                Class<?> adapter = loader.loadClass("cn.soulapp.android.component.publish.ui.position." + pair[0]);
                Class<?> listener = loader.loadClass(adapter.getName() + "$OnPoiItemClickListener");
                adapter.getMethod(pair[1], listener);
            }
            // Old O converts the selected Baidu POI and emits MetaSelectCityEvent.
            // Consume it only for our token-marked activity, before any host event.
            Method select = presenter.getDeclaredMethod(legacy ? "O" : "F", poi);
            module.hook(select).setId("new-soul/location-picker-select")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object screen = field(chain.getThisObject(), "a");
                        if (!(screen instanceof Activity) || !marked((Activity) screen)) return chain.proceed();
                        choose((Activity) screen, chain.getArg(0));
                        return null;
                    });
            Method init = picker.getDeclaredMethod("bindEvent");
            module.hook(init).setId("new-soul/location-picker-ui")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Activity activity = (Activity) chain.getThisObject();
                        if (!marked(activity)) return chain.proceed();
                        if (!valid(activity)) { activity.finish(); return null; }
                        Object result = chain.proceed();
                        try { bind(activity); }
                        catch (Throwable error) {
                            DebugDiagnostics.failure("location.picker.bind_failed", error);
                            toast(activity, "位置选择暂不可用"); activity.finish();
                        }
                        return result;
                    });
            // A can recreate adapters: install direct callbacks every time, not just on page creation.
            Method adapters = presenter.getDeclaredMethod(legacy ? "H" : "A");
            module.hook(adapters).setId("new-soul/location-picker-adapters")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        Object screen = field(chain.getThisObject(), "a");
                        if (screen instanceof Activity && marked((Activity) screen)) {
                            try { bind((Activity) screen); }
                            catch (Throwable error) {
                                DebugDiagnostics.failure("location.picker.bind_failed", error);
                                ((Activity) screen).finish();
                            }
                        }
                        return result;
                    });
            for (Method method : presenter.getDeclaredMethods()) module.deoptimize(method);
            for (Method method : picker.getDeclaredMethods()) module.deoptimize(method);
            ready = true;
            DebugDiagnostics.registration("location.picker", null);
        } catch (Throwable error) { ready = false; DebugDiagnostics.registration("location.picker", error); }
    }
    static void open(Activity activity) {
        try {
            if (!ready) throw new IllegalStateException("Picker unavailable");
            if (legacy && activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                token = null; owner = null;
                toast(activity, "请先在系统设置中允许 Soul 定位");
                return;
            }
            owner = (String) account.invoke(null);
            if (owner == null || owner.isEmpty()) throw new IllegalStateException("Account unavailable");
            token = UUID.randomUUID().toString();
            activity.startActivity(new Intent(activity, picker).putExtra(EXTRA, token)
                    .putExtra("fromPop", false).putExtra("pos_name", ""));
            DebugDiagnostics.event("location.picker.open", "native_search=true");
        } catch (Throwable error) {
            token = null; owner = null;
            DebugDiagnostics.failure("location.picker.open_failed", error);
            toast(activity, "无法打开位置选择");
        }
    }
    private static boolean marked(Activity activity) { return activity.getIntent().hasExtra(EXTRA); }
    private static boolean valid(Activity activity) {
        try {
            return ready && token != null && token.equals(activity.getIntent().getStringExtra(EXTRA))
                    && owner != null && owner.equals(account.invoke(null))
                    && !activity.isFinishing() && !activity.isDestroyed();
        } catch (Exception error) { return false; }
    }
    private static void bind(Activity activity) throws Exception {
        if (!valid(activity)) { activity.finish(); return; }
        Object presenter = field(activity, "presenter");
        for (String[] pair : legacy ? new String[][]{{"h", "d"}, {"i", "d"}} : new String[][]{{"g", "e"}, {"h", "d"}}) {
            Object adapter = field(presenter, pair[0]);
            Class<?> listener = adapter.getClass().getClassLoader().loadClass(adapter.getClass().getName() + "$OnPoiItemClickListener");
            Object callback = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("equals")) return proxy == args[0];
                    return "NewSoulLocationPicker";
                }
                if (method.getName().equals("onPoiItemClick")) choose(activity, args[0]);
                return null;
            });
            adapter.getClass().getMethod(pair[1], listener).invoke(adapter, callback);
        }
        DebugDiagnostics.event("location.picker.bound", "adapters=2");
    }
    private static void choose(Activity activity, Object poi) {
        if (!valid(activity)) { toast(activity, "位置选择已失效，请重新打开"); activity.finish(); return; }
        try {
            LocationOverridePoint point = legacy ? fromLegacyPoi(poi) : fromPoi(poi);
            String encoded = point.encode();
            SharedPreferences prefs = PluginPreferences.get(activity);
            // Selection only changes the saved target. The explicit switch controls activation.
            if (!PreferenceChanges.write(prefs, java.util.Collections.singletonMap(LocationOverridePoint.POINT, encoded)))
                throw new IllegalStateException("Save failed");
            token = null; owner = null;
            DebugDiagnostics.event("location.picker.saved", "coordinates_present=true");
            toast(activity, "位置已保存"); activity.finish();
        } catch (Throwable error) {
            DebugDiagnostics.failure("location.picker.selection_failed", error);
            toast(activity, "地点信息不完整，请选择具体地点");
        }
    }
    static LocationOverridePoint fromPoi(Object poi) throws Exception {
        if (poi == null) throw new IllegalArgumentException("No place");
        Object coordinates = field(poi, "location");
        if (coordinates == null || field(poi, "address") == null) throw new IllegalArgumentException("No coordinates");
        return new LocationOverridePoint(((Number) field(coordinates, "lat")).doubleValue(),
                ((Number) field(coordinates, "lng")).doubleValue(), string(poi, "title"), string(poi, "city"),
                string(poi, "province"), string(poi, "country"), string(poi, "code"), string(poi, "bdCityCode"));
    }
    static LocationOverridePoint fromLegacyPoi(Object poi) throws Exception {
        if (poi == null || field(poi, "address") == null) throw new IllegalArgumentException("No place");
        Object coordinates = field(poi, "location");
        if (coordinates == null) throw new IllegalArgumentException("No coordinates");
        // Match the old host's PoiInfo -> Poi conversion. It has no province,
        // country or ad-code output; don't fill these with the real location.
        String cityCode = ((Number) field(poi, "distance")).intValue() == -2 ? string(poi, "uid") : "";
        return new LocationOverridePoint(((Number) field(coordinates, "latitude")).doubleValue(),
                ((Number) field(coordinates, "longitude")).doubleValue(), string(poi, "name"),
                string(poi, "city"), "", "", "", cityCode);
    }
    private static String string(Object value, String name) throws Exception {
        Object result = field(value, name); return result instanceof String ? (String) result : "";
    }
    static Object field(Object value, String name) throws Exception {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(value); }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static void toast(Activity activity, String message) { Toast.makeText(activity, message, Toast.LENGTH_SHORT).show(); }
}
