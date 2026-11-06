package dev.local.newsoul;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

/** Allowlisted Soul post-card formats; raw IDs/URLs stay local, never arbitrary JSON export. */
final class AiSharedPostRules {
    static final class Card {
        final long postId;
        final JSONObject data;
        final List<String> images;
        Card(long id,JSONObject data,List<String> images){postId=id;this.data=data;this.images=images;}
        boolean hasText(){return !data.optString("content").isEmpty()||!data.optString("contentTitle").isEmpty()
                ||!data.optString("summary").isEmpty();}
    }
    static Card card(int type,String cls,JSONObject body,String discriminator) throws Exception {
        if(type==6&&cls.equals("cn.soulapp.imlib.msg.chat.RePostMsg")) {
            JSONObject data=new JSONObject().put("source","聊天帖子卡片快照");
            copy(body,data,"content",3000);copy(body,data,"userSignature",120);copy(body,data,"type",40);
            List<String> images=new ArrayList<>();
            if("IMAGE".equals(body.optString("type")))addImage(images,body.optString("url"));
            return new Card(id(body.opt("postId")),data,images);
        }
        if(type==32&&cls.equals("cn.soulapp.imlib.msg.chat.StringMsg")&&"POST".equals(discriminator)
                ||type==35&&cls.equals("cn.soulapp.imlib.msg.chat.JsonMsg")
                &&"share_music_post".equals(body.optString("messageType"))) {
            String raw=body.optString("content","");
            if(raw.isEmpty()||raw.length()>256*1024)return null;
            JSONObject post=new JSONObject(raw);
            if(restricted(post))return new Card(0,new JSONObject().put("source","聊天帖子卡片").put("status","restricted"),new ArrayList<>());
            return post(post,"聊天帖子卡片快照");
        }
        return null;
    }
    static Card detail(JSONObject post,long expected) throws Exception {
        if(expected<=0||id(post.opt("id"))!=expected)throw new IllegalArgumentException("Post identity mismatch");
        String visibility=post.optString("visibility");
        if(restricted(post)||!(visibility.equals("PUBLIC")||visibility.equals("HOMEPAGE")))
            return new Card(0,new JSONObject().put("status","restricted"),new ArrayList<>());
        return post(post,"Soul 帖子详情（采集时版本，可能与分享时不同）");
    }
    private static boolean restricted(JSONObject post) {
        String visibility=post.optString("visibility","");
        return Boolean.FALSE.equals(post.opt("canWatch"))||post.optBoolean("is24H",false)
                ||!post.optString("privacyTag","").isEmpty()
                ||post.optJSONArray("privacyTagModelList")!=null&&post.optJSONArray("privacyTagModelList").length()>0
                ||!visibility.isEmpty()&&!visibility.equals("PUBLIC")&&!visibility.equals("HOMEPAGE");
    }
    private static Card post(JSONObject post,String source) throws Exception {
        JSONObject data=new JSONObject().put("source",source);
        copy(post,data,"content",6000);copy(post,data,"contentTitle",300);copy(post,data,"summary",1000);
        copy(post,data,"type",40);
        List<String> images=new ArrayList<>();
        JSONArray attachments=post.optJSONArray("attachments");
        if(attachments!=null)for(int i=0;i<attachments.length()&&images.size()<24;i++){
            JSONObject attachment=attachments.optJSONObject(i);
            if(attachment!=null&&"IMAGE".equals(attachment.optString("type"))&&!attachment.optBoolean("hasFogFlag",false))
                addImage(images,attachment.optString("fileUrl"));
        }
        data.put("imageCount",images.size());
        return new Card(id(post.opt("id")),data,images);
    }
    private static long id(Object value) {
        if(value==null||!value.toString().matches("[1-9][0-9]{0,18}"))return 0;
        try{return Long.parseLong(value.toString());}catch(NumberFormatException ignored){return 0;}
    }
    private static void copy(JSONObject from,JSONObject to,String key,int limit) throws Exception {
        Object raw=from.opt(key);if(!(raw instanceof String))return;
        String text=((String)raw).replace("<officialTag>","").replace("</officialTag>","");
        String clean=AiContextRules.cleanText(text,limit);
        if(!clean.isEmpty())to.put(key,clean);
        if(text.length()>limit)to.put(key+"Truncated",true);
    }
    private static void addImage(List<String> images,String raw){
        String url=AiContextRules.ordinaryImageUrl(raw);
        if(url!=null&&!images.contains(url))images.add(url);
    }
}
