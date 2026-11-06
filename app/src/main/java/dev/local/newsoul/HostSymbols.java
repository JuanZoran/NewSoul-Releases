package dev.local.newsoul;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Process;
import android.util.Log;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.zip.ZipFile;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.wrap.DexClass;
import org.luckypray.dexkit.wrap.DexMethod;

/** One resolution pass before installing hooks; no host method is executed by a scan. */
final class HostSymbols {
    private static final Map<String, String> SYMBOLS = new HashMap<>();
    private static final String TAG = "SoulHook102";
    private HostSymbols() {}

    static synchronized void initialize(ApplicationInfo host, ApplicationInfo module, ClassLoader loader) {
        SYMBOLS.clear();
        long start = System.nanoTime();
        List<DexKitBridge> bridges = new ArrayList<>();
        boolean attempted = false;
        try {
            List<File> apks = new ArrayList<>(); apks.add(new File(host.sourceDir));
            if (host.splitSourceDirs != null) for (String split : host.splitSourceDirs) apks.add(new File(split));
            String fingerprint = HostSymbolCache.fingerprint(apks, HostDexRules.REVISION);
            File cache = new File(host.dataDir, "cache/newsoul-host-symbols.properties");
            Properties values = HostSymbolCache.read(cache, fingerprint);
            for (String key : HostDexRules.KEYS) {
                try {
                    String descriptor = values.getProperty(key);
                    boolean cached = descriptor != null;
                    if (cached) {
                        try { validate(key, descriptor, loader); }
                        catch (Exception | LinkageError invalid) { descriptor = null; values.remove(key); cached = false; }
                    }
                    if (descriptor == null) {
                        if (!attempted) {
                            attempted = true;
                            loadNative(module);
                            try {
                                for (File apk : apks) {
                                    try (ZipFile zip = new ZipFile(apk)) {
                                        if (zip.stream().noneMatch(e -> e.getName().matches("classes[0-9]*\\.dex"))) continue;
                                    }
                                    DexKitBridge bridge = DexKitBridge.create(apk.getAbsolutePath());
                                    if (bridge == null) throw new IllegalStateException("DexKit open failed");
                                    bridges.add(bridge); bridge.setThreadNum(2);
                                }
                            } catch (Throwable error) {
                                for (DexKitBridge bridge : bridges) bridge.close(); bridges.clear(); throw error;
                            }
                        }
                        if (bridges.isEmpty()) throw new IllegalStateException("DexKit unavailable");
                        List<String> matches = new ArrayList<>();
                        for (DexKitBridge bridge : bridges) matches.addAll(HostDexRules.find(bridge, key));
                        descriptor = HostSymbolCache.unique(matches);
                        validate(key, descriptor, loader);
                        values.setProperty(key, descriptor);
                    }
                    SYMBOLS.put(key, descriptor);
                    Log.i(TAG, "host symbol " + key + "=" + descriptor + "; cache=" + cached);
                } catch (Throwable error) {
                    values.remove(key);
                    Log.e(TAG, "host symbol unavailable: " + key, error);
                }
            }
            try { HostSymbolCache.write(cache, fingerprint, values); }
            catch (Exception error) { Log.w(TAG, "host symbol cache write skipped", error); }
        } catch (Throwable error) { Log.e(TAG, "host symbol initialization failed", error); }
        finally {
            for (DexKitBridge bridge : bridges) bridge.close();
            Log.i(TAG, "host symbols resolved=" + SYMBOLS.size() + "/" + HostDexRules.KEYS.length
                    + "; elapsedMs=" + (System.nanoTime() - start) / 1_000_000);
        }
    }

    private static void loadNative(ApplicationInfo module) throws Exception {
        // The module APK stores and page-aligns its native libraries. Load from that APK,
        // never a writable extracted library or a similarly named host library.
        String[] abis = Process.is64Bit() ? Build.SUPPORTED_64_BIT_ABIS : Build.SUPPORTED_32_BIT_ABIS;
        try (ZipFile zip = new ZipFile(module.sourceDir)) {
            for (String abi : abis) {
                String prefix = "lib/" + abi + "/";
                if (zip.getEntry(prefix + "libdexkit.so") == null) continue;
                System.load(module.sourceDir + "!/" + prefix + "libdexkit.so");
                return;
            }
        }
        throw new IllegalStateException("No DexKit library for process ABI");
    }

    static Method method(String key, ClassLoader loader) throws Exception {
        String descriptor = SYMBOLS.get(key);
        if (descriptor == null) throw new NoSuchMethodException("Unresolved host symbol: " + key);
        Method method = new DexMethod(descriptor).getMethodInstance(loader);
        method.setAccessible(true); return method;
    }

