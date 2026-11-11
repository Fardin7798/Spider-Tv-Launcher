package com.spidertv.launcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Pure list logic behind Manage apps: the saved order and hidden list are comma-separated package
 * names; Home sorts its row by the saved order and leaves hidden packages out.
 */
final class SpiderAppOrder {

    interface Key<T> {
        String of(T item);
    }

    private SpiderAppOrder() {
    }

    /** Package names from a saved list: trimmed, empty entries and repeats dropped, order kept. */
    static List<String> split(String saved) {
        final LinkedHashSet<String> out = new LinkedHashSet<>();
        if (saved != null) {
            for (String part : saved.split(",")) {
                final String pkg = part.trim();
                if (!pkg.isEmpty()) out.add(pkg);
            }
        }
        return new ArrayList<>(out);
    }

    static String join(Collection<String> pkgs) {
        final StringBuilder b = new StringBuilder();
        for (String pkg : pkgs) {
            if (b.length() > 0) b.append(',');
            b.append(pkg);
        }
        return b.toString();
    }

    /**
     * Stable re-sort of {@code items}: those named in {@code order} first, in that order; the rest after
     * them in the order they already had.
     */
    static <T> void arrange(List<T> items, List<String> order, Key<T> key) {
        if (order.isEmpty()) return;
        final Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < order.size(); i++) rank.put(order.get(i), i);
        final int last = order.size();
        final List<T> sorted = new ArrayList<>(items);
        Collections.sort(sorted, (a, b) -> {
            final Integer ra = rank.get(key.of(a));
            final Integer rb = rank.get(key.of(b));
            return Integer.compare(ra == null ? last : ra, rb == null ? last : rb);
        });
        items.clear();
        items.addAll(sorted);
    }

    /** {@code shown} with {@code pkg} moved {@code by} places (clamped at the ends); a copy, input untouched. */
    static List<String> move(List<String> shown, String pkg, int by) {
        final List<String> out = new ArrayList<>(shown);
        final int from = out.indexOf(pkg);
        if (from < 0) return out;
        final int to = Math.max(0, Math.min(out.size() - 1, from + by));
        out.add(to, out.remove(from));
        return out;
    }

    /**
     * The order to save after a move: the list as shown, then packages from the old saved order that were
     * not on screen (say, phone apps while those are switched off), so their place is not lost.
     */
    static List<String> keepRest(List<String> shown, List<String> saved) {
        final LinkedHashSet<String> out = new LinkedHashSet<>(shown);
        out.addAll(saved);
        return new ArrayList<>(out);
    }

    /** The hidden list with {@code pkg} added when {@code hide}, removed otherwise. */
    static List<String> setHidden(List<String> hidden, String pkg, boolean hide) {
        final LinkedHashSet<String> out = new LinkedHashSet<>(hidden);
        if (hide) out.add(pkg);
        else out.remove(pkg);
        return new ArrayList<>(out);
    }
}
