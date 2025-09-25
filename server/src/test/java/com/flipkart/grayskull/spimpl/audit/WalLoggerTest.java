package com.flipkart.grayskull.spimpl.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flipkart.grayskull.models.db.AuditEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class WalLoggerTest {
    private final ObjectMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
    private Path auditFolder;

    private WalLogger walLogger;

    @BeforeEach
    void setUp() throws IOException {
        // Create audit folder in temp directory
        auditFolder = Files.createTempDirectory("audit-test");

        walLogger = new WalLogger(auditFolder.toString(), 10, objectMapper);
    }

    @AfterEach
    void tearDown() {
        auditFolder.toFile().delete();
    }

    @Test
    void shouldWriteAuditEntry() throws IOException {
        // Given
        AuditEntry auditEntry = createTestAuditEntry();

        // When
        long entryId = walLogger.write(auditEntry);

        // Then
        assertThat(entryId).isEqualTo(1);
    }

    @Test
    void shouldRotateFilesWhenMaxLinesReached() throws IOException {
        // Given - maxLines is 10
        List<AuditEntry> entries = createMultipleAuditEntries(11);

        // When
        for (AuditEntry entry : entries) {
            walLogger.write(entry);
        }

        // Then - should have created 2 files (first file with 10 entries, second with 5)
        File[] auditFiles = auditFolder.toFile().listFiles();

        assertThat(auditFiles).hasSize(2);
    }

    @ParameterizedTest
    @CsvSource({"12, 8, 9, 12", "10, 0, 1, 10", "10, 10, 0, -1", "11, 10, 11, 11"})
    void shouldReadBacklogAudits(int existing, int committed, long expectedRangeStart, long expectedRangeEnd) throws IOException {
        // Given - maxLines is 10
        List<AuditEntry> entries = createMultipleAuditEntries(existing);

        // When
        for (AuditEntry entry : entries) {
            walLogger.write(entry);
        }

        // When
        List<AuditOrTick.Audit> backlog = walLogger.backlogAudits(committed);

        // Then
        assertThat(backlog.stream().map(AuditOrTick.Audit::counter).toList()).isEqualTo(LongStream.rangeClosed(expectedRangeStart, expectedRangeEnd).boxed().toList());
    }

    @ParameterizedTest
    @CsvSource({"12, 11, 1", "10, 10, 1", "10, 9, 2"})
    // - maxLines is 10
    void shouldCleanOldFiles(int total, int committed, int expectedRemaining) throws IOException {
        // Given
        List<AuditEntry> entries = createMultipleAuditEntries(total);

        // When
        for (AuditEntry entry : entries) {
            walLogger.write(entry);
        }

        // When
        walLogger.cleanOldFiles(committed);

        // Then - files with entries < 2 should be deleted
        File[] remainingFiles = auditFolder.toFile().listFiles();

        // Should only have the current file
        assertThat(remainingFiles).hasSize(expectedRemaining);
    }

    @Test
    void cleanOldFilesShouldFailWhenCleaningMoreThanCommitted() throws IOException {
        // Given
        List<AuditEntry> entries = createMultipleAuditEntries(10);

        // When
        for (AuditEntry entry : entries) {
            walLogger.write(entry);
        }

        // When
        assertThatThrownBy(() -> walLogger.cleanOldFiles(12)).hasMessage("deleting more than latest existing entry: 10");

        // Then - files with entries < 2 should be deleted
        File[] remainingFiles = auditFolder.toFile().listFiles();

        // Should only have the current file
        assertThat(remainingFiles).hasSize(2);
    }

    @ParameterizedTest
    @CsvSource({"0", "10", "11", "12", "25", "39", "40"})
    void createLoggerShouldGetProperEventNum(int existing) throws IOException {
        // Given
        List<AuditEntry> entries = createMultipleAuditEntries(existing);

        // When
        for (AuditEntry entry : entries) {
            walLogger.write(entry);
        }

        long newId = new WalLogger(auditFolder.toString(), 10, objectMapper).write(createTestAuditEntry());
        assertThat(newId).isEqualTo(existing + 1);

    }

    private AuditEntry createTestAuditEntry() {
        return new AuditEntry("test-project", "test-secret", 1, "TEST_ACTION", "test-user", Map.of("key", "val"));

    }

    private List<AuditEntry> createMultipleAuditEntries(int count) {
        List<AuditEntry> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            AuditEntry testAuditEntry = createTestAuditEntry();
            list.add(testAuditEntry);
        }
        return list;
    }
}