package com.mc9y.nyeconomy.interfaces;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.handler.execute.ExecuteModel;

import java.sql.*;

/**
 * @author Blank038
 * @since 2021-03-11
 */
public abstract class AbstractDataSourceHandlerImpl implements IDataSourceHandler {
    public static boolean SQL_STATUS = false;

    private Connection connection;

    public void connect(ExecuteModel executeModel, String sql) {
        Connection activeConnection = null;
        PreparedStatement statement = null;
        boolean borrowedConnection = false;
        try {
            activeConnection = this.connection;
            if (activeConnection == null || activeConnection.isClosed()) {
                activeConnection = this.getConnection();
                borrowedConnection = true;
            }
            if (activeConnection == null) {
                throw new SQLException("无法取得 MySQL 连接");
            }
            statement = activeConnection.prepareStatement(sql);
            executeModel.run(activeConnection, statement);
            SQL_STATUS = true;
        } catch (SQLException e) {
            SQL_STATUS = false;
            Main.getInstance().getLogger().log(java.util.logging.Level.WARNING, "执行 MySQL 语句失败: " + sql, e);
        } finally {
            this.close(statement, null);
            if (borrowedConnection && activeConnection != null) {
                try {
                    activeConnection.close();
                } catch (SQLException e) {
                    Main.getInstance().getLogger().log(java.util.logging.Level.WARNING, "关闭 MySQL 连接失败", e);
                }
            }
        }
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Connection getConnection() {
        return null;
    }

    @Override
    public void close(Statement statement, ResultSet resultSet) {
        try {
            if (resultSet != null) {
                resultSet.close();
            }
            if (statement != null) {
                statement.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
