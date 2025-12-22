package com.mc9y.nyeconomy.handler;

import com.aystudio.core.bukkit.AyCore;
import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.data.AccountCache;
import com.mc9y.nyeconomy.data.AccountTopCache;
import com.mc9y.nyeconomy.data.TopCache;
import com.mc9y.nyeconomy.interfaces.AbstractDataSourceHandlerImpl;
import com.mc9y.nyeconomy.interfaces.impl.CommonDataSourceHandler;
import com.mc9y.nyeconomy.interfaces.impl.HikariDataSourceHandler;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

public class MySqlStorgeHandler extends AbstractStorgeHandler {
    public static boolean SQL_STATUS = false;

    private final String[] SQL_ARRAY = {
            "CREATE TABLE IF NOT EXISTS nyeconomy_balances (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "currency_type VARCHAR(50) NOT NULL," +
                    "balance INTEGER NOT NULL DEFAULT 0," +
                    "last_updated BIGINT NOT NULL," +
                    "UNIQUE KEY uk_player_currency (player_uuid, currency_type)," +
                    "INDEX idx_player_uuid (player_uuid)," +
                    "INDEX idx_currency (currency_type)," +
                    "INDEX idx_balance_desc (currency_type, balance DESC))",
            "CREATE TABLE IF NOT EXISTS nyeconomy_players (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY," +
                    "player_name VARCHAR(16) NOT NULL," +
                    "last_seen BIGINT NOT NULL," +
                    "INDEX idx_player_name (player_name))",
            "CREATE TABLE IF NOT EXISTS nyeconomy_options (" +
                    "option_key VARCHAR(50) PRIMARY KEY," +
                    "option_value TEXT," +
                    "updated_at BIGINT NOT NULL)"
    };
    private final AbstractDataSourceHandlerImpl DATA_SOURCE;

    public MySqlStorgeHandler() {
        this.DATA_SOURCE = Main.getInstance().hasHikariCP() ? new HikariDataSourceHandler() : new CommonDataSourceHandler();
        this.createTable();
        AyCore.getPlatformApi().runTaskTimerAsynchronously(Main.getInstance(), () -> this.DATA_SOURCE.connect((connection, statement) -> {
            ResultSet resultSet = null;
            try {
                resultSet = statement.executeQuery();
            } catch (SQLException throwables) {
                Main.getInstance().getLogger().log(Level.WARNING, throwables, () -> "定时查询线程出现异常");
            } finally {
                this.DATA_SOURCE.close(statement, resultSet);
            }
        }, "show full columns from nyeconomy_balances"), 1200L, 1200L);
    }

    public void createTable() {
        for (String sql : this.SQL_ARRAY) {
            this.DATA_SOURCE.connect((connection, statement) -> {
                try {
                    statement.executeUpdate();
                    MySqlStorgeHandler.SQL_STATUS = true;
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }, sql);
        }
    }

    @Override
    public void updatePlayerMapping(UUID uuid, String name) {
        long now = System.currentTimeMillis();
        this.DATA_SOURCE.connect((connection, statement) -> {
            try {
                statement.setString(1, uuid.toString());
                statement.setString(2, name);
                statement.setLong(3, now);
                statement.setString(4, name);
                statement.setLong(5, now);
                statement.executeUpdate();
            } catch (SQLException throwables) {
                Main.getInstance().getLogger().log(Level.WARNING, "更新玩家映射失败", throwables);
            } finally {
                this.DATA_SOURCE.close(statement, null);
            }
        }, "INSERT INTO nyeconomy_players (player_uuid, player_name, last_seen) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE player_name = ?, last_seen = ?");
    }

    @Override
    public UUID queryPlayerUUID(String playerName) {
        AtomicBoolean found = new AtomicBoolean(false);
        String[] result = new String[1];
        this.DATA_SOURCE.connect((connection, statement) -> {
            ResultSet resultSet = null;
            try {
                statement.setString(1, playerName);
                resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    result[0] = resultSet.getString("player_uuid");
                    found.set(true);
                }
            } catch (SQLException throwables) {
                Main.getInstance().getLogger().log(Level.WARNING, "查询玩家UUID失败", throwables);
            } finally {
                this.DATA_SOURCE.close(statement, resultSet);
            }
        }, "SELECT player_uuid FROM nyeconomy_players WHERE player_name = ?");
        
        if (found.get() && result[0] != null) {
            try {
                return UUID.fromString(result[0]);
            } catch (IllegalArgumentException e) {
                Main.getInstance().getLogger().log(Level.WARNING, "UUID格式错误", e);
            }
        }
        return null;
    }

