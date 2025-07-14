package com.flipkart.grayskull.spimpl.audit;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.util.FileSize;
import com.flipkart.grayskull.configuration.properties.AuditProperties;
import com.flipkart.grayskull.models.db.AuditEntry;
import com.flipkart.grayskull.spi.AsyncAuditLogger;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class FileAsyncAuditLogger implements AsyncAuditLogger {

    private final RollingFileAppender<AuditEntry> fileAppender;

    public FileAsyncAuditLogger(AuditProperties auditProperties, JacksonAuditEncoder<AuditEntry> encoder) {
        this.fileAppender = new  RollingFileAppender<>();
        fileAppender.setEncoder(encoder);
        fileAppender.setName("FileAsyncAuditLogger");
        fileAppender.setFile(auditProperties.getFilePath());
        fileAppender.setContext(new LoggerContext());
        SizeAndTimeBasedRollingPolicy<AuditEntry> rollingPolicy = new SizeAndTimeBasedRollingPolicy<>();
        rollingPolicy.setContext(fileAppender.getContext());
        rollingPolicy.setFileNamePattern(auditProperties.getFilePattern());
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.setMaxHistory(auditProperties.getMaxHistory());
        rollingPolicy.setMaxFileSize(FileSize.valueOf(auditProperties.getMaxFileSize()));
        fileAppender.setRollingPolicy(rollingPolicy);
    }

    @PostConstruct
    public void start() {
        fileAppender.getRollingPolicy().start();
        checkContextStatus();
        fileAppender.start();
        checkContextStatus();
    }

    public void checkContextStatus() {
        StringBuilder message = new StringBuilder();
        for (Status status : fileAppender.getContext().getStatusManager().getCopyOfStatusList()) {
            if (status.getLevel() == Status.ERROR || status.getLevel() == Status.WARN) {
                message.append(status.getMessage());
                message.append("---");
            }
            log.info(status.getMessage());
        }
        if (!message.isEmpty()) {
            throw new IllegalStateException(message.toString());
        }
        fileAppender.getContext().getStatusManager().clear();
    }

    @PreDestroy
    public void stop() {
        fileAppender.stop();
    }

    @Override
    public void log(String projectId, String secret, Integer secretVersion, String action, String status, Map<String, String> metadata) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        // adding id and time so that created time is not overridden by the @CreatedDate annotation while doing the flush at a later time
        AuditEntry auditEntry = new AuditEntry(UUID.randomUUID().toString(), projectId, secret, secretVersion, action, status, username, Instant.now(), metadata);
        fileAppender.doAppend(auditEntry);
    }
}
