package com.mc9y.nyeconomy.data;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Blank038
 * @since 2021-03-11
 */
public class AccountTopCache {
    private final Map<String, Integer> tempMap = new HashMap<>();

    public int getCurrencyCount(String key) {
        return this.tempMap.getOrDefault(key, 0);
    }

    public Map<String, Integer> getTempMap() {
        return tempMap;
    }

    public static class Entry<K, V> {
        private final K name;
        private final V count;

        public Entry(K k, V v) {
            this.name = k;
            this.count = v;
        }

        public K getName() {
            return this.name;
        }

        public V getValue() {
            return count;
        }
    }
}