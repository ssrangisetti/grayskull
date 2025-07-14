package com.flipkart.grayskull.spimpl.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.grayskull.audit.AuditLogFlusher;
import com.flipkart.grayskull.audit.AsyncAuditLogProcessor;
import com.flipkart.grayskull.configuration.properties.AuditProperties;
import com.flipkart.grayskull.repositories.AuditCheckpointRepository;
import com.flipkart.grayskull.repositories.AuditEntryRepository;
import com.flipkart.grayskull.spi.AsyncAuditLogger;
import io.micrometer.core.instrument.MeterRegistry;
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
    public AsyncAuditLogProcessor asyncAuditLogProcessor(AuditLogFlusher flusher,
                                                         AuditCheckpointRepository auditCheckpointRepository,
                                                         AuditProperties auditProperties,
                                                         ObjectMapper objectMapper,
                                                         MeterRegistry meterRegistry) {
        return new AsyncAuditLogProcessor(auditCheckpointRepository, auditProperties, flusher, objectMapper, meterRegistry);
    }

    @Bean
    public AuditLogFlusher asyncAuditLogFlusher(AuditEntryRepository auditEntryRepository, AuditCheckpointRepository auditCheckpointRepository) {
        return new AuditLogFlusher(auditEntryRepository, auditCheckpointRepository);
    }
}
