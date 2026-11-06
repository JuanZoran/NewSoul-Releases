package dev.local.newsoul;

public final class AiOfficialSuggestionGateTest {
    private static int checks;
    public static void main(String[] args) {
        AiOfficialSuggestionGate gate = new AiOfficialSuggestionGate();
        long first = gate.beginVisit();
        check(!gate.allows(first, "a", "s", "t", 1000));
        check(gate.issue(first, "a", "s", "t", 1000));
        check(gate.allows(first, "a", "s", "t", 1001));
        check(!gate.allows(first, "other", "s", "t", 1001));
        check(!gate.allows(first, "a", "other", "t", 1001));
        check(!gate.allows(first, "a", "s", "other", 1001));
        check(!gate.allows(first, "a", "s", "t", 999));
        check(gate.allows(first, "a", "s", "t", 120999));
        // A duplicate signal must not renew an old entry's request window.
        check(gate.issue(first, "a", "s", "t", 120999));
        check(!gate.allows(first, "a", "s", "t", 121000));
        check(gate.sameVisit(first, "a", "s", "t", 121000));
        check(!gate.sameVisit(first, "other", "s", "t", 121000));
        check(!gate.sameVisit(first, "a", "other", "t", 121000));
        check(!gate.sameVisit(first, "a", "s", "other", 121000));
        check(!gate.sameVisit(first, "a", "s", "t", 999));
        check(!gate.issue(first, "a", "s", "t", 121001));
        gate.cancel(first);
        check(!gate.sameVisit(first, "a", "s", "t", 121001));
        check(!gate.allows(first, "a", "s", "t", 1001));
        check(!gate.issue(first, "a", "s", "t", 1001));
        long second = gate.beginVisit();
        check(second != first);
        check(!gate.sameVisit(second,"b","new-session","new-target",2000));
        check(!gate.issue(first, "a", "s", "t", 1002));
        gate.cancel(first); // Delayed pause/signal from an earlier visit must not alter the new visit.
        check(gate.issue(second, "b", "new-session", "new-target", 2000));
        check(gate.allows(second, "b", "new-session", "new-target", 2001));
        check(!gate.allows(second, "a", "s", "t", 2001));
        check(!gate.issue(second, "a", "s", "t", 2001));
        long third = gate.beginVisit();
        check(!gate.issue(third, "", "s", "t", 3000));
        check(!gate.issue(third, "a", null, "t", 3000));
        check(!gate.issue(third, "a", "s", "null", 3000));
        check(!gate.issue(third, "a", "s", "t", -1));
        check(!gate.issue(0, "a", "s", "t", 3000));
        initialSignals();
        placementIsolation();
        System.out.println("AiOfficialSuggestionGateTest: " + checks + " checks passed");
    }

    private static void initialSignals() {
        Object owner = new Object(), conversation = new Object();
        AiOfficialSuggestionGate gate = new AiOfficialSuggestionGate();
        check(gate.stage(owner, conversation, "a", "s", "t", 10_000));
        check(gate.stage(owner, conversation, "a", "s", "t", 14_999));
        long first = gate.beginVisit(owner, conversation, "a", "s", "t", 14_999);
        check(gate.allows(first, "a", "s", "t", 14_999));
        // Activation preserves the decision's original two-minute lifetime.
        check(!gate.allows(first, "a", "s", "t", 130_000));
        check(!gate.stage(owner, conversation, "a", "s", "t", 15_000));
        gate.cancelOwner(owner); gate.cancel(first);
        check(!gate.stage(owner, conversation, "a", "s", "t", 15_001));
        long second = gate.beginVisit(owner, conversation, "a", "s", "t", 15_001);
        check(!gate.allows(second, "a", "s", "t", 15_001));

        for (int mismatch = 0; mismatch < 7; mismatch++) {
            AiOfficialSuggestionGate isolated = new AiOfficialSuggestionGate();
            Object exactOwner = new Object(), exactConversation = new Object();
            check(isolated.stage(exactOwner, exactConversation, "a", "s", "t", 10_000));
            long visit = isolated.beginVisit(mismatch == 0 ? new Object() : exactOwner,
                    mismatch == 1 ? new Object() : exactConversation,
                    mismatch == 2 ? "b" : "a", mismatch == 3 ? "other-session" : "s",
                    mismatch == 4 ? "other-target" : "t", mismatch == 5 ? 15_000 : mismatch == 6 ? 9_999 : 10_001);
            check(!isolated.allows(visit, "a", "s", "t", 10_002));
        }
        AiOfficialSuggestionGate paused = new AiOfficialSuggestionGate();
        Object pausedOwner = new Object();
        check(paused.stage(pausedOwner, conversation, "a", "s", "t", 20_000));
        paused.cancelOwner(pausedOwner);
        check(!paused.stage(pausedOwner, conversation, "a", "s", "t", 20_001));
        long pausedVisit = paused.beginVisit(pausedOwner, conversation, "a", "s", "t", 20_002);
        check(!paused.allows(pausedVisit, "a", "s", "t", 20_003));

        AiOfficialSuggestionGate expired = new AiOfficialSuggestionGate();
        Object expiredOwner = new Object();
        check(expired.stage(expiredOwner, conversation, "a", "s", "t", 20_000));
        check(!expired.stage(expiredOwner, conversation, "a", "s", "t", 25_000));
        check(!expired.stage(expiredOwner, new Object(), "a", "s", "t", 20_001));
        check(!expired.stage(expiredOwner, conversation, "b", "s", "t", 20_001));
        long expiredVisit = expired.beginVisit(expiredOwner, conversation, "a", "s", "t", 25_001);
        check(!expired.allows(expiredVisit, "a", "s", "t", 25_002));

        AiOfficialSuggestionGate missing = new AiOfficialSuggestionGate();
        check(!missing.stage(null, conversation, "a", "s", "t", 0));
        check(!missing.stage(new Object(), null, "a", "s", "t", 0));
        check(!missing.stage(new Object(), conversation, "", "s", "t", 0));
        check(!missing.stage(new Object(), conversation, "a", "s", "t", -1));
    }

