package dev.local.newsoul;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Confirmed full-avatar rewards only; durable, account-scoped notification receipts. */
final class AvatarRewardNotifications {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Set<String> FULL_CATEGORIES = Set.of("SECOND_DRAFT_GOODS", "SECOND_SUPER_LIMIT",
            "SECOND_LIMIT_TIME_AVATAR", "SECOND_AUTO_DIFF_AVATAR_GENDER");
    static void resolve(SignInTasks.Result result, SignInTasks.Transport transport) throws Exception {
        for (JSONObject record : result.drawnRewards) {
            JSONObject item = record.optJSONObject("lotteryReward");
            if (item == null) item = record;
            if (excluded(name(item))) continue;
            String id = item.optString("itemId", item.optString("itemIdentity", record.optString("itemId", "")));
            if (id.isEmpty() || "0".equals(id) || "null".equals(id)) continue;
            // Same read-only preview endpoint used by the current Soul lottery result page.
            JSONObject detail = SignInTasks.object(transport.request("https://api-pay.soulapp.cn/preview/commonCommodity",
                    true, SignInTasks.params("itemIdentity", id)));
            String category = detail.optString("itemCategoryType", "");
            if (category.isEmpty()) throw new IllegalStateException("奖品类型待确认，请稍后重试");
            record.put("_newsoulAvatarCategory", category);
        }
    }
    private static String queueKey(String account, String task, String day) {
        return account + ":avatar-notices-v1:" + task + ":" + day;
    }
    static boolean complete(SharedPreferences state, String account, String task, String day,
            SignInTasks.Result result) throws Exception {
        if (!result.complete) throw new IllegalArgumentException("Unverified reward result");
        JSONArray notices = new JSONArray();
        for (JSONObject record : result.drawnRewards) {
            JSONObject notice = avatar(record);
            if (notice != null) notices.put(notice);
        }
        SharedPreferences.Editor edit = state.edit().putString(account + ":complete:" + task, day);
        String key = queueKey(account, task, day);
        // Keep a recovered undelivered queue even when this round has no new reward payload.
        if (notices.length() > 0) edit.putString(key, notices.toString());
        return edit.commit();
    }
    static void deliver(Context context, SharedPreferences state, String account, String task, String day,
            BooleanSupplier valid) {
        String queue = queueKey(account, task, day);
        String encoded = state.getString(queue, "");
        if (encoded.isEmpty()) return;
        final JSONArray notices;
        try { notices = new JSONArray(encoded); }
        catch (JSONException error) { DebugDiagnostics.failure("account.welfare.avatar-notice", error); return; }
        MAIN.post(() -> {
            if (!valid.getAsBoolean() || !day.equals(SignInTasks.day())) return;
            try {
                for (int i = 0; i < notices.length(); i++) {
                    if (!valid.getAsBoolean() || !day.equals(SignInTasks.day())) return;
                    JSONObject notice = notices.getJSONObject(i);
                    String receipt = queue + ":shown:" + notice.getString("id");
                    if (state.getBoolean(receipt, false)) continue;
                    // A failed durable receipt cannot produce a notification that repeats on restart.
                    if (!state.edit().putBoolean(receipt, true).commit()) continue;
                    Toast.makeText(context, "抽中头像：" + notice.getString("name"), Toast.LENGTH_LONG).show();
                }
            } catch (Exception error) { DebugDiagnostics.failure("account.welfare.avatar-notice", error); }
        });
    }
    private static String name(JSONObject item) {
        for (String key : new String[]{"name", "assetName", "itemName", "prizeName", "awardName"}) {
            Object value = item.opt(key);
            if (value instanceof String && !((String)value).trim().isEmpty())
                return ((String)value).replaceAll("[\\p{Cc}\\p{Cf}]", "").trim();
        }
        return "";
    }
    private static boolean excluded(String title) {
        return title.contains("框") || title.contains("碎片") || title.contains("券")
                || title.contains("礼包") || title.contains("宝箱") || title.contains("兑换")
                || title.contains("体验卡") || title.contains("背景") || title.contains("空间装扮");
    }
    static JSONObject avatar(JSONObject record) throws Exception {
        JSONObject item = record.optJSONObject("lotteryReward");
        if (item == null) item = record;
        String title = name(item);
        if (title.isEmpty()) return null;
        if (excluded(title)) return null;
        String category = record.optString("_newsoulAvatarCategory", "");
        if (!category.isEmpty() && !FULL_CATEGORIES.contains(category)) return null;
        boolean full = FULL_CATEGORIES.contains(category);
        for (String key : new String[]{"type", "itemType", "assetType", "prizeType", "rightsType"}) {
            Object value = item.opt(key);
            // Numeric mappings are not inferred from an unrelated native commodity model.
            if (value instanceof Number) {
                continue;
            }
            if (!(value instanceof String)) continue;
            String type = ((String)value).toUpperCase(Locale.ROOT);
            if (type.matches("[0-9]+")) continue;
            if (!"AVATAR".equals(type) && !"HEAD_PORTRAIT".equals(type)) return null;
            full |= "AVATAR".equals(type) || "HEAD_PORTRAIT".equals(type);
        }
        if (!full) return null;
        StringBuilder identity = new StringBuilder();
        for (String key : new String[]{"id", "prizeId", "itemId", "assetId", "createTime", "gmtCreate", "time", "lotteryTime", "drawTime", "awardTime", "updateTime", "recTime", "lotteryDate", "date", "day", "bizDate"}) {
            Object value = record.opt(key);
            if (value instanceof String || value instanceof Number) identity.append(key).append('=').append(value).append(';');
        }
        identity.append("name=").append(title);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(identity.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder id = new StringBuilder();
        for (byte value : hash) id.append(String.format(Locale.ROOT, "%02x", value & 255));
        return new JSONObject().put("id", id.toString()).put("name", title);
    }
    private AvatarRewardNotifications() {}
}
