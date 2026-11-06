package dev.local.newsoul;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Adds a post-only batch action to the native operation row; shares only after recipient confirmation. */
final class PostBatchShareControls {
    private static final int ACTION = 0x4e5311;
    static boolean ready;
    static boolean isBatchAction(int id) { return id == ACTION; }

    static void install(XposedModule module, ClassLoader loader) {
        try {
            MessageForwardControls picker = MessageForwardControls.active;
            if (picker == null || !picker.ready()) throw new IllegalStateException("Recipient picker unavailable");
            PostSharePayload.Factory payloads = PostSharePayload.Factory.resolve(loader);
            Class<?> sheet = loader.loadClass("cn.soulapp.android.square.SeedsShareDialogFragment");
            Class<?> base = loader.loadClass("cn.soulapp.android.square.BaseSeedsDialogFragment");
            Class<?> operation = loader.loadClass(base.getName() + "$Operate");
            HostShareBindings bindings = new HostShareBindings(loader);
            Field post = bindings.post, user = bindings.user, kind = bindings.kind, actions = bindings.actions;
            Field id = operation.getField("a");
            Field titles = operation.getField(bindings.legacy ? "i" : "j");
            Method account = HostBindings.account(loader);
            Method click = bindings.click;
            Method init = sheet.getDeclaredMethod("initViews", View.class);
            Method dismiss = sheet.getMethod("dismiss");
            // Bind consumer first. Unknown custom IDs never fall through to native publishing.
            module.hook(click).setId("new-soul/post-batch-click")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        if (id.getInt(chain.getArg(0)) != ACTION) return chain.proceed();
                        Activity activity = (Activity) sheet.getMethod("getActivity").invoke(chain.getThisObject());
                        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return false;
                        try {
                            Object current = post.get(chain.getThisObject());
                            if (kind.getInt(chain.getThisObject()) == 2 || user.get(chain.getThisObject()) != null || current == null)
                                throw new IllegalStateException("Not a post share panel");
                            boolean relay = current.getClass().getField("relay").getBoolean(current);
                            if (!PostShareControls.canShare(relay, ChatExperienceControls.enabled(PostShareControls.ENABLED))) {
                                Toast.makeText(activity, "该帖子限制分享", Toast.LENGTH_SHORT).show();
                                return false;
                            }
                            PostSharePayload snapshot = payloads.snapshot(current);
                            picker.open(activity, (String) account.invoke(null), "", MessageForwardRequest.POST, snapshot);
                            dismiss.invoke(chain.getThisObject());
                        } catch (Throwable error) {
                            DebugDiagnostics.failure("post.batch.open.failed", error);
                            Toast.makeText(activity, "无法分享，请重新打开帖子菜单", Toast.LENGTH_SHORT).show();
                        }
                        return false;
                    });
            module.deoptimize(click);
            for (Method caller : bindings.clickCallers) module.deoptimize(caller);
            module.hook(init).setId("new-soul/post-batch-action")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        try {
                            List<?> original = (List<?>) actions.get(chain.getThisObject());
                            List<Object> updated = new ArrayList<>();
                            if (original != null) for (Object item : original)
                                if (!isBatchAction(id.getInt(item))) updated.add(item);
                            Object current = post.get(chain.getThisObject());
                            if (current != null && kind.getInt(chain.getThisObject()) != 2 && user.get(chain.getThisObject()) == null
                                    && payloads.id.get(current) instanceof String
                                    && !((String) payloads.id.get(current)).isEmpty()) {
                                // Reuse a share/forward icon from this exact native operation row and theme.
                                int nativeIcon = shareIcon((String[]) titles.get(null));
                                if (nativeIcon >= 0) {
                                    Object item = bindings.create(nativeIcon, "多人分享", false);
                                    id.setInt(item, ACTION);
                                    int at = updated.size();
                                    for (int i = 0; i < updated.size(); i++)
                                        if (id.getInt(updated.get(i)) == 26) { at = i; break; }
                                    updated.add(at, item);
                                } else DebugDiagnostics.event("post.batch.menu.skipped", "reason=no_native_share_icon");
                            }
                            actions.set(chain.getThisObject(), updated);
                        } catch (Throwable error) { DebugDiagnostics.failure("post.batch.menu.failed", error); }
                        return chain.proceed();
                    });
            module.deoptimize(init);
            ready = true;
            DebugDiagnostics.registration("post.batch", null);
        } catch (Throwable error) {
            DebugDiagnostics.registration("post.batch", error);
            module.log(Log.ERROR, "SoulHook102", "Post batch share unavailable", error);
        }
    }

    static int shareIcon(String[] titles) {
        if (titles == null) return -1;
        for (String name : new String[]{"转发给souler", "转发", "分享"})
            for (int i = 0; i < titles.length; i++) if (name.equalsIgnoreCase(titles[i])) return i;
        return -1;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
}
