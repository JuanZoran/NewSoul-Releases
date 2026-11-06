package dev.local.newsoul;

import android.content.SharedPreferences;
import android.util.Log;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Only replaces bound text on the owner's profile; never writes to the host model. */
final class FollowDisguiseControls {
    static final String FOLLOW = "profile_disguise_follow_";
    static final String FOLLOWED = "profile_disguise_followed_";
    static final String VISITOR = "profile_disguise_visitor_";
    static final String DAYS = "profile_disguise_days_";
    static boolean revisionReady, metaReady, headerReady;
    static boolean revisionDaysReady, metaDaysReady;
    private static final WeakHashMap<TextView, Display> DISPLAYS = new WeakHashMap<>();
    private static XposedModule logger;
    private static final java.util.Set<String> REPORTED = new java.util.HashSet<>();
    private static Field follow, followed, visitors;
    private static Method formatter, oldFormatter;

    private static final class Display {
        final int original;
        final String key;
        final CharSequence text;
        final Method format;
        final String daySource;
        Display(int original, String key, CharSequence text, Method format) {
            this(original, key, text, format, null);
        }
        Display(int original, String key, CharSequence text, Method format, String daySource) {
            this.original = original; this.key = key; this.text = text; this.format = format;
            this.daySource = daySource;
        }
    }
    static boolean daysReady() { return revisionDaysReady || metaDaysReady; }
    static boolean ready() { return revisionReady || metaReady || headerReady; }
    static void install(XposedModule module, ClassLoader loader) {
        logger = module;
        final boolean legacy;
        try {
            legacy = HostBindings.legacyChat(loader);
            Class<?> count = loader.loadClass("cn.soulapp.android.component.home.api.user.user.bean.UserFollowCount");
            follow = field(count, "followNum"); followed = field(count, "beFollowNum");
            visitors = field(count, "beViewNum");
            formatter = HostBindings.profileNumberFormatter(loader, false);
            oldFormatter = HostBindings.profileNumberFormatter(loader, true);
        } catch (Throwable error) { failure("fields", error); return; }
        revisionReady = !legacy && installSurface(module, loader, "revision",
                HostDexRules.PROFILE, null,
                "cn.soulapp.android.component.home.revision.RevisionProfileProvider$ViewHolder", "o", "q", "g0", "h0", "b", "d", true);
        metaReady = legacy ? installLegacyMeta(module, loader) : installSurface(module, loader, "meta",
                "cn.soulapp.android.component.home.meta.view.MetaFollowFansContainerView", "w",
                "cn.soulapp.android.component.home.databinding.CUsrMetaFollowFansContainerBinding", "e", "g", "q", "r", "k", "b", false);
        try {
            Class<?> owner = loader.loadClass("cn.soulapp.android.component.home.me.UserHomeHeaderHelper");
            Class<?> count = follow.getDeclaringClass();
            // Use DEX names, not Kotlin metadata aliases supplied by JADX.
            Field type = field(owner, legacy ? "O" : "M"), first = field(owner, "j"), second = field(owner, "l");
            Field visitor = field(owner, "n"), visitorNew = field(owner, "o");
            Field visitorLabel = field(owner, "q"), visitorLabelNew = field(owner, "r");
            Method bind = owner.getDeclaredMethod("setUserFollowData", count);
            module.hook(bind).setId("new-soul/follow-disguise-header")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            Object target = chain.getThisObject();
                            reportOnce("header type=" + type.getInt(target));
                            boolean own = type.getInt(target) == 0;
                            bindPair(first.get(target), second.get(target), chain.getArg(0), own, oldFormatter);
                            bindVisitor(visitor.get(target), visitorLabel.get(target), chain.getArg(0), own, oldFormatter);
                            bindVisitor(visitorNew.get(target), visitorLabelNew.get(target), chain.getArg(0), own, oldFormatter);
                        } catch (Throwable error) { reportErrorOnce("header bind", error); }
                        return result;
                    });
            module.deoptimize(bind);
            for (Method caller : owner.getDeclaredMethods()) module.deoptimize(caller);
            headerReady = true;
            DebugDiagnostics.registration("profile.disguise.header", null);
        } catch (Throwable error) { failure("header", error); }
        revisionDaysReady = legacy ? installLegacyMeDays(module, loader) : installDays(module, loader,
                HostDexRules.PROFILE, null,
                "cn.soulapp.android.component.home.revision.RevisionProfileProvider$ViewHolder", "V", true);
        metaDaysReady = legacy ? installLegacyMetaDays(module, loader) : installDays(module, loader,
                "cn.soulapp.android.component.home.meta.me2.userinfo.UserInfoProvider", "t0",
                "cn.soulapp.android.component.home.databinding.CUsrLayoutMeInfoNewBinding", "y", false);
        module.log(Log.INFO, "SoulHook102", "days disguise registered: revision=" + revisionDaysReady + "; meta=" + metaDaysReady);
        module.log(Log.INFO, "SoulHook102", "follow disguise registered: revision=" + revisionReady
                + "; meta=" + metaReady + "; header=" + headerReady);
    }
    private static boolean installLegacyMeta(XposedModule module, ClassLoader loader) {
        try {
            Class<?> owner = loader.loadClass("cn.soulapp.android.component.home.meta.adapter.MetaUserProfileProvider");
            Class<?> holder = loader.loadClass(owner.getName() + "$a");
            Class<?> user = loader.loadClass("com.soul.component.componentlib.service.user.bean.User");
            Field type = field(owner, "b"), counts = field(owner, "e");
            Method first = holder.getMethod("Y"), second = holder.getMethod("a0");
            Method visitor = holder.getMethod("f0"), visitorNew = holder.getMethod("g0");
            Method label = holder.getMethod("h0"), labelNew = holder.getMethod("i0");
            Method bind = owner.getDeclaredMethod("d1", user, holder);
            module.hook(bind).setId("new-soul/follow-disguise-legacy-meta")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            Object view = chain.getArg(1), count = counts.get(chain.getThisObject());
                            boolean own = type.getInt(chain.getThisObject()) == 0;
                            if (view != null) {
                                bindPair(first.invoke(view), second.invoke(view), count, own, oldFormatter);
                                bindVisitor(visitor.invoke(view), label.invoke(view), count, own, oldFormatter);
                                bindVisitor(visitorNew.invoke(view), labelNew.invoke(view), count, own, oldFormatter);
                            }
                        } catch (Throwable error) { reportErrorOnce("legacy meta bind", error); }
                        return result;
                    });
            deoptimizeOwner(module, owner);
            DebugDiagnostics.registration("profile.disguise.meta", null);
            return true;
        } catch (Throwable error) { failure("legacy-meta", error); return false; }
    }
    private static boolean installLegacyMeDays(XposedModule module, ClassLoader loader) {
        try {
            Class<?> owner = loader.loadClass("cn.soulapp.android.component.home.me.MeFragment");
            Class<?> user = loader.loadClass("com.soul.component.componentlib.service.user.bean.User");
            Field days = user.getField("registerDay");
            Method root = owner.getMethod("getView");
            Method bind = owner.getDeclaredMethod("j3", user, boolean.class);
            module.hook(bind).setId("new-soul/days-legacy-me")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            android.view.View view = (android.view.View) root.invoke(chain.getThisObject());
                            if (view != null) {
                                int id = view.getResources().getIdentifier("me_time", "id", "cn.soulapp.android");
                                android.view.View number = id == 0 ? null : view.findViewById(id);
                                if (number instanceof TextView) bindDays((TextView) number, chain.getArg(0), days, true);
                            }
                        } catch (Throwable error) { reportErrorOnce("legacy me days", error); }
                        return result;
                    });
            deoptimizeOwner(module, owner);
            DebugDiagnostics.registration("profile.disguise.days-legacy-me", null);
            return true;
        } catch (Throwable error) { failure("days-legacy-me", error); return false; }
    }
    private static boolean installLegacyMetaDays(XposedModule module, ClassLoader loader) {
        try {
            Class<?> owner = loader.loadClass("cn.soulapp.android.component.home.meta.adapter.MetaUserProfileProvider");
            Class<?> holder = loader.loadClass(owner.getName() + "$a");
            Class<?> user = loader.loadClass("com.soul.component.componentlib.service.user.bean.User");
            Field type = field(owner, "b"), days = user.getField("registerDay");
            Method number = holder.getMethod("I"), bind = owner.getDeclaredMethod("k1", user, holder);
            module.hook(bind).setId("new-soul/days-legacy-meta")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            Object view = chain.getArg(1);
                            if (view != null) bindDays((TextView) number.invoke(view), chain.getArg(0), days,
                                    type.getInt(chain.getThisObject()) == 0);
                        } catch (Throwable error) { reportErrorOnce("legacy meta days", error); }
                        return result;
                    });
            deoptimizeOwner(module, owner);
            DebugDiagnostics.registration("profile.disguise.days-legacy-meta", null);
            return true;
        } catch (Throwable error) { failure("days-legacy-meta", error); return false; }
    }
    private static void deoptimizeOwner(XposedModule module, Class<?> owner) {
        for (Method method : owner.getDeclaredMethods()) module.deoptimize(method);
    }
    private static void bindDays(TextView view, Object source, Field days, boolean own) throws Exception {
        if (view == null) return;
        DISPLAYS.remove(view);
        FollowDisguiseShortcut.bind(view, false);
        if (!own || source == null) return;
        String raw = (String) days.get(source);
        if (raw == null || raw.isEmpty() || !view.getText().toString().contains(raw)) return;
        Display display = new Display(FollowDisguiseRules.parse(raw, false), DAYS, view.getText(), null, raw);
        DISPLAYS.put(view, display);
        FollowDisguiseShortcut.bind(view, true);
        render(view, display);
    }
    private static boolean installSurface(XposedModule module, ClassLoader loader, String id,
            String ownerName, String methodName, String bindingName, String firstName, String secondName,
            String visitorName, String visitorLabelName, String typeName, String countName, boolean holder) {
        try {
            Class<?> owner = loader.loadClass(ownerName), binding = loader.loadClass(bindingName);
            Class<?> user = loader.loadClass("com.soul.component.componentlib.service.user.bean.User");
            Method getBinding = holder ? binding.getDeclaredMethod("b") : null;
            if (getBinding != null) getBinding.setAccessible(true);
            Class<?> fields = holder ? getBinding.getReturnType() : binding;
            Field first = field(fields, firstName), second = field(fields, secondName);
            Field visitor = field(fields, visitorName), visitorLabel = field(fields, visitorLabelName);
            Field type = field(owner, typeName), counts = field(owner, countName);
            Method bind = holder ? HostSymbols.method(HostDexRules.FOLLOW, loader)
                    : owner.getDeclaredMethod(methodName, user, binding);
            module.hook(bind).setId("new-soul/follow-disguise-" + id)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            Object target = chain.getThisObject(), view = chain.getArg(1);
                            if (view != null) {
                                Object bound = holder ? getBinding.invoke(view) : view;
                                reportOnce(id + " type=" + type.getInt(target) + "; count=" + (counts.get(target) != null));
                                boolean own = type.getInt(target) == 0;
                                Object count = counts.get(target);
                                bindPair(first.get(bound), second.get(bound), count, own, formatter);
                                bindVisitor(visitor.get(bound), visitorLabel.get(bound), count, own, formatter);
                            }
                        } catch (Throwable error) { reportErrorOnce(id + " bind", error); }
                        return result;
                    });
            module.deoptimize(bind);
            // Private render helpers can be inlined into their owning class's bind methods.
            for (Method caller : owner.getDeclaredMethods()) module.deoptimize(caller);
            DebugDiagnostics.registration("profile.disguise." + id, null);
            return true;
        } catch (Throwable error) { failure(id, error); return false; }
    }
    private static boolean installDays(XposedModule module, ClassLoader loader, String ownerName,
            String methodName, String bindingName, String viewName, boolean holder) {
        String id = holder ? "days-revision" : "days-meta";
        try {
            Class<?> owner = loader.loadClass(ownerName), binding = loader.loadClass(bindingName);
            Class<?> user = loader.loadClass("com.soul.component.componentlib.service.user.bean.User");
            Field days = user.getField("registerDay"), type = field(owner, "b");
            Method getBinding = holder ? binding.getDeclaredMethod("b") : null;
            if (getBinding != null) getBinding.setAccessible(true);
            Field number = field(holder ? getBinding.getReturnType() : binding, viewName);
            Method bind = holder ? HostSymbols.method(HostDexRules.DAYS, loader)
                    : owner.getDeclaredMethod(methodName, user, binding);
            module.hook(bind).setId("new-soul/" + id)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            Object bound = chain.getArg(1), source = chain.getArg(0);
                            if (bound != null) {
                                if (holder) bound = getBinding.invoke(bound);
                                TextView view = (TextView) number.get(bound);
                                if (view != null) {
                                    DISPLAYS.remove(view);
                                    FollowDisguiseShortcut.bind(view, false);
                                    if (type.getInt(chain.getThisObject()) == 0 && source != null) {
                                        String raw = (String) days.get(source);
                                        if (raw != null && view.getText().toString().contains(raw) && !raw.isEmpty()) {
                                            int original = FollowDisguiseRules.parse(raw, false);
                                            Display display = new Display(original, DAYS, view.getText(), null, raw);
                                            DISPLAYS.put(view, display);
                                            FollowDisguiseShortcut.bind(view, true);
                                            render(view, display);
                                            reportOnce("bound " + id);
                                        }
                                    }
                                }
                            }
                        } catch (Throwable error) { reportErrorOnce(id + " bind", error); }
                        return result;
                    });
            module.deoptimize(bind);
            for (Method caller : owner.getDeclaredMethods()) module.deoptimize(caller);
            DebugDiagnostics.registration("profile.disguise." + id, null);
            return true;
        } catch (Throwable error) { failure(id, error); return false; }
    }
    private static void bindPair(Object first, Object second, Object count, boolean own, Method format) throws Exception {
        bind((TextView) first, count, own, FOLLOW, follow, format);
        bind((TextView) second, count, own, FOLLOWED, followed, format);
    }
    private static void bindVisitor(Object view, Object label, Object count, boolean own, Method format) throws Exception {
        CharSequence metric = label instanceof TextView ? ((TextView) label).getText() : null;
        bind((TextView) view, count, own && FollowDisguiseRules.isVisitorMetric(metric), VISITOR, visitors, format);
    }
    private static void bind(TextView view, Object count, boolean own, String key, Field field, Method format) throws Exception {
        if (view == null) return;
        DISPLAYS.remove(view); // Recycled non-owner rows must never retain a previous owner's binding.
        FollowDisguiseShortcut.bind(view, own && count != null);
        if (!own || count == null) return;
        Display display = new Display(field.getInt(count), key, view.getText(), format);
        DISPLAYS.put(view, display);
        reportOnce("bound " + key);
        render(view, display);
    }
    private static void render(TextView view, Display display) throws Exception {
        SharedPreferences prefs = PluginPreferences.get(view.getContext());
        if (!prefs.getBoolean(display.key + "enabled", false)) { view.setText(display.text); return; }
        String mode = prefs.getString(display.key + "mode", "absolute");
        int count = FollowDisguiseRules.apply(display.original, true, mode, prefs.getInt(display.key + mode, 0));
        view.setText(display.daySource == null ? (CharSequence) display.format.invoke(null, count)
                : FollowDisguiseRules.replaceDays(display.text.toString(), display.daySource, count));
    }
    static void refresh() {
        // Called on the UI thread after settings are saved. Weak keys do not retain Activities.
        for (java.util.Map.Entry<TextView, Display> entry : DISPLAYS.entrySet()) {
            try { render(entry.getKey(), entry.getValue()); }
            catch (Exception error) { reportErrorOnce("refresh", error); }
        }
    }
    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void reportOnce(String state) {
        if (logger != null && REPORTED.add(state)) logger.log(Log.INFO, "SoulHook102", "follow disguise " + state);
    }
    private static void reportErrorOnce(String state, Throwable error) {
        if (logger != null && REPORTED.add("error:" + state))
            logger.log(Log.ERROR, "SoulHook102", "follow disguise " + state + " failed", error);
    }
    private static void failure(String id, Throwable error) {
        DebugDiagnostics.registration("profile.disguise." + id, error);
        if (logger != null) logger.log(Log.ERROR, "SoulHook102", "follow disguise " + id + " unavailable", error);
        Log.e("SoulHook102", "follow disguise " + id + " unavailable", error);
    }
}
