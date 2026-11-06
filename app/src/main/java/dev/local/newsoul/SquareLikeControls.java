package dev.local.newsoul;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.view.ViewTreeObserver;
import android.util.AttributeSet;
import android.widget.Toast;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import android.os.SystemClock;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Manual and opt-in automatic likes over the current Soul feed. */
final class SquareLikeControls {
    static final String ENABLED = "square_visible_like";
    static final String ALL_LOADED = "square_like_all_loaded";
    static final String AUTO = "square_auto_like";
    static final String CITY_PAGES = "square_like_city_pages_v1";
    private static final String CITY = "cn.soulapp.android.component.square.location.LocationSquareActivity";
    private static final String MAIN = "cn.soulapp.android.component.startup.main.MainActivity";
    private static final String TAG = "new.soul.square.like";
    static boolean ready;
    private static boolean legacy;
    private static Class<?> postClass;
    private static Field postId;
    private static Method hasDeclare;
    private static Method getAdResponse;
    private static Field incentive;
    private static boolean callbacksRegistered;
    private static Method accountId;
    private record RequestKey(String account, long postId) {}
    // Prevent duplicate submissions across controllers, independently for each signed-in account.
    private static final Map<RequestKey, Long> recentRequests = new HashMap<>();
    // A stopped batch must not block unrelated posts, but an unanswered request
    // must still exclude its account/post, including after the 30-second recent window.
    // Tokens hold no controller/view reference and are released by terminal callbacks.
    private static final Map<RequestKey, Object> unresolvedRequests = new HashMap<>();

