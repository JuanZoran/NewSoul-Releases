package dev.local.newsoul;

import java.util.*;
import org.json.*;

public final class SignInTasksTest {
    static int checks;
    static final String DAY = "2026-10-11";
    interface Attempt { void run() throws Exception; }
    static void check(boolean v, String reason) { checks++; if (!v) throw new AssertionError(reason); }
    static void rejects(Attempt a, String reason) throws Exception {
        try { a.run(); throw new AssertionError(reason); } catch (IllegalStateException | JSONException expected) { checks++; }
    }
    static JSONObject wrap(Object value) throws Exception { return new JSONObject().put("code", 10001).put("data", value); }
    static final class Journal implements SignInTasks.Journal {
        final Set<String> pending = new HashSet<>(); boolean fail;
        public boolean pending(String op) { return pending.contains(op); }
        public void begin(String op) { if (fail) throw new IllegalStateException("disk failure"); pending.add(op); }
    }
    static final class Host implements SignInTasks.Transport {
        final String task; final Journal journal = new Journal();
        boolean free = true, missing, queryFailure, unconfirmed, drawFailure, signed, noTodayCalendar, historical, changedPhase, lostPhase, catalogOnly;
        int draws, firstPlays, awards, signs, recordQueries;
        Map<String, Object> drawBody; final Set<String> received = new HashSet<>();
        Host(String task) { this.task = task; }
        public JSONObject request(String url, boolean post, Map<String, Object> p) throws Exception {
            if (url.contains("queryEffectActivityInfo")) return wrap(new JSONObject().put("activityId", "live-activity"));
            if (url.contains("queryHoroscopeList")) return wrap(catalogOnly
                    ? new JSONObject().put("horoscopeInfoModels", new JSONArray().put(new JSONObject().put("id", "2")))
                    : new JSONObject().put("anchorHoroscopeId", "2"));
            if (url.contains("queryBoxLotteryRecordMulti") || url.contains("queryLotteryList") || url.contains("queryLotteryRecord")) {
                recordQueries++;
                if (queryFailure) return new JSONObject().put("code", 500);
                JSONArray a = new JSONArray();
                if (historical || (draws > 0 && !unconfirmed)) a.put(new JSONObject().put("createTime", historical ? "2026-10-10 12:00:00" : DAY + " 12:00:00"));
                return wrap(a);
            }
            if (url.contains("FirstPlay")) { firstPlays++; return wrap(JSONObject.NULL); }
            if (url.contains("queryBoxUserAsset") || url.contains("queryUserAsset")) {
                if (missing) return wrap(new JSONObject());
                return wrap(new JSONObject().put("lotteryShowList", new JSONArray().put(new JSONObject()
                        .put("consumeType", free ? "free" : "coin").put("lotteryNum", "1").put("couponAccountNos", new JSONArray().put("coupon-1")))));
            }
            if (url.contains("queryLotteryPrice")) return wrap(missing ? new JSONObject() : new JSONObject().put("freeNum", free ? 1 : 0).put("pointNum", 999));
            if (url.endsWith("boxLottery") || url.endsWith("userLottery")) {
                check(journal.pending("draw"), "durable mutation marker precedes request");
                draws++; drawBody = p;
                if (drawFailure) throw new java.io.IOException("response lost after send");
                return wrap(new JSONObject().put("state", 1));
            }
            if (url.endsWith("main-page-info")) {
                return wrap(new JSONObject().put("lotterySignCalendarDetails", noTodayCalendar ? new JSONArray()
                        : new JSONArray().put(new JSONObject().put("day", 11).put("signed", signed)))
                        .put("obtainedFragmentCount", 4));
            }
            if (url.endsWith("lottery-sign/sign")) { signs++; if (!unconfirmed) signed = true; return wrap(JSONObject.NULL); }
            if (url.endsWith("dress/daily/task/list")) {
                JSONArray phases = new JSONArray();
                for (int i = 1; i <= 3; i++) {
                    String id = String.valueOf(i);
                    if (lostPhase && awards > 0 && i == 1) continue;
                    phases.put(new JSONObject().put("phaseId", i == 2 ? Integer.valueOf(i) : id).put("targetScore", i == 3 ? 100 : 10)
                            .put("receiveStatus", received.contains(id) ? (changedPhase ? "LOCKED" : "RECEIVED") : "CAN_RECEIVE"));
                }
                return wrap(new JSONObject().put("phaseScore", 20).put("taskPhaseInfoList", phases));
            }
            if (url.endsWith("award/v2")) {
                String id = (String) p.get("phaseId"); check(journal.pending(id), "award journal saved before request");
                awards++; received.add(id); return wrap(new JSONObject().put("success", true));
            }
            throw new AssertionError(url);
        }
        SignInTasks.Result run() throws Exception { return SignInTasks.run(task, this, journal, DAY); }
    }
    static void drawChecks(String task) throws Exception {
        Host good = new Host(task);
        SignInTasks.Result first = good.run();
        check(first.complete && good.draws == 1, "one qualified free draw completes");
        check(first.drawnRewards.size() == 1, "verified new draw exposes its current reward record");
        check("1".equals(good.drawBody.getOrDefault("lotteryNum", good.drawBody.get("combombo"))), "never multi-draw");
        check("FREE".equals(good.drawBody.get("currency")) || "free".equals(good.drawBody.get("consumeType")), "never paid currency");
        check(good.run().complete && good.draws == 1, "server today's record prevents duplicate");
        Host paid = new Host(task); paid.free = false;
        check(!paid.run().complete && paid.draws == 0, "paid assets and point balances grant no free eligibility");
        Host failQuery = new Host(task); failQuery.queryFailure = true;
        rejects(failQuery::run, "failed query cannot be empty records");
        check(failQuery.draws == 0 && failQuery.firstPlays == 0, "failed query sends no mutation");
        Host missing = new Host(task); missing.missing = true;
        rejects(missing::run, "missing eligibility is rejected"); check(missing.draws == 0, "missing assets send no draw");
        Host noAck = new Host(task); noAck.unconfirmed = true;
        check(!noAck.run().complete, "submit acknowledgement alone is not completion");
        noAck.run(); check(noAck.draws == 1, "unconfirmed response never retries draw");
        Host lost = new Host(task); lost.drawFailure = true; lost.unconfirmed = true;
        try { lost.run(); throw new AssertionError(); } catch (java.io.IOException expected) { checks++; }
        lost.run(); check(lost.draws == 1, "network loss retains journal across a new run");
        lost.unconfirmed = false;
        check(lost.run().drawnRewards.size() == 1 && lost.draws == 1, "pending draw recovers verified reward without another draw");
        Host external = new Host(task); external.draws = 1;
        check(external.run().drawnRewards.isEmpty(), "pre-existing draw without plugin journal never creates a notification");
        Host disk = new Host(task); disk.journal.fail = true;
        rejects(disk::run, "failed durable save blocks draw"); check(disk.draws == 0, "disk failure sends no draw");
        Host old = new Host(task); old.historical = true;
        check(!old.run().complete && old.draws == 1, "old record is never today's completion");
    }
    static void signChecks() throws Exception {
        Host play = new Host(SignInTasks.PLAY);
        check(play.run().complete && play.signs == 1, "playground verifies signed calendar");
        play.run(); check(play.signs == 1, "signed calendar skips mutation");
        Host unknown = new Host(SignInTasks.PLAY); unknown.noTodayCalendar = true;
        rejects(unknown::run, "missing calendar does not grant eligibility"); check(unknown.signs == 0, "no today sends no sign");
        Host unconfirmed = new Host(SignInTasks.PLAY); unconfirmed.unconfirmed = true;
        check(!unconfirmed.run().complete, "calendar must reflect successful submission");
        int[] calls = {0};
        SignInTasks.Result coins = SignInTasks.coins((url, post, p) -> {
            calls[0]++;
            if (url.endsWith("querySign")) return wrap(new JSONObject().put("todayIsSign", calls[0] > 1));
            if (url.endsWith("already")) return wrap(new JSONObject().put("already", calls[0] > 4 ? 1 : 0));
            return wrap(JSONObject.NULL);
        });
        check(coins.complete && calls[0] == 6, "both coin signs require query/submit/verify");
        check(!SignInTasks.coins((url, post, p) -> wrap(new JSONObject())).complete, "missing status cannot mark completed");
        List<String> urls = new ArrayList<>();
        SignInTasks.Result independent = SignInTasks.coins((url, post, p) -> {
            urls.add(url);
            return wrap(url.endsWith("already") ? new JSONObject().put("already", 1) : new JSONObject().put("todayIsSign", false));
        });
        check(!independent.complete && urls.stream().anyMatch(u -> u.endsWith("already")), "bag failure does not suppress independent center sign");
    }
    static void chestChecks() throws Exception {
        Host good = new Host(SignInTasks.CHEST);
        check(good.run().message.equals("已领取可用宝箱") && good.awards == 2, "only eligible score tiers claimed");
        good.run(); check(good.awards == 2, "received tiers never reclaimed");
        Host changed = new Host(SignInTasks.CHEST); changed.changedPhase = true;
        check(changed.run().message.contains("待确认"), "non-claimable is not received");
        Host lost = new Host(SignInTasks.CHEST); lost.lostPhase = true;
        check(lost.run().message.contains("待确认"), "missing target tier is not received");
    }
    static void gatewayAndPaginationChecks() throws Exception {
        Host play = new Host(SignInTasks.PLAY);
        SignInTasks.Transport gateway = (url, post, p) -> {
            JSONObject response = play.request(url, post, p);
            if (url.endsWith("main-page-info"))
                return wrap(new JSONObject().put("code", 200).put("success", true).put("data", response.get("data")));
            return response;
        };
        check(SignInTasks.run(SignInTasks.PLAY, gateway, play.journal, DAY).complete && play.signs == 1,
                "observed nested gateway calendar supports query, sign and verification");
        for (String task : new String[]{SignInTasks.BOX, SignInTasks.STAR}) {
            Host host = new Host(task);
            SignInTasks.Transport pages = (url, post, p) -> {
                JSONObject response = host.request(url, post, p);
                if (url.endsWith("queryBoxLotteryRecordMulti") || url.endsWith("queryLotteryList"))
                    return wrap(new JSONObject().put("data", response.get("data"))
                            .put("hasNextPage", false).put("pageCursor", ""));
                return response;
            };
            check(SignInTasks.run(task, pages, host.journal, DAY).complete && host.draws == 1,
                    "observed data.data pagination supports free draw and verification");
            check(SignInTasks.run(task, pages, host.journal, DAY).complete && host.draws == 1,
                    "today's paginated record prevents repeated draw");
            Host malformed = new Host(task);
            rejects(() -> SignInTasks.run(task, (url, post, p) ->
                    url.endsWith("queryBoxLotteryRecordMulti") || url.endsWith("queryLotteryList")
                    ? wrap(new JSONObject().put("data", JSONObject.NULL)) : malformed.request(url, post, p),
                    malformed.journal, DAY), "null pagination cannot grant a draw");
            check(malformed.draws == 0 && malformed.firstPlays == 0, "invalid page stops before any mutation");
        }
        for (JSONObject invalid : new JSONObject[]{new JSONObject().put("code", 500).put("success", true),
                new JSONObject().put("code", 0).put("success", false)}) {
            invalid.put("data", new JSONObject().put("lotterySignCalendarDetails", new JSONArray()
                    .put(new JSONObject().put("day", 11).put("signed", false))));
            Host host = new Host(SignInTasks.PLAY);
            rejects(() -> SignInTasks.run(SignInTasks.PLAY, (url, post, p) -> wrap(invalid), host.journal, DAY),
                    "failed or missing inner gateway data cannot grant sign");
            check(host.signs == 0, "invalid gateway sends no sign");
        }
        rejects(() -> SignInTasks.object(wrap(new JSONObject().put("code", 0).put("data", JSONObject.NULL))),
                "successful gateway with missing payload is rejected");
        rejects(() -> SignInTasks.raw(wrap(new JSONArray()).put("success", false)),
                "explicit outer failure cannot become empty records");
        rejects(() -> SignInTasks.object(wrap(new JSONObject().put("code", 200).put("data", new JSONObject()))),
                "gateway code 200 alone cannot imply business success");
        for (String task : new String[]{SignInTasks.BOX, SignInTasks.STAR}) {
            Host host = new Host(task);
            rejects(() -> SignInTasks.run(task, (url, post, p) ->
                    url.endsWith("queryBoxLotteryRecordMulti") || url.endsWith("queryLotteryList")
                    ? wrap(new JSONObject().put("code", 500).put("data", new JSONArray())) : host.request(url, post, p),
                    host.journal, DAY), "failed inner page cannot become empty records");
            check(host.draws == 0 && host.firstPlays == 0, "failed inner page sends no mutation");
        }
    }
    public static void main(String[] args) throws Exception {
        for (String task : new String[]{SignInTasks.BOX, SignInTasks.STAR, SignInTasks.WISH}) drawChecks(task);
        signChecks(); chestChecks(); gatewayAndPaginationChecks();
        Host catalog = new Host(SignInTasks.STAR); catalog.catalogOnly = true;
        check(catalog.run().complete && "2".equals(catalog.drawBody.get("horoscopeId")), "missing anchor uses a real ID from current catalog");
        for (Object bad : new Object[]{"", "nonsense", 0, "2026-02-30", "2026-10-11 junk", "2026-10-11 99:00:00", JSONObject.NULL}) check(SignInTasks.recordDay(bad).isEmpty(), "invalid timestamp cannot become today");
        check(SignInTasks.recordDay("2026/10/11 12:00:00").equals(DAY), "valid server date parsed");
        check(SignInTasks.recordDay("2026-10-10T18:00:00Z").equals(DAY), "UTC timestamp compared with service day in Shanghai");
        try { SignInTasks.validateRequest("https://api-pay.soulapp.cn/ip/buy", true); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { checks++; }
        System.out.println("Sign-in tasks: " + checks + " checks passed");
    }
}
