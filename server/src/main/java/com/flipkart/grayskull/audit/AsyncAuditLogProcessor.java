package com.flipkart.grayskull.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.grayskull.models.db.AuditEntry;
import com.flipkart.grayskull.models.db.Checkpoint;
import com.flipkart.grayskull.repositories.AuditCheckpointRepository;
import com.flipkart.grayskull.utils.CloseableReentrantLock;
import com.flipkart.grayskull.configuration.properties.AuditProperties;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListenerAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class AsyncAuditLogProcessor extends TailerListenerAdapter {
    private final CloseableReentrantLock lock = new CloseableReentrantLock();

    private final Tailer tailer;
    private final ScheduledExecutorService scheduler; // scheduler for scheduling time based flushing
    private final List<AuditEntry> buffer = new ArrayList<>(); // active buffer need not be concurrent because we are handling everything with locks
    private final int maxBatchSize; // max batch size after which gets flushed
    private final int flushInterval;

    private Checkpoint checkpoint;
    private boolean checkpointReached = false;
    private final AtomicLong lines = new AtomicLong(0);

    private final AuditLogFlusher flusher;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public AsyncAuditLogProcessor(AuditCheckpointRepository auditCheckpointRepository,
                                  AuditProperties auditProperties,
                                  AuditLogFlusher flusher,
                                  ObjectMapper objectMapper,
                                  MeterRegistry meterRegistry) {
        this.flusher = flusher;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;

        this.checkpoint = auditCheckpointRepository.getCheckpointByName(auditProperties.getNodeName()).orElse(new Checkpoint(auditProperties.getNodeName()));
        this.tailer = Tailer.builder()
                .setFile(new File(auditProperties.getFilePath()))
                .setTailerListener(this)
                .get();
        this.maxBatchSize = auditProperties.getBatchSize();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(AsyncAuditLogProcessor::newFlusherThread);
        this.flushInterval = auditProperties.getBatchTimeSeconds();
    }

    private static Thread newFlusherThread(final Runnable runnable) {
        Thread thread = new Thread(runnable, "Flusher-Scheduler");
        thread.setDaemon(true);
        return thread;
    }

    @PostConstruct
    public void startFlusher() {
        this.scheduler.scheduleAtFixedRate(this::flush, flushInterval, flushInterval, TimeUnit.SECONDS);
    }

    @Override
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
            meterRegistry.counter("audit-log-handle-error").increment();
        }
    }

    @Override
    public void fileRotated() {
        try (var ignored = lock.lockAsResource()) {
            this.lines.set(0);
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
            buffer.clear();
            checkpoint.setLines(this.lines.get());
            checkpoint = flusher.flush(buffer, checkpoint);
        } catch (Exception e) {
            log.error("Failed to flush audits at {}: {}", this.lines.get(), e.getMessage(), e);
            meterRegistry.counter("audit-log-flush-error").increment();
        }
    }

    @PreDestroy
    public void close() {
        tailer.close();
        scheduler.shutdown();
    }

}
