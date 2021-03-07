package com.mc9y.nyeconomy.handler.execute;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * @author Blank038
 */
@FunctionalInterface
public interface ExecuteModel {

    /**
     * 连接数据库执行
     *
     * @param connection 数据库连接
     * @param statement  命令提交
     */
    void run(Connection connection, PreparedStatement statement);
}