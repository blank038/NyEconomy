package com.mc9y.nyeconomy.data;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

public class AccountCacheTest {
    @Test
    public void keepsExactBalanceAndLegacyProjection() {
        AccountCache cache = new AccountCache();
        cache.setBalanceExact("coin", new BigDecimal("12.99"));

        assertEquals(new BigDecimal("12.99"), cache.balanceExact("coin"));
        assertEquals(12, cache.balance("coin"));
        assertEquals(12, cache.getCurrencyMap().get("coin").get());
    }

    @Test
    public void addsAndSubtractsExactly() {
        AccountCache cache = new AccountCache();
        cache.addBalanceExact("coin", new BigDecimal("0.10"));
        cache.addBalanceExact("coin", new BigDecimal("0.20"));
        cache.subtractBalanceExact("coin", new BigDecimal("0.01"));

        assertEquals(new BigDecimal("0.29"), cache.balanceExact("coin"));
    }
}
