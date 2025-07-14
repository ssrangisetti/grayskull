package com.flipkart.grayskull.audit;

import com.flipkart.grayskull.configuration.properties.AuditProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListenerAdapter;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AsyncAuditLogProcessor extends TailerListenerAdapter {

    private final Tailer tailer;
    private final ScheduledExecutorService scheduler; // scheduler for scheduling time based flushing


    public AsyncAuditLogProcessor(AuditProperties auditProperties, AuditLogTailer auditLogTailer) {
        this.tailer = Tailer.builder()
                .setFile(new File(auditProperties.getFilePath()))
                .setTailerListener(auditLogTailer)
                .get();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(AsyncAuditLogProcessor::newFlusherThread);
        int flushInterval = auditProperties.getBatchTimeSeconds();
        this.scheduler.scheduleAtFixedRate(auditLogTailer::flush, flushInterval, flushInterval, TimeUnit.SECONDS);
    }

    private static Thread newFlusherThread(final Runnable runnable) {
        Thread thread = new Thread(runnable, "Flusher-Scheduler");
        thread.setDaemon(true);
        return thread;
    }

    @PreDestroy
    public void close() {
        tailer.close();
        scheduler.shutdown();
    }

}
