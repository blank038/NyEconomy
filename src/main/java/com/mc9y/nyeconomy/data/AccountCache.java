package com.mc9y.nyeconomy.data;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class AccountCache {
    public static final ConcurrentHashMap<UUID, AccountCache> CACHE_DATA = new ConcurrentHashMap<>();
    
    private static final ConcurrentHashMap<UUID, ReentrantLock> ACCOUNT_LOCKS = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, AtomicInteger> currencyMap = new ConcurrentHashMap<>();

    public AccountCache() {
    }

    public int balance(String type) {
        AtomicInteger balance = currencyMap.get(type);
        return balance != null ? balance.get() : 0;
    }

    public void setBalance(String type, int amount) {
        currencyMap.computeIfAbsent(type, k -> new AtomicInteger(0))
                   .set(Math.max(0, amount));
    }

    public int addBalance(String type, int amount) {
        return currencyMap.computeIfAbsent(type, k -> new AtomicInteger(0))
                          .addAndGet(amount);
    }

    public int subtractBalance(String type, int amount) {
        AtomicInteger balance = currencyMap.computeIfAbsent(type, k -> new AtomicInteger(0));
        int newValue;
        int current;
        do {
            current = balance.get();
            newValue = Math.max(0, current - amount);
        } while (!balance.compareAndSet(current, newValue));
        return newValue;
    }

    public Map<String, AtomicInteger> getCurrencyMap() {
        return currencyMap;
    }

    public static ReentrantLock getAccountLock(UUID playerUUID) {
        return ACCOUNT_LOCKS.computeIfAbsent(playerUUID, k -> new ReentrantLock());
    }
}