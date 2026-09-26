package com.snoozeshare.testsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import com.snoozeshare.infra.db.ConnectionFactory;

/**
 * A throw-away copy of the committed mock DB. The copy exists only so mutating tests never write to
 * the committed file; the data is used exactly as the team sees it.
 */
public final class MockDbFixture implements AutoCloseable {

    public static final Path COMMITTED_DB = Path.of("db/snoozeshare-mock.db");

    private final Path copy;
    private final Connection connection;

    private MockDbFixture(Path copy) throws SQLException {
        this.copy = copy;
        this.connection = ConnectionFactory.open(urlFor(copy));
    }

    public static MockDbFixture open(Path directory) throws IOException, SQLException {
        Path copy = directory.resolve("mock-copy.db");
        Files.copy(COMMITTED_DB, copy);
        return new MockDbFixture(copy);
    }

    private static String urlFor(Path path) {
        return "jdbc:sqlite:" + path.toAbsolutePath();
    }

    public Connection connection() {
        return connection;
    }

    public String jdbcUrl() {
        return urlFor(copy);
    }

    public long scalarLong(String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    public String scalarString(String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    public void execute(String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            statement.executeUpdate();
        }
    }

    public BigDecimal walletBalance(UUID walletId) throws SQLException {
        return new BigDecimal(scalarString(
                "SELECT balance FROM wallets WHERE walletId = ?", walletId));
    }

    private static void bind(PreparedStatement statement, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object value = params[i] instanceof UUID ? params[i].toString() : params[i];
            statement.setObject(i + 1, value);
        }
    }

    /**
     * Every wallet balance equals the sum of its transactions, and every row's balanceAfter equals the
     * chronological running sum.
     */
    public void assertLedgerInvariant() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery(
                    "SELECT w.walletId AS walletId, w.balance AS balance, "
                            + "COALESCE(SUM(t.amount), 0) AS total FROM wallets w "
                            + "LEFT JOIN wallet_transactions t ON t.walletId = w.walletId "
                            + "GROUP BY w.walletId")) {
                while (result.next()) {
                    assertEquals(result.getDouble("total"), result.getDouble("balance"), 0.005,
                            "balance != sum(transactions) for wallet " + result.getString("walletId"));
                }
            }
            try (ResultSet result = statement.executeQuery(
                    "SELECT walletId, transactionId, amount, balanceAfter FROM wallet_transactions "
                            + "ORDER BY walletId, createdAt, transactionId")) {
                String currentWallet = null;
                double running = 0;
                while (result.next()) {
                    if (!result.getString("walletId").equals(currentWallet)) {
                        currentWallet = result.getString("walletId");
                        running = 0;
                    }
                    running += result.getDouble("amount");
                    assertEquals(running, result.getDouble("balanceAfter"), 0.005,
                            "balanceAfter chain broken at " + result.getString("transactionId"));
                }
            }
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
