package com.mc9y.nyeconomy.interfaces;

import java.sql.Connection;

/**
 * @author Blank038
 * @since 2021-03-11
 */
public interface IDataSourceHandler {

    /**
     * 获取 MySQL 连接
     *
     * @return MySQL 连接
     */
    Connection getConnection();
}