    static Class<?> type(String key, ClassLoader loader) throws Exception {
        String descriptor = SYMBOLS.get(key);
        if (descriptor == null) throw new ClassNotFoundException("Unresolved host symbol: " + key);
        return new DexClass(descriptor).getInstance(loader);
    }

    private static void validate(String key, String descriptor, ClassLoader loader) throws Exception {
        if (HostDexRules.AD_ORIGIN.equals(key) || HostDexRules.AD_PROVIDER.equals(key)) {
            Class<?> type = new DexClass(descriptor).getInstance(loader);
            Class<?> presenter = recipientPresenter(loader);
            if (HostDexRules.AD_ORIGIN.equals(key)) type.getDeclaredConstructor(presenter);
            else {
                if (!loader.loadClass("com.chad.library.adapter.base.provider.BaseItemProvider").isAssignableFrom(type))
                    throw new IllegalStateException("Unexpected ad provider");
                type.getDeclaredConstructor(presenter, loader.loadClass("com.chad.library.adapter.base.BaseProviderMultiAdapter"));
            }
            return;
        }
        Method m = new DexMethod(descriptor).getMethodInstance(loader);
        if (HostDexRules.RECEIVE.equals(key)) {
            if (!Runnable.class.isAssignableFrom(m.getDeclaringClass()) || m.getParameterCount() != 0
                    || m.getReturnType() != void.class || Modifier.isStatic(m.getModifiers()))
                throw new IllegalStateException("Unexpected message dispatch");
        } else if (HostDexRules.READ_ACK.equals(key)) {
            if (!m.getDeclaringClass().getName().equals("cn.soulapp.imlib.Conversation")
                    || m.getReturnType() != void.class || Modifier.isStatic(m.getModifiers())
                    || !Arrays.equals(m.getParameterTypes(), new Class<?>[]{loader.loadClass("cn.soulapp.imlib.msg.ImMessage")}))
                throw new IllegalStateException("Unexpected read receipt");
        } else if (HostDexRules.CONTEXT.equals(key)) {
            if (!Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 0 || m.getReturnType() != Context.class)
                throw new IllegalStateException("Unexpected context accessor");
        } else if (HostDexRules.COLLAPSE.equals(key)) {
            if (!m.getDeclaringClass().getName().equals(HostDexRules.MEDIA_MENU)
                    || m.getParameterCount() != 0 || m.getReturnType() != boolean.class
                    || !Modifier.isPublic(m.getModifiers()) || Modifier.isStatic(m.getModifiers()))
                throw new IllegalStateException("Unexpected media collapse");
        } else {
            if (!m.getDeclaringClass().getName().equals(HostDexRules.PROFILE) || m.getReturnType() != void.class
                    || Modifier.isStatic(m.getModifiers()) || !Arrays.equals(m.getParameterTypes(), new Class<?>[]{
                        loader.loadClass(HostDexRules.USER), loader.loadClass(HostDexRules.PROFILE + "$ViewHolder")}))
                throw new IllegalStateException("Unexpected profile renderer");
        }
    }

    static Class<?> chatPresenter(ClassLoader loader) throws Exception {
        return loader.loadClass("cn.soulapp.android.component.chat.fragment.ConversationFragment")
                .getDeclaredField("E").getType();
    }

    static Class<?> recipientPresenter(ClassLoader loader) throws Exception {
        Class<?> adapter = loader.loadClass("cn.soulapp.android.component.chat.adapter.ConversationAdapter");
        Class<?> type = adapter.getDeclaredField("c").getType();
        // 4.88 has Context in c and the presenter in d; validate() checks its constructors.
        return type == Context.class ? adapter.getDeclaredField("d").getType() : type;
    }

    static Method subscribe(Object api, Object observable, Class<?> callback) throws Exception {
        if (api == null || observable == null) throw new IllegalArgumentException("Missing host request");
        Method match = null;
        for (Method candidate : api.getClass().getMethods()) {
            Class<?>[] args = candidate.getParameterTypes();
            if (candidate.getName().equals("m") && !Modifier.isStatic(candidate.getModifiers())
                    && args.length == 2 && args[0].isInstance(observable) && args[1] == callback
                    && candidate.getReturnType().getName().equals("com.walid.rxretrofit.HttpSubscriber")) {
                if (match != null) throw new IllegalStateException("Ambiguous host subscription");
                match = candidate;
            }
        }
        if (match == null) throw new NoSuchMethodException("Host subscription");
        return match;
    }
}
