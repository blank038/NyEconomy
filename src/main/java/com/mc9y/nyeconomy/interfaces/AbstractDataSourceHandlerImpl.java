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
        PreparedStatement statement = null;
        try {
            if (Main.getInstance().isDebug()) {
                Main.getInstance().getLogger().info("MySQL 连接是否为空: " + (connection == null));
            }
            if (connection == null || connection.isClosed()) {
                if (Main.getInstance().isDebug()) {
                    Main.getInstance().getLogger().info("已重新从 MySQL 取得 Connection. ");
                }
                this.connection = this.getConnection();
            }
            statement = connection.prepareStatement(sql);
            executeModel.run(connection, statement);
        } catch (SQLException e) {
            SQL_STATUS = false;
            connection = this.getConnection();
            if (Main.getInstance().isDebug()) {
                e.printStackTrace();
            }
            this.connect(executeModel, sql);
        } finally {
            this.close(statement, null);
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
