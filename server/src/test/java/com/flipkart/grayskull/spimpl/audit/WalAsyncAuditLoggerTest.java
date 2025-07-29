package com.flipkart.grayskull.spimpl.audit;

import com.flipkart.grayskull.models.db.AuditEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class WalAsyncAuditLoggerTest {

    private final AuditLogPersister auditLogPersister = mock(AuditLogPersister.class);

    private final WalLogger walLogger = mock(WalLogger.class);

    private WalAsyncAuditLogger walAsyncAuditLogger;

    @BeforeEach
    void setUp() {
        walAsyncAuditLogger = new WalAsyncAuditLogger(100, 3, auditLogPersister, walLogger);
    }

    @AfterEach
    void tearDown() {
        walAsyncAuditLogger.preDestroy();
    }

    @Test
    void shouldLogAuditEntrySuccessfully() throws IOException {
        // Given
        AuditEntry auditEntry = createTestAuditEntry("project1", "secret1", 1);
        when(walLogger.write(auditEntry)).thenReturn(1L);

        // When
        walAsyncAuditLogger.log(auditEntry);

        // Then
        verify(walLogger).write(auditEntry);
        // Note: We can't easily verify the queue content due to private access
        // but we can verify the walLogger.write was called
    }

    @Test
    void shouldHandleIOExceptionDuringLogging() throws IOException {
        // Given
        AuditEntry auditEntry = createTestAuditEntry("project1", "secret1", 1);
        when(walLogger.write(auditEntry)).thenThrow(new IOException("Test IO exception"));

        // When
        walAsyncAuditLogger.log(auditEntry);

        // Then
        verify(walLogger).write(auditEntry);
        // The exception should be logged but not re-thrown
        // We can't easily verify logging without a test logger, but the method should complete normally
    }

    @Test
    void shouldProcessAuditEntryAndFlushOnBatchSize() throws IOException {
        // Given
        AuditEntry auditEntry1 = createTestAuditEntry("project1", "secret1", 1);
        AuditEntry auditEntry2 = createTestAuditEntry("project2", "secret2", 2);
        AuditEntry auditEntry3 = createTestAuditEntry("project3", "secret3", 3);

        when(walLogger.write(any(AuditEntry.class)))
                .thenReturn(1L)
                .thenReturn(2L)
                .thenReturn(3L);

        // Start the async logger
        walAsyncAuditLogger.start(10);

        // When
        walAsyncAuditLogger.log(auditEntry1);
        walAsyncAuditLogger.log(auditEntry2);
        walAsyncAuditLogger.log(auditEntry3);

        // Then
        verify(auditLogPersister, timeout(1000)).add(auditEntry1, 1L);
        verify(auditLogPersister, timeout(1000)).add(auditEntry2, 2L);
        verify(auditLogPersister, timeout(1000)).add(auditEntry3, 3L);
        // Should flush when counter (3) % batchSize (3) == 0
        verify(auditLogPersister, timeout(1000)).flush();
    }

    @Test
    void shouldProcessAuditEntryWithoutFlushWhenNotBatchSize() throws IOException {
        // Given
        AuditEntry auditEntry1 = createTestAuditEntry("project1", "secret1", 1);
        AuditEntry auditEntry2 = createTestAuditEntry("project2", "secret2", 2);

        when(walLogger.write(any(AuditEntry.class)))
                .thenReturn(1L)
                .thenReturn(2L);

        // Start the async logger
        walAsyncAuditLogger.start(10);

        // When
        walAsyncAuditLogger.log(auditEntry1);
        walAsyncAuditLogger.log(auditEntry2);

        // Then
        verify(auditLogPersister, timeout(1000)).add(auditEntry1, 1L);
        verify(auditLogPersister, timeout(1000)).add(auditEntry2, 2L);
        // Should not flush since counter (2) % batchSize (3) != 0
        verify(auditLogPersister, never()).flush();
    }

    @Test
    void shouldProcessTickAndFlush() {
        // Given
        // Start the async logger
        walAsyncAuditLogger.start(1);

        // Then
        verify(auditLogPersister, timeout(1100)).flush();
    }

    @Test
    void shouldHandleMultipleTicks() {
        // Given
        // Start the async logger
        walAsyncAuditLogger.start(10);

        // When
        walAsyncAuditLogger.sendTick();
        walAsyncAuditLogger.sendTick();
        walAsyncAuditLogger.sendTick();

        // Then
        verify(auditLogPersister, timeout(100).times(3)).flush();
    }

    @Test
    void shouldProcessMixedAuditAndTickEvents() throws IOException {
        // Given
        AuditEntry auditEntry1 = createTestAuditEntry("project1", "secret1", 1);
        AuditEntry auditEntry2 = createTestAuditEntry("project2", "secret2", 2);

        when(walLogger.write(any(AuditEntry.class)))
                .thenReturn(1L)
                .thenReturn(2L);

        // Start the async logger
        walAsyncAuditLogger.start(1);

        // When
        walAsyncAuditLogger.log(auditEntry1);
        walAsyncAuditLogger.sendTick();
        walAsyncAuditLogger.log(auditEntry2);
        walAsyncAuditLogger.sendTick();

        // Then
        verify(auditLogPersister, timeout(1000)).add(auditEntry1, 1L);
        verify(auditLogPersister, timeout(1000)).add(auditEntry2, 2L);
        // Should flush for each tick (2 times)
        verify(auditLogPersister, timeout(1000).times(2)).flush();
    }

    @Test
    void shouldHandleLargeNumberOfAuditEntries() throws IOException {
        // Given
        int numberOfEntries = 10;
        AtomicLong num = new AtomicLong(0);
        when(walLogger.write(any(AuditEntry.class))).thenAnswer(invocation -> num.getAndIncrement());

        // Start the async logger
        walAsyncAuditLogger.start(10);

        // When
        for (int i = 0; i < numberOfEntries; i++) {
            AuditEntry auditEntry = createTestAuditEntry("project" + i, "secret" + i, i);
            walAsyncAuditLogger.log(auditEntry);
        }

        // Then
        verify(auditLogPersister, timeout(2000).times(numberOfEntries)).add(any(AuditEntry.class), anyLong());
        // Should flush when counter % batchSize == 0 (every 3rd entry)
        verify(auditLogPersister, timeout(2000).atLeast(3)).flush();
    }

    @Test
    void shouldHandleConcurrentLogging() throws InterruptedException, IOException {
        // Given
        int numberOfThreads = 5;
        int entriesPerThread = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numberOfThreads);

        AtomicLong num = new AtomicLong(0);

        when(walLogger.write(any(AuditEntry.class))).thenAnswer(invocation -> num.getAndIncrement());

        // Start the async logger
        walAsyncAuditLogger.start(1);

        // When
        for (int t = 0; t < numberOfThreads; t++) {
            final int threadId = t;
            new Thread(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < entriesPerThread; i++) {
                        AuditEntry auditEntry = createTestAuditEntry(
                                "project" + threadId,
                                "secret" + threadId + "_" + i,
                                i
                        );
                        walAsyncAuditLogger.log(auditEntry);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        endLatch.await(5, TimeUnit.SECONDS);

        // Then
        verify(auditLogPersister, timeout(2000).times(numberOfThreads * entriesPerThread))
                .add(any(AuditEntry.class), anyLong());
    }

    @Test
    void shouldHandleQueueFullScenario() throws IOException {
        // Given - Create logger with very small queue size
        WalAsyncAuditLogger smallQueueLogger = new WalAsyncAuditLogger(1, 3, auditLogPersister, walLogger);
        // not starting the listener as it will keep consuming the events constantly

        when(walLogger.write(any(AuditEntry.class))).thenReturn(1L);

        // When - Add more entries than queue can hold
        AuditEntry auditEntry1 = createTestAuditEntry("project1", "secret1", 1);
        AuditEntry auditEntry2 = createTestAuditEntry("project2", "secret2", 2);

        smallQueueLogger.log(auditEntry1);
        assertThatThrownBy(() -> smallQueueLogger.log(auditEntry2)).isInstanceOf(IllegalStateException.class);

        // Cleanup
        smallQueueLogger.preDestroy();
    }

    @Test
    void shouldProcessAuditWithDifferentBatchSizes() throws IOException {
        // Given - Create logger with different batch size
        WalAsyncAuditLogger customBatchLogger = new WalAsyncAuditLogger(100, 5, auditLogPersister, walLogger);

        AtomicLong num = new AtomicLong(0);
        when(walLogger.write(any(AuditEntry.class))).thenAnswer(invocation -> num.getAndIncrement());

        // Start the async logger
        customBatchLogger.start(1);

        // When - Add entries
        for (int i = 0; i < 10; i++) {
            AuditEntry auditEntry = createTestAuditEntry("project" + i, "secret" + i, i);
            customBatchLogger.log(auditEntry);
        }

        // Then
        verify(auditLogPersister, timeout(1000).times(10)).add(any(AuditEntry.class), anyLong());
        // Should flush when counter % batchSize (5) == 0
        verify(auditLogPersister, timeout(1000).atLeast(2)).flush();

        // Cleanup
        customBatchLogger.preDestroy();
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