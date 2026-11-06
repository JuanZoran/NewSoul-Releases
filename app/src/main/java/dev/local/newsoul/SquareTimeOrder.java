package dev.local.newsoul;

import android.view.View;
import android.util.Log;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Prepare a private filtered/sorted projection before RecyclerView receives host updates. */
final class SquareTimeOrder {
    static final String ENABLED = "square_time_order_v1";
    static boolean ready;
    private static Class<?> squareAdapter, postClass;
    private static Field data, createTime;
    private static Method notifyChanged, inserted, removed, moved, changed;
    private static Method getAdapter, computingLayout, adResponse, itemCount, headerCount, setList;
    private static final WeakHashMap<Object, State> states = new WeakHashMap<>();
    private static final class State {
        WeakReference<View> view;
        List<Object> original = new ArrayList<>(), displayed = new ArrayList<>(), before;
        boolean queued, active, reset, notified, contentChanged, publishing;
        final List<ContentUpdate> contentUpdates = new ArrayList<>();
        int depth, beforeCount, beforeHeaders, displayedCount, displayedHeaders;
    }

    private static final class ContentUpdate {
        final Method method;
        final Object[] args;
        final List<?> current;
        final int headers, count;
        ContentUpdate(Method method, XposedInterface.Chain chain, Object adapter) throws ReflectiveOperationException {
            this.method = method;
            args = new Object[method.getParameterCount()];
            for (int i = 0; i < args.length; i++) args[i] = chain.getArg(i);
            current = new ArrayList<>((List<?>) data.get(adapter));
            headers = ((Number) headerCount.invoke(adapter)).intValue();
            count = ((Number) itemCount.invoke(adapter)).intValue();
        }
    }

