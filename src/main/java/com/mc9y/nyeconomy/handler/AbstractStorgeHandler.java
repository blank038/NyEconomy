package com.mc9y.nyeconomy.handler;

import com.mc9y.nyeconomy.data.AccountCache;

import java.util.UUID;

@SuppressWarnings(value = {"UnusedReturnValue"})
public abstract class AbstractStorgeHandler {
    private static AbstractStorgeHandler INSTANCE;

    public abstract int balance(UUID uuid, String type, int status);

    public abstract boolean deposit(UUID uuid, String type, int amount);

    public abstract boolean withdraw(UUID uuid, String type, int amount);

    public abstract boolean set(UUID uuid, String type, int amount);

    public abstract void save();

    public abstract void refreshTop();

    public abstract void updatePlayerMapping(UUID uuid, String name);

    public abstract UUID queryPlayerUUID(String playerName);

    public abstract String queryPlayerName(UUID uuid);

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