    static void install(XposedModule module, ClassLoader loader) {
        try {
            legacy = HostBindings.legacyChat(loader);
            accountId = HostBindings.account(loader);
            postClass = loader.loadClass("cn.soulapp.android.square.post.bean.Post");
            postId = postClass.getField("id");
            hasDeclare = HostBindings.postMethod(postClass, "hasDeclare");
            LoadedPostLikes.install(loader, postClass);
            getAdResponse = HostBindings.postMethod(postClass, "getAdResponse");
            try { incentive = postClass.getField("incentiveModel"); }
            catch (NoSuchFieldException missing) {
                if (!legacy) throw missing;
                incentive = null; // This card type is absent from the 4.88 Post model.
            }
            // MainActivity's compiled super.onResume path may bypass a framework
            // method hook. Application callbacks are dispatched by the framework.
            module.hook(loader.loadClass(MAIN).getDeclaredMethod("onCreate", Bundle.class))
                    .setId("new-soul/square-like-lifecycle")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Activity main = (Activity) chain.getThisObject();
                        if (!callbacksRegistered) {
                            main.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                                public void onActivityCreated(Activity a, Bundle b) {}
                                public void onActivityStarted(Activity a) {}
                                public void onActivityResumed(Activity a) {
                                    try { observe(a); } catch (Throwable error) {
                                        Log.e("SoulHook102", "square like UI unavailable", error);
                                    }
                                }
                                public void onActivityPaused(Activity a) { if (supported(a)) pause(a); }
                                public void onActivityStopped(Activity a) {}
                                public void onActivitySaveInstanceState(Activity a, Bundle b) {}
                                public void onActivityDestroyed(Activity a) { if (supported(a)) pause(a); }
                            });
                            callbacksRegistered = true;
                            Log.i("SoulHook102", "square like lifecycle registered");
                        }
                        return chain.proceed();
                    });
            Class<?> floating = loader.loadClass(
                    "cn.soulapp.android.component.square.widget.SquareFloatingButton");
            module.hook(floating.getDeclaredConstructor(Context.class, AttributeSet.class, int.class))
                    .setId("new-soul/square-like-envelope-create")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        View envelope = (View) chain.getThisObject();
                        envelope.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                            public void onViewAttachedToWindow(View v) { v.post(() -> attachSafely(v)); }
                            public void onViewDetachedFromWindow(View v) {
                                Controller controller = controllerFor(v);
                                if (controller != null) controller.pause();
                            }
                        });
                        return result;
                    });
            module.hook(floating.getDeclaredMethod("onVisibilityChanged", View.class, int.class))
                    .setId("new-soul/square-like-envelope-visibility")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        View envelope = (View) chain.getThisObject();
                        Controller controller = controllerFor(envelope);
                        if (controller != null) controller.syncVisibility();
                        return result;
                    });
            ready = true;
        } catch (Throwable error) {
            Log.e("SoulHook102", "square like unavailable", error);
            module.log(Log.ERROR, "SoulHook102", "square like unavailable", error);
        }
    }

    static String currentAccount() {
        try {
            Object value = accountId == null ? null : accountId.invoke(null);
            return value instanceof String && !((String) value).trim().isEmpty() ? (String) value : "";
        } catch (ReflectiveOperationException | RuntimeException error) { return ""; }
    }

    private static boolean supported(Activity activity) {
        String name = activity.getClass().getName();
        return MAIN.equals(name) || CITY.equals(name);
    }

    private static String envelopeName(Activity activity) {
        return CITY.equals(activity.getClass().getName()) ? "messageButton" : "message_button";
    }

    static void observe(Activity activity) {
        if (!ready || !supported(activity)) return;
        int id = activity.getResources().getIdentifier(envelopeName(activity), "id", activity.getPackageName());
        if (id != 0) attachSafely(activity.findViewById(id));
    }

    private static Controller controllerFor(View envelope) {
        if (envelope == null || !(envelope.getParent() instanceof ViewGroup)) return null;
        View action = ((ViewGroup) envelope.getParent()).findViewWithTag(TAG);
        if (!(action instanceof ActionView)) return null;
        Controller controller = ((ActionView) action).controller;
        return controller.envelope == envelope ? controller : null;
    }

    private static void attachSafely(View envelope) {
        Controller controller = null;
        try {
            if (envelope == null || !envelope.isAttachedToWindow()
                    || !(envelope.getParent() instanceof RelativeLayout
                    || legacy && envelope.getParent() instanceof LinearLayout
                    && ((LinearLayout) envelope.getParent()).getOrientation() == LinearLayout.VERTICAL)) return;
            Context context = envelope.getContext();
            while (context instanceof android.content.ContextWrapper && !(context instanceof Activity)) {
                context = ((android.content.ContextWrapper) context).getBaseContext();
            }
            if (!(context instanceof Activity)) return;
            Activity activity = (Activity) context;
            if (!supported(activity) || envelope.getId() != activity.getResources()
                    .getIdentifier(envelopeName(activity), "id", activity.getPackageName())) return;
            Controller existing = controllerFor(envelope);
            if (existing != null) { existing.resume(); return; }
            ViewGroup parent = (ViewGroup) envelope.getParent();
            ViewGroup.LayoutParams params;
            int index = parent.indexOfChild(envelope);
            if (index < 0) return;
            if (parent instanceof RelativeLayout) {
                RelativeLayout.LayoutParams relative = new RelativeLayout.LayoutParams(dp(activity, 68), dp(activity, 68));
                relative.addRule(RelativeLayout.ABOVE, envelope.getId());
                relative.addRule(RelativeLayout.ALIGN_RIGHT, envelope.getId());
                params = relative;
            } else {
                LinearLayout.LayoutParams linear = new LinearLayout.LayoutParams(dp(activity, 68), dp(activity, 68));
                linear.gravity = Gravity.LEFT;
                params = linear;
            }
            controller = new Controller(activity, envelope);
            controller.button.setTag(TAG);
            if (parent instanceof LinearLayout) parent.addView(controller.button, index, params);
            else parent.addView(controller.button, params);
            controller.resume();
            Log.i("SoulHook102", "square like attached beside envelope; native parent=" + parent.getClass().getSimpleName());
        } catch (Throwable error) {
            if (controller != null) {
                try {
                    try { controller.pause(); }
                    finally {
                        if (controller.button.getParent() instanceof ViewGroup)
                            ((ViewGroup) controller.button.getParent()).removeView(controller.button);
                    }
                } catch (Throwable cleanup) { error.addSuppressed(cleanup); }
            }
            Log.e("SoulHook102", "square like envelope attachment failed", error);
        }
    }

    private static void pause(Activity activity) {
        View button = activity.getWindow().getDecorView().findViewWithTag(TAG);
        if (button != null) ((ActionView) button).controller.pause();
    }

    /** Matches the envelope's 68dp touch area and inset 48dp outlined circle. */
    static final class ActionView extends View {
        final Controller controller;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path heart = new Path();
        int count;
        boolean busy;
        ActionView(Context context, Controller controller) {
            super(context);
            this.controller = controller;
            setFocusable(true);
            heart.moveTo(34, 45);
            heart.cubicTo(30, 41, 21, 35, 21, 29);
            heart.cubicTo(21, 22, 30, 20, 34, 27);
            heart.cubicTo(38, 20, 47, 22, 47, 29);
            heart.cubicTo(47, 35, 38, 41, 34, 45);
            heart.close();
        }
        void setStatus(int count, boolean busy) {
            if (this.count != count || this.busy != busy) {
                this.count = count;
                this.busy = busy;
                invalidate();
            }
            if (controller.profilePage) {
                setContentDescription(busy ? "正在点赞，点击停止，剩余 " + count + " 条"
                        : "一键点赞主页瞬间，可点赞 " + count + " 条");
                return;
            }
            setContentDescription(controller.autoLike() ? "自动点赞已开启，长按关闭，待点赞 " + count + " 条"
                    : busy ? "正在点赞，点击停止，长按开启自动点赞，剩余 " + count + " 条"
                    : (controller.allLoaded() ? "一键点赞已加载帖子，可点赞 " : "一键点赞当前可见帖子，可点赞 ") + count + " 条，长按开启自动点赞");
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            boolean dark = (getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            canvas.save();
            canvas.scale(getWidth() / 68f, getHeight() / 68f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(dark ? 0xff131320 : 0xfffafafa);
            canvas.drawCircle(34, 34, 24, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(0.8f);
            paint.setColor(dark ? 0xff444457 : 0xffd5d5df);
            canvas.drawCircle(34, 34, 24, paint);
            paint.setStrokeWidth(1.8f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(busy || isPressed() ? 0xff00b5a2 : dark ? 0xff77758f : 0xff858399);
            canvas.drawPath(heart, paint);
            if (count > 0) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(dark ? 0xff39394d : 0xffe9e9f0);
                canvas.drawCircle(52, 16, 7, paint);
                paint.setColor(dark ? 0xffd5d4e2 : 0xff66647c);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(9);
                canvas.drawText(count > 99 ? "99" : String.valueOf(count), 52, 19, paint);
            }
            canvas.restore();
        }
    }

    private static final class Target {
        final View row;
        final View like;
        final Object post;
        final long id;
        Target(View row, View like, Object post, long id) {
            this.row = row; this.like = like; this.post = post; this.id = id;
        }
    }

    static final class Controller {
        final Activity activity;
        final View envelope;
        final ActionView button;
        ViewTreeObserver observer;
        final ViewTreeObserver.OnPreDrawListener beforeDraw = () -> { syncVisibility(); return true; };
        final int pagerId, tagId, likeId;
        final int profileLikeId;
        final boolean cityPage, profilePage;
        java.util.function.BooleanSupplier profileAccess;
        final Map<RequestKey, Long> pending = recentRequests;
        final List<Target> batch = new ArrayList<>();
        View batchPager;
        String batchAccount;
        boolean active, running;
        int cursor, submitted, runId;
        boolean loadedBatch, automaticBatch;
        LoadedPostLikes.Source batchSource;
        final Set<Long> inFlight = new HashSet<>();
        final Runnable ticker = this::refresh;
        final Runnable worker = this::next;
        final Runnable requestTimeout = this::timeout;

        void timeout() {
            if (!running) return;
            if (!sameAccount()) finish(false);
            else fail("点赞响应超时，已停止");
        }

        Controller(Activity activity, View envelope) { this(activity, envelope, false); }

        Controller(Activity activity, View envelope, boolean profilePage) {
            this.profilePage = profilePage;
            this.activity = activity;
            this.envelope = envelope;
            cityPage = CITY.equals(activity.getClass().getName());
            pagerId = id("pager_square");
            tagId = id("key_item_post");
            likeId = id("iv_like");
            profileLikeId = id("tvLike");
            button = new ActionView(activity, this);
            PluginActionMark.set(button, true);
            button.setVisibility(View.GONE);
            button.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                public void onViewAttachedToWindow(View v) { resume(); }
                public void onViewDetachedFromWindow(View v) { pause(); }
            });
            button.setOnClickListener(v -> start());
            button.setOnLongClickListener(v -> {
                if (profilePage) return false;
                toggleAutoLike();
                return true;
            });
        }

        int id(String name) {
            return activity.getResources().getIdentifier(name, "id", activity.getPackageName());
        }

        void resume() {
            active = true;
            if (observer == null || !observer.isAlive()) {
                observer = envelope.getViewTreeObserver();
                observer.addOnPreDrawListener(beforeDraw);
            }
            syncVisibility();
            button.removeCallbacks(ticker);
            button.post(ticker);
        }

        void pause() {
            active = false;
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(beforeDraw);
            observer = null;
            button.removeCallbacks(ticker);
            finish(false);
            button.setVisibility(View.GONE);
        }

        View pager() {
            if (profilePage) {
                if (profileAccess != null && !profileAccess.getAsBoolean()) return null;
                if (!active || activity.isFinishing() || activity.isDestroyed() || !activity.hasWindowFocus()
                        || !visible(envelope) || !PluginPreferences.get(activity).getBoolean(ENABLED, true)) return null;
                LoadedPostLikes.Source source = LoadedPostLikes.find(envelope, false, true);
                return source == null ? null : source.view;
            }
            if (!active || activity.isFinishing() || activity.isDestroyed()
                    || !activity.hasWindowFocus() || (!cityPage && pagerId == 0) || tagId == 0
                    || (likeId == 0 && (!legacy || profileLikeId == 0))
                    || (!PluginPreferences.get(activity).getBoolean(ENABLED, true) && !autoLike())) return null;
            if (!visible(envelope)) return null;
            if (cityPage) {
                if (!LoadedPostLikes.cityReady || !PluginPreferences.get(activity).getBoolean(CITY_PAGES, false)) return null;
                LoadedPostLikes.Source source = LoadedPostLikes.find(activity.getWindow().getDecorView(), true);
                return source == null ? null : source.view;
            }
            View view = activity.findViewById(pagerId);
            return visible(view) ? view : null;
        }

        void refresh() {
            if (!active) return;
            View pager = pager();
            syncVisibility();
            LoadedPostLikes.Source visibleSource = LoadedPostLikes.find(pager, cityPage, profilePage);
            if (visibleSource != null) visibleSource.refreshCompleted();
            if (!running) {
                int count = pager == null ? 0 : snapshot().size();
                button.setStatus(count, false);
                if (count > 0 && autoLike() && inFlight.isEmpty()) startBatch(true);
            }
            button.postDelayed(ticker, 700);
        }

        // Native parent handles tab transitions. The envelope callback handles
        // its own visibility; pre-draw mirrors animation properties before rendering.
        void syncVisibility() {
            boolean show = pager() != null;
            if (running && !sameAccount()) finish(false);
            if (running && automaticBatch && !autoLike()) finish(false);
            if (running && loadedBatch && !sameSource()) finish(false);
            if (!show && running) finish(false);
            int visibility = show ? View.VISIBLE : View.GONE;
            if (button.getVisibility() != visibility) button.setVisibility(visibility);
            if (profilePage) return;
            if (button.getAlpha() != envelope.getAlpha()) button.setAlpha(envelope.getAlpha());
            if (button.getTranslationX() != envelope.getTranslationX()) button.setTranslationX(envelope.getTranslationX());
            if (button.getTranslationY() != envelope.getTranslationY()) button.setTranslationY(envelope.getTranslationY());
        }

        boolean allLoaded() {
            return LoadedPostLikes.ready && (autoLike()
                    || PluginPreferences.get(activity).getBoolean(ALL_LOADED, false));
        }

        boolean autoLike() {
            return !profilePage && LoadedPostLikes.ready && PluginPreferences.get(activity).getBoolean(AUTO, false);
        }

        boolean allowsPost(Object post) {
            // The two city scope switches are independent: only hidden city posts are excluded.
            return profilePage || cityPage && !PluginPreferences.get(activity).getBoolean(SquareFeedFilter.CITY_PAGES, false)
                    || SquareFeedFilter.allows(activity, post);
        }

        List<Target> snapshot() {
            List<Target> result = new ArrayList<>();
            String account = currentAccount();
            if (account.isEmpty()) return result;
            View root = pager();
            pending.entrySet().removeIf(e -> SystemClock.elapsedRealtime() - e.getValue() > 30000);
            if (root != null && allLoaded()) {
                LoadedPostLikes.Source source = LoadedPostLikes.find(root, cityPage, profilePage);
                Set<Long> ids = new HashSet<>();
                if (source != null) try {
                    for (Object post : source.data()) {
                        if (!postClass.isInstance(post)) continue;
                        long id = postId.getLong(post);
                        if (id > 0 && ordinaryPost(post) && allowsPost(post) && !((Boolean) hasDeclare.invoke(post))
                                && !pending.containsKey(new RequestKey(account, id)) && !unresolvedRequests.containsKey(new RequestKey(account, id)) && ids.add(id)) {
                            result.add(new Target(null, null, post, id));
                        }
                    }
                } catch (ReflectiveOperationException error) {
                    Log.w("SoulHook102", "loaded like snapshot unavailable", error);
                }
            } else if (root != null) collect(root, result, new HashSet<>(), account);
            return result;
        }

        void collect(View view, List<Target> result, Set<Long> ids, String account) {
            if (!visible(view)) return;
            Object post = view.getTag(tagId);
            if (postClass.isInstance(post)) {
                try {
                    long id = postId.getLong(post);
                    View like = view.findViewById(likeId);
                    if (profileLikeId != 0 && (profilePage && (like == null || !like.hasOnClickListeners())
                            || legacy && !profilePage && (like == null || !like.isShown()
                            || !like.isEnabled() || !like.hasOnClickListeners())))
                        like = view.findViewById(profileLikeId);
                    // hasDeclare also covers special reactions; never toggle these off.
                    if (id > 0 && ordinaryPost(post) && allowsPost(post) && !((Boolean) hasDeclare.invoke(post)) && !pending.containsKey(new RequestKey(account, id)) && !unresolvedRequests.containsKey(new RequestKey(account, id))
                            && like != null && like.isShown() && like.isEnabled()
                            && like.hasOnClickListeners() && ids.add(id)) {
                        result.add(new Target(view, like, post, id));
                    }
                } catch (ReflectiveOperationException error) {
                    Log.w("SoulHook102", "square like row skipped", error);
                }
                return;
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), result, ids, account);
            }
        }

        void toggleAutoLike() {
            if (!LoadedPostLikes.ready) {
                Toast.makeText(activity, "当前版本自动点赞未就绪", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean enabled = !autoLike();
            PreferenceChanges.putBoolean(PluginPreferences.get(activity), AUTO, enabled);
            finish(false);
            button.setStatus(snapshot().size(), false);
            button.removeCallbacks(ticker);
            if (active) button.post(ticker);
            Toast.makeText(activity, enabled ? "自动点赞已开启" : "自动点赞已关闭", Toast.LENGTH_SHORT).show();
            Log.i("SoulHook102", "square auto like toggled by long press: enabled=" + enabled);
        }

        void start() {
            if (autoLike()) {
                Toast.makeText(activity, "自动点赞已开启，长按爱心可关闭", Toast.LENGTH_SHORT).show();
                return;
            }
            if (running) {
                finish(true);
                return;
            }
            startBatch(false);
        }

        void startBatch(boolean automatic) {
            String account = currentAccount();
            if (account.isEmpty()) {
                if (!automatic) Toast.makeText(activity, "无法确认当前账号，重新登录后重试", Toast.LENGTH_SHORT).show();
                return;
            }
            batch.clear();
            batch.addAll(snapshot());
            if (!account.equals(currentAccount())) { batch.clear(); return; }
            batchAccount = account;
            if (batch.isEmpty()) {
                if (!automatic) Toast.makeText(activity, "当前范围内没有可点赞的帖子", Toast.LENGTH_SHORT).show();
                return;
            }
            batchPager = pager();
            loadedBatch = allLoaded();
            automaticBatch = automatic;
            batchSource = loadedBatch ? LoadedPostLikes.find(batchPager, cityPage, profilePage) : null;
            runId++;
            cursor = submitted = 0;
            running = true;
            button.setEnabled(true);
            Log.i("SoulHook102", "square like batch started: scope=" + (loadedBatch ? "loaded" : "visible") + "; count=" + batch.size());
            next();
        }

        void next() {
            if (!running) return;
            if (!sameAccount()) { finish(false); return; }
            if (automaticBatch && !autoLike()) { finish(false); return; }
            if (pager() != batchPager || allLoaded() != loadedBatch || cursor >= batch.size()) {
                finish(true);
                return;
            }
            if (loadedBatch) { nextLoaded(); return; }
            Target target = batch.get(cursor++);
            RequestKey key = new RequestKey(batchAccount, target.id);
            try {
                // Recycled rows, scrolled-away rows and newly liked posts are skipped.
                if (visible(target.row) && descendantOf(target.row, batchPager)
                        && target.row.getTag(tagId) == target.post
                        && postId.getLong(target.post) == target.id
                        && ordinaryPost(target.post) && allowsPost(target.post)
                        && !((Boolean) hasDeclare.invoke(target.post))
                        && target.like.isShown() && target.like.isEnabled()
                        && !pending.containsKey(key) && !unresolvedRequests.containsKey(key) && sameAccount()) {
                    pending.put(key, SystemClock.elapsedRealtime());
                    if (target.like.performClick()) submitted++;
                    else pending.remove(key);
                }
            } catch (Throwable error) {
                Log.w("SoulHook102", "square like click failed; not retried", error);
            }
            button.setStatus(batch.size() - cursor, true);
            button.postDelayed(worker, 500);
        }

        boolean sameAccount() {
            return batchAccount != null && !batchAccount.isEmpty() && batchAccount.equals(currentAccount());
        }

        boolean sameSource() {
            LoadedPostLikes.Source current = LoadedPostLikes.find(pager(), cityPage, profilePage);
            return current != null && batchSource != null && current.view == batchSource.view
                    && current.adapter == batchSource.adapter;
        }

        void nextLoaded() {
            if (!sameAccount()) { finish(false); return; }
            if (!sameSource()) { finish(true); return; }
            Target target = batch.get(cursor++);
            final int generation = runId;
            final Object request = new Object();
            final RequestKey key = new RequestKey(batchAccount, target.id);
            try {
                if (!batchSource.contains(target.post) || postId.getLong(target.post) != target.id
                        || !ordinaryPost(target.post) || !allowsPost(target.post) || (Boolean) hasDeclare.invoke(target.post)
                        || pending.containsKey(key) || unresolvedRequests.containsKey(key) || !sameAccount()) {
                    button.postDelayed(worker, 500);
                    return;
                }
                pending.put(key, SystemClock.elapsedRealtime());
                inFlight.add(target.id);
                unresolvedRequests.put(key, request);
                button.setStatus(batch.size() - cursor + 1, true);
                button.postDelayed(requestTimeout, 30000);
                LoadedPostLikes.send(activity, batchSource, target.post, key.account(), success -> {
                    if (unresolvedRequests.get(key) != request) return;
                    unresolvedRequests.remove(key);
                    if (!success) pending.remove(key);
                    if (!running || generation != runId) return;
                    if (!sameAccount()) { finish(false); return; }
                    inFlight.remove(target.id);
                    button.removeCallbacks(requestTimeout);
                    if (success) {
                        submitted++;
                        Log.i("SoulHook102", "loaded like response: success=" + submitted);
                        button.setStatus(batch.size() - cursor, true);
                        button.postDelayed(worker, 500);
                    } else {
                        fail("点赞失败，已停止；本批成功 " + submitted + " 条");
                    }
                });
            } catch (Throwable error) {
                // subscribe may submit before throwing. Without a terminal result,
                // retain this token rather than risking a second like on that post.
                inFlight.remove(target.id);
                Log.w("SoulHook102", "loaded like failed; batch stopped", error);
                if (!sameAccount()) finish(false);
                else fail("点赞未完成，已停止");
            }
        }

        void fail(String message) {
            if (automaticBatch) {
                PreferenceChanges.putBoolean(PluginPreferences.get(activity), AUTO, false);
                message += "，自动点赞已关闭";
            }
            finish(false);
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        }

        void finish(boolean notify) {
            button.removeCallbacks(worker);
            button.removeCallbacks(requestTimeout);
            if (running) {
                Log.i("SoulHook102", "square like batch ended: submitted=" + submitted);
                if (notify && !automaticBatch) Toast.makeText(activity, loadedBatch ? "本批已成功点赞 " + submitted + " 条" : "已触发 " + submitted + " 条点赞，结果以帖子状态为准",
                        Toast.LENGTH_SHORT).show();
            }
            running = false;
            inFlight.clear();
            runId++;
            batchSource = null;
            batch.clear();
            batchPager = null;
            batchAccount = null;
            button.setEnabled(true);
        }
    }

    private static boolean ordinaryPost(Object post) throws ReflectiveOperationException {
        // Some incentive cards replace their heart action with a task/navigation.
        return getAdResponse.invoke(post) == null && (incentive == null || incentive.get(post) == null);
    }

    private static boolean visible(View view) {
        if (view == null || !view.isAttachedToWindow() || !view.isShown() || view.getAlpha() == 0) return false;
        Rect rect = new Rect();
        return view.getGlobalVisibleRect(rect) && rect.width() > 0 && rect.height() > 0;
    }

    private static boolean descendantOf(View view, View root) {
        for (android.view.ViewParent parent = view.getParent(); parent != null; parent = parent.getParent()) {
            if (parent == root) return true;
        }
        return false;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
