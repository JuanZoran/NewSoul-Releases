package dev.local.newsoul;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

public final class HostAdBindingsTest {
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    static void placement(String slot, String origin, String callback, String expected) {
        String actual=HostAdBindings.legacyPlacement(slot,origin,callback);
        check(expected==null?actual==null:expected.equals(actual));
    }
    public static class LegacySlot { public String c() { return "18"; } }
    public static class ModernSlot { public String b() { return "18"; } }
    public static class BadSlot { public int c() { return 18; } }
    public static class PostAd { }
    public static class ExpressAd { }
    public interface Callback { void onAdFailed(int code, String reason); }
    public static class NativeLoader {
        public LegacySlot a; public Callback b; public WeakReference<Object> f;
        public void loadAds() { }
    }
    public static class WrongLoader {
        public ModernSlot a; public Callback b; public WeakReference<Object> f;
        public void loadAds() { }
    }
    public static class Emitter { }
    public static class Producer {
        private void Z1() { throw new AssertionError("host invoked"); }
        private void N0(boolean refresh, Emitter emitter) { throw new AssertionError("host invoked"); }
        private void u2(boolean refresh, Emitter emitter) { throw new AssertionError("host invoked"); }
        private void t3() { throw new AssertionError("host invoked"); }
        private void s(String word, int slot) { throw new AssertionError("host invoked"); }
        private void h(boolean refresh, Emitter emitter) { throw new AssertionError("host invoked"); }
        private void d(String slot, Callback callback) { throw new AssertionError("host invoked"); }
        private void e(String slot) { throw new AssertionError("host invoked"); }
        private void preloadRewardAd(String unit, String app) { throw new AssertionError("host invoked"); }
    }
    static class FakeLoader extends ClassLoader {
        final boolean legacy;
        final Map<String,Class<?>> types=new HashMap<>();
        FakeLoader(boolean legacy) {
            this.legacy=legacy;
            types.put(legacy?"d3.a":"fa.a",legacy?LegacySlot.class:ModernSlot.class);
            types.put(legacy?"j3.a":"la.a",PostAd.class);
            types.put(legacy?"g3.a":"ia.a",ExpressAd.class);
            types.put("cn.soulapp.android.ad.core.services.plaforms.listener.SoulAdRequestListener",Callback.class);
            types.put("g4.d",NativeLoader.class);
        }
        @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
            Class<?> type=types.get(name);return type==null?super.loadClass(name):type;
        }
    }
    public static void main(String[] args) throws Exception {
        for(String slot:new String[]{"1","6","14","16","22"}) placement(slot,null,null,"ad_feed");
        for(String slot:new String[]{"5","11"}) placement(slot,null,null,"ad_detail");
        placement("2",null,null,"ad_tag");placement("20",null,null,"ad_video");
        placement("9",null,null,"ad_search");
        for(String slot:new String[]{"7","8"}) placement(slot,null,null,"ad_interstitial");
        placement("23",null,null,null); // Search results are outside the top-banner switch.
        placement("12",null,null,null); // A peer profile is outside the own-page switch.
        placement("18",null,null,null);
        placement("18",null,"ka.d$a","ad_conversation");
        placement("18",null,"unrelated.Request",null);
        placement("13",null,null,null);
        placement("13",null,"cn.soulapp.android.ad.ui.AdTestActivity$a",null);
        placement("13",null,"y4.c$a","ad_reward_web");
        placement("13",null,"cn.soulapp.android.ad.h5.moduel.RewardModule$b","ad_reward_web");
        placement("34","ad_float",null,"ad_float");
        placement("34",null,"c3.a$a","ad_float");
        placement("9",null,"c3.c$a","ad_interstitial");
        check(HostAdBindings.LEGACY_UNSUPPORTED.size()==9);
        check(!HostAdBindings.LEGACY_UNSUPPORTED.contains("ad_reward_web"));
        check(HostAdBindings.LEGACY_UNSUPPORTED.contains("ad_reward_chat"));
        FakeLoader old=new FakeLoader(true);
        HostAdBindings bindings=new HostAdBindings(old);
        check(bindings.legacy);check(bindings.postAd==PostAd.class);
        check(bindings.loaders.length==3); // Splash has its own lifecycle-safe hook.
        bindings.validateLoader(old,new String[]{"g4.d","a","b","f"});checks++;
        old.types.put("g4.d",WrongLoader.class);
        try { bindings.validateLoader(old,new String[]{"g4.d","a","b","f"});throw new AssertionError("wrong slot accepted"); }
        catch(NoSuchFieldException expected) { checks++; }
        old.types.put("d3.a",BadSlot.class);
        try { new HostAdBindings(old);throw new AssertionError("wrong slot method accepted"); }
        catch(NoSuchMethodException expected) { checks++; }
        HostAdBindings modern=new HostAdBindings(new FakeLoader(false));
        check(!modern.legacy);check(modern.slotId.getName().equals("b"));
        check(bindings.requestKeys(old,"g4.d").isEmpty()); // No producer means no readiness.
        for(String owner:new String[]{"cn.soulapp.android.component.square.tag.TagSquareFragment",
                "cn.soulapp.android.component.square.videoplay.VideoPlayPreviewFragmentB",
                "cn.soulapp.android.component.square.recommend.TimeLineFragment",
                "cn.soulapp.android.component.square.post.base.detail.PostDetailActivity",
                "cn.soulapp.android.component.square.search.SearchResultFragmentA",
                "ka.d","c3.a","c3.c","cn.soulapp.android.ad.h5.moduel.RewardModule"})
            old.types.put(owner,Producer.class);
        old.types.put("io.reactivex.SingleEmitter",Emitter.class);
        old.types.put("io.reactivex.ObservableEmitter",Emitter.class);
        old.types.put("cn.soulapp.android.ad.callback.SimpleCallBack",Callback.class);
        check(bindings.requestKeys(old,"c4.c").size()==2);
        check(bindings.requestKeys(old,"g4.d").size()==6);
        check(bindings.requestKeys(old,"e4.g").size()==1);
        check(modern.requestKeys(old,"g4.d").isEmpty());
        System.out.println(checks+" host ad binding checks passed");
    }
}
