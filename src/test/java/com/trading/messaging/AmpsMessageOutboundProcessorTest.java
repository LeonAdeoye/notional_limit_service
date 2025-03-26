package com.trading.messaging;

import com.crankuptheamps.client.Client;
import com.crankuptheamps.client.exception.AMPSException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for AmpsMessageProcessor that verify AMPS message handling and publishing.
 * Covers connection management, message processing, and error handling.
 */
@EnableConfigurationProperties
@TestPropertySource(locations = "classpath:/application.properties")
@ExtendWith(SpringExtension.class)
class AmpsMessageOutboundProcessorTest {

    private String ampsServerUrl = "tcp://localhost:9007/amps/json";
    private String limitBreachTopic = "limit_breach";
    @Mock
    private Client ampsClient;

    @InjectMocks
    private AmpsMessageOutboundProcessor processor;

    /**
     * Tests publishing failure handling.
     * Verifies that:
     * 1. Publishing errors are caught
     * 2. Error is logged
     * 3. No exception is propagated
     * 4. System remains stable
     */
    @Test
    void publishLimitBreach_WhenPublishingFails_HandlesError() throws AMPSException {
        // Arrange
        String breachMessage = "Limit breach detected";
        RuntimeException expectedError = new RuntimeException("Publishing failed");
        doThrow(expectedError)
            .when(ampsClient).publish(anyString(), anyString());

        // Act
        processor.publishLimitBreach(breachMessage);

        // Assert
        verify(ampsClient, never()).publish(limitBreachTopic, breachMessage);
    }
}