    @Override
    public String queryPlayerName(UUID uuid) {
        String[] result = new String[1];
        this.DATA_SOURCE.connect((connection, statement) -> {
            ResultSet resultSet = null;
            try {
                statement.setString(1, uuid.toString());
                resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    result[0] = resultSet.getString("player_name");
                }
            } catch (SQLException throwables) {
                Main.getInstance().getLogger().log(Level.WARNING, "查询玩家名称失败", throwables);
            } finally {
                this.DATA_SOURCE.close(statement, resultSet);
            }
        }, "SELECT player_name FROM nyeconomy_players WHERE player_uuid = ?");
        return result[0];
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

        final String currencyType = Main.getNyEconomyAPI().checkVaultType(type);
        
        AtomicBoolean success = new AtomicBoolean(false);
        this.DATA_SOURCE.connect((connection, statement) -> {
            try {
                long now = System.currentTimeMillis();
                statement.setString(1, uuid.toString());
                statement.setString(2, currencyType);
                statement.setInt(3, amount);
                statement.setLong(4, now);
                statement.setInt(5, amount);
                statement.setLong(6, now);
                
                int rows = statement.executeUpdate();
                success.set(rows > 0);
                
                if (rows > 0) {
                    AccountCache cache = AccountCache.CACHE_DATA.get(uuid);
                    if (cache != null) {
                        cache.addBalance(currencyType, amount);
                    }
                }
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            } finally {
                this.DATA_SOURCE.close(statement, null);
            }
        }, "INSERT INTO nyeconomy_balances (player_uuid, currency_type, balance, last_updated) " +
           "VALUES (?, ?, ?, ?) " +
           "ON DUPLICATE KEY UPDATE balance = balance + ?, last_updated = ?");
        
