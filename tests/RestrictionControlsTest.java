package dev.local.newsoul;

import android.view.View;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.*;
import java.util.*;

/** Execute real hooks with fake host methods; no messages, network or host state. */
public final class RestrictionControlsTest {
    static final Map<Executable, XposedInterface.Hooker> hooks = new HashMap<>();
    static final Set<Executable> deoptimized = new HashSet<>();
    static int calls, checks;
    public static class Post {}
    public static class Share {
        public Post E;
        private void r0() {}
        public void initViews(View view) {}
    }
    public static class LegacyShare {
        public String E;
        public Post B;
        public boolean P0() { return true; }
        private void j0(View view) {}
        public void initViews(View view) {}
    }
    public static class Model {
        public boolean getCanNotSendBeforeReply() { return true; }
        public boolean getCreateSessionLimit() { return true; }
        public boolean getForbidCall() { return true; }
        public boolean getForbidPic() { return true; }
        public int getSocialGraceScore() { return 42; }
        public int getRemainCreateChatSessionNum() { return 0; }
    }
    public static class Manager {}
    public static class Config {}
    public static class Dialog { public static void b(Manager manager, Config config) {} }
    public static class Fragment { public Caller E; }
    public static class Caller { public void call(Model model) {} }
    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, InvocationHandler h) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, h);
    }
    static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    static Object invoke(Method method, Object receiver, Object result, boolean throwsOriginal) throws Throwable {
        return hooks.get(method).intercept(proxy(XposedInterface.Chain.class, (p,m,a) -> {
            if (m.getName().equals("getThisObject")) return receiver;
            if (m.getName().equals("proceed")) {
                calls++;
                if (throwsOriginal) throw new IllegalStateException("original");
                return result;
            }
            throw new AssertionError(m);
        }));
    }
    public static void main(String[] args) throws Throwable {
        XposedModule module = new XposedModule() {};
        module.attachFramework(proxy(XposedInterface.class, (p,m,a) -> {
            if (m.getName().equals("hook")) {
                Executable target = (Executable) a[0];
                return proxy(XposedInterface.HookBuilder.class, (b,bm,ba) -> {
                    if (bm.getName().equals("intercept")) { hooks.put(target,(XposedInterface.Hooker)ba[0]); return null; }
                    return b;
                });
            }
            if (m.getName().equals("deoptimize")) { deoptimized.add((Executable)a[0]); return true; }
            if (m.getName().equals("log")) for (Object value : a)
                if (value instanceof Throwable) ((Throwable)value).printStackTrace();
            return null;
        }), () -> {});
        ClassLoader loader = new ClassLoader() {
            @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.endsWith("SeedsShareDialogFragment")) return Share.class;
                if (name.endsWith(".Post")) return Post.class;
                if (name.equals(SocialScoreControls.MODEL)) return Model.class;
                if (name.equals(SocialScoreControls.DIALOG)) return Dialog.class;
                if (name.equals(SocialScoreControls.DIALOG + "$a")) return Config.class;
                if (name.endsWith("FragmentManager")) return Manager.class;
                if (name.endsWith(".ConversationFragment")) return Fragment.class;
                if (name.endsWith("$d1")) return Caller.class;
                return super.loadClass(name);
            }
        };
        PostShareControls.install(module, loader);
        SocialScoreControls.install(module, loader);
        check(PostShareControls.ready && SocialScoreControls.modelReady && SocialScoreControls.dialogReady, "installed");
        Method share = Share.class.getDeclaredMethod("r0");
        Share receiver = new Share();
        for (boolean hasPost : new boolean[]{false,true}) for (boolean enabled : new boolean[]{false,true}) {
            receiver.E = hasPost ? new Post() : null;
            ChatExperienceControls.enabled = enabled;
            calls=0;
            invoke(share,receiver,null,false);
            check(calls == (hasPost && enabled ? 0 : 1), "post/profile and switch scope");
        }
        ChatExperienceControls.enabled=false;
        calls=0;
        try { invoke(share,receiver,null,true); throw new AssertionError("exception swallowed"); }
        catch (IllegalStateException expected) { check(calls==1,"no retry"); }
        for (Method gate : Model.class.getDeclaredMethods()) {
            if (gate.getReturnType()!=boolean.class) { check(!hooks.containsKey(gate),"score and quota untouched"); continue; }
            for (boolean original : new boolean[]{false,true}) for (boolean enabled : new boolean[]{false,true}) {
                ChatExperienceControls.enabled=enabled;calls=0;
                check(invoke(gate,new Model(),original,false).equals(original && !enabled),"dynamic model gate");
                check(calls==1,"original once");
            }
        }
        Method dialog=Dialog.class.getDeclaredMethod("b",Manager.class,Config.class);
        for (boolean enabled:new boolean[]{true,false,true}) {
            ChatExperienceControls.enabled=enabled;calls=0;
            invoke(dialog,null,null,false);
            check(calls==(enabled?0:1),"dialog live switch");
        }
        check(deoptimized.contains(Share.class.getDeclaredMethod("initViews",View.class)),"share caller deoptimized");
        check(deoptimized.contains(Caller.class.getDeclaredMethod("call",Model.class)),"model caller deoptimized");
        ClassLoader legacyLoader = new ClassLoader(loader) {
            @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.endsWith("SeedsShareDialogFragment")) return LegacyShare.class;
                return loader.loadClass(name);
            }
        };
        PostShareControls.install(module, legacyLoader);
        Method legacyGate = LegacyShare.class.getDeclaredMethod("P0");
        check(hooks.containsKey(legacyGate), "legacy gate installed despite unrelated E field");
        LegacyShare legacy = new LegacyShare();
        for (boolean hasPost : new boolean[]{false,true}) for (boolean enabled : new boolean[]{false,true})
            for (boolean original : new boolean[]{false,true}) {
                legacy.B = hasPost ? new Post() : null;
                ChatExperienceControls.enabled = enabled; calls=0;
                check(invoke(legacyGate,legacy,original,false).equals(hasPost && enabled || original),
                        "legacy post/profile scope and live preference");
                check(calls==(hasPost && enabled?0:1), "legacy original called once or bypassed");
            }
        ChatExperienceControls.enabled=false; calls=0;
        try { invoke(legacyGate,legacy,false,true); throw new AssertionError("legacy exception swallowed"); }
        catch (IllegalStateException expected) { check(calls==1,"legacy host failure never retried"); }
        check(deoptimized.contains(LegacyShare.class.getDeclaredMethod("j0",View.class)),"legacy direct renderer deoptimized");
        check(deoptimized.contains(LegacyShare.class.getDeclaredMethod("initViews",View.class)),"legacy outer renderer deoptimized");
        for (boolean relay : new boolean[]{false,true}) for (boolean bypass : new boolean[]{false,true})
            check(PostShareControls.canShare(relay,bypass)==(!relay || bypass), "relay means restriction");
        System.out.println("RestrictionControls: " + checks + " checks passed");
    }
}
