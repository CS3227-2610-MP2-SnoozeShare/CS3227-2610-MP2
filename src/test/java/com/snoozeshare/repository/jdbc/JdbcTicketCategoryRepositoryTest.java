package com.snoozeshare.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snoozeshare.domain.model.TicketCategory;
import com.snoozeshare.testsupport.MockDbFixture;
import com.snoozeshare.testsupport.MockIds;

class JdbcTicketCategoryRepositoryTest {

    @Test
    void listsActiveCategoriesAlphabetically(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            List<String> labels = new JdbcTicketCategoryRepository(db.connection())
                    .findActive().stream().map(TicketCategory::label).toList();

            assertEquals(List.of("Cancellation Dispute", "Cleanliness", "Damage Dispute",
                    "Host Unresponsive", "Other", "Property Mismatch"), labels);
        }
    }

    @Test
    void deactivatedCategoriesLeaveTheActiveListButStayInFindAll(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            JdbcTicketCategoryRepository repository = new JdbcTicketCategoryRepository(db.connection());
            TicketCategory cleanliness = repository.findById(MockIds.CATEGORY_CLEANLINESS).orElseThrow();

            repository.save(new TicketCategory(cleanliness.categoryId(), cleanliness.label(), false));

            assertEquals(5, repository.findActive().size());
            assertEquals(6, repository.findAll().size());
            assertFalse(repository.findById(MockIds.CATEGORY_CLEANLINESS).orElseThrow().active());
        }
    }

    @Test
    void savesNewAndRenamedCategories(@TempDir Path directory) throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            JdbcTicketCategoryRepository repository = new JdbcTicketCategoryRepository(db.connection());
            UUID noiseId = UUID.randomUUID();

            repository.save(new TicketCategory(noiseId, "Noise", true));
            repository.save(new TicketCategory(noiseId, "Noise complaint", true));

            assertEquals("Noise complaint", repository.findById(noiseId).orElseThrow().label());
            assertEquals(7, repository.findAll().size());
        }
    }

    @Test
    void labelInUseIsCaseInsensitiveAndCanExcludeTheCategoryBeingRenamed(@TempDir Path directory)
            throws Exception {
        try (MockDbFixture db = MockDbFixture.open(directory)) {
            JdbcTicketCategoryRepository repository = new JdbcTicketCategoryRepository(db.connection());

            assertTrue(repository.labelInUse("cleanliness", null));
            assertFalse(repository.labelInUse("cleanliness", MockIds.CATEGORY_CLEANLINESS));
            assertFalse(repository.labelInUse("Noise", null));
        }
    }
}
