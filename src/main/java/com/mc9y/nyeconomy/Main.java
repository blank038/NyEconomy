package com.mc9y.nyeconomy;

import com.aystudio.core.bukkit.plugin.AyPlugin;
import com.aystudio.core.bukkit.util.common.TextUtil;
import com.mc9y.nyeconomy.api.NyEconomyAPI;
import com.mc9y.nyeconomy.bridge.VaultBridge;
import com.mc9y.nyeconomy.cache.StateCache;
import com.mc9y.nyeconomy.command.NyeCommand;
import com.mc9y.nyeconomy.data.TopCache;
import com.mc9y.nyeconomy.handler.AbstractStorgeHandler;
import com.mc9y.nyeconomy.handler.MySqlStorgeHandler;
import com.mc9y.nyeconomy.handler.SqliteStorageHandler;
import com.mc9y.nyeconomy.hook.PlaceholderHook;
import com.mc9y.nyeconomy.listener.PlayerListener;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Main extends AyPlugin {
    private static NyEconomyAPI economyAPI;
    private static Main instance;

    private boolean debug;
    public List<String> vaults = new ArrayList<>();

    public static Main getInstance() {
        return instance;
    }

    public static NyEconomyAPI getNyEconomyAPI() {
        return economyAPI;
    }

    public boolean isDebug() {
        return this.debug;
    }

    @Override
    public void onEnable() {
        instance = this;
        economyAPI = new NyEconomyAPI();

        this.getConsoleLogger().setPrefix("");
        this.loadConfig();
        
        String storageType = this.getConfig().getString("data-option.type", "sqlite").toLowerCase();
        if ("mysql".equals(storageType)) {
            AbstractStorgeHandler.setHandler(MySqlStorgeHandler.class);
        } else {
            AbstractStorgeHandler.setHandler(SqliteStorageHandler.class);
        }
        
        Bukkit.getPluginManager().registerEvents(new PlayerListener(), this);
        this.getCommand("nye").setExecutor(new NyeCommand(this));

        if (Bukkit.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderHook(this).register();
        }
        new TopCache();
    }

    @Override
    public void onDisable() {
        if (AbstractStorgeHandler.getHandler() != null) {
            AbstractStorgeHandler.getHandler().save();
        }
        if (StateCache.vaultState) {
            VaultBridge.unregister();
        }
    }

    public void loadConfig() {
        print(" ");
        print("   &3NyEconomy &bv" + this.getDescription().getVersion());
        print(" ");

        saveDefaultConfig();
        reloadConfig();
        debug = getConfig().getBoolean("debug");

        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            VaultBridge.register();
            VaultBridge.checkCurrencyFile();
        }

        this.print("&6 * &fPlaceholderAPI: " + (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null ? "&aON" : "&cOFF"));
        this.print("&6 * &fVault: " + (StateCache.vaultState ? "&aON" : "&cOFF"));
        this.print("&6 * &fHikariCP: " + (this.hasHikariCP() ? "&aON" : "&cOFF"));

        this.loadCurrencies();
        this.loadCommodity();
        if (TopCache.getInstance() != null) {
            TopCache.getInstance().refreshTask();
        }
        print(" ");
    }

    private void loadCurrencies() {
        File currencies = new File(getDataFolder(), "Currencies");
        currencies.mkdir();
        vaults.clear();

        List<String> enabled = getConfig().getStringList("enable");
        if (enabled.isEmpty()) {
            this.print("&c * &fCurrencies: &cEMPTY");
            return;
        }
        this.print("&6 * &fCurrencies:");
        for (String i : enabled) {
            File cf = new File(currencies, i + ".yml");
            boolean success = cf.exists() || i.equals(getConfig().getString("economy-bridge.currency"));
            if (success) this.vaults.add(i);
            this.print("&e   + &f" + i + ": " + (success ? "&aON" : "&cOFF"));
        }
    }

    private void loadCommodity() {
        Commodity.COMMODITY_MAP.clear();
        File shop = new File(getDataFolder(), "shop.yml");
        if (!shop.exists()) {
            saveResource("shop.yml", true);
        }
        FileConfiguration data = YamlConfiguration.loadConfiguration(shop);
        int fails = 0;
        for (String key : data.getKeys(false)) {
            if (vaults.contains(data.getString(key + ".type"))) {
                Commodity commodity = new Commodity(data.getString(key + ".type"), data.getInt(key + ".amount"),
                        data.getStringList(key + ".commands"), data.getString(key + ".name"));
                Commodity.COMMODITY_MAP.put(key, commodity);
            } else {
                fails++;
            }
        }
        this.print("&6 * &fCommodity: &a" + Commodity.COMMODITY_MAP.size() + " &f/ &c" + fails);
    }

    public boolean hasHikariCP() {
        try {
            Class.forName("com.zaxxer.hikari.HikariConfig", false, this.getClassLoader());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void print(String msg) {
        this.getConsoleLogger().log(msg);
    }

    public static String getString(String key, boolean... prefix) {
        String message = instance.getConfig().getString(key, "");
        if (prefix.length > 0 && prefix[0]) {
            message = instance.getConfig().getString("message.prefix") + message;
        }
        return TextUtil.formatHexColor(message);
    }
}