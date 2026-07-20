package com.mc9y.nyeconomy.hook;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.data.AccountCache;
import com.mc9y.nyeconomy.data.AccountTopCache;
import com.mc9y.nyeconomy.data.TopCache;
import com.mc9y.nyeconomy.util.Money;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
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
            AccountTopCache.Entry<String, BigDecimal> entry = TopCache.getInstance().getTopDataExact(currency).get(index);
            return entry == null ? Main.getString("placeholder.none") : entry.getName();
        });
        this.placeholderConsumerMap.put("top@count", (player, currency, params) -> {
            int index = Integer.parseInt(params[2]);
            AccountTopCache.Entry<String, BigDecimal> entry = TopCache.getInstance().getTopDataExact(currency).get(index);
            return entry == null ? Main.getString("placeholder.none") : Money.format(entry.getValue());
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
        if (Main.getInstance().vaults.contains(s)) {
            return Money.format(Main.getNyEconomyAPI().getBalanceExact(currency, player.getUniqueId()));
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
