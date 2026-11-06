package dev.local.newsoul;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.json.*;
public final class AiStickerRulesTest {
    static int checks;
    static String expression="cn.soulapp.imlib.msg.chat.ExpressionMsg",json="cn.soulapp.imlib.msg.chat.JsonMsg";
    public static void main(String[] args) throws Exception {
        JSONObject body=new JSONObject().put("imageUrl","https://img.soulapp.cn/expression.gif").put("secret","do-not-export");
        AiStickerRules.Sticker s=AiStickerRules.parse(8,expression,body,"系列名");
        check(s!=null);check(s.url.endsWith(".gif"));check(s.data.getString("packTitle").equals("系列名"));
        check(!s.data.has("secret"));check(!s.data.has("imageUrl"));
        check(AiStickerRules.parse(8,json,body,"")==null);
        body.put("imageUrl","file:///private/photo.png");s=AiStickerRules.parse(8,expression,body,"");
        check(s.url==null);check(!s.data.getBoolean("imageAvailable"));
        JSONObject emoji=new JSONObject().put("emojiName","打招呼").put("emojiContent","挥挥手")
                .put("smallDynamicEmojiUrl","https://img.soulapp.cn/wave.gif")
                .put("emojiBigPicUrl","https://img.soulapp.cn/wave.png")
                .put("emojiVideoUrl","https://img.soulapp.cn/video.mp4");
        JSONObject payload=new JSONObject().put("messageType","cartoon_new_action")
                .put("content",new JSONObject().put("emojiInfo",emoji.toString()).toString());
        s=AiStickerRules.parse(35,json,payload,"");check(s.url.endsWith(".gif"));
        check(s.data.getString("emojiName").equals("打招呼"));check(!s.data.has("emojiVideoUrl"));
        payload.put("content",new JSONObject().put("emojiInfo",emoji).toString());
        check(AiStickerRules.parse(35,json,payload,"").url.endsWith(".gif"));
        payload.put("messageType","unknown");check(AiStickerRules.parse(35,json,payload,"")==null);
        byte[] gif=Files.readAllBytes(Path.of("tests/fixtures/ai-two-frame.gif"));
        javax.imageio.ImageReader reader=javax.imageio.ImageIO.getImageReadersByFormatName("gif").next();
        try(javax.imageio.stream.ImageInputStream stream=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(gif))) {
            reader.setInput(stream);check(reader.getNumImages(true)==2);check(reader.read(0).getRGB(0,0)!=reader.read(1).getRGB(0,0));
        } finally {reader.dispose();}
        AiReplyClient.ImageInput input=AiReplyClient.ImageInput.original(gif);
        check(input.url.startsWith("data:image/gif;base64,"));
        check(Arrays.equals(gif,Base64.getDecoder().decode(input.url.substring(input.url.indexOf(',')+1))));
        JSONObject req=AiReplyClient.createImageRequest("rules","context",List.of(input));
        String embedded=req.getJSONArray("messages").getJSONObject(1).getJSONArray("content")
                .getJSONObject(1).getJSONObject("image_url").getString("url");
        check(embedded.equals(input.url)); // the two-frame GIF remains byte-for-byte intact
        for(byte[] invalid:new byte[][]{null,new byte[2],new byte[13],new byte[8*1024*1024+1]}) {
            boolean rejected=false;try{AiReplyClient.ImageInput.original(invalid);}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected);
        }
        boolean rejected=false;
        try{AiReplyClient.ImageInput.remote("data:image/gif;base64,AAAA");}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected); // untrusted URL path still cannot inject arbitrary inline bytes
        System.out.println("AiStickerRulesTest: "+checks+" checks passed (original GIF request bytes; no model animation-quality claim)");
    }
    static void check(boolean condition){if(!condition)throw new AssertionError("check "+(checks+1));checks++;}
}