    private static void placementIsolation() {
        for (AiOfficialSuggestionGate.Placement first : AiOfficialSuggestionGate.Placement.values()) {
            AiOfficialSuggestionGate.Placement later = first == AiOfficialSuggestionGate.Placement.ROW
                    ? AiOfficialSuggestionGate.Placement.OPENING : AiOfficialSuggestionGate.Placement.ROW;
            AiOfficialSuggestionGate gate = new AiOfficialSuggestionGate();
            long visit = gate.beginVisit();
            check(gate.placement(visit, "a", "s", "t", 1000) == null);
            check(!gate.issue(visit, "a", "s", "t", 1000, null));
            check(gate.issue(visit, "a", "s", "t", 1000, first));
            check(gate.placement(visit, "a", "s", "t", 1000) == first);
            check(gate.issue(visit, "a", "s", "t", 1001, later));
            check(gate.placement(visit, "a", "s", "t", 1001) == first);
            check(gate.placement(visit, "b", "s", "t", 1001) == null);
            check(gate.placement(visit, "a", "other", "t", 1001) == null);
            check(gate.placement(visit, "a", "s", "other", 1001) == null);
            check(gate.placement(visit, "a", "s", "t", 999) == null);
            check(!gate.allows(visit, "a", "s", "t", 121000));
            check(!gate.issue(visit, "a", "s", "t", 121000, later));
            // Existing cards retain their placement after the automatic-start window, for this visit only.
            check(gate.placement(visit, "a", "s", "t", 121000) == first);
            gate.cancel(visit);
            check(gate.placement(visit, "a", "s", "t", 121001) == null);
            long next = gate.beginVisit();
            check(!gate.issue(visit, "a", "s", "t", 121002, first));
            check(gate.placement(next, "a", "s", "t", 121002) == null);
            check(gate.issue(next, "a", "s", "t", 121002, later));
            gate.cancel(visit);
            check(gate.placement(next, "a", "s", "t", 121003) == later);

            Object owner = new Object(), conversation = new Object();
            AiOfficialSuggestionGate initial = new AiOfficialSuggestionGate();
            check(!initial.stage(owner, conversation, "a", "s", "t", 20000, null));
            check(initial.stage(owner, conversation, "a", "s", "t", 20000, first));
            check(initial.stage(owner, conversation, "a", "s", "t", 24999, later));
            long carried = initial.beginVisit(owner, conversation, "a", "s", "t", 24999);
            check(initial.placement(carried, "a", "s", "t", 24999) == first);
            check(initial.issue(carried, "a", "s", "t", 25000, later));
            check(initial.placement(carried, "a", "s", "t", 25000) == first);
            check(!initial.allows(carried, "a", "s", "t", 140000));

            for (int mismatch = 0; mismatch < 8; mismatch++) {
                AiOfficialSuggestionGate isolated = new AiOfficialSuggestionGate();
                Object exactOwner = new Object(), exactConversation = new Object();
                check(isolated.stage(exactOwner, exactConversation, "a", "s", "t", 30000, first));
                if (mismatch == 7) isolated.cancelOwner(exactOwner);
                long invalid = isolated.beginVisit(mismatch == 0 ? new Object() : exactOwner,
                        mismatch == 1 ? new Object() : exactConversation,
                        mismatch == 2 ? "other" : "a", mismatch == 3 ? "other" : "s",
                        mismatch == 4 ? "other" : "t", mismatch == 5 ? 35000 : mismatch == 6 ? 29999 : 30001);
                check(isolated.placement(invalid, "a", "s", "t", 35001) == null);
            }
        }
        AiOfficialSuggestionGate legacy = new AiOfficialSuggestionGate();
        long visit = legacy.beginVisit();
        check(legacy.issue(visit, "a", "s", "t", 0));
        check(legacy.placement(visit, "a", "s", "t", 0) == AiOfficialSuggestionGate.Placement.ROW);
        Object owner = new Object(), conversation = new Object();
        check(legacy.stage(owner, conversation, "a", "s", "t", 1));
        visit = legacy.beginVisit(owner, conversation, "a", "s", "t", 2);
        check(legacy.placement(visit, "a", "s", "t", 2) == AiOfficialSuggestionGate.Placement.ROW);
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("check " + (checks + 1)); checks++; }
}
