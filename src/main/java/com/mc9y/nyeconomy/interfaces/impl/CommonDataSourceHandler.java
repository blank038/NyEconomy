package com.mc9y.nyeconomy.interfaces.impl;

import com.mc9y.nyeconomy.interfaces.IDataSourceHandler;

import java.sql.Connection;

/**
 * @author Blank038
 * @since 2021-03-11
 */
public class CommonDataSourceHandler implements IDataSourceHandler {

    private final Connection SQL_CONNECTION;

    public CommonDataSourceHandler() {
        this.SQL_CONNECTION = null;
    }

    @Override
    public Connection getConnection() {
        return this.SQL_CONNECTION;
    }
}