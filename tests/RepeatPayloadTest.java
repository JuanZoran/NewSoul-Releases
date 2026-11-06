package dev.local.newsoul;

import cn.soulapp.imlib.msg.chat.*;
import java.util.ArrayList;
import java.util.List;

/** Test doubles expose the verified host payload API, without a phone or network. */
public final class RepeatPayloadTest {
    private static int checks;
    private static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("check " + checks); }
    private static Object copy(int type, Object body) throws Exception {
        ChatMessage chat = new ChatMessage(); chat.type = type; chat.body = body;
        return ChatRepeatControls.copyBody(chat);
    }
    private static void reject(int type, Object body) throws Exception {
        try { copy(type, body); throw new AssertionError("accepted invalid body"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    public static void main(String[] args) throws Exception {
        TextMsg text = new TextMsg("  原文\n[微笑]😊  ");
        TextMsg repeated = (TextMsg) copy(1, text);
        check(repeated != text && repeated.text.equals(text.text));
        reject(1, new TextMsg(" ")); reject(1, new TextMsg("a".repeat(501)));
        ImgMsg image = new ImgMsg(); image.imageUrl="https://cdn.example/img.gif";
        image.imageW=320; image.imageH=240; image.imageLocalPath="/private/original";
        ImgMsg newImage = (ImgMsg) copy(2, image);
        check(newImage != image && newImage.imageUrl.equals(image.imageUrl));
        check(newImage.imageW==320 && newImage.imageH==240 && newImage.imageLocalPath==null);
        newImage.imageW=99; check(image.imageW==320);
        ImgMsgs images = new ImgMsgs(new ArrayList<>(List.of(image,image)));
        ImgMsgs newImages = (ImgMsgs) copy(3, images);
        check(newImages != images && newImages.imgMsgList != images.imgMsgList);
        check(newImages.imgMsgList.size()==2 && newImages.imgMsgList.get(0)!=image);
        newImages.imgMsgList.clear(); check(images.imgMsgList.size()==2);
        reject(3,new ImgMsgs(List.of())); reject(2,text); reject(8,image);
        AudioMsg audio=new AudioMsg("https://cdn.example/voice.mp3","/private/voice",7,"private transcript");
        AudioMsg newAudio=(AudioMsg)copy(5,audio);
        check(newAudio!=audio && newAudio.url.equals(audio.url) && newAudio.duration==7);
        check(newAudio.localUrl.isEmpty() && newAudio.word.isEmpty());
        check(audio.localUrl.equals("/private/voice") && audio.word.equals("private transcript"));
        reject(5,new AudioMsg("file:///a","",3,""));
        reject(5,new AudioMsg(audio.url,"",0,""));
        VideoMsg video = new VideoMsg("https://cdn.example/video.mp4", "/private/video", 720, 1280);
        VideoMsg copiedVideo = (VideoMsg) copy(4, video);
        check(copiedVideo != video && copiedVideo.url.equals(video.url));
        check(copiedVideo.localUrl.isEmpty() && copiedVideo.width == 720 && copiedVideo.height == 1280);
        copiedVideo.width = 1; check(video.width == 720 && video.localUrl.equals("/private/video"));
        check(((VideoMsg) copy(4, new VideoMsg(video.url, "", 0, 0))).width == 0);
        reject(4, new VideoMsg("file:///video", "private", 1, 1));
        reject(4, new VideoMsg(video.url, "", -1, 1));
        reject(4, image); reject(4, null);
        image.imageUrl=""; reject(2,image); reject(1,null);
        System.out.println("RepeatPayload: " + checks + " checks passed");
    }
}
