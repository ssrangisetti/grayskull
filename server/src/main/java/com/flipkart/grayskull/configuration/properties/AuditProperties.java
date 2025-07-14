package com.flipkart.grayskull.configuration.properties;

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
    private String filePath;
    @NotEmpty
    private String filePattern;
    @NotEmpty
    private String maxFileSize;
    /**
     * number of files to keep in audit history
     */
    private int maxHistory;
    @NotEmpty
    private String nodeName;
    /**
     * batch size for processing the audit logs and flushing to DB
     */
    @Min(1)
    private int batchSize;
    /**
     * if batchSize is too low, then auditProcessor will wait for this duration to flush audit logs to DB
     */
    @Min(1)
    private int batchTimeSeconds;
}
