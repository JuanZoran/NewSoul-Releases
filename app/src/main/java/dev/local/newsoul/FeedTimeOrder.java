package dev.local.newsoul;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.ToLongFunction;

/** Stable ordering of dated post slots; headers, ads and undated items stay put. */
final class FeedTimeOrder {
    static List<Object> sorted(List<?> source, ToLongFunction<Object> time) {
        List<Object> result = new ArrayList<>(source);
        List<Object> posts = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) if (time.applyAsLong(source.get(i)) > 0) {
            slots.add(i); posts.add(source.get(i));
        }
        posts.sort((a, b) -> Long.compare(time.applyAsLong(b), time.applyAsLong(a)));
        for (int i = 0; i < slots.size(); i++) result.set(slots.get(i), posts.get(i));
        return result;
    }

    // Retain server order across append/removal without resurrecting deleted rows.
    static List<Object> reconcile(List<?> original, List<?> current) {
        IdentityHashMap<Object, Integer> remaining = new IdentityHashMap<>();
        for (Object item : current) remaining.put(item, remaining.getOrDefault(item, 0) + 1);
        List<Object> result = new ArrayList<>();
        for (Object item : original) if (remaining.getOrDefault(item, 0) > 0) {
            result.add(item); remaining.put(item, remaining.get(item) - 1);
        }
        for (Object item : current) if (remaining.getOrDefault(item, 0) > 0) {
            result.add(item); remaining.put(item, remaining.get(item) - 1);
        }
        return result;
    }

    /** Merge host edits into server order without dropping rows hidden by the projection. */
    static List<Object> merge(List<?> original, List<?> before, List<?> after) {
        IdentityHashMap<Object, Integer> oldCounts = counts(before), newCounts = counts(after);
        IdentityHashMap<Object, Integer> removed = new IdentityHashMap<>();
        for (Object item : oldCounts.keySet()) removed.put(item,
                Math.max(0, oldCounts.get(item) - newCounts.getOrDefault(item, 0)));
        List<Object> result = new ArrayList<>();
        for (Object item : original) {
            int n = removed.getOrDefault(item, 0);
            if (n > 0) removed.put(item, n - 1); else result.add(item);
        }
        IdentityHashMap<Object, Integer> seen = new IdentityHashMap<>();
        for (int i = 0; i < after.size(); i++) {
            Object item = after.get(i);
            int n = seen.getOrDefault(item, 0) + 1; seen.put(item, n);
            if (n <= oldCounts.getOrDefault(item, 0)) continue;
            // Indexed inserts retain the following surviving host item as their anchor.
            // Plain pagination has no following item and appends to original order.
            int insertion = result.size();
            boolean found = false;
            for (int j = i + 1; j < after.size() && !found; j++) {
                Object next = after.get(j);
                if (!oldCounts.containsKey(next)) continue;
                for (int k = 0; k < result.size(); k++) if (result.get(k) == next) {
                    insertion = k; found = true; break;
                }
            }
            result.add(insertion, item);
        }
        return result;
    }

    private static IdentityHashMap<Object, Integer> counts(List<?> values) {
        IdentityHashMap<Object, Integer> result = new IdentityHashMap<>();
        for (Object item : values) result.put(item, result.getOrDefault(item, 0) + 1);
        return result;
    }

    static boolean same(List<?> a, List<?> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }
}
