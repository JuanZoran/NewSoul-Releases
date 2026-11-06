package dev.local.newsoul;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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

/** Soul 6.35.0 post share action, rendered by the host's TypeAdapter. */
final class PostCopyControls {
    static boolean ready;
    private static final int COPY_TEXT = -220922;
    private static final String TAG = "SoulHook102";

    static boolean isCopyAction(int id) { return id==COPY_TEXT; }

    static void install(XposedModule module, ClassLoader loader) {
        try {
            HostShareBindings bindings = new HostShareBindings(loader);
            Class<?> dialog = bindings.sheet;
            Class<?> base = loader.loadClass("cn.soulapp.android.square.BaseSeedsDialogFragment");
            Class<?> operate = loader.loadClass(base.getName() + "$Operate");
            Field post = bindings.post;
            Field actions = bindings.actions;
            post.setAccessible(true);
            actions.setAccessible(true);
            Field content = loader.loadClass("cn.soulapp.android.square.post.bean.Post")
                    .getField("content");
            Field actionId = operate.getField("a");
            installMarks(module, loader, operate, actionId);

            Method init = dialog.getDeclaredMethod("initViews", View.class);
            Method click = bindings.click;
            Method dismiss = dialog.getMethod("dismiss");

            // Install the consumer first: a custom action must never reach Soul's submit handler.
            module.hook(click).setId("new-soul/post-copy-click")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object action = chain.getArg(0);
                        if (action == null || actionId.getInt(action) != COPY_TEXT) return chain.proceed();
                        View view = (View) chain.getArg(1);
                        Context context = view.getContext();
                        try {
                            Object current = post.get(chain.getThisObject());
                            String text = current == null ? null : (String) content.get(current);
                            if (text == null || text.trim().isEmpty()) {
                                Toast.makeText(context, "该帖子暂无可复制文案", Toast.LENGTH_SHORT).show();
                                return false;
                            }
                            ClipboardManager clipboard = context.getSystemService(ClipboardManager.class);
                            if (clipboard == null) throw new IllegalStateException("Clipboard unavailable");
                            clipboard.setPrimaryClip(ClipData.newPlainText("帖子文案", text));
                            ClipData copied = clipboard.getPrimaryClip();
                            boolean verified = copied != null && copied.getItemCount() == 1
                                    && text.contentEquals(copied.getItemAt(0).getText());
                            Log.i(TAG, "post text copied; chars=" + text.length() + "; verified=" + verified);
                            Toast.makeText(context, "已复制文案", Toast.LENGTH_SHORT).show();
                            dismiss.invoke(chain.getThisObject());
                        } catch (Throwable error) {
                            Log.e(TAG, "post text copy failed", error);
                            Toast.makeText(context, "复制失败，请重新打开分享页", Toast.LENGTH_SHORT).show();
                        }
                        return false;
                    });
            module.hook(init).setId("new-soul/post-copy-action")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Object current = post.get(chain.getThisObject());
                            if (current != null) {
                                List<?> original = (List<?>) actions.get(chain.getThisObject());
                                List<Object> updated = new ArrayList<>();
                                if (original != null) {
                                    for (Object item : original) {
                                        if (actionId.getInt(item) != COPY_TEXT) updated.add(item);
                                    }
                                }
                                int index = updated.size();
                                for (int i = 0; i < updated.size(); i++) {
                                    if (actionId.getInt(updated.get(i)) == 26) { index = i; break; }
                                }
                                // Native copy action 51 supplies Soul's light/night icon.
                                Object item = bindings.create(51, "复制文案", false);
                                actionId.setInt(item, COPY_TEXT);
                                updated.add(index, item);
                                actions.set(chain.getThisObject(), updated);
                                Log.i(TAG, "post copy action added; count=" + updated.size());
                            }
                        } catch (Throwable error) {
                            Log.e(TAG, "post copy action unavailable", error);
                        }
                        return chain.proceed();
                    });
            module.deoptimize(click);
            for (Method caller : bindings.clickCallers) module.deoptimize(caller);
            ready = true;
            Log.i(TAG, "post copy hooks installed");
        } catch (Throwable error) {
            module.log(Log.ERROR, TAG, "post copy hooks unavailable", error);
        }
    }

    private static void installMarks(XposedModule module, ClassLoader loader, Class<?> operate, Field actionId) {
        try {
            Class<?> holder = loader.loadClass(HostBindings.legacyChat(loader) ? "uo.d$a" : "cn.soulapp.android.square.adapter.TypeAdapter$a");
            Method bind = holder.getDeclaredMethod("a", operate);
            Field icon = holder.getDeclaredField("c");
            icon.setAccessible(true);
            module.hook(bind).setId("new-soul/action-marks-post")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            PluginActionMark.set((View) icon.get(chain.getThisObject()),
                                    actionId.getInt(chain.getArg(0)) == COPY_TEXT
                                            || PostBatchShareControls.isBatchAction(actionId.getInt(chain.getArg(0)))
                                            || ProfileBookmarkControls.isBookmarkAction(actionId.getInt(chain.getArg(0))));
                        } catch (Throwable error) { Log.w(TAG, "post action mark unavailable", error); }
                        return result;
                    });
            module.deoptimize(bind);
        } catch (Throwable error) { Log.w(TAG, "post action mark unavailable", error); }
    }
}
