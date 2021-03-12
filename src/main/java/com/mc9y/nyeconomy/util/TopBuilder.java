package com.mc9y.nyeconomy.util;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.data.AccountTopCache;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author Blank038
 * @since 2021-03-12
 */
public class TopBuilder {
    private final List<String> MESSAGE_LIST = new ArrayList<>();

    public TopBuilder(HashMap<Integer, AccountTopCache.Entry<String, Integer>> top, int page) {
        if (top.isEmpty()) {
            return;
        }
        page -= 1;
        this.MESSAGE_LIST.add(Main.getInstance().getConfig().getString("Message.top.header"));
        int max = Main.getInstance().getConfig().getInt("cache-count");
        // 开始下标
        int start = Math.min(page * 10, max), end = Math.min((page + 1) * 10, max);
        for (int i = start; i < end; i++) {
            if (i > top.size()) {
                this.MESSAGE_LIST.add(Main.getInstance().getConfig().getString("Message.top.line").replace("%top%", String.valueOf(i + 1))
                        .replace("%name%", "无").replace("%count%", "无"));
                continue;
            }
            AccountTopCache.Entry<String, Integer> entry = top.get(i);
            this.MESSAGE_LIST.add(Main.getInstance().getConfig().getString("Message.top.line").replace("%top%", String.valueOf(i + 1))
                    .replace("%name%", entry.getName()).replace("%count%", String.valueOf(entry.getValue())));
        }
    }

    public void send(String currency, CommandSender player) {
        for (String line : this.MESSAGE_LIST) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', line.replace("%currency%", currency)));
        }
    }
}
