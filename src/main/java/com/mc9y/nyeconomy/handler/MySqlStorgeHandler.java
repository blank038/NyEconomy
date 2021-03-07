package com.mc9y.nyeconomy.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.data.AccountCache;
import com.mc9y.nyeconomy.handler.execute.ExecuteModel;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Blank038
 * @since 2021-03-07
 */
public class MySqlStorgeHandler extends AbstractStorgeHandler {
    public static boolean SQL_STATUS = false;

    private final HikariDataSource DATA_SOURCE;
    private final Gson GSON = new GsonBuilder().create();

    public MySqlStorgeHandler() {
        HikariConfig config = new HikariConfig();
        config.setPoolName(Main.getInstance().getDescription().getName() + "Pool");
        config.setJdbcUrl(Main.getInstance().getConfig().getString("data-option.url"));
        config.setUsername(Main.getInstance().getConfig().getString("data-option.user"));
        config.setPassword(Main.getInstance().getConfig().getString("data-option.password"));
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("autoReconnect", "true");
        config.addDataSourceProperty("useSSL", "false");
        this.DATA_SOURCE = new HikariDataSource(config);
        this.createTable();
    }

    public void createTable() {
        connect((connection, statement) -> {
            try {
                statement.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, "CREATE TABLE IF NOT EXISTS ny_economy (user VARCHAR(30) NOT NULL, data TEXT, PRIMARY KEY ( user ))");
    }

    @Override
    public synchronized int balance(String name, String type, int status) {
        if (status == 1) {
            return 0;
        }
        if (status == 0 || this.isExists(name)) {
            AtomicReference<JsonObject> atomic = new AtomicReference<>();
            this.connect((connection, statement) -> {
                ResultSet resultSet = null;
                try {
                    statement.setString(1, name);
                    resultSet = statement.executeQuery();
                    if (resultSet.next()) {
                        atomic.set(GSON.fromJson(resultSet.getString("data"), JsonObject.class));
                    }
                } catch (SQLException throwables) {
                    throwables.printStackTrace();
                } finally {
                    this.close(connection, statement, resultSet);
                }
            }, "SELECT data from ny_economy WHERE user=?");
            AccountCache cache = AccountCache.CACHE_DATA.getOrDefault(name,
                    AccountCache.CACHE_DATA.put(name, new AccountCache(atomic.get())));
            cache.update(atomic.get());
            return cache.balance(type);
        } else {
            AccountCache.CACHE_DATA.getOrDefault(name, AccountCache.CACHE_DATA.put(name, new AccountCache(new JsonObject())));
            return 0;
        }
    }

    @Override
    public synchronized boolean deposit(String name, String type, int amount) {
        boolean exists = this.isExists(name);
        int now = AccountCache.CACHE_DATA.containsKey(name) ?
                AccountCache.CACHE_DATA.get(name).balance(type) : this.balance(name, type, exists ? 0 : 1);
        JsonObject object = AccountCache.CACHE_DATA.get(name).set(type, now + amount).toJsonObject();
        this.submitData(exists, name, object);
        return true;
    }

    @Override
    public synchronized boolean withdraw(String name, String type, int amount) {
        if (this.isExists(name)) {
            int now = AccountCache.CACHE_DATA.containsKey(name) ?
                    AccountCache.CACHE_DATA.get(name).balance(type) : this.balance(name, type, 0);
            if (now < amount) {
                return false;
            }
            JsonObject object = AccountCache.CACHE_DATA.get(name).set(type, now - amount).toJsonObject();
            this.submitData(true, name, object);
            return true;
        }
        return false;
    }

    @Override
    public synchronized boolean set(String name, String type, int amount) {
        boolean exists = this.isExists(name);
        if (exists) {
            this.balance(name, type, 0);
        } else {
            AccountCache.CACHE_DATA.put(name, new AccountCache(new JsonObject()));
        }
        JsonObject object = AccountCache.CACHE_DATA.get(name).set(type, amount).toJsonObject();
        this.submitData(exists, name, object);
        return true;
    }

    @Override
    public boolean isExists(String name) {
        AtomicBoolean exists = new AtomicBoolean(false);
        this.connect((connection, statement) -> {
            ResultSet resultSet = null;
            try {
                statement.setString(1, name);
                resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    exists.set(true);
                }
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            } finally {
                this.close(connection, statement, resultSet);
            }
        }, "SELECT data from ny_economy WHERE user=?");
        return exists.get();
    }

    @Override
    public void save() {

    }

    public void connect(ExecuteModel executeModel, String sql) {
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = this.DATA_SOURCE.getConnection();
            statement = connection.prepareStatement(sql);
            executeModel.run(connection, statement);
        } catch (SQLException e) {
            SQL_STATUS = false;
            e.printStackTrace();
        } finally {
            close(connection, statement, null);
        }
    }

    public void close(Connection connection, Statement statement, ResultSet resultSet) {
        try {
            if (resultSet != null) {
                resultSet.close();
            }
            if (statement != null) {
                statement.close();
            }
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void submitData(boolean exists, String name, JsonObject object) {
        this.connect((connection, statement) -> {
            try {
                if (exists) {
                    statement.setString(1, object.toString());
                    statement.setString(2, name);
                } else {
                    statement.setString(1, name);
                    statement.setString(2, object.toString());
                }
                statement.executeUpdate();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            } finally {
                this.close(connection, statement, null);
            }
        }, exists ? "UPDATE ny_economy SET data=? WHERE user=?" : "INSERT INTO ny_economy (user,data) VALUES (?,?)");
    }
}