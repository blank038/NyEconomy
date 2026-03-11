package com.mc9y.nyeconomy.api;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.api.event.EconomyAction;
import com.mc9y.nyeconomy.api.event.PlayerEconomyChangeEvent;
import com.mc9y.nyeconomy.handler.AbstractStorgeHandler;
import com.mc9y.nyeconomy.service.UUIDService;
import org.bukkit.Bukkit;

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

    // ========== Event-firing methods ==========

    public boolean deposit(String type, String name, int amount) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(name);
        return deposit(type, uuid, amount);
    }

    public boolean deposit(String type, UUID uuid, int amount) {
        return performChange(uuid, type, EconomyAction.DEPOSIT, amount, false, false);
    }

    public boolean withdraw(String type, String name, int amount) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(name);
        return withdraw(type, uuid, amount);
    }

    public boolean withdraw(String type, UUID uuid, int amount) {
        return performChange(uuid, type, EconomyAction.WITHDRAW, amount, false, false);
    }

    public boolean set(String type, String name, int amount) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(name);
        return set(type, uuid, amount);
    }

    public boolean set(String type, UUID uuid, int amount) {
        return performChange(uuid, type, EconomyAction.SET, amount, false, false);
    }

    public boolean reset(String type, String name) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(name);
        return reset(type, uuid);
    }

    public boolean reset(String type, UUID uuid) {
        return performChange(uuid, type, EconomyAction.RESET, 0, false, false);
    }

    // ========== Silent methods (no events) ==========

    public void depositSilent(String type, String name, int amount) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(name);
        depositSilent(type, uuid, amount);
    }

    public void depositSilent(String type, UUID uuid, int amount) {
        AbstractStorgeHandler.getHandler().deposit(uuid, type, amount);
    }

    public void withdrawSilent(String type, String name, int amount) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(name);
        withdrawSilent(type, uuid, amount);
    }

    public void withdrawSilent(String type, UUID uuid, int amount) {
        AbstractStorgeHandler.getHandler().withdraw(uuid, type, amount);
    }

    public void setSilent(String type, String name, int amount) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(name);
        setSilent(type, uuid, amount);
    }

    public void setSilent(String type, UUID uuid, int amount) {
        AbstractStorgeHandler.getHandler().set(uuid, type, amount);
    }

    public void resetSilent(String type, String name) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(name);
        resetSilent(type, uuid);
    }

    public void resetSilent(String type, UUID uuid) {
        AbstractStorgeHandler.getHandler().set(uuid, type, 0);
    }

    // ========== Core method ==========

    /**
     * Perform an economy change with optional event firing.
     *
     * @param uuid      player UUID
     * @param type      currency type
     * @param action    economy action
     * @param amount    amount
     * @param silent    if true, no event is fired
     * @param fromVault if true, the change originated from the Vault bridge
     * @return true if the operation succeeded, false if cancelled or failed
     */
    public boolean performChange(UUID uuid, String type, EconomyAction action, int amount, boolean silent, boolean fromVault) {
        if (!silent) {
            int oldBalance = AbstractStorgeHandler.getHandler().balance(uuid, type, 2);
            PlayerEconomyChangeEvent event = new PlayerEconomyChangeEvent(uuid, type, action, amount, oldBalance, fromVault);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return false;
            }
            amount = event.getAmount();
        }

        switch (action) {
            case DEPOSIT:
                return AbstractStorgeHandler.getHandler().deposit(uuid, type, amount);
            case WITHDRAW:
                return AbstractStorgeHandler.getHandler().withdraw(uuid, type, amount);
            case SET:
                return AbstractStorgeHandler.getHandler().set(uuid, type, amount);
            case RESET:
                return AbstractStorgeHandler.getHandler().set(uuid, type, 0);
            default:
                return false;
        }
    }

    public static NyEconomyAPI getInstance() {
        return Main.getNyEconomyAPI();
    }
}
