package com.mc9y.nyeconomy.data;

import com.mc9y.nyeconomy.Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Blank038
 * @since 2021/03/07
 */
public class CurrencyData {
    public static final Map<String, CurrencyData> CURRENCY_DATA = new HashMap<>();

    private final File tarFile;
    private final FileConfiguration configuration;

    public CurrencyData(String name) {
        tarFile = new File(Main.getInstance().getDataFolder() + "/Currencies/", name + ".yml");
        configuration = YamlConfiguration.loadConfiguration(tarFile);
    }

    public void save() {
        try {
            configuration.save(tarFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getUserBalance(String name) {
        if (configuration.contains(name)) {
            return configuration.getInt(name);
        } else {
            return 0;
        }
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