package dev.local.newsoul;

import java.util.HashSet;
import java.util.Set;
import org.json.JSONObject;

/** Command types confirmed against the host and the reference module. */
final class InteractionPrivacyRules {
    static boolean blockReceipt(boolean enabled, int envelope, int content) {
        return enabled && envelope == 1 && content == 25;
    }
    /** Exact payload parser: unknown/malformed commands must remain untouched. */
    static int profileState(String command, String params) {
        if (!"ONLINE_STATE".equals(command) || params == null) return -1;
        try {
            JSONObject value = new JSONObject(params);
            Object type = value.opt("msgType"), state = value.opt("msgState");
            if (!(type instanceof Number) || ((Number) type).doubleValue() != 1) return -1;
            if (!(state instanceof Number)) return -1;
            double number = ((Number) state).doubleValue();
            return number == 0 ? 0 : number == 1 ? 1 : -1;
        } catch (Exception ignored) { return -1; }
    }

    static final class ProfileNotices {
        private final Set<String> forwardedStarts = new HashSet<>();

        synchronized boolean block(boolean enabled, int state, String target) {
            // A start already sent before enabling must still be allowed to end.
            return enabled && state >= 0 && !(state == 1 && forwardedStarts.contains(target));
        }

        synchronized void forwarded(int state, String target) {
            if (target == null || target.isEmpty()) return;
            if (state == 0) forwardedStarts.add(target);
            else if (state == 1) forwardedStarts.remove(target);
        }
    }

    static int selectionLimit(boolean enabled, int original) {
        return enabled ? Math.max(original, 99) : original;
    }
}
