package com.mc9y.nyeconomy.api;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.data.CurrencyData;

/**
 * @author Blank038
 * @since 2021-03-07
 */
public class NyEconomyAPI {

    public int getBalance(String type, String name) {
        if (CurrencyData.CURRENCY_DATA.containsKey(type)) {
            return CurrencyData.CURRENCY_DATA.get(type).getUserBalance(name);
        } else {
            return 0;
        }
    }

    public void deposit(String type, String name, int amount) {
        if (CurrencyData.CURRENCY_DATA.containsKey(type)) {
            CurrencyData.CURRENCY_DATA.get(type).deposit(name, amount);
        }
    }

    public void reset(String type, String name) {
        if (CurrencyData.CURRENCY_DATA.containsKey(type)) {
            CurrencyData.CURRENCY_DATA.get(type).reset(name);
        }
    }

    public void withdraw(String type, String name, int amount) {
        if (CurrencyData.CURRENCY_DATA.containsKey(type)) {
            CurrencyData.CURRENCY_DATA.get(type).withdraw(name, amount);
        }
    }

    public void set(String type, String name, int amount) {
        if (CurrencyData.CURRENCY_DATA.containsKey(type)) {
            CurrencyData.CURRENCY_DATA.get(type).set(name, amount);
        }
    }

    public static NyEconomyAPI getInstance() {
        return Main.getNyEconomyAPI();
    }
}