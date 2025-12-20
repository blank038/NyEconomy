package com.mc9y.nyeconomy.interfaces.impl;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.interfaces.AbstractDataSourceHandlerImpl;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author Blank038
 * @since 2021-03-11
 */
public class HikariDataSourceHandler extends AbstractDataSourceHandlerImpl {
    private final HikariDataSource dataSource;

    public HikariDataSourceHandler() {
        HikariConfig config = new HikariConfig();
        config.setPoolName(Main.getInstance().getDescription().getName() + "-Pool");
        config.setJdbcUrl(Main.getInstance().getConfig().getString("data-option.mysql.url"));
        config.setUsername(Main.getInstance().getConfig().getString("data-option.mysql.user"));
        config.setPassword(Main.getInstance().getConfig().getString("data-option.mysql.password"));
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("autoReconnect", "true");
        config.addDataSourceProperty("useSSL", "false");
        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public Connection getConnection() {
        try {
            return this.dataSource.getConnection();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return null;
    }

}