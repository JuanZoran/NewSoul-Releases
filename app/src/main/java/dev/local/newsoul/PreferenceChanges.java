package dev.local.newsoul;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Persist user configuration before coordinating its runtime effects. Reading UI is separate. */
final class PreferenceChanges {
    private static final int ADS = 1, FEED = 2, ORDER = 4, ACCOUNT = 8;
    private static final int DISGUISE = 16, TIME = 32, RECALL = 64;
    private static final class MainThread {
        static final Handler HANDLER = new Handler(Looper.getMainLooper());
    }

    static boolean putBoolean(SharedPreferences prefs, String key, boolean value) {
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(key, value);
        // Preserve the existing explicit-edit rule, including edits that turn a choice off.
        if (AutoReadControls.ENABLED.equals(key)) updates.put(InteractionPrivacyControls.RECEIPT, false);
        if (InteractionPrivacyControls.RECEIPT.equals(key)) updates.put(AutoReadControls.ENABLED, false);
        return applyUpdates(prefs, updates);
    }

    static boolean putString(SharedPreferences prefs, String key, String value) {
        return applyUpdates(prefs, Collections.singletonMap(key, value));
    }

    static boolean putInt(SharedPreferences prefs, String key, int value) {
        return applyUpdates(prefs, Collections.singletonMap(key, value));
    }

    static boolean applyUpdates(SharedPreferences prefs, Map<String, ?> updates) {
        return persist(prefs, updates, false);
    }

    /** Batch values must already be validated/normalized by their editor or import rules. */
    static boolean write(SharedPreferences prefs, Map<String, ?> updates) {
        return persist(prefs, updates, true);
    }

    private static boolean persist(SharedPreferences prefs, Map<String, ?> updates, boolean durable) {
        if (prefs == null) return false;
        prefs = TransactionalPreferences.wrap(prefs);
        Set<String> changed = new LinkedHashSet<>();
        synchronized (prefs) {
            Map<String, ?> before = prefs.getAll();
            SharedPreferences.Editor edit = prefs.edit();
            for (Map.Entry<String, ?> entry : updates.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Boolean) edit.putBoolean(entry.getKey(), (Boolean) value);
                else if (value instanceof Integer) edit.putInt(entry.getKey(), (Integer) value);
                else if (value instanceof String) edit.putString(entry.getKey(), (String) value);
                else throw new IllegalArgumentException("Unsupported preference type: " + entry.getKey());
                if (!Objects.equals(before.get(entry.getKey()), value)) changed.add(entry.getKey());
            }
            if (durable) {
                if (!edit.commit()) return false;
            } else {
                if (changed.isEmpty()) return true;
                edit.apply();
            }
        }
        int effects = effects(changed);
        if (effects == 0) return true;
        Runnable notify = () -> refresh(effects);
        if (Looper.myLooper() == Looper.getMainLooper()) notify.run();
        else MainThread.HANDLER.post(notify);
        return true;
    }

    private static int effects(Set<String> keys) {
        boolean ads = false, feed = false, order = false, account = false;
        boolean disguise = false, time = false, recall = false;
        for (String key : keys) {
            if (key.startsWith("ad_") || "block_splash".equals(key))
                for (String[] item : AdControls.ITEMS) if (item[0].equals(key)) { ads = true; break; }
            feed |= key.startsWith("feed_filter_") || key.startsWith("feed_keywords_")
                    || key.startsWith("feed_registration_") || "square_show_ip_location_v1".equals(key)
                    || "square_show_registration_days_v1".equals(key)
                    || key.startsWith("square_ip_label_") || key.startsWith("square_days_label_");
            order |= SquareTimeOrder.ENABLED.equals(key);
            account |= AccountAutomationControls.SIGN.equals(key) || AccountAutomationControls.HIDE.equals(key);
            account |= key.startsWith("account_welfare_");
            disguise |= key.startsWith(FollowDisguiseControls.FOLLOW) || key.startsWith(FollowDisguiseControls.FOLLOWED)
                    || key.startsWith(FollowDisguiseControls.VISITOR) || key.startsWith(FollowDisguiseControls.DAYS);
            time |= ChatTimeControls.ENABLED.equals(key) || ChatTimeControls.EVERY.equals(key) || ChatTimeControls.FORMAT.equals(key);
            recall |= ChatRecallControls.ENABLED.equals(key) || ChatRecallControls.STYLE.equals(key);
        }
        return (ads ? ADS : 0) | (feed ? FEED : 0) | (order ? ORDER : 0) | (account ? ACCOUNT : 0)
                | (disguise ? DISGUISE : 0) | (time ? TIME : 0) | (recall ? RECALL : 0);
    }

    private static void refresh(int effects) {
        if ((effects & ADS) != 0) AdControls.refreshViews();
        if ((effects & FEED) != 0) SquareFeedFilter.refresh();
        else if ((effects & ORDER) != 0) SquareTimeOrder.refresh();
        if ((effects & ACCOUNT) != 0) AccountAutomationControls.changed();
        if ((effects & DISGUISE) != 0) FollowDisguiseControls.refresh();
        if ((effects & TIME) != 0) ChatTimeControls.refreshUi();
        if ((effects & RECALL) != 0) ChatRecallControls.refreshUi();
    }

    private PreferenceChanges() {}
}
