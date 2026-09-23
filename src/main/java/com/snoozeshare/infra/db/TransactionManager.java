package com.snoozeshare.infra.db;

import java.sql.Connection;
import java.sql.SQLException;

public final class TransactionManager {

    private final Connection connection;

    public TransactionManager(Connection connection) {
        this.connection = connection;
    }

    public <T> T inTransaction(SqlWork<T> work) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            T result = work.apply(connection);
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }
}
