package com.mc9y.nyeconomy.data;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class AccountCache {
    public static final ConcurrentHashMap<UUID, AccountCache> CACHE_DATA = new ConcurrentHashMap<>();
    
    private static final ConcurrentHashMap<UUID, ReentrantLock> ACCOUNT_LOCKS = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Double> currencyMap = new ConcurrentHashMap<>();

    public AccountCache() {
    }

    public double balance(String type) {
        return currencyMap.getOrDefault(type, 0.0D);
    }

    public void setBalance(String type, double amount) {
        currencyMap.put(type, sanitize(amount));
    }

    public double addBalance(String type, double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount < 0.0D) {
            return balance(type);
        }
        return currencyMap.merge(type, amount, Double::sum);
    }

    public double subtractBalance(String type, double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount < 0.0D) {
            return balance(type);
        }
        return currencyMap.compute(type, (key, current) -> Math.max(0.0D, (current == null ? 0.0D : current) - amount));
    }

    public Map<String, Double> getCurrencyMap() {
        return currencyMap;
    }

    private double sanitize(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            return 0.0D;
        }
        return Math.max(0.0D, amount);
    }

    public static ReentrantLock getAccountLock(UUID playerUUID) {
        return ACCOUNT_LOCKS.computeIfAbsent(playerUUID, k -> new ReentrantLock());
    }
}
