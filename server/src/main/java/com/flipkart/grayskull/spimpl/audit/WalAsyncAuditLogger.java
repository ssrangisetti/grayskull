package com.flipkart.grayskull.spimpl.audit;

import com.flipkart.grayskull.models.db.AuditEntry;
import com.flipkart.grayskull.spi.AsyncAuditLogger;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.*;

/**
 * Wal code inspired from <a href="https://github.com/lant/wal">lant/wal</a>
 */
@Slf4j
public class WalAsyncAuditLogger implements AsyncAuditLogger {

    private final BlockingQueue<AuditOrTick> queue;
    private final int batchSize;
    private final AuditLogPersister auditLogPersister;
    private final WalLogger walLogger;
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());

    public WalAsyncAuditLogger(int queueSize, int batchSize, AuditLogPersister auditLogPersister, WalLogger walLogger) {
        this.queue = new LinkedBlockingQueue<>(queueSize);
        this.batchSize = batchSize;
        this.auditLogPersister = auditLogPersister;
        this.walLogger = walLogger;
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
                    if (counter % batchSize == 0) {
                        auditLogPersister.flush();
                    }
                }
                case AuditOrTick.Tick() -> auditLogPersister.flush();
            }
        }
    }
}
