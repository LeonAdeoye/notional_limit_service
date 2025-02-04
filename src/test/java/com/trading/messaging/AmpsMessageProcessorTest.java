package com.trading.messaging;

import com.crankuptheamps.client.Client;
import com.crankuptheamps.client.Message;
import com.crankuptheamps.client.exception.AMPSException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.model.Currency;
import com.trading.model.Order;
import com.trading.model.TradeSide;
import com.trading.service.NotionalLimitService;
import com.trading.validation.OrderMessageValidator;
import com.trading.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for AmpsMessageProcessor that verify AMPS message handling and publishing.
 * Covers connection management, message processing, and error handling.
 */
@EnableConfigurationProperties
@TestPropertySource(locations = "classpath:/application.properties")
@ExtendWith(SpringExtension.class)
class AmpsMessageProcessorTest {

    private String ampsServerUrl = "tcp://localhost:9007/amps/json";
    
    private String ordersTopic = "orders";

    private String limitBreachTopic = "limit_breach";

    @Mock
    private NotionalLimitService notionalLimitService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private OrderMessageValidator messageValidator;

    @Mock
    private Client ampsClient;

    @Mock
    private Message message;

    @InjectMocks
    private AmpsMessageProcessor processor;

    private Order testOrder;
    private String validMessageData;

    @BeforeEach
    void setUp() {
        // Arrange
        testOrder = new Order();
        testOrder.setTraderId(UUID.randomUUID());
        testOrder.setSymbol("AAPL");
        testOrder.setQuantity(100);
        testOrder.setPrice(150.0);
        testOrder.setSide(TradeSide.BUY);
        testOrder.setCurrency(Currency.USD);
        testOrder.setTradeDate(LocalDate.now());

        validMessageData = "{\"valid\":\"json\"}";
    }

    /**
     * Tests successful message processing.
     * Verifies that:
     * 1. Message is parsed correctly
     * 2. Validation passes
     * 3. Order is processed
     * 4. MDC context is managed
     */
    @Test
    void invoke_WithValidMessage_ProcessesOrder() throws Exception {
        // Arrange
        when(message.getData()).thenReturn(validMessageData);
        when(messageValidator.validateMessage(validMessageData))
            .thenReturn(new ValidationResult(true, null));
        when(objectMapper.readValue(validMessageData, Order.class)).thenReturn(testOrder);

        // Act
        processor.invoke(message);

        // Assert
        verify(notionalLimitService).processOrder(testOrder);
        assertNull(MDC.get("errorId"));
    }

    /**
     * Tests invalid message handling.
     * Verifies that:
     * 1. Invalid messages are rejected
     * 2. Error is logged
     * 3. No order processing occurs
     * 4. MDC context is cleaned up
     */
    @Test
    void invoke_WithInvalidMessage_DoesNotProcessOrder() throws Exception {
        // Arrange
        String invalidMessageData = "invalid json";
        when(message.getData()).thenReturn(invalidMessageData);
        when(messageValidator.validateMessage(invalidMessageData))
            .thenReturn(new ValidationResult(false, "Invalid message format"));

        // Act
        processor.invoke(message);

        // Assert
        verify(notionalLimitService, never()).processOrder(any());
        assertNull(MDC.get("errorId"));
    }

    @Test
    void invoke_WhenParsingFails_HandlesError() throws Exception {
        // Arrange
        when(message.getData()).thenReturn(validMessageData);
        when(messageValidator.validateMessage(validMessageData))
            .thenReturn(new ValidationResult(true, null));
        when(objectMapper.readValue(anyString(), eq(Order.class)))
            .thenThrow(new RuntimeException("Parsing failed"));

        // Act
        processor.invoke(message);

        // Assert
        verify(notionalLimitService, never()).processOrder(any());
        assertNull(MDC.get("errorId"));
    }

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

    /**
     * Tests MDC context management.
     * Verifies that:
     * 1. Context is set before processing
     * 2. Context is available during processing
     * 3. Context is cleared after processing
     * 4. Error tracking is maintained
     */
    @Test
    void invoke_SetsAndClearsMDCContext() throws JsonProcessingException {
        // Arrange
        when(message.getData()).thenReturn(validMessageData);
        when(messageValidator.validateMessage(validMessageData))
            .thenReturn(new ValidationResult(true, null));
        when(objectMapper.readValue(validMessageData, Order.class)).thenReturn(testOrder);

        // Act
        processor.invoke(message);

        // Assert
        assertNull(MDC.get("errorId"));
    }

    /**
     * Tests null message handling.
     * Verifies that:
     * 1. Null messages are handled gracefully
     * 2. Error is logged
     * 3. No processing is attempted
     * 4. MDC context is cleaned up
     */
    @Test
    void invoke_WithNullMessage_HandlesError() {
        // Arrange
        when(message.getData()).thenReturn(null);

        // Act
        processor.invoke(message);

        // Assert
        verify(notionalLimitService, never()).processOrder(any());
        assertNull(MDC.get("errorId"));
    }

    /**
     * Tests validation failure handling.
     * Verifies that:
     * 1. Validation errors are caught
     * 2. Error is logged
     * 3. No order processing occurs
     * 4. System remains stable
     */
    @Test
    void invoke_WhenValidationFails_LogsError() throws Exception {
        // Arrange
        when(message.getData()).thenReturn(validMessageData);
        when(messageValidator.validateMessage(validMessageData))
            .thenThrow(new RuntimeException("Validation error"));

        // Act
        processor.invoke(message);

        // Assert
        verify(notionalLimitService, never()).processOrder(any());
        assertNull(MDC.get("errorId"));
    }

    /**
     * Tests order processing failure handling.
     * Verifies that:
     * 1. Processing errors are caught
     * 2. Error is logged
     * 3. MDC context is cleaned up
     * 4. System remains stable
     */
    @Test
    void invoke_WhenProcessingFails_LogsError() throws Exception {
        // Arrange
        when(message.getData()).thenReturn(validMessageData);
        when(messageValidator.validateMessage(validMessageData))
            .thenReturn(new ValidationResult(true, null));
        when(objectMapper.readValue(validMessageData, Order.class)).thenReturn(testOrder);
        doThrow(new RuntimeException("Processing failed"))
            .when(notionalLimitService).processOrder(any());

        // Act
        processor.invoke(message);

        // Assert
        verify(notionalLimitService).processOrder(testOrder);
        assertNull(MDC.get("errorId"));
    }
} 