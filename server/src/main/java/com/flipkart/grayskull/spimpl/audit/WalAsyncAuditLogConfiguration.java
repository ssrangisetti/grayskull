package com.flipkart.grayskull.spimpl.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.grayskull.spi.AsyncAuditLogger;
import com.flipkart.grayskull.spi.repositories.AuditCheckpointRepository;
import com.flipkart.grayskull.spi.repositories.AuditEntryRepository;
import lombok.AllArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@ConditionalOnMissingBean(AsyncAuditLogger.class)
@AllArgsConstructor
public class WalAsyncAuditLogConfiguration {

    private final AuditProperties auditProperties;
    private final AuditCheckpointRepository auditCheckpointRepository;

    @Bean
    public AuditLogPersister auditLogPersister(AuditEntryRepository auditEntryRepository) {
        return new AuditLogPersister(auditEntryRepository, auditCheckpointRepository, auditProperties.getNodeName());
    }

    @Bean
    public WalLogger walLogger(ObjectMapper objectMapper) throws IOException {
        return new WalLogger(auditProperties.getAuditFolder(), auditProperties.getMaxAuditLines(), objectMapper);
    }

    @Bean
    public WalAsyncAuditLogger walAsyncAuditLogger(AuditLogPersister auditLogPersister, WalLogger walLogger) {
        return new WalAsyncAuditLogger(auditProperties.getAuditQueueSize(), auditProperties.getBatchSize(), auditLogPersister, walLogger);
    }

    @Bean
    public WalBacklogProcessor  walBacklogProcessor(WalLogger walLogger, AuditLogPersister auditLogPersister, WalAsyncAuditLogger walAsyncAuditLogger) {
        return new WalBacklogProcessor(auditCheckpointRepository, walLogger, auditLogPersister, walAsyncAuditLogger, auditProperties);
    }

    @Bean
    public WalCleaner walCleaner(WalLogger walLogger) {
        return new WalCleaner(walLogger, auditCheckpointRepository,  auditProperties);
    }
}