        return success.get();
    }

    @Override
    public boolean withdraw(UUID uuid, String type, int amount) {
        if (amount < 0) {
            return false;
        }

        final String currencyType = Main.getNyEconomyAPI().checkVaultType(type);
        
        AtomicBoolean success = new AtomicBoolean(false);
        this.DATA_SOURCE.connect((connection, statement) -> {
            try {
                long now = System.currentTimeMillis();
                statement.setInt(1, amount);
                statement.setLong(2, now);
                statement.setString(3, uuid.toString());
                statement.setString(4, currencyType);
                statement.setInt(5, amount);
                
                int rows = statement.executeUpdate();
                success.set(rows > 0);
                
                if (rows > 0) {
                    AccountCache cache = AccountCache.CACHE_DATA.get(uuid);
                    if (cache != null) {
                        cache.subtractBalance(currencyType, amount);
                    }
                }
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            } finally {
                this.DATA_SOURCE.close(statement, null);
            }
        }, "UPDATE nyeconomy_balances " +
           "SET balance = balance - ?, last_updated = ? " +
           "WHERE player_uuid = ? AND currency_type = ? AND balance >= ?");
        
        return success.get();
    }

    @Override
    public boolean set(UUID uuid, String type, int amount) {
        if (amount < 0) {
            return false;
        }

        final String currencyType = Main.getNyEconomyAPI().checkVaultType(type);
        
        AtomicBoolean success = new AtomicBoolean(false);
        this.DATA_SOURCE.connect((connection, statement) -> {
            try {
                long now = System.currentTimeMillis();
                statement.setString(1, uuid.toString());
                statement.setString(2, currencyType);
                statement.setInt(3, amount);
                statement.setLong(4, now);
                statement.setInt(5, amount);
                statement.setLong(6, now);
                
                int rows = statement.executeUpdate();
                success.set(rows > 0);
                
                if (rows > 0) {
                    AccountCache cache = AccountCache.CACHE_DATA.get(uuid);
                    if (cache != null) {
                        cache.setBalance(currencyType, amount);
                    }
                }
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            } finally {
                this.DATA_SOURCE.close(statement, null);
            }
        }, "INSERT INTO nyeconomy_balances (player_uuid, currency_type, balance, last_updated) " +
           "VALUES (?, ?, ?, ?) " +
           "ON DUPLICATE KEY UPDATE balance = ?, last_updated = ?");
        
        return success.get();
    }

    @Override
    public boolean isExists(UUID uuid) {
        AtomicBoolean exists = new AtomicBoolean(false);
        this.DATA_SOURCE.connect((connection, statement) -> {
            ResultSet resultSet = null;
            try {
                statement.setString(1, uuid.toString());
                resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    exists.set(true);
                }
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            } finally {
                this.DATA_SOURCE.close(statement, resultSet);
            }
        }, "SELECT 1 FROM nyeconomy_balances WHERE player_uuid=? LIMIT 1");
        return exists.get();
    }

    @Override
    public void save() {
    }

    @Override
    public synchronized void refreshTop() {
        long now = System.currentTimeMillis();
        AtomicBoolean exists = new AtomicBoolean(false);

        this.DATA_SOURCE.connect((connection, statement) -> {
            ResultSet resultSet = null;
            try {
                resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    exists.set(true);
                }
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            } finally {
                this.DATA_SOURCE.close(statement, resultSet);
            }
        }, "SELECT 1 FROM nyeconomy_options WHERE option_key='refresh_time'");

        this.DATA_SOURCE.connect((connection, statement) -> {
            try {
                statement.setString(1, String.valueOf(now));
                statement.setLong(2, now);
                if (!exists.get()) {
                    statement.setString(3, "refresh_time");
                }
                statement.executeUpdate();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            } finally {
                this.DATA_SOURCE.close(statement, null);
            }
        }, exists.get() ? "UPDATE nyeconomy_options SET option_value=?, updated_at=? WHERE option_key='refresh_time'"
                : "INSERT INTO nyeconomy_options (option_value, updated_at, option_key) VALUES (?,?,?)");

        Map<String, AccountTopCache> cacheData = new HashMap<>();
        this.DATA_SOURCE.connect((connection, statement) -> {
            ResultSet resultSet = null;
            try {
                resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    String playerName = resultSet.getString("player_name");
                    if (!cacheData.containsKey(playerName)) {
                        cacheData.put(playerName, new AccountTopCache());
                    }
                }
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            } finally {
                this.DATA_SOURCE.close(statement, resultSet);
            }
        }, "SELECT DISTINCT p.player_name FROM nyeconomy_balances b " +
           "JOIN nyeconomy_players p ON b.player_uuid = p.player_uuid");

        for (String playerName : cacheData.keySet()) {
            AccountTopCache topCache = cacheData.get(playerName);
            this.DATA_SOURCE.connect((connection, statement) -> {
                ResultSet resultSet = null;
                try {
                    statement.setString(1, playerName);
                    resultSet = statement.executeQuery();
                    while (resultSet.next()) {
                        topCache.getTempMap().put(
                                resultSet.getString("currency_type"),
                                resultSet.getInt("balance")
                        );
                    }
                } catch (SQLException throwables) {
                    throwables.printStackTrace();
                } finally {
                    this.DATA_SOURCE.close(statement, resultSet);
                }
            }, "SELECT b.currency_type, b.balance FROM nyeconomy_balances b " +
               "JOIN nyeconomy_players p ON b.player_uuid = p.player_uuid " +
               "WHERE p.player_name = ?");
        }

        TopCache.getInstance().submitCache(cacheData);
    }

    @Override
    public AccountCache getPlayerCache(UUID uuid) {
        AccountCache cache = AccountCache.CACHE_DATA.get(uuid);
        if (cache != null) {
            return cache;
        }

        final AccountCache newCache = new AccountCache();
        this.DATA_SOURCE.connect((connection, statement) -> {
            ResultSet resultSet = null;
            try {
                statement.setString(1, uuid.toString());
                resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    String currencyType = resultSet.getString("currency_type");
                    int balance = resultSet.getInt("balance");
                    newCache.setBalance(currencyType, balance);
                }
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            } finally {
                this.DATA_SOURCE.close(statement, resultSet);
            }
        }, "SELECT currency_type, balance FROM nyeconomy_balances WHERE player_uuid=?");

        AccountCache existing = AccountCache.CACHE_DATA.putIfAbsent(uuid, newCache);
        return existing != null ? existing : newCache;
    }

    private AccountCache getOrCreateCache(UUID uuid) {
        AccountCache cache = AccountCache.CACHE_DATA.get(uuid);
        if (cache != null) {
            return cache;
        }
        return getPlayerCache(uuid);
    }

    private boolean updateBalance(UUID playerUuid, String currencyType, int balance) {
        AtomicBoolean success = new AtomicBoolean(false);
        this.DATA_SOURCE.connect((connection, statement) -> {
            try {
                long now = System.currentTimeMillis();
                statement.setString(1, playerUuid.toString());
                statement.setString(2, currencyType);
                statement.setInt(3, balance);
                statement.setLong(4, now);
                statement.setInt(5, balance);
                statement.setLong(6, now);

                statement.executeUpdate();
                success.set(true);
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            } finally {
                this.DATA_SOURCE.close(statement, null);
            }
        }, "INSERT INTO nyeconomy_balances (player_uuid, currency_type, balance, last_updated) " +
                "VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE balance = ?, last_updated = ?");
        return success.get();
    }

    @Override
    public int depositAll(String type, int amount) {
        if (amount < 0) {
            return 0;
        }

        final String currencyType = Main.getNyEconomyAPI().checkVaultType(type);
        final int[] affectedRows = {0};
        
        this.DATA_SOURCE.connect((connection, statement) -> {
            try {
                long now = System.currentTimeMillis();
                statement.setString(1, currencyType);
                statement.setInt(2, amount);
                statement.setLong(3, now);
                
                affectedRows[0] = statement.executeUpdate();
                
                AccountCache.CACHE_DATA.forEach((uuid, cache) -> {
                    cache.addBalance(currencyType, amount);
                });
            } catch (SQLException throwables) {
                Main.getInstance().getLogger().log(Level.WARNING, "批量给予货币失败", throwables);
            } finally {
                this.DATA_SOURCE.close(statement, null);
            }
        }, "UPDATE nyeconomy_balances SET balance = balance + ?, last_updated = ? WHERE currency_type = ?");
        
        return affectedRows[0];
    }

    @Override
    public int resetAll(String type) {
        final String currencyType = Main.getNyEconomyAPI().checkVaultType(type);
        final int[] affectedRows = {0};
        
        this.DATA_SOURCE.connect((connection, statement) -> {
            try {
                long now = System.currentTimeMillis();
                statement.setLong(1, now);
                statement.setString(2, currencyType);
                
                affectedRows[0] = statement.executeUpdate();
                
                AccountCache.CACHE_DATA.forEach((uuid, cache) -> {
                    cache.setBalance(currencyType, 0);
                });
            } catch (SQLException throwables) {
                Main.getInstance().getLogger().log(Level.WARNING, "批量重置货币失败", throwables);
            } finally {
                this.DATA_SOURCE.close(statement, null);
            }
        }, "UPDATE nyeconomy_balances SET balance = 0, last_updated = ? WHERE currency_type = ?");
        
        return affectedRows[0];
    }
}