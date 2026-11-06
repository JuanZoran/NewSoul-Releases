package dev.local.newsoul;

import java.time.*;
import java.util.*;
import org.json.*;

/** Query -> qualify -> one mutation -> verify. Missing data never grants eligibility. */
final class SignInTasks {
    static final String PREFIX = "account_welfare_";
    static final String PLAY = PREFIX + "play_v1", BOX = PREFIX + "treasure_v1",
            STAR = PREFIX + "star_v1", WISH = PREFIX + "wish_v1", CHEST = PREFIX + "chest_v1";
    static final String[][] ITEMS = {{PLAY, "星游乐场签到"}, {BOX, "星域宝藏免费抽奖"},
            {STAR, "摘星试炼免费抽奖"}, {WISH, "装扮祈愿免费抽奖"}, {CHEST, "活跃度宝箱领取"}};
    private static final String PAY = "https://api-pay.soulapp.cn", ACT = "https://activity.soulapp.cn";
    // Query identifiers observed in Soulmate R 1.3.29. Live free assets must still qualify the draw.
    private static final String STAR_ID = "20251212", WISH_ID = "313", MODULE = "dressDailyAward";
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    interface Transport { JSONObject request(String url, boolean post, Map<String, Object> data) throws Exception; }
    interface Journal {
        boolean pending(String operation);
        void begin(String operation) throws Exception;
    }
    static final class Result {
        final boolean complete;
        final String message;
        final List<JSONObject> drawnRewards;
        Result(boolean complete, String message) { this(complete, message, Collections.emptyList()); }
        Result(boolean complete, String message, List<JSONObject> drawnRewards) {
            this.complete = complete; this.message = message;
            this.drawnRewards = complete ? Collections.unmodifiableList(new ArrayList<>(drawnRewards)) : Collections.emptyList();
        }
    }
    static String day() { return LocalDate.now(ZONE).toString(); }
    static void validateRequest(String url, boolean post) {
        String[] get = {ACT + "/lottery-sign/main-page-info", PAY + "/dress/daily/task/list"};
        String[] posts = {ACT + "/lottery-sign/sign",
            PAY + "/preview/commonCommodity",
            PAY + "/box/queryEffectActivityInfo", PAY + "/box/queryBoxLotteryRecordMulti",
            PAY + "/box/userBoxDailyFirstPlay", PAY + "/box/queryBoxUserAsset", PAY + "/box/boxLottery",
            PAY + "/horoscopeLottery/queryHoroscopeList", PAY + "/horoscopeLottery/queryLotteryList",
            PAY + "/horoscopeLottery/dailyFirstPlay", PAY + "/horoscopeLottery/queryLotteryPrice", PAY + "/horoscopeLottery/userLottery",
            PAY + "/ip/queryLotteryRecord", PAY + "/ip/dailyFirstPlay", PAY + "/ip/queryUserAsset", PAY + "/ip/userLottery",
            "https://marketing-openapi.soulapp.cn/html/common/activity/task/phase/award/v2"};
        if (!Arrays.asList(post ? posts : get).contains(url)) throw new IllegalArgumentException("Unexpected welfare request");
    }
    static Map<String, Object> params(Object... values) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) out.put((String) values[i], values[i + 1]);
        return out;
    }
    static Object raw(JSONObject response) {
        if (!response.has("code") || response.optInt("code", -1) != 10001)
            throw new IllegalStateException("服务返回 " + response.optInt("code", -1));
        if (response.has("success") && !response.optBoolean("success", false))
            throw new IllegalStateException("服务未确认成功");
        Object data = response.opt("data");
        if (data == null || data == JSONObject.NULL) throw new IllegalStateException("服务结果缺失");
        return data;
    }
    static JSONObject object(JSONObject response) {
        Object value = raw(response);
        if (!(value instanceof JSONObject)) throw new IllegalStateException("服务结果格式已变化");
        JSONObject object = (JSONObject) value;
        // The activity gateway wraps the business response inside the outer Soul envelope.
        if (object.has("code") && object.has("data")) {
            int code = object.optInt("code", -1);
            boolean successfulCode = code == 0 || code == 10001
                    || (code == 200 && object.optBoolean("success", false));
            if (!successfulCode || (object.has("success") && !object.optBoolean("success", false)))
                throw new IllegalStateException("服务返回 " + code);
            Object nested = object.opt("data");
            if (!(nested instanceof JSONObject)) throw new IllegalStateException("服务结果格式已变化");
            return (JSONObject) nested;
        }
        return object;
    }
    private static JSONObject get(Transport t, String url, Map<String, Object> p) throws Exception {
        return object(t.request(url, false, p));
    }
    private static JSONObject post(Transport t, String url, Map<String, Object> p) throws Exception {
        return object(t.request(url, true, p));
    }
    static Result coins(Transport t) throws Exception {
        List<String> incomplete = new ArrayList<>();
        for (int part = 0; part < 2; part++) {
            try {
                String message = coinPart(t, part);
                if (!message.isEmpty()) incomplete.add(message);
            } catch (java.io.InterruptedIOException cancelled) { throw cancelled; }
            catch (Exception error) {
                incomplete.add((part == 0 ? "福袋签到" : "签到中心") + "未完成："
                        + (error instanceof IllegalStateException ? error.getMessage() : "查询失败，请在 Soul 中检查"));
            }
        }
        return new Result(incomplete.isEmpty(), incomplete.isEmpty() ? "今日签到已完成" : String.join("\n", incomplete));
    }
    private static String coinPart(Transport t, int part) throws Exception {
        if (part == 0) {
            String base = "https://increase-openapi.soulapp.cn/increase/sign/";
            JSONObject bag = get(t, base + "querySign", params());
            if (!bag.has("todayIsSign")) throw new IllegalStateException("签到状态缺失");
            if (!bag.getBoolean("todayIsSign")) {
                // Browser tasks and repair cards need a separate user choice; do not consume them here.
                t.request(base + "userSign", false, params());
                bag = get(t, base + "querySign", params());
                if (!bag.has("todayIsSign") || !bag.getBoolean("todayIsSign"))
                    return "福袋签到未完成，请在 Soul 签到页处理";
            }
        } else {
            String base = "https://api-a.soulapp.cn/sign-in/";
            JSONObject state = get(t, base + "already", params());
            int before = state.optInt("already", -1);
            if (before != 0 && before != 1) throw new IllegalStateException("签到状态缺失");
            if (before == 0) {
                t.request(base + "do/v3", true, params());
                state = get(t, base + "already", params());
                if (state.optInt("already", -1) != 1) return "签到中心未确认，请在 Soul 中检查";
            }
        }
        return "";
    }
    static Result run(String task, Transport t, Journal journal, String day) throws Exception {
        if (PLAY.equals(task)) return playground(t, day);
        if (CHEST.equals(task)) return chest(t, journal);
        if (BOX.equals(task) || STAR.equals(task) || WISH.equals(task)) return draw(task, t, journal, day);
        throw new IllegalArgumentException("Unknown sign task");
    }
    private static boolean signed(JSONObject page, String day) throws Exception {
        JSONArray calendar = page.getJSONArray("lotterySignCalendarDetails");
        int date = LocalDate.parse(day).getDayOfMonth();
        for (int i = 0; i < calendar.length(); i++) {
            JSONObject item = calendar.getJSONObject(i);
            if (item.optInt("day", -1) == date) return item.getBoolean("signed");
        }
        throw new IllegalStateException("今日日历状态缺失");
    }
    private static Result playground(Transport t, String day) throws Exception {
        String pageUrl = ACT + "/lottery-sign/main-page-info";
        if (signed(get(t, pageUrl, params()), day)) return new Result(true, "今日已签到");
        t.request(ACT + "/lottery-sign/sign", true, params());
        JSONObject after = get(t, pageUrl, params());
        if (!signed(after, day)) return new Result(false, "签到结果待确认，请在 Soul 中检查");
        return new Result(true, "今日已签到" + (after.has("obtainedFragmentCount")
                ? " · 碎片 " + after.getInt("obtainedFragmentCount") : ""));
    }
    static String recordDay(Object value) {
        if (value == null || value == JSONObject.NULL) return "";
        try {
            String s = String.valueOf(value);
            if (s.matches("\\d{10}|\\d{13}")) {
                long n = Long.parseLong(s);
                return Instant.ofEpochMilli(s.length() == 10 ? n * 1000 : n).atZone(ZONE).toLocalDate().toString();
            }
            if (s.endsWith("Z") || s.matches(".*[+-]\\d{2}:\\d{2}$"))
                return OffsetDateTime.parse(s.replace(' ', 'T')).atZoneSameInstant(ZONE).toLocalDate().toString();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})(?:[ T](\\d{2}):(\\d{2}):(\\d{2})(?:\\.\\d{1,9})?)?$").matcher(s);
            if (m.matches() && m.group(4) != null)
                LocalTime.of(Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6)));
            if (m.matches()) return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))).toString();
        } catch (RuntimeException ignored) { }
        return "";
    }
    private static List<JSONObject> todayRecords(Transport t, String url, Map<String, Object> p, String day) throws Exception {
        Object value = raw(t.request(url, true, p));
        JSONArray records = value instanceof JSONArray ? (JSONArray) value : null;
        if (value instanceof JSONObject) {
            JSONObject page = (JSONObject) value;
            if (page.has("code") && page.optInt("code", -1) != 0 && page.optInt("code", -1) != 10001)
                throw new IllegalStateException("服务返回 " + page.optInt("code", -1));
            if (page.has("success") && !page.optBoolean("success", false))
                throw new IllegalStateException("服务未确认成功");
            for (String key : new String[]{"list", "dataList", "records", "lotteryRecordList", "lotteryRecords", "data"})
                if (page.has(key)) { records = page.getJSONArray(key); break; }
        }
        if (records == null) throw new IllegalStateException("抽奖记录格式已变化");
        List<JSONObject> today = new ArrayList<>();
        for (int i = 0; i < records.length(); i++) {
            JSONObject item = records.getJSONObject(i);
            String date = "";
            for (String key : new String[]{"createTime", "gmtCreate", "time", "lotteryTime", "drawTime", "awardTime", "updateTime", "recTime", "lotteryDate", "date", "day", "bizDate"})
                if (item.has(key)) { date = recordDay(item.opt(key)); break; }
            if (date.isEmpty()) throw new IllegalStateException("抽奖记录日期缺失");
            if (day.equals(date)) today.add(item);
        }
        return today;
    }
    private static JSONObject free(JSONObject assets) throws Exception {
        JSONArray options = assets.getJSONArray("lotteryShowList");
        for (int i = 0; i < options.length(); i++) {
            JSONObject option = options.getJSONObject(i);
            if ("free".equals(option.optString("consumeType")) && "1".equals(option.optString("lotteryNum"))) return option;
        }
        return null;
    }
    private static String coupons(JSONObject option) throws Exception {
        Object v = option.opt("couponAccountNos");
        if (v == null || v == JSONObject.NULL) return "";
        if (v instanceof String) return (String) v;
        if (!(v instanceof JSONArray)) throw new IllegalStateException("免费券格式已变化");
        List<String> values = new ArrayList<>();
        JSONArray a = (JSONArray) v;
        for (int i = 0; i < a.length(); i++) {
            Object item = a.get(i);
            if (!(item instanceof String) && !(item instanceof Number)) throw new IllegalStateException("免费券格式已变化");
            values.add(String.valueOf(item));
        }
        return String.join(",", values);
    }
    private static void checkActivity(JSONObject data, String id) {
        if (data.has("activityId") && !id.equals(data.optString("activityId")))
            throw new IllegalStateException("活动已变化，请在 Soul 中检查");
    }
    private static Result draw(String task, Transport t, Journal journal, String day) throws Exception {
        String record, submit, id, root;
        Map<String, Object> recordParams, body;
        if (BOX.equals(task)) {
            root = PAY + "/box/";
            id = post(t, root + "queryEffectActivityInfo", params()).optString("activityId", "");
            if (id.isEmpty()) throw new IllegalStateException("活动信息缺失");
            record = root + "queryBoxLotteryRecordMulti"; recordParams = params(); submit = root + "boxLottery";
        } else {
            boolean star = STAR.equals(task);
            root = PAY + (star ? "/horoscopeLottery/" : "/ip/"); id = star ? STAR_ID : WISH_ID;
            record = root + (star ? "queryLotteryList" : "queryLotteryRecord");
            recordParams = params("activityId", id); submit = root + "userLottery";
            if (star) {
                JSONObject signs = post(t, root + "queryHoroscopeList", recordParams);
                checkActivity(signs, id);
                String horoscope = signs.optString("anchorHoroscopeId", "");
                if (horoscope.isEmpty()) {
                    JSONArray choices = signs.optJSONArray("horoscopeInfoModels");
                    if (choices != null && choices.length() > 0 && choices.optJSONObject(0) != null)
                        horoscope = choices.getJSONObject(0).optString("id", "");
                }
                if (horoscope.isEmpty() || "null".equals(horoscope)) throw new IllegalStateException("星座信息缺失");
                recordParams = params("activityId", id, "horoscopeId", horoscope, "pageSize", "20");
            }
        }
        List<JSONObject> before = todayRecords(t, record, recordParams, day);
        if (!before.isEmpty()) return new Result(true, "今日已抽奖",
                journal.pending("draw") ? before : Collections.emptyList());
        if (journal.pending("draw")) return new Result(false, "抽奖结果待确认，请在 Soul 中检查");
        JSONObject first = t.request(root + (BOX.equals(task) ? "userBoxDailyFirstPlay" : "dailyFirstPlay"), true, params("activityId", id));
        if (first.optInt("code", -1) != 10001) throw new IllegalStateException("免费机会查询失败");
        if (STAR.equals(task)) {
            JSONObject assets = post(t, root + "queryLotteryPrice", params("activityId", id)); checkActivity(assets, id);
            if (!assets.has("freeNum")) throw new IllegalStateException("免费次数缺失");
            if (assets.getInt("freeNum") <= 0) return new Result(false, "今日无免费机会");
            body = params("activityId", id, "horoscopeId", recordParams.get("horoscopeId"), "combombo", "1", "currency", "FREE");
        } else {
            JSONObject assets = post(t, root + (BOX.equals(task) ? "queryBoxUserAsset" : "queryUserAsset"), params("activityId", id));
            checkActivity(assets, id); JSONObject choice = free(assets);
            if (choice == null) return new Result(false, "今日无免费机会");
            body = params("activityId", id, "consumeType", "free", "lotteryNum", "1");
            if (BOX.equals(task)) { body.put("metaRefer", "meta_reward"); body.put("couponAccountNos", coupons(choice)); }
        }
        journal.begin("draw"); // Durable before sending; ambiguous responses cannot re-spend after restart.
        t.request(submit, true, body);
        List<JSONObject> after = todayRecords(t, record, recordParams, day);
        return !after.isEmpty() ? new Result(true, "今日已抽奖", after)
                : new Result(false, "抽奖结果待确认，请在 Soul 中检查");
    }
    private static Result chest(Transport t, Journal journal) throws Exception {
        String url = PAY + "/dress/daily/task/list";
        JSONObject page = get(t, url, params("activityCode", MODULE));
        int score = page.getInt("phaseScore"); JSONArray phases = page.getJSONArray("taskPhaseInfoList");
        Set<String> expected = new LinkedHashSet<>(); boolean pending = false;
        for (int i = 0; i < phases.length(); i++) {
            JSONObject phase = phases.getJSONObject(i);
            if (!"CAN_RECEIVE".equals(phase.getString("receiveStatus")) || score < phase.getInt("targetScore")) continue;
            Object rawId = phase.opt("phaseId");
            if (!(rawId instanceof String) && !(rawId instanceof Number)) throw new IllegalStateException("宝箱档位缺失");
            String id = String.valueOf(rawId);
            if (id.isEmpty()) throw new IllegalStateException("宝箱档位缺失");
            if (journal.pending(id)) { pending = true; continue; }
            expected.add(id);
            journal.begin(id);
            t.request("https://marketing-openapi.soulapp.cn/html/common/activity/task/phase/award/v2", true,
                    params("phaseId", id, "moduleCode", MODULE));
        }
        if (expected.isEmpty()) return new Result(false, pending ? "领取结果待确认，请在 Soul 中检查" : "暂无可领取宝箱");
        JSONArray after = get(t, url, params("activityCode", MODULE)).getJSONArray("taskPhaseInfoList");
        for (int i = 0; i < after.length(); i++) {
            JSONObject phase = after.getJSONObject(i);
            if ("RECEIVED".equals(phase.optString("receiveStatus"))) expected.remove(phase.optString("phaseId"));
        }
        return new Result(false, expected.isEmpty() && !pending ? "已领取可用宝箱" : "领取结果待确认，请在 Soul 中检查");
    }
    private SignInTasks() {}
}
