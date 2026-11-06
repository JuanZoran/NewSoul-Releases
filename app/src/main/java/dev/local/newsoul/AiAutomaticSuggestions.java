package dev.local.newsoul;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

/** Official-timed suggestions; only an explicit candidate tap can enter the shared send path. */
final class AiAutomaticSuggestions {
    interface Callback {
        void ready(AiReplyRequest.Output output, AiChatContext.Snapshot verifiedSnapshot);
        void failed(String message);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORK = Executors.newFixedThreadPool(2);
    private static final Map<Activity, State> ACTIVE = new WeakHashMap<>();
    private static final Map<Activity, Boolean> DISMISSED = new WeakHashMap<>();
    private static final Map<Activity, AiAutomaticSuggestionCard.Pending> DISMISSED_PENDING = new WeakHashMap<>();
    private static final AiSuggestionCache CACHE = new AiSuggestionCache();
    private static final AtomicInteger TRACE_SEQUENCE = new AtomicInteger();
    private static final long[] READY_DELAYS = {900L, 1800L, 3500L};
    private static final String CHANGED = "聊天、输入框或设置已变化，请点击换一组重新生成";
    private static String lastAccount;

    // Fixed diagnostic vocabulary only: never log chat text, source IDs, credentials or exception messages.
    private enum Stage {
        OBSERVED, PENDING_ACQUIRED, PENDING_RELEASED, HOST_WAIT, COLLECT_START, COLLECT_READY, COLLECT_FAILED, SNAPSHOT_REJECTED,
        BEGIN, CACHE_START, CACHE_HIT, CACHE_SKIP, REQUEST_PREFLIGHT, REQUEST_CALL,
        REQUEST_VALIDATED, REQUEST_REBIND, REQUEST_POSTCHECK, REQUEST_ERROR, CACHE_STORED, REPLAY_PREFLIGHT,
        REPLAY_VALIDATED, REPLAY_ERROR, REPLAY_DISCARDED, PANEL_DELIVERED, CARD_SHOW,
        CARD_SHOWN, CARD_REJECTED, CARD_CLOSED, CARD_REFRESH, CARD_CHOOSE, ACTION_PREFLIGHT,
        MONITOR, VISIBLE_GUARD, DRAFT_CHANGED, CONFIG_CHANGED, CANCELLED, TIMEOUT, STOP
    }
    private enum Reason {
        NONE, NOT_LIVE, UNAVAILABLE, DRAFT, CONFIGURATION, PERMISSION, HISTORY_CHANGED,
        HISTORY_EXCEPTION, VISIBLE_CHANGED, VISIBLE_EXCEPTION, INTERACTION_STATE,
        NO_PACKET, NO_CANDIDATE, CARD_ATTACHMENT, OLD_DELIVERY, REQUEST_EXCEPTION, CACHE_KEY_CHANGED
    }
    private enum Failure { NONE, JSON_VALIDATION, CLIENT, STATE, TIMEOUT, REFLECTION, IO, OTHER }
    private enum Action {
        NONE, CLARIFY, SHARE, CONNECT, RELATE, FLIRT, REPAIR, BOUNDARY, INVITE, WECHAT,
        LOGISTICS, WAIT, STOP, OTHER
    }

