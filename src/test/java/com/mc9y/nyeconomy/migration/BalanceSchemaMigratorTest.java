package com.mc9y.nyeconomy.migration;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BalanceSchemaMigratorTest {
    @ClassRule
    public static final TemporaryFolder TEMPORARY_FOLDER = new TemporaryFolder();

    @Test
    public void migratesLegacySQLiteBalancesOnce() throws Exception {
        Class.forName("org.sqlite.JDBC");
        File database = TEMPORARY_FOLDER.newFile("economy.db");
        String url = "jdbc:sqlite:" + database.getAbsolutePath();

        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE nyeconomy_balances ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "player_uuid VARCHAR(36) NOT NULL,"
                    + "currency_type VARCHAR(50) NOT NULL,"
                    + "balance INTEGER NOT NULL DEFAULT 0,"
                    + "last_updated BIGINT NOT NULL,"
                    + "UNIQUE(player_uuid, currency_type))");
            statement.executeUpdate("CREATE TABLE nyeconomy_options ("
                    + "option_key VARCHAR(50) PRIMARY KEY, option_value TEXT, updated_at BIGINT NOT NULL)");
            statement.executeUpdate("INSERT INTO nyeconomy_balances "
                    + "(player_uuid, currency_type, balance, last_updated) VALUES "
                    + "('00000000-0000-0000-0000-000000000001', 'coin', 100, 1),"
                    + "('00000000-0000-0000-0000-000000000002', 'coin', 1, 2)");
        }

        Logger logger = Logger.getLogger("BalanceSchemaMigratorTest");
        BalanceSchemaMigrator.migrateSqliteFile(database, logger);
        assertSQLiteState(url, 10000L, 100L);

        BalanceSchemaMigrator.migrateSqliteFile(database, logger);
        assertSQLiteState(url, 10000L, 100L);

        File[] backups = database.getParentFile().listFiles((directory, name) ->
                name.startsWith("economy.db.pre-decimal-") && name.endsWith(".bak"));
        assertTrue(backups != null && backups.length == 1);
    }

    @Test
    public void concurrentMigrationDoesNotScaleTwice() throws Exception {
        Class.forName("org.sqlite.JDBC");
        File database = TEMPORARY_FOLDER.newFile("concurrent.db");
        String url = "jdbc:sqlite:" + database.getAbsolutePath();
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE nyeconomy_balances ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "player_uuid VARCHAR(36) NOT NULL,"
                    + "currency_type VARCHAR(50) NOT NULL,"
                    + "balance INTEGER NOT NULL DEFAULT 0,"
                    + "last_updated BIGINT NOT NULL,"
                    + "UNIQUE(player_uuid, currency_type))");
            statement.executeUpdate("CREATE TABLE nyeconomy_options ("
                    + "option_key VARCHAR(50) PRIMARY KEY, option_value TEXT, updated_at BIGINT NOT NULL)");
            statement.executeUpdate("INSERT INTO nyeconomy_balances "
                    + "(player_uuid, currency_type, balance, last_updated) VALUES "
                    + "('00000000-0000-0000-0000-000000000003', 'coin', 100, 1)");
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> migrateAfter(start, database));
            Future<?> second = executor.submit(() -> migrateAfter(start, database));
            start.countDown();
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }

        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT balance FROM nyeconomy_balances")) {
            assertTrue(resultSet.next());
            assertEquals(10000L, resultSet.getLong(1));
        }
    }

    private void migrateAfter(CountDownLatch start, File database) {
        try {
            start.await();
            BalanceSchemaMigrator.migrateSqliteFile(database,
                    Logger.getLogger("BalanceSchemaMigratorConcurrentTest"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertSQLiteState(String url, long firstBalance, long secondBalance) throws Exception {
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT balance FROM nyeconomy_balances ORDER BY id")) {
                assertTrue(resultSet.next());
                assertEquals(firstBalance, resultSet.getLong(1));
                assertTrue(resultSet.next());
                assertEquals(secondBalance, resultSet.getLong(1));
            }
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT option_value FROM nyeconomy_options WHERE option_key = 'balance_schema_version'")) {
                assertTrue(resultSet.next());
                assertEquals("2", resultSet.getString(1));
            }
        }
    }
}
