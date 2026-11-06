package dev.local.newsoul;

import java.util.*;
import org.luckypray.dexkit.DexKitBridge;

/** Runs in an isolated app_process, reads APKs only, never loads Soul classes or installs hooks. */
public final class HostDexProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("native-library APK version(488-inspect|637|638|639)");
        System.load(args[0]);
        boolean inspect = "488-inspect".equals(args[2]);
        boolean legacy = "488".equals(args[2]);
        boolean soul639 = "639".equals(args[2]);
        boolean newer = "638".equals(args[2]) || soul639;
        if (!legacy && !inspect && !newer && !"637".equals(args[2])) throw new IllegalArgumentException("Unknown fixture");
        String p = "Lcn/soulapp/android/component/home/revision/RevisionProfileProvider;->";
        String params = "(Lcom/soul/component/componentlib/service/user/bean/User;Lcn/soulapp/android/component/home/revision/RevisionProfileProvider$ViewHolder;)V";
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put(HostDexRules.CONTEXT, newer ? "Lmf/b;->b()Landroid/content/Context;" : "Llf/b;->b()Landroid/content/Context;");
        expected.put(HostDexRules.AD_ORIGIN, newer ? "Lqi/d;" : "Lpi/d;");
        expected.put(HostDexRules.AD_PROVIDER, newer ? "Lqi/k;" : "Lpi/k;");
        expected.put(HostDexRules.FOLLOW, p + (newer ? "T1" : "S1") + params);
        expected.put(HostDexRules.DAYS, p + (newer ? "R1" : "Q1") + params);
        expected.put(HostDexRules.COLLAPSE, "Lcn/soulapp/android/component/chat/inputmenu/BaseMediaMenu;->" + (newer ? "A" : "y") + "()Z");
        expected.put(HostDexRules.RECEIVE, "Lcn/soulapp/imlib/handler/h$a;->run()V");
        expected.put(HostDexRules.READ_ACK, "Lcn/soulapp/imlib/Conversation;->ackMsgRead(Lcn/soulapp/imlib/msg/ImMessage;)V");
        if (soul639) {
            expected.put(HostDexRules.FOLLOW, p + "V1" + params);
            expected.put(HostDexRules.DAYS, p + "T1" + params);
        }
        if (legacy) {
            expected.put(HostDexRules.CONTEXT, "Ls7/b;->getContext()Landroid/content/Context;");
            expected.put(HostDexRules.AD_ORIGIN, "Lka/d;");
            expected.put(HostDexRules.AD_PROVIDER, "Lka/l;");
            expected.put(HostDexRules.FOLLOW, null); // Legacy renderers use explicit, separately probed bindings.
            expected.put(HostDexRules.DAYS, null);
            expected.put(HostDexRules.COLLAPSE, "Lcn/soulapp/android/component/chat/inputmenu/BaseMediaMenu;->p()Z");
            expected.put(HostDexRules.RECEIVE, "Lcn/soulapp/imlib/handler/g$a;->run()V");
            expected.put(HostDexRules.READ_ACK, "Lcn/soulapp/imlib/Conversation;->w(Lcn/soulapp/imlib/msg/ImMessage;)V");
        }
        long start = System.nanoTime();
        try (DexKitBridge bridge = DexKitBridge.create(args[1])) {
            bridge.setThreadNum(2);
            for (String key : HostDexRules.KEYS) {
                List<String> candidates = HostDexRules.find(bridge, key);
                System.out.println(key + " candidates=" + candidates);
                if (inspect) continue;
                if (legacy && expected.get(key) == null) {
                    if (!candidates.isEmpty()) throw new AssertionError("Unexpected modern profile renderer");
                    continue;
                }
                String found = HostSymbolCache.unique(candidates);
                if (!found.equals(expected.get(key))) throw new AssertionError(key + " unexpected " + found);
            }
        }
        System.out.println((inspect ? "INSPECT host=" : "PASS host=") + args[2] + " selectors=" + expected.size()
                + " elapsedMs=" + (System.nanoTime() - start) / 1_000_000);
    }
}
