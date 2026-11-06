package dev.local.newsoul;

import java.util.concurrent.TimeUnit;

/** Root is requested only by the user's restart action, never during status detection. */
final class HomeRestart {
    static String command(int user) {
        if (user < 0) throw new IllegalArgumentException("Invalid Android user");
        return "am force-stop --user " + user + " cn.soulapp.android";
    }
    static boolean stop(int user) throws Exception {
        Process process = new ProcessBuilder("su", "-c", command(user))
                .redirectErrorStream(true).start();
        // Drain command output without exposing it as UI text or blocking a full pipe.
        Thread drain = new Thread(() -> {
            try (java.io.InputStream in = process.getInputStream()) {
                byte[] buffer = new byte[1024];
                while (in.read(buffer) != -1) { }
            } catch (java.io.IOException ignored) { }
        }, "newsoul-root-output");
        drain.setDaemon(true);
        drain.start();
        try {
            if (!process.waitFor(30, TimeUnit.SECONDS)) return false;
            int code = process.exitValue();
            android.util.Log.i("NewSoulHome", "Root stop exit=" + code);
            return code == 0;
        } finally { process.destroy(); }
    }
}
