package com.mc9y.nyeconomy.listener;

import com.aystudio.core.bukkit.AyCore;
import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.data.AccountCache;
import com.mc9y.nyeconomy.handler.AbstractStorgeHandler;
import com.mc9y.nyeconomy.service.UUIDService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        
        AyCore.getPlatformApi().runTaskAsynchronously(Main.getInstance(), () -> {
            UUIDService.getInstance().updatePlayerMapping(uuid, name);
            
            if ("mysql".equalsIgnoreCase(Main.getInstance().getConfig().getString("data-option.type"))) {
                AccountCache.CACHE_DATA.remove(uuid);
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        AccountCache.CACHE_DATA.remove(event.getPlayer().getUniqueId());
    }
}