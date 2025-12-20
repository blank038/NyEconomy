package com.mc9y.nyeconomy.migration;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.handler.AbstractStorgeHandler;
import com.mc9y.nyeconomy.service.UUIDService;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class UUIDMigrationHandler {
    
    public MigrationResult migrateToUUID(Connection connection) {
        MigrationResult result = new MigrationResult();
        
        try {
            if (isAlreadyMigrated(connection)) {
                result.success = false;
                result.message = "数据已经迁移过,无需重复迁移";
                return result;
            }
            
            Set<String> playerNames = loadAllPlayerNames(connection);
            result.totalPlayers = playerNames.size();
            
            for (String name : playerNames) {
                try {
                    UUID uuid = UUIDService.getInstance().getPlayerUUID(name);
                    insertPlayerMapping(connection, uuid, name);
                    result.successCount++;
                } catch (Exception e) {
                    Main.getInstance().getLogger().warning("迁移玩家失败: " + name);
                    result.failCount++;
                }
            }
            
            migrateBalancesTable(connection);
            
            result.success = true;
            result.message = "UUID 迁移完成";
            
        } catch (Exception e) {
            Main.getInstance().getLogger().log(Level.SEVERE, "UUID 迁移失败", e);
            result.success = false;
            result.message = "迁移异常: " + e.getMessage();
        }
        
        return result;
    }
    
    private boolean isAlreadyMigrated(Connection connection) {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM nyeconomy_players")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return true;
            }
        } catch (SQLException e) {
            return false;
        }
        return false;
    }
    
    private Set<String> loadAllPlayerNames(Connection connection) {
        Set<String> names = new HashSet<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT DISTINCT player_name FROM nyeconomy_balances")) {
            while (rs.next()) {
                names.add(rs.getString("player_name"));
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "加载玩家名列表失败", e);
        }
        return names;
    }
    
    private void insertPlayerMapping(Connection connection, UUID uuid, String name) throws SQLException {
        long now = System.currentTimeMillis();
        String dbType = Main.getInstance().getConfig().getString("data-option.type", "sqlite");
        
        if ("mysql".equalsIgnoreCase(dbType)) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO nyeconomy_players (player_uuid, player_name, last_seen) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), last_seen = VALUES(last_seen)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setLong(3, now);
                ps.executeUpdate();
            }
        } else {
            boolean exists = false;
            try (PreparedStatement checkPs = connection.prepareStatement(
                    "SELECT 1 FROM nyeconomy_players WHERE player_uuid = ?")) {
                checkPs.setString(1, uuid.toString());
                try (ResultSet rs = checkPs.executeQuery()) {
                    exists = rs.next();
                }
            }
            
            if (exists) {
                try (PreparedStatement updatePs = connection.prepareStatement(
                        "UPDATE nyeconomy_players SET player_name = ?, last_seen = ? WHERE player_uuid = ?")) {
                    updatePs.setString(1, name);
                    updatePs.setLong(2, now);
                    updatePs.setString(3, uuid.toString());
                    updatePs.executeUpdate();
                }
            } else {
                try (PreparedStatement insertPs = connection.prepareStatement(
                        "INSERT INTO nyeconomy_players (player_uuid, player_name, last_seen) VALUES (?, ?, ?)")) {
                    insertPs.setString(1, uuid.toString());
                    insertPs.setString(2, name);
                    insertPs.setLong(3, now);
                    insertPs.executeUpdate();
                }
            }
        }
    }
    
    private void migrateBalancesTable(Connection connection) throws SQLException {
        Map<String, List<BalanceRecord>> playerBalances = new HashMap<>();
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT player_name, currency_type, balance, last_updated FROM nyeconomy_balances")) {
            while (rs.next()) {
                String playerName = rs.getString("player_name");
                playerBalances.computeIfAbsent(playerName, k -> new ArrayList<>()).add(new BalanceRecord(
                    rs.getString("currency_type"),
                    rs.getInt("balance"),
                    rs.getLong("last_updated")
                ));
            }
        }
        
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DELETE FROM nyeconomy_balances");
        }
        
        String dbType = Main.getInstance().getConfig().getString("data-option.type", "sqlite");
        for (Map.Entry<String, List<BalanceRecord>> entry : playerBalances.entrySet()) {
            String playerName = entry.getKey();
            UUID uuid = UUIDService.getInstance().getPlayerUUID(playerName);
            
            for (BalanceRecord record : entry.getValue()) {
                if ("mysql".equalsIgnoreCase(dbType)) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO nyeconomy_balances (player_uuid, currency_type, balance, last_updated) VALUES (?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE balance = VALUES(balance), last_updated = VALUES(last_updated)")) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, record.currencyType);
                        ps.setInt(3, record.balance);
                        ps.setLong(4, record.lastUpdated);
                        ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "INSERT OR REPLACE INTO nyeconomy_balances (player_uuid, currency_type, balance, last_updated) " +
                            "SELECT ?, ?, ?, ? WHERE NOT EXISTS (" +
                            "  SELECT 1 FROM nyeconomy_balances WHERE player_uuid = ? AND currency_type = ?)")) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, record.currencyType);
                        ps.setInt(3, record.balance);
                        ps.setLong(4, record.lastUpdated);
                        ps.setString(5, uuid.toString());
                        ps.setString(6, record.currencyType);
                        ps.executeUpdate();
                    }
                }
            }
        }
    }
    
    private static class BalanceRecord {
        String currencyType;
        int balance;
        long lastUpdated;
        
        BalanceRecord(String currencyType, int balance, long lastUpdated) {
            this.currencyType = currencyType;
            this.balance = balance;
            this.lastUpdated = lastUpdated;
        }
    }
    
    public static class MigrationResult {
        public boolean success;
        public String message;
        public int totalPlayers;
        public int successCount;
        public int failCount;
    }
}
