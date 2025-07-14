package com.flipkart.grayskull.spimpl.audit;

import ch.qos.logback.core.rolling.RollingFileAppender;
import com.flipkart.grayskull.configuration.properties.AuditProperties;
import com.flipkart.grayskull.models.db.AuditEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileAsyncAuditLoggerTest {

    @TempDir
    Path tempDir;

    private FileAsyncAuditLogger fileAsyncAuditLogger;
    private JacksonAuditEncoder<AuditEntry> encoder;

    @BeforeEach
    void setUp() throws Exception {
        // Create a temporary audit file
        File auditFile = tempDir.resolve("audit.log").toFile();
        Files.createFile(auditFile.toPath());

        // Create AuditProperties
        AuditProperties auditProperties = new AuditProperties();
        auditProperties.setFilePath(auditFile.getAbsolutePath());
        auditProperties.setFilePattern(tempDir.resolve("audit-%d{yyyy-MM-dd}.%i.log").toString());
        auditProperties.setMaxFileSize("10MB");
        auditProperties.setMaxHistory(30);

        // Mock encoder
        encoder = mock();

        // Create the logger
        fileAsyncAuditLogger = new FileAsyncAuditLogger(auditProperties, encoder);
    }

    @Test
    void testStart_WithValidConfiguration_StartsFileAppender() {
        // Act
        fileAsyncAuditLogger.start();

        // Assert
        RollingFileAppender<AuditEntry> fileAppender = (RollingFileAppender<AuditEntry>) ReflectionTestUtils.getField(fileAsyncAuditLogger, "fileAppender");

        assertTrue(fileAppender.isStarted());
        assertTrue(fileAppender.getRollingPolicy().isStarted());
    }

    @Test
    void testStart_WithInvalidConfiguration_ThrowsException() {
        // Arrange - Create invalid properties
        AuditProperties invalidProperties = new AuditProperties();
        invalidProperties.setFilePath(null);
        invalidProperties.setFilePattern(null);
        invalidProperties.setMaxFileSize("10MB");
        invalidProperties.setMaxHistory(0);

        FileAsyncAuditLogger invalidLogger = new FileAsyncAuditLogger(invalidProperties, encoder);

        // Act & Assert
        assertThrows(IllegalStateException.class, invalidLogger::start);
    }

    @Test
    void testLog_CreatesAuditEntryWithCorrectValues() {
        // Arrange
        fileAsyncAuditLogger.start();

        String projectId = "test-project";
        String secret = "test-secret";
        Integer secretVersion = 2;
        String action = "SECRET_CREATE";
        String status = "SUCCESS";
        Map<String, String> metadata = new HashMap<>();
        metadata.put("environment", "production");

        TestingAuthenticationToken authentication = new TestingAuthenticationToken("admin-user", null);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Act
        fileAsyncAuditLogger.log(projectId, secret, secretVersion, action, status, metadata);

        // Assert
        // Verify that the encoder was called with an AuditEntry
        ArgumentCaptor<AuditEntry> argumentCaptor = ArgumentCaptor.captor();
        verify(encoder, times(1)).encode(argumentCaptor.capture());

        AuditEntry auditEntry = argumentCaptor.getValue();
        assertEquals(projectId, auditEntry.getProjectId());
        assertEquals(metadata, auditEntry.getMetadata());
        // add more asserts
    }
}