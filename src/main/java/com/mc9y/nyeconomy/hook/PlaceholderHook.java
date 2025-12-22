package com.mc9y.nyeconomy.hook;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.data.AccountCache;
import com.mc9y.nyeconomy.data.AccountTopCache;
import com.mc9y.nyeconomy.data.TopCache;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Blank038
 */
public class PlaceholderHook extends PlaceholderExpansion {
    private final Main plugin;
    private final Map<String, PlaceholderConsumer> placeholderConsumerMap = new HashMap<>();

    public PlaceholderHook(Main main) {
        this.plugin = main;
        this.placeholderConsumerMap.put("top@name", (player, currency, params) -> {
            int index = Integer.parseInt(params[2]);
            AccountTopCache.Entry<String, Integer> entry = TopCache.getInstance().getTopData(currency).get(index);
            return entry == null ? Main.getString("placeholder.none") : entry.getName();
        });
        this.placeholderConsumerMap.put("top@count", (player, currency, params) -> {
            int index = Integer.parseInt(params[2]);
            AccountTopCache.Entry<String, Integer> entry = TopCache.getInstance().getTopData(currency).get(index);
            return entry == null ? Main.getString("placeholder.none") : String.valueOf(entry.getValue());
        });
    }

    @Override
    public String onPlaceholderRequest(Player player, String s) {
        if (player == null || !player.isOnline()) {
            return "";
        }
        String[] split = s.split("_");
        String key = s.contains("_") ? split[1] : s;
        if (this.placeholderConsumerMap.containsKey(key)) {
            String currency = this.formatVariable(split[0]);
            return this.placeholderConsumerMap.get(key).accept(player, currency, split);
        }
        String currency = this.formatVariable(s);
        if ("mysql".equalsIgnoreCase(plugin.getConfig().getString("data-option.type"))) {
            if (AccountCache.CACHE_DATA.containsKey(player.getUniqueId())) {
                return String.valueOf(AccountCache.CACHE_DATA.get(player.getUniqueId()).balance(currency));
            }
            return "0";
        } else if (Main.getInstance().vaults.contains(s)) {
            return String.valueOf(Main.getNyEconomyAPI().getBalance(currency, player.getName()));
        }
        return "";
    }

    private String formatVariable(String line) {
        return Main.getNyEconomyAPI().checkVaultType(line.replace("{ul}", "_"));
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
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @FunctionalInterface
    public interface PlaceholderConsumer {

        String accept(Player player, String currency, String[] params);
    }
}