package com.mc9y.nyeconomy.interfaces;

import com.mc9y.nyeconomy.handler.execute.ExecuteModel;

import java.sql.*;

/**
 * @author Blank038
 * @since 2021-03-11
 */
public abstract class AbstractDataSourceHandler implements IDataSourceHandler {
    public static boolean SQL_STATUS = false;

    private Connection connection;

    public void connect(ExecuteModel executeModel, String sql) {
        PreparedStatement statement = null;
        try {
            if (connection == null || connection.isClosed()) {
                connection = this.getConnection();
            }
            statement = connection.prepareStatement(sql);
            executeModel.run(connection, statement);
        } catch (SQLException e) {
            SQL_STATUS = false;
            e.printStackTrace();
        } finally {
            close(statement, null);
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
