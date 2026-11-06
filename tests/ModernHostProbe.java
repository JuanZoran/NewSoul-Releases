package dev.local.newsoul;

import dalvik.system.PathClassLoader;
import java.lang.reflect.Method;

/** Resolve the production bindings in ART against the supplied APK; never invoke host methods. */
public final class ModernHostProbe {
    private static int passed, failed;
    interface Check { void run() throws Exception; }
    static void check(String label, Check check) {
        try { check.run(); passed++; System.out.println("PASS " + label); }
        catch (Throwable error) { failed++; System.out.println("FAIL " + label + ": " + error); }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("host-apk");
        ClassLoader loader = new PathClassLoader(args[0], ModernHostProbe.class.getClassLoader());
        check("chat identity", () -> new HostBindings.Chat(loader));
        check("chat manager", () -> HostBindings.chatManager(loader));
        check("conversation lookup", () -> HostBindings.conversationFor(loader));
        check("account identity", () -> HostBindings.account(loader));
        check("encrypted account", () -> new HostBindings.Account(loader));
        check("HTTP GET", () -> new HostBindings.Transport(loader));
        check("HTTP POST", () -> new HostBindings.Post(loader));
        check("gift response", () -> new ChatGateResponse(loader));
        check("teenager popup response", () -> new TeenagePopupResponse(loader));
        check("current fragment", () -> HostBindings.currentFragment(loader));
        check("keyboard", () -> HostBindings.keyboard(loader));
        check("text sender", () -> HostBindings.textSend(loader));
        check("voice sender", () -> HostBindings.voiceSend(loader));
        check("dice sender", () -> HostBindings.gameSend(loader, true));
        check("finger sender", () -> HostBindings.gameSend(loader, false));
        check("game model", () -> { HostBindings.gameResult(loader); HostBindings.gameNumber(loader); });
        check("chat lifecycle", () -> { HostBindings.lifecycle(loader, true); HostBindings.lifecycle(loader, false); });
        check("command sender", () -> { HostBindings.commandSend(loader, true); HostBindings.commandSend(loader, false); });
        check("image download", () -> HostMediaBindings.image(loader));
        check("video download", () -> HostMediaBindings.video(loader));
        Class<?> im = loader.loadClass(HostBindings.MESSAGE);
        String[] names = {"getFrom", "getTo", "getMsgId", "getMsgType", "getChatMessage", "isAnonymityChat",
                "getIsAck", "getServerTime", "getLocalTime", "getTransCmd", "getMsgStatus"};
        Class<?>[] returns = {String.class, String.class, String.class, int.class,
                loader.loadClass("cn.soulapp.imlib.msg.chat.ChatMessage"), boolean.class, int.class,
                long.class, long.class, loader.loadClass("cn.soulapp.imlib.msg.transcmd.TransCmdMsg"), int.class};
        for (int i = 0; i < names.length; i++) {
            String name = names[i]; Class<?> result = returns[i];
            check("message " + name, () -> {
                Method method = HostBindings.messageMethod(im, name);
                if (method.getReturnType() != result) throw new AssertionError("return type");
            });
        }
        Class<?> post = loader.loadClass("cn.soulapp.android.square.post.bean.Post");
        for (String name : new String[]{"hasDeclare", "getAdResponse", "showName"})
            check("post " + name, () -> HostBindings.postMethod(post, name));
        check("profile number formats", () -> {
            HostBindings.profileNumberFormatter(loader, false); HostBindings.profileNumberFormatter(loader, true);
        });
        check("official suggestion entry", () -> HostBindings.officialSuggestionEntry(loader.loadClass(HostBindings.FRAGMENT)));
        check("chat menu", () -> new HostBindings.ChatMenu(loader, HostSymbols.chatPresenter(loader)));
        check("bubble long click", () -> HostBindings.bubbleLongClick(loader.loadClass(HostBindings.FRAGMENT), im, android.view.View.class, false));
        check("square feed", () -> new HostBindings.SquareFeed(loader, false));
        check("city feed", () -> new HostBindings.CityFeed(loader, post, false));
        check("welfare", () -> new HostBindings.Welfare(loader));
        check("share sheet", () -> new HostShareBindings(loader));
        check("ad loaders", () -> {
            HostAdBindings ads = new HostAdBindings(loader);
            if (ads.legacy) throw new AssertionError("modern host expected");
            for (String[] spec : ads.loaders) ads.validateLoader(loader, spec);
        });
        check("outgoing decoration", () -> new HostMessageDecoration(loader));
        System.out.println("RESULT passed=" + passed + " failed=" + failed);
        if (failed != 0) System.exit(1);
    }
}
