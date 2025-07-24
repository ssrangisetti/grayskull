package com.flipkart.grayskull.spimpl.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.grayskull.audit.AuditLogFlusher;
import com.flipkart.grayskull.audit.AsyncAuditLogProcessor;
import com.flipkart.grayskull.audit.AuditLogTailer;
import com.flipkart.grayskull.configuration.properties.AuditProperties;
import com.flipkart.grayskull.spi.AsyncAuditLogger;
import com.flipkart.grayskull.spi.repositories.AuditCheckpointRepository;
import com.flipkart.grayskull.spi.repositories.AuditEntryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnMissingBean(AsyncAuditLogger.class)
public class FileAsyncAuditConfiguration {

    @Bean
    public AsyncAuditLogger asyncAuditLogger(AuditProperties auditProperties, ObjectMapper objectMapper) {
        return new FileAsyncAuditLogger(auditProperties, new JacksonAuditEncoder<>(objectMapper));
    }

    @Bean
    public AsyncAuditLogProcessor asyncAuditLogProcessor(AuditProperties auditProperties, AuditLogTailer auditLogTailer) {
        return new AsyncAuditLogProcessor(auditProperties, auditLogTailer);
    }

    @Bean
    public AuditLogFlusher asyncAuditLogFlusher(AuditEntryRepository auditEntryRepository, AuditCheckpointRepository auditCheckpointRepository) {
        return new AuditLogFlusher(auditEntryRepository, auditCheckpointRepository);
    }

    @Bean
    public AuditLogTailer asyncAuditLogTailer(AuditLogFlusher flusher, ObjectMapper objectMapper, AuditProperties auditProperties, AuditCheckpointRepository auditCheckpointRepository) {
        return new AuditLogTailer(flusher, objectMapper, auditProperties, auditCheckpointRepository);
    }
}
