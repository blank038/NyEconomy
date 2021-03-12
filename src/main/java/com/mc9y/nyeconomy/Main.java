package com.mc9y.nyeconomy;

import com.mc9y.nyeconomy.api.NyEconomyAPI;
import com.mc9y.nyeconomy.bridge.VaultBridge;
import com.mc9y.nyeconomy.command.NyeCommand;
import com.mc9y.nyeconomy.data.CurrencyData;
import com.mc9y.nyeconomy.data.TopCache;
import com.mc9y.nyeconomy.handler.AbstractStorgeHandler;
import com.mc9y.nyeconomy.handler.MySqlStorgeHandler;
import com.mc9y.nyeconomy.handler.YamlStorgeHandler;
import com.mc9y.nyeconomy.hook.PlaceholderHook;
import com.mc9y.nyeconomy.listener.PlayerListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Blank038
 */
public class Main extends JavaPlugin {
    private static NyEconomyAPI economyAPI;
    private static Main main;

    private boolean logStatus, debug;
    public List<String> vaults = new ArrayList<>();
    public String prefix;

    public static Main getInstance() {
        return main;
    }

    public static NyEconomyAPI getNyEconomyAPI() {
        return economyAPI;
    }

    public boolean isDebug() {
        return this.debug;
    }

    @Override
    public void onEnable() {
        economyAPI = new NyEconomyAPI();
        // 检测数据类型
        boolean useMySQL = "mysql".equalsIgnoreCase(this.getConfig().getString("data-option.type"));
        // 设置数据管理器
        AbstractStorgeHandler.setHandler(useMySQL ? MySqlStorgeHandler.class : YamlStorgeHandler.class);
        // 开启存储线程
        int saveDelay = getConfig().getInt("auto-save");
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            for (CurrencyData d : CurrencyData.CURRENCY_DATA.values()) {
                d.save();
            }
        }, 20L * (saveDelay > 0 ? saveDelay : 300), 20L * (saveDelay > 0 ? saveDelay : 300));
        Bukkit.getPluginManager().registerEvents(new PlayerListener(), this);
        super.getCommand("nye").setExecutor(new NyeCommand(this));
        // 挂钩 PlaceholderAPI
        if (Bukkit.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderHook(this).register();
        }
        // 注册排行榜
        new TopCache();
        // 设置状态
        logStatus = true;
        // 载入配置
        this.loadConfig();
    }

    @Override
    public void onDisable() {
        if (AbstractStorgeHandler.getHandler() != null) {
            AbstractStorgeHandler.getHandler().save();
        }
        VaultBridge.unregister();
    }

    @Override
    public void onLoad() {
        main = this;
        this.loadConfig();
    }

    public void loadConfig() {
        this.log("§b[NyEconomy]§f ================================");
        this.log("§b[NyEconomy]§f  §a> §b正在加载九域多经济系统...");
        getDataFolder().mkdir();
        saveDefaultConfig();
        reloadConfig();
        prefix = getConfig().getString("Message.Prefix").replace("&", "§");
        debug = getConfig().getBoolean("debug");
        // 输出 PlaceholderAPI 挂钩信息
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            this.log("§b[NyEconomy]§f  §a> §e成功挂钩 PlaceholderAPI §a✔");
        } else {
            this.log("§b[NyEconomy]§f  §a> §7未检测到 PlaceholderAPI §c✘");
        }
        // 输出 Vault 是否挂钩
        VaultBridge.register();
        VaultBridge.checkCurrencyFile();
        if (VaultBridge.getVaultBridge() != null) {
            this.log("§b[NyEconomy]§f  §a> §e成功挂钩 Vault 经济桥 §a✔");
        } else {
            this.log("§b[NyEconomy]§f  §a> §7未检测到 Vault 经济桥 §c✘");
        }
        // 检测是否含有 HikariCP
        if (this.hasHikariCP()) {
            this.log("§b[NyEconomy]§f  §a> §eHikariCP 状态: §a✔ ");
        } else {
            this.log("§b[NyEconomy]§f  §a> §7HikariCP 状态: §c✘");
        }
        this.loadCurrencies();
        this.loadCommodity();
        this.log("§b[NyEconomy]§f  §a> §b九域多经济系统加载完成");
        this.log("§b[NyEconomy]§f ================================");
        if (TopCache.getInstance() != null) {
            TopCache.getInstance().refreshTask();
        }
    }

    private void loadCurrencies() {
        // 载入货币列表
        File currencies = new File(getDataFolder(), "Currencies");
        currencies.mkdir();
        vaults.clear();
        CurrencyData.CURRENCY_DATA.clear();
        this.log("§b[NyEconomy]§f  §a> §e开始加载经济货币项");
        for (String i : getConfig().getStringList("Enable")) {
            File cf = new File(getDataFolder() + "/Currencies/", i + ".yml");
            if (cf.exists() || i.equals(getConfig().getString("economy-bridge.currency"))) {
                vaults.add(i);
                CurrencyData.CURRENCY_DATA.putIfAbsent(i, new CurrencyData(i));
                this.log("§b[NyEconomy]§f  §a -> §2成功加载货币项: §f" + i);
            } else {
                this.log("§b[NyEconomy]§f  §a -> §c货币项 §f" + i + " §c不存在");
            }
        }
        this.log("§b[NyEconomy]§f  §a> §e经济加载完成, 共加载 §f%amount% §e项"
                .replace("%amount%", vaults.size() + ""));
    }

    private void loadCommodity() {
        this.log("§b[NyEconomy]§f  §a> §e开始加载商店物品项");
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
        this.log("§b[NyEconomy]§f  §a> §e已加载商品 (§2成功§f[" + Commodity.COMMODITY_MAP.size() + "] §c失败§f[" + fails + "]§e)");
    }

    public boolean hasHikariCP() {
        try {
            Class.forName("com.zaxxer.hikari.HikariDataSource");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void log(String msg) {
        if (this.logStatus) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }
    }
}