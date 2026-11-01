package com.useleess.litematicaupgraded.preview;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** The last few values used, oldest dropped first; a dropped value (or one cleared away) is handed to {@code onDrop} so it can free what it holds. */
final class LruCache<K, V> {
    private final LinkedHashMap<K, V> entries;
    private final Consumer<V> onDrop;

    LruCache(int capacity, Consumer<V> onDrop) {
        this.onDrop = onDrop;
        this.entries = new LinkedHashMap<K, V>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                if (size() <= capacity) return false;
                LruCache.this.onDrop.accept(eldest.getValue());
                return true;
            }
        };
    }

    V get(K key) {
        return entries.get(key);
    }

    void put(K key, V value) {
        V old = entries.put(key, value);
        if (old != null && old != value) onDrop.accept(old);
    }

    boolean containsValue(V value) {
        return entries.containsValue(value);
    }

    int size() {
        return entries.size();
    }

    void clear() {
        for (V value : entries.values()) onDrop.accept(value);
        entries.clear();
    }
}
