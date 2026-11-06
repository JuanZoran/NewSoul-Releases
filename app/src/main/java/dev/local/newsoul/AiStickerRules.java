package dev.local.newsoul;

import org.json.JSONObject;

/** Known host expression payloads only; descriptions are metadata, not inferred emotion. */
final class AiStickerRules {
    static final class Sticker {
        final JSONObject data;
        final String url;
        Sticker(JSONObject data,String url){this.data=data;this.url=url;}
    }
    static Sticker parse(int type,String cls,JSONObject body,String packTitle) throws Exception {
        JSONObject data=new JSONObject().put("source","Soul 表情消息元数据");
        String url=null;
        if(type==8&&cls.equals("cn.soulapp.imlib.msg.chat.ExpressionMsg")) {
            String title=AiContextRules.cleanText(packTitle==null?"":packTitle,120);
            if(!title.isEmpty()&&!title.equals("null"))data.put("packTitle",title);
            url=AiContextRules.ordinaryImageUrl(body.optString("imageUrl"));
        } else if(type==35&&cls.equals("cn.soulapp.imlib.msg.chat.JsonMsg")
                &&"cartoon_new_action".equals(body.optString("messageType"))) {
            String content=body.optString("content");if(content.length()>65536)return null;
            Object raw=new JSONObject(content).opt("emojiInfo");
            JSONObject emoji=raw instanceof JSONObject?(JSONObject)raw:raw instanceof String?new JSONObject((String)raw):null;
            if(emoji==null)return null;
            for(String key:new String[]{"emojiName","emojiContent"}) {
                if(emoji.opt(key) instanceof String) {
                    String text=AiContextRules.cleanText(emoji.getString(key),300);
                    if(!text.isEmpty())data.put(key,text);
                }
            }
            for(String key:new String[]{"smallDynamicEmojiUrl","emojiBigPicUrl","emojiSmallPicUrl"}) {
                url=AiContextRules.ordinaryImageUrl(emoji.optString(key));if(url!=null)break;
            }
        } else return null;
        data.put("imageAvailable",url!=null).put("visualNote","名称/描述仅为表情元数据；画面仅在选中并成功提供后可见，保留原文件，不能仅凭格式断定模型已完整理解动画");
        return new Sticker(data,url);
    }
}
