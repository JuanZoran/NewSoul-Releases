package dev.local.newsoul;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.xmlpull.v1.XmlPullParser;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Host presentation XML with plugin data and callbacks, in a plugin-owned input slot. */
final class AiAutomaticSuggestionCard implements AutoCloseable {
    interface Actions { void choose(String text); void refresh(); void close(); }
    private static final String CONSTRAINT = "androidx.constraintlayout.widget.ConstraintLayout";
    private static final String INPUT_BAR = "cn.soulapp.android.component.chat.inputmenu.InputBar";
    private static final String MENU = "cn.soulapp.android.component.chat.inputmenu.SingleChatMediaMenu";
    private static final String TAG = "newsoul.ai.automatic.card.v1";
    private static final String TITLE = "NewSoul回复推荐";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Activity, WeakReference<AiAutomaticSuggestionCard>> ACTIVE = new WeakHashMap<>();
    private static final Map<Activity, WeakReference<Suppression>> SUPPRESSIONS = new WeakHashMap<>();
    private static final Map<View, Cover> COVERS = new WeakHashMap<>();
    private static final Map<View, RowBinding> ROW_BINDINGS = new WeakHashMap<>();
    private static boolean installed, rowBindingObserved;
    private static Field listenerInfo, clickListener;
    private enum Stage {
        THREAD, INPUT, RESULT, IDENTITY, COMPOSER, INPUT_BAR, MEDIA_MENU, ATTACHED, KEYBOARD,
        INFLATER, ROOT_XML, ROOT_CHILDREN, NATIVE_ASSETS, PRESENTATION, OPTIONS_XML, OPTION_CHILDREN,
        OPTION_TEXT, REMAP_IDS, COVER_OPENING, COVER_ROW, INSERT, LISTENERS, MARK, BACK_HANDLER,
        SHOWN, MEASURED, NOT_MEASURED, DISMISS_IDENTITY, DISMISS_PARENT, DISMISS_MENU, DETACHED, CLOSED,
        SUPPRESS_START, SUPPRESS_RELEASE, SUPPRESS_FAILURE, SLOT_WAIT, SLOT_ROW, SLOT_OPENING, SLOT_REBIND
    }
    private static final class Trace { Stage stage = Stage.INPUT; }

    private final WeakReference<Activity> activity;
    private final AiChatContext.Snapshot snapshot;
    private final Actions actions;
    private final LinearLayout bar;
    private final View root, composer, mediaMenu, refresh, icon;
    private final List<View> choices = new ArrayList<>();
    private final ViewTreeObserver.OnGlobalLayoutListener layout = this::checkLayout;
    private final View.OnAttachStateChangeListener attachment;
    private final long openedAt = SystemClock.uptimeMillis();
    private ViewTreeObserver observer;
    private Runnable unregisterBack;
    private AlertDialog fullTextDialog;
    private boolean closed, busy, markIcon, mounting;
    private ViewGroup mountedSlot;
    private Suppression suppression;
    private Map<Integer, Integer> nativeIds;

