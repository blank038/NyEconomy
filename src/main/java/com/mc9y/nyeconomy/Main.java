package com.mc9y.nyeconomy;

import com.mc9y.nyeconomy.api.NyEconomyAPI;
import com.mc9y.nyeconomy.command.NyeCommand;
import com.mc9y.nyeconomy.data.CurrencyData;
import com.mc9y.nyeconomy.handler.AbstractStorgeHandler;
import com.mc9y.nyeconomy.handler.MySqlStorgeHandler;
import com.mc9y.nyeconomy.handler.YamlStorgeHandler;
import com.mc9y.nyeconomy.hook.PlaceholderHook;
import com.mc9y.nyeconomy.listener.PlayerListener;
import org.bukkit.Bukkit;
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

    private boolean papi = false;
    public List<String> vaults = new ArrayList<>();
    public String prefix;

    public static Main getInstance() {
        return main;
    }

    public static NyEconomyAPI getNyEconomyAPI() {
        return economyAPI;
    }

    @Override
    public void onEnable() {
        main = this;
        economyAPI = new NyEconomyAPI();
        this.loadConfig();
        // 检测数据类型
        boolean useMySQL = "mysql".equalsIgnoreCase(this.getConfig().getString("data-option.type"));
        if (!hasHikariCP()) {
            Bukkit.getConsoleSender().sendMessage("§c ✘ §f未找到 HikariCP §f已取消加载.");
            this.setEnabled(false);
            return;
        }
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
            papi = true;
        }
    }

    @Override
    public void onDisable() {
        for (CurrencyData d : CurrencyData.CURRENCY_DATA.values()) {
            d.save();
        }
    }

    public void loadConfig() {
        Bukkit.getConsoleSender().sendMessage("§b[NyEconomy]§f ================================");
        Bukkit.getConsoleSender().sendMessage("§b[NyEconomy]§f  §a> §b正在加载九域多经济系统...");
        getDataFolder().mkdir();
        saveDefaultConfig();
        reloadConfig();
        prefix = getConfig().getString("Message.Prefix").replace("&", "§");
        // 载入货币列表
        File currencies = new File(getDataFolder(), "Currencies");
        currencies.mkdir();
        vaults.clear();
        CurrencyData.CURRENCY_DATA.clear();
        Bukkit.getConsoleSender().sendMessage("§b[NyEconomy]§f  §a> §e开始加载经济货币项");
        for (String i : getConfig().getStringList("Enable")) {
            File cf = new File(getDataFolder() + "/Currencies/", i + ".yml");
            if (cf.exists()) {
                vaults.add(i);
                CurrencyData.CURRENCY_DATA.put(i, new CurrencyData(i));
                Bukkit.getConsoleSender().sendMessage("§b[NyEconomy]§f  §a -> §2成功加载货币项: §f" + i);
            } else {
                Bukkit.getConsoleSender().sendMessage("§b[NyEconomy]§f  §a -> §c货币项 §f" + i + " §c不存在");
            }
        }
        Bukkit.getConsoleSender().sendMessage("§b[NyEconomy]§f  §a> §e经济加载完成, 共加载 §f%amount% §e项"
                .replace("%amount%", vaults.size() + ""));
        Bukkit.getConsoleSender().sendMessage("§b[NyEconomy]§f  §a> §e开始加载商店物品项");
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
        Bukkit.getConsoleSender().sendMessage("§b[NyEconomy]§f  §a> §e已加载商品 (§2成功§f[" + Commodity.COMMODITY_MAP.size() + "] §c失败§f[" + fails + "]§e)");
        if (papi) {
            Bukkit.getConsoleSender().sendMessage("§b[NyEconomy]§f  §a> §e成功挂钩PlaceholderAPI §a✔");
        } else {
            Bukkit.getConsoleSender().sendMessage("§b[NyEconomy]§f  §a> §7未检测到PlaceholderAPI §c✘");
        }
        Bukkit.getConsoleSender().sendMessage("§b[NyEconomy]§f  §a> §b九域多经济系统加载完成");
        Bukkit.getConsoleSender().sendMessage("§b[NyEconomy]§f ================================");
    }

    private boolean hasHikariCP() {
        try {
            Class.forName("com.zaxxer.hikari.HikariDataSource");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}