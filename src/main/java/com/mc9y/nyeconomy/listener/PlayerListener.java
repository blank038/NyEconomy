package com.mc9y.nyeconomy.listener;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.data.AccountCache;
import com.mc9y.nyeconomy.handler.AbstractStorgeHandler;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * @author Blank038
 */
public class PlayerListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if ("mysql".equalsIgnoreCase(Main.getInstance().getConfig().getString("data-option.type"))) {
            Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () ->
                    AbstractStorgeHandler.getHandler().balance(event.getPlayer().getName(), "", 2));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        AccountCache.CACHE_DATA.remove(event.getPlayer().getName());
    }
}