    /** Also restores a covered host row before its adapter rebinds that exact ViewHolder. */
    static synchronized void installBackHook(XposedModule module, ClassLoader loader) {
        if (installed) return;
        installed = true;
        boolean backReady = false;
        try {
            Class<?> fragment = loader.loadClass("cn.soulapp.android.component.chat.fragment.ConversationFragment");
            Method back = fragment.getDeclaredMethod("onBackPressed"), owner = fragment.getMethod("getActivity");
            if (back.getReturnType() != boolean.class) throw new IllegalStateException();
            module.hook(back).setId("new-soul/ai-automatic-card-back")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        try {
                            Object activity = owner.invoke(chain.getThisObject());
                            if (activity instanceof Activity && closeForBack((Activity) activity)) return true;
                        } catch (Throwable ignored) { }
                        return chain.proceed();
                    });
            backReady = true;
        } catch (Throwable ignored) { /* Android 13 dispatcher remains available. */ }
        try {
            listenerInfo = View.class.getDeclaredField("mListenerInfo"); listenerInfo.setAccessible(true);
            clickListener = listenerInfo.getType().getDeclaredField("mOnClickListener"); clickListener.setAccessible(true);
            Class<?> row = loader.loadClass("cn.soulapp.android.component.chat.widget.RowAIGCGuide");
            Class<?> holder = loader.loadClass("cn.soulapp.android.component.chat.widget.RowAIGCGuide$ViewHolder");
            Class<?> message = loader.loadClass("cn.soulapp.imlib.msg.ImMessage");
            Method bind = row.getDeclaredMethod("r", holder, message, int.class, List.class);
            Field item = holder.getField("itemView");
            if (bind.getReturnType() != void.class || !View.class.isAssignableFrom(item.getType()))
                throw new IllegalStateException();
            module.hook(bind).setId("new-soul/ai-automatic-card-row-ownership")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        View view = null;
                        try {
                            Object value = item.get(chain.getArg(0));
                            if (value instanceof View) {
                                view = (View) value;
                                beforeBind(view);
                                restoreTree(view);
                            }
                        } catch (Throwable ignored) { }
                        Object result = chain.proceed();
                        try { if (view != null) afterBind(view, chain.getArg(1)); }
                        catch (Throwable ignored) { }
                        return result;
                    });
            module.deoptimize(bind);
            rowBindingObserved = true;
        } catch (Throwable ignored) { /* Unverified recycler ownership means no row is hidden. */ }
        module.log(android.util.Log.INFO, "SoulHook102", "AI automatic card ready: hostBack=" + backReady
                + "; rowOwnership=" + rowBindingObserved);
    }

    static AiAutomaticSuggestionCard show(Activity activity, AiChatContext.Snapshot snapshot,
            AiReplyRequest.Output output, Actions actions) {
        return show(activity, snapshot, output, actions, null);
    }

    static AiAutomaticSuggestionCard show(Activity activity, AiChatContext.Snapshot snapshot,
            AiReplyRequest.Output output, Actions actions, Pending pending) {
        if (Looper.myLooper() != Looper.getMainLooper()) { diagnostic(Stage.THREAD, null); return null; }
        if (activity == null || snapshot == null || output == null || output.result == null || actions == null) {
            diagnostic(Stage.INPUT, null); return null;
        }
        if (output.result.candidates.isEmpty()
                || "WAIT".equals(output.result.action) || "STOP".equals(output.result.action)
                || !output.result.questions.isEmpty()) { diagnostic(Stage.RESULT, null); return null; }
        Trace trace = new Trace();
        try {
            trace.stage = Stage.IDENTITY;
            if (!AiChatContext.sameVisibleConversation(activity, snapshot)) { diagnostic(trace.stage, null); return null; }
            trace.stage = Stage.COMPOSER;
            View composer = AiReplyUi.composer(activity);
            if (composer == null) { diagnostic(trace.stage, null); return null; }
            trace.stage = Stage.INPUT_BAR;
            LinearLayout bar = inputBar(composer);
            if (bar == null) { diagnostic(trace.stage, null); diagnosticParents(composer); return null; }
            trace.stage = Stage.MEDIA_MENU;
            View menu = ancestor(bar, MENU);
            if (menu == null) { diagnostic(trace.stage, null); diagnosticParents(bar); return null; }
            trace.stage = Stage.ATTACHED;
            if (!bar.isAttachedToWindow()) { diagnostic(trace.stage, null); return null; }
            trace.stage = Stage.KEYBOARD;
            if (keyboard(activity, menu)) { diagnostic(trace.stage, null); return null; }
            closeFor(activity);
            AiAutomaticSuggestionCard card = new AiAutomaticSuggestionCard(activity, snapshot, output, actions, bar, composer, menu, trace);
            try {
                card.suppression = pending == null ? null : pending.take(activity, snapshot);
                if (card.suppression == null) {
                    Pending acquired = suppressOfficial(activity);
                    if (acquired != null) card.suppression = acquired.take(activity, snapshot);
                }
                if (card.suppression == null) { diagnostic(Stage.SUPPRESS_FAILURE, null); return null; }
                card.suppression.card = new WeakReference<>(card);
                ACTIVE.put(activity, new WeakReference<>(card));
                trace.stage = Stage.LISTENERS;
                card.root.addOnAttachStateChangeListener(card.attachment);
                card.observer = activity.getWindow().getDecorView().getViewTreeObserver();
                card.observer.addOnGlobalLayoutListener(card.layout);
                trace.stage = Stage.INSERT;
                card.suppression.refresh();
                card.mount();
                // This is a native chat-list/inline slot, not a modal surface. Back belongs to the host.
                diagnostic(card.mountedSlot == null ? Stage.SLOT_WAIT : Stage.SHOWN, null);
                MAIN.postDelayed(() -> {
                    if (!card.closed && card.mountedSlot != null) diagnostic(card.root.isShown() && card.root.getWidth() > 0 && card.root.getHeight() > 0
                            ? Stage.MEASURED : Stage.NOT_MEASURED, null);
                }, 900L);
                return card;
            } catch (Throwable error) { diagnostic(trace.stage, error); card.release(false, false); return null; }
        } catch (Exception | LinkageError error) { diagnostic(trace.stage, error); return null; }
    }

    private AiAutomaticSuggestionCard(Activity owner, AiChatContext.Snapshot snapshot, AiReplyRequest.Output output,
            Actions actions, LinearLayout bar, View composer, View menu, Trace trace) throws Exception {
        this.activity = new WeakReference<>(owner); this.snapshot = snapshot; this.actions = actions;
        this.bar = bar; this.composer = composer; this.mediaMenu = menu;
        trace.stage = Stage.INFLATER;
        NativeInflater inflater = new NativeInflater(owner);
        trace.stage = Stage.ROOT_XML;
        root = inflate(inflater, owner, "c_ct_layout_row_aigc_item", bar);
        trace.stage = Stage.ROOT_CHILDREN;
        View normal = require(root, owner, "main"), content = require(root, owner, "c_ct_content_container");
        View title = require(root, owner, "tvTitle");
        refresh = require(root, owner, "tv_refresh"); icon = require(root, owner, "iv_icon");
        View close = require(root, owner, "iv_close");
        if (!(content instanceof LinearLayout) || !(title instanceof TextView) || !(icon instanceof ImageView))
            throw new IllegalStateException();
        // XML has ordinary/night assets. Mirror the host's read-only new-style switch as well,
        // including first-match conversations where no RowAIGCGuide exists to copy yet.
        trace.stage = Stage.NATIVE_ASSETS;
        boolean newStyle = newHostStyle(owner), dark = BottomSettingsEntry.dark(owner);
        Drawable nativeIcon = drawable(owner, newStyle ? (dark ? "c_ct_input_menu_chat_aigc_new_night"
                : "c_ct_input_menu_chat_aigc_new") : "c_ct_input_menu_chat_aigc");
        Drawable nativeRefresh = drawable(owner, newStyle ? (dark ? "c_ct_aigc_row_refresh_night_new"
                : "c_ct_aigc_row_refresh_new") : "c_ct_aigc_row_refresh");
        if (nativeIcon == null || nativeRefresh == null || !(refresh instanceof TextView)) throw new IllegalStateException();
        ((ImageView) icon).setImageDrawable(nativeIcon);
        ((TextView) refresh).setCompoundDrawablesRelativeWithIntrinsicBounds(nativeRefresh, null, null, null);
        ((TextView) title).setTextColor(color(owner, newStyle ? "color_aigc_multi_title_new" : "color_aigc_multi_title",
                ((TextView) title).getCurrentTextColor()));
        trace.stage = Stage.PRESENTATION;
        View official = officialRow(owner);
        if (official != null) copyPresentation(owner, official, root);
        markIcon = !isHostBadge(icon);
        normal.setVisibility(View.VISIBLE);
        hide(root, owner, "postPanel"); hide(root, owner, "clEducation");
        ((TextView) title).setText(TITLE); title.setFocusable(false); title.setFocusableInTouchMode(false);
        title.setContentDescription(TITLE);
        icon.setContentDescription(TITLE);
        refresh.setContentDescription("换一组"); refresh.setFocusable(true);
        refresh.setOnClickListener(view -> {
            if (!busy && current()) { busy(true); try { actions.refresh(); } catch (Throwable ignored) { error("更新未完成，请再试一次"); } }
        });
        close.setContentDescription("关闭NewSoul回复推荐"); close.setFocusable(true);
        close.setOnClickListener(view -> release(true, false));
        LinearLayout rows = (LinearLayout) content; rows.removeAllViews();
        SettingsUi.MenuPanel presentation = new SettingsUi.MenuPanel(owner, dark);
        for (int i = 0; i < Math.min(2, output.result.candidates.size()); i++) {
            AiReplyRules.Candidate candidate = output.result.candidates.get(i);
            trace.stage = Stage.OPTIONS_XML;
            View row = inflate(inflater, owner, "c_ct_layout_row_aigc_item_opt", rows);
            trace.stage = Stage.OPTION_CHILDREN;
            View body = require(row, owner, "container"), target = require(row, owner, "aigcRootView");
            if (!(body instanceof LinearLayout)) throw new IllegalStateException();
            hide(row, owner, "divider"); hide(row, owner, "iv_description"); hide(row, owner, "tv_description");
            trace.stage = Stage.OPTION_TEXT;
            TextView text = new TextView(owner); text.setText(AiReplyComponents.displayText(presentation, candidate.text));
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            text.setTextColor(color(owner, "color_s_03", BottomSettingsEntry.dark(owner) ? 0xffb8b8cb : 0xff333333));
            text.setGravity(android.view.Gravity.START); text.setMaxLines(2); text.setEllipsize(TextUtils.TruncateAt.END);
            text.setContentDescription(candidate.text);
            ((LinearLayout) body).addView(text, new LinearLayout.LayoutParams(-1, -2));
            if (official != null) {
                View sample = find(official, owner, "aigcRootView");
                if (sample != null) copyBackground(sample, target);
                TextView sampleText = firstText(find(official, owner, "container"));
                if (sampleText != null) copyTextStyle(sampleText, text);
            }
            target.setFocusable(true); target.setOnClickListener(view -> {
                if (busy || !current()) return;
                if (complete(text)) choose(candidate.text); else fullText(candidate.text);
            });
            target.setOnLongClickListener(view -> { if (!busy && current()) fullText(candidate.text); return true; });
            choices.add(target);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            if (i > 0) params.topMargin = dp(owner, 8);
            rows.addView(row, params);
        }
        // Keep all native constraint anchors local; cloned IDs must never shadow host lookups.
        trace.stage = Stage.REMAP_IDS;
        nativeIds = remapIds(root); root.setTag(TAG);
        if (Build.VERSION.SDK_INT >= 28) root.setAccessibilityPaneTitle(TITLE);
        attachment = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { }
            @Override public void onViewDetachedFromWindow(View view) {
                // A recycled/offscreen chat row is not a user's dismissal. Keep this visit's result.
                if (!closed) diagnostic(Stage.DETACHED, null);
            }
        };
    }

    void busy(boolean value) {
        if (closed) return;
        busy = value; refresh.setEnabled(!value); refresh.setAlpha(value ? .5f : 1f);
        refresh.setContentDescription(value ? "正在准备回复" : "换一组");
        for (View choice : choices) choice.setEnabled(!value);
    }

    void error(String message) {
        if (closed || !current()) return;
        busy(false);
        Activity owner = activity.get();
        if (owner != null && message != null && !message.isEmpty()) Toast.makeText(owner, message, Toast.LENGTH_SHORT).show();
    }

    private void choose(String text) {
        if (busy || !current()) return;
        busy(true);
        try { actions.choose(text); } catch (Throwable ignored) { error("回复未提交，请重新点击候选"); }
    }
    private void fullText(String text) {
        Activity owner = activity.get();
        if (owner == null || fullTextDialog != null || !current()) return;
        SettingsUi.MenuPanel preview = new SettingsUi.MenuPanel(owner, BottomSettingsEntry.dark(owner));
        TextView body = SettingsUi.label(preview.context, text, 14, preview.foreground);
        preview.body.addView(body, new LinearLayout.LayoutParams(-1, -2));
        preview.show("回复全文", "发送"); fullTextDialog = preview.dialog;
        preview.dialog.setOnDismissListener(dialog -> { if (fullTextDialog == preview.dialog) fullTextDialog = null; });
        preview.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            preview.dialog.dismiss(); choose(text);
        });
    }
    private static boolean complete(TextView text) {
        android.text.Layout layout = text.getLayout();
        if (layout == null || layout.getLineCount() == 0) return false;
        for (int i = 0; i < layout.getLineCount(); i++) if (layout.getEllipsisCount(i) != 0) return false;
        return layout.getLineEnd(layout.getLineCount() - 1) == text.length();
    }

    @Override public void close() {
        if (Looper.myLooper() != Looper.getMainLooper()) { MAIN.post(this::close); return; }
        release(false, false);
    }
    static void closeFor(Activity activity) {
        if (Looper.myLooper() != Looper.getMainLooper()) { MAIN.post(() -> closeFor(activity)); return; }
        WeakReference<AiAutomaticSuggestionCard> ref = ACTIVE.get(activity);
        AiAutomaticSuggestionCard card = ref == null ? null : ref.get();
        if (card != null) card.close();
    }
    static boolean closeForBack(Activity activity) {
        if (Looper.myLooper() != Looper.getMainLooper()) return false;
        WeakReference<AiAutomaticSuggestionCard> ref = ACTIVE.get(activity);
        AiAutomaticSuggestionCard card = ref == null ? null : ref.get();
        return false; // Both native slots are inline; leaving chat is handled by the host and onPause.
    }

    private boolean current() {
        Activity owner = activity.get();
        if (closed || owner == null || owner.isFinishing() || owner.isDestroyed()) return false;
        try {
            if (AiChatContext.sameVisibleConversation(owner, snapshot)) return true;
        } catch (Exception ignored) { }
        diagnostic(Stage.DISMISS_IDENTITY, null);
        release(true, false); return false;
    }
    private void checkLayout() {
        if (closed) return;
        Activity owner = activity.get();
        if (owner == null) { diagnostic(Stage.DISMISS_PARENT, null); release(true, false); return; }
        if (!current()) return;
        if (suppression != null) suppression.refresh();
        mount();
        if (!owner.hasWindowFocus() || SystemClock.uptimeMillis() - openedAt < 800) return;
        try {
            int state = ((Number) mediaMenu.getClass().getMethod("getCurrentState").invoke(mediaMenu)).intValue();
            if (state == 3 || state == 6 || keyboard(owner, mediaMenu)) {
                diagnostic(Stage.DISMISS_MENU, null); release(true, false);
            }
        } catch (Exception ignored) { }
    }
    private void release(boolean notify, boolean detached) {
        if (closed) return;
        diagnostic(Stage.CLOSED, null);
        closed = true; root.removeOnAttachStateChangeListener(attachment);
        if (fullTextDialog != null) { fullTextDialog.dismiss(); fullTextDialog = null; }
        if (observer != null && observer.isAlive()) observer.removeOnGlobalLayoutListener(layout);
        observer = null;
        if (unregisterBack != null) { try { unregisterBack.run(); } catch (Throwable ignored) { } unregisterBack = null; }
        Runnable finish = () -> {
            Activity owner = activity.get();
            WeakReference<AiAutomaticSuggestionCard> ref = owner == null ? null : ACTIVE.get(owner);
            if (ref != null && ref.get() == this) ACTIVE.remove(owner);
            unmount();
            PluginActionMark.set(icon, false);
            if (suppression != null) { suppression.close(); suppression = null; }
            if (notify) { try { actions.close(); } catch (Throwable ignored) { } }
        };
        if (detached) MAIN.post(finish); else finish.run();
    }

    /** Transferable owner: closing an already consumed pending lease cannot restore the active card's slot. */
    static final class Pending implements AutoCloseable {
        private Suppression value;
        private Pending(Suppression value) { this.value = value; }
        private Suppression take(Activity owner, AiChatContext.Snapshot snapshot) {
            Suppression result = value;
            if (result == null || !result.matches(owner, snapshot)) return null;
            value = null;
            return result;
        }
        @Override public void close() {
            if (Looper.myLooper() != Looper.getMainLooper()) { MAIN.post(this::close); return; }
            Suppression result = value; value = null;
            if (result != null) result.close();
        }
    }

    static Pending suppressOfficial(Activity owner) {
        if (Looper.myLooper() != Looper.getMainLooper() || owner == null
                || !AiOfficialSuggestionTrigger.sameVisit(owner, null) || !suppressionEnabled(owner)) return null;
        Suppression created = null;
        try {
            WeakReference<Suppression> previous = SUPPRESSIONS.get(owner);
            Suppression existing = previous == null ? null : previous.get();
            if (existing != null && !existing.closed) return null;
            created = new Suppression(owner, AiChatContext.currentIdentity(owner));
            SUPPRESSIONS.put(owner, new WeakReference<>(created));
            created.start(); created.refresh();
            diagnostic(Stage.SUPPRESS_START, null);
            return new Pending(created);
        } catch (Exception | LinkageError error) {
            diagnostic(Stage.SUPPRESS_FAILURE, error);
            if (created != null) created.close();
            return null;
        }
    }

    /** All changes are owned by exact Views, parents and host bind callbacks, never by Recycler positions. */
    private static final class Suppression implements AutoCloseable {
        final WeakReference<Activity> activity;
        final AiChatContext.Identity identity;
        final boolean openingPlacement;
        final List<Cover> covers = new ArrayList<>();
        final ViewTreeObserver.OnPreDrawListener preDraw = () -> !refresh();
        ViewTreeObserver observer;
        View officialOpening;
        ViewParent openingParent;
        ViewGroup.LayoutParams openingParams;
        int openingHeight;
        boolean closed, refreshing;
        WeakReference<AiAutomaticSuggestionCard> card;
        Suppression(Activity owner, AiChatContext.Identity identity) {
            activity = new WeakReference<>(owner); this.identity = identity;
            openingPlacement = AiOfficialSuggestionTrigger.openingPlacement(owner);
        }
        void start() {
            Activity owner = activity.get();
            observer = owner.getWindow().getDecorView().getViewTreeObserver();
            observer.addOnPreDrawListener(preDraw);
        }
        boolean matches(Activity owner, AiChatContext.Snapshot snapshot) {
            if (closed || owner == null || activity.get() != owner || owner.isFinishing() || owner.isDestroyed()
                    || !AiOfficialSuggestionTrigger.sameVisit(owner, snapshot) || !suppressionEnabled(owner)) return false;
            try {
                AiChatContext.Identity now = AiChatContext.currentIdentity(owner);
                return identity.same(now) && identity.conversation == now.conversation;
            } catch (Exception ignored) { return false; }
        }
        boolean refresh() {
            if (closed || refreshing) return false;
            Activity owner = activity.get();
            if (!matches(owner, null)) {
                AiAutomaticSuggestionCard shown = card == null ? null : card.get();
                if (shown != null && !shown.closed) shown.release(true, false); else close();
                return false;
            }
            refreshing = true;
            boolean changed = false;
            try {
                for (int index = covers.size() - 1; index >= 0; index--) {
                    Cover cover = covers.get(index);
                    if (!cover.owned()) { cover.restore(); covers.remove(index); }
                }
                if (openingPlacement) changed = hideOpening(owner);
                else {
                    View row = officialRow(owner);
                    if (row != null && verifiedRow(row, identity)) changed = hideRow(owner, row);
                }
                AiAutomaticSuggestionCard shown = card == null ? null : card.get();
                if (shown != null && !shown.closed) changed |= shown.mount();
            } catch (Exception | LinkageError error) { diagnostic(Stage.SUPPRESS_FAILURE, error); }
            finally { refreshing = false; }
            return changed;
        }
        private boolean hideOpening(Activity owner) {
            View composer = AiReplyUi.composer(owner);
            View bar = inputBar(composer);
            View opening = find(bar, owner, "cv_aigc_replay2");
            if (opening == null || !(opening.getParent() instanceof RelativeLayout)
                    || opening.getLayoutParams() == null || opening.getVisibility() != View.VISIBLE
                    || find(opening, owner, "c_ct_aigc_success") == null) return false;
            if (officialOpening == opening && opening.getParent() == openingParent
                    && opening.getLayoutParams() == openingParams && openingParams.height == 0) return false;
            restoreOpening();
            officialOpening = opening; openingParent = opening.getParent();
            openingParams = opening.getLayoutParams(); openingHeight = openingParams.height;
            openingParams.height = 0; opening.setLayoutParams(openingParams);
            return true;
        }
        private boolean hideRow(Activity owner, View row) {
            if (!rowBindingObserved || !verifiedRow(row, identity)) return false;
            View close = find(row, owner, "iv_close"), refresh = find(row, owner, "tv_refresh");
            Object closeAction = listener(close), refreshAction = listener(refresh);
            if (closeAction == null || refreshAction == null) return false;
            boolean changed = false;
            for (String name : new String[]{"llContent", "iv_close"}) {
                View target = find(row, owner, name);
                if (target == null || target.getVisibility() != View.VISIBLE || COVERS.containsKey(target)) continue;
                Cover cover = new Cover(target, row, close, refresh, closeAction, refreshAction, false);
                COVERS.put(target, cover); covers.add(cover); target.setVisibility(View.GONE); changed = true;
            }
            View content = find(row, owner, "llContent");
            if (content != null && covers.contains(COVERS.get(content)) && covers.contains(COVERS.get(close))
                    && !COVERS.containsKey(row) && (row.getPaddingTop() != 0 || row.getPaddingBottom() != 0)) {
                Cover padding = new Cover(row, row, close, refresh, closeAction, refreshAction, true);
                COVERS.put(row, padding); covers.add(padding);
                row.setPadding(row.getPaddingLeft(), 0, row.getPaddingRight(), 0); changed = true;
            }
            return changed;
        }
        boolean ownsRow(View row) {
            Activity owner = activity.get();
            if (owner == null || !verifiedRow(row, identity)) return false;
            Cover content = COVERS.get(find(row, owner, "llContent")), close = COVERS.get(find(row, owner, "iv_close"));
            return content != null && close != null && covers.contains(content) && covers.contains(close)
                    && content.owned() && close.owned();
        }
        private void restoreOpening() {
            if (officialOpening != null && officialOpening.getParent() == openingParent
                    && officialOpening.getLayoutParams() == openingParams && openingParams.height == 0) {
                openingParams.height = openingHeight; officialOpening.setLayoutParams(openingParams);
            }
            officialOpening = null; openingParent = null; openingParams = null;
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            Activity owner = activity.get();
            WeakReference<Suppression> reference = owner == null ? null : SUPPRESSIONS.get(owner);
            if (reference != null && reference.get() == this) SUPPRESSIONS.remove(owner);
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(preDraw);
            observer = null;
            for (Cover cover : covers) cover.restore(); covers.clear();
            restoreOpening(); diagnostic(Stage.SUPPRESS_RELEASE, null);
        }
    }

    private boolean mount() {
        if (closed || mounting || suppression == null || !suppression.matches(activity.get(), snapshot)) return false;
        mounting = true;
        try {
            Activity owner = activity.get();
            ViewGroup slot;
            ViewGroup.LayoutParams params;
            int index = -1;
            if (suppression.openingPlacement) {
                View opening = suppression.officialOpening;
                if (opening == null || !(opening.getParent() instanceof RelativeLayout)
                        || opening.getVisibility() != View.VISIBLE) {
                    boolean mounted = mountedSlot != null; unmount(); return mounted;
                }
                slot = (RelativeLayout) opening.getParent();
                if (mountedSlot == slot && root.getParent() == slot) return false;
                index = slot.indexOfChild(opening);
                RelativeLayout.LayoutParams copy = new RelativeLayout.LayoutParams((RelativeLayout.LayoutParams) suppression.openingParams);
                copy.height = ViewGroup.LayoutParams.WRAP_CONTENT; params = copy;
            } else {
                View row = officialRow(owner);
                if (!(row instanceof ViewGroup) || !suppression.ownsRow(row)) return false;
                slot = (ViewGroup) row;
                if (mountedSlot == slot && root.getParent() == slot) return false;
                Class<?> type = owner.getClassLoader().loadClass(CONSTRAINT + "$LayoutParams");
                params = (ViewGroup.LayoutParams) type.getConstructor(int.class, int.class).newInstance(-1, -2);
                for (String anchor : new String[]{"startToStart", "endToEnd", "topToTop"}) type.getField(anchor).setInt(params, 0);
            }
            unmount();
            if (!suppression.openingPlacement) copyHeaderForMount(owner, slot);
            slot.addView(root, index, params); mountedSlot = slot;
            PluginActionMark.set(icon, markIcon);
            diagnostic(suppression.openingPlacement ? Stage.SLOT_OPENING : Stage.SLOT_ROW, null);
            return true;
        } catch (Exception | LinkageError error) { diagnostic(Stage.INSERT, error); return false; }
        finally { mounting = false; }
    }
    private void unmount() {
        ViewGroup previous = mountedSlot; mountedSlot = null;
        if (previous != null && root.getParent() == previous) previous.removeView(root);
        PluginActionMark.set(icon, false);
    }

    private static void beforeBind(View tree) {
        for (WeakReference<AiAutomaticSuggestionCard> reference : new ArrayList<>(ACTIVE.values())) {
            AiAutomaticSuggestionCard card = reference.get();
            if (card != null && card.mountedSlot != null && descendant(card.mountedSlot, tree)) {
                card.unmount(); diagnostic(Stage.SLOT_REBIND, null);
            }
        }
        clearBindings(tree);
    }
    private static void afterBind(View tree, Object message) {
        View row = find(tree, tree.getContext(), "cl_main");
        RowBinding binding = RowBinding.read(message, row);
        if (row != null && binding != null) ROW_BINDINGS.put(row, binding);
        for (WeakReference<Suppression> reference : new ArrayList<>(SUPPRESSIONS.values())) {
            Suppression suppression = reference.get(); if (suppression != null) suppression.refresh();
        }
    }
    private static void clearBindings(View tree) {
        ROW_BINDINGS.remove(tree);
        if (tree instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) tree).getChildCount(); i++) clearBindings(((ViewGroup) tree).getChildAt(i));
    }
    private static boolean descendant(View view, View ancestor) {
        for (int depth = 0; view != null && depth < 40; depth++) {
            if (view == ancestor) return true;
            ViewParent parent = view.getParent(); view = parent instanceof View ? (View) parent : null;
        }
        return false;
    }
    private static boolean verifiedRow(View row, AiChatContext.Identity identity) {
        RowBinding binding = ROW_BINDINGS.get(row);
        return binding != null && binding.matches(row, identity);
    }
    private static final class RowBinding {
        final String from, to;
        final WeakReference<Object> closeAction, refreshAction;
        RowBinding(String from, String to, Object closeAction, Object refreshAction) {
            this.from = from; this.to = to;
            this.closeAction = new WeakReference<>(closeAction); this.refreshAction = new WeakReference<>(refreshAction);
        }
        static RowBinding read(Object message, View row) {
            if (message == null || row == null) return null;
            try {
                if (!"cn.soulapp.imlib.msg.ImMessage".equals(message.getClass().getName())
                        || !"AIGC_GUIDE_MSG_ID".equals(call(message, "getMsgId"))
                        || !Integer.valueOf(1).equals(call(message, "getMsgType"))) return null;
                Object chat = call(message, "getChatMessage");
                if (chat == null || !Integer.valueOf(35).equals(call(chat, "getMsgType"))) return null;
                Object body = call(chat, "getMsgContent");
                if (body == null || !"cn.soulapp.imlib.msg.chat.JsonMsg".equals(body.getClass().getName())
                        || !"AIGC_GUIDE".equals(body.getClass().getField("messageType").get(body))) return null;
                Object from = call(message, "getFrom"), to = call(message, "getTo");
                Object close = listener(find(row, row.getContext(), "iv_close")), refresh = listener(find(row, row.getContext(), "tv_refresh"));
                return from instanceof String && to instanceof String && close != null && refresh != null
                        ? new RowBinding((String) from, (String) to, close, refresh) : null;
            } catch (Exception ignored) { return null; }
        }
        boolean matches(View row, AiChatContext.Identity identity) {
            try { return from.equals(identity.account) && to.equals(call(identity.conversation, "getToUserId"))
                    && closeAction.get() != null && refreshAction.get() != null
                    && closeAction.get() == listener(find(row, row.getContext(), "iv_close"))
                    && refreshAction.get() == listener(find(row, row.getContext(), "tv_refresh")); }
            catch (Exception ignored) { return false; }
        }
    }
    private static Object call(Object target, String method) throws Exception { return target.getClass().getMethod(method).invoke(target); }
    private static boolean suppressionEnabled(Activity owner) {
        return AiReplyConfig.enabled(owner) && AiReplyConfig.autoSuggestions(owner) && AiReplyConfig.hasSelectedKey(owner);
    }
    private static final class Cover {
        final View view, row, close, refresh;
        final ViewParent parent;
        final Object closeAction, refreshAction;
        final int visibility;
        final boolean padding;
        final int left, top, right, bottom;
        Cover(View view, View row, View close, View refresh, Object closeAction, Object refreshAction, boolean padding) {
            this.view = view; this.row = row; this.close = close; this.refresh = refresh; this.parent = view.getParent();
            this.closeAction = closeAction; this.refreshAction = refreshAction; visibility = view.getVisibility();
            this.padding = padding; left = view.getPaddingLeft(); top = view.getPaddingTop();
            right = view.getPaddingRight(); bottom = view.getPaddingBottom();
        }
        void restore() {
            if (COVERS.get(view) != this) return;
            COVERS.remove(view);
            if (view.getParent() != parent || listener(close) != closeAction || listener(refresh) != refreshAction) return;
            if (padding) {
                if (view.getPaddingLeft() == left && view.getPaddingTop() == 0
                        && view.getPaddingRight() == right && view.getPaddingBottom() == 0)
                    view.setPadding(left, top, right, bottom);
            } else if (view.getVisibility() == View.GONE) view.setVisibility(visibility);
        }
        boolean owned() {
            return COVERS.get(view) == this && view.getParent() == parent
                    && listener(close) == closeAction && listener(refresh) == refreshAction
                    && (padding ? view.getPaddingLeft() == left && view.getPaddingTop() == 0
                        && view.getPaddingRight() == right && view.getPaddingBottom() == 0
                        : view.getVisibility() == View.GONE);
        }
    }
    private static void restoreTree(View tree) {
        Cover cover = COVERS.get(tree); if (cover != null) cover.restore();
        if (tree instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) tree).getChildCount(); i++) restoreTree(((ViewGroup) tree).getChildAt(i));
    }
    private static Object listener(View view) {
        if (view == null || listenerInfo == null || clickListener == null) return null;
        try { Object info = listenerInfo.get(view); return info == null ? null : clickListener.get(info); }
        catch (Exception ignored) { return null; }
    }

    private static View officialRow(Activity owner) {
        View list = find(owner.getWindow().getDecorView(), owner, "list_conversation");
        return lastRow(list, owner);
    }
    private static View lastRow(View node, Activity owner) {
        if (node == null || !node.isShown()) return null;
        if (node.getId() == id(owner, "cl_main") && ROW_BINDINGS.containsKey(node) && find(node, owner, "c_ct_content_container") != null
                && find(node, owner, "tvTitle") != null && find(node, owner, "iv_close") != null) return node;
        if (node instanceof ViewGroup) for (int i = ((ViewGroup) node).getChildCount() - 1; i >= 0; i--) {
            View found = lastRow(((ViewGroup) node).getChildAt(i), owner); if (found != null) return found;
        }
        return null;
    }
    private static void copyPresentation(Activity owner, View source, View target) {
        for (String name : new String[]{"main", "iv_icon", "tvTitle", "tv_refresh", "iv_close"}) {
            View from = find(source, owner, name), to = find(target, owner, name);
            if (from == null || to == null) continue;
            copyBackground(from, to);
            if (from instanceof TextView && to instanceof TextView) copyTextStyle((TextView) from, (TextView) to);
            if (from instanceof ImageView && to instanceof ImageView) {
                copyImage((ImageView) from, (ImageView) to);
            }
            if ("iv_icon".equals(name) || "tvTitle".equals(name) || "tv_refresh".equals(name)) copyHeaderLayout(from, to, null);
        }
    }
    private void copyHeaderForMount(Activity owner, View source) {
        for (String name : new String[]{"main", "iv_icon", "tvTitle", "tv_refresh", "iv_close"}) {
            Integer local = nativeIds.get(id(owner, name));
            View from = find(source, owner, name), to = local == null ? null : root.findViewById(local);
            if (from == null || to == null) continue;
            copyBackground(from, to);
            if (from instanceof TextView && to instanceof TextView) copyTextStyle((TextView) from, (TextView) to);
            if (from instanceof ImageView && to instanceof ImageView) copyImage((ImageView) from, (ImageView) to);
            if ("iv_icon".equals(name) || "tvTitle".equals(name) || "tv_refresh".equals(name)) copyHeaderLayout(from, to, nativeIds);
        }
        markIcon = !isHostBadge(icon);
    }
    private static void copyImage(ImageView from, ImageView to) {
        Drawable drawable = cloneDrawable(from.getDrawable(), to.getContext());
        if (drawable != null) to.setImageDrawable(drawable);
        to.setImageTintList(from.getImageTintList()); to.setScaleType(from.getScaleType());
        to.setAdjustViewBounds(from.getAdjustViewBounds()); to.setCropToPadding(from.getCropToPadding());
        to.setPadding(from.getPaddingLeft(), from.getPaddingTop(), from.getPaddingRight(), from.getPaddingBottom());
    }
    private static void copyHeaderLayout(View from, View to, Map<Integer, Integer> ids) {
        ViewGroup.LayoutParams original = from.getLayoutParams();
        if (original == null || to.getLayoutParams() == null || original.getClass() != to.getLayoutParams().getClass()) return;
        try {
            // ConstraintLayout's copy constructor shares its mutable solver widget. Copy layout values only.
            ViewGroup.LayoutParams copy = (ViewGroup.LayoutParams) original.getClass().getConstructor(int.class, int.class)
                    .newInstance(original.width, original.height);
            for (Field field : original.getClass().getFields()) {
                int modifiers = field.getModifiers();
                if (java.lang.reflect.Modifier.isStatic(modifiers) || java.lang.reflect.Modifier.isFinal(modifiers)) continue;
                if (field.getType().isPrimitive() || "dimensionRatio".equals(field.getName())) field.set(copy, field.get(original));
            }
            if (original instanceof ViewGroup.MarginLayoutParams && copy instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) original;
                ((ViewGroup.MarginLayoutParams) copy).setMarginStart(margins.getMarginStart());
                ((ViewGroup.MarginLayoutParams) copy).setMarginEnd(margins.getMarginEnd());
            }
            if (ids != null) remapParams(copy, ids);
            to.setLayoutParams(copy);
        } catch (Exception error) { diagnostic(Stage.PRESENTATION, error); }
    }
    private static boolean isHostBadge(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        // The verified host branch uses 50x20dp for last-hot-chat, and 18x18dp for the AI icon.
        return params != null && params.width == dp(view.getContext(), 50) && params.height == dp(view.getContext(), 20);
    }
    private static void copyTextStyle(TextView from, TextView to) {
        to.setTextColor(from.getTextColors()); to.setTypeface(from.getTypeface());
        to.setTextSize(TypedValue.COMPLEX_UNIT_PX, from.getTextSize());
        Drawable[] icons = from.getCompoundDrawablesRelative();
        to.setCompoundDrawablesRelativeWithIntrinsicBounds(cloneDrawable(icons[0], to.getContext()), cloneDrawable(icons[1], to.getContext()),
                cloneDrawable(icons[2], to.getContext()), cloneDrawable(icons[3], to.getContext()));
        to.setCompoundDrawablePadding(from.getCompoundDrawablePadding());
    }
    private static void copyBackground(View from, View to) {
        Drawable drawable = cloneDrawable(from.getBackground(), to.getContext());
        if (drawable != null) UiAppearance.bind(to, drawable);
    }
    private static Drawable cloneDrawable(Drawable original, Context context) {
        return original == null || original.getConstantState() == null ? null
                : original.getConstantState().newDrawable(context.getResources(), context.getTheme()).mutate();
    }
    private static TextView firstText(View node) {
        if (node instanceof TextView) return (TextView) node;
        if (node instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) node).getChildCount(); i++) {
            TextView text = firstText(((ViewGroup) node).getChildAt(i)); if (text != null) return text;
        }
        return null;
    }
    private static View inflate(NativeInflater inflater, Activity owner, String name, ViewGroup parent) throws Exception {
        int resource = owner.getResources().getIdentifier(name, "layout", owner.getPackageName());
        if (resource == 0) throw new IllegalStateException();
        try (XmlResourceParser xml = owner.getResources().getLayout(resource)) {
            int count = 0;
            for (int event = xml.getEventType(); event != XmlPullParser.END_DOCUMENT; event = xml.next())
                if (event == XmlPullParser.START_TAG && (++count > 64 || !NativeInflater.allowed(xml.getName())
                        || xml.getAttributeValue("http://schemas.android.com/apk/res/android", "onClick") != null)) throw new IllegalStateException();
        }
        return inflater.inflate(resource, parent, false);
    }
    private static Map<Integer, Integer> remapIds(View root) throws Exception {
        List<View> nodes = new ArrayList<>(); flatten(root, nodes); Map<Integer, Integer> ids = new HashMap<>();
        for (View view : nodes) if (view.getId() != View.NO_ID) ids.putIfAbsent(view.getId(), View.generateViewId());
        for (View view : nodes) {
            if (view.getId() != View.NO_ID) view.setId(ids.get(view.getId()));
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params == null || !params.getClass().getName().startsWith(CONSTRAINT)) continue;
            remapParams(params, ids);
            view.setLayoutParams(params);
        }
        return ids;
    }
    private static void remapParams(ViewGroup.LayoutParams params, Map<Integer, Integer> ids) throws Exception {
        for (Field field : params.getClass().getFields()) if (field.getType() == int.class && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
            Integer replacement = ids.get(field.getInt(params)); if (replacement != null) field.setInt(params, replacement);
        }
    }
    private static void flatten(View view, List<View> nodes) {
        nodes.add(view); if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) flatten(((ViewGroup) view).getChildAt(i), nodes);
    }
    private static final class NativeInflater extends LayoutInflater {
        NativeInflater(Context context) { super(context); setFactory2(new Factory2() {
            public View onCreateView(View parent, String name, Context context, AttributeSet attrs) { return create(name, context, attrs); }
            public View onCreateView(String name, Context context, AttributeSet attrs) { return create(name, context, attrs); }
        }); }
        @Override public LayoutInflater cloneInContext(Context context) { return new NativeInflater(context); }
        static boolean allowed(String name) {
            return CONSTRAINT.equals(name) || "View".equals(name) || "FrameLayout".equals(name) || "LinearLayout".equals(name)
                    || "TextView".equals(name) || "ImageView".equals(name) || "androidx.appcompat.widget.AppCompatTextView".equals(name)
                    || "androidx.appcompat.widget.AppCompatImageView".equals(name) || "cn.soul.android.widget.image.MateImageView".equals(name)
                    || "cn.soulapp.android.component.view.AIGuidePostPanel".equals(name);
        }
        private View create(String name, Context context, AttributeSet attrs) {
            if (!allowed(name)) throw new IllegalArgumentException();
            // Business/network widgets are never constructed, even when present but GONE in XML.
            if (name.endsWith("AIGuidePostPanel")) return new View(context, attrs);
            if (name.endsWith("MateImageView")) return new ImageView(context, attrs);
            if (CONSTRAINT.equals(name) || name.startsWith("androidx.appcompat.widget.")) {
                try { return (View) Class.forName(name, true, context.getClassLoader()).getConstructor(Context.class, AttributeSet.class).newInstance(context, attrs); }
                catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
            }
            if ("FrameLayout".equals(name)) return new FrameLayout(context, attrs);
            if ("LinearLayout".equals(name)) return new LinearLayout(context, attrs);
            if ("TextView".equals(name)) return new TextView(context, attrs);
            if ("ImageView".equals(name)) return new ImageView(context, attrs);
            return new View(context, attrs);
        }
    }
    /** Only fixed stages, runtime class names and code locations; never exception messages or view content. */
    private static void diagnostic(Stage stage, Throwable error) {
        StringBuilder value = new StringBuilder("AI auto card stage=").append(stage.name());
        for (int depth = 0; error != null && depth < 4; depth++, error = error.getCause()) {
            value.append(" class=").append(error.getClass().getName());
            StackTraceElement[] frames = error.getStackTrace();
            for (int index = 0; index < Math.min(3, frames.length); index++) {
                StackTraceElement frame = frames[index];
                value.append(" at=").append(frame.getClassName()).append('.').append(frame.getMethodName())
                        .append(':').append(frame.getLineNumber());
            }
        }
        android.util.Log.i("SoulHook102", value.toString());
    }
    private static void diagnosticParents(View view) {
        StringBuilder value = new StringBuilder("AI auto card stage=PARENTS");
        for (int depth = 0; depth < 25 && view != null; depth++) {
            value.append(" class=").append(view.getClass().getName());
            ViewParent parent = view.getParent(); view = parent instanceof View ? (View) parent : null;
        }
        android.util.Log.i("SoulHook102", value.toString());
    }
    private static LinearLayout inputBar(View view) { View bar = ancestor(view, INPUT_BAR); return bar instanceof LinearLayout && ((LinearLayout) bar).getOrientation() == LinearLayout.VERTICAL ? (LinearLayout) bar : null; }
    private static View ancestor(View view, String name) { for (int i = 0; i < 25 && view != null; i++) { if (name.equals(view.getClass().getName())) return view; ViewParent p = view.getParent(); view = p instanceof View ? (View) p : null; } return null; }
    private static int id(Context context, String name) { return context.getResources().getIdentifier(name, "id", context.getPackageName()); }
    private static View find(View view, Context context, String name) { int id = id(context, name); return view == null || id == 0 ? null : view.findViewById(id); }
    private static View require(View root, Context context, String name) { View value = find(root, context, name); if (value == null) throw new IllegalStateException(); return value; }
    private static void hide(View root, Context context, String name) { View value = find(root, context, name); if (value != null) value.setVisibility(View.GONE); }
    private static int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private static int color(Context context, String name, int fallback) { int id = context.getResources().getIdentifier(name, "color", context.getPackageName()); return id == 0 ? fallback : context.getResources().getColor(id, context.getTheme()); }
    private static Drawable drawable(Context context, String name) {
        int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
        return id == 0 ? null : context.getResources().getDrawable(id, context.getTheme()).mutate();
    }
    private static boolean newHostStyle(Activity owner) {
        try {
            Class<?> type = owner.getClassLoader().loadClass("cn.soul.android.middle.platform.newui.UnifyUIStyleManager");
            return Boolean.TRUE.equals(type.getMethod("k").invoke(type.getField("a").get(null)));
        } catch (Exception ignored) { return false; }
    }
    private static boolean keyboard(Activity activity, View menu) {
        try { if (Build.VERSION.SDK_INT >= 30) return Insets30.visible(activity); return Boolean.TRUE.equals(menu.getClass().getMethod("getKeyboardShow").invoke(menu)); }
        catch (Exception ignored) { return false; }
    }
    private static final class Insets30 { static boolean visible(Activity owner) { android.view.WindowInsets insets = owner.getWindow().getDecorView().getRootWindowInsets(); return insets != null && insets.isVisible(android.view.WindowInsets.Type.ime()); } }
    private static final class Back33 {
        static Runnable register(Activity activity) {
            android.window.OnBackInvokedDispatcher dispatcher = activity.getOnBackInvokedDispatcher();
            android.window.OnBackInvokedCallback callback = () -> closeForBack(activity);
            dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            return () -> dispatcher.unregisterOnBackInvokedCallback(callback);
        }
    }
}
