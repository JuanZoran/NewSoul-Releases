package dev.local.newsoul;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Select whole source rows before sending; character limits are not token estimates. */
final class AiRequestContext {
    static final int MAX_INPUT_CHARS=48_000;
    private static final int REWRITE_MESSAGES=12, OPENING_MESSAGES=8;
    private static final String TOO_LARGE="AI 请求资料过长，请缩短个人简介、本轮要求或草稿，或减少所选图片后重试";

    private AiRequestContext() { }

    static JSONObject prepare(String system,JSONObject original) throws JSONException {
        if(original==null||system==null)throw new IllegalArgumentException("聊天资料未准备好，请更新资料后重试");
        JSONObject data=new JSONObject(original.toString());
        JSONObject history=data.optJSONObject("history");
        JSONObject interaction=history==null?null:history.optJSONObject("interaction");
        boolean rewrite="REWRITE".equals(data.optString("writing_mode"));
        boolean opening=!rewrite&&interaction!=null&&"opening".equals(interaction.optString("state"));
        String scene=rewrite?"rewrite":opening?"opening":"reply";
        JSONArray messages=history==null?null:history.optJSONArray("messages");
        JSONObject section=data.optJSONObject("homepagePosts");
        JSONObject postsData=section==null?null:section.optJSONObject("data");
        JSONArray posts=postsData==null?null:postsData.optJSONArray("posts");
        Set<String> selected=selectedImages(data);
        int messagesBefore=size(messages),postsBefore=size(posts);
        // Older dialogue may contain boundaries or agreements; don't discard it merely to hit a count.
        int messageCap=rewrite?REWRITE_MESSAGES:opening?OPENING_MESSAGES:Integer.MAX_VALUE;
        int postCap=rewrite?0:opening?6:2;
        // Images explicitly selected by the user keep their owning source, even beyond the scene cap.
        while(size(posts)>postCap&&removePost(posts,selected)) { }
        while(size(messages)>messageCap&&removeMessage(messages,selected,1,true)) { }
        updateCounts(history,postsData,messagesBefore,postsBefore);
        reconcile(history);
        JSONObject selection=new JSONObject().put("scene",scene).put("characterLimit",MAX_INPUT_CHARS)
                .put("limitUnit","UTF-16 characters, not tokens")
                .put("historyOmittedCount",messagesBefore-size(messages))
                .put("homepagePostsOmittedCount",postsBefore-size(posts));
        data.put("requestSelection",selection);
        // Prefer the homepage for openings and the recent conversation for replies/rewrites.
        while(inputChars(system,data.toString())>MAX_INPUT_CHARS) {
            boolean removed=opening
                    ? removeMessage(messages,selected,1,true)||removePost(posts,selected)
                    : removePost(posts,selected)||removeMessage(messages,selected,4,true);
            if(!removed)removed=removeQuotedMessage(history,selected);
            if(!removed)removed=removeMessage(messages,selected,1,false);
            if(!removed)throw new IllegalArgumentException(TOO_LARGE);
            reconcile(history);
            updateCounts(history,postsData,messagesBefore,postsBefore);
            selection.put("historyOmittedCount",messagesBefore-size(messages))
                    .put("homepagePostsOmittedCount",postsBefore-size(posts));
        }
        updateCounts(history,postsData,messagesBefore,postsBefore);
        reconcile(history);
        requireInputBudget(system,data.toString());
        return data;
    }

    static long inputChars(String system,String context) {
        return (long)(system==null?0:system.length())+(context==null?0:context.length())
                +AiReplyClient.JSON_OUTPUT_SUFFIX.length();
    }

    static void requireInputBudget(String system,String context) {
        if(inputChars(system,context)>MAX_INPUT_CHARS)throw new IllegalArgumentException(TOO_LARGE);
    }

    private static int size(JSONArray array) { return array==null?0:array.length(); }

    private static boolean removePost(JSONArray posts,Set<String> selected) {
        for(int i=size(posts)-1;i>=0;i--)if(!hasSelectedMedia(posts.opt(i),selected)) {
            posts.remove(i);return true;
        }
        return false;
    }

