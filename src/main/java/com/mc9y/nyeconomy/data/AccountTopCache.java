package com.mc9y.nyeconomy.data;

import com.mc9y.nyeconomy.util.Money;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Blank038
 * @since 2021-03-11
 */
public class AccountTopCache {
    private final Map<String, Integer> tempMap = new HashMap<>();
    private final Map<String, BigDecimal> exactTempMap = new HashMap<>();

    public int getCurrencyCount(String key) {
        return Money.toLegacyInt(getCurrencyCountExact(key));
    }

    public synchronized BigDecimal getCurrencyCountExact(String key) {
        BigDecimal exact = exactTempMap.get(key);
        Integer legacy = tempMap.get(key);
        if (exact == null) {
            if (legacy == null || legacy < 0) {
                return Money.ZERO;
            }
            exact = Money.of(legacy);
            exactTempMap.put(key, exact);
            return exact;
        }
        if (legacy != null && legacy >= 0 && legacy != Money.toLegacyInt(exact)) {
            exact = Money.of(legacy);
            exactTempMap.put(key, exact);
        }
        return exact;
    }

    public synchronized void setCurrencyCountExact(String key, BigDecimal amount) {
        BigDecimal normalized = Money.normalize(amount);
        exactTempMap.put(key, normalized);
        tempMap.put(key, Money.toLegacyInt(normalized));
    }

    public Map<String, Integer> getTempMap() {
        return tempMap;
    }

    public Map<String, BigDecimal> getTempMapExact() {
        return Collections.unmodifiableMap(exactTempMap);
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
