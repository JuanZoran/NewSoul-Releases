package dev.local.newsoul;

/** Standalone app_process probe: no host app, account, settings or UI access. */
public final class DebugAndroidProbe {
    public static void main(String[] args) throws Exception {
        java.io.File root = new java.io.File(args[0]);
        DebugStore store = new DebugStore(new java.io.File(root, "records"), DebugRecipient.key());
        byte[] row = "{\"point\":\"android.crypto.probe\",\"facts\":\"synthetic-only\"}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (!store.append(row)) throw new AssertionError("append failed");
        DebugStore resumed = new DebugStore(new java.io.File(root, "records"), DebugRecipient.key());
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(root, "probe.nsdebug"))) {
            out.write(resumed.export());
        }
        resumed.clear();
        if (resumed.size() != 0) throw new AssertionError("clear failed");
        System.out.println("Android encryption, restart read and clear succeeded");
    }
}