    private static boolean removeMessage(JSONArray messages,Set<String> selected,int recent,boolean keepQuotes) {
        Set<String> targets=new HashSet<>();
        int latestInterpersonal=-1;
        for(int i=size(messages)-1;i>=0;i--) {
            JSONObject row=messages.optJSONObject(i);
            if(row!=null&&!"system".equals(row.optString("sender"))) {latestInterpersonal=i;break;}
        }
        if(keepQuotes)for(int i=0;i<size(messages);i++) {
            JSONObject row=messages.optJSONObject(i);
            JSONObject quote=row==null?null:row.optJSONObject("quote");
            if(quote!=null&&"resolved".equals(quote.optString("status")))targets.add(quote.optString("targetId"));
        }
        for(int i=0;i<size(messages)-recent;i++) {
            JSONObject row=messages.optJSONObject(i);
            if(i!=latestInterpersonal&&row!=null&&!targets.contains(row.optString("id"))&&!hasSelectedMedia(row,selected)) {
                messages.remove(i);return true;
            }
        }
        return false;
    }

    private static boolean removeQuotedMessage(JSONObject history,Set<String> selected) {
        JSONArray quoted=history==null?null:history.optJSONArray("quotedMessages");
        for(int i=size(quoted)-1;i>=0;i--)if(!hasSelectedMedia(quoted.opt(i),selected)) {
            quoted.remove(i);return true;
        }
        return false;
    }

    private static Set<String> selectedImages(JSONObject data) {
        Set<String> selected=new HashSet<>();
        JSONArray images=data.optJSONArray("selected_images");
        for(int i=0;i<size(images);i++) {
            JSONObject row=images.optJSONObject(i);
            if(row!=null&&!row.optString("id").isEmpty())selected.add(row.optString("id"));
        }
        return selected;
    }

    private static boolean hasSelectedMedia(Object value,Set<String> selected) {
        if(selected.isEmpty())return false;
        if(value instanceof JSONArray) {
            JSONArray array=(JSONArray)value;
            for(int i=0;i<array.length();i++)if(hasSelectedMedia(array.opt(i),selected))return true;
        } else if(value instanceof JSONObject) {
            JSONObject object=(JSONObject)value;
            JSONArray ids=object.optJSONArray("mediaIds");
            for(int i=0;i<size(ids);i++)if(selected.contains(ids.optString(i)))return true;
            Iterator<String> keys=object.keys();
            while(keys.hasNext())if(hasSelectedMedia(object.opt(keys.next()),selected))return true;
        }
        return false;
    }

    private static void updateCounts(JSONObject history,JSONObject postsData,int messagesBefore,int postsBefore) throws JSONException {
        if(history!=null) {
            int count=size(history.optJSONArray("messages")),omitted=messagesBefore-count;
            history.put("includedCount",count);
            if(omitted>0) {
                history.put("truncated",true).put("requestOmittedCount",omitted);
                history.put("requestSelectionNote","按本轮场景提供部分记录；省略不表示从未互动，完整本地统计以 interaction 为准");
            }
        }
        if(postsData!=null) {
            int count=size(postsData.optJSONArray("posts"));
            postsData.put("includedCount",count);
            if(postsBefore>count)postsData.put("requestOmittedCount",postsBefore-count);
        }
    }

    private static void reconcile(JSONObject history) throws JSONException {
        if(history==null)return;
        AiMessageContextRules.reconcileQuotes(history);
        JSONArray messages=history.optJSONArray("messages");
        Set<String> present=new HashSet<>();
        for(int i=0;i<size(messages);i++) {
            JSONObject row=messages.optJSONObject(i);
            if(row!=null)present.add(row.optString("id"));
        }
        for(int i=0;i<size(messages);i++) {
            JSONObject row=messages.optJSONObject(i);
            JSONObject timing=row==null?null:row.optJSONObject("timing");
            if(timing!=null&&timing.has("previousMessageId")&&!present.contains(timing.optString("previousMessageId"))) {
                timing.remove("previousMessageId");
                timing.remove("gapFromPreviousInterpersonalMs");
                timing.remove("gapFromOppositeMessageMs");
            }
        }
    }
}
