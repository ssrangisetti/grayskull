package com.flipkart.grayskull.spimpl.audit;

import com.flipkart.grayskull.models.db.AuditCheckpoint;
import com.flipkart.grayskull.spi.repositories.AuditCheckpointRepository;
import jakarta.annotation.PreDestroy;
import lombok.SneakyThrows;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Wal code inspired from <a href="https://github.com/lant/wal">lant/wal</a>
 */
public class WalCleaner {
    private final WalLogger walLogger;
    private final AuditCheckpointRepository auditCheckpointRepository;
    private final AuditProperties auditProperties;
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());

    public WalCleaner(WalLogger walLogger, AuditCheckpointRepository auditCheckpointRepository, AuditProperties auditProperties) {
        this.walLogger = walLogger;
        this.auditCheckpointRepository = auditCheckpointRepository;
        this.auditProperties = auditProperties;
        scheduledExecutorService.scheduleWithFixedDelay(this::clean, auditProperties.getRotateTimeSeconds(), auditProperties.getRotateTimeSeconds(), TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        scheduledExecutorService.shutdown();
    }

    @SneakyThrows
    public void clean() {
        AuditCheckpoint checkpoint = auditCheckpointRepository.findByNodeName(auditProperties.getNodeName()).orElse(new AuditCheckpoint(auditProperties.getNodeName()));
        walLogger.cleanOldFiles(checkpoint.getLines());
    }
}
