package com.flipkart.grayskull.spimpl.audit;

import com.flipkart.grayskull.models.db.AuditCheckpoint;
import com.flipkart.grayskull.models.db.AuditEntry;
import com.flipkart.grayskull.spi.repositories.AuditCheckpointRepository;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class WalBacklogProcessorTest {

    private final AuditCheckpointRepository auditCheckpointRepository = mock(AuditCheckpointRepository.class);

    private final WalLogger walLogger = mock(WalLogger.class);

    private final AuditLogPersister auditLogPersister = mock(AuditLogPersister.class);

    private final WalAsyncAuditLogger walAsyncAuditLogger = mock(WalAsyncAuditLogger.class);

    private final AuditProperties auditProperties = mock(AuditProperties.class);

    private final WalBacklogProcessor walBacklogProcessor = new WalBacklogProcessor(auditCheckpointRepository, walLogger, auditLogPersister, walAsyncAuditLogger, auditProperties);

    @Test
    void shouldProcessAuditBacklogWithNoExistingCheckpoint() throws IOException {
        // Given
        String nodeName = "test-node";
        int batchSize = 3;
        int batchTimeSeconds = 10;

        when(auditProperties.getNodeName()).thenReturn(nodeName);
        when(auditProperties.getBatchSize()).thenReturn(batchSize);
        when(auditProperties.getBatchTimeSeconds()).thenReturn(batchTimeSeconds);
        when(auditCheckpointRepository.findByNodeName(nodeName)).thenReturn(Optional.empty());

        AuditEntry auditEntry1 = createTestAuditEntry("project1", "secret1", 1);
        AuditEntry auditEntry2 = createTestAuditEntry("project2", "secret2", 2);
        AuditEntry auditEntry3 = createTestAuditEntry("project3", "secret3", 3);
        AuditEntry auditEntry4 = createTestAuditEntry("project4", "secret4", 4);

        Stream<AuditOrTick.Audit> auditStream = Stream.of(
                new AuditOrTick.Audit(auditEntry1, 1L),
                new AuditOrTick.Audit(auditEntry2, 2L),
                new AuditOrTick.Audit(auditEntry3, 3L),
                new AuditOrTick.Audit(auditEntry4, 4L)
        );

        when(walLogger.backlogAudits(0L)).thenReturn(auditStream);

        // When
        walBacklogProcessor.processAuditBacklog();

        // Then
        verify(auditCheckpointRepository).findByNodeName(nodeName);
        verify(walLogger).backlogAudits(0L);

        // Verify audit entries were added to persister
        verify(auditLogPersister).add(auditEntry1, 1L);
        verify(auditLogPersister).add(auditEntry2, 2L);
        verify(auditLogPersister).add(auditEntry3, 3L);
        verify(auditLogPersister).add(auditEntry4, 4L);

        // Verify flush was called after batch size (3) and at the end
        verify(auditLogPersister, times(2)).flush();
        verify(walAsyncAuditLogger).start(batchTimeSeconds);
    }

    @Test
    void shouldProcessAuditBacklogWithExistingCheckpoint() throws IOException {
        // Given
        String nodeName = "test-node";
        long existingCheckpoint = 5L;
        int batchSize = 2;
        int batchTimeSeconds = 15;

        when(auditProperties.getNodeName()).thenReturn(nodeName);
        when(auditProperties.getBatchSize()).thenReturn(batchSize);
        when(auditProperties.getBatchTimeSeconds()).thenReturn(batchTimeSeconds);

        AuditCheckpoint checkpoint = new AuditCheckpoint(nodeName);
        checkpoint.setLines(existingCheckpoint);
        when(auditCheckpointRepository.findByNodeName(nodeName)).thenReturn(Optional.of(checkpoint));

        AuditEntry auditEntry1 = createTestAuditEntry("project1", "secret1", 1);
        AuditEntry auditEntry2 = createTestAuditEntry("project2", "secret2", 2);

        Stream<AuditOrTick.Audit> auditStream = Stream.of(
                new AuditOrTick.Audit(auditEntry1, 6L),
                new AuditOrTick.Audit(auditEntry2, 7L)
        );

        when(walLogger.backlogAudits(existingCheckpoint)).thenReturn(auditStream);

        // When
        walBacklogProcessor.processAuditBacklog();

        // Then
        verify(auditCheckpointRepository).findByNodeName(nodeName);
        verify(walLogger).backlogAudits(existingCheckpoint);

        // Verify audit entries were added to persister
        verify(auditLogPersister).add(auditEntry1, 6L);
        verify(auditLogPersister).add(auditEntry2, 7L);

        // Verify flush was called after batch size (2) and at the end
        verify(auditLogPersister, times(2)).flush();
        verify(walAsyncAuditLogger).start(batchTimeSeconds);
    }

    @Test
    void shouldProcessAuditBacklogWithNoBacklogAudits() throws IOException {
        // Given
        String nodeName = "test-node";
        int batchTimeSeconds = 20;

        when(auditProperties.getNodeName()).thenReturn(nodeName);
        when(auditProperties.getBatchTimeSeconds()).thenReturn(batchTimeSeconds);
        when(auditCheckpointRepository.findByNodeName(nodeName)).thenReturn(Optional.empty());
        when(walLogger.backlogAudits(0L)).thenReturn(Stream.empty());

        // When
        walBacklogProcessor.processAuditBacklog();

        // Then
        verify(auditCheckpointRepository).findByNodeName(nodeName);
        verify(walLogger).backlogAudits(0L);
        verify(auditLogPersister, never()).add(any(), anyLong());
        verify(auditLogPersister, times(1)).flush();
        verify(walAsyncAuditLogger).start(batchTimeSeconds);
    }

    @Test
    void shouldProcessAuditBacklogWithExactBatchSize() throws IOException {
        // Given
        String nodeName = "test-node";
        int batchSize = 2;
        int batchTimeSeconds = 25;

        when(auditProperties.getNodeName()).thenReturn(nodeName);
        when(auditProperties.getBatchSize()).thenReturn(batchSize);
        when(auditProperties.getBatchTimeSeconds()).thenReturn(batchTimeSeconds);
        when(auditCheckpointRepository.findByNodeName(nodeName)).thenReturn(Optional.empty());

        AuditEntry auditEntry1 = createTestAuditEntry("project1", "secret1", 1);
        AuditEntry auditEntry2 = createTestAuditEntry("project2", "secret2", 2);

        Stream<AuditOrTick.Audit> auditStream = Stream.of(
                new AuditOrTick.Audit(auditEntry1, 1L),
                new AuditOrTick.Audit(auditEntry2, 2L)
        );

        when(walLogger.backlogAudits(0L)).thenReturn(auditStream);

        // When
        walBacklogProcessor.processAuditBacklog();

        // Then
        verify(auditCheckpointRepository).findByNodeName(nodeName);
        verify(walLogger).backlogAudits(0L);

        // Verify audit entries were added to persister
        verify(auditLogPersister).add(auditEntry1, 1L);
        verify(auditLogPersister).add(auditEntry2, 2L);

        // Verify flush was called after batch size (2) and at the end
        verify(auditLogPersister, times(2)).flush();
        verify(walAsyncAuditLogger).start(batchTimeSeconds);
    }

    @Test
    void shouldProcessAuditBacklogWithMultipleBatches() throws IOException {
        // Given
        String nodeName = "test-node";
        int batchSize = 2;
        int batchTimeSeconds = 30;

        when(auditProperties.getNodeName()).thenReturn(nodeName);
        when(auditProperties.getBatchSize()).thenReturn(batchSize);
        when(auditProperties.getBatchTimeSeconds()).thenReturn(batchTimeSeconds);
        when(auditCheckpointRepository.findByNodeName(nodeName)).thenReturn(Optional.empty());

        AuditEntry auditEntry1 = createTestAuditEntry("project1", "secret1", 1);
        AuditEntry auditEntry2 = createTestAuditEntry("project2", "secret2", 2);
        AuditEntry auditEntry3 = createTestAuditEntry("project3", "secret3", 3);
        AuditEntry auditEntry4 = createTestAuditEntry("project4", "secret4", 4);
        AuditEntry auditEntry5 = createTestAuditEntry("project5", "secret5", 5);

        Stream<AuditOrTick.Audit> auditStream = Stream.of(
                new AuditOrTick.Audit(auditEntry1, 1L),
                new AuditOrTick.Audit(auditEntry2, 2L),
                new AuditOrTick.Audit(auditEntry3, 3L),
                new AuditOrTick.Audit(auditEntry4, 4L),
                new AuditOrTick.Audit(auditEntry5, 5L)
        );

        when(walLogger.backlogAudits(0L)).thenReturn(auditStream);

        // When
        walBacklogProcessor.processAuditBacklog();

        // Then
        verify(auditCheckpointRepository).findByNodeName(nodeName);
        verify(walLogger).backlogAudits(0L);

        // Verify audit entries were added to persister
        verify(auditLogPersister).add(auditEntry1, 1L);
        verify(auditLogPersister).add(auditEntry2, 2L);
        verify(auditLogPersister).add(auditEntry3, 3L);
        verify(auditLogPersister).add(auditEntry4, 4L);
        verify(auditLogPersister).add(auditEntry5, 5L);

        // Verify flush was called after each batch (2 entries) and at the end
        // Batch 1: entries 1,2 -> flush
        // Batch 2: entries 3,4 -> flush  
        // Final: entry 5 -> flush
        verify(auditLogPersister, times(3)).flush();
        verify(walAsyncAuditLogger).start(batchTimeSeconds);
    }

    @Test
    void shouldHandleIOExceptionFromWalLogger() throws IOException {
        // Given
        String nodeName = "test-node";

        when(auditProperties.getNodeName()).thenReturn(nodeName);
        when(auditCheckpointRepository.findByNodeName(nodeName)).thenReturn(Optional.empty());
        when(walLogger.backlogAudits(0L)).thenThrow(new IOException("Test IO exception"));

        // When & Then
        try {
            walBacklogProcessor.processAuditBacklog();
        } catch (IOException e) {
            assertThat(e.getMessage()).isEqualTo("Test IO exception");
        }

        verify(auditCheckpointRepository).findByNodeName(nodeName);
        verify(walLogger).backlogAudits(0L);
        verify(auditLogPersister, never()).add(any(), anyLong());
        verify(auditLogPersister, never()).flush();
        verify(walAsyncAuditLogger, never()).start(anyInt());
    }

    @Test
    void shouldVerifyCorrectOrderOfOperations() throws IOException {
        // Given
        String nodeName = "test-node";
        int batchSize = 1;
        int batchTimeSeconds = 40;

        when(auditProperties.getNodeName()).thenReturn(nodeName);
        when(auditProperties.getBatchSize()).thenReturn(batchSize);
        when(auditProperties.getBatchTimeSeconds()).thenReturn(batchTimeSeconds);
        when(auditCheckpointRepository.findByNodeName(nodeName)).thenReturn(Optional.empty());

        AuditEntry auditEntry = createTestAuditEntry("project1", "secret1", 1);
        Stream<AuditOrTick.Audit> auditStream = Stream.of(
                new AuditOrTick.Audit(auditEntry, 1L)
        );

        when(walLogger.backlogAudits(0L)).thenReturn(auditStream);

        // When
        walBacklogProcessor.processAuditBacklog();

        // Then - verify the order of operations
        verify(auditCheckpointRepository).findByNodeName(nodeName);
        verify(walLogger).backlogAudits(0L);
        verify(auditLogPersister).add(auditEntry, 1L);
        // 2 times
        // 1 time to flush first entry
        // 2 time to flush remaining
        verify(auditLogPersister, times(2)).flush();
        verify(walAsyncAuditLogger).start(batchTimeSeconds);
    }

    private AuditEntry createTestAuditEntry(String projectId, String secretName, Integer secretVersion) {
        return new AuditEntry(
                projectId,
                secretName,
                secretVersion,
                "TEST_ACTION",
                "test-user",
                Map.of("key", "value")
        );
    }
} 