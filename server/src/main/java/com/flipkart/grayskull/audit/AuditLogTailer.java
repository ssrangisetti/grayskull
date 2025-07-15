package com.flipkart.grayskull.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.grayskull.configuration.properties.AuditProperties;
import com.flipkart.grayskull.models.db.AuditEntry;
import com.flipkart.grayskull.models.db.Checkpoint;
import com.flipkart.grayskull.repositories.AuditCheckpointRepository;
import com.flipkart.grayskull.utils.CloseableReentrantLock;
import io.micrometer.core.annotation.Counted;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.TailerListenerAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class AuditLogTailer extends TailerListenerAdapter {
    private final CloseableReentrantLock lock = new CloseableReentrantLock();

    private final List<AuditEntry> buffer = new ArrayList<>(); // active buffer need not be concurrent because we are handling everything with locks
    private final int maxBatchSize; // max batch size after which gets flushed

    private Checkpoint checkpoint;
    private boolean checkpointReached = false;
    private final AtomicLong lines = new AtomicLong(0);

    private final AuditLogFlusher flusher;
    private final ObjectMapper objectMapper;

    public AuditLogTailer(AuditLogFlusher flusher, ObjectMapper objectMapper, AuditProperties auditProperties, AuditCheckpointRepository auditCheckpointRepository) {
        this.flusher = flusher;
        this.objectMapper = objectMapper;
        this.maxBatchSize = auditProperties.getBatchSize();
        this.checkpoint = auditCheckpointRepository.getCheckpointByName(auditProperties.getNodeName()).orElse(new Checkpoint(auditProperties.getNodeName()));
    }

    @Override
    @Counted(recordFailuresOnly = true)
    public void handle(String line) {
        try (var ignored = lock.lockAsResource()) {
            long curLines = this.lines.incrementAndGet();
            if (!checkpointReached) {
                checkpointReached = curLines > checkpoint.getLines();
            }
            if (checkpointReached) {
                buffer.add(objectMapper.readValue(line, AuditEntry.class));
                if (buffer.size() == maxBatchSize) {
                    this.flush();
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle the log line {}", e.getMessage(), e);
        }
    }

    @Override
    public void fileRotated() {
        try (var ignored = lock.lockAsResource()) {
            this.lines.set(0);
            if (!checkpointReached) {
                checkpoint.setLines(0);
            }
            log.info("audit file rotated");
        }
    }

    /**
     * This method should never throw an exception because java's scheduler quits if an exception is thrown
     */
    public void flush() {
        try (var ignored = lock.lockAsResource()) {
            if (buffer.isEmpty()) {
                return;
            }
            checkpoint.setLines(this.lines.get());
            checkpoint = flusher.flush(buffer, checkpoint);
            buffer.clear();
        } catch (Exception e) {
            log.error("Failed to flush audits at {}: {}", this.lines.get(), e.getMessage(), e);
        }
    }
}
