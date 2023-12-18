package com.mc9y.nyeconomy;

import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Blank038
 */
public class Commodity {
    public static final Map<String, Commodity> COMMODITY_MAP = new HashMap<>();

    private final String economyType;
    private final int amount;
    private final List<String> commands;
    private final String displayName;

    public Commodity(String type, int amount, List<String> commands, String name) {
        this.economyType = type;
        this.amount = amount;
        this.commands = commands;
        this.displayName = name;
    }

    public String getType() {
        return economyType;
    }

    public int getAmount() {
        return amount;
    }

    public List<String> getCommands() {
        return commands;
    }

    public void give(String name) {
        for (String command : getCommands()) {
            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", name));
        }
    }

    public String getName() {
        return displayName;
    }
}