package com.mc9y.nyeconomy.migration;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.util.Money;

import java.io.File;
import java.nio.file.Path;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public final class BalanceSchemaMigrator {
    public static final String VERSION_KEY = "balance_schema_version";
    public static final String VERSION = "2";

    private static final String MYSQL_LOCK = "nyeconomy_balance_schema_v2";
    private static final String MYSQL_BACKUP_TABLE = "nyeconomy_balances_v1_backup";
    private static final String SQLITE_MIGRATION_TABLE = "nyeconomy_balances_v2_migration";
    private static final ReentrantLock SQLITE_MIGRATION_LOCK = new ReentrantLock();

    private BalanceSchemaMigrator() {
    }

    public static void migrate(Main plugin) throws Exception {
        if (plugin.isMySqlMode()) {
            migrateMySql(plugin);
        } else {
            migrateSqlite(plugin);
        }
    }

    public static File getSqliteDatabaseFile(Main plugin) {
        return new File(new File(plugin.getDataFolder(), "data"), "economy.db");
    }

    private static void migrateMySql(Main plugin) throws SQLException {
        String url = plugin.getConfig().getString("data-option.mysql.url");
        String user = plugin.getConfig().getString("data-option.mysql.user");
        String password = plugin.getConfig().getString("data-option.mysql.password");

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            if (!acquireMySqlLock(connection)) {
                throw new SQLException("无法取得余额结构迁移锁");
            }

            try {
                createMySqlOptionsTable(connection);
                if (!tableExists(connection, "nyeconomy_balances")) {
                    return;
                }

                String version = readVersion(connection);
                if (version != null && !version.isEmpty() && !VERSION.equals(version)) {
                    throw new SQLException("不支持的 MySQL 余额结构版本: " + version);
                }
                MySqlColumn column = readMySqlBalanceColumn(connection);
                if (column == null) {
                    throw new SQLException("nyeconomy_balances.balance 列不存在");
                }

                if (column.isDecimal()) {
                    verifyMySqlDecimalColumn(column);
                    verifyMySqlBalances(connection);
                    if (!VERSION.equals(version) && tableExists(connection, MYSQL_BACKUP_TABLE)) {
                        verifyMySqlBackup(connection);
                    }
                    writeMySqlVersion(connection);
                    plugin.getLogger().info("MySQL 余额结构已经是 DECIMAL(12,2)");
                    return;
                }

                if (VERSION.equals(version)) {
                    throw new SQLException("余额迁移标记为 v2，但 MySQL balance 列不是 DECIMAL");
                }
                if (!column.isInteger()) {
                    throw new SQLException("不支持的 MySQL balance 类型: " + column.dataType);
                }

                validateMySqlLegacyBalances(connection);
                backupMySqlBalances(connection);

                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("ALTER TABLE nyeconomy_balances "
                            + "MODIFY COLUMN balance DECIMAL(12,2) NOT NULL DEFAULT 0.00");
                }

                verifyMySqlDecimalColumn(readMySqlBalanceColumn(connection));
                verifyMySqlBalances(connection);
                verifyMySqlBackup(connection);
                writeMySqlVersion(connection);
                plugin.getLogger().info("MySQL 余额已自动迁移为 DECIMAL(12,2)，备份表: " + MYSQL_BACKUP_TABLE);
            } finally {
                releaseMySqlLock(connection);
            }
        }
    }

    private static boolean acquireMySqlLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 30)")) {
            statement.setString(1, MYSQL_LOCK);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) == 1;
            }
        }
    }

    private static void releaseMySqlLock(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, MYSQL_LOCK);
            statement.executeQuery();
        } catch (SQLException ignored) {
        }
    }

    private static void createMySqlOptionsTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS nyeconomy_options ("
                    + "option_key VARCHAR(50) PRIMARY KEY,"
                    + "option_value TEXT,"
                    + "updated_at BIGINT NOT NULL)");
        }
    }

    private static MySqlColumn readMySqlBalanceColumn(Connection connection) throws SQLException {
        String sql = "SELECT DATA_TYPE, NUMERIC_PRECISION, NUMERIC_SCALE "
                + "FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'nyeconomy_balances' "
                + "AND COLUMN_NAME = 'balance'";
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                return null;
            }
            return new MySqlColumn(resultSet.getString("DATA_TYPE"),
                    resultSet.getInt("NUMERIC_PRECISION"), resultSet.getInt("NUMERIC_SCALE"));
        }
    }

    private static void verifyMySqlDecimalColumn(MySqlColumn column) throws SQLException {
        if (column == null || !column.isDecimal() || column.precision != 12 || column.scale != 2) {
            throw new SQLException("MySQL balance 列不是预期的 DECIMAL(12,2)");
        }
    }

    private static void validateMySqlLegacyBalances(Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM nyeconomy_balances "
                + "WHERE balance IS NULL OR balance < 0 OR balance > 2147483647";
        if (queryCount(connection, sql) != 0) {
            throw new SQLException("MySQL 存在负数、空值或超出旧 int 范围的余额");
        }
    }

    private static void verifyMySqlBalances(Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM nyeconomy_balances "
                + "WHERE balance IS NULL OR balance < 0.00 OR balance > 2147483647.99 "
                + "OR balance <> ROUND(balance, 2)";
        if (queryCount(connection, sql) != 0) {
            throw new SQLException("MySQL DECIMAL 余额校验失败");
        }
    }

    private static void backupMySqlBalances(Connection connection) throws SQLException {
        if (!tableExists(connection, MYSQL_BACKUP_TABLE)) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE " + MYSQL_BACKUP_TABLE + " LIKE nyeconomy_balances");
                statement.executeUpdate("INSERT INTO " + MYSQL_BACKUP_TABLE + " SELECT * FROM nyeconomy_balances");
            }
        }
        verifyMySqlBackup(connection);
    }

    private static void verifyMySqlBackup(Connection connection) throws SQLException {
        long sourceCount = queryCount(connection, "SELECT COUNT(*) FROM nyeconomy_balances");
        long backupCount = queryCount(connection, "SELECT COUNT(*) FROM " + MYSQL_BACKUP_TABLE);
        if (sourceCount != backupCount) {
            throw new SQLException("MySQL 余额备份行数不一致");
        }

        String mismatchSql = "SELECT COUNT(*) FROM nyeconomy_balances current_data "
                + "LEFT JOIN " + MYSQL_BACKUP_TABLE + " old_data ON current_data.id = old_data.id "
                + "WHERE old_data.id IS NULL "
                + "OR NOT (current_data.player_uuid <=> old_data.player_uuid) "
                + "OR NOT (current_data.currency_type <=> old_data.currency_type) "
                + "OR current_data.balance <> CAST(old_data.balance AS DECIMAL(12,2)) "
                + "OR current_data.last_updated <> old_data.last_updated";
        if (queryCount(connection, mismatchSql) != 0) {
            throw new SQLException("MySQL 余额备份内容不一致");
        }
    }

    private static void writeMySqlVersion(Connection connection) throws SQLException {
        String sql = "INSERT INTO nyeconomy_options (option_key, option_value, updated_at) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE option_value = VALUES(option_value), updated_at = VALUES(updated_at)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, VERSION_KEY);
            statement.setString(2, VERSION);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private static void migrateSqlite(Main plugin) throws Exception {
        File databaseFile = getSqliteDatabaseFile(plugin);
        migrateSqliteFile(databaseFile, plugin.getLogger());
    }

    static void migrateSqliteFile(File databaseFile, Logger logger) throws Exception {
        if (!databaseFile.exists()) {
            return;
        }

        Path lockPath = new File(databaseFile.getParentFile(), databaseFile.getName() + ".migration.lock").toPath();
        SQLITE_MIGRATION_LOCK.lock();
        try {
            try (FileChannel channel = FileChannel.open(lockPath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                migrateSqliteFileLocked(databaseFile, logger);
            }
        } finally {
            SQLITE_MIGRATION_LOCK.unlock();
        }
    }

    private static void migrateSqliteFileLocked(File databaseFile, Logger logger) throws Exception {

        Class.forName("org.sqlite.JDBC");
        String jdbcUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
        boolean migrationRequired;
        Path backup = new File(databaseFile.getParentFile(),
                databaseFile.getName() + ".pre-decimal-" + System.currentTimeMillis() + ".bak").toPath();

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            createSqliteOptionsTable(connection);
            if (!tableExists(connection, "nyeconomy_balances")) {
                return;
            }

            String version = readVersion(connection);
            if (VERSION.equals(version)) {
                verifySqliteBalances(connection);
                logger.info("SQLite 余额结构已经使用整数分存储");
                return;
            }
            if (version != null && !version.isEmpty()) {
                throw new SQLException("不支持的 SQLite 余额结构版本: " + version);
            }

            verifySqliteLegacySchema(connection);
            validateSqliteLegacyBalances(connection);
            verifySqliteIntegrity(connection);
            checkpointSqlite(connection);
            createSqliteBackup(connection, backup);
            logger.info("SQLite 迁移前备份已创建: " + backup.toAbsolutePath());
            migrationRequired = true;
        }

        if (!migrationRequired) {
            return;
        }

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            connection.setAutoCommit(false);
            try {
                String currentVersion = readVersion(connection);
                if (VERSION.equals(currentVersion)) {
                    verifySqliteBalances(connection);
                    connection.commit();
                    return;
                }
                if (currentVersion != null && !currentVersion.isEmpty()) {
                    throw new SQLException("不支持的 SQLite 余额结构版本: " + currentVersion);
                }
                validateSqliteLegacyBalances(connection);
                if (tableExists(connection, SQLITE_MIGRATION_TABLE)) {
                    throw new SQLException("SQLite 临时迁移表已存在: " + SQLITE_MIGRATION_TABLE);
                }

                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("CREATE TABLE " + SQLITE_MIGRATION_TABLE + " ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + "player_uuid VARCHAR(36) NOT NULL,"
                            + "currency_type VARCHAR(50) NOT NULL,"
                            + "balance INTEGER NOT NULL DEFAULT 0,"
                            + "last_updated BIGINT NOT NULL,"
                            + "UNIQUE(player_uuid, currency_type))");
                    statement.executeUpdate("INSERT INTO " + SQLITE_MIGRATION_TABLE
                            + " (id, player_uuid, currency_type, balance, last_updated) "
                            + "SELECT id, player_uuid, currency_type, balance * 100, last_updated "
                            + "FROM nyeconomy_balances");
                }

                long oldCount = queryCount(connection, "SELECT COUNT(*) FROM nyeconomy_balances");
                long newCount = queryCount(connection, "SELECT COUNT(*) FROM " + SQLITE_MIGRATION_TABLE);
                long mismatch = queryCount(connection, "SELECT COUNT(*) FROM nyeconomy_balances old_data "
                        + "JOIN " + SQLITE_MIGRATION_TABLE + " new_data ON old_data.id = new_data.id "
                        + "WHERE old_data.player_uuid <> new_data.player_uuid "
                        + "OR old_data.currency_type <> new_data.currency_type "
                        + "OR old_data.balance * 100 <> new_data.balance "
                        + "OR old_data.last_updated <> new_data.last_updated");
                if (oldCount != newCount || mismatch != 0) {
                    throw new SQLException("SQLite 余额复制校验失败");
                }

                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DROP TABLE nyeconomy_balances");
                    statement.executeUpdate("ALTER TABLE " + SQLITE_MIGRATION_TABLE + " RENAME TO nyeconomy_balances");
                    statement.executeUpdate("CREATE INDEX idx_player_uuid ON nyeconomy_balances(player_uuid)");
                    statement.executeUpdate("CREATE INDEX idx_currency ON nyeconomy_balances(currency_type)");
                    statement.executeUpdate("CREATE INDEX idx_balance_desc ON nyeconomy_balances(currency_type, balance DESC)");
                }
                writeSqliteVersion(connection);
                verifySqliteBalances(connection);
                connection.commit();
                logger.info("SQLite 余额已自动迁移为整数分存储");
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private static void createSqliteOptionsTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS nyeconomy_options ("
                    + "option_key VARCHAR(50) PRIMARY KEY,"
                    + "option_value TEXT,"
                    + "updated_at BIGINT NOT NULL)");
        }
    }

    private static void verifySqliteLegacySchema(Connection connection) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(nyeconomy_balances)")) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("name"));
            }
        }
        Set<String> required = new HashSet<>(Arrays.asList(
                "id", "player_uuid", "currency_type", "balance", "last_updated"));
        if (!columns.containsAll(required)) {
            throw new SQLException("SQLite 余额表不是预期的 2.0.3 结构");
        }
    }

    private static void validateSqliteLegacyBalances(Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM nyeconomy_balances "
                + "WHERE balance IS NULL OR typeof(balance) <> 'integer' "
                + "OR balance < 0 OR balance > 2147483647";
        if (queryCount(connection, sql) != 0) {
            throw new SQLException("SQLite 存在负数、非整数或超出旧 int 范围的余额");
        }
    }

    private static void verifySqliteBalances(Connection connection) throws SQLException {
        long maxCents = Money.toCents(Money.MAX_BALANCE);
        String sql = "SELECT COUNT(*) FROM nyeconomy_balances "
                + "WHERE balance IS NULL OR typeof(balance) <> 'integer' OR balance < 0 OR balance > ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, maxCents);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getLong(1) != 0) {
                    throw new SQLException("SQLite 整数分余额校验失败");
                }
            }
        }
    }

    private static void verifySqliteIntegrity(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA integrity_check")) {
            if (!resultSet.next() || !"ok".equalsIgnoreCase(resultSet.getString(1))) {
                throw new SQLException("SQLite integrity_check 未通过");
            }
        }
    }

    private static void checkpointSqlite(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA wal_checkpoint(FULL)")) {
            if (resultSet.next()) {
                int busy = resultSet.getInt(1);
                int logFrames = resultSet.getInt(2);
                int checkpointedFrames = resultSet.getInt(3);
                if (busy != 0 || logFrames != checkpointedFrames) {
                    throw new SQLException("SQLite WAL checkpoint 未完成");
                }
            }
        }
    }

    private static void createSqliteBackup(Connection connection, Path backup) throws SQLException {
        String escapedPath = backup.toAbsolutePath().toString().replace("'", "''");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("VACUUM INTO '" + escapedPath + "'");
        }
    }

    private static void writeSqliteVersion(Connection connection) throws SQLException {
        String sql = "INSERT OR REPLACE INTO nyeconomy_options "
                + "(option_key, option_value, updated_at) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, VERSION_KEY);
            statement.setString(2, VERSION);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private static String readVersion(Connection connection) throws SQLException {
        String sql = "SELECT option_value FROM nyeconomy_options WHERE option_key = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, VERSION_KEY);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet resultSet = metadata.getTables(connection.getCatalog(), null, tableName, new String[]{"TABLE"})) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metadata.getTables(connection.getCatalog(), null,
                tableName.toUpperCase(), new String[]{"TABLE"})) {
            return resultSet.next();
        }
    }

    private static long queryCount(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                throw new SQLException("查询未返回结果: " + sql);
            }
            return resultSet.getLong(1);
        }
    }

    private static final class MySqlColumn {
        private final String dataType;
        private final int precision;
        private final int scale;

        private MySqlColumn(String dataType, int precision, int scale) {
            this.dataType = dataType;
            this.precision = precision;
            this.scale = scale;
        }

        private boolean isDecimal() {
            return "decimal".equalsIgnoreCase(dataType) || "numeric".equalsIgnoreCase(dataType);
        }

        private boolean isInteger() {
            return "int".equalsIgnoreCase(dataType) || "integer".equalsIgnoreCase(dataType)
                    || "smallint".equalsIgnoreCase(dataType) || "mediumint".equalsIgnoreCase(dataType)
                    || "bigint".equalsIgnoreCase(dataType);
        }
    }
}
