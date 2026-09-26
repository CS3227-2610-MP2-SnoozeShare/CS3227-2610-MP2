package com.snoozeshare.infra.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.testsupport.MockDbFixture;

class MigrationRunnerReferenceDbTest {

    @Test
    void adoptsAPreProvisionedReferenceDatabaseWithoutReapplyingTheFoundation(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            MigrationRunner.migrate(db.connection());
            MigrationRunner.migrate(db.connection());

            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM schema_history WHERE version = 1"));
            assertEquals(16L, db.scalarLong("SELECT COUNT(*) FROM users"));
        }
    }
}
