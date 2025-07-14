package com.flipkart.grayskull.spimpl.audit;

import com.flipkart.grayskull.repositories.AuditEntryRepository;
import com.flipkart.grayskull.spi.AsyncAuditLogger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnMissingBean(AsyncAuditLogger.class)
public class SimpleAsyncAuditConfiguration {

    @Bean
    public AsyncAuditLogger asyncAuditLogger(AuditEntryRepository auditEntryRepository) {
        return new VirtualThreadAsyncAuditLogger(auditEntryRepository);
    }
}
