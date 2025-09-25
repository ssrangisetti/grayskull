package com.flipkart.grayskull.spimpl.audit;

import com.flipkart.grayskull.models.db.AuditEntry;
import com.flipkart.grayskull.spi.AsyncAuditLogger;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.*;

import static com.flipkart.grayskull.spimpl.audit.WalConstants.*;

/**
 * Wal code inspired from <a href="https://github.com/lant/wal">lant/wal</a>
 */
@Slf4j
public class WalAsyncAuditLogger implements AsyncAuditLogger {

    private final BlockingQueue<AuditOrTick> queue;
    private final int batchSize;
    private final AuditLogPersister auditLogPersister;
    private final WalLogger walLogger;
    private final MeterRegistry meterRegistry;
    private final ExecutorService executorService = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("audit-event-listener", 1).factory());
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("audit-ticker", 1).factory());

    public WalAsyncAuditLogger(int queueSize, int batchSize, AuditLogPersister auditLogPersister, WalLogger walLogger, MeterRegistry meterRegistry) {
        this.queue = new LinkedBlockingQueue<>(queueSize);
        this.batchSize = batchSize;
        this.auditLogPersister = auditLogPersister;
        this.walLogger = walLogger;
        this.meterRegistry = meterRegistry;
    }

    public void start(int flushIntervalSec) {
        executorService.submit(this::listenForEvent);
        scheduledExecutorService.scheduleAtFixedRate(this::sendTick, flushIntervalSec, flushIntervalSec, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void preDestroy() {
        executorService.shutdown();
        scheduledExecutorService.shutdown();
    }

    @Override
    public void log(AuditEntry auditEntry) {
        try {
            long entryNum = walLogger.write(auditEntry);
            queue.add(new AuditOrTick.Audit(auditEntry, entryNum));
        } catch (IOException e) {
            log.error("Failed to log audit entry", e);
            meterRegistry.counter(AUDIT_ERROR_METRIC, ACTION_TAG, "log", EXCEPTION_TAG, "IOException").increment();
        } catch (IllegalStateException e) {
            log.error("Failed to log audit entry because queue is full", e);
            meterRegistry.counter(AUDIT_ERROR_METRIC, ACTION_TAG, "log", EXCEPTION_TAG, "IllegalStateException").increment();
        }
    }

    public void sendTick() {
        queue.add(new AuditOrTick.Tick());
    }

    @SuppressWarnings({"java:S2189", "InfiniteLoopStatement"})
    public Void listenForEvent() throws InterruptedException {
        while (true) {
            AuditOrTick entry = queue.take();
            switch (entry) {
                case AuditOrTick.Audit(AuditEntry auditEntry, long counter) -> {
                    auditLogPersister.add(auditEntry, counter);
                    if (batchSize != 0 && counter % batchSize == 0) {
                        flush();
                    }
                }
                case AuditOrTick.Tick() -> flush();
            }
        }
    }

    private void flush() {
        try {
            auditLogPersister.flush();
        } catch (Exception e) {
            log.error("Failed to log audit entry", e);
        }
    }
}
