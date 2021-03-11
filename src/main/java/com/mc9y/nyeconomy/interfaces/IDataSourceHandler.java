package com.mc9y.nyeconomy.interfaces;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

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

    /**
     * 关闭SQL执行器和返回集合
     *
     * @param statement SQL执行器
     * @param resultSet 返回集合
     */
    void close(Statement statement, ResultSet resultSet);
}
