package dev.local.newsoul;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import java.lang.reflect.*;
import java.util.*;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;

/** Version-bound ad placements. Independent opt-in rules; never grants ad rewards. */
final class AdControls {
    static final String[][] ITEMS = {
        {"block_splash", "冷启动与热启动开屏", ""},
        {"ad_feed", "广场信息流", ""},
        {"ad_detail", "帖子详情", ""},
        {"ad_video", "竖屏视频流", ""},
        {"ad_tag", "话题横幅", ""},
        {"ad_tag_web", "话题网页广告", ""},
        {"ad_search", "搜索顶部横幅", ""},
        {"ad_group", "群组横幅推广", ""},
        {"ad_planet", "星球游戏推广", ""},
        {"ad_me", "我的页面推广", ""},
        {"ad_conversation", "聊天会话列表", ""},
        {"ad_envelope", "通知信封广告", ""},
        {"ad_notice", "系统通知广告", ""},
        {"ad_chat_tools", "聊天面板推广", "已缓存的推广图标可能需要重启 Soul 才会消失"},
        {"ad_float", "悬浮广告", ""},
        {"ad_interstitial", "插屏与底部弹窗", ""},
        {"ad_reward_chat", "聊天激励广告", ""},
        {"ad_reward_drama", "短剧激励广告", ""},
        {"ad_reward_web", "网页与任务激励", ""}
    };
    static final Set<String> ready = Collections.synchronizedSet(new HashSet<>());
    static final Set<String> unsupported = Collections.synchronizedSet(new HashSet<>());
    private static final Map<String,String> SLOTS = new HashMap<>();
    private static final Map<View,State> VIEWS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object,String> ORIGINS = Collections.synchronizedMap(new WeakHashMap<>());
    private static Method slotId;
    private static HostAdBindings bindings;
    private static String conversationOrigin;
    private static XposedModule module;
    private static volatile Context applicationContext;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final class State {
        final String key;
        int visibility;
        boolean hidden;
        State(String key, View view) { this.key=key; visibility=view.getVisibility(); }
    }
    static {
        String[] ids={"2","26","5","20","27","24","31","35","36","18","9"};
        String[] keys={"ad_tag","ad_tag_web","ad_detail","ad_video","ad_group","ad_planet","ad_me","ad_envelope","ad_notice","ad_chat_tools","ad_search"};
        for(int i=0;i<ids.length;i++) SLOTS.put(ids[i],keys[i]);
    }
    static boolean supported(String key) { return !unsupported.contains(key); }
    static boolean enabled(Context c,String key) {
        return PluginPreferences.get(c).getBoolean(key, "block_splash".equals(key));
    }
    static void install(XposedModule m,ClassLoader loader) {
        module=m;
        boolean legacy;
        try { legacy = HostBindings.legacyChat(loader); }
        catch (Throwable failure) { error("host identity",failure); return; }
        unsupported.clear();
        if (legacy) unsupported.addAll(HostAdBindings.LEGACY_UNSUPPORTED);
        try { conversationOrigin = HostSymbols.type(HostDexRules.AD_ORIGIN, loader).getName(); }
        catch (Throwable error) { error("conversation origin", error); }
        try {
            bindings = new HostAdBindings(loader);
            slotId = bindings.slotId;
            installFeedDataFilter(loader);
            for (String[] spec : bindings.loaders) {
                try { bindings.validateLoader(loader, spec); installLoader(loader,spec[0],spec[1],spec[2],spec[3]); }
                catch (Throwable error) { error(spec[0], error); }
            }
        } catch(Throwable t) { error("request hooks",t); }
        String prefix="cn.soulapp.android.ad.soulad.ad.views.express.render.";
        String[][] views=legacy ? new String[][] {
            {prefix+"FeedDrawExpressView","ad_video"},
            {prefix+"TagBannerExpressView","ad_tag"},
            {prefix+"TagTopHeaderExpressView","ad_tag"},
            {"cn.soulapp.android.ad.base.view.SearchBannerAdView","ad_search"},
            {"cn.soulapp.android.ad.base.view.FloatAdView","ad_float"}
        } : new String[][] {
            {prefix+"PostFeedExpressComponent","ad_feed"},
            {prefix+"PostDetailExpressView","ad_detail"},
            {prefix+"FeedDrawExpressView","ad_video"},
            {prefix+"TagBannerExpressView","ad_tag"},
            // TagTopHeaderExpressView is shared by slots 2 and 26: do not tag by class.
            {prefix+"FeedWebExpressView","ad_tag_web"},
            {prefix+"GroupBannerExpressView","ad_group"},
            {prefix+"PlantGameExpressView","ad_planet"},
            {prefix+"PlantGameTagExpressView","ad_planet"},
            {prefix+"MeCellExpressView","ad_me"},
            {prefix+"EnvelopeMessageFeedExpressView","ad_envelope"},
            {prefix+"SystemNoticeFeedExpressView","ad_notice"},
            {"cn.soulapp.android.ad.base.view.SearchBannerAdView","ad_search"},
            {"cn.soulapp.android.ad.base.view.FloatAdView","ad_float"},
            {"cn.soulapp.android.ad.base.view.FloatAdViewV2","ad_float"},
            {"cn.soulapp.android.ad.base.view.DraggableFloatAdView","ad_float"},
            {"cn.soulapp.android.ad.base.view.FloatEntranceView","ad_float"}
        };
        for(String[] rule:views) installView(loader,rule[0],rule[1]);
        installHolder(loader,"cn.soulapp.android.component.square.main.ad.AdPostViewHolder","ad_feed",false);
        if (!legacy) installHolder(loader,"cn.soulapp.android.component.square.main.ad.AdExpressViewHolder","ad_feed",false);
        try { installHolder(loader, HostSymbols.type(HostDexRules.AD_PROVIDER, loader).getName(), "ad_conversation", true); }
        catch (Throwable error) { error("conversation provider", error); }
        if (!legacy) installHolder(loader,"cn.soulapp.android.component.square.video.adapter.a","ad_video",true);
        installDialog(loader,"cn.soulapp.android.ad.dialog.InterstitialAdDialog");
        if (legacy) installLegacyDialogRequest(loader);
        else installDialog(loader,"cn.soulapp.android.ad.dialog.InterstitialAdBottomDialog");
        try {
            module.hook(View.class.getDeclaredMethod("setVisibility",int.class))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    View view=(View)chain.getThisObject(); State state=VIEWS.get(view);
                    if(state!=null && enabled(view.getContext(),state.key)) {
                        state.visibility=(Integer)chain.getArg(0); state.hidden=true;
                        return chain.proceed(new Object[]{View.GONE});
                    }
                    return chain.proceed();
                });
        } catch(Throwable t) { error("view visibility",t); }
        info("ad controls installed; ready="+ready.size()+"/"+(18-unsupported.size())
                +" (excluding splash); unsupported="+new TreeSet<>(unsupported));
    }
    private static Field field(Class<?> c,String name) throws Exception {
        for(Class<?> p=c;p!=null;p=p.getSuperclass()) {
            try {Field f=p.getDeclaredField(name);f.setAccessible(true);return f;}
            catch(NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static String origin() {
        for(StackTraceElement e:Thread.currentThread().getStackTrace()) {
            String n=e.getClassName();
            // Slot 18 is shared. ConversationAdPresenter must win over the generic
            // chat-tools slot mapping so ordinary conversation rows remain untouched.
            if(n.equals(conversationOrigin)) return "ad_conversation";
            if(n.startsWith("cn.soulapp.android.component.chat.limitdialog.RewardAdHelper")) return "ad_reward_chat";
            if(n.startsWith("cn.soulapp.android.component.square.video.drama.o0")) return "ad_reward_drama";
            if(n.startsWith("cn.soulapp.android.ad.h5.moduel.") || (bindings != null && !bindings.legacy && n.equals("cc.c"))
                || n.startsWith("cn.soul.android.soul_rn_sdk.plugin.SRNAdPlugin")
                || n.startsWith("cn.soul.android.soul_rn_sdk.plugin.SoulRnAdPlugin")) return "ad_reward_web";
            if(n.startsWith("cn.soulapp.android.ad.base.view.AdFloatViewProvider")) return "ad_float";
            if(bindings != null && !bindings.legacy && n.equals("cn.soulapp.android.ad.base.view.e")) return "ad_interstitial";
            if (bindings != null && bindings.legacy) {
                if (n.equals("c3.a")) return "ad_float";
                if (n.equals("c3.c")) return "ad_interstitial";
                if (n.equals("y4.c")) return "ad_reward_web";
            }
        }
        return null;
    }
    private static void installLoader(ClassLoader loader,String name,String sf,String lf,String cf) {
        try {
            Class<?> c=loader.loadClass(name); Field slot=field(c,sf), listener=field(c,lf), context=field(c,cf);
            for(Constructor<?> ctor:c.getDeclaredConstructors()) {
                module.hook(ctor).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    Object result=chain.proceed();String key=origin();
                    if(key!=null) ORIGINS.put(chain.getThisObject(),key);
                    return result;
                });
            }
            Method failed=loader.loadClass("cn.soulapp.android.ad.core.services.plaforms.listener.SoulAdRequestListener")
                .getMethod("onAdFailed",int.class,String.class);
            module.hook(c.getDeclaredMethod("loadAds")).setId("new-soul/ad-loader/"+name)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    Object self=chain.getThisObject(); Object ref=context.get(self);
                    Object ctx=ref instanceof java.lang.ref.Reference ? ((java.lang.ref.Reference<?>)ref).get():null;
                    if(!(ctx instanceof Context)) return chain.proceed();
                    String id=(String)slotId.invoke(slot.get(self));
                    Object cb=listener.get(self);
                    String key=ORIGINS.get(self);
                    if (bindings.legacy) key=HostAdBindings.legacyPlacement(id,key,cb==null?null:cb.getClass().getName());
                    else {
                        // Modern slot 18 is shared: no verified presenter means no fallback.
                        if ("18".equals(id) && conversationOrigin == null) return chain.proceed();
                        if(key==null) key=SLOTS.get(id);
                    }
                    // Unclassified reward requests are retained, not grouped indiscriminately.
                    if(key==null || !enabled((Context)ctx,key)) return chain.proceed();
                    String selected=key;
                    if(cb!=null) MAIN.post(()->{
                        try { failed.invoke(cb,-102,"new soul: disabled by user"); }
                        catch(Throwable t) {error("ad failure callback",t);}
                    });
                    info("ad blocked: "+selected+" slot="+id);
                    return null;
                });
            if (bindings.legacy) {
                ready.addAll(bindings.requestKeys(loader,name));
            } else if(name.equals("lb.h")) ready.addAll(Arrays.asList("ad_reward_chat","ad_reward_drama","ad_reward_web"));
            else {ready.addAll(SLOTS.values());ready.add("ad_float");ready.add("ad_interstitial");}
        } catch(Throwable t) {error(name,t);}
    }
    private static void installView(ClassLoader loader,String name,String key) {
        try {
            Class<?> c=loader.loadClass(name);
            if(!View.class.isAssignableFrom(c)) throw new IllegalStateException("not a View");
            for(Constructor<?> ctor:c.getDeclaredConstructors()) module.hook(ctor)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    Object result=chain.proceed();View view=(View)chain.getThisObject();remember(view.getContext());tag(view,key);return result;
                });
            ready.add(key);
        } catch(Throwable t) {error(name,t);}
    }
    private static void installFeedDataFilter(ClassLoader loader) {
        try {
            Class<?> squareAdapter=loader.loadClass("cn.soulapp.android.component.square.main.squarepost.SquareAdapter");
            Class<?> baseAdapter=loader.loadClass("com.chad.library.adapter.base.BaseQuickAdapter");
            Class<?> postAd=bindings.postAd;
            Class<?> expressAd=bindings.expressAd;
            Set<String> names=new HashSet<>(Arrays.asList(
                "addData","setData","setList","replaceData","setNewInstance","setNewData","setDiffNewData"));
            int count=0;
            for(Method method:baseAdapter.getDeclaredMethods()) {
                if(!names.contains(method.getName()) || method.getReturnType()!=void.class) continue;
                Class<?>[] types=method.getParameterTypes();
                if(types.length==0) continue;
                // DiffResult is calculated against the unfiltered positions; let the one-argument
                // overload perform filtering before it calculates a new diff instead.
                if(method.getName().equals("setDiffNewData") && types.length!=1) continue;
                int index=types.length-1;
                boolean collection=Collection.class.isAssignableFrom(types[index]);
                boolean item=types[index]==Object.class;
                if(!collection && !item) continue;
                module.hook(method).setId("new-soul/feed-data/"+method.getName()+"/"+count)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                        Object self=chain.getThisObject();
                        if(!squareAdapter.isInstance(self) || !feedEnabled()) return chain.proceed();
                        Object value=chain.getArg(index);
                        if(collection && value instanceof Collection<?>) {
                            AdFeedRules.Result filtered=AdFeedRules.filter((Collection<?>)value,postAd,expressAd);
                            if(filtered.removed==0) return chain.proceed();
                            Object[] args=chain.getArgs().toArray();args[index]=filtered.items;
                            info("feed ad models filtered="+filtered.removed+" via "+method.getName());
                            return chain.proceed(args);
                        }
                        if(item && AdFeedRules.isAd(value,postAd,expressAd)) {
                            info("feed ad model skipped via "+method.getName());
                            return null;
                        }
                        return chain.proceed();
                    });
                count++;
            }
            if(count==0) throw new IllegalStateException("no adapter mutation methods");
            ready.add("ad_feed");
            info("feed data filter installed; methods="+count);
        } catch(Throwable t) {error("feed data filter",t);}
    }
    private static boolean feedEnabled() {
        Context context=applicationContext;
        if(context==null) {
            try {
                Object current=Class.forName("android.app.ActivityThread").getDeclaredMethod("currentApplication").invoke(null);
                if(current instanceof Context) {remember((Context)current);context=applicationContext;}
            } catch(Throwable ignored) {}
        }
        return context!=null && enabled(context,"ad_feed");
    }
    private static void remember(Context context) {
        if(context==null) return;
        Context app=context.getApplicationContext();
        applicationContext=app!=null?app:context;
    }
    private static void installHolder(ClassLoader loader,String name,String key,boolean provider) {
        try {
            Class<?> c=loader.loadClass(name);int count=0;
            for(Method method:c.getDeclaredMethods()) {
                // Match provider signatures, including obfuscated typed convert methods.
                boolean match=provider ? method.getReturnType()==void.class && method.getParameterCount()>=2
                    && method.getParameterTypes()[0].getSimpleName().equals("BaseViewHolder")
                    : method.getName().equals("onBind") || method.getName().equals("onCreate");
                if(!match) continue;
                module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    Object result=chain.proceed();Object holder=provider?chain.getArg(0):chain.getThisObject();
                    View view=holderView(holder);if(view!=null) tag(view,key);return result;
                });count++;
            }
            if(count==0) throw new IllegalStateException("no bind methods");
            ready.add(key);
        } catch(Throwable t) {error(name,t);}
    }
    private static View holderView(Object holder) {
        try {return (View)field(holder.getClass(),"itemView").get(holder);} catch(Exception ignored) {}
        try {Object binding=field(holder.getClass(),"binding").get(holder);
            return (View)binding.getClass().getMethod("getRoot").invoke(binding);
        } catch(Exception ignored) {return null;}
    }
    private static void installDialog(ClassLoader loader,String name) {
        try {
            Class<?> c=loader.loadClass(name);
            // These are DialogFragments. Dismiss after onStart through their own
            // dismiss() override, which releases Soul's global popup queue callback.
            Method started=c.getMethod("onStart");
            Method getContext=c.getMethod("getContext");
            Method dismiss=c.getMethod("dismiss");
            module.hook(started).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                Object result=chain.proceed();Object self=chain.getThisObject();
                if(c.isInstance(self)) {
                    Object ctx=getContext.invoke(self);
                    if(ctx instanceof Context && enabled((Context)ctx,"ad_interstitial")) {
                        dismiss.invoke(self);info("ad dialog dismissed");
                    }
                }
                return result;
            });ready.add("ad_interstitial");
        } catch(Throwable t) {error(name,t);}
    }
    private static void installLegacyDialogRequest(ClassLoader loader) {
        try {
            // Tag/search and profile native ads use j3.a.v0 -> showDialogView.
            // Stop before creation, so there is no popup queue or match callback to release.
            Method show=bindings.postAd.getDeclaredMethod("v0",
                    loader.loadClass("androidx.fragment.app.FragmentManager"),Context.class);
            if(show.getReturnType()!=void.class) throw new NoSuchMethodException("native ad dialog");
            module.hook(show).setId("new-soul/legacy-native-ad-dialog")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain->{
                    Context context=(Context)chain.getArg(1);
                    if(context!=null && enabled(context,"ad_interstitial")) return null;
                    return chain.proceed();
                });
            ready.add("ad_interstitial");
        } catch(Throwable t) {error("legacy native dialog",t);}
    }
    private static void tag(View view,String key) {
        State state=VIEWS.get(view);
        if(state==null || !state.key.equals(key)) {state=new State(key,view);VIEWS.put(view,state);}
        apply(view,state);
    }
    private static void apply(View view,State state) {
        if(enabled(view.getContext(),state.key)) {
            if(!state.hidden) {int original=view.getVisibility();view.setVisibility(View.GONE);state.visibility=original;state.hidden=true;}
        } else if(state.hidden) {state.hidden=false;view.setVisibility(state.visibility);}
    }
    static void refreshViews() {
        MAIN.post(()->{
            synchronized(VIEWS) {for(Map.Entry<View,State> e:VIEWS.entrySet()) apply(e.getKey(),e.getValue());}
        });
    }
    private static void info(String s) {Log.i("SoulHook102",s);module.log(Log.INFO,"SoulHook102",s);}
    private static void error(String s,Throwable t) {
        Log.e("SoulHook102","ad control unavailable: "+s,t);
        module.log(Log.ERROR,"SoulHook102","ad control unavailable: "+s,t);
    }
}
