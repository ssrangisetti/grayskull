package com.flipkart.grayskull.spimpl.audit;

import com.flipkart.grayskull.models.db.AuditCheckpoint;
import com.flipkart.grayskull.spi.repositories.AuditCheckpointRepository;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wal code inspired from <a href="https://github.com/lant/wal">lant/wal</a>
 */
@AllArgsConstructor
public class WalBacklogProcessor {

    private AuditCheckpointRepository auditCheckpointRepository;
    private WalLogger walLogger;
    private AuditLogPersister auditLogPersister;
    private WalAsyncAuditLogger walAsyncAuditLogger;
    private AuditProperties auditProperties;

    @PostConstruct
    public void processAuditBacklog() throws IOException {
        long checkpoint = auditCheckpointRepository.findByNodeName(auditProperties.getNodeName()).map(AuditCheckpoint::getLines).orElse(0L);
        AtomicInteger i = new AtomicInteger(0);
        walLogger.backlogAudits(checkpoint).forEach(audit -> {
            auditLogPersister.add(audit.entry(), audit.counter());
            i.incrementAndGet();
            if (i.get() == auditProperties.getBatchSize()) {
                auditLogPersister.flush();
                i.set(0);
            }
        });
        auditLogPersister.flush();
        walAsyncAuditLogger.start(auditProperties.getBatchTimeSeconds());
    }
}