    static void observe(Activity activity) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> observe(activity));
            return;
        }
        if (!available(activity) || !AiOfficialSuggestionTrigger.eligible(activity)
                || ACTIVE.containsKey(activity) || DISMISSED.containsKey(activity)) return;
        State state = new State(activity);
        ACTIVE.put(activity, state);
        state.pending = AiAutomaticSuggestionCard.suppressOfficial(activity);
        phase(state, Stage.OBSERVED);
        trace(state, Stage.PENDING_ACQUIRED, state.pending == null ? Reason.CARD_ATTACHMENT : Reason.NONE, Failure.NONE);
        MAIN.postDelayed(state.timeout, AiSuggestionCache.TTL_MS);
        scheduleReady(state, 0);
    }

    /** Called by the host pause/destroy hook and by panel close; no cancelled request is retried. */
    static void cancelFor(Activity activity) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> cancelFor(activity));
            return;
        }
        State state = ACTIVE.get(activity);
        if (state != null) { phase(state, Stage.CANCELLED); stop(state, null); }
    }

    /** Only a real host visit boundary resets a user's decision to close the automatic card. */
    static void endVisit(Activity activity) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> endVisit(activity));
            return;
        }
        closeDismissedPending(activity);
        cancelFor(activity);
        DISMISSED.remove(activity);
    }

    /** True means a cache replay or one automatic request is pending; callbacks run on MAIN. */
    static boolean onPanelReady(Activity activity, AiChatContext.Snapshot snapshot, Callback callback) {
        if (Looper.myLooper() != Looper.getMainLooper()) return false;
        State existing = ACTIVE.get(activity);
        // A user-confirmed send check owns this state until it consumes or cancels the card.
        // Returning false leaves the panel usable instead of attaching a callback that send would drop.
        if (DISMISSED.containsKey(activity) || existing != null && existing.checkingAction) return false;
        if (callback == null
                || !available(activity) || !emptyComposer(activity) || !eligible(snapshot)
                || !(AiOfficialSuggestionTrigger.eligible(activity, snapshot)
                    || existing != null && existing.firstPermitComplete
                        && AiOfficialSuggestionTrigger.sameVisit(activity, snapshot))) return false;
        try {
            Inputs inputs = inputs(activity, snapshot);
            State active = ACTIVE.get(activity);
            accountChanged(snapshot.account, active);
            if (active != null && !active.closed && active.inputs != null
                    && active.inputs.key.same(inputs.key)) {
                closePending(active);
                hideCard(active);
                active.callback = callback;
                active.panelSnapshot = snapshot;
                active.deliveryRevision++;
                if (active.packet != null && !active.requestInFlight) replay(active);
                return true;
            }
            if (active != null) stop(active, null);
            State state = new State(activity);
            state.callback = callback;
            state.panelSnapshot = snapshot;
            ACTIVE.put(activity, state);
            MAIN.postDelayed(state.timeout, AiSuggestionCache.TTL_MS);
            return begin(state, snapshot, inputs);
        } catch (Exception ignored) {
            State state = ACTIVE.get(activity);
            if (state != null && state.callback == callback) stop(state, null);
            return false;
        }
    }

    private static void scheduleReady(State state, int index) {
        if (!live(state)) return;
        long delay = index == 0 ? READY_DELAYS[0] : READY_DELAYS[index] - READY_DELAYS[index - 1];
        state.delayed = () -> checkReady(state, index);
        MAIN.postDelayed(state.delayed, delay);
    }

    private static void checkReady(State state, int index) {
        Activity activity = state.activity.get();
        if (!live(state) || !available(activity) || !AiOfficialSuggestionTrigger.eligible(activity)) {
            stop(state, null); return;
        }
        try {
            // Retry only an unready host surface. Collection and model failures never loop.
            AiChatContext.Identity identity = AiChatContext.currentIdentity(activity);
            if (AiReplyUi.composer(activity) == null) throw new IllegalStateException();
            if (!emptyComposer(activity)) { stop(state, null); return; }
            accountChanged(identity.account, state);
        } catch (Exception notReady) {
            trace(state, Stage.HOST_WAIT, Reason.UNAVAILABLE, failure(notReady));
            if (index + 1 < READY_DELAYS.length) scheduleReady(state, index + 1);
            else stop(state, null);
            return;
        }
        phase(state, Stage.COLLECT_START);
        state.collection = AiChatContext.collect(activity, new AiChatContext.Callback() {
            @Override public void onReady(AiChatContext.Snapshot snapshot) {
                if (!live(state)) return;
                state.collection = null;
                phase(state, Stage.COLLECT_READY);
                Activity current = state.activity.get();
                if (!available(current) || !emptyComposer(current) || !eligible(snapshot)
                        || !AiOfficialSuggestionTrigger.eligible(current, snapshot)) {
                    trace(state, Stage.SNAPSHOT_REJECTED,
                            !available(current) ? Reason.UNAVAILABLE : !emptyComposer(current) ? Reason.DRAFT
                            : !eligible(snapshot) ? Reason.INTERACTION_STATE : Reason.PERMISSION, Failure.NONE);
                    stop(state, null); return;
                }
                try { begin(state, snapshot, inputs(current, snapshot)); }
                catch (Exception error) {
                    trace(state, Stage.SNAPSHOT_REJECTED, Reason.REQUEST_EXCEPTION, failure(error));
                    stop(state, null);
                }
            }
            @Override public void onError(String message) {
                phase(state, Stage.COLLECT_FAILED); stop(state, null);
            }
        });
    }

    private static boolean begin(State state, AiChatContext.Snapshot snapshot, Inputs inputs) {
        phase(state, Stage.BEGIN);
        Activity activity = state.activity.get();
        if (!live(state) || !available(activity) || !emptyComposer(activity)
                || !AiOfficialSuggestionTrigger.eligible(activity, snapshot)) {
            stop(state, null); return false;
        }
        state.snapshot = snapshot;
        state.inputs = inputs;
        if (!visibleCurrent(state, snapshot)) { stop(state, null); return false; }
        accountChanged(snapshot.account, state);
        AiSuggestionCache.Decision decision = CACHE.acquire(inputs.key, SystemClock.elapsedRealtime());
        if (decision.status != AiSuggestionCache.Status.START && decision.status != AiSuggestionCache.Status.HIT) {
            phase(state, Stage.CACHE_SKIP);
            stop(state, null); return false;
        }
        state.lease = decision.lease;
        watch(state, activity);
        if (decision.status == AiSuggestionCache.Status.HIT) {
            phase(state, Stage.CACHE_HIT);
            state.packet = decision.cachedJson;
            replay(state);
            return true;
        }
        phase(state, Stage.CACHE_START);
        state.requestInFlight = true;
        WORK.execute(() -> request(state, false));
        return true;
    }

    private static void request(State state, boolean explicitRefresh) {
        try {
            final AiChatContext.Snapshot sourceSnapshot = state.snapshot;
            final Inputs sourceInputs = state.inputs;
            phase(state, Stage.REQUEST_PREFLIGHT);
            state.cancel.check();
            Activity activity = state.activity.get();
            if (!workerCurrent(state, activity, sourceSnapshot, Stage.REQUEST_PREFLIGHT))
                throw new IllegalStateException(CHANGED);
            state.cancel.check();
            FutureTask<Boolean> visible = new FutureTask<>(() -> visibleCurrent(state, sourceSnapshot));
            MAIN.post(visible);
            if (!visible.get(5L, TimeUnit.SECONDS)) throw new IllegalStateException(CHANGED);
            state.cancel.check();
            String apiKey = AiReplyConfig.readKey(state.context, sourceInputs.model);
            phase(state, Stage.REQUEST_CALL);
            AiReplyRequest.Output output = AiReplyRequest.run(sourceSnapshot, sourceInputs.profile, "", "",
                    AiWritingRules.Mode.REPLY, AiWritingRules.Style.DEFAULT, sourceInputs.images,
                    sourceInputs.model, apiKey, state.cancel);
            traceResult(state, Stage.REQUEST_VALIDATED, output);
            state.cancel.check();
            // Host-only memory rows can change during the request. Capture their current guard
            // first, then prove that the complete source history still matches on the worker.
            phase(state, Stage.REQUEST_REBIND);
            FutureTask<AiChatContext.Snapshot> reboundTask = new FutureTask<>(() -> {
                if (!visibleScopeCurrent(state, sourceSnapshot)) throw new IllegalStateException(CHANGED);
                return AiChatContext.rebindVisible(activity, sourceSnapshot);
            });
            MAIN.post(reboundTask);
            final AiChatContext.Snapshot rebound;
            try { rebound = reboundTask.get(5L, TimeUnit.SECONDS); }
            finally { reboundTask.cancel(false); }
            state.cancel.check();
            phase(state, Stage.REQUEST_POSTCHECK);
            if (!workerCurrent(state, activity, rebound, Stage.REQUEST_POSTCHECK))
                throw new IllegalStateException(CHANGED);
            String packet = output.packet();
            MAIN.post(() -> {
                if (!live(state)) { trace(state, Stage.REPLAY_DISCARDED, Reason.NOT_LIVE, Failure.NONE); return; }
                if (state.snapshot != sourceSnapshot || state.inputs != sourceInputs
                        || !visibleCurrent(state, rebound)) { stop(state, CHANGED); return; }
                final Inputs reboundInputs;
                try {
                    reboundInputs = inputs(activity, rebound);
                    if (!sourceInputs.configuration.equals(reboundInputs.configuration)
                            || !visibleCurrent(state, rebound)) { stop(state, CHANGED); return; }
                } catch (Exception error) {
                    trace(state, Stage.REQUEST_POSTCHECK, Reason.CONFIGURATION, failure(error));
                    stop(state, CHANGED); return;
                }
                boolean sameKey = sourceInputs.key.same(reboundInputs.key);
                boolean stored = sameKey && CACHE.complete(state.lease, packet, SystemClock.elapsedRealtime());
                // A new visible guard has no lease. Keep this validated packet only on the active
                // state, preserving the original cooldown without starting another cache attempt.
                if (!sameKey) CACHE.fail(state.lease, SystemClock.elapsedRealtime());
                trace(state, Stage.CACHE_STORED,
                        !sameKey ? Reason.CACHE_KEY_CHANGED : stored ? Reason.NONE : Reason.NO_PACKET, Failure.NONE);
                state.lease = null;
                state.snapshot = rebound;
                if (state.panelSnapshot != null && state.panelSnapshot.snapshotId.equals(sourceSnapshot.snapshotId))
                    state.panelSnapshot = rebound;
                state.inputs = reboundInputs;
                state.requestInFlight = false;
                state.packet = packet;
                replay(state);
            });
        } catch (Exception error) {
            trace(state, Stage.REQUEST_ERROR, Reason.REQUEST_EXCEPTION, failure(error));
            String message = error instanceof AiReplyClient.ClientException ? error.getMessage()
                    : error instanceof IllegalStateException && CHANGED.equals(error.getMessage()) ? CHANGED
                    : "自动建议未完成，可点击换一组重试";
            MAIN.post(() -> {
                if (!live(state)) return;
                state.requestInFlight = false;
                if (state.lease != null) {
                    CACHE.fail(state.lease, SystemClock.elapsedRealtime()); state.lease = null;
                }
                if (explicitRefresh && state.card != null && state.callback == null
                        && visibleCurrent(state, state.snapshot)) {
                    MAIN.removeCallbacks(state.timeout);
                    state.card.busy(false);
                    state.card.error(message);
                } else stop(state, message);
            });
        }
    }

    private static void replay(State state) {
        phase(state, Stage.REPLAY_PREFLIGHT);
        final int revision = ++state.deliveryRevision;
        final Callback callback = state.callback;
        final AiChatContext.Snapshot snapshot = callback == null ? state.snapshot : state.panelSnapshot;
        final String packet = state.packet;
        if (snapshot == null || packet == null) {
            trace(state, Stage.REPLAY_DISCARDED, Reason.NO_PACKET, Failure.NONE);
            stop(state, null); return;
        }
        WORK.execute(() -> {
            try {
                state.cancel.check();
                Activity activity = state.activity.get();
                if (!workerCurrent(state, activity, snapshot, Stage.REPLAY_PREFLIGHT))
                    throw new IllegalStateException(CHANGED);
                AiReplyRequest.Output output = AiReplyRequest.restore(packet, snapshot.snapshotId);
                traceResult(state, Stage.REPLAY_VALIDATED, output);
                state.cancel.check();
                MAIN.post(() -> {
                    if (!live(state) || revision != state.deliveryRevision || state.callback != callback) {
                        trace(state, Stage.REPLAY_DISCARDED, Reason.OLD_DELIVERY, Failure.NONE); return;
                    }
                    if (!visibleCurrent(state, snapshot)) { stop(state, CHANGED); return; }
                    if (callback != null) {
                        phase(state, Stage.PANEL_DELIVERED);
                        stop(state, null);
                        callback.ready(output, snapshot);
                    } else showCard(state, output);
                });
            } catch (Exception error) {
                trace(state, Stage.REPLAY_ERROR, Reason.REQUEST_EXCEPTION, failure(error));
                MAIN.post(() -> {
                    if (live(state) && revision == state.deliveryRevision)
                        stop(state, "自动建议已失效，请点击换一组重新生成");
                });
            }
        });
    }

    private static void showCard(State state, AiReplyRequest.Output output) {
        traceResult(state, Stage.CARD_SHOW, output);
        Activity activity = state.activity.get();
        if (!visibleCurrent(state, state.snapshot)) { stop(state, CHANGED); return; }
        hideCard(state);
        state.output = output;
        state.card = AiAutomaticSuggestionCard.show(activity, state.snapshot, output,
                new AiAutomaticSuggestionCard.Actions() {
                    @Override public void choose(String text) { chooseCandidate(state, text); }
                    @Override public void refresh() { refreshCard(state); }
                    @Override public void close() {
                        phase(state, Stage.CARD_CLOSED);
                        Activity owner = state.activity.get();
                        if (owner != null) DISMISSED.put(owner, true);
                        stop(state, null);
                        // The explicit close consumes the recommendation for this visit, without
                        // changing the host's separate 24-hour recommendation preference.
                        retainDismissedPending(owner, state.snapshot);
                    }
                }, state.pending);
        if (state.card == null) {
            boolean noCandidate = output == null || output.result == null || output.result.candidates.isEmpty()
                    || !output.result.questions.isEmpty() || "WAIT".equals(output.result.action)
                    || "STOP".equals(output.result.action);
            trace(state, Stage.CARD_REJECTED, noCandidate ? Reason.NO_CANDIDATE : Reason.CARD_ATTACHMENT, Failure.NONE);
            stop(state, null); return;
        }
        closePending(state); // A successful show consumed its suppression owner; close is now a no-op.
        phase(state, Stage.CARD_SHOWN);
        state.firstPermitComplete = true;
        MAIN.removeCallbacks(state.timeout);
        state.nextHistoryCheck = SystemClock.elapsedRealtime() + 5000L;
        MAIN.postDelayed(state.monitor, 1000L);
    }

    private static void hideCard(State state) {
        MAIN.removeCallbacks(state.monitor);
        AiAutomaticSuggestionCard card = state.card;
        state.card = null;
        if (card != null) card.close();
    }

    private static void closePending(State state) {
        AiAutomaticSuggestionCard.Pending pending = state.pending;
        state.pending = null;
        if (pending != null) {
            pending.close();
            trace(state, Stage.PENDING_RELEASED, Reason.NONE, Failure.NONE);
        }
    }

    private static void closeDismissedPending(Activity activity) {
        AiAutomaticSuggestionCard.Pending pending = DISMISSED_PENDING.remove(activity);
        if (pending != null) pending.close();
    }

    private static void retainDismissedPending(Activity activity, AiChatContext.Snapshot snapshot) {
        if (!available(activity) || !emptyComposer(activity)
                || !AiOfficialSuggestionTrigger.sameVisit(activity, snapshot)) return;
        try {
            // The card also reports host-surface invalidation through close; never keep hiding
            // a different visible source after such an invalidation.
            if (!AiChatContext.sameVisibleConversation(activity, snapshot)) return;
            closeDismissedPending(activity);
            AiAutomaticSuggestionCard.Pending pending = AiAutomaticSuggestionCard.suppressOfficial(activity);
            if (pending != null) DISMISSED_PENDING.put(activity, pending);
        } catch (Exception ignored) { }
    }

    /** An explicit refresh consumes the old cache but does not bypass any source/identity guard. */
    private static void refreshCard(State state) {
        if (!live(state) || state.card == null || state.requestInFlight || state.checkingAction) return;
        if (!visibleCurrent(state, state.snapshot)) { stop(state, CHANGED); return; }
        phase(state, Stage.CARD_REFRESH);
        state.deliveryRevision++;
        state.requestInFlight = true;
        MAIN.removeCallbacks(state.timeout);
        MAIN.postDelayed(state.timeout, AiSuggestionCache.TTL_MS);
        state.card.busy(true);
        // Do not replay the previous group after the user explicitly asked for a new one.
        CACHE.clearAccount(state.snapshot.account);
        AiSuggestionCache.Decision decision = CACHE.acquire(state.inputs.key, SystemClock.elapsedRealtime());
        state.lease = decision.status == AiSuggestionCache.Status.START ? decision.lease : null;
        WORK.execute(() -> request(state, true));
    }

    /** Candidate taps still pass through the same host sender and per-message checks as the panel. */
    private static void chooseCandidate(State state, String text) {
        if (!live(state) || state.card == null || state.requestInFlight || state.checkingAction
                || !candidate(state, text)) return;
        if (!visibleCurrent(state, state.snapshot)) { stop(state, CHANGED); return; }
        phase(state, Stage.CARD_CHOOSE);
        state.checkingAction = true;
        MAIN.removeCallbacks(state.timeout);
        MAIN.postDelayed(state.timeout, AiSuggestionCache.TTL_MS);
        state.card.busy(true);
        WORK.execute(() -> {
            try {
                state.cancel.check();
                Activity activity = state.activity.get();
                if (!workerCurrent(state, activity, state.snapshot, Stage.ACTION_PREFLIGHT))
                    throw new IllegalStateException(CHANGED);
                state.cancel.check();
                MAIN.post(() -> {
                    if (!live(state)) return;
                    Activity current = state.activity.get();
                    if (!visibleCurrent(state, state.snapshot)) { stop(state, CHANGED); return; }
                    AiChatContext.Snapshot snapshot = state.snapshot;
                    CACHE.clearAccount(snapshot.account);
                    stop(state, null);
                    WeakReference<Activity> owner = new WeakReference<>(current);
                    AiReplyUi.sendAutomaticSuggestion(current, snapshot, text, message -> {
                        Activity visible = owner.get();
                        if (visible != null && !visible.isFinishing() && !visible.isDestroyed()
                                && message != null && !message.isEmpty())
                            Toast.makeText(visible, message, Toast.LENGTH_LONG).show();
                    });
                });
            } catch (Exception ignored) {
                MAIN.post(() -> { if (live(state)) stop(state, CHANGED); });
            }
        });
    }

    private static boolean candidate(State state, String text) {
        if (text == null || text.isEmpty() || state.output == null) return false;
        for (AiReplyRules.Candidate candidate : state.output.result.candidates)
            if (text.equals(candidate.text)) return true;
        return false;
    }

    /** Bound visible cards to the same empty composer, configuration, visit and source messages. */
    private static void monitorCard(State state) {
        if (!live(state) || state.card == null) return;
        if (!visibleCurrent(state, state.snapshot)) { stop(state, null); return; }
        long now = SystemClock.elapsedRealtime();
        if (!state.historyCheckInFlight && now >= state.nextHistoryCheck) {
            state.historyCheckInFlight = true;
            state.nextHistoryCheck = now + 5000L;
            WORK.execute(() -> {
                boolean fresh = false;
                try {
                    Activity activity = state.activity.get();
                    fresh = !state.cancel.isCancelled()
                            && workerCurrent(state, activity, state.snapshot, Stage.MONITOR);
                } catch (Exception ignored) { }
                final boolean current = fresh;
                MAIN.post(() -> {
                    state.historyCheckInFlight = false;
                    if (live(state) && state.card != null && !current) stop(state, null);
                });
            });
        }
        MAIN.postDelayed(state.monitor, 1000L);
    }

    private static void watch(State state, Activity activity) {
        EditText composer = AiReplyUi.composer(activity);
        if (composer != null) {
            state.composer = new WeakReference<>(composer);
            state.watcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                    if (live(state)) { phase(state, Stage.DRAFT_CHANGED); stop(state, CHANGED); }
                }
                @Override public void afterTextChanged(Editable text) {}
            };
            composer.addTextChangedListener(state.watcher);
        }
        state.preferences = PluginPreferences.get(state.context);
        state.listener = (preferences, key) -> MAIN.post(() -> {
            if (live(state) && !configCurrent(state)) { phase(state, Stage.CONFIG_CHANGED); stop(state, CHANGED); }
        });
        state.preferences.registerOnSharedPreferenceChangeListener(state.listener);
    }

    private static boolean visibleCurrent(State state, AiChatContext.Snapshot snapshot) {
        if (!visibleScopeCurrent(state, snapshot)) return false;
        Activity activity = state.activity.get();
        try {
            if (AiChatContext.sameVisibleConversation(activity, snapshot)) return true;
            trace(state, Stage.VISIBLE_GUARD, Reason.VISIBLE_CHANGED, Failure.NONE);
        } catch (Exception error) {
            trace(state, Stage.VISIBLE_GUARD, Reason.VISIBLE_EXCEPTION, failure(error));
        }
        return false;
    }

    /** Scope-only guard for rebinding; complete history and the new visible guard still follow. */
    private static boolean visibleScopeCurrent(State state, AiChatContext.Snapshot snapshot) {
        Activity activity = state.activity.get();
        Reason reason = !live(state) ? Reason.NOT_LIVE : !available(activity) ? Reason.UNAVAILABLE
                : !emptyComposer(activity) ? Reason.DRAFT : !configCurrent(state) ? Reason.CONFIGURATION
                : !allowed(state, activity, snapshot) ? Reason.PERMISSION : Reason.NONE;
        if (reason != Reason.NONE) {
            trace(state, Stage.VISIBLE_GUARD, reason, Failure.NONE); return false;
        }
        return true;
    }

    private static boolean workerCurrent(State state, Activity activity,
            AiChatContext.Snapshot snapshot, Stage stage) throws Exception {
        Reason reason = activity == null ? Reason.NOT_LIVE : !configCurrent(state) ? Reason.CONFIGURATION
                : !allowed(state, activity, snapshot) ? Reason.PERMISSION : Reason.NONE;
        if (reason != Reason.NONE) { trace(state, stage, reason, Failure.NONE); return false; }
        try {
            if (AiChatContext.isCurrent(activity, snapshot)) return true;
            trace(state, stage, Reason.HISTORY_CHANGED, Failure.NONE);
            return false;
        } catch (Exception error) {
            trace(state, stage, Reason.HISTORY_EXCEPTION, failure(error));
            throw error;
        }
    }

    private static boolean configCurrent(State state) {
        try {
            return !state.closed && state.inputs != null && enabled(state.context)
                    && state.inputs.configuration.equals(AiReplyConfig.requestFingerprint(state.context));
        } catch (Exception ignored) { return false; }
    }

    private static boolean allowed(State state, Activity activity, AiChatContext.Snapshot snapshot) {
        return state.firstPermitComplete ? AiOfficialSuggestionTrigger.sameVisit(activity, snapshot)
                : AiOfficialSuggestionTrigger.eligible(activity, snapshot);
    }

    private static void accountChanged(String account, State keep) {
        if (lastAccount != null && !lastAccount.equals(account)) {
            CACHE.clearAccount(lastAccount);
            for (State state : new ArrayList<>(ACTIVE.values())) if (state != keep) stop(state, CHANGED);
        }
        lastAccount = account;
    }

    private static void stop(State state, String message) {
        if (state == null || state.closed) return;
        trace(state, Stage.STOP, Reason.NONE, Failure.NONE);
        state.closed = true;
        state.deliveryRevision++;
        hideCard(state);
        closePending(state);
        // A closed, consumed or invalidated recommendation must never reappear through a cache hit.
        if (state.snapshot != null) CACHE.clearAccount(state.snapshot.account);
        MAIN.removeCallbacks(state.timeout);
        if (state.delayed != null) MAIN.removeCallbacks(state.delayed);
        if (state.collection != null) { state.collection.cancel(); state.collection = null; }
        state.cancel.cancel();
        if (state.lease != null) { CACHE.fail(state.lease, SystemClock.elapsedRealtime()); state.lease = null; }
        EditText composer = state.composer == null ? null : state.composer.get();
        if (composer != null && state.watcher != null) composer.removeTextChangedListener(state.watcher);
        state.watcher = null;
        if (state.preferences != null && state.listener != null)
            state.preferences.unregisterOnSharedPreferenceChangeListener(state.listener);
        state.listener = null;
        state.preferences = null;
        Activity activity = state.activity.get();
        if (activity != null && ACTIVE.get(activity) == state) ACTIVE.remove(activity);
        Callback callback = state.callback;
        state.callback = null;
        state.packet = null;
        state.output = null;
        if (message != null && callback != null) callback.failed(message);
    }

    private static boolean live(State state) {
        Activity activity = state.activity.get();
        return !state.closed && activity != null && !activity.isFinishing() && !activity.isDestroyed()
                && ACTIVE.get(activity) == state;
    }

    private static boolean available(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed()
                && "cn.soulapp.android.component.chat.ConversationActivity".equals(activity.getClass().getName())
                && enabled(activity);
    }

    private static boolean enabled(Context context) {
        return AiReplyConfig.enabled(context) && AiReplyConfig.autoSuggestions(context)
                && AiReplyConfig.hasSelectedKey(context);
    }

    private static boolean emptyComposer(Activity activity) {
        EditText composer = activity == null ? null : AiReplyUi.composer(activity);
        return composer != null && composer.length() == 0;
    }

    private static boolean eligible(AiChatContext.Snapshot snapshot) {
        JSONObject history = snapshot == null ? null : snapshot.context.optJSONObject("history");
        JSONObject interaction = history == null ? null : history.optJSONObject("interaction");
        String state = interaction == null ? "" : interaction.optString("state");
        return "opening".equals(state) || "reply".equals(state) || "incoming_sticker".equals(state);
    }

    private static Inputs inputs(Activity activity, AiChatContext.Snapshot snapshot) throws Exception {
        String configuration = AiReplyConfig.requestFingerprint(activity);
        AiModelConfig model = AiReplyConfig.selectedModel(activity);
        String profile = AiMaterialRules.profileForRequest(
                PluginPreferences.get(activity).getString(AiReplyConfig.PROFILE, ""),
                Collections.emptyList(), AiWritingRules.Mode.REPLY);
        List<AiChatContext.Media> chosen = new ArrayList<>();
        if (AiImageSelectionRules.autoSelect(snapshot.images.size(), AiReplyConfig.autoImageThreshold(activity)))
            chosen.addAll(snapshot.images);
        JSONObject context = new JSONObject(snapshot.context.toString());
        context.remove("capturedAt");
        JSONArray images = new JSONArray();
        for (AiChatContext.Media image : chosen)
            images.put(new JSONObject().put("id", image.id).put("url", image.httpsUrl)
                    .put("label", image.label).put("sticker", image.sticker));
        String fingerprint = AiContextRules.digest(AiContextRules.canonical(new JSONObject().put("context", context)
                .put("images", images).put("profile", profile).put("configuration", configuration)
                .put("mode", "REPLY").put("style", "DEFAULT").put("intent", "").put("draft", "")));
        if (!configuration.equals(AiReplyConfig.requestFingerprint(activity)))
            throw new IllegalStateException(CHANGED);
        AiSuggestionCache.Key key = AiSuggestionCache.Key.of(snapshot.account, snapshot.sessionId,
                snapshot.target, snapshot.historyFingerprint, snapshot.cacheGuard, model.id, fingerprint);
        return new Inputs(configuration, model, profile, Collections.unmodifiableList(chosen), key);
    }

    private static final class Inputs {
        final String configuration, profile;
        final AiModelConfig model;
        final List<AiChatContext.Media> images;
        final AiSuggestionCache.Key key;
        Inputs(String configuration, AiModelConfig model, String profile,
                List<AiChatContext.Media> images, AiSuggestionCache.Key key) {
            this.configuration = configuration;
            this.model = model;
            this.profile = profile;
            this.images = images;
            this.key = key;
        }
    }

    private static final class State {
        final int traceId = TRACE_SEQUENCE.incrementAndGet();
        final WeakReference<Activity> activity;
        final Context context;
        final AiReplyClient.Cancellation cancel = new AiReplyClient.Cancellation();
        final Runnable timeout;
        final Runnable monitor;
        AiChatContext.Handle collection;
        AiChatContext.Snapshot snapshot, panelSnapshot;
        Inputs inputs;
        AiSuggestionCache.Lease lease;
        Callback callback;
        WeakReference<EditText> composer;
        TextWatcher watcher;
        SharedPreferences preferences;
        SharedPreferences.OnSharedPreferenceChangeListener listener;
        Runnable delayed;
        String packet;
        AiReplyRequest.Output output;
        AiAutomaticSuggestionCard card;
        AiAutomaticSuggestionCard.Pending pending;
        boolean requestInFlight, checkingAction, historyCheckInFlight;
        volatile boolean firstPermitComplete;
        long nextHistoryCheck;
        int deliveryRevision;
        volatile boolean closed;
        volatile Stage phase = Stage.OBSERVED;
        State(Activity activity) {
            this.activity = new WeakReference<>(activity);
            this.context = activity.getApplicationContext();
            this.timeout = () -> { phase(this, Stage.TIMEOUT); stop(this, "自动建议已超时，可点击换一组重试"); };
            this.monitor = () -> monitorCard(this);
        }
    }

    private static void phase(State state, Stage stage) {
        state.phase = stage;
        trace(state, stage, Reason.NONE, Failure.NONE);
    }

    private static void trace(State state, Stage stage, Reason reason, Failure failure) {
        android.util.Log.i("SoulHook102", "AI auto stage=" + stage.name() + " phase=" + state.phase.name()
                + " attempt=" + state.traceId + " reason=" + reason.name() + " failure=" + failure.name()
                + " panel=" + (state.callback != null) + " card=" + (state.card != null)
                + " pending=" + (state.pending != null) + " started=" + state.firstPermitComplete);
    }

    private static void traceResult(State state, Stage stage, AiReplyRequest.Output output) {
        state.phase = stage;
        AiReplyRules.Result result = output == null ? null : output.result;
        Action action = Action.NONE;
        if (result != null) {
            try { action = Action.valueOf(result.action); }
            catch (IllegalArgumentException | NullPointerException ignored) { action = Action.OTHER; }
        }
        android.util.Log.i("SoulHook102", "AI auto stage=" + stage.name() + " attempt=" + state.traceId
                + " action=" + action.name() + " candidates=" + (result == null ? 0 : result.candidates.size())
                + " questions=" + (result == null ? 0 : result.questions.size()));
    }

    private static Failure failure(Exception error) {
        if (error instanceof org.json.JSONException) return Failure.JSON_VALIDATION;
        if (error instanceof AiReplyClient.ClientException) return Failure.CLIENT;
        if (error instanceof IllegalStateException) return Failure.STATE;
        if (error instanceof java.util.concurrent.TimeoutException) return Failure.TIMEOUT;
        if (error instanceof ReflectiveOperationException) return Failure.REFLECTION;
        if (error instanceof java.io.IOException) return Failure.IO;
        return Failure.OTHER;
    }

    private AiAutomaticSuggestions() {}
}
