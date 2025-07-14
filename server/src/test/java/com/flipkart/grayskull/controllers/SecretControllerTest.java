package com.flipkart.grayskull.controllers;

import com.flipkart.grayskull.models.dto.response.SecretDataResponse;
import com.flipkart.grayskull.models.enums.AuditAction;
import com.flipkart.grayskull.models.enums.AuditStatus;
import com.flipkart.grayskull.service.interfaces.SecretService;
import com.flipkart.grayskull.spi.AsyncAuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("SecretController Unit Tests")
class SecretControllerTest {

    private final SecretService secretService = mock(SecretService.class);

    private final AsyncAuditLogger asyncAuditLogger = mock(AsyncAuditLogger.class);

    private SecretController secretController;

    @BeforeEach
    void setUp() {
        secretController = new SecretController(secretService, asyncAuditLogger);
    }

    @Nested
    @DisplayName("readSecretValue Tests")
    class ReadSecretValueTests {

        private static final String PROJECT_ID = "test-project";
        private static final String SECRET_NAME = "test-secret";
        private static final String SECRET_VALUE = "sensitive-secret-value";
        private static final int DATA_VERSION = 1;

        @Test
        @DisplayName("Should successfully read secret value and log audit")
        void shouldSuccessfullyReadSecretValue() {
            // Arrange
            SecretDataResponse expectedResponse = SecretDataResponse.builder()
                    .dataVersion(DATA_VERSION)
                    .publicPart("public-data")
                    .privatePart(SECRET_VALUE)
                    .lastRotated(Instant.now())
                    .creationTime(Instant.now())
                    .updatedTime(Instant.now())
                    .createdBy("test-user")
                    .updatedBy("test-user")
                    .state("ACTIVE")
                    .build();

            when(secretService.readSecretValue(PROJECT_ID, SECRET_NAME))
                    .thenReturn(expectedResponse);

            // Act
            var result = secretController.readSecretValue(PROJECT_ID, SECRET_NAME);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getData()).isEqualTo(expectedResponse);
            assertThat(result.getMessage()).isEqualTo("Successfully read secret value.");

            // Verify audit logging
            ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.captor();
            verify(asyncAuditLogger).log(
                    eq(PROJECT_ID),
                    eq(SECRET_NAME),
                    eq(DATA_VERSION),
                    eq(AuditAction.READ_SECRET.name()),
                    eq(AuditStatus.SUCCESS.name()),
                    metadataCaptor.capture()
            );

            Map<String, String> capturedMetadata = metadataCaptor.getValue();
            assertThat(capturedMetadata).containsKey("result");
            assertThat(capturedMetadata.get("result")).contains("privatePart");
            // The privatePart should be masked in the audit log
            assertThat(capturedMetadata.get("result")).doesNotContain(SECRET_VALUE);
        }

        @Test
        @DisplayName("Should handle service exception and log failure audit")
        void shouldHandleServiceExceptionAndLogFailureAudit() {
            // Arrange
            RuntimeException serviceException = new RuntimeException("Secret not found");
            when(secretService.readSecretValue(PROJECT_ID, SECRET_NAME))
                    .thenThrow(serviceException);

            // Act & Assert
            assertThatThrownBy(() -> secretController.readSecretValue(PROJECT_ID, SECRET_NAME))
                    .isEqualTo(serviceException);

            // Verify audit logging for failure
            ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.captor();
            verify(asyncAuditLogger).log(
                    eq(PROJECT_ID),
                    eq(SECRET_NAME),
                    eq(null),
                    eq(AuditAction.READ_SECRET.name()),
                    eq(AuditStatus.FAILURE.name()),
                    metadataCaptor.capture()
            );

            Map<String, String> capturedMetadata = metadataCaptor.getValue();
            assertThat(capturedMetadata)
                    .containsEntry("errorMessage", "Secret not found")
                    .containsEntry("errorType", "java.lang.RuntimeException");
        }

    }
} 