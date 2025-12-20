package com.mc9y.nyeconomy.api;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.handler.AbstractStorgeHandler;
import com.mc9y.nyeconomy.service.UUIDService;

import java.util.UUID;

public class NyEconomyAPI {

    public int getBalance(String type, String name) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(name);
        return getBalance(type, uuid);
    }

    public int getBalance(String type, UUID uuid) {
        return AbstractStorgeHandler.getHandler().balance(uuid, type, 2);
    }

    public String checkVaultType(String type) {
        if (Main.getInstance().getConfig().getBoolean("economy-bridge.enable")
                && Main.getInstance().getConfig().getString("economy-bridge.currency").equals(type)) {
            return "60CFC2D63B8F0E9D";
        }
        return type;
    }

    public void deposit(String type, String name, int amount) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(name);
        deposit(type, uuid, amount);
    }

    public void deposit(String type, UUID uuid, int amount) {
        AbstractStorgeHandler.getHandler().deposit(uuid, type, amount);
    }

    public void reset(String type, String name) {
        this.set(type, name, 0);
    }

    public void reset(String type, UUID uuid) {
        this.set(type, uuid, 0);
    }

    public void withdraw(String type, String name, int amount) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(name);
        withdraw(type, uuid, amount);
    }

    public void withdraw(String type, UUID uuid, int amount) {
        AbstractStorgeHandler.getHandler().withdraw(uuid, type, amount);
    }

    public void set(String type, String name, int amount) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(name);
        set(type, uuid, amount);
    }

    public void set(String type, UUID uuid, int amount) {
        AbstractStorgeHandler.getHandler().set(uuid, type, amount);
    }

    public static NyEconomyAPI getInstance() {
        return Main.getNyEconomyAPI();
    }
}