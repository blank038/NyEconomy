package com.mc9y.nyeconomy.api.event;

import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public class PlayerEconomyChangeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final UUID playerUuid;
    private final String currencyType;
    private final EconomyAction action;
    private final double oldBalance;
    private final boolean fromVault;
    private double amount;
    private boolean cancelled;

    public PlayerEconomyChangeEvent(UUID playerUuid, String currencyType, EconomyAction action,
                                    int amount, int oldBalance, boolean fromVault) {
        this(playerUuid, currencyType, action, (double) amount, (double) oldBalance, fromVault);
    }

    public PlayerEconomyChangeEvent(UUID playerUuid, String currencyType, EconomyAction action,
                                    double amount, double oldBalance, boolean fromVault) {
        super(!Bukkit.isPrimaryThread());
        this.playerUuid = playerUuid;
        this.currencyType = currencyType;
        this.action = action;
        this.amount = amount;
        this.oldBalance = oldBalance;
        this.fromVault = fromVault;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getCurrencyType() {
        return currencyType;
    }

    public EconomyAction getAction() {
        return action;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public double getOldBalance() {
        return oldBalance;
    }

    public boolean isFromVault() {
        return fromVault;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean b) {
        this.cancelled = b;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
