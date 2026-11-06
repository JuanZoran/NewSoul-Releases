package dev.local.newsoul;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Request ABI only; placement classification stays independent of loader type. */
final class HostAdBindings {
    final boolean legacy;
    final Class<?> slot;
    final Method slotId;
    final String[][] loaders;
    final Class<?> postAd, expressAd;
    private static final Map<String, String> LEGACY_SLOTS = new HashMap<>();
    static final Set<String> LEGACY_UNSUPPORTED = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "ad_tag_web", "ad_group", "ad_planet", "ad_me", "ad_envelope", "ad_notice",
            "ad_chat_tools", "ad_reward_chat", "ad_reward_drama")));

    static {
        for (String id : new String[]{"1", "6", "14", "16", "22"}) LEGACY_SLOTS.put(id, "ad_feed");
        LEGACY_SLOTS.put("5", "ad_detail");
        LEGACY_SLOTS.put("11", "ad_detail");
        LEGACY_SLOTS.put("20", "ad_video");
        LEGACY_SLOTS.put("2", "ad_tag");
        LEGACY_SLOTS.put("9", "ad_search");
        // Tag/search native ads call SoulUnifiedAd.showDialogView, not a web banner.
        LEGACY_SLOTS.put("7", "ad_interstitial");
        LEGACY_SLOTS.put("8", "ad_interstitial");
    }

    HostAdBindings(ClassLoader loader) throws Exception {
        legacy = HostBindings.legacyChat(loader);
        slot = loader.loadClass(legacy ? "d3.a" : "fa.a");
        slotId = slot.getDeclaredMethod(legacy ? "c" : "b");
        if (slotId.getReturnType() != String.class) throw new NoSuchMethodException("ad slot identity");
        loaders = legacy ? new String[][] {
                {"c4.c", "a", "b", "e"}, {"g4.d", "a", "b", "f"},
                {"e4.g", "b", "c", "a"}
        } : new String[][] {
                {"jb.e", "a", "b", "e"}, {"mb.d", "a", "c", "b"}, {"lb.h", "b", "c", "a"}
        };
        // la.a / ia.a exist in 4.88 but describe unrelated chat classes.
        postAd = loader.loadClass(legacy ? "j3.a" : "la.a");
        expressAd = loader.loadClass(legacy ? "g3.a" : "ia.a");
    }

    static String legacyPlacement(String slot, String origin, String callback) {
        if (origin != null) return origin;
        if (callback != null) {
            if (callback.startsWith("ka.d$")) return "ad_conversation";
            if (callback.startsWith("c3.a$")) return "ad_float";
            if (callback.startsWith("c3.c$")) return "ad_interstitial";
            if (callback.startsWith("cn.soulapp.android.ad.h5.moduel.RewardModule$")
                    || callback.startsWith("y4.c$")) return "ad_reward_web";
        }
        // Slot 18 is a conversation placement here; unknown callers are preserved.
        // Slot 13 includes the test activity, so only its real web/applet origin matches.
        return LEGACY_SLOTS.get(slot);
    }

    Set<String> requestKeys(ClassLoader loader, String owner) {
        if (!legacy) return Collections.emptySet();
        Set<String> result = new HashSet<>();
        if (owner.equals("c4.c")) {
            producer(result, loader, "ad_tag", "cn.soulapp.android.component.square.tag.TagSquareFragment", "Z1");
            producer(result, loader, "ad_video", "cn.soulapp.android.component.square.videoplay.VideoPlayPreviewFragmentB",
                    "N0", "boolean", "io.reactivex.SingleEmitter");
        } else if (owner.equals("g4.d")) {
            producer(result, loader, "ad_feed", "cn.soulapp.android.component.square.recommend.TimeLineFragment",
                    "u2", "boolean", "io.reactivex.ObservableEmitter");
            producer(result, loader, "ad_detail", "cn.soulapp.android.component.square.post.base.detail.PostDetailActivity", "t3");
            producer(result, loader, "ad_search", "cn.soulapp.android.component.square.search.SearchResultFragmentA",
                    "s", "java.lang.String", "int");
            producer(result, loader, "ad_conversation", "ka.d", "h", "boolean", "io.reactivex.SingleEmitter");
            producer(result, loader, "ad_float", "c3.a", "d", "java.lang.String",
                    "cn.soulapp.android.ad.callback.SimpleCallBack");
            producer(result, loader, "ad_interstitial", "c3.c", "e", "java.lang.String");
        } else if (owner.equals("e4.g")) {
            producer(result, loader, "ad_reward_web", "cn.soulapp.android.ad.h5.moduel.RewardModule",
                    "preloadRewardAd", "java.lang.String", "java.lang.String");
        }
        return result;
    }

    private static void producer(Set<String> result, ClassLoader loader, String key,
            String owner, String name, String... args) {
        try {
            Class<?>[] types = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++)
                types[i] = args[i].equals("boolean") ? boolean.class : args[i].equals("int") ? int.class : loader.loadClass(args[i]);
            if (loader.loadClass(owner).getDeclaredMethod(name, types).getReturnType() == void.class) result.add(key);
        } catch (ReflectiveOperationException ignored) { }
    }

    void validateLoader(ClassLoader loader, String[] names) throws Exception {
        Class<?> type = loader.loadClass(names[0]);
        Field slotField = type.getDeclaredField(names[1]);
        Field listener = type.getDeclaredField(names[2]);
        Field context = type.getDeclaredField(names[3]);
        Class<?> callback = loader.loadClass("cn.soulapp.android.ad.core.services.plaforms.listener.SoulAdRequestListener");
        if (slotField.getType() != slot || !callback.isAssignableFrom(listener.getType())
                || !Reference.class.isAssignableFrom(context.getType()))
            throw new NoSuchFieldException("ad loader identity " + names[0]);
        if (type.getDeclaredMethod("loadAds").getReturnType() != void.class)
            throw new NoSuchMethodException("ad loader return type");
        callback.getMethod("onAdFailed", int.class, String.class);
    }
}
