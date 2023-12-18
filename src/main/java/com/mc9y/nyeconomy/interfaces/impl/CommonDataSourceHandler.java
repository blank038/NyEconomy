package com.mc9y.nyeconomy.interfaces.impl;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.interfaces.AbstractDataSourceHandlerImpl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * @author Blank038
 * @since 2021-03-11
 */
public class CommonDataSourceHandler extends AbstractDataSourceHandlerImpl {
    private final String sqlUrl, sqlUser, sqlPassword;

    public CommonDataSourceHandler() {
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        // 初始化 MySQL 参数
        this.sqlUrl = Main.getInstance().getConfig().getString("data-option.url");
        this.sqlUser = Main.getInstance().getConfig().getString("data-option.user");
        this.sqlPassword = Main.getInstance().getConfig().getString("data-option.password");
    }

    @Override
    public Connection getConnection() {
        try {
            return DriverManager.getConnection(this.sqlUrl, this.sqlUser, this.sqlPassword);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return null;
    }
}