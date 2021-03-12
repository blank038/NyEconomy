package com.mc9y.nyeconomy.data;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.handler.AbstractStorgeHandler;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Blank038
 * @since 2021-03-11
 */
public class TopCache {
    private static TopCache topCache;

    private final HashMap<String, HashMap<Integer, AccountTopCache.Entry<String, Integer>>> TOP_DATA = new HashMap<>();
    private BukkitTask task;
    private boolean ENABLE_TOP;

    public static TopCache getInstance() {
        return topCache;
    }

    public TopCache() {
        topCache = this;
    }

    public boolean isEnabled() {
        return this.ENABLE_TOP;
    }

    public void setTopEnabled(boolean topEnabled) {
        this.ENABLE_TOP = topEnabled;
    }

    public void refreshTask() {
        if (task != null) {
            task.cancel();
        }
        int delay = Main.getInstance().getConfig().getInt("refresh-delay");
        if (delay > 0) {
            this.setTopEnabled(true);
            task = Bukkit.getScheduler().runTaskTimerAsynchronously(Main.getInstance(),
                    () -> AbstractStorgeHandler.getHandler().refreshTop(), 20L * 5, 20L * delay);
        } else {
            this.setTopEnabled(false);
        }
    }

    /**
     * 计算排行
     *
     * @param cacheData 缓存数据
     */
    public void submitCache(HashMap<String, AccountTopCache> cacheData) {
        // 清空数据
        this.TOP_DATA.clear();
        for (String currency : Main.getInstance().vaults) {
            this.TOP_DATA.put(currency, this.calc(currency, cacheData));
        }
    }

    public HashMap<Integer, AccountTopCache.Entry<String, Integer>> getTopData(String currency) {
        return this.TOP_DATA.getOrDefault(currency, new HashMap<>(0));
    }

    private HashMap<Integer, AccountTopCache.Entry<String, Integer>> calc(String currency, HashMap<String, AccountTopCache> cacheData) {
        final int count = Main.getInstance().getConfig().getInt("cache-count");
        HashMap<Integer, AccountTopCache.Entry<String, Integer>> result = new HashMap<>(count);
        HashMap<String, Integer> tempData = new HashMap<>(cacheData.size());
        for (Map.Entry<String, AccountTopCache> entry : cacheData.entrySet()) {
            tempData.put(entry.getKey(), entry.getValue().getCurrencyCount(currency));
        }
        String[] names = tempData.keySet().toArray(new String[0]);
        Integer[] counts = tempData.values().toArray(new Integer[0]);
        for (int i = 0; i < names.length - 1; i++) {
            for (int x = i + 1; x < names.length; x++) {
                if (counts[i] < counts[x]) {
                    String tmp = names[i];
                    Integer tmp1 = counts[i];
                    counts[i] = counts[x];
                    names[i] = names[x];
                    names[x] = tmp;
                    counts[x] = tmp1;
                }
            }
        }
        for (int i = 0; i < names.length; i++) {
            if (i < count) {
                result.put(i, new AccountTopCache.Entry<>(names[i], counts[i]));
            }
            cacheData.get(names[i]).setRank(currency, i);
        }
        return result;
    }
}
