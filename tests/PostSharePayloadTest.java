package dev.local.newsoul;

import java.lang.reflect.*;
import java.util.*;

/** Snapshot immutability and native dispatch boundary; no host, account, or network. */
public class PostSharePayloadTest {
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    public static class Post {
        public String postIdEcpt, content;
        public List<String> attachments = new ArrayList<>();
    }
    public static String encode(Object value) {
        Post p = (Post) value;
        return p.postIdEcpt + "\n" + p.content + "\n" + String.join(",", p.attachments);
    }
    public static Object decode(String json, Class<?> ignored) {
        String[] fields = json.split("\n", -1);
        Post p = new Post(); p.postIdEcpt = fields[0]; p.content = fields[1];
        if (!fields[2].isEmpty()) p.attachments.addAll(Arrays.asList(fields[2].split(",")));
        return p;
    }
    public static class Sender {
        final List<Post> posts = new ArrayList<>(); final List<String> targets = new ArrayList<>();
        public void send(Post post, String target) { posts.add(post); targets.add(target); }
    }
    public static void main(String[] args) throws Exception {
        Sender sender = new Sender();
        PostSharePayload.Factory factory = new PostSharePayload.Factory(Post.class,
                PostSharePayloadTest.class.getMethod("encode", Object.class),
                PostSharePayloadTest.class.getMethod("decode", String.class, Class.class),
                Sender.class.getMethod("send", Post.class, String.class), sender);
        Post original = new Post(); original.postIdEcpt="post-id"; original.content="original"; original.attachments.add("video");
        PostSharePayload frozen = factory.snapshot(original);
        check(sender.posts.isEmpty());
        original.content="edited"; original.attachments.clear(); original.postIdEcpt="another-id";
        MessageForwardRequest request = new MessageForwardRequest("me", "", MessageForwardRequest.POST, frozen, 100);
        check(request.targetAllowed("author",0)); check(!request.targetAllowed("me",0));
        check(!request.targetAllowed("group",1)); check(!request.targetAllowed("anonymous",2));
        check(request.claim("other",List.of("a"),100)==null);
        check(request.claim("me",List.of("a","a"),100)==null);
        List<String> targets = request.claim("me",List.of("a","b"),100);
        check(targets.equals(List.of("a","b")));
        Method send = MessageForwardControls.class.getDeclaredMethod("send", MessageForwardRequest.class,String.class);
        send.setAccessible(true); MessageForwardControls controls = new MessageForwardControls();
        for(String target:targets) send.invoke(controls,request,target);
        check(sender.targets.equals(List.of("a","b"))); check(sender.posts.size()==2);
        Post a=sender.posts.get(0), b=sender.posts.get(1);
        check(a!=b && a!=original && a.attachments!=b.attachments);
        check(a.postIdEcpt.equals("post-id") && a.content.equals("original") && a.attachments.equals(List.of("video")));
        a.attachments.clear(); check(b.attachments.equals(List.of("video")));
        check(request.claim("me",targets,100)==null);
        check(!request.live("me",100));
        try { factory.snapshot(originalWithNoId()); throw new AssertionError(); }
        catch(IllegalArgumentException expected) {checks++;}
        try { new MessageForwardRequest("me","",32,"wrong body",100); throw new AssertionError(); }
        catch(IllegalArgumentException expected) {checks++;}
        try { new MessageForwardRequest("me","",4,"video",100); throw new AssertionError(); }
        catch(IllegalArgumentException expected) {checks++;}
        check(PostBatchShareControls.shareIcon(new String[]{"复制链接","转发","分享"})==1);
        check(PostBatchShareControls.shareIcon(new String[]{"复制链接","分享"})==1);
        check(PostBatchShareControls.shareIcon(new String[]{"复制链接"})==-1);
        check(PostBatchShareControls.shareIcon(null)==-1);
        check(PostBatchShareControls.shareIcon(new String[]{"删帖", "转发给souler", "复制链接"})==1);
        System.out.println("PostSharePayload: " + checks + " checks passed");
    }
    static Post originalWithNoId() { return new Post(); }
}
