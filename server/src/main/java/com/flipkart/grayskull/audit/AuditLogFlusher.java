package com.flipkart.grayskull.audit;

import com.flipkart.grayskull.models.db.AuditEntry;
import com.flipkart.grayskull.models.db.Checkpoint;
import com.flipkart.grayskull.spi.repositories.AuditCheckpointRepository;
import com.flipkart.grayskull.spi.repositories.AuditEntryRepository;
import io.micrometer.core.annotation.Counted;
import lombok.AllArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@AllArgsConstructor
public class AuditLogFlusher {
    private final AuditEntryRepository auditEntryRepository;
    private final AuditCheckpointRepository auditCheckpointRepository;

    @Transactional
    @Counted(recordFailuresOnly = true)
    public Checkpoint flush(List<AuditEntry> buffer, Checkpoint checkpoint) {
        auditEntryRepository.saveAll(buffer);
        return auditCheckpointRepository.save(checkpoint);
    }
}
