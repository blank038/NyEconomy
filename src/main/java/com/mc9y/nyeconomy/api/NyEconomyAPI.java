package com.mc9y.nyeconomy.api;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.api.event.EconomyAction;
import com.mc9y.nyeconomy.api.event.PlayerEconomyChangeEvent;
import com.mc9y.nyeconomy.handler.AbstractStorgeHandler;
import com.mc9y.nyeconomy.service.UUIDService;
import com.mc9y.nyeconomy.util.Money;
import org.bukkit.Bukkit;

import java.math.BigDecimal;
import java.util.UUID;

public class NyEconomyAPI {

    public int getBalance(String type, String name) {
        return Money.toLegacyInt(getBalanceExact(type, name));
    }

    public BigDecimal getBalanceExact(String type, String name) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(name);
        return getBalanceExact(type, uuid);
    }

    public int getBalance(String type, UUID uuid) {
        return Money.toLegacyInt(getBalanceExact(type, uuid));
    }

    public BigDecimal getBalanceExact(String type, UUID uuid) {
        return AbstractStorgeHandler.getHandler().balanceExact(uuid, type, 2);
    }

    public String checkVaultType(String type) {
        if (Main.getInstance().getConfig().getBoolean("economy-bridge.enable")
                && Main.getInstance().getConfig().getString("economy-bridge.currency").equals(type)) {
            return "60CFC2D63B8F0E9D";
        }
        return type;
    }

    // ========== Event-firing methods (void for binary compat with v1.x) ==========

    public void deposit(String type, String name, int amount) {
        if (amount < 0) {
            return;
        }
        deposit(type, name, Money.of(amount));
    }

    public void deposit(String type, UUID uuid, int amount) {
        if (amount < 0) {
            return;
        }
        deposit(type, uuid, Money.of(amount));
    }

    public void deposit(String type, String name, BigDecimal amount) {
        tryDeposit(type, name, amount);
    }

    public void deposit(String type, UUID uuid, BigDecimal amount) {
        tryDeposit(type, uuid, amount);
    }

    public void withdraw(String type, String name, int amount) {
        if (amount < 0) {
            return;
        }
        withdraw(type, name, Money.of(amount));
    }

    public void withdraw(String type, UUID uuid, int amount) {
        if (amount < 0) {
            return;
        }
        withdraw(type, uuid, Money.of(amount));
    }

    public void withdraw(String type, String name, BigDecimal amount) {
        tryWithdraw(type, name, amount);
    }

    public void withdraw(String type, UUID uuid, BigDecimal amount) {
        tryWithdraw(type, uuid, amount);
    }

    public void set(String type, String name, int amount) {
        if (amount < 0) {
            return;
        }
        set(type, name, Money.of(amount));
    }

    public void set(String type, UUID uuid, int amount) {
        if (amount < 0) {
            return;
        }
        set(type, uuid, Money.of(amount));
    }

    public void set(String type, String name, BigDecimal amount) {
        trySet(type, name, amount);
    }

    public void set(String type, UUID uuid, BigDecimal amount) {
        trySet(type, uuid, amount);
    }

    public void reset(String type, String name) {
        tryReset(type, name);
    }

    public void reset(String type, UUID uuid) {
        tryReset(type, uuid);
    }

    // ========== Boolean-returning variants ==========

    public boolean tryDeposit(String type, String name, int amount) {
        return amount >= 0 && tryDeposit(type, name, Money.of(amount));
    }

    public boolean tryDeposit(String type, String name, BigDecimal amount) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(name);
        return tryDeposit(type, uuid, amount);
    }

    public boolean tryDeposit(String type, UUID uuid, int amount) {
        return amount >= 0 && tryDeposit(type, uuid, Money.of(amount));
    }

    public boolean tryDeposit(String type, UUID uuid, BigDecimal amount) {
        return performChange(uuid, type, EconomyAction.DEPOSIT, amount, false, false);
    }

    public boolean tryWithdraw(String type, String name, int amount) {
        return amount >= 0 && tryWithdraw(type, name, Money.of(amount));
    }

    public boolean tryWithdraw(String type, String name, BigDecimal amount) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(name);
        return tryWithdraw(type, uuid, amount);
    }

    public boolean tryWithdraw(String type, UUID uuid, int amount) {
        return amount >= 0 && tryWithdraw(type, uuid, Money.of(amount));
    }

    public boolean tryWithdraw(String type, UUID uuid, BigDecimal amount) {
        return performChange(uuid, type, EconomyAction.WITHDRAW, amount, false, false);
    }

    public boolean trySet(String type, String name, int amount) {
        return amount >= 0 && trySet(type, name, Money.of(amount));
    }

    public boolean trySet(String type, String name, BigDecimal amount) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(name);
        return trySet(type, uuid, amount);
    }

    public boolean trySet(String type, UUID uuid, int amount) {
        return amount >= 0 && trySet(type, uuid, Money.of(amount));
    }

    public boolean trySet(String type, UUID uuid, BigDecimal amount) {
        return performChange(uuid, type, EconomyAction.SET, amount, false, false);
    }

    public boolean tryReset(String type, String name) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(name);
        return tryReset(type, uuid);
    }

    public boolean tryReset(String type, UUID uuid) {
        return performChange(uuid, type, EconomyAction.RESET, Money.ZERO, false, false);
    }

    public boolean tryTransfer(String type, String sourceName, String targetName, int amount) {
        return amount > 0 && tryTransfer(type, sourceName, targetName, Money.of(amount));
    }

    public boolean tryTransfer(String type, String sourceName, String targetName, BigDecimal amount) {
        UUID source = UUIDService.getInstance().getPlayerUUID(sourceName);
        UUID target = UUIDService.getInstance().getPlayerUUID(targetName);
        return tryTransfer(type, source, target, amount);
    }

    public boolean tryTransfer(String type, UUID source, UUID target, BigDecimal amount) {
        amount = Money.normalize(amount);
        if (source == null || target == null || source.equals(target) || amount.signum() == 0) {
            return false;
        }

        BigDecimal sourceBalance = getBalanceExact(type, source);
        PlayerEconomyChangeEvent withdrawEvent = new PlayerEconomyChangeEvent(
                source, type, EconomyAction.WITHDRAW, amount, sourceBalance, false);
        Bukkit.getPluginManager().callEvent(withdrawEvent);
        if (withdrawEvent.isCancelled()) {
            return false;
        }

        final BigDecimal transferAmount;
        try {
            transferAmount = Money.normalize(withdrawEvent.getAmountExact());
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (transferAmount.signum() == 0) {
            return false;
        }
        BigDecimal targetBalance = getBalanceExact(type, target);
        PlayerEconomyChangeEvent depositEvent = new PlayerEconomyChangeEvent(
                target, type, EconomyAction.DEPOSIT, transferAmount, targetBalance, false);
        Bukkit.getPluginManager().callEvent(depositEvent);
        if (depositEvent.isCancelled()) {
            return false;
        }
        try {
            if (Money.normalize(depositEvent.getAmountExact()).compareTo(transferAmount) != 0) {
                return false;
            }
        } catch (IllegalArgumentException e) {
            return false;
        }

        return AbstractStorgeHandler.getHandler().transfer(source, target, type, transferAmount);
    }

    // ========== Silent methods (no events) ==========

    public void depositSilent(String type, String name, int amount) {
        if (amount < 0) {
            return;
        }
        depositSilent(type, name, Money.of(amount));
    }

    public void depositSilent(String type, String name, BigDecimal amount) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(name);
        depositSilent(type, uuid, amount);
    }

    public void depositSilent(String type, UUID uuid, int amount) {
        if (amount < 0) {
            return;
        }
        depositSilent(type, uuid, Money.of(amount));
    }

    public void depositSilent(String type, UUID uuid, BigDecimal amount) {
        AbstractStorgeHandler.getHandler().deposit(uuid, type, Money.normalize(amount));
    }

    public void withdrawSilent(String type, String name, int amount) {
        if (amount < 0) {
            return;
        }
        withdrawSilent(type, name, Money.of(amount));
    }

    public void withdrawSilent(String type, String name, BigDecimal amount) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(name);
        withdrawSilent(type, uuid, amount);
    }

    public void withdrawSilent(String type, UUID uuid, int amount) {
        if (amount < 0) {
            return;
        }
        withdrawSilent(type, uuid, Money.of(amount));
    }

    public void withdrawSilent(String type, UUID uuid, BigDecimal amount) {
        AbstractStorgeHandler.getHandler().withdraw(uuid, type, Money.normalize(amount));
    }

    public void setSilent(String type, String name, int amount) {
        if (amount < 0) {
            return;
        }
        setSilent(type, name, Money.of(amount));
    }

    public void setSilent(String type, String name, BigDecimal amount) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(name);
        setSilent(type, uuid, amount);
    }

    public void setSilent(String type, UUID uuid, int amount) {
        if (amount < 0) {
            return;
        }
        setSilent(type, uuid, Money.of(amount));
    }

    public void setSilent(String type, UUID uuid, BigDecimal amount) {
        AbstractStorgeHandler.getHandler().set(uuid, type, Money.normalize(amount));
    }

    public void resetSilent(String type, String name) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(name);
        resetSilent(type, uuid);
    }

    public void resetSilent(String type, UUID uuid) {
        AbstractStorgeHandler.getHandler().set(uuid, type, Money.ZERO);
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
        return amount >= 0 && performChange(uuid, type, action, Money.of(amount), silent, fromVault);
    }

    public boolean performChange(UUID uuid, String type, EconomyAction action, BigDecimal amount,
                                 boolean silent, boolean fromVault) {
        amount = Money.normalize(amount);
        if (!silent) {
            BigDecimal oldBalance = AbstractStorgeHandler.getHandler().balanceExact(uuid, type, 2);
            PlayerEconomyChangeEvent event = new PlayerEconomyChangeEvent(uuid, type, action, amount, oldBalance, fromVault);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return false;
            }
            try {
                amount = Money.normalize(event.getAmountExact());
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        if ((action == EconomyAction.DEPOSIT || action == EconomyAction.WITHDRAW) && amount.signum() == 0) {
            return false;
        }

        switch (action) {
            case DEPOSIT:
                return AbstractStorgeHandler.getHandler().deposit(uuid, type, amount);
            case WITHDRAW:
                return AbstractStorgeHandler.getHandler().withdraw(uuid, type, amount);
            case SET:
                return AbstractStorgeHandler.getHandler().set(uuid, type, amount);
            case RESET:
                return AbstractStorgeHandler.getHandler().set(uuid, type, Money.ZERO);
            default:
                return false;
        }
    }

    public static NyEconomyAPI getInstance() {
        return Main.getNyEconomyAPI();
    }
}
