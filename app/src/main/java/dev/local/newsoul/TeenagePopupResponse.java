package dev.local.newsoul;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Xyner's teenager/config response strategy, scoped to the verified model. */
final class TeenagePopupResponse {
    final Method parse;
    private final HostDecodedResponse response;
    private final Class<?> modelType;
    private final Field mode, exam, popped;

    TeenagePopupResponse(ClassLoader loader) throws Exception {
        response = new HostDecodedResponse(loader);
        parse = response.parse;
        modelType = loader.loadClass("cn.soulapp.android.client.component.middle.platform.model.api.account.FuncSetting");
        mode = flag("isTeenageMode");
        exam = flag("needTeenageExam");
        popped = flag("isPopTeenWindow");
    }

    private Field flag(String name) throws Exception {
        Field field = modelType.getDeclaredField(name);
        if (field.getType() != boolean.class || Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers()))
            throw new NoSuchFieldException(name + " must be a writable boolean");
        field.setAccessible(true);
        return field;
    }

    boolean apply(Object raw, Object result, boolean enabled) throws Exception {
        if (!enabled) return false;
        Object model = response.data(raw, result, "/teenager/config");
        if (!modelType.isInstance(model)) return false;
        mode.setBoolean(model, false);
        exam.setBoolean(model, true);
        popped.setBoolean(model, true);
        return true;
    }
}
