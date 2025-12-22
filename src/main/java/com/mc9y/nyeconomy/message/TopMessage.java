package com.mc9y.nyeconomy.message;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.data.AccountTopCache;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Blank038
 * @since 2021-03-12
 */
public class TopMessage {
    private final List<String> messageList = new ArrayList<>();

    public TopMessage(Map<Integer, AccountTopCache.Entry<String, Integer>> top, int page) {
        if (top.isEmpty()) {
            this.messageList.add(Main.getString("message.empty-data", true));
            return;
        }
        page -= 1;
        this.messageList.add(Main.getInstance().getConfig().getString("message.top.header"));
        int max = Main.getInstance().getConfig().getInt("cache-count");
        int start = Math.min(page * 10, max), end = Math.min((page + 1) * 10, max);
        for (int i = start; i < end; i++) {
            if (i > top.size() || top.get(i) == null) {
                this.messageList.add(Main.getString("message.top.line")
                        .replace("%top%", String.valueOf(i + 1))
                        .replace("%name%", Main.getInstance().getConfig().getString("placeholder.none", "无"))
                        .replace("%count%", Main.getInstance().getConfig().getString("placeholder.none", "无")));
                continue;
            }
            AccountTopCache.Entry<String, Integer> entry = top.get(i);
            this.messageList.add(Main.getString("message.top.line")
                    .replace("%top%", String.valueOf(i + 1))
                    .replace("%name%", entry.getName())
                    .replace("%count%", String.valueOf(entry.getValue())));
        }
    }

    public void send(String currency, CommandSender player) {
        for (String line : this.messageList) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', line.replace("%currency%", currency)));
        }
    }
}