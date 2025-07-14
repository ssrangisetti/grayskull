package com.flipkart.grayskull.spimpl.audit;

import com.flipkart.grayskull.models.db.AuditEntry;
import com.flipkart.grayskull.repositories.AuditEntryRepository;
import com.flipkart.grayskull.spi.AsyncAuditLogger;
import lombok.AllArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A simple implementation of {@link AsyncAuditLogger} which just saves audit entries in DB asynchronously.
 */
@AllArgsConstructor
public class VirtualThreadAsyncAuditLogger implements AsyncAuditLogger {

    private final AuditEntryRepository auditEntryRepository;
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public void log(String projectId, String secret, Integer secretVersion, String action, String status, Map<String, String> metadata) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        executorService.submit(() ->
                auditEntryRepository.save(new AuditEntry(projectId, secret, secretVersion, action, status, userId, metadata))
        );
    }
}