    static void install(XposedModule module, ClassLoader loader) {
        try {
            squareAdapter = loader.loadClass("cn.soulapp.android.component.square.main.squarepost.SquareAdapter");
            Class<?> base = loader.loadClass("com.chad.library.adapter.base.BaseQuickAdapter");
            Class<?> recycler = loader.loadClass("androidx.recyclerview.widget.RecyclerView");
            Class<?> adapterBase = loader.loadClass("androidx.recyclerview.widget.RecyclerView$Adapter");
            postClass = loader.loadClass("cn.soulapp.android.square.post.bean.Post");
            createTime = postClass.getField("createTime");
            adResponse = HostBindings.postMethod(postClass, "getAdResponse");
            if (createTime.getType() != long.class) throw new NoSuchFieldException("createTime:long");
            data = base.getDeclaredField("data"); data.setAccessible(true);
            setList = base.getDeclaredMethod("setList", java.util.Collection.class);
            notifyChanged = squareAdapter.getMethod("notifyDataSetChanged");
            inserted = squareAdapter.getMethod("notifyItemRangeInserted", int.class, int.class);
            removed = squareAdapter.getMethod("notifyItemRangeRemoved", int.class, int.class);
            moved = squareAdapter.getMethod("notifyItemMoved", int.class, int.class);
            changed = squareAdapter.getMethod("notifyItemRangeChanged", int.class, int.class);
            itemCount = squareAdapter.getMethod("getItemCount");
            headerCount = squareAdapter.getMethod("getHeaderLayoutCount");
            getAdapter = recycler.getMethod("getAdapter");
            computingLayout = recycler.getMethod("isComputingLayout");
            for (Method method : adapterBase.getDeclaredMethods()) {
                if (!method.getName().startsWith("notify") || method.getReturnType() != void.class) continue;
                module.hook(method).setId("new-soul/feed-display/notify/" + method.toGenericString())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            State state = states.get(chain.getThisObject());
                            if (state != null && state.depth > 0) {
                                state.notified = true;
                                if (contentNotification(method)) state.contentUpdates.add(new ContentUpdate(method, chain, chain.getThisObject()));
                                return null;
                            }
                            Object adapter = chain.getThisObject();
                            if (state != null && !state.publishing && manages(adapter)) {
                                // A notification can also describe an in-place Post edit. Reproject
                                // even when every object identity is unchanged.
                                List<?> current = new ArrayList<>((List<?>) data.get(adapter));
                                List<Object> original = FeedTimeOrder.merge(state.original, state.displayed, current);
                                List<Object> ordered = project(state.view.get(), original);
                                if (FeedTimeOrder.same(state.displayed, current)
                                        && FeedTimeOrder.same(current, ordered)) {
                                    state.original = original;
                                    remember(adapter, state, ordered);
                                    return chain.proceed();
                                }
                                ContentUpdate update = contentNotification(method) ? new ContentUpdate(method, chain, adapter) : null;
                                int oldHeaders = state.displayedHeaders;
                                state.before = new ArrayList<>(state.displayed);
                                state.beforeCount = state.displayedCount;
                                state.beforeHeaders = oldHeaders;
                                // Only replacement setters reset original data. A full notification
                                // after editing getData() must retain posts hidden by filtering.
                                state.reset = false;
                                state.contentChanged = method.getName().equals("notifyDataSetChanged");
                                state.notified = true;
                                boolean fullRefresh = finishMutation(adapter, state);
                                if (state.active && !fullRefresh) replayContentChange(adapter, state, update, false);
                                return null;
                            }
                            return chain.proceed();
                        });
            }
            module.hook(squareAdapter.getMethod("onAttachedToRecyclerView", recycler))
                    .setId("new-soul/time-order/attach").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object adapter = chain.getThisObject();
                        if (!squareAdapter.isInstance(adapter)) return chain.proceed();
                        State state = states.computeIfAbsent(adapter, ignored -> new State());
                        state.view = new WeakReference<>((View) chain.getArg(0));
                        Object result = chain.proceed();
                        // Attachment happens before the first layout. Prepare immediately.
                        apply(adapter, state);
                        return result;
                    });
            Set<String> mutations = new HashSet<>(Arrays.asList("addData", "setData", "setList",
                    "replaceData", "setNewInstance", "setNewData", "remove", "removeAt", "setDiffNewData"));
            Set<String> resets = new HashSet<>(Arrays.asList("setList", "replaceData", "setNewInstance", "setNewData", "setDiffNewData"));
            for (Method method : base.getDeclaredMethods()) {
                if (!mutations.contains(method.getName()) || method.getReturnType() != void.class) continue;
                module.hook(method).setId("new-soul/time-order/" + method.toGenericString())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            Object adapter = chain.getThisObject();
                            State state = states.get(adapter);
                            View view = state == null || state.view == null ? null : state.view.get();
                            if (!ready || view == null || !square(view)) return chain.proceed();
                            boolean outer = state.depth == 0;
                            // Preserve BrvahAsyncDiffer's generation/cancellation contract. A new
                            // async submission must invalidate older queued commits even while projection
                            // is enabled; its eventual commit is handled by the exact dispatch hook.
                            if (outer && method.getName().equals("setDiffNewData") && method.getParameterCount() == 1) {
                                if (!hasRules(view) && state.active) apply(adapter, state);
                                if (!state.active && !hasRules(view)) return chain.proceed();
                                // g(List, Runnable) also has synchronous empty/null fast paths.
                                // Capture their notifications as a reset transaction, but leave a
                                // queued asynchronous submission's current projection untouched.
                                Object priorData = data.get(adapter);
                                state.before = new ArrayList<>((List<?>) priorData);
                                state.beforeCount = ((Number) itemCount.invoke(adapter)).intValue();
                                state.beforeHeaders = ((Number) headerCount.invoke(adapter)).intValue();
                                state.reset = state.contentChanged = true;
                                state.notified = false;
                                state.depth++;
                                try { return chain.proceed(); }
                                finally {
                                    state.depth--;
                                    if (state.notified || data.get(adapter) != priorData) finishMutation(adapter, state);
                                    else state.before = null;
                                }
                            }
                            if (outer && !hasRules(view)) {
                                // Restore hidden rows before host mutations use native indices.
                                // Thereafter no cached projection may override an async host commit.
                                if (state.active) apply(adapter, state);
                                if (!state.active) {
                                    return chain.proceed();
                                }
                            }
                            if (outer) {
                                state.before = new ArrayList<>((List<?>) data.get(adapter));
                                state.beforeCount = ((Number) itemCount.invoke(adapter)).intValue();
                                state.beforeHeaders = ((Number) headerCount.invoke(adapter)).intValue();
                                state.reset = state.notified = state.contentChanged = false;
                            }
                            state.reset |= resets.contains(method.getName());
                            state.contentChanged |= method.getName().equals("setData") || resets.contains(method.getName());
                            state.depth++;
                            try {
                                // The two-argument API dispatches before assigning data. Commit it
                                // atomically through the host's synchronous setter while projecting.
                                if (method.getName().equals("setDiffNewData")) {
                                    setList.invoke(adapter, (Object) chain.getArg(method.getParameterCount() - 1));
                                    return null;
                                }
                                return chain.proceed();
                            }
                            finally {
                                state.depth--;
                                if (outer) finishMutation(adapter, state);
                            }
                        });
                // Mutation bodies call final notify methods that may have been inlined by ART.
                module.deoptimize(method);
            }
            // BrvahAsyncDiffer commits its data before dispatching a DiffResult to this callback.
            // Intercept that exact synchronous dispatch boundary, rather than suppressing notifications
            // until a future frame (which could swallow an unrelated host content update).
            boolean legacy = HostBindings.legacyChat(loader);
            Class<?> callback = loader.loadClass(legacy ? "cx.c"
                    : "com.chad.library.adapter.base.diff.BrvahListUpdateCallback");
            Field callbackAdapter = null;
            for (Field field : callback.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.getType() != base) continue;
                if (callbackAdapter != null) throw new NoSuchFieldException("Ambiguous diff adapter");
                callbackAdapter = field;
            }
            if (callbackAdapter == null) throw new NoSuchFieldException("Diff adapter unavailable");
            callbackAdapter.setAccessible(true);
            final Field diffAdapter = callbackAdapter;
            Class<?> diffResult = loader.loadClass("androidx.recyclerview.widget.DiffUtil$DiffResult");
            Method dispatch = diffResult.getDeclaredMethod("dispatchUpdatesTo",
                    loader.loadClass("androidx.recyclerview.widget.ListUpdateCallback"));
            module.hook(dispatch).setId("new-soul/time-order/native-diff-dispatch")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object update = chain.getArg(0);
                        if (!callback.isInstance(update)) return chain.proceed();
                        Object adapter = diffAdapter.get(update);
                        State state = states.get(adapter);
                        if (state == null || state.publishing || !manages(adapter)) return chain.proceed();
                        if (state.depth > 0) {
                            // A host executor may commit synchronously inside the submission.
                            // Let the surrounding transaction publish exactly once after unwinding.
                            state.reset = state.notified = state.contentChanged = true;
                            return null;
                        }
                        state.before = new ArrayList<>(state.displayed);
                        state.beforeCount = state.displayedCount;
                        state.beforeHeaders = state.displayedHeaders;
                        state.reset = state.notified = state.contentChanged = true;
                        finishMutation(adapter, state);
                        return null;
                    });
            module.deoptimize(dispatch);
            // e(List, DiffResult, Runnable) is the host's asynchronous commit boundary.
            // Its dispatch call can otherwise be inlined past the hook on ART.
            Class<?> differ = loader.loadClass(legacy ? "cx.a" : "com.chad.library.adapter.base.diff.BrvahAsyncDiffer");
            Method asyncCommit = differ
                    .getDeclaredMethod("e", List.class, diffResult, Runnable.class);
            module.deoptimize(asyncCommit);
            module.deoptimize(differ
                    .getDeclaredMethod("g", List.class, Runnable.class));
            module.deoptimize(callback.getDeclaredMethod("onInserted", int.class, int.class));
            module.deoptimize(callback.getDeclaredMethod("onRemoved", int.class, int.class));
            module.deoptimize(callback.getDeclaredMethod("onMoved", int.class, int.class));
            module.deoptimize(callback.getDeclaredMethod("onChanged", int.class, int.class, Object.class));
            if (legacy) installLegacyPagination(module, loader);
            ready = true;
            Log.i("SoulHook102", "square pre-display filter and time order ready");
        } catch (Throwable error) { Log.e("SoulHook102", "square time order unavailable", error); }
    }

    /** 4.88 enters Loading before its callback rejects a displayed list smaller than five. */
    private static void installLegacyPagination(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> page = loader.loadClass("cn.soulapp.android.component.square.recommend.TimeLineFragment");
        Field adapterField = page.getDeclaredField("l"), scene = page.getDeclaredField("S");
        Method callback = page.getDeclaredMethod("H2"), load = page.getDeclaredMethod("e3", boolean.class);
        Method bridge = page.getDeclaredMethod("w", page);
        Method posted = loader.loadClass("gx.b$c").getDeclaredMethod("run");
        Method start = loader.loadClass("gx.b").getDeclaredMethod("n");
        Method listener = loader.loadClass("cn.soulapp.android.component.square.recommend.s1").getDeclaredMethod("onLoadMore");
        if (adapterField.getType() != squareAdapter || scene.getType() != int.class
                || callback.getReturnType() != void.class || load.getReturnType() != void.class) {
            throw new NoSuchMethodException("Legacy pagination ABI changed");
        }
        adapterField.setAccessible(true); scene.setAccessible(true); load.setAccessible(true);
        module.hook(callback).setId("new-soul/feed-pagination/legacy-eligibility")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object owner = chain.getThisObject(), adapter = adapterField.get(owner);
                    State state = states.get(adapter);
                    if (!manages(adapter) || state.depth != 0 || state.original.size() < 5
                            || ((List<?>) data.get(adapter)).size() >= 5) return chain.proceed();
                    // Preserve the exact eligible native branch. The host still owns request,
                    // cursor, Loading/re-entry protection, success, failure and completion.
                    scene.setInt(owner, 3);
                    load.invoke(owner, false);
                    return null;
                });
        module.deoptimize(callback);
        module.deoptimize(bridge);
        module.deoptimize(listener);
        module.deoptimize(posted);
        module.deoptimize(start);
    }

    static boolean manages(Object adapter) {
        if (squareAdapter == null || !squareAdapter.isInstance(adapter)) return false;
        State state = states.get(adapter);
        View view = state == null || state.view == null ? null : state.view.get();
        return ready && view != null && state.active && square(view);
    }

    private static boolean square(View view) {
        for (View p = view; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            try { if ("pager_square".equals(p.getResources().getResourceEntryName(p.getId()))) return true; }
            catch (RuntimeException ignored) { }
        }
        return false;
    }

    private static boolean finishMutation(Object adapter, State state) {
        boolean fullRefresh = false;
        try {
            List<?> current = (List<?>) data.get(adapter);
            state.original = state.reset || !state.active ? new ArrayList<>(current)
                    : FeedTimeOrder.merge(state.original, state.before, current);
            View view = state.view.get();
            List<Object> ordered = project(view, state.original);
            state.active = hasRules(view);
            data.set(adapter, ordered);
            remember(adapter, state, ordered);
            if (state.notified || !FeedTimeOrder.same(state.before, ordered)) {
                fullRefresh = publish(adapter, state, state.before, ordered, state.beforeCount, state.beforeHeaders, state.contentChanged);
            }
            if (!fullRefresh) for (ContentUpdate update : state.contentUpdates) replayContentChange(adapter, state, update, true);
        } catch (Throwable error) {
            Log.w("SoulHook102", "pre-display preparation failed", error);
            state.active = false;
            fullRefresh = true;
            state.publishing = true;
            try { notifyChanged.invoke(adapter); } catch (ReflectiveOperationException ignored) { }
            finally { state.publishing = false; }
        } finally { state.before = null; state.contentUpdates.clear(); }
        return fullRefresh;
    }

    private static boolean hasRules(View view) {
        return square(view) && (PluginPreferences.get(view.getContext()).getBoolean(ENABLED, false)
                || SquareFeedFilter.hasRules(view.getContext()));
    }

    private static List<Object> project(View view, List<?> original) {
        List<Object> visible = new ArrayList<>();
        for (Object item : original) if (SquareFeedFilter.allows(view.getContext(), item)) visible.add(item);
        return PluginPreferences.get(view.getContext()).getBoolean(ENABLED, false)
                ? FeedTimeOrder.sorted(visible, SquareTimeOrder::time) : visible;
    }

    private static void schedule(Object adapter, State state) {
        View view = state.view == null ? null : state.view.get();
        if (state.queued || view == null) return;
        state.queued = true;
        view.post(() -> apply(adapter, state));
    }

    private static void apply(Object adapter, State state) {
        state.queued = false;
        View view = state.view == null ? null : state.view.get();
        if (view == null || !ready || state.depth > 0) return;
        try {
            if (getAdapter.invoke(view) != adapter) return;
            if (Boolean.TRUE.equals(computingLayout.invoke(view))) {
                state.queued = true; view.postDelayed(() -> apply(adapter, state), 16); return;
            }
            List<?> current = (List<?>) data.get(adapter);
            if (!state.active) state.original = new ArrayList<>(current);
            else state.original = FeedTimeOrder.merge(state.original, state.displayed, current);
            List<Object> ordered = hasRules(view) ? project(view, state.original) : new ArrayList<>(state.original);
            state.active = hasRules(view);
            if (FeedTimeOrder.same(current, ordered)) { remember(adapter, state, ordered); return; }
            int count = ((Number) itemCount.invoke(adapter)).intValue();
            int headers = ((Number) headerCount.invoke(adapter)).intValue();
            data.set(adapter, ordered);
            remember(adapter, state, ordered);
            publish(adapter, state, current, ordered, count, headers, false);
        } catch (Throwable error) { Log.w("SoulHook102", "square display refresh skipped", error); }
    }

    private static boolean publish(Object adapter, State state, List<?> before, List<?> after,
            int oldCount, int oldHeaders, boolean contentChanged) throws ReflectiveOperationException {
        state.publishing = true;
        try {
            int headers = ((Number) headerCount.invoke(adapter)).intValue();
            int count = ((Number) itemCount.invoke(adapter)).intValue();
            // Empty views, load-more rows and headers use host-specific positions.
            if (before.isEmpty() || after.isEmpty() || headers != oldHeaders
                    || oldCount - before.size() != count - after.size()) {
                notifyChanged.invoke(adapter); return true;
            }
            // Send structural changes only: unchanged posts keep their bound image/media views.
            List<Object> working = new ArrayList<>(before);
            for (int i = 0; i < after.size(); i++) {
                Object item = after.get(i);
                if (i < working.size() && working.get(i) == item) continue;
                int found = -1;
                for (int j = i + 1; j < working.size(); j++) if (working.get(j) == item) { found = j; break; }
                if (found < 0) { working.add(i, item); inserted.invoke(adapter, i + headers, 1); }
                else { working.remove(found); working.add(i, item); moved.invoke(adapter, found + headers, i + headers); }
            }
            if (working.size() > after.size()) removed.invoke(adapter, after.size() + headers, working.size() - after.size());
            if (contentChanged) changed.invoke(adapter, headers, after.size());
            return false;
        } finally { state.publishing = false; }
    }

    private static boolean contentNotification(Method method) {
        return method.getName().equals("notifyItemRangeChanged") || method.getName().equals("notifyItemChanged");
    }

    /** Preserve post payloads and host header/footer updates across projection changes. */
    private static void replayContentChange(Object adapter, State state, ContentUpdate update, boolean hostRowsOnly) throws ReflectiveOperationException {
        if (update == null) return;
        boolean range = update.method.getName().equals("notifyItemRangeChanged");
        int start = ((Number) update.args[0]).intValue();
        int count = range ? ((Number) update.args[1]).intValue() : 1;
        Set<Object> affected = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        long end = (long) start + count;
        for (int p = Math.max(update.headers, start); p < update.headers + update.current.size() && (long) p < end; p++) {
            affected.add(update.current.get(p - update.headers));
        }
        Object[] args = update.args.clone();
        state.publishing = true;
        try {
            for (int p = 0; p < state.displayedCount; p++) {
                int index = p - state.displayedHeaders;
                boolean notify;
                if (index < 0) notify = p >= start && (long) p < end && p < update.headers;
                else if (index < state.displayed.size()) notify = !hostRowsOnly && affected.contains(state.displayed.get(index));
                else {
                    // Trailing rows are outside data: map their offset after the last post.
                    int oldPosition = update.headers + update.current.size() + index - state.displayed.size();
                    notify = oldPosition >= start && (long) oldPosition < end && oldPosition < update.count;
                }
                if (!notify) continue;
                args[0] = p;
                if (range) args[1] = 1;
                update.method.invoke(adapter, args);
            }
        } finally { state.publishing = false; }
    }

    private static void remember(Object adapter, State state, List<?> shown) throws ReflectiveOperationException {
        state.displayed = new ArrayList<>(shown);
        state.displayedCount = ((Number) itemCount.invoke(adapter)).intValue();
        state.displayedHeaders = ((Number) headerCount.invoke(adapter)).intValue();
    }

    private static long time(Object item) {
        if (!postClass.isInstance(item)) return 0;
        try { return adResponse.invoke(item) == null ? createTime.getLong(item) : 0; }
        catch (ReflectiveOperationException error) { return 0; }
    }

    static void refresh() {
        for (Object adapter : new ArrayList<>(states.keySet())) {
            State state = states.get(adapter);
            if (state != null) schedule(adapter, state);
        }
    }
}
