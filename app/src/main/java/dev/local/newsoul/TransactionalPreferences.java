package dev.local.newsoul;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Keeps failed disk commits out of runtime reads and preference listeners. */
final class TransactionalPreferences implements SharedPreferences {
    // Each isolated settings, launcher or material file has its own accepted
    // snapshot for the process lifetime even if restoring the disk store throws.
    // Neither the wrapper nor its listener registry retains an Activity/Context.
    private static final Map<SharedPreferences, TransactionalPreferences> STORES = new java.util.IdentityHashMap<>();
    private final SharedPreferences disk;
    private final Set<OnSharedPreferenceChangeListener> listeners = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private volatile Map<String, Object> accepted;

    static SharedPreferences wrap(SharedPreferences prefs) {
        if (prefs instanceof TransactionalPreferences) return prefs;
        synchronized (STORES) {
            TransactionalPreferences store = STORES.get(prefs);
            if (store == null) {
                store = new TransactionalPreferences(prefs);
                STORES.put(prefs, store);
            }
            return store;
        }
    }

    private TransactionalPreferences(SharedPreferences disk) {
        this.disk = disk;
        accepted = copy(disk.getAll());
    }
    private static Object copyValue(Object value) {
        return value instanceof Set ? new HashSet<>((Set<?>) value) : value;
    }
    private static Map<String, Object> copy(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) result.put(entry.getKey(), copyValue(entry.getValue()));
        return result;
    }
    @Override public Map<String, ?> getAll() { return copy(accepted); }
    private Object value(String key, Object fallback) {
        Object value = accepted.get(key);
        return value == null ? fallback : copyValue(value);
    }
    @Override public String getString(String key, String fallback) { return (String) value(key, fallback); }
    @SuppressWarnings("unchecked")
    @Override public Set<String> getStringSet(String key, Set<String> fallback) { return (Set<String>) value(key, fallback); }
    @Override public int getInt(String key, int fallback) { return (Integer) value(key, fallback); }
    @Override public long getLong(String key, long fallback) { return (Long) value(key, fallback); }
    @Override public float getFloat(String key, float fallback) { return (Float) value(key, fallback); }
    @Override public boolean getBoolean(String key, boolean fallback) { return (Boolean) value(key, fallback); }
    @Override public boolean contains(String key) { return accepted.containsKey(key); }
    @Override public Editor edit() { return new Changes(); }
    @Override public synchronized void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { listeners.add(listener); }
    @Override public synchronized void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { listeners.remove(listener); }

    @SuppressWarnings("unchecked")
    private static void put(Editor editor, String key, Object value) {
        if (value == null) editor.remove(key);
        else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
        else throw new IllegalArgumentException("Unsupported preference type: " + key);
    }
    private void restore() {
        Editor restore = disk.edit().clear();
        for (Map.Entry<String, Object> entry : accepted.entrySet()) put(restore, entry.getKey(), entry.getValue());
        restore.apply();
    }
    private final class Changes implements Editor {
        private final Map<String, Object> updates = new LinkedHashMap<>();
        private boolean clear;
        private synchronized Editor change(String key, Object value) { updates.put(key, copyValue(value)); return this; }
        @Override public Editor putString(String key, String value) { return change(key, value); }
        @Override public Editor putStringSet(String key, Set<String> value) { return change(key, value); }
        @Override public Editor putInt(String key, int value) { return change(key, value); }
        @Override public Editor putLong(String key, long value) { return change(key, value); }
        @Override public Editor putFloat(String key, float value) { return change(key, value); }
        @Override public Editor putBoolean(String key, boolean value) { return change(key, value); }
        @Override public Editor remove(String key) { return change(key, null); }
        @Override public synchronized Editor clear() { clear = true; return this; }
        @Override public boolean commit() { return save(true); }
        @Override public void apply() { save(false); }
        private boolean save(boolean durable) {
            Map<String, Object> pending;
            boolean clearing;
            synchronized (this) { pending = copy(updates); clearing = clear; updates.clear(); clear = false; }
            Set<String> changed = new LinkedHashSet<>();
            ArrayList<OnSharedPreferenceChangeListener> notify;
            boolean cleared;
            synchronized (TransactionalPreferences.this) {
                Map<String, Object> next = clearing ? new LinkedHashMap<>() : copy(accepted);
                for (Map.Entry<String, Object> entry : pending.entrySet()) {
                    if (entry.getValue() == null) next.remove(entry.getKey());
                    else next.put(entry.getKey(), entry.getValue());
                }
                cleared = clearing && !accepted.isEmpty();
                // Android clear reports a null key on R+, while explicit puts/removes
                // still report their own keys. Cleared keys are not separate events.
                for (String key : pending.keySet()) if (!java.util.Objects.equals(
                        clearing ? null : accepted.get(key), next.get(key))) changed.add(key);
                // Rebuild from the accepted image, so a failed rollback cannot leak
                // rejected values into a later successful write or process restart.
                Editor editor = disk.edit().clear();
                for (Map.Entry<String, Object> entry : next.entrySet()) put(editor, entry.getKey(), entry.getValue());
                boolean saved = true;
                try {
                    if (durable) saved = editor.commit();
                    else editor.apply();
                } catch (RuntimeException error) {
                    try { restore(); }
                    catch (RuntimeException recovery) { error.addSuppressed(recovery); }
                    throw error;
                }
                if (!saved) {
                    // Android commitToMemory precedes its disk result. Restore before releasing
                    // the writer lock; runtime reads retain the old published snapshot.
                    restore();
                    return false;
                }
                accepted = next;
                notify = new ArrayList<>(listeners);
            }
            if (cleared && android.os.Build.VERSION.SDK_INT >= 30) changed.add(null);
            Runnable callbacks = () -> {
                for (String key : changed) for (OnSharedPreferenceChangeListener listener : notify) {
                    synchronized (TransactionalPreferences.this) { if (!listeners.contains(listener)) continue; }
                    listener.onSharedPreferenceChanged(TransactionalPreferences.this, key);
                }
            };
            if (!changed.isEmpty()) {
                if (Looper.myLooper() == Looper.getMainLooper()) callbacks.run();
                else new Handler(Looper.getMainLooper()).post(callbacks);
            }
            return true;
        }
    }
}
