package dev.local.newsoul;

import dalvik.system.PathClassLoader;
import java.lang.reflect.Method;

/** Resolve the production bindings in ART against the supplied APK; never invoke host methods. */
public final class LegacyHostProbe {
    private static int passed, failed;
    interface Check { void run() throws Exception; }
    static void check(String label, Check check) {
        try { check.run(); passed++; System.out.println("PASS " + label); }
        catch (Throwable error) { failed++; System.out.println("FAIL " + label + ": " + error); }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("host-apk");
        ClassLoader loader = new PathClassLoader(args[0], LegacyHostProbe.class.getClassLoader());
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
        check("legacy profile counts", () -> {
            Class<?> owner = loader.loadClass("cn.soulapp.android.component.home.meta.adapter.MetaUserProfileProvider");
            Class<?> holder = loader.loadClass(owner.getName() + "$a");
            Class<?> user = loader.loadClass("com.soul.component.componentlib.service.user.bean.User");
            owner.getDeclaredMethod("d1", user, holder); owner.getDeclaredMethod("k1", user, holder);
            for (String name : new String[]{"Y", "a0", "f0", "g0", "h0", "i0", "I"})
                if (holder.getMethod(name).getReturnType() != android.widget.TextView.class) throw new AssertionError(name);
            if (owner.getDeclaredField("b").getType() != int.class) throw new AssertionError("profile type");
            loader.loadClass("cn.soulapp.android.component.home.me.MeFragment").getDeclaredMethod("j3", user, boolean.class);
        });
        check("legacy update callback", () -> {
            Class<?> callback = loader.loadClass("n9.c$a");
            callback.getDeclaredMethod("a", loader.loadClass("cn.soulapp.android.client.component.middle.platform.version.bean.VersionUpdateEntity"), boolean.class);
            if (callback.getDeclaredField("b").getType() != int.class || callback.getDeclaredField("c").getType() != boolean.class)
                throw new AssertionError("update callback fields");
        });
        check("share sheet", () -> new HostShareBindings(loader));
        check("legacy location service", () -> {
            Class<?> service=loader.loadClass("cn.soulapp.android.lib.location.bdmap.BDMapLocationServiceImpl");
            Class<?> data=loader.loadClass("cn.soulapp.android.lib.location.model.LocationData");
            Class<?> listener=loader.loadClass("cn.soulapp.android.lib.location.LocationListener");
            data.getConstructor(String.class,String.class);
            for(String name:new String[]{"City","Province","Country","AdCode","CityCode","DistrictName"}) data.getMethod("set"+name,String.class);
            data.getMethod("setLocationWhere",int.class);
            for(String name:new String[]{"getLastLocation","getInnerLastLocation","getMLastLocation"}) service.getDeclaredMethod(name);
            service.getDeclaredMethod("startLocation",listener); service.getDeclaredMethod("stopLocation",listener);
            service.getDeclaredMethod("callback2Listener",int.class,data);
        });
        check("legacy location picker", () -> {
            Class<?> presenter=loader.loadClass("cn.soulapp.android.component.publish.ui.position.a");
            presenter.getDeclaredMethod("O",loader.loadClass("com.baidu.mapapi.search.core.PoiInfo"));
            presenter.getDeclaredMethod("H");
            loader.loadClass("cn.soulapp.android.component.publish.ui.position.NewPoiActivity").getDeclaredMethod("bindEvent");
            for(String name:new String[]{"PoiChatAdapter","PoiChatSearchAdapter"}) {
                Class<?> adapter=loader.loadClass("cn.soulapp.android.component.publish.ui.position."+name);
                adapter.getMethod("d",loader.loadClass(adapter.getName()+"$OnPoiItemClickListener"));
            }
        });
        check("legacy city tab", () -> {
            Class<?> square=loader.loadClass("cn.soulapp.android.component.square.main.SquareFragment");
            square.getDeclaredMethod("U0");square.getDeclaredField("j");square.getDeclaredField("k");
            Class<?> tabs=loader.loadClass("com.google.android.material.tabs.TabLayout");
            tabs.getMethod("getTabAt",int.class).getReturnType().getMethod("d");
            loader.loadClass("cn.soulapp.android.component.square.main.n1").getMethod("getItem",int.class);
        });
        check("legacy square rows", () -> {
            Class<?> adapter=loader.loadClass("cn.soulapp.android.component.square.main.squarepost.SquareAdapter");
            Class<?> holder=loader.loadClass("cn.soulapp.android.component.square.main.squarepost.viewholder.BaseSquareViewHolder");
            for(String name:new String[]{"c","g","h"}) {
                if(name.equals("c"))adapter.getDeclaredMethod(name,holder,Object.class);
                else adapter.getDeclaredMethod(name,holder);
            }
            adapter.getMethod("onAttachedToRecyclerView",loader.loadClass("androidx.recyclerview.widget.RecyclerView"));
            post.getField("authorIdEcpt");post.getField("createTime");post.getField("content");post.getField("title");post.getField("tags");
            loader.loadClass("cn.soulapp.android.component.square.main.PostVH").getDeclaredMethod("onBind",post,int.class);
        });
        check("legacy square diff", () -> {
            Class<?> base=loader.loadClass("com.chad.library.adapter.base.BaseQuickAdapter");
            base.getDeclaredField("data");base.getDeclaredMethod("setList",java.util.Collection.class);
            Class<?> callback=loader.loadClass("cx.c");
            if(callback.getDeclaredField("a").getType()!=base)throw new AssertionError("callback adapter");
            Class<?> differ=loader.loadClass("cx.a");
            differ.getDeclaredMethod("e",java.util.List.class,loader.loadClass("androidx.recyclerview.widget.DiffUtil$DiffResult"),Runnable.class);
            differ.getDeclaredMethod("g",java.util.List.class,Runnable.class);
        });
        check("legacy post share sender", () -> {
            Class<?> sender=loader.loadClass("cn.soulapp.android.component.chat.utils.t3");
            sender.getDeclaredMethod("g",post,String.class);sender.getDeclaredField("a");
            Class<?> json=loader.loadClass("ks.i");json.getMethod("b",Object.class);json.getMethod("d",String.class,Class.class);
            Class<?> panel=loader.loadClass("cn.soulapp.android.square.SeedsShareDialogFragment");
            if(panel.getDeclaredField("B").getType()!=post)throw new AssertionError("share post");
            panel.getDeclaredMethod("P0");panel.getDeclaredMethod("j0",android.view.View.class);
        });
        check("legacy loaded post sources", () -> {
            loader.loadClass("com.lufficc.lightadapter.LightAdapter").getMethod("j");
            loader.loadClass("cn.soulapp.android.component.home.user.adapter.l0").getMethod("getAllData");
            Class<?> holder=loader.loadClass("cn.soulapp.android.component.square.main.PostVH");
            holder.getDeclaredField("post");holder.getField("config").getType().getMethod("k").getReturnType()
                    .getMethod("onBindViewHolder",int.class,post);
            for(String owner:new String[]{"cn.soulapp.android.component.home.user.fragment.PageUserHomeFragment","cn.soulapp.android.component.home.meta.user.MetaPageUserHomeFragment"})
                loader.loadClass(owner).getDeclaredMethod("onResume");
        });
        check("legacy user gender", () -> {
            Class<?> gender=loader.loadClass("com.soul.component.componentlib.service.user.cons.Gender");
            if(loader.loadClass("com.soul.component.componentlib.service.user.bean.User").getField("gender").getType()!=gender)throw new AssertionError("user gender");
            for(String value:new String[]{"MALE","FEMALE","UNKNOWN"})gender.getField(value);
        });
        check("ad request and model identities", () -> {
            HostAdBindings ads = new HostAdBindings(loader);
            if (!ads.legacy || !ads.postAd.getName().equals("j3.a")) throw new AssertionError("ad model");
            for (String[] spec : ads.loaders) ads.validateLoader(loader, spec);
        });
        check("outgoing decoration", () -> new HostMessageDecoration(loader));
        check("outgoing factory", () -> {
            Method factory = HostBindings.messageMethod(im, "createChatSendMsg",
                    loader.loadClass("cn.soulapp.imlib.msg.chat.ChatMessage"), String.class);
            if (!factory.getName().equals("c") || factory.getReturnType() != im) throw new AssertionError("send factory");
        });
        check("legacy repeat menu", () -> {
            Class<?> base = loader.loadClass(HostBindings.FRAGMENT).getSuperclass();
            Class<?> menu = loader.loadClass("cn.soulapp.android.component.chat.window.ChatLongClickPopUp$a");
            Class<?> helper = loader.loadClass("cn.soulapp.android.component.chat.utils.o");
            helper.getDeclaredMethod("f", im, android.content.Context.class, base, base.getDeclaredField("x").getType(),
                    android.view.View.class, int.class, android.view.View.class, menu, int.class);
            helper.getDeclaredMethod("d", base, android.view.View.class, im);
            HostBindings.sender(loader).getDeclaredMethod("a1", im);
            base.getDeclaredMethod("onBubbleLongClick", android.view.View.class, im, int.class);
        });
        System.out.println("RESULT passed=" + passed + " failed=" + failed + "; host methods not invoked");
        if (failed != 0) System.exit(1);
    }
}
