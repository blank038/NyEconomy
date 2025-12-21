package com.mc9y.nyeconomy.handler;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.data.AccountCache;
import com.mc9y.nyeconomy.data.AccountTopCache;
import com.mc9y.nyeconomy.data.TopCache;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

public class SqliteStorageHandler extends AbstractStorgeHandler {
    private Connection connection;
    private final String dbPath;

    public SqliteStorageHandler() {
        File dataDir = new File(Main.getInstance().getDataFolder(), "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        this.dbPath = dataDir.getAbsolutePath() + File.separator + "economy.db";
        this.initDatabase();
    }

    private void initDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            createTables();
            Main.getInstance().getLogger().info("SQLite 数据库初始化成功: " + dbPath);
        } catch (Exception e) {
            Main.getInstance().getLogger().log(Level.SEVERE, "SQLite 初始化失败", e);
        }
    }

    private void createTables() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS nyeconomy_balances (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "player_uuid VARCHAR(36) NOT NULL," +
                        "currency_type VARCHAR(50) NOT NULL," +
                        "balance INTEGER NOT NULL DEFAULT 0," +
                        "last_updated BIGINT NOT NULL," +
                        "UNIQUE(player_uuid, currency_type))");
            
            stmt.execute("CREATE TABLE IF NOT EXISTS nyeconomy_players (" +
                        "player_uuid VARCHAR(36) PRIMARY KEY," +
                        "player_name VARCHAR(16) NOT NULL," +
                        "last_seen BIGINT NOT NULL)");
            
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_uuid ON nyeconomy_balances(player_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_currency ON nyeconomy_balances(currency_type)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_balance_desc ON nyeconomy_balances(currency_type, balance DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_name ON nyeconomy_players(player_name)");
            
            stmt.execute("CREATE TABLE IF NOT EXISTS nyeconomy_options (" +
                        "option_key VARCHAR(50) PRIMARY KEY," +
                        "option_value TEXT," +
                        "updated_at BIGINT NOT NULL)");
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.SEVERE, "创建数据库表失败", e);
        }
    }

    @Override
    public void updatePlayerMapping(UUID uuid, String name) {
        long now = System.currentTimeMillis();
        try {
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
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "更新玩家映射失败", e);
        }
    }

    @Override
    public UUID queryPlayerUUID(String playerName) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT player_uuid FROM nyeconomy_players WHERE player_name = ?")) {
            ps.setString(1, playerName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return UUID.fromString(rs.getString("player_uuid"));
                }
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "查询玩家UUID失败", e);
        }
        return null;
    }

    @Override
    public String queryPlayerName(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT player_name FROM nyeconomy_players WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("player_name");
                }
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "查询玩家名称失败", e);
        }
        return null;
    }

    @Override
    public int balance(UUID uuid, String type, int status) {
        if (status == 1) {
            return 0;
        }
        
        type = Main.getNyEconomyAPI().checkVaultType(type);
        
        AccountCache cache = AccountCache.CACHE_DATA.get(uuid);
        if (cache != null) {
            return cache.balance(type);
        }
        
        if (status == 0 || isExists(uuid)) {
            cache = getPlayerCache(uuid);
            return cache.balance(type);
        }
        
        return 0;
    }

    @Override
    public boolean deposit(UUID uuid, String type, int amount) {
        if (amount < 0) {
            return false;
        }
        
        type = Main.getNyEconomyAPI().checkVaultType(type);
        ReentrantLock lock = AccountCache.getAccountLock(uuid);
        lock.lock();
        try {
            AccountCache cache = getOrCreateCache(uuid);
            int newBalance = cache.addBalance(type, amount);
            return updateBalance(uuid, type, newBalance);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean withdraw(UUID uuid, String type, int amount) {
        if (amount < 0) {
            return false;
        }
        
        type = Main.getNyEconomyAPI().checkVaultType(type);
        ReentrantLock lock = AccountCache.getAccountLock(uuid);
        lock.lock();
        try {
            AccountCache cache = getOrCreateCache(uuid);
            int currentBalance = cache.balance(type);
            
            if (currentBalance < amount) {
                return false;
            }
            
            int newBalance = cache.subtractBalance(type, amount);
            return updateBalance(uuid, type, newBalance);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean set(UUID uuid, String type, int amount) {
        if (amount < 0) {
            return false;
        }
        
        type = Main.getNyEconomyAPI().checkVaultType(type);
        ReentrantLock lock = AccountCache.getAccountLock(uuid);
        lock.lock();
        try {
            AccountCache cache = getOrCreateCache(uuid);
            cache.setBalance(type, amount);
            return updateBalance(uuid, type, amount);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void save() {
    }

    @Override
    public synchronized void refreshTop() {
        try {
            long now = System.currentTimeMillis();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO nyeconomy_options (option_key, option_value, updated_at) VALUES (?, ?, ?)")) {
                ps.setString(1, "refresh_time");
                ps.setString(2, String.valueOf(now));
                ps.setLong(3, now);
                ps.executeUpdate();
            }
            
            Map<String, AccountTopCache> cacheData = new HashMap<>();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT DISTINCT player_uuid FROM nyeconomy_balances")) {
                
                while (rs.next()) {
                    String playerUuid = rs.getString("player_uuid");
                    AccountTopCache topCache = new AccountTopCache();
                    
                    try (PreparedStatement ps = connection.prepareStatement(
                            "SELECT currency_type, balance FROM nyeconomy_balances WHERE player_uuid = ?")) {
                        ps.setString(1, playerUuid);
                        try (ResultSet balanceRs = ps.executeQuery()) {
                            while (balanceRs.next()) {
                                topCache.getTempMap().put(
                                    balanceRs.getString("currency_type"),
                                    balanceRs.getInt("balance")
                                );
                            }
                        }
                    }
                    
                    cacheData.put(playerUuid, topCache);
                }
            }
            
            TopCache.getInstance().submitCache(cacheData);
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "刷新排行榜失败", e);
        }
    }

    @Override
    public boolean isExists(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM nyeconomy_balances WHERE player_uuid = ? LIMIT 1")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "检查玩家存在性失败", e);
            return false;
        }
    }

    @Override
    public AccountCache getPlayerCache(UUID uuid) {
        AccountCache cache = AccountCache.CACHE_DATA.get(uuid);
        if (cache != null) {
            return cache;
        }
        
        cache = loadPlayerCacheFromDatabase(uuid);
        AccountCache existing = AccountCache.CACHE_DATA.putIfAbsent(uuid, cache);
        return existing != null ? existing : cache;
    }

    private AccountCache getOrCreateCache(UUID uuid) {
        AccountCache cache = AccountCache.CACHE_DATA.get(uuid);
        if (cache != null) {
            return cache;
        }
        return getPlayerCache(uuid);
    }
    
    private AccountCache loadPlayerCacheFromDatabase(UUID uuid) {
        AccountCache cache = new AccountCache();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT currency_type, balance FROM nyeconomy_balances WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String currencyType = rs.getString("currency_type");
                    int balance = rs.getInt("balance");
                    cache.setBalance(currencyType, balance);
                }
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "加载玩家缓存失败: " + uuid, e);
        }
        return cache;
    }

    private boolean updateBalance(UUID playerUuid, String currencyType, int balance) {
        long now = System.currentTimeMillis();
        
        try {
            try (PreparedStatement checkPs = connection.prepareStatement(
                    "SELECT 1 FROM nyeconomy_balances WHERE player_uuid = ? AND currency_type = ?")) {
                checkPs.setString(1, playerUuid.toString());
                checkPs.setString(2, currencyType);
                
                boolean exists = false;
                try (ResultSet rs = checkPs.executeQuery()) {
                    exists = rs.next();
                }
                
                if (exists) {
                    try (PreparedStatement updatePs = connection.prepareStatement(
                            "UPDATE nyeconomy_balances SET balance = ?, last_updated = ? WHERE player_uuid = ? AND currency_type = ?")) {
                        updatePs.setInt(1, balance);
                        updatePs.setLong(2, now);
                        updatePs.setString(3, playerUuid.toString());
                        updatePs.setString(4, currencyType);
                        return updatePs.executeUpdate() > 0;
                    }
                } else {
                    try (PreparedStatement insertPs = connection.prepareStatement(
                            "INSERT INTO nyeconomy_balances (player_uuid, currency_type, balance, last_updated) VALUES (?, ?, ?, ?)")) {
                        insertPs.setString(1, playerUuid.toString());
                        insertPs.setString(2, currencyType);
                        insertPs.setInt(3, balance);
                        insertPs.setLong(4, now);
                        return insertPs.executeUpdate() > 0;
                    }
                }
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "更新余额失败", e);
            return false;
        }
    }

    @Override
    public int depositAll(String type, int amount) {
        if (amount < 0) {
            return 0;
        }

        type = Main.getNyEconomyAPI().checkVaultType(type);
        long now = System.currentTimeMillis();
        
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE nyeconomy_balances SET balance = balance + ?, last_updated = ? WHERE currency_type = ?")) {
            ps.setInt(1, amount);
            ps.setLong(2, now);
            ps.setString(3, type);
            
            int affectedRows = ps.executeUpdate();
            
            String finalType = type;
            AccountCache.CACHE_DATA.forEach((uuid, cache) -> {
                cache.addBalance(finalType, amount);
            });
            
            return affectedRows;
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "批量给予货币失败", e);
            return 0;
        }
    }

    @Override
    public int resetAll(String type) {
        type = Main.getNyEconomyAPI().checkVaultType(type);
        long now = System.currentTimeMillis();
        
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE nyeconomy_balances SET balance = 0, last_updated = ? WHERE currency_type = ?")) {
            ps.setLong(1, now);
            ps.setString(2, type);
            
            int affectedRows = ps.executeUpdate();
            
            String finalType = type;
            AccountCache.CACHE_DATA.forEach((uuid, cache) -> {
                cache.setBalance(finalType, 0);
            });
            
            return affectedRows;
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "批量重置货币失败", e);
            return 0;
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "关闭数据库连接失败", e);
        }
    }
}