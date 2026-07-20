package com.mc9y.nyeconomy.handler;

import com.aystudio.core.bukkit.AyCore;
import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.data.AccountCache;
import com.mc9y.nyeconomy.data.AccountTopCache;
import com.mc9y.nyeconomy.data.TopCache;
import com.mc9y.nyeconomy.interfaces.AbstractDataSourceHandlerImpl;
import com.mc9y.nyeconomy.interfaces.impl.CommonDataSourceHandler;
import com.mc9y.nyeconomy.interfaces.impl.HikariDataSourceHandler;
import com.mc9y.nyeconomy.migration.BalanceSchemaMigrator;
import com.mc9y.nyeconomy.util.Money;

import java.math.BigDecimal;
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
                    "balance DECIMAL(12,2) NOT NULL DEFAULT 0.00," +
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
        try (Connection connection = DATA_SOURCE.getConnection()) {
            if (connection == null) {
                throw new SQLException("无法取得 MySQL 连接");
            }
            for (String sql : this.SQL_ARRAY) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(sql);
                }
            }
            String versionSql = "INSERT INTO nyeconomy_options "
                    + "(option_key, option_value, updated_at) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE option_value = VALUES(option_value), updated_at = VALUES(updated_at)";
            try (PreparedStatement statement = connection.prepareStatement(versionSql)) {
                statement.setString(1, BalanceSchemaMigrator.VERSION_KEY);
                statement.setString(2, BalanceSchemaMigrator.VERSION);
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
            }
            MySqlStorgeHandler.SQL_STATUS = true;
        } catch (SQLException e) {
            throw new IllegalStateException("初始化 MySQL 表结构失败", e);
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
        return Money.toLegacyInt(balanceExact(uuid, type, status));
    }

    @Override
    public BigDecimal balanceExact(UUID uuid, String type, int status) {
        if (status == 1) {
            return Money.ZERO;
        }

        type = Main.getNyEconomyAPI().checkVaultType(type);

        AccountCache cache = AccountCache.CACHE_DATA.get(uuid);
        if (cache != null) {
            return cache.balanceExact(type);
        }

        if (status == 0 || isExists(uuid)) {
            cache = getPlayerCache(uuid);
            return cache.balanceExact(type);
        }

        return Money.ZERO;
    }

    @Override
    public boolean deposit(UUID uuid, String type, int amount) {
        return deposit(uuid, type, Money.of(amount));
    }

    @Override
    public boolean deposit(UUID uuid, String type, BigDecimal amount) {
        final BigDecimal normalized;
        try {
            normalized = Money.normalize(amount);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return mutateBalance(uuid, type, normalized, Mutation.DEPOSIT);
    }

    @Override
    public boolean withdraw(UUID uuid, String type, int amount) {
        return withdraw(uuid, type, Money.of(amount));
    }

    @Override
    public boolean withdraw(UUID uuid, String type, BigDecimal amount) {
        final BigDecimal normalized;
        try {
            normalized = Money.normalize(amount);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return mutateBalance(uuid, type, normalized, Mutation.WITHDRAW);
    }

    @Override
    public boolean set(UUID uuid, String type, int amount) {
        return set(uuid, type, Money.of(amount));
    }

    @Override
    public boolean set(UUID uuid, String type, BigDecimal amount) {
        final BigDecimal normalized;
        try {
            normalized = Money.normalize(amount);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return mutateBalance(uuid, type, normalized, Mutation.SET);
    }

    private boolean mutateBalance(UUID uuid, String type, BigDecimal amount, Mutation mutation) {
        String currencyType = Main.getNyEconomyAPI().checkVaultType(type);
        ReentrantLock lock = AccountCache.getAccountLock(uuid);
        boolean committed = false;
        lock.lock();
        try (Connection connection = DATA_SOURCE.getConnection()) {
            if (connection == null) {
                return false;
            }
            connection.setAutoCommit(false);
            try {
                ensureBalanceRow(connection, uuid, currencyType);
                BigDecimal currentBalance = selectBalanceForUpdate(connection, uuid, currencyType);
                final BigDecimal newBalance;
                if (mutation == Mutation.DEPOSIT) {
                    try {
                        newBalance = Money.normalize(currentBalance.add(amount));
                    } catch (IllegalArgumentException e) {
                        connection.rollback();
                        return false;
                    }
                } else if (mutation == Mutation.WITHDRAW) {
                    if (currentBalance.compareTo(amount) < 0) {
                        connection.rollback();
                        return false;
                    }
                    newBalance = currentBalance.subtract(amount);
                } else {
                    newBalance = amount;
                }

                upsertBalance(connection, uuid, currencyType, newBalance);
                connection.commit();
                committed = true;
                AccountCache cache = AccountCache.CACHE_DATA.get(uuid);
                if (cache != null) {
                    cache.setBalanceExact(currencyType, newBalance);
                }
                return true;
            } catch (SQLException e) {
                connection.rollback();
                Main.getInstance().getLogger().log(Level.WARNING, "MySQL 更新余额失败", e);
                return false;
            }
        } catch (SQLException e) {
            if (committed) {
                Main.getInstance().getLogger().log(Level.WARNING, "MySQL 余额已提交，但关闭连接失败", e);
                return true;
            }
            Main.getInstance().getLogger().log(Level.WARNING, "MySQL 更新余额连接失败", e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean transfer(UUID source, UUID target, String type, BigDecimal amount) {
        final BigDecimal normalized;
        try {
            normalized = Money.normalize(amount);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (source.equals(target) || normalized.signum() == 0) {
            return false;
        }

        String currencyType = Main.getNyEconomyAPI().checkVaultType(type);
        ReentrantLock sourceLock = AccountCache.getAccountLock(source);
        ReentrantLock targetLock = AccountCache.getAccountLock(target);
        ReentrantLock first = source.toString().compareTo(target.toString()) < 0 ? sourceLock : targetLock;
        ReentrantLock second = first == sourceLock ? targetLock : sourceLock;
        boolean committed = false;
        first.lock();
        second.lock();
        try (Connection connection = DATA_SOURCE.getConnection()) {
            if (connection == null) {
                return false;
            }
            connection.setAutoCommit(false);
            try {
                ensureBalanceRow(connection, source, currencyType);
                ensureBalanceRow(connection, target, currencyType);
                BigDecimal sourceBalance = selectBalanceForUpdate(connection, source, currencyType);
                BigDecimal targetBalance = selectBalanceForUpdate(connection, target, currencyType);
                if (sourceBalance.compareTo(normalized) < 0) {
                    connection.rollback();
                    return false;
                }

                BigDecimal newSourceBalance = sourceBalance.subtract(normalized);
                final BigDecimal newTargetBalance;
                try {
                    newTargetBalance = Money.normalize(targetBalance.add(normalized));
                } catch (IllegalArgumentException e) {
                    connection.rollback();
                    return false;
                }

                upsertBalance(connection, source, currencyType, newSourceBalance);
                upsertBalance(connection, target, currencyType, newTargetBalance);
                connection.commit();
                committed = true;

                AccountCache sourceCache = AccountCache.CACHE_DATA.get(source);
                if (sourceCache != null) {
                    sourceCache.setBalanceExact(currencyType, newSourceBalance);
                }
                AccountCache targetCache = AccountCache.CACHE_DATA.get(target);
                if (targetCache != null) {
                    targetCache.setBalanceExact(currencyType, newTargetBalance);
                }
                return true;
            } catch (SQLException e) {
                connection.rollback();
                Main.getInstance().getLogger().log(Level.WARNING, "MySQL 转账失败", e);
                return false;
            }
        } catch (SQLException e) {
            if (committed) {
                Main.getInstance().getLogger().log(Level.WARNING, "MySQL 转账已提交，但关闭连接失败", e);
                return true;
            }
            Main.getInstance().getLogger().log(Level.WARNING, "MySQL 转账连接失败", e);
            return false;
        } finally {
            second.unlock();
            first.unlock();
        }
    }

    private BigDecimal selectBalanceForUpdate(Connection connection, UUID uuid, String currencyType)
            throws SQLException {
        String sql = "SELECT balance FROM nyeconomy_balances "
                + "WHERE player_uuid = ? AND currency_type = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, currencyType);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Money.normalize(resultSet.getBigDecimal(1)) : Money.ZERO;
            }
        }
    }

    private void ensureBalanceRow(Connection connection, UUID uuid, String currencyType) throws SQLException {
        String sql = "INSERT INTO nyeconomy_balances (player_uuid, currency_type, balance, last_updated) "
                + "VALUES (?, ?, 0.00, ?) ON DUPLICATE KEY UPDATE player_uuid = player_uuid";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, currencyType);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private void upsertBalance(Connection connection, UUID uuid, String currencyType, BigDecimal balance)
            throws SQLException {
        String sql = "INSERT INTO nyeconomy_balances (player_uuid, currency_type, balance, last_updated) "
                + "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE balance = VALUES(balance), "
                + "last_updated = VALUES(last_updated)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, currencyType);
            statement.setBigDecimal(3, balance);
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private enum Mutation {
        DEPOSIT,
        WITHDRAW,
        SET
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
                        topCache.setCurrencyCountExact(resultSet.getString("currency_type"),
                                Money.normalize(resultSet.getBigDecimal("balance")));
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
        try (Connection connection = DATA_SOURCE.getConnection()) {
            if (connection == null) {
                throw new SQLException("无法取得 MySQL 连接");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT currency_type, balance FROM nyeconomy_balances WHERE player_uuid=?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String currencyType = resultSet.getString("currency_type");
                    BigDecimal balance = resultSet.getBigDecimal("balance");
                    newCache.setBalanceExact(currencyType, Money.normalize(balance));
                }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("加载 MySQL 玩家余额失败: " + uuid, e);
        }

        AccountCache existing = AccountCache.CACHE_DATA.putIfAbsent(uuid, newCache);
        return existing != null ? existing : newCache;
    }

    @Override
    public int depositAll(String type, int amount) {
        return depositAll(type, Money.of(amount));
    }

    @Override
    public int depositAll(String type, BigDecimal amount) {
        final BigDecimal normalized;
        try {
            normalized = Money.normalize(amount);
        } catch (IllegalArgumentException e) {
            return 0;
        }

        String currencyType = Main.getNyEconomyAPI().checkVaultType(type);
        boolean committed = false;
        int committedRows = 0;
        try (Connection connection = DATA_SOURCE.getConnection()) {
            if (connection == null) {
                return 0;
            }
            connection.setAutoCommit(false);
            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT balance FROM nyeconomy_balances WHERE currency_type = ? FOR UPDATE")) {
                check.setString(1, currencyType);
                try (ResultSet resultSet = check.executeQuery()) {
                    BigDecimal maximumBeforeDeposit = Money.MAX_BALANCE.subtract(normalized);
                    while (resultSet.next()) {
                        if (resultSet.getBigDecimal(1).compareTo(maximumBeforeDeposit) > 0) {
                            connection.rollback();
                            return 0;
                        }
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE nyeconomy_balances SET balance = balance + ?, last_updated = ? WHERE currency_type = ?")) {
                long now = System.currentTimeMillis();
                statement.setBigDecimal(1, normalized);
                statement.setLong(2, now);
                statement.setString(3, currencyType);
                int affectedRows = statement.executeUpdate();
                connection.commit();
                committedRows = affectedRows;
                committed = true;
                AccountCache.CACHE_DATA.clear();
                return affectedRows;
            }
        } catch (SQLException e) {
            if (committed) {
                Main.getInstance().getLogger().log(Level.WARNING, "批量给予已提交，但关闭连接失败", e);
                return committedRows;
            }
            Main.getInstance().getLogger().log(Level.WARNING, "批量给予货币失败", e);
            return 0;
        }
    }

    @Override
    public int resetAll(String type) {
        String currencyType = Main.getNyEconomyAPI().checkVaultType(type);
        boolean committed = false;
        int committedRows = 0;
        try (Connection connection = DATA_SOURCE.getConnection()) {
            if (connection == null) {
                return 0;
            }
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE nyeconomy_balances SET balance = 0.00, last_updated = ? WHERE currency_type = ?")) {
                long now = System.currentTimeMillis();
                statement.setLong(1, now);
                statement.setString(2, currencyType);
                int affectedRows = statement.executeUpdate();
                connection.commit();
                committedRows = affectedRows;
                committed = true;
                AccountCache.CACHE_DATA.clear();
                return affectedRows;
            }
        } catch (SQLException e) {
            if (committed) {
                Main.getInstance().getLogger().log(Level.WARNING, "批量重置已提交，但关闭连接失败", e);
                return committedRows;
            }
            Main.getInstance().getLogger().log(Level.WARNING, "批量重置货币失败", e);
            return 0;
        }
    }
}
