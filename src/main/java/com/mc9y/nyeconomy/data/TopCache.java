package com.mc9y.nyeconomy.data;

import com.aystudio.core.bukkit.AyCore;
import com.aystudio.core.bukkit.platform.wrapper.ITaskWrapper;
import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.handler.AbstractStorgeHandler;
import com.mc9y.nyeconomy.util.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TopCache {
    private static TopCache topCache;

    private volatile Map<String, Map<Integer, AccountTopCache.Entry<String, Integer>>> topData = Collections.emptyMap();
    private volatile Map<String, Map<Integer, AccountTopCache.Entry<String, BigDecimal>>> exactTopData = Collections.emptyMap();
    private ITaskWrapper task;
    private boolean enableTop;

    public static TopCache getInstance() {
        return topCache;
    }

    public TopCache() {
        topCache = this;
        this.refreshTask();
    }

    public boolean isEnabled() {
        return this.enableTop;
    }

    public void setTopEnabled(boolean topEnabled) {
        this.enableTop = topEnabled;
    }

    public void refreshTask() {
        if (task != null) {
            task.cancel();
        }
        int delay = Main.getInstance().getConfig().getInt("refresh-delay");
        if (delay > 0) {
            this.setTopEnabled(true);
            task = AyCore.getPlatformApi().runTaskTimerAsynchronously(Main.getInstance(), () -> AbstractStorgeHandler.getHandler().refreshTop(), 20L * 5, 20L * delay);
        } else {
            this.setTopEnabled(false);
        }
    }

    /**
     * 计算排行
     *
     * @param cacheData 缓存数据
     */
    public void submitCache(Map<String, AccountTopCache> cacheData) {
        Map<String, Map<Integer, AccountTopCache.Entry<String, Integer>>> legacyResult = new HashMap<>();
        Map<String, Map<Integer, AccountTopCache.Entry<String, BigDecimal>>> exactResult = new HashMap<>();
        for (String currency : Main.getInstance().vaults) {
            final String finalCurrency = Main.getNyEconomyAPI().checkVaultType(currency);
            Map<Integer, AccountTopCache.Entry<String, BigDecimal>> exact = this.calcExact(finalCurrency, cacheData);
            Map<Integer, AccountTopCache.Entry<String, Integer>> legacy = new HashMap<>();
            exact.forEach((index, entry) -> legacy.put(index,
                    new AccountTopCache.Entry<>(entry.getName(), Money.toLegacyInt(entry.getValue()))));
            exactResult.put(currency, exact);
            legacyResult.put(currency, legacy);
        }
        this.exactTopData = exactResult;
        this.topData = legacyResult;
    }

    public Map<Integer, AccountTopCache.Entry<String, Integer>> getTopData(String currency) {
        return this.topData.getOrDefault(currency, new HashMap<>(0));
    }

    public Map<Integer, AccountTopCache.Entry<String, BigDecimal>> getTopDataExact(String currency) {
        return this.exactTopData.getOrDefault(currency, new HashMap<>(0));
    }

    private Map<Integer, AccountTopCache.Entry<String, BigDecimal>> calcExact(
            String currency, Map<String, AccountTopCache> cacheData) {
        final int count = Main.getInstance().getConfig().getInt("cache-count");
        Map<Integer, AccountTopCache.Entry<String, BigDecimal>> result = new HashMap<>(count);
        List<Map.Entry<String, BigDecimal>> balances = new ArrayList<>(cacheData.size());
        for (Map.Entry<String, AccountTopCache> entry : cacheData.entrySet()) {
            balances.add(new java.util.AbstractMap.SimpleImmutableEntry<>(entry.getKey(),
                    entry.getValue().getCurrencyCountExact(currency)));
        }
        balances.sort((left, right) -> right.getValue().compareTo(left.getValue()));
        for (int i = 0; i < balances.size() && i < count; i++) {
            Map.Entry<String, BigDecimal> entry = balances.get(i);
            result.put(i, new AccountTopCache.Entry<>(entry.getKey(), entry.getValue()));
        }
        return result;
    }
}
