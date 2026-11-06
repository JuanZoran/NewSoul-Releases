package dev.local.newsoul;

import java.util.List;
import org.json.JSONObject;
import org.json.JSONArray;

public final class AiMediaContextTest {
    private static int count;
    private static final String REPOST="cn.soulapp.imlib.msg.chat.RePostMsg";
    private static final String STRING="cn.soulapp.imlib.msg.chat.StringMsg";
    private static final String JSON="cn.soulapp.imlib.msg.chat.JsonMsg";
    public static void main(String[] args) throws Exception {
        check(!AiImageSelectionRules.autoSelect(0,5));
        check(AiImageSelectionRules.autoSelect(1,2));
        check(!AiImageSelectionRules.autoSelect(2,2));
        check(AiImageSelectionRules.autoSelect(4,5));
        check(!AiImageSelectionRules.autoSelect(5,5));
        check(!AiImageSelectionRules.autoSelect(2,0));
        check(!AiImageSelectionRules.autoSelect(2,6));
        check(!AiImageSelectionRules.validThreshold(1));
        check(!AiImageSelectionRules.validThreshold(-1));
        JSONObject body=new JSONObject().put("postId",123).put("content","<officialTag>周末散步</officialTag>")
                .put("type","IMAGE").put("url","https://img.soulapp.cn/photo.jpg").put("secret","do-not-export");
        AiSharedPostRules.Card card=AiSharedPostRules.card(6,REPOST,body,"");
        check(card.postId==123);check(card.hasText());check(card.data.getString("content").equals("周末散步"));
        check(card.images.size()==1);check(!card.data.has("postId"));check(!card.data.has("secret"));check(!card.data.has("url"));
        check(AiSharedPostRules.card(6,STRING,body,"")==null);
        JSONObject post=post();
        JSONObject serialized=new JSONObject().put("content",post.toString());
        card=AiSharedPostRules.card(32,STRING,serialized,"POST");
        check(card.postId==123);check(card.hasText());check(card.images.size()==1);
        check(AiSharedPostRules.card(32,STRING,serialized,"OTHER")==null);
        serialized.put("messageType","other_type");check(AiSharedPostRules.card(35,JSON,serialized,"")==null);
        serialized.put("messageType","share_music_post");check(AiSharedPostRules.card(35,JSON,serialized,"").postId==123);
        check(AiSharedPostRules.detail(post,123).hasText());
        boolean mismatch=false;
        try {AiSharedPostRules.detail(post,456);}catch(IllegalArgumentException expected){mismatch=true;}check(mismatch);
        for(String visibility:new String[]{"PRIVATE","", "UNKNOWN"}){
            JSONObject restricted=post();restricted.put("visibility",visibility);
            check(AiSharedPostRules.detail(restricted,123).images.isEmpty());
            check("restricted".equals(AiSharedPostRules.detail(restricted,123).data.getString("status")));
        }
        for(String field:new String[]{"canWatch","is24H","privacyTag","privacyTagModelList"}){
            JSONObject restricted=post();restricted.put(field,field.equals("canWatch")?false:field.equals("is24H")?true:
                    field.equals("privacyTag")?"limited":new JSONArray().put("limited"));
            check(!AiSharedPostRules.detail(restricted,123).hasText());
        }
        JSONObject longText=post();longText.put("content","字".repeat(6500));
        card=AiSharedPostRules.detail(longText,123);check(card.data.getString("content").length()==6000);
        check(card.data.getBoolean("contentTruncated"));
        JSONObject request=AiReplyClient.createRequest("rules","{\"selected_images\":[{\"id\":\"m_a_image_0\"}]}",card.images);
        JSONArray blocks=request.getJSONArray("messages").getJSONObject(1).getJSONArray("content");
        check(blocks.length()==2);check("image_url".equals(blocks.getJSONObject(1).getString("type")));
        // Omission remains omission; metadata alone never creates an image block.
        request=AiReplyClient.createRequest("rules","{\"mediaIds\":[\"m_a_image_0\"]}",List.of());
        check(request.getJSONArray("messages").getJSONObject(1).get("content") instanceof String);
        System.out.println("AiMediaContextTest: "+count+" checks passed (offline; post API not device verified)");
    }
    private static JSONObject post() throws Exception {
        return new JSONObject().put("id",123).put("visibility","PUBLIC").put("canWatch",true)
                .put("content","周末逛公园").put("attachments",new JSONArray()
                    .put(new JSONObject().put("type","IMAGE").put("fileUrl","https://img.soulapp.cn/photo.jpg"))
                    .put(new JSONObject().put("type","IMAGE").put("fileUrl","https://img.soulapp.cn/photo.jpg"))
                    .put(new JSONObject().put("type","IMAGE").put("fileUrl","https://img.soulapp.cn/fog.jpg").put("hasFogFlag",true))
                    .put(new JSONObject().put("type","VIDEO").put("fileUrl","https://img.soulapp.cn/video.mp4"))
                    .put(new JSONObject().put("type","IMAGE").put("fileUrl","https://example.com/image.jpg")));
    }
    private static void check(boolean condition){if(!condition)throw new AssertionError("check "+(count+1));count++;}
}
