package com.mc9y.nyeconomy.hook;

import com.mc9y.nyeconomy.Main;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

/**
 * @author Blank038
 */
public class PlaceholderHook extends PlaceholderExpansion {
    private final Main INSTANCE;

    public PlaceholderHook(Main main) {
        this.INSTANCE = main;
    }

    @Override
    public String onPlaceholderRequest(Player player, String s) {
        if (Main.getInstance().vaults.contains(s)) {
            return String.valueOf(Main.getNyEconomyAPI().getBalance(s, player.getName()));
        }
        return "";
    }

    @Override
    public String getIdentifier() {
        return "nyeconomy";
    }

    @Override
    public String getAuthor() {
        return "Blank038";
    }

    @Override
    public String getVersion() {
        return INSTANCE.getDescription().getVersion();
    }
}