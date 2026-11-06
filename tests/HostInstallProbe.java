package dev.local.newsoul;

import android.content.pm.ApplicationInfo;
import dalvik.system.PathClassLoader;
import java.io.File;
import java.util.*;

/** Integration test without launching Soul: reflect on its classes, never instantiate or invoke them. */
public final class HostInstallProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("module-apk host-apk temporary-cache-root 637|638|639");
        ApplicationInfo host = new ApplicationInfo(), module = new ApplicationInfo();
        host.sourceDir = args[1]; host.dataDir = args[2]; module.sourceDir = args[0];
        ClassLoader loader = new PathClassLoader(host.sourceDir, HostInstallProbe.class.getClassLoader());
        boolean soul639 = args[3].equals("639");
        boolean newer = args[3].equals("638") || soul639;
        for (int attempt = 0; attempt < 2; attempt++) {
            long start = System.nanoTime();
            HostSymbols.initialize(host, module, loader);
            if (!HostSymbols.method(HostDexRules.CONTEXT,loader).getDeclaringClass().getName().equals(newer ? "mf.b" : "lf.b")) throw new AssertionError("context");
            if (!HostSymbols.type(HostDexRules.AD_ORIGIN,loader).getName().equals(newer ? "qi.d" : "pi.d")) throw new AssertionError("origin");
            if (!HostSymbols.type(HostDexRules.AD_PROVIDER,loader).getName().equals(newer ? "qi.k" : "pi.k")) throw new AssertionError("provider");
            if (!HostSymbols.method(HostDexRules.FOLLOW,loader).getName().equals(soul639 ? "V1" : newer ? "T1" : "S1")) throw new AssertionError("follow");
            if (!HostSymbols.method(HostDexRules.DAYS,loader).getName().equals(soul639 ? "T1" : newer ? "R1" : "Q1")) throw new AssertionError("days");
            if (!HostSymbols.method(HostDexRules.COLLAPSE,loader).getName().equals(newer ? "A" : "y")) throw new AssertionError("collapse");
            if (!HostSymbols.method(HostDexRules.RECEIVE,loader).getName().equals("run")) throw new AssertionError("receive");
            if (!HostSymbols.method(HostDexRules.READ_ACK,loader).getName().equals("ackMsgRead")) throw new AssertionError("ack");
            String fingerprint = HostSymbolCache.fingerprint(List.of(new File(host.sourceDir)), HostDexRules.REVISION);
            Properties saved = HostSymbolCache.read(new File(host.dataDir,"cache/newsoul-host-symbols.properties"),fingerprint);
            for(String key:HostDexRules.KEYS) if(saved.getProperty(key)==null)throw new AssertionError("cache "+key);
            System.out.println("PASS integration host="+args[3]+" attempt="+attempt+" elapsedMs="+(System.nanoTime()-start)/1_000_000);
        }
    }
}
