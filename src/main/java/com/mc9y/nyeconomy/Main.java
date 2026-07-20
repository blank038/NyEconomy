package com.mc9y.nyeconomy;

import com.aystudio.core.bukkit.plugin.AyPlugin;
import com.aystudio.core.bukkit.util.common.TextUtil;
import com.mc9y.nyeconomy.api.NyEconomyAPI;
import com.mc9y.nyeconomy.bridge.VaultBridge;
import com.mc9y.nyeconomy.cache.StateCache;
import com.mc9y.nyeconomy.command.NyeCommand;
import com.mc9y.nyeconomy.cron.CronResetManager;
import com.mc9y.nyeconomy.data.TopCache;
import com.mc9y.nyeconomy.handler.AbstractStorgeHandler;
import com.mc9y.nyeconomy.handler.MySqlStorgeHandler;
import com.mc9y.nyeconomy.handler.SqliteStorageHandler;
import com.mc9y.nyeconomy.hook.PlaceholderHook;
import com.mc9y.nyeconomy.listener.PlayerListener;
import com.mc9y.nyeconomy.migration.BalanceSchemaMigrator;
import com.mc9y.nyeconomy.util.Money;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

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

    private String getStorageType() {
        return this.getConfig().getString("data-option.type", "sqlite").trim();
    }

    public boolean isMySqlMode() {
        return "mysql".equalsIgnoreCase(this.getStorageType());
    }

    @Override
    public void onEnable() {
        instance = this;
        economyAPI = new NyEconomyAPI();

        this.getConsoleLogger().setPrefix("");
        saveDefaultConfig();
        reloadConfig();
        debug = getConfig().getBoolean("debug");

        try {
            BalanceSchemaMigrator.migrate(this);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "余额数据库自动迁移失败，插件已停止启用", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        try {
            if (this.isMySqlMode()) {
                AbstractStorgeHandler.setHandler(MySqlStorgeHandler.class);
            } else {
                AbstractStorgeHandler.setHandler(SqliteStorageHandler.class);
            }
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "存储处理器初始化失败，插件已停止启用", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (AbstractStorgeHandler.getHandler() == null) {
            getLogger().severe("存储处理器初始化失败，插件已停止启用");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.loadConfig();
        
        Bukkit.getPluginManager().registerEvents(new PlayerListener(), this);
        this.getCommand("nye").setExecutor(new NyeCommand(this));

        if (Bukkit.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderHook(this).register();
        }
        new TopCache();
        CronResetManager.initialize();
    }

    @Override
    public void onDisable() {
        CronResetManager.shutdown();
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
        CronResetManager.reload();
        print(" ");
    }

    private void loadCurrencies() {
        vaults.clear();

        List<String> enabled = getConfig().getStringList("enable");
        if (enabled.isEmpty()) {
            this.print("&c * &fCurrencies: &cEMPTY");
            return;
        }
        this.print("&6 * &fCurrencies:");
        for (String i : enabled) {
            this.vaults.add(i);
            this.print("&e   + &f" + i + ": &aON");
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
            if (!vaults.contains(data.getString(key + ".type"))) {
                fails++;
                continue;
            }
            try {
                Commodity commodity = new Commodity(data.getString(key + ".type"),
                        Money.parseConfig(data.get(key + ".amount")), data.getStringList(key + ".commands"),
                        data.getString(key + ".name"));
                if (commodity.getAmountExact().signum() == 0) {
                    throw new IllegalArgumentException("商品价格必须大于 0");
                }
                Commodity.COMMODITY_MAP.put(key, commodity);
            } catch (IllegalArgumentException e) {
                fails++;
                getLogger().warning("商品 " + key + " 的价格无效: " + e.getMessage());
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
