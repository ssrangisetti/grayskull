package com.flipkart.grayskull.spimpl.audit;

import com.flipkart.grayskull.models.db.AuditCheckpoint;
import com.flipkart.grayskull.models.db.AuditEntry;
import com.flipkart.grayskull.spi.repositories.AuditCheckpointRepository;
import com.flipkart.grayskull.spi.repositories.AuditEntryRepository;
import io.micrometer.core.annotation.Counted;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class AuditLogPersister {

    private final AuditEntryRepository auditEntryRepository;
    private final AuditCheckpointRepository auditCheckpointRepository;
    private final String nodeName;
    private final List<AuditEntry> auditEntries = new ArrayList<>();
    private long lineNum;

    public void add(AuditEntry auditEntry, long counter) {
        auditEntries.add(auditEntry);
        this.lineNum = counter;
    }

    @Transactional
    @Counted(recordFailuresOnly = true)
    public void flush() {
        if (auditEntries.isEmpty()) {
            return;
        }
        auditEntryRepository.saveAll(auditEntries);
        AuditCheckpoint checkpoint = auditCheckpointRepository.findByNodeName(nodeName).orElse(new AuditCheckpoint(nodeName));
        checkpoint.setLines(lineNum);
        auditCheckpointRepository.save(checkpoint);
        auditEntries.clear();
    }
}
