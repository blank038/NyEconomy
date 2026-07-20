package com.mc9y.nyeconomy.handler;

import com.mc9y.nyeconomy.data.AccountCache;
import com.mc9y.nyeconomy.util.Money;

import java.math.BigDecimal;
import java.util.UUID;

@SuppressWarnings(value = {"UnusedReturnValue"})
public abstract class AbstractStorgeHandler {
    private static AbstractStorgeHandler INSTANCE;

    public abstract int balance(UUID uuid, String type, int status);

    public BigDecimal balanceExact(UUID uuid, String type, int status) {
        return Money.of(balance(uuid, type, status));
    }

    public abstract boolean deposit(UUID uuid, String type, int amount);

    public boolean deposit(UUID uuid, String type, BigDecimal amount) {
        return deposit(uuid, type, Money.toWholeIntExact(amount));
    }

    public abstract boolean withdraw(UUID uuid, String type, int amount);

    public boolean withdraw(UUID uuid, String type, BigDecimal amount) {
        return withdraw(uuid, type, Money.toWholeIntExact(amount));
    }

    public abstract boolean set(UUID uuid, String type, int amount);

    public boolean set(UUID uuid, String type, BigDecimal amount) {
        return set(uuid, type, Money.toWholeIntExact(amount));
    }

    public boolean transfer(UUID source, UUID target, String type, BigDecimal amount) {
        return false;
    }

    public abstract void save();

    public abstract void refreshTop();

    public abstract void updatePlayerMapping(UUID uuid, String name);

    public abstract UUID queryPlayerUUID(String playerName);

    public abstract String queryPlayerName(UUID uuid);

    public abstract int depositAll(String type, int amount);

    public int depositAll(String type, BigDecimal amount) {
        return depositAll(type, Money.toWholeIntExact(amount));
    }

    public abstract int resetAll(String type);

    public AccountCache getPlayerCache(UUID uuid) {
        return null;
    }

    public boolean isExists(UUID uuid) {
        return true;
    }

    public static void setHandler(Class<? extends AbstractStorgeHandler> handler) {
        try {
            INSTANCE = handler.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public static AbstractStorgeHandler getHandler() {
        return INSTANCE;
    }
}
