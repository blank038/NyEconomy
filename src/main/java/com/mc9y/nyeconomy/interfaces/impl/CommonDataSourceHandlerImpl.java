package com.mc9y.nyeconomy.interfaces.impl;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.interfaces.AbstractDataSourceHandler;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * @author Blank038
 * @since 2021-03-11
 */
public class CommonDataSourceHandlerImpl extends AbstractDataSourceHandler {
    private final String SQL_URL, SQL_USER, SQL_PASSWORD;

    public CommonDataSourceHandlerImpl() {
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        // 初始化 MySQL 参数
        this.SQL_URL = Main.getInstance().getConfig().getString("data-option.url");
        this.SQL_USER = Main.getInstance().getConfig().getString("data-option.user");
        this.SQL_PASSWORD = Main.getInstance().getConfig().getString("data-option.password");
    }

    @Override
    public Connection getConnection() {
        try {
            return DriverManager.getConnection(this.SQL_URL, this.SQL_USER, this.SQL_PASSWORD);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return null;
    }
}