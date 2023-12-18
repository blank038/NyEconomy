package com.mc9y.nyeconomy.handler;

import com.mc9y.nyeconomy.data.AccountTopCache;
import com.mc9y.nyeconomy.data.CurrencyData;
import com.mc9y.nyeconomy.data.TopCache;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Blank038
 * @since 2021-03-07
 */
public class YamlStorgeHandler extends AbstractStorgeHandler {

    @Override
    public int balance(String name, String type, int status) {
        if (CurrencyData.CURRENCY_DATA.containsKey(type)) {
            return CurrencyData.CURRENCY_DATA.get(type).getUserBalance(name);
        } else {
            return 0;
        }
    }

    @Override
    public boolean deposit(String name, String type, int amount) {
        if (amount < 0 || !CurrencyData.CURRENCY_DATA.containsKey(type)) {
            return false;
        }
        CurrencyData.CURRENCY_DATA.get(type).deposit(name, amount);
        return true;
    }

    @Override
    public boolean withdraw(String name, String type, int amount) {
        if (amount < 0 || !CurrencyData.CURRENCY_DATA.containsKey(type) || balance(name, type, 0) < amount) {
            return false;
        }
        CurrencyData.CURRENCY_DATA.get(type).withdraw(name, amount);
        return true;
    }

    @Override
    public boolean set(String name, String type, int amount) {
        if (!CurrencyData.CURRENCY_DATA.containsKey(type)) {
            return false;
        }
        CurrencyData.CURRENCY_DATA.get(type).set(name, amount);
        return true;
    }

    @Override
    public void save() {
        for (Map.Entry<String, CurrencyData> entry : CurrencyData.CURRENCY_DATA.entrySet()) {
            entry.getValue().save();
        }
    }

    @Override
    public synchronized void refreshTop() {
        Map<String, AccountTopCache> map = new HashMap<>();
        CurrencyData.CURRENCY_DATA.forEach((k, v) -> {
            for (String player : v.getAllPlayers()) {
                if (!map.containsKey(player)) {
                    map.put(player, new AccountTopCache());
                }
                map.get(player).getTempMap().putIfAbsent(k, v.getUserBalance(player));
            }
        });
        TopCache.getInstance().submitCache(map);
    }
}