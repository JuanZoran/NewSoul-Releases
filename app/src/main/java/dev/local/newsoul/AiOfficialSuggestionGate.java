package dev.local.newsoul;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Monotonic visit-scoped permission; duplicate signals never extend a visit's two-minute window. */
final class AiOfficialSuggestionGate {
    static final long TTL_MS = 120_000L;
    static final long INITIAL_SIGNAL_MS = 5_000L;
    enum Placement { ROW, OPENING }
    private final WeakHashMap<Object, Boolean> seenOwners = new WeakHashMap<>();
    private Pending pending;
    private long sequence, currentVisit;
    private String account, session, target;
    private long issuedAt;
    private boolean issued;
    private Placement placement;

    private static final class Pending {
        final WeakReference<Object> owner, conversation;
        final String account, session, target;
        final long time;
        final Placement placement;
        Pending(Object owner, Object conversation, String account, String session, String target, long time,
                Placement placement) {
            this.owner = new WeakReference<>(owner); this.conversation = new WeakReference<>(conversation);
            this.account = account; this.session = session; this.target = target; this.time = time;
            this.placement = placement;
        }
    }

    /** Only an exact, never-resumed owner may carry an initial natural decision to its first resume. */
    synchronized boolean stage(Object owner, Object conversation, String account, String session, String target, long now) {
        return stage(owner, conversation, account, session, target, now, Placement.ROW);
    }

    synchronized boolean stage(Object owner, Object conversation, String account, String session, String target,
            long now, Placement placement) {
        if (owner == null || conversation == null || seenOwners.containsKey(owner) || now < 0
                || empty(account) || empty(session) || empty(target) || placement == null) return false;
        if (pending != null && pending.owner.get() == owner) {
            // A second natural source cannot change the initial decision's placement or lifetime.
            return pending.conversation.get() == conversation && pending.account.equals(account)
                    && pending.session.equals(session) && pending.target.equals(target)
                    && now >= pending.time && now - pending.time < INITIAL_SIGNAL_MS;
        }
        pending = new Pending(owner, conversation, account, session, target, now, placement);
        return true;
    }

    synchronized long beginVisit(Object owner, Object conversation, String account, String session, String target, long now) {
        long visit = beginVisit();
        if (owner != null) seenOwners.put(owner, Boolean.TRUE);
        Pending initial = pending;
        pending = null;
        if (initial != null && owner != null && conversation != null
                && initial.owner.get() == owner && initial.conversation.get() == conversation
                && initial.account.equals(account) && initial.session.equals(session) && initial.target.equals(target)
                && now >= initial.time && now - initial.time < INITIAL_SIGNAL_MS)
            issue(visit, account, session, target, initial.time, initial.placement);
        return visit;
    }

    /** A pause before the first resume also makes any queued initial signal unusable. */
    synchronized void cancelOwner(Object owner) {
        if (owner == null) return;
        seenOwners.put(owner, Boolean.TRUE);
        if (pending != null && pending.owner.get() == owner) pending = null;
    }

    synchronized long beginVisit() {
        currentVisit = ++sequence;
        issued = false; account = session = target = null; placement = null;
        return currentVisit;
    }

    synchronized void cancel(long visit) {
        if (visit != currentVisit) return;
        currentVisit = 0; issued = false; account = session = target = null; placement = null;
    }

    synchronized boolean issue(long visit, String account, String session, String target, long now) {
        return issue(visit, account, session, target, now, Placement.ROW);
    }

    synchronized boolean issue(long visit, String account, String session, String target, long now, Placement placement) {
        if (visit == 0 || visit != currentVisit || now < 0 || empty(account) || empty(session)
                || empty(target) || placement == null) return false;
        if (issued) return allows(visit, account, session, target, now);
        this.account = account; this.session = session; this.target = target;
        issuedAt = now; issued = true; this.placement = placement;
        return true;
    }

    synchronized boolean allows(long visit, String account, String session, String target, long now) {
        return sameVisit(visit, account, session, target, now) && now - issuedAt < TTL_MS;
    }

    /** An already displayed result may be used during this visit after the automatic start window ends. */
    synchronized boolean sameVisit(long visit, String account, String session, String target, long now) {
        return issued && visit != 0 && visit == currentVisit && now >= issuedAt
                && this.account.equals(account) && this.session.equals(session) && this.target.equals(target);
    }

    /** Placement follows the first authorized source for this exact visit, never a currently visible View. */
    synchronized Placement placement(long visit, String account, String session, String target, long now) {
        return sameVisit(visit, account, session, target, now) ? placement : null;
    }

    private static boolean empty(String text) { return text == null || text.isEmpty() || text.equals("null"); }
}
