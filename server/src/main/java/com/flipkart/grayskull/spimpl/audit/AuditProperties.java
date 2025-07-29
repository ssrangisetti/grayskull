package com.flipkart.grayskull.spimpl.audit;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@ConfigurationProperties(prefix = "audit")
@Validated
public class AuditProperties {
    @NotEmpty
    private String auditFolder;

    /**
     * queue size for sending audit events to db persister. defaults to Integer.MAX_VALUE
     */
    @Min(1)
    private int auditQueueSize = Integer.MAX_VALUE;

    @Min(1)
    private int maxAuditLines;

    @NotEmpty
    private String nodeName;

    /**
     * batch size for processing the audit logs and flushing to DB
     */
    private int batchSize;

    /**
     * if batchSize is too low, then auditProcessor will wait for this duration to flush audit logs to DB
     */
    @Min(1)
    private int batchTimeSeconds;

    @Min(1)
    private int rotateTimeSeconds;
}
