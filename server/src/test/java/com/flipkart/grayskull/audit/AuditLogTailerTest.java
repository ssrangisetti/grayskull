package com.flipkart.grayskull.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flipkart.grayskull.configuration.properties.AuditProperties;
import com.flipkart.grayskull.models.db.AuditEntry;
import com.flipkart.grayskull.models.db.Checkpoint;
import com.flipkart.grayskull.repositories.AuditCheckpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

class AuditLogTailerTest {

    @TempDir
    Path tempDir;

    private AuditLogTailer auditLogTailer;
    private AuditLogFlusher flusher;
    private AuditCheckpointRepository auditCheckpointRepository;
    private AuditProperties auditProperties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        // Create a temporary audit file
        File auditFile = tempDir.resolve("audit.log").toFile();
        Files.createFile(auditFile.toPath());

        // Mock dependencies
        flusher = mock(AuditLogFlusher.class);
        auditCheckpointRepository = mock(AuditCheckpointRepository.class);
        objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

        // Create AuditProperties
        auditProperties = new AuditProperties();
        auditProperties.setFilePath(auditFile.getAbsolutePath());
        auditProperties.setNodeName("test-node");
        auditProperties.setBatchSize(3);
        auditProperties.setBatchTimeSeconds(2);

        // Create processor
        auditLogTailer = new AuditLogTailer(
                flusher,
                objectMapper,
                auditProperties,
                auditCheckpointRepository
        );

    }

    @Test
    void testHandle_WithValidAuditEntry_AddsToBuffer() throws Exception {
        // Arrange
        AuditEntry auditEntry = createSampleAuditEntry();
        String auditLine = objectMapper.writeValueAsString(auditEntry);

        // Act
        auditLogTailer.handle(auditLine);

        // Assert
        List<AuditEntry> buffer = (List<AuditEntry>) ReflectionTestUtils.getField(auditLogTailer, "buffer");
        assertEquals(1, buffer.size());
        assertEquals(auditEntry.getAction(), buffer.get(0).getAction());
    }

    @Test
    void testHandle_WithCheckpointNotReached_DoesNotProcessLine() throws Exception {
        // Arrange
        AuditEntry auditEntry = createSampleAuditEntry();
        String auditLine = objectMapper.writeValueAsString(auditEntry);

        // Mock checkpoint that hasn't been reached yet
        Checkpoint checkpoint = new Checkpoint("test-node");
        checkpoint.setLines(5L); // Current line should be > 5 to process
        ReflectionTestUtils.setField(auditLogTailer, "checkpoint", checkpoint);

        // Act
        auditLogTailer.handle(auditLine);

        // Assert
        List<AuditEntry> buffer = (List<AuditEntry>) ReflectionTestUtils.getField(auditLogTailer, "buffer");
        assertEquals(0, buffer.size());
    }

    @Test
    void testHandle_WithBatchSizeReached_TriggersFlush() throws Exception {
        // Arrange
        AuditEntry auditEntry1 = createSampleAuditEntry("SECRET_READ");
        AuditEntry auditEntry2 = createSampleAuditEntry("SECRET_CREATE");
        AuditEntry auditEntry3 = createSampleAuditEntry("SECRET_UPDATE");

        String line1 = objectMapper.writeValueAsString(auditEntry1);
        String line2 = objectMapper.writeValueAsString(auditEntry2);
        String line3 = objectMapper.writeValueAsString(auditEntry3);

        // Mock checkpoint to allow processing
        Checkpoint checkpoint = new Checkpoint("test-node");
        checkpoint.setLines(0L);

        // Mock successful save
        when(flusher.flush(anyList(), any())).thenReturn(checkpoint);

        // Act
        auditLogTailer.handle(line1);
        auditLogTailer.handle(line2);
        auditLogTailer.handle(line3); // This should trigger flush

        // Assert
        verify(flusher).flush(any(), any());
    }

    @Test
    void testFlush_WithEmptyBuffer_DoesNothing() {
        // Act
        auditLogTailer.flush();

        // Assert
        verify(flusher, never()).flush(any(), any());
        verify(auditCheckpointRepository, never()).save(any(Checkpoint.class));
    }

    @Test
    void testFlush_WithValidBuffer_SavesToDatabase() {
        // Arrange
        AuditEntry auditEntry1 = createSampleAuditEntry("SECRET_READ");
        AuditEntry auditEntry2 = createSampleAuditEntry("SECRET_CREATE");

        List<AuditEntry> buffer = (List<AuditEntry>) ReflectionTestUtils.getField(auditLogTailer, "buffer");
        buffer.add(auditEntry1);
        buffer.add(auditEntry2);

        when(flusher.flush(anyList(), any())).thenReturn(new Checkpoint("test-node"));

        // Act
        auditLogTailer.flush();

        // Assert
        verify(flusher).flush(anyList(), any());
        assertEquals(0, buffer.size()); // Buffer should be cleared
    }

    @Test
    void testFileRotated_ResetsLineCounter() {
        // Arrange
        AtomicLong lines = (AtomicLong) ReflectionTestUtils.getField(auditLogTailer, "lines");
        lines.set(100L);

        // Act
        auditLogTailer.fileRotated();

        // Assert
        assertEquals(0L, lines.get());
    }

    @Test
    void testConstructor_WithNewCheckpoint_CreatesDefaultCheckpoint() {
        // Arrange
        when(auditCheckpointRepository.getCheckpointByName("test-node"))
                .thenReturn(Optional.empty());

        // Act
        AuditLogTailer newTailer = new AuditLogTailer(
                flusher,
                objectMapper,
                auditProperties,
                auditCheckpointRepository
        );

        // Assert
        Checkpoint checkpoint = (Checkpoint) ReflectionTestUtils.getField(newTailer, "checkpoint");
        assertEquals("test-node", checkpoint.getName());
        assertEquals(0L, checkpoint.getLines());
    }

    @Test
    void testConstructor_WithExistingCheckpoint_UsesExistingCheckpoint() {
        // Arrange
        Checkpoint existingCheckpoint = new Checkpoint("test-node");
        existingCheckpoint.setLines(50L);
        when(auditCheckpointRepository.getCheckpointByName("test-node"))
                .thenReturn(Optional.of(existingCheckpoint));

        // Act
        AuditLogTailer newTailer = new AuditLogTailer(
                flusher,
                objectMapper,
                auditProperties,
                auditCheckpointRepository
        );

        // Assert
        Checkpoint checkpoint = (Checkpoint) ReflectionTestUtils.getField(newTailer, "checkpoint");
        assertEquals("test-node", checkpoint.getName());
        assertEquals(50L, checkpoint.getLines());
    }

    private AuditEntry createSampleAuditEntry() {
        return createSampleAuditEntry("SECRET_READ");
    }

    private AuditEntry createSampleAuditEntry(String action) {
        return new AuditEntry(
                "test-id",
                "test-node-id",
                "test-secret",
                1,
                action,
                "SUCCESS",
                "test-user",
                Instant.now(),
                new HashMap<>()
        );
    }
}