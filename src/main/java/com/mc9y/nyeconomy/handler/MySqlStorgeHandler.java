package com.mc9y.nyeconomy.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.data.AccountCache;
import com.mc9y.nyeconomy.interfaces.AbstractDataSourceHandler;
import com.mc9y.nyeconomy.interfaces.impl.CommonDataSourceHandlerImpl;
import com.mc9y.nyeconomy.interfaces.impl.HikariDataSourceHandlerImpl;

import java.sql.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Blank038
 * @since 2021-03-07
 */
public class MySqlStorgeHandler extends AbstractStorgeHandler {
    public static boolean SQL_STATUS = false;

    private final AbstractDataSourceHandler DATA_SOURCE;
    private final Gson GSON = new GsonBuilder().create();

    public MySqlStorgeHandler() {
        this.DATA_SOURCE = Main.getInstance().hasHikariCP() ? new HikariDataSourceHandlerImpl() : new CommonDataSourceHandlerImpl();
        this.createTable();
    }

    public void createTable() {
        this.DATA_SOURCE.connect((connection, statement) -> {
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
        System.out.println();
        if (status == 0 || this.isExists(name)) {
            AccountCache cache = this.getPlayerCache(name);
            // 模拟提交玩家数据, 如果玩家不存在
            return cache.balance(Main.getNyEconomyAPI().checkVaultType(type));
        } else {
            return 0;
        }
    }

    @Override
    public synchronized boolean deposit(String name, String type, int amount) {
        boolean exists = this.isExists(name);
        AccountCache cache = this.getPlayerCache(name);
        String lastType = Main.getNyEconomyAPI().checkVaultType(type);
        int now = cache.balance(lastType);
        this.submitData(exists, name, cache.set(lastType, now + amount).toJsonObject());
        this.updateCache(name, cache);
        return true;
    }

    @Override
    public synchronized boolean withdraw(String name, String type, int amount) {
        if (this.isExists(name)) {
            String lastType = Main.getNyEconomyAPI().checkVaultType(type);
            int now = this.balance(name, lastType, 0);
            if (now < amount) {
                return false;
            }
            AccountCache cache = this.getPlayerCache(name);
            JsonObject object = cache.set(lastType, now - amount).toJsonObject();
            this.submitData(true, name, object);
            this.updateCache(name, cache);
            return true;
        }
        return false;
    }

    @Override
    public synchronized boolean set(String name, String type, int amount) {
        boolean exists = this.isExists(name);
        AccountCache cache = this.getPlayerCache(name);
        this.submitData(exists, name, cache.set(Main.getNyEconomyAPI().checkVaultType(type), amount).toJsonObject());
        this.updateCache(name, cache);
        return true;
    }

    @Override
    public boolean isExists(String name) {
        AtomicBoolean exists = new AtomicBoolean(false);
        this.DATA_SOURCE.connect((connection, statement) -> {
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
                this.DATA_SOURCE.close(statement, resultSet);
            }
        }, "SELECT data from ny_economy WHERE user=?");
        return exists.get();
    }

    @Override
    public void save() {

    }

    public void updateCache(String name, AccountCache cache) {
        if (AccountCache.CACHE_DATA.containsKey(name)) {
            AccountCache.CACHE_DATA.get(name).update(cache.toJsonObject());
        }
    }

    private void submitData(boolean exists, String name, JsonObject object) {
        this.DATA_SOURCE.connect((connection, statement) -> {
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
                this.DATA_SOURCE.close(statement, null);
            }
        }, exists ? "UPDATE ny_economy SET data=? WHERE user=?" : "INSERT INTO ny_economy (user,data) VALUES (?,?)");
    }

    @Override
    public AccountCache getPlayerCache(String name) {
        if (this.isExists(name)) {
            AtomicReference<JsonObject> atomicReference = new AtomicReference<>();
            this.DATA_SOURCE.connect((connection, statement) -> {
                ResultSet resultSet = null;
                try {
                    statement.setString(1, name);
                    resultSet = statement.executeQuery();
                    if (resultSet.next()) {
                        atomicReference.set(GSON.fromJson(resultSet.getString("data"), JsonObject.class));
                    }
                } catch (SQLException throwables) {
                    throwables.printStackTrace();
                } finally {
                    this.DATA_SOURCE.close(statement, resultSet);
                }
            }, "SELECT data from ny_economy WHERE user=?");
            return new AccountCache(atomicReference.get());
        }
        return new AccountCache(new JsonObject());
    }
}