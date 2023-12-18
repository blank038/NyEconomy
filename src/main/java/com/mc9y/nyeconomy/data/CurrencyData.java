package com.mc9y.nyeconomy.data;

import com.mc9y.nyeconomy.Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Blank038
 * @since 2021/03/07
 */
public class CurrencyData {
    public static final Map<String, CurrencyData> CURRENCY_DATA = new HashMap<>();

    private final String currencyName;
    private final FileConfiguration configuration;

    public CurrencyData(String name) {
        this.currencyName = name;
        File tarFile = new File(Main.getInstance().getDataFolder() + "/Currencies/", name + ".yml");
        configuration = YamlConfiguration.loadConfiguration(tarFile);
    }

    public void save() {
        try {
            File tarFile = new File(Main.getInstance().getDataFolder() + "/Currencies/", this.currencyName + ".yml");
            configuration.save(tarFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getUserBalance(String name) {
        return this.configuration.getInt(name, 0);
    }

    public Set<String> getAllPlayers() {
        return this.configuration.getKeys(false);
    }

    public void deposit(String name, int amount) {
        configuration.set(name, (configuration.getInt(name) + amount));
    }

    public void reset(String name) {
        configuration.set(name, 0);
    }

    public void withdraw(String name, int amount) {
        configuration.set(name, (Math.max(configuration.getInt(name) - amount, 0)));
    }

    public void set(String name, int amount) {
        configuration.set(name, (Math.max(amount, 0)));
    }
}