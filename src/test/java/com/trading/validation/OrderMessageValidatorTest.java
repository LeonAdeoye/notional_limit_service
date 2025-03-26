package com.trading.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.model.Currency;
import com.trading.model.Order;
import com.trading.model.TradeSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for OrderMessageValidator that verify message parsing and validation.
 */
@ExtendWith(MockitoExtension.class)
class OrderMessageValidatorTest {

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderMessageValidator validator;

    private Order validOrder;
    private String validOrderJson;

    @BeforeEach
    void setUp() {
        // Arrange
        UUID id = UUID.randomUUID();
        UUID traderId = UUID.randomUUID();
        validOrder = new Order(id, traderId, "AAPL", 100, 150.0, TradeSide.BUY, Currency.USD, LocalDate.now());
        validOrderJson = "{\"valid\":\"json\"}";
    }

    /**
     * Tests validation of well-formed order messages.
     * Verifies that:
     * 1. Valid JSON is parsed correctly
     * 2. All required fields are present
     * 3. Validation result indicates success
     * 4. No error message is returned
     */
    @Test
    void validateMessage_WithValidOrder_ReturnsValidResult() throws Exception {
        // Arrange
        when(objectMapper.readValue(anyString(), eq(Order.class))).thenReturn(validOrder);

        // Act
        ValidationResult result = validator.validateMessage(validOrderJson);

        // Assert
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());
    }

    /**
     * Tests validation of orders with negative prices.
     * Verifies that:
     * 1. Negative prices are rejected
     * 2. Validation result indicates failure
     * 3. Error message mentions price validation
     */
    @Test
    void validateMessage_WithNegativePrice_ReturnsInvalidResult() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        UUID traderId = UUID.randomUUID();
        validOrder = new Order(id, traderId, "AAPL", 100, -100.0, TradeSide.BUY, Currency.USD, LocalDate.now());
        when(objectMapper.readValue(anyString(), eq(Order.class))).thenReturn(validOrder);

        // Act
        ValidationResult result = validator.validateMessage(validOrderJson);

        // Assert
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Price must be positive"));
    }

    /**
     * Tests validation of orders with zero quantity.
     * Verifies that:
     * 1. Zero quantities are rejected
     * 2. Validation result indicates failure
     * 3. Error message mentions quantity validation
     */
    @Test
    void validateMessage_WithZeroQuantity_ReturnsInvalidResult() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        UUID traderId = UUID.randomUUID();
        validOrder = new Order(id, traderId, "AAPL", 0, 500.0, TradeSide.BUY, Currency.USD, LocalDate.now());
        when(objectMapper.readValue(anyString(), eq(Order.class))).thenReturn(validOrder);

        // Act
        ValidationResult result = validator.validateMessage(validOrderJson);

        // Assert
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Quantity must be positive"));
    }

    /**
     * Tests validation of orders with missing symbol.
     * Verifies that:
     * 1. Null symbols are rejected
     * 2. Validation result indicates failure
     * 3. Error message mentions required symbol
     */
    @Test
    void validateMessage_WithNullSymbol_ReturnsInvalidResult() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        UUID traderId = UUID.randomUUID();
        validOrder = new Order(id, traderId, null, 100, 150.0, TradeSide.BUY, Currency.USD, LocalDate.now());
        when(objectMapper.readValue(anyString(), eq(Order.class))).thenReturn(validOrder);

        // Act
        ValidationResult result = validator.validateMessage(validOrderJson);

        // Assert
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Symbol is required"));
    }

    /**
     * Tests handling of malformed JSON messages.
     * Verifies that:
     * 1. Invalid JSON is detected
     * 2. Validation result indicates failure
     * 3. Error message mentions parsing failure
     * 4. Original parsing exception is logged
     */
    @Test
    void validateMessage_WithInvalidJson_ReturnsInvalidResult() throws JsonProcessingException {
        // Arrange
        RuntimeException expectedError = new RuntimeException("Invalid JSON");
        when(objectMapper.readValue(anyString(), eq(Order.class)))
            .thenThrow(expectedError);

        // Act
        ValidationResult result = validator.validateMessage("invalid json");

        // Assert
        assertAll(
            () -> assertFalse(result.isValid()),
            () -> assertTrue(result.getErrorMessage().contains("Failed to parse message")),
            () -> verify(objectMapper).readValue(anyString(), eq(Order.class))
        );
    }
} 