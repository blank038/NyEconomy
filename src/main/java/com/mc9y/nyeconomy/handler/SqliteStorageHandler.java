package com.mc9y.nyeconomy.handler;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.data.AccountCache;
import com.mc9y.nyeconomy.data.AccountTopCache;
import com.mc9y.nyeconomy.data.TopCache;
import com.mc9y.nyeconomy.migration.BalanceSchemaMigrator;
import com.mc9y.nyeconomy.util.Money;

import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

public class SqliteStorageHandler extends AbstractStorgeHandler {
    private Connection connection;
    private final String dbPath;
    private final ReentrantLock databaseLock = new ReentrantLock();

    public SqliteStorageHandler() {
        File dataDir = new File(Main.getInstance().getDataFolder(), "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        this.dbPath = BalanceSchemaMigrator.getSqliteDatabaseFile(Main.getInstance()).getAbsolutePath();
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
            throw new IllegalStateException("SQLite 初始化失败", e);
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
            try (PreparedStatement version = connection.prepareStatement(
                    "INSERT OR REPLACE INTO nyeconomy_options (option_key, option_value, updated_at) VALUES (?, ?, ?)")) {
                version.setString(1, BalanceSchemaMigrator.VERSION_KEY);
                version.setString(2, BalanceSchemaMigrator.VERSION);
                version.setLong(3, System.currentTimeMillis());
                version.executeUpdate();
            }
        } catch (SQLException e) {
            Main.getInstance().getLogger().log(Level.SEVERE, "创建数据库表失败", e);
            throw new IllegalStateException("创建 SQLite 数据库表失败", e);
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
        ReentrantLock accountLock = AccountCache.getAccountLock(uuid);
        boolean committed = false;
        accountLock.lock();
        databaseLock.lock();
        try (Connection transaction = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            transaction.setAutoCommit(false);
            try {
                BigDecimal currentBalance = selectBalance(transaction, uuid, currencyType);
                final BigDecimal newBalance;
                if (mutation == Mutation.DEPOSIT) {
                    try {
                        newBalance = Money.normalize(currentBalance.add(amount));
                    } catch (IllegalArgumentException e) {
                        transaction.rollback();
                        return false;
                    }
                } else if (mutation == Mutation.WITHDRAW) {
                    if (currentBalance.compareTo(amount) < 0) {
                        transaction.rollback();
                        return false;
                    }
                    newBalance = currentBalance.subtract(amount);
                } else {
                    newBalance = amount;
                }

                upsertBalance(transaction, uuid, currencyType, newBalance);
                transaction.commit();
                committed = true;
                AccountCache cache = AccountCache.CACHE_DATA.get(uuid);
                if (cache != null) {
                    cache.setBalanceExact(currencyType, newBalance);
                }
                return true;
            } catch (SQLException e) {
                transaction.rollback();
                Main.getInstance().getLogger().log(Level.WARNING, "SQLite 更新余额失败", e);
                return false;
            }
        } catch (SQLException e) {
            if (committed) {
                Main.getInstance().getLogger().log(Level.WARNING, "SQLite 余额已提交，但关闭连接失败", e);
                return true;
            }
            Main.getInstance().getLogger().log(Level.WARNING, "SQLite 更新余额事务失败", e);
            return false;
        } finally {
            databaseLock.unlock();
            accountLock.unlock();
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
        databaseLock.lock();
        try (Connection transaction = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            transaction.setAutoCommit(false);
            try {
                BigDecimal sourceBalance = selectBalance(transaction, source, currencyType);
                BigDecimal targetBalance = selectBalance(transaction, target, currencyType);
                if (sourceBalance.compareTo(normalized) < 0) {
                    transaction.rollback();
                    return false;
                }

                BigDecimal newSourceBalance = sourceBalance.subtract(normalized);
                final BigDecimal newTargetBalance;
                try {
                    newTargetBalance = Money.normalize(targetBalance.add(normalized));
                } catch (IllegalArgumentException e) {
                    transaction.rollback();
                    return false;
                }

                upsertBalance(transaction, source, currencyType, newSourceBalance);
                upsertBalance(transaction, target, currencyType, newTargetBalance);
                transaction.commit();
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
                transaction.rollback();
                Main.getInstance().getLogger().log(Level.WARNING, "SQLite 转账失败", e);
                return false;
            }
        } catch (SQLException e) {
            if (committed) {
                Main.getInstance().getLogger().log(Level.WARNING, "SQLite 转账已提交，但关闭连接失败", e);
                return true;
            }
            Main.getInstance().getLogger().log(Level.WARNING, "SQLite 转账事务失败", e);
            return false;
        } finally {
            databaseLock.unlock();
            second.unlock();
            first.unlock();
        }
    }

    private BigDecimal selectBalance(Connection sourceConnection, UUID uuid, String currencyType)
            throws SQLException {
        String sql = "SELECT balance FROM nyeconomy_balances WHERE player_uuid = ? AND currency_type = ?";
        try (PreparedStatement statement = sourceConnection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, currencyType);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Money.fromCents(resultSet.getLong(1)) : Money.ZERO;
            }
        }
    }

