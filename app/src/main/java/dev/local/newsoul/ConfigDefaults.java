package dev.local.newsoul;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared defaults for preference UI and the explicit transferable configuration schema. */
final class ConfigDefaults {
    static final Map<String,Object> REQUIRED;
    static final Map<String,Object> OPTIONAL;
    static {
        Map<String,Object> required = new LinkedHashMap<>();
        Map<String,Object> optional = new LinkedHashMap<>();
        for(String key : ("block_splash game_dice game_finger chat_anti_recall "
                + "chat_own_recall_no_time_limit chat_remove_gift_gate chat_remove_bell_gift_gate post_remove_share_limit chat_remove_social_score_limit "
                + "chat_hide_typing match_mute_background_music download_image_no_watermark "
                + "download_video_no_watermark square_visible_like page_log startup_toast").split(" "))
            required.put(key,true);
        for(String key : ("ad_feed ad_detail ad_video ad_tag ad_tag_web ad_search ad_group ad_planet "
                + "ad_me ad_conversation ad_envelope ad_notice ad_chat_tools ad_float ad_interstitial "
                + "ad_reward_chat ad_reward_drama ad_reward_web privacy_allow_download "
                + "privacy_allow_screenshot privacy_allow_repeat feed_filter_enabled feed_filter_keep_unknown "
                + "feed_keywords_enabled_v1 square_like_all_loaded square_auto_like chat_auto_read_v1 feed_filter_city_pages_v1 square_like_city_pages_v1").split(" "))
            required.put(key,false);
        required.put("feed_filter_gender","FEMALE");
        required.put("feed_keywords_v1","");
        required.put("chat_recall_style","below");
        optional.put("chat_time_format_enabled_v1", false);
        optional.put("chat_time_every_message_v1", false);
        optional.put("chat_time_format_v1", ChatTimeRules.DEFAULT);
        optional.put("soul_block_update_popup_v1", false);
        optional.put("soul_block_teenage_popup_v1", false);
        for (String key : new String[]{"chat_block_read_receipt_v1", "chat_block_profile_notice_v1",
                "chat_block_close_friend_invite_v1", "picker_photo_limit_99_v1", "picker_video_limit_99_v1",
                "feed_filter_vip_v1"}) optional.put(key, false);
        optional.put("foreground_clear_notifications_v1", false);
        optional.put("menu_frosted_glass_v1", true);
        optional.put("square_time_order_v1", false);
        optional.put("feed_filter_content_enabled_v1", false);
        optional.put("feed_filter_content_types_v1", 7);
        optional.put("feed_keywords_name_enabled_v1", false);
        optional.put("feed_keywords_name_v1", "");
        optional.put("square_show_ip_location_v1", true);
        optional.put("square_show_registration_days_v1", false);
        for (String prefix : new String[]{"square_ip_label_", "square_days_label_"}) {
            optional.put(prefix + "color", "default");
            optional.put(prefix + "size", "default");
        }
        optional.put("account_auto_sign_v1", false);
        for (String key : new String[]{"account_welfare_play_v1", "account_welfare_treasure_v1",
                "account_welfare_star_v1", "account_welfare_wish_v1", "account_welfare_chest_v1"}) optional.put(key, false);
        optional.put("account_auto_hide_v1", false);
        optional.put("feed_registration_days_v1", false);
        optional.put("feed_registration_min_v1", 1);
        optional.put("feed_registration_max_v1", 0);
        for (String prefix : new String[]{"profile_disguise_follow_", "profile_disguise_followed_", "profile_disguise_visitor_", "profile_disguise_days_"}) {
            optional.put(prefix + "enabled", false);
            optional.put(prefix + "mode", "absolute");
            optional.put(prefix + "absolute", 0);
            optional.put(prefix + "delta", 0);
        }
        // Inert values may round-trip through the public edition without enabling AI.
        optional.put("ai_reply_enabled_v1",true);
        optional.put("ai_reply_auto_image_threshold_v1",0);
        optional.put("ai_reply_auto_suggestions_v1",false);
        optional.put("ai_reply_profile_v1","");
        optional.put("ai_reply_model_v1","");
        REQUIRED = Collections.unmodifiableMap(required);
        OPTIONAL = Collections.unmodifiableMap(optional);
    }
    static Object value(String key) {
        return REQUIRED.containsKey(key) ? REQUIRED.get(key) : OPTIONAL.get(key);
    }
    static boolean booleanValue(String key) {
        Object value = value(key);
        if (!(value instanceof Boolean)) throw new IllegalArgumentException("Unknown boolean setting: " + key);
        return (Boolean) value;
    }
    private ConfigDefaults() {}
}
