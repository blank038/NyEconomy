package com.mc9y.nyeconomy.data;

import com.mc9y.nyeconomy.util.Money;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class AccountCache {
    public static final ConcurrentHashMap<UUID, AccountCache> CACHE_DATA = new ConcurrentHashMap<>();
    
    private static final ConcurrentHashMap<UUID, ReentrantLock> ACCOUNT_LOCKS = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, BigDecimal> exactCurrencyMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> currencyMap = new ConcurrentHashMap<>();

    public AccountCache() {
    }

    public int balance(String type) {
        return Money.toLegacyInt(balanceExact(type));
    }

    public synchronized BigDecimal balanceExact(String type) {
        BigDecimal exact = exactCurrencyMap.get(type);
        AtomicInteger legacy = currencyMap.get(type);
        if (exact == null) {
            if (legacy == null || legacy.get() < 0) {
                return Money.ZERO;
            }
            exact = Money.of(legacy.get());
            exactCurrencyMap.put(type, exact);
            return exact;
        }
        if (legacy != null && legacy.get() >= 0 && legacy.get() != Money.toLegacyInt(exact)) {
            exact = Money.of(legacy.get());
            exactCurrencyMap.put(type, exact);
        }
        return exact;
    }

    public void setBalance(String type, int amount) {
        setBalanceExact(type, Money.of(amount));
    }

    public synchronized void setBalanceExact(String type, BigDecimal amount) {
        BigDecimal normalized = Money.normalize(amount);
        exactCurrencyMap.put(type, normalized);
        syncLegacy(type, normalized);
    }

    public int addBalance(String type, int amount) {
        return Money.toLegacyInt(addBalanceExact(type, Money.of(amount)));
    }

    public synchronized BigDecimal addBalanceExact(String type, BigDecimal amount) {
        BigDecimal normalized = Money.normalize(amount);
        BigDecimal result = exactCurrencyMap.compute(type, (key, current) ->
                Money.normalize((current == null ? Money.ZERO : current).add(normalized)));
        syncLegacy(type, result);
        return result;
    }

    public int subtractBalance(String type, int amount) {
        return Money.toLegacyInt(subtractBalanceExact(type, Money.of(amount)));
    }

    public synchronized BigDecimal subtractBalanceExact(String type, BigDecimal amount) {
        BigDecimal normalized = Money.normalize(amount);
        BigDecimal result = exactCurrencyMap.compute(type, (key, current) -> {
            BigDecimal balance = current == null ? Money.ZERO : current;
            return balance.compareTo(normalized) < 0 ? Money.ZERO : balance.subtract(normalized);
        });
        syncLegacy(type, result);
        return result;
    }

    public Map<String, AtomicInteger> getCurrencyMap() {
        return currencyMap;
    }

    public Map<String, BigDecimal> getCurrencyMapExact() {
        return Collections.unmodifiableMap(exactCurrencyMap);
    }

    private void syncLegacy(String type, BigDecimal amount) {
        currencyMap.computeIfAbsent(type, key -> new AtomicInteger()).set(Money.toLegacyInt(amount));
    }

    public static ReentrantLock getAccountLock(UUID playerUUID) {
        return ACCOUNT_LOCKS.computeIfAbsent(playerUUID, k -> new ReentrantLock());
    }
}