    private void upsertBalance(Connection sourceConnection, UUID uuid, String currencyType, BigDecimal balance)
            throws SQLException {
        String sql = "INSERT OR REPLACE INTO nyeconomy_balances "
                + "(id, player_uuid, currency_type, balance, last_updated) "
                + "VALUES ((SELECT id FROM nyeconomy_balances WHERE player_uuid = ? AND currency_type = ?), ?, ?, ?, ?)";
        try (PreparedStatement statement = sourceConnection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, currencyType);
            statement.setString(3, uuid.toString());
            statement.setString(4, currencyType);
            statement.setLong(5, Money.toCents(balance));
            statement.setLong(6, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    @Override
    public void save() {
        close();
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
                 ResultSet rs = stmt.executeQuery(
                         "SELECT DISTINCT p.player_name FROM nyeconomy_balances b " +
                         "JOIN nyeconomy_players p ON b.player_uuid = p.player_uuid")) {
                
                while (rs.next()) {
                    String playerName = rs.getString("player_name");
                    AccountTopCache topCache = new AccountTopCache();
                    
                    try (PreparedStatement ps = connection.prepareStatement(
                            "SELECT b.currency_type, b.balance FROM nyeconomy_balances b " +
                            "JOIN nyeconomy_players p ON b.player_uuid = p.player_uuid " +
                            "WHERE p.player_name = ?")) {
                        ps.setString(1, playerName);
                        try (ResultSet balanceRs = ps.executeQuery()) {
                            while (balanceRs.next()) {
                                topCache.setCurrencyCountExact(balanceRs.getString("currency_type"),
                                        Money.fromCents(balanceRs.getLong("balance")));
                            }
                        }
                    }
                    
                    cacheData.put(playerName, topCache);
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

    private AccountCache loadPlayerCacheFromDatabase(UUID uuid) {
        AccountCache cache = new AccountCache();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT currency_type, balance FROM nyeconomy_balances WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String currencyType = rs.getString("currency_type");
                    long balance = rs.getLong("balance");
                    cache.setBalanceExact(currencyType, Money.fromCents(balance));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("加载 SQLite 玩家余额失败: " + uuid, e);
        }
        return cache;
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
        long cents = Money.toCents(normalized);
        boolean committed = false;
        int committedRows = 0;
        databaseLock.lock();
        try (Connection transaction = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            transaction.setAutoCommit(false);
            try (PreparedStatement check = transaction.prepareStatement(
                    "SELECT 1 FROM nyeconomy_balances WHERE currency_type = ? AND balance > ? LIMIT 1")) {
                check.setString(1, currencyType);
                check.setLong(2, Money.toCents(Money.MAX_BALANCE.subtract(normalized)));
                try (ResultSet resultSet = check.executeQuery()) {
                    if (resultSet.next()) {
                        transaction.rollback();
                        return 0;
                    }
                }
            }
            try (PreparedStatement statement = transaction.prepareStatement(
                    "UPDATE nyeconomy_balances SET balance = balance + ?, last_updated = ? WHERE currency_type = ?")) {
                statement.setLong(1, cents);
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, currencyType);
                int affectedRows = statement.executeUpdate();
                transaction.commit();
                committedRows = affectedRows;
                committed = true;
                AccountCache.CACHE_DATA.clear();
                return affectedRows;
            }
        } catch (SQLException e) {
            if (committed) {
                Main.getInstance().getLogger().log(Level.WARNING, "SQLite 批量给予已提交，但关闭连接失败", e);
                return committedRows;
            }
            Main.getInstance().getLogger().log(Level.WARNING, "批量给予货币失败", e);
            return 0;
        } finally {
            databaseLock.unlock();
        }
    }

    @Override
    public int resetAll(String type) {
        String currencyType = Main.getNyEconomyAPI().checkVaultType(type);
        boolean committed = false;
        int committedRows = 0;
        databaseLock.lock();
        try (Connection transaction = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            transaction.setAutoCommit(false);
            try (PreparedStatement statement = transaction.prepareStatement(
                    "UPDATE nyeconomy_balances SET balance = 0, last_updated = ? WHERE currency_type = ?")) {
                statement.setLong(1, System.currentTimeMillis());
                statement.setString(2, currencyType);
                int affectedRows = statement.executeUpdate();
                transaction.commit();
                committedRows = affectedRows;
                committed = true;
                AccountCache.CACHE_DATA.clear();
                return affectedRows;
            }
        } catch (SQLException e) {
            if (committed) {
                Main.getInstance().getLogger().log(Level.WARNING, "SQLite 批量重置已提交，但关闭连接失败", e);
                return committedRows;
            }
            Main.getInstance().getLogger().log(Level.WARNING, "批量重置货币失败", e);
            return 0;
        } finally {
            databaseLock.unlock();
        }
    }

    private enum Mutation {
        DEPOSIT,
        WITHDRAW,
        SET
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
