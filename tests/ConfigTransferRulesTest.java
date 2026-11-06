package dev.local.newsoul;
import java.util.*;
import java.io.*;
import org.json.*;
public final class ConfigTransferRulesTest {
    static int checks;
    static void check(boolean ok){checks++;if(!ok)throw new AssertionError("check "+checks);}
    interface Attempt{void run()throws Exception;}
    static void rejects(Attempt a)throws Exception{boolean rejected=false;try{a.run();}catch(Exception e){rejected=true;}check(rejected);}
    static String config(JSONObject settings)throws Exception{return new JSONObject().put("format","newsoul-config").put("schemaVersion",1).put("settings",settings).toString();}
    public static void main(String[] args)throws Exception{
        Map<String,Object> source=new HashMap<>();
        source.put("post_remove_share_limit",false);source.put("chat_remove_social_score_limit",false);
        source.put("ad_feed",true);source.put("game_dice",false);
        source.put("feed_keywords_v1","广告\n推广");
        source.put("ai_reply_profile_v1","日常聊天");source.put("ai_reply_auto_image_threshold_v1",3);
        source.put("ai_reply_credential_v1","SECRET_DEEPSEEK");
        source.put("ai_reply_mimo_credential_v1","SECRET_MIMO");
        source.put("ai_reply_custom_credential_v1","SECRET_CUSTOM");
        source.put("ai_reply_custom_endpoint_v1","https://private.example.com/v1");
        source.put("ai_reply_custom_model_v1","private-model");
        source.put("future_secret","SECRET_FUTURE");
        String exported=ConfigTransferRules.exportConfig(source);
        check(!exported.contains("SECRET"));check(!exported.contains("credential"));check(!exported.contains("private.example"));check(!exported.contains("private-model"));
        Map<String,Object> incoming=ConfigTransferRules.parse(exported);
        check(Boolean.TRUE.equals(incoming.get("ad_feed")));check(Boolean.FALSE.equals(incoming.get("game_dice")));
        check("广告\n推广".equals(incoming.get("feed_keywords_v1")));
        check(Boolean.TRUE.equals(incoming.get("download_image_no_watermark")));
        check(Boolean.FALSE.equals(incoming.get("privacy_allow_download")));
        check(ConfigTransferRules.parse(ConfigTransferRules.exportConfig(incoming)).equals(incoming));
        Map<String,Object> clean=ConfigTransferRules.parse(ConfigTransferRules.exportConfig(Collections.emptyMap()));
        check(!clean.containsKey("ai_reply_profile_v1"));
        check(Boolean.FALSE.equals(clean.get("chat_auto_read_v1")));
        check(Boolean.TRUE.equals(ConfigTransferRules.parse(ConfigTransferRules.exportConfig(Map.of("chat_auto_read_v1",true))).get("chat_auto_read_v1")));
        check(!ConfigTransferRules.parse(config(new JSONObject().put("game_dice",false))).containsKey("chat_auto_read_v1"));
        rejects(()->ConfigTransferRules.parse(config(new JSONObject().put("chat_auto_read_v1","true"))));
        for(String key:List.of("post_remove_share_limit","chat_remove_social_score_limit")){
            check(Boolean.TRUE.equals(clean.get(key)));check(Boolean.FALSE.equals(incoming.get(key)));
        }
        for(String key: List.of("chat_block_read_receipt_v1", "chat_block_profile_notice_v1",
                "chat_block_close_friend_invite_v1", "picker_photo_limit_99_v1", "picker_video_limit_99_v1",
                "feed_filter_vip_v1", "foreground_clear_notifications_v1", "square_time_order_v1", "square_show_ip_location_v1", "square_show_registration_days_v1")) {
            check(!clean.containsKey(key));
            check(Boolean.TRUE.equals(ConfigTransferRules.parse(ConfigTransferRules.exportConfig(Map.of(key,true))).get(key)));
            rejects(() -> ConfigTransferRules.parse(config(new JSONObject().put(key,"true"))));
        }
        for (String prefix : List.of("square_ip_label_", "square_days_label_")) {
            check(!clean.containsKey(prefix + "color"));
            check(!clean.containsKey(prefix + "size"));
            for (String color : List.of("default", "mint", "blue", "orange", "#F08AAB", "#000000", "#ffffff"))
                check(color.equals(ConfigTransferRules.parse(ConfigTransferRules.exportConfig(Map.of(prefix + "color", color))).get(prefix + "color")));
            for (String size : List.of("default", "8", "10", "12", "14", "16", "24"))
                check(size.equals(ConfigTransferRules.parse(ConfigTransferRules.exportConfig(Map.of(prefix + "size", size))).get(prefix + "size")));
            rejects(() -> ConfigTransferRules.parse(config(new JSONObject().put(prefix + "color", "invalid"))));
            rejects(() -> ConfigTransferRules.parse(config(new JSONObject().put(prefix + "size", "100"))));
            rejects(() -> ConfigTransferRules.parse(config(new JSONObject().put(prefix + "size", "7"))));
            rejects(() -> ConfigTransferRules.parse(config(new JSONObject().put(prefix + "color", "#GGGGGG"))));
            rejects(() -> ConfigTransferRules.parse(config(new JSONObject().put(prefix + "color", "#00ffffff"))));
            rejects(() -> ConfigTransferRules.parse(config(new JSONObject().put(prefix + "size", 14))));
        }
        Map<String,Object> names = Map.of("feed_keywords_name_enabled_v1", true, "feed_keywords_name_v1", "昵称词\nAD");
        Map<String,Object> namesCopy = ConfigTransferRules.parse(ConfigTransferRules.exportConfig(names));
        for (String key : names.keySet()) check(names.get(key).equals(namesCopy.get(key)));
        check(!clean.containsKey("feed_keywords_name_enabled_v1"));
        check(!clean.containsKey("feed_keywords_name_v1"));
        rejects(() -> ConfigTransferRules.parse(config(new JSONObject().put("feed_keywords_name_enabled_v1", "true"))));
        rejects(() -> ConfigTransferRules.parse(config(new JSONObject().put("feed_keywords_name_v1", "x".repeat(101)))));
        Map<String,Object> automation = Map.of("account_auto_sign_v1", true, "account_auto_hide_v1", false,
            "feed_registration_days_v1", true, "feed_registration_min_v1", 7, "feed_registration_max_v1", 365);
        Map<String,Object> automationCopy = ConfigTransferRules.parse(ConfigTransferRules.exportConfig(automation));
        for (String key : automation.keySet()) check(automation.get(key).equals(automationCopy.get(key)));
        check(!clean.containsKey("account_auto_sign_v1"));
        check(!clean.containsKey("feed_registration_days_v1"));
        rejects(() -> ConfigTransferRules.parse(config(new JSONObject().put("feed_registration_min_v1", -1))));
        rejects(() -> ConfigTransferRules.parse(config(new JSONObject().put("account_auto_hide_v1", 1))));
        Map<String,Object> time = Map.of("chat_time_format_enabled_v1", true,
                "chat_time_format_v1", "MM-dd HH:mm:ss", "soul_block_update_popup_v1", true,
                "chat_time_every_message_v1", true);
        Map<String,Object> timeCopy = ConfigTransferRules.parse(ConfigTransferRules.exportConfig(time));
        for (String key : time.keySet()) check(time.get(key).equals(timeCopy.get(key)));
        check(!clean.containsKey("chat_time_format_v1"));
        check(!clean.containsKey("chat_time_every_message_v1"));
        check(!clean.containsKey("soul_block_update_popup_v1"));
        check(!clean.containsKey("soul_block_teenage_popup_v1"));
        check(!ConfigDefaults.booleanValue("soul_block_teenage_popup_v1"));
        Map<String,Object> teenage = Map.of("soul_block_teenage_popup_v1", true);
        check(Boolean.TRUE.equals(ConfigTransferRules.parse(ConfigTransferRules.exportConfig(teenage))
                .get("soul_block_teenage_popup_v1")));
        rejects(() -> ConfigTransferRules.parse(config(new JSONObject().put("soul_block_teenage_popup_v1", 1))));
        rejects(() -> ConfigTransferRules.parse(config(new JSONObject().put("chat_time_format_v1", "yyyy QQ"))));
        rejects(() -> ConfigTransferRules.parse(config(new JSONObject().put("chat_time_format_v1", ""))));
        rejects(() -> ConfigTransferRules.parse(config(new JSONObject().put("chat_time_format_enabled_v1", "true"))));
        rejects(() -> ConfigTransferRules.parse(config(new JSONObject().put("soul_block_update_popup_v1", 1))));
        Map<String,Object> disguise = new HashMap<>();
        disguise.put("profile_disguise_follow_enabled", true);
        disguise.put("profile_disguise_follow_mode", "delta");
        disguise.put("profile_disguise_follow_delta", -12);
        disguise.put("profile_disguise_follow_absolute", 800);
        disguise.put("profile_disguise_visitor_enabled", true);
        disguise.put("profile_disguise_visitor_mode", "delta");
        disguise.put("profile_disguise_visitor_delta", -8);
        disguise.put("profile_disguise_visitor_absolute", 999);
        disguise.put("profile_disguise_days_enabled", true);
        disguise.put("profile_disguise_days_mode", "absolute");
        disguise.put("profile_disguise_days_delta", -10);
        disguise.put("profile_disguise_days_absolute", 3650);
        Map<String,Object> disguiseCopy = ConfigTransferRules.parse(ConfigTransferRules.exportConfig(disguise));
        for (String key : disguise.keySet()) check(disguise.get(key).equals(disguiseCopy.get(key)));
        check(!clean.containsKey("profile_disguise_follow_enabled"));
        rejects(()->ConfigTransferRules.parse(config(new JSONObject().put("profile_disguise_follow_mode", "invalid"))));
        rejects(()->ConfigTransferRules.parse(config(new JSONObject().put("profile_disguise_follow_absolute", -1))));
        rejects(()->ConfigTransferRules.parse(config(new JSONObject().put("profile_disguise_follow_delta", 1.5))));
        String malicious=config(new JSONObject().put("ad_feed",false).put("ai_reply_credential_v1","REPLACE").put("ai_reply_mimo_credential_v1","REPLACE").put("ai_reply_custom_credential_v1","REPLACE").put("ai_reply_custom_endpoint_v1","REPLACE").put("ai_reply_custom_model_v1","REPLACE").put("unknown",true));
        Map<String,Object> updates=ConfigTransferRules.parse(malicious);
        check(updates.size()==1);Map<String,Object> target=new HashMap<>(source);target.putAll(updates);
        check("SECRET_DEEPSEEK".equals(target.get("ai_reply_credential_v1")));check("SECRET_MIMO".equals(target.get("ai_reply_mimo_credential_v1")));
        check("SECRET_CUSTOM".equals(target.get("ai_reply_custom_credential_v1")));
        check("https://private.example.com/v1".equals(target.get("ai_reply_custom_endpoint_v1")));
        check("private-model".equals(target.get("ai_reply_custom_model_v1")));
        check(Boolean.FALSE.equals(target.get("game_dice")));
        rejects(()->ConfigTransferRules.parse(config(new JSONObject().put("ad_feed","true"))));
        rejects(()->ConfigTransferRules.parse(config(new JSONObject().put("game_dice",JSONObject.NULL))));
        rejects(()->ConfigTransferRules.parse(config(new JSONObject().put("feed_filter_gender","INVALID"))));
        rejects(()->ConfigTransferRules.parse(config(new JSONObject().put("feed_filter_content_types_v1",0))));
        rejects(()->ConfigTransferRules.parse(config(new JSONObject().put("feed_filter_content_types_v1",8))));
        for(int mask=1;mask<=7;mask++) check(ConfigTransferRules.parse(config(new JSONObject().put("feed_filter_content_types_v1",mask))).get("feed_filter_content_types_v1").equals(mask));
        rejects(()->ConfigTransferRules.parse(config(new JSONObject().put("chat_recall_style","INVALID"))));
        rejects(()->ConfigTransferRules.parse(config(new JSONObject().put("ai_reply_auto_image_threshold_v1",1))));
        rejects(()->ConfigTransferRules.parse(config(new JSONObject().put("ai_reply_auto_image_threshold_v1",3.5))));
        rejects(()->ConfigTransferRules.parse(config(new JSONObject().put("feed_keywords_v1","x".repeat(101)))));
        rejects(()->ConfigTransferRules.parse(exported+"garbage"));
        rejects(()->ConfigTransferRules.parse("{}"));
        JSONObject newer=new JSONObject(exported).put("schemaVersion",2);rejects(()->ConfigTransferRules.parse(newer.toString()));
        rejects(()->ConfigTransferRules.parse(" ".repeat(ConfigTransferRules.MAX_BYTES+1)));
        rejects(()->ConfigTransferRules.parse(config(new JSONObject().put("credential","secret"))));
        byte[] limit=new byte[ConfigTransferRules.MAX_BYTES];
        check(ConfigTransferRules.readBounded(new ByteArrayInputStream(limit)).length==limit.length);
        rejects(()->ConfigTransferRules.readBounded(new ByteArrayInputStream(new byte[limit.length+1])));
        check(Boolean.FALSE.equals(ConfigTransferRules.parse(ConfigTransferRules.exportConfig(Map.of())).get("feed_filter_city_pages_v1")));
        check(Boolean.TRUE.equals(ConfigTransferRules.parse(ConfigTransferRules.exportConfig(Map.of("feed_filter_city_pages_v1", true))).get("feed_filter_city_pages_v1")));
        rejects(()->ConfigTransferRules.parse(config(new JSONObject().put("feed_filter_city_pages_v1", "true"))));
        check(Boolean.FALSE.equals(ConfigTransferRules.parse(ConfigTransferRules.exportConfig(Map.of())).get("square_like_city_pages_v1")));
        check(Boolean.TRUE.equals(ConfigTransferRules.parse(ConfigTransferRules.exportConfig(Map.of("square_like_city_pages_v1", true))).get("square_like_city_pages_v1")));
        rejects(()->ConfigTransferRules.parse(config(new JSONObject().put("square_like_city_pages_v1", "true"))));
        registrationRanges();
        sharedDefaults();
        System.out.println("ConfigTransferRules: "+checks+" checks passed");
    }
    private static final String RANGE_ENABLED="feed_registration_days_v1";
    private static final String RANGE_MIN="feed_registration_min_v1";
    private static final String RANGE_MAX="feed_registration_max_v1";
    static String rangeConfig(Map<String,?> values)throws Exception{return config(new JSONObject(values));}
    static void registrationRanges()throws Exception{
        Map<String,Object> invalid=Map.of(RANGE_ENABLED,true,RANGE_MIN,100,RANGE_MAX,10,"game_dice",false);
        rejects(()->ConfigTransferRules.parse(rangeConfig(invalid)));
        Map<String,Object> current=new HashMap<>(Map.of(RANGE_ENABLED,true,RANGE_MIN,7,RANGE_MAX,20,"game_dice",true));
        Map<String,Object> before=new HashMap<>(current);
        rejects(()->ConfigTransferRules.parse(rangeConfig(Map.of(RANGE_MIN,100,"game_dice",false)),current));
        rejects(()->ConfigTransferRules.parse(rangeConfig(Map.of(RANGE_MAX,5)),current));
        // Only enabling a previously disabled, contradictory range must also fail.
        Map<String,Object> dormant=Map.of(RANGE_ENABLED,false,RANGE_MIN,100,RANGE_MAX,10);
        rejects(()->ConfigTransferRules.parse(rangeConfig(Map.of(RANGE_ENABLED,true)),dormant));
        check(ConfigTransferRules.parse(rangeConfig(Map.of(RANGE_ENABLED,false)),invalid).size()==1);
        check(ConfigTransferRules.parse(rangeConfig(dormant)).get(RANGE_MIN).equals(100));
        check(ConfigTransferRules.parse(rangeConfig(Map.of(RANGE_MIN,200)),dormant).get(RANGE_MIN).equals(200));
        check(ConfigTransferRules.parse(rangeConfig(Map.of(RANGE_MAX,0)),current).get(RANGE_MAX).equals(0));
        check(ConfigTransferRules.parse(rangeConfig(Map.of(RANGE_MIN,20)),current).get(RANGE_MIN).equals(20));
        check(ConfigTransferRules.parse(rangeConfig(Map.of(RANGE_ENABLED,true,RANGE_MIN,0,RANGE_MAX,1))).get(RANGE_MIN).equals(0));
        check(ConfigTransferRules.parse(rangeConfig(Map.of(RANGE_ENABLED,true))).size()==1);
        check(ConfigTransferRules.parse(rangeConfig(Map.of(RANGE_MIN,15)),current).size()==1);
        check(current.equals(before));
        // The production writer gateway guards the entire batch, before the editor is obtained.
        int[] writes={0};
        ConfigTransferRules.ImportWriter writer=values->{writes[0]++;current.putAll(values);return true;};
        rejects(()->ConfigTransferRules.writeImport(Map.of(RANGE_MIN,100,"game_dice",false),current,writer));
        check(writes[0]==0);check(current.equals(before));
        Map<String,Object> updates=ConfigTransferRules.parse(rangeConfig(Map.of(RANGE_MIN,15,"game_dice",false)),current);
        // Recheck the latest preferences if the upper boundary changed during confirmation.
        current.put(RANGE_MAX,10);
        Map<String,Object> changed=new HashMap<>(current);
        rejects(()->ConfigTransferRules.writeImport(updates,current,writer));
        check(writes[0]==0);check(current.equals(changed));
        current.put(RANGE_MAX,20);
        check(ConfigTransferRules.writeImport(updates,current,writer));
        check(writes[0]==1);check(current.get(RANGE_MIN).equals(15));check(current.get(RANGE_MAX).equals(20));
        check(Boolean.FALSE.equals(current.get("game_dice")));
        check(ConfigTransferRules.writeImport(Map.of(RANGE_ENABLED,false,RANGE_MIN,100),current,writer));
        check(writes[0]==2);check(Boolean.FALSE.equals(current.get(RANGE_ENABLED)));
        try{ConfigTransferRules.parse(rangeConfig(invalid));throw new AssertionError();}
        catch(ConfigTransferRules.InvalidRangeException error){
            check(error.getMessage().contains("下限不能大于上限"));
            check(error.getMessage().contains("重新导入"));
            check(!error.getMessage().endsWith("。"));
        }
    }
    static void sharedDefaults()throws Exception{
        Map<String,Object> exported=ConfigTransferRules.parse(ConfigTransferRules.exportConfig(Map.of()));
        for(Map.Entry<String,Object> item:ConfigDefaults.REQUIRED.entrySet()){
            check(item.getValue().equals(exported.get(item.getKey())));
            if(item.getValue() instanceof Boolean)check(ConfigDefaults.booleanValue(item.getKey())==(Boolean)item.getValue());
        }
        for(Map.Entry<String,Object> item:ConfigDefaults.OPTIONAL.entrySet()){
            check(!exported.containsKey(item.getKey()));
            if(item.getValue() instanceof Boolean)check(ConfigDefaults.booleanValue(item.getKey())==(Boolean)item.getValue());
        }
        // Literal pre-refactor defaults protect persisted switches in public and internal menus.
        for(String key:("block_splash game_dice game_finger chat_anti_recall chat_own_recall_no_time_limit "
                +"chat_remove_gift_gate chat_remove_bell_gift_gate post_remove_share_limit chat_remove_social_score_limit "
                +"chat_hide_typing match_mute_background_music download_image_no_watermark download_video_no_watermark "
                +"square_visible_like page_log startup_toast ai_reply_enabled_v1").split(" "))
            check(ConfigDefaults.booleanValue(key));
        for(String key:("ad_feed ad_detail ad_video ad_tag ad_tag_web ad_search ad_group ad_planet ad_me "
                +"ad_conversation ad_envelope ad_notice ad_chat_tools ad_float ad_interstitial ad_reward_chat "
                +"ad_reward_drama ad_reward_web privacy_allow_download privacy_allow_screenshot privacy_allow_repeat "
                +"feed_filter_enabled feed_filter_keep_unknown feed_keywords_enabled_v1 square_like_all_loaded "
                +"square_auto_like chat_auto_read_v1 feed_filter_city_pages_v1 square_like_city_pages_v1 "
                +"chat_time_format_enabled_v1 chat_time_every_message_v1 soul_block_update_popup_v1 "
                +"chat_block_read_receipt_v1 chat_block_profile_notice_v1 chat_block_close_friend_invite_v1 "
                +"picker_photo_limit_99_v1 picker_video_limit_99_v1 feed_filter_vip_v1 "
                +"foreground_clear_notifications_v1 square_time_order_v1 account_auto_sign_v1 account_auto_hide_v1 "
                +"feed_registration_days_v1 profile_disguise_follow_enabled profile_disguise_followed_enabled "
                +"profile_disguise_visitor_enabled profile_disguise_days_enabled").split(" "))
            check(!ConfigDefaults.booleanValue(key));
        check(ConfigDefaults.value(RANGE_MIN).equals(1));check(ConfigDefaults.value(RANGE_MAX).equals(0));
        rejects(()->ConfigDefaults.booleanValue("unknown_switch"));
        rejects(()->ConfigDefaults.REQUIRED.put("game_dice",false));
        rejects(()->ConfigDefaults.OPTIONAL.put(RANGE_MIN,10));
    }

}
