package com.mc9y.nyeconomy.service;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.handler.AbstractStorgeHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UUIDService {
    private static UUIDService instance;
    
    private final ConcurrentHashMap<UUID, String> uuidToNameCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> nameToUuidCache = new ConcurrentHashMap<>();
    
    public static UUIDService getInstance() {
        if (instance == null) {
            instance = new UUIDService();
        }
        return instance;
    }
    
    public UUID getPlayerUUID(String playerName) {
        UUID cached = nameToUuidCache.get(playerName);
        if (cached != null) {
            return cached;
        }
        
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null) {
            UUID uuid = player.getUniqueId();
            updateCache(uuid, playerName);
            return uuid;
        }
        
        UUID dbUuid = queryUUIDFromDatabase(playerName);
        if (dbUuid != null) {
            updateCache(dbUuid, playerName);
            return dbUuid;
        }
        
        UUID offlineUuid = getOfflineUUID(playerName);
        updateCache(offlineUuid, playerName);
        return offlineUuid;
    }
    
    public String getPlayerName(UUID uuid) {
        String cached = uuidToNameCache.get(uuid);
        if (cached != null) {
            return cached;
        }
        
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            String name = player.getName();
            updateCache(uuid, name);
            return name;
        }
        
        String dbName = queryNameFromDatabase(uuid);
        if (dbName != null) {
            updateCache(uuid, dbName);
            return dbName;
        }
        
        return null;
    }
    
    public void updatePlayerMapping(UUID uuid, String name) {
        updateCache(uuid, name);
        AbstractStorgeHandler.getHandler().updatePlayerMapping(uuid, name);
    }
    
    private void updateCache(UUID uuid, String name) {
        uuidToNameCache.put(uuid, name);
        nameToUuidCache.put(name, uuid);
    }
    
    private UUID queryUUIDFromDatabase(String playerName) {
        return AbstractStorgeHandler.getHandler().queryPlayerUUID(playerName);
    }
    
    private String queryNameFromDatabase(UUID uuid) {
        return AbstractStorgeHandler.getHandler().queryPlayerName(uuid);
    }
    
    private UUID getOfflineUUID(String playerName) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
    }
}
