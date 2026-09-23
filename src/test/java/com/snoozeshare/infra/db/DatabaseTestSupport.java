package com.snoozeshare.infra.db;

import java.sql.Connection;
import java.sql.SQLException;

public final class DatabaseTestSupport {

    private DatabaseTestSupport() {
    }

    public static Connection openIsolatedDatabase() throws SQLException {
        return ConnectionFactory.open("jdbc:sqlite::memory:");
    }
}
