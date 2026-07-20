package com.mc9y.nyeconomy.bridge;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.api.event.EconomyAction;
import com.mc9y.nyeconomy.cache.StateCache;
import com.mc9y.nyeconomy.handler.AbstractStorgeHandler;
import com.mc9y.nyeconomy.service.UUIDService;
import com.mc9y.nyeconomy.util.Money;
import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
public class VaultBridge extends AbstractEconomy {
    private static VaultBridge vaultBridge;

    private final String currency;

    public VaultBridge() {
        vaultBridge = this;
        this.currency = Main.getInstance().getConfig().getString("economy-bridge.currency");
    }

    public String getCurrencyName() {
        return this.currency;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return Main.getInstance().getDescription().getName();
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return Money.SCALE;
    }

    @Override
    public String format(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            throw new IllegalArgumentException("金额必须是有限数字");
        }
        return BigDecimal.valueOf(v).setScale(Money.SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    @Override
    public String currencyNamePlural() {
        return Main.getInstance().getConfig().getString("economy-bridge.plural");
    }

    @Override
    public String currencyNameSingular() {
        return Main.getInstance().getConfig().getString("economy-bridge.singular");
    }

    @Override
    public boolean hasAccount(String s) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(s);
        return AbstractStorgeHandler.getHandler().isExists(uuid);
    }

    @Override
    public boolean hasAccount(OfflinePlayer s) {
        return AbstractStorgeHandler.getHandler().isExists(s.getUniqueId());
    }

    @Override
    public boolean hasAccount(String s, String s1) {
        return this.hasAccount(s);
    }

    @Override
    public boolean hasAccount(OfflinePlayer s, String s1) {
        return this.hasAccount(s);
    }

    @Override
    public double getBalance(String s) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(s);
        return AbstractStorgeHandler.getHandler().balanceExact(uuid, this.currency, 2).doubleValue();
    }

    @Override
    public double getBalance(OfflinePlayer s) {
        return AbstractStorgeHandler.getHandler().balanceExact(s.getUniqueId(), this.currency, 2).doubleValue();
    }

    @Override
    public double getBalance(String s, String s1) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(s);
        return AbstractStorgeHandler.getHandler().balanceExact(uuid, this.currency, 2).doubleValue();
    }

    @Override
    public boolean has(String s, double v) {
        try {
            BigDecimal amount = Money.fromVault(v);
            UUID uuid = UUIDService.getInstance().getPlayerUUID(s);
            return AbstractStorgeHandler.getHandler().balanceExact(uuid, this.currency, 2).compareTo(amount) >= 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public boolean has(String s, String s1, double v) {
        return this.has(s, v);
    }

    @Override
    public EconomyResponse withdrawPlayer(String s, double v) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(s);
        return withdraw(uuid, v);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return withdraw(player.getUniqueId(), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String s, String s1, double v) {
        return this.withdrawPlayer(s, v);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return this.withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String s, double v) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(s);
        return deposit(uuid, v);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return deposit(player.getUniqueId(), amount);
    }

    @Override
    public EconomyResponse depositPlayer(String s, String s1, double v) {
        return this.depositPlayer(s, v);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return this.depositPlayer(player, amount);
    }

    private EconomyResponse withdraw(UUID uuid, double input) {
        BigDecimal balance = AbstractStorgeHandler.getHandler().balanceExact(uuid, this.currency, 2);
        final BigDecimal amount;
        try {
            amount = Money.fromVault(input);
        } catch (IllegalArgumentException e) {
            return new EconomyResponse(0.0, balance.doubleValue(), EconomyResponse.ResponseType.FAILURE,
                    e.getMessage());
        }

        boolean result = Main.getNyEconomyAPI().performChange(
                uuid, this.currency, EconomyAction.WITHDRAW, amount, false, true);
        balance = AbstractStorgeHandler.getHandler().balanceExact(uuid, this.currency, 2);
        return new EconomyResponse(amount.doubleValue(), balance.doubleValue(),
                result ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE,
                result ? "" : "余额不足或操作被取消");
    }

    private EconomyResponse deposit(UUID uuid, double input) {
        BigDecimal balance = AbstractStorgeHandler.getHandler().balanceExact(uuid, this.currency, 2);
        final BigDecimal amount;
        try {
            amount = Money.fromVault(input);
        } catch (IllegalArgumentException e) {
            return new EconomyResponse(0.0, balance.doubleValue(), EconomyResponse.ResponseType.FAILURE,
                    e.getMessage());
        }

        boolean result = Main.getNyEconomyAPI().performChange(
                uuid, this.currency, EconomyAction.DEPOSIT, amount, false, true);
        balance = AbstractStorgeHandler.getHandler().balanceExact(uuid, this.currency, 2);
        return new EconomyResponse(amount.doubleValue(), balance.doubleValue(),
                result ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE,
                result ? "" : "操作被取消");
    }

    @Override
    public EconomyResponse createBank(String s, String s1) {
        return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "目前没有银行功能");
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return this.createBank(name, player.getName());
    }

    @Override
    public EconomyResponse deleteBank(String s) {
        return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "目前没有银行功能");
    }

    @Override
    public EconomyResponse bankBalance(String s) {
        return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "目前没有银行功能");
    }

    @Override
    public EconomyResponse bankHas(String s, double v) {
        return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "目前没有银行功能");
    }

    @Override
    public EconomyResponse bankWithdraw(String s, double v) {
        return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "目前没有银行功能");
    }

    @Override
    public EconomyResponse bankDeposit(String s, double v) {
        return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "目前没有银行功能");
    }

    @Override
    public EconomyResponse isBankOwner(String s, String s1) {
        return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "目前没有银行功能");
    }

    @Override
    public EconomyResponse isBankMember(String s, String s1) {
        return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "目前没有银行功能");
    }

    @Override
    public List<String> getBanks() {
        return new ArrayList<>();
    }

    @Override
    public boolean createPlayerAccount(String s) {
        return false;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return this.createPlayerAccount(player.getName());
    }

    @Override
    public boolean createPlayerAccount(String s, String s1) {
        return this.createPlayerAccount(s);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return this.createPlayerAccount(player.getName());
    }

    public static void register() {
        if (vaultBridge == null && Main.getInstance().getConfig().getBoolean("economy-bridge.enable")) {
            Bukkit.getServicesManager().register(Economy.class, new VaultBridge(), Main.getInstance(), ServicePriority.Highest);
            StateCache.vaultState = true;
        }
    }

    public static VaultBridge getVaultBridge() {
        return vaultBridge;
    }

    public static void unregister() {
        if (vaultBridge != null) {
            Bukkit.getServicesManager().unregister(Economy.class, vaultBridge);
        }
    }

    public static void checkCurrencyFile() {
        if (Main.getInstance().getConfig().getBoolean("economy-bridge.enable")) {
            String currency = Main.getInstance().getConfig().getString("economy-bridge.currency");
            if (!Main.getInstance().vaults.contains(currency)) {
                Main.getInstance().vaults.add(currency);
            }
        }
    }
}
