package com.mc9y.nyeconomy.interfaces.impl;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.interfaces.IDataSourceHandler;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author Blank038
 * @since 2021-03-11
 */
public class HikariDataSourceHandler implements IDataSourceHandler {
    private final HikariDataSource DATA_SOURCE;

    public HikariDataSourceHandler() {
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
    }

    @Override
    public Connection getConnection() {
        try {
            return this.DATA_SOURCE.getConnection();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return null;
    }
}
