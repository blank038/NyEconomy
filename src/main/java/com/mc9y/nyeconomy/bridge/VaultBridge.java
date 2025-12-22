package com.mc9y.nyeconomy.bridge;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.cache.StateCache;
import com.mc9y.nyeconomy.handler.AbstractStorgeHandler;
import com.mc9y.nyeconomy.service.UUIDService;
import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;

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
        return 0;
    }

    @Override
    public String format(double v) {
        return String.format("%.0f", v);
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
        return AbstractStorgeHandler.getHandler().balance(uuid, this.currency, 2);
    }

    @Override
    public double getBalance(OfflinePlayer s) {
        return AbstractStorgeHandler.getHandler().balance(s.getUniqueId(), this.currency, 2);
    }

    @Override
    public double getBalance(String s, String s1) {
        UUID uuid = UUIDService.getInstance().getPlayerUUID(s);
        return AbstractStorgeHandler.getHandler().balance(uuid, this.currency, 2);
    }

    @Override
    public boolean has(String s, double v) {
        return this.getBalance(s) >= v;
    }

    @Override
    public boolean has(String s, String s1, double v) {
        return this.has(s, v);
    }

    @Override
    public EconomyResponse withdrawPlayer(String s, double v) {
        if (v < 0.0) {
            return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "数字小于0");
        }
        UUID uuid = UUIDService.getInstance().getPlayerUUID(s);
        boolean result = AbstractStorgeHandler.getHandler().withdraw(uuid, this.currency, (int) v);
        int balance = AbstractStorgeHandler.getHandler().balance(uuid, this.currency, 2);
        return new EconomyResponse(v, balance,
                result ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE, "余额不足");
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (amount < 0.0) {
            return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "数字小于0");
        }
        UUID uuid = player.getUniqueId();
        boolean result = AbstractStorgeHandler.getHandler().withdraw(uuid, this.currency, (int) amount);
        int balance = AbstractStorgeHandler.getHandler().balance(uuid, this.currency, 2);
        return new EconomyResponse(amount, balance,
                result ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE, "余额不足");
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
        if (v < 0.0) {
            return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "数字小于0");
        }
        UUID uuid = UUIDService.getInstance().getPlayerUUID(s);
        boolean result = AbstractStorgeHandler.getHandler().deposit(uuid, this.currency, (int) v);
        int balance = AbstractStorgeHandler.getHandler().balance(uuid, this.currency, 2);
        return new EconomyResponse(v, balance,
                result ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE, "出现异常");
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (amount < 0.0) {
            return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "数字小于0");
        }
        UUID uuid = player.getUniqueId();
        boolean result = AbstractStorgeHandler.getHandler().deposit(uuid, this.currency, (int) amount);
        int balance = AbstractStorgeHandler.getHandler().balance(uuid, this.currency, 2);
        return new EconomyResponse(amount, balance,
                result ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE, "出现异常");
    }

    @Override
    public EconomyResponse depositPlayer(String s, String s1, double v) {
        return this.depositPlayer(s, v);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return this.depositPlayer(player, amount);
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