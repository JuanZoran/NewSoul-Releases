# LSPosed loads this class by the literal name in META-INF/xposed/java_init.list.
-keep class dev.local.newsoul.SoulHook {
    public <init>();
    public void onModuleLoaded(...);
    public void onPackageReady(...);
}

# Keep useful stack traces in the private R8 mapping file for release diagnostics.
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*
-renamesourcefileattribute SourceFile

# Android instantiates manifest components by name.
-keep class dev.local.newsoul.HomeActivity { public <init>(); }
-keep class dev.local.newsoul.HomeStatusProvider { public <init>(); }

-keep class io.github.libxposed.service.XposedProvider { public <init>(); }
-dontwarn io.github.libxposed.annotation.**
# Compile-time nullability annotations in the official service AAR; no runtime behavior.
-dontwarn androidx.annotation.NonNull
-dontwarn androidx.annotation.Nullable

-keep class dev.local.newsoul.HomeApplication { public <init>(); }
-keep class dev.local.newsoul.HomeUpgradeReceiver { public <init>(); }
