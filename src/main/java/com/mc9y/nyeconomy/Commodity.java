package com.mc9y.nyeconomy;

import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.List;

/**
 * @author Blank038
 */
public class Commodity {
    public static final HashMap<String, Commodity> COMMODITY_MAP = new HashMap<>();

    private final String ECO_TYPE;
    private final int AMOUNT;
    private final List<String> COMMANDS;
    private final String DISPLAY_NAME;

    public Commodity(String type, int amount, List<String> commands, String name) {
        this.ECO_TYPE = type;
        this.AMOUNT = amount;
        this.COMMANDS = commands;
        this.DISPLAY_NAME = name;
    }

    public String getType() {
        return ECO_TYPE;
    }

    public int getAmount() {
        return AMOUNT;
    }

    public List<String> getCommands() {
        return COMMANDS;
    }

    public void give(String name) {
        for (String command : getCommands()) {
            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", name));
        }
    }

    public String getName() {
        return DISPLAY_NAME;
    }
}