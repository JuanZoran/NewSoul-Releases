package dev.local.newsoul;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** The two shipped share-sheet ABIs; legacy construction starts from a valid native icon slot. */
final class HostShareBindings {
    final boolean legacy;
    final Class<?> sheet, base, operation, adapter, holder;
    final Field post, user, kind, actions;
    final Method click;
    private final Constructor<?> create;
    private final Object emptyReasons;
    final Method[] clickCallers;
    HostShareBindings(ClassLoader loader) throws Exception {
        legacy = HostBindings.legacyChat(loader);
        sheet = loader.loadClass("cn.soulapp.android.square.SeedsShareDialogFragment");
        base = loader.loadClass("cn.soulapp.android.square.BaseSeedsDialogFragment");
        operation = loader.loadClass(base.getName() + "$Operate");
        post = field(sheet, legacy ? "B" : "E");
        user = field(sheet, legacy ? "A" : "D");
        kind = field(sheet, legacy ? "z" : "C");
        actions = field(base, legacy ? "n" : "q");
        click = sheet.getDeclaredMethod(legacy ? "l0" : "P0", operation, android.view.View.class, int.class);
        adapter = loader.loadClass(legacy ? "uo.d" : "cn.soulapp.android.square.adapter.TypeAdapter");
        holder = loader.loadClass(adapter.getName() + "$a");
        if (legacy) {
            clickCallers = new Method[] {
                    sheet.getDeclaredMethod("n", sheet, operation, android.view.View.class, int.class),
                    loader.loadClass("cn.soulapp.android.square.k").getDeclaredMethod("onItemClick", Object.class, android.view.View.class, int.class)
            };
            emptyReasons = java.lang.reflect.Array.newInstance(loader.loadClass("cn.soulapp.android.square.bean.ReasonEntry"), 0);
            create = operation.getConstructor(int.class, emptyReasons.getClass());
        } else {
            clickCallers = new Method[0];
            emptyReasons = null;
            create = operation.getConstructor(int.class, String.class, boolean.class);
        }
    }
    Object create(int iconSlot, String title, boolean selected) throws Exception {
        if (!legacy) return create.newInstance(iconSlot, title, selected);
        int[] icons = (int[]) operation.getField("h").get(null);
        // Newer icon slot numbers need not exist in 4.88. Use its native copy-link icon.
        int seed = iconSlot >= 0 && iconSlot < icons.length ? iconSlot : 26;
        Object value = create.newInstance(seed, emptyReasons);
        operation.getField("b").set(value, title);
        operation.getField("c").setBoolean(value, selected);
        return value;
    }
    private static Field field(Class<?> owner, String name) throws Exception {
        Field value = owner.getDeclaredField(name); value.setAccessible(true); return value;
    }
}
