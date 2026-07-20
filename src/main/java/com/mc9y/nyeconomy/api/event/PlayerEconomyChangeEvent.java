package com.mc9y.nyeconomy.api.event;

import com.mc9y.nyeconomy.util.Money;
import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.math.BigDecimal;
import java.util.UUID;

public class PlayerEconomyChangeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final UUID playerUuid;
    private final String currencyType;
    private final EconomyAction action;
    private final BigDecimal oldBalance;
    private final boolean fromVault;
    private BigDecimal amount;
    private boolean cancelled;

    public PlayerEconomyChangeEvent(UUID playerUuid, String currencyType, EconomyAction action,
                                    int amount, int oldBalance, boolean fromVault) {
        this(playerUuid, currencyType, action, Money.of(amount), Money.of(oldBalance), fromVault);
    }

    public PlayerEconomyChangeEvent(UUID playerUuid, String currencyType, EconomyAction action,
                                    BigDecimal amount, BigDecimal oldBalance, boolean fromVault) {
        super(!Bukkit.isPrimaryThread());
        this.playerUuid = playerUuid;
        this.currencyType = currencyType;
        this.action = action;
        this.amount = Money.normalize(amount);
        this.oldBalance = Money.normalize(oldBalance);
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

    public int getAmount() {
        return Money.toLegacyInt(amount);
    }

    public void setAmount(int amount) {
        this.amount = Money.of(amount);
    }

    public BigDecimal getAmountExact() {
        return amount;
    }

    public void setAmountExact(BigDecimal amount) {
        this.amount = Money.normalize(amount);
    }

    public int getOldBalance() {
        return Money.toLegacyInt(oldBalance);
    }

    public BigDecimal getOldBalanceExact() {
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
