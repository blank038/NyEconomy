package com.mc9y.nyeconomy.data;

import com.mc9y.nyeconomy.Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

/**
 * @author Blank038
 * @since 2021/03/07
 */
public class CurrencyData {
    public static final HashMap<String, CurrencyData> CURRENCY_DATA = new HashMap<>();

    private final File TAR_FILE;
    private final FileConfiguration CONFIGURATION;

    public CurrencyData(String name) {
        TAR_FILE = new File(Main.getInstance().getDataFolder() + "/Currencies/", name + ".yml");
        CONFIGURATION = YamlConfiguration.loadConfiguration(TAR_FILE);
    }

    public void save() {
        try {
            CONFIGURATION.save(TAR_FILE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getUserBalance(String name) {
        if (CONFIGURATION.contains(name)) {
            return CONFIGURATION.getInt(name);
        } else {
            return 0;
        }
    }

    public void deposit(String name, int amount) {
        CONFIGURATION.set(name, (CONFIGURATION.getInt(name) + amount));
    }

    public void reset(String name) {
        CONFIGURATION.set(name, 0);
    }

    public void withdraw(String name, int amount) {
        CONFIGURATION.set(name, (Math.max(CONFIGURATION.getInt(name) - amount, 0)));
    }

    public void set(String name, int amount) {
        CONFIGURATION.set(name, (Math.max(amount, 0)));
    }
}