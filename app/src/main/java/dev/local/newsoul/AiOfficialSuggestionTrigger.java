package dev.local.newsoul;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Observes natural host passive opening/relationship decisions; never invokes official AI requests. */
final class AiOfficialSuggestionTrigger {
    private static final String CONVERSATION_ACTIVITY = "cn.soulapp.android.component.chat.ConversationActivity";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object LOCK = new Object();
    private static final AiOfficialSuggestionGate GATE = new AiOfficialSuggestionGate();
    private static final ThreadLocal<Origin> ENTERING = new ThreadLocal<>();
    private static volatile boolean installed;
    private static boolean attempted;
    private static Visit current;
    static boolean ready() { return installed; }

    private static final class Visit {
        final WeakReference<Activity> activity;
        final long token;
        Visit(Activity activity, long token) { this.activity = new WeakReference<>(activity); this.token = token; }
    }

    private static final class Origin {
        final WeakReference<Activity> activity;
        final WeakReference<Object> conversation;
        final String account, session, target;
        final Visit visit;
        Origin(Activity activity) throws Exception {
            this.activity = new WeakReference<>(activity);
            AiChatContext.Identity identity = AiChatContext.currentIdentity(activity);
            conversation = new WeakReference<>(identity.conversation);
            account = identity.account; session = identity.sessionId; target = identity.target;
            Visit active = visit();
            this.visit = active != null && active.activity.get() == activity ? active : null;
        }
    }

    static synchronized void install(XposedModule module, ClassLoader loader) {
        if (attempted) return;
        attempted = true;
        try {
            Class<?> manager = loader.loadClass("cn.soulapp.android.component.chat.aigc.AIGCMultiGuideManager");
            Class<?> conversation = loader.loadClass("cn.soulapp.imlib.Conversation");
            Class<?> meeting = loader.loadClass("cn.soulapp.android.component.chat.bean.MeetingBean");
            Class<?> compat = loader.loadClass("cn.soulapp.android.component.chat.conversation.b");
            Class<?> scene = loader.loadClass("cn.soulapp.android.component.chat.aigc.c");
            Class<?> assistScene = loader.loadClass("cn.soulapp.android.component.chat.aigc.c$a");
            Class<?> position = loader.loadClass("cn.soulapp.android.component.chat.aigc.AigcReqPosition");
            Class<?> fragment = loader.loadClass("cn.soulapp.android.component.chat.fragment.ConversationFragment");
            Class<?> prologue = loader.loadClass("cn.soulapp.android.component.chat.inputmenu.inputservice.ChatInputAIGCPrologueService");
            Class<?> menu = loader.loadClass("cn.soulapp.android.component.chat.inputmenu.SingleChatMediaMenu");
            Class<?> privilege = loader.loadClass("cn.soulapp.android.component.chat.bean.PrivilegeInfo");
            Method decision = manager.getDeclaredMethod("q0", conversation, String.class, boolean.class,
                    meeting, String.class, int.class, int.class, compat);
            Method positionOf = scene.getMethod("a");
            Method entering = HostBindings.officialSuggestionEntry(fragment);
            Method getActivity = fragment.getMethod("getActivity");
            Method opening = prologue.getDeclaredMethod("r", privilege, boolean.class, String.class, position);
            Field sourceMenu = prologue.getDeclaredField("a");
            sourceMenu.setAccessible(true);
            Method getFragment = menu.getMethod("getConversationFragment$cpnt_chat_release");
            Method getTarget = menu.getMethod("getToChatUserId");
            Method conversationTarget = conversation.getMethod("getToUserId");
            if (decision.getReturnType() != scene || Modifier.isStatic(decision.getModifiers())
                    || !Modifier.isPublic(decision.getModifiers()) || !scene.isAssignableFrom(assistScene)
                    || positionOf.getReturnType() != position || positionOf.getParameterCount() != 0
                    || entering.getReturnType() != void.class || !Modifier.isPrivate(entering.getModifiers())
                    || Modifier.isStatic(entering.getModifiers()) || !Activity.class.isAssignableFrom(getActivity.getReturnType())
                    || opening.getReturnType() != void.class || !Modifier.isPrivate(opening.getModifiers())
                    || Modifier.isStatic(opening.getModifiers()) || sourceMenu.getType() != menu
                    || getFragment.getReturnType() != fragment || getTarget.getReturnType() != String.class
                    || conversationTarget.getReturnType() != String.class)
                throw new IllegalStateException("Official suggestion signature differs");
            Object assistActive = position.getField("ASSIST_ACTIVE").get(null);
            Object passivePrologue = position.getField("PASSIVE_PROLOGUE").get(null);
            if (assistActive == null || !position.isInstance(assistActive)
                    || passivePrologue == null || !position.isInstance(passivePrologue))
                throw new IllegalStateException("Official suggestion scene unavailable");
            module.hook(entering).setId("new-soul/ai-official-entry-origin")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Origin previous = ENTERING.get(), source = null;
                        try {
                            Object owner = getActivity.invoke(chain.getThisObject());
                            if (owner instanceof Activity && usable((Activity) owner)) source = new Origin((Activity) owner);
                        } catch (Throwable ignored) { }
                        // Only retain provenance during the host's own synchronous profile-ready call.
                        ENTERING.set(source);
                        try { return chain.proceed(); }
                        finally { if (previous == null) ENTERING.remove(); else ENTERING.set(previous); }
                    });
            module.hook(decision).setId("new-soul/ai-official-suggestion-timing")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        // The host's own return determines permission; its behavior proceeds exactly once.
                        Origin source = ENTERING.get();
                        Object result = chain.proceed();
                        try {
                            if (source != null && result != null && assistScene.isInstance(result)
                                    && positionOf.invoke(result) == assistActive) {
                                Object cv = chain.getArg(0), target = chain.getArg(1);
                                if (conversation.isInstance(cv) && target instanceof String)
                                    signal(source, cv, (String) target, AiOfficialSuggestionGate.Placement.ROW);
                            }
                        } catch (Throwable ignored) { /* The original return value always wins. */ }
                        return result;
                    });
            module.hook(opening).setId("new-soul/ai-official-passive-opening")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Origin source = null;
                        Object cv = null;
                        String encryptedTarget = null;
                        try {
                            // r(..., true, null, PASSIVE_PROLOGUE) is the natural initial opening request.
                            // Refresh uses false; post, virtual-user and input-board scenes are excluded.
                            Object post = chain.getArg(2);
                            if (Boolean.TRUE.equals(chain.getArg(1)) && chain.getArg(3) == passivePrologue
                                    && (post == null || "".equals(post))) {
                                Object input = sourceMenu.get(chain.getThisObject());
                                Object ownerFragment = getFragment.invoke(input);
                                Object owner = ownerFragment == null ? null : getActivity.invoke(ownerFragment);
                                if (owner instanceof Activity && usable((Activity) owner)) {
                                    Activity activity = (Activity) owner;
                                    // Prevent a stale input service on the same Activity from authorizing its replacement.
                                    if (HostBindings.currentFragment(activity.getClassLoader()).invoke(activity) == ownerFragment) {
                                        AiChatContext.Identity identity = AiChatContext.currentIdentity(activity);
                                        Object target = getTarget.invoke(input);
                                        if (target instanceof String && !((String) target).isEmpty()
                                                && target.equals(conversationTarget.invoke(identity.conversation))) {
                                            source = new Origin(activity); cv = identity.conversation;
                                            encryptedTarget = identity.target;
                                        }
                                    }
                                }
                            }
                        } catch (Throwable ignored) { }
                        Object result = chain.proceed();
                        try { if (source != null) signal(source, cv, encryptedTarget, AiOfficialSuggestionGate.Placement.OPENING); }
                        catch (Throwable ignored) { }
                        return result;
                    });
            module.deoptimize(entering);
            module.deoptimize(decision);
            module.deoptimize(opening);
            installed = true;
            module.log(Log.INFO, "SoulHook102", "AI automatic timing observes official passive opening and relationship activation");
        } catch (Throwable error) {
            module.log(Log.WARN, "SoulHook102", "AI official timing unavailable; automatic suggestions remain disabled", error);
        }
    }

    /** Call from onResume before any automatic controller observation. Repeated calls are idempotent. */
    static void observe(Activity activity) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            WeakReference<Activity> ref = new WeakReference<>(activity);
            MAIN.post(() -> observe(ref.get())); return;
        }
        if (!installed || !usable(activity)) return;
        AiChatContext.Identity identity = null;
        try { identity = AiChatContext.currentIdentity(activity); } catch (Exception ignored) { }
        Visit entered;
        synchronized (LOCK) {
            if (current != null && current.activity.get() == activity) return;
            current = new Visit(activity, GATE.beginVisit(activity,
                    identity == null ? null : identity.conversation, identity == null ? null : identity.account,
                    identity == null ? null : identity.sessionId, identity == null ? null : identity.target,
                    SystemClock.elapsedRealtime()));
            entered = current;
        }
        dispatch(entered);
    }

    /** Synchronous invalidation also prevents a queued old signal from authorizing a later visit. */
    static void cancelFor(Activity activity) {
        synchronized (LOCK) {
            GATE.cancelOwner(activity);
            if (current != null && current.activity.get() == activity) {
                GATE.cancel(current.token); current = null;
            }
        }
    }

    static boolean eligible(Activity activity) { return eligible(activity, null); }

    /** Safe on a worker: currentIdentity reads host model objects, never Views or the database. */
    static boolean eligible(Activity activity, AiChatContext.Snapshot snapshot) {
        return matchesVisit(activity,snapshot,true);
    }

    /** Only for results already displayed from an eligible automatic request, or explicit actions on them. */
    static boolean sameVisit(Activity activity,AiChatContext.Snapshot snapshot) {
        return matchesVisit(activity,snapshot,false);
    }

    /** Only an authenticated passive-prologue source may use the host's input-area opening slot. */
    static boolean openingPlacement(Activity activity) {
        return matchesVisit(activity,null,false,AiOfficialSuggestionGate.Placement.OPENING);
    }

    /** Snapshot-bound variant for placing a completed result. */
    static boolean openingPlacement(Activity activity,AiChatContext.Snapshot snapshot) {
        return snapshot != null && matchesVisit(activity,snapshot,false,AiOfficialSuggestionGate.Placement.OPENING);
    }

    private static boolean matchesVisit(Activity activity,AiChatContext.Snapshot snapshot,boolean startWindow) {
        return matchesVisit(activity,snapshot,startWindow,null);
    }

    private static boolean matchesVisit(Activity activity,AiChatContext.Snapshot snapshot,boolean startWindow,
            AiOfficialSuggestionGate.Placement expectedPlacement) {
        if (!installed || !usable(activity)) return false;
        Visit entered = visit();
        if (entered == null || entered.activity.get() != activity) return false;
        try {
            AiChatContext.Identity identity = AiChatContext.currentIdentity(activity);
            if (snapshot != null && (!identity.account.equals(snapshot.account)
                    || !identity.sessionId.equals(snapshot.sessionId) || !identity.target.equals(snapshot.target))) return false;
            synchronized (LOCK) {
                long now = SystemClock.elapsedRealtime();
                return current == entered && (expectedPlacement == null || expectedPlacement == GATE.placement(
                        entered.token, identity.account, identity.sessionId, identity.target, now)) && (startWindow
                        ? GATE.allows(entered.token, identity.account, identity.sessionId, identity.target, now)
                        : GATE.sameVisit(entered.token, identity.account, identity.sessionId, identity.target, now));
            }
        } catch (Exception ignored) { return false; }
    }

    private static void signal(Origin source, Object conversation, String target, AiOfficialSuggestionGate.Placement placement) {
        if (!installed) return;
        Activity activity = source.activity.get();
        if (!usable(activity)) return;
        try {
            AiChatContext.Identity identity = AiChatContext.currentIdentity(activity);
            if (source.conversation.get() != conversation || identity.conversation != conversation
                    || !identity.target.equals(target) || !source.target.equals(identity.target)
                    || !source.account.equals(identity.account) || !source.session.equals(identity.sessionId)) return;
            synchronized (LOCK) {
                if (source.visit == null) {
                    GATE.stage(activity, conversation, identity.account, identity.sessionId, identity.target,
                            SystemClock.elapsedRealtime(), placement);
                    return;
                }
                if (current != source.visit || !GATE.issue(source.visit.token, identity.account, identity.sessionId,
                        identity.target, SystemClock.elapsedRealtime(), placement)) return;
            }
            dispatch(source.visit);
        } catch (Exception ignored) { /* Incomplete identity cannot grant permission. */ }
    }

    private static void dispatch(Visit entered) {
        MAIN.post(() -> {
            Activity activity = entered.activity.get();
            if (entered == visit() && eligible(activity)) AiAutomaticSuggestions.observe(activity);
        });
    }

    private static Visit visit() { synchronized (LOCK) { return current; } }
    private static boolean usable(Activity activity) {
        return activity != null && CONVERSATION_ACTIVITY.equals(activity.getClass().getName())
                && !activity.isFinishing() && !activity.isDestroyed();
    }
}
