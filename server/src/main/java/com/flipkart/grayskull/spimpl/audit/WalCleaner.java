package com.flipkart.grayskull.spimpl.audit;

import com.flipkart.grayskull.models.db.AuditCheckpoint;
import com.flipkart.grayskull.spi.repositories.AuditCheckpointRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.flipkart.grayskull.spimpl.audit.WalConstants.*;

/**
 * Wal code inspired from <a href="https://github.com/lant/wal">lant/wal</a>
 */
@Slf4j
public class WalCleaner {
    private final WalLogger walLogger;
    private final AuditCheckpointRepository auditCheckpointRepository;
    private final AuditProperties auditProperties;
    private final MeterRegistry meterRegistry;
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());

    public WalCleaner(WalLogger walLogger, AuditCheckpointRepository auditCheckpointRepository, AuditProperties auditProperties, MeterRegistry meterRegistry) {
        this.walLogger = walLogger;
        this.auditCheckpointRepository = auditCheckpointRepository;
        this.auditProperties = auditProperties;
        this.meterRegistry = meterRegistry;
        scheduledExecutorService.scheduleWithFixedDelay(this::clean, auditProperties.getRotateTimeSeconds(), auditProperties.getRotateTimeSeconds(), TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        scheduledExecutorService.shutdown();
    }

    public void clean() {
        try {
            AuditCheckpoint checkpoint = auditCheckpointRepository.findByNodeName(auditProperties.getNodeName()).orElse(new AuditCheckpoint(auditProperties.getNodeName()));
            walLogger.cleanOldFiles(checkpoint.getLines());
        } catch (Exception e) {
            log.error(e.getMessage());
            meterRegistry.counter(AUDIT_ERROR_METRIC, ACTION_TAG, CLEAN_ACTION, EXCEPTION_TAG, e.getClass().getSimpleName()).increment();
        }
    }
}
