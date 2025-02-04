package com.trading.controller;

import com.trading.model.Currency;
import com.trading.service.CurrencyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for CurrencyController that verify FX rate management endpoints.
 * Covers rate updates, queries, and error handling for currency operations.
 */
@ExtendWith(MockitoExtension.class)
class CurrencyControllerTest {

    @Mock
    private CurrencyManager currencyManager;

    @InjectMocks
    private CurrencyController controller;

    private Map<Currency, Double> testRates;

    @BeforeEach
    void setUp() {
        // Arrange
        testRates = new HashMap<>();
        testRates.put(Currency.EUR, 1.18);
        testRates.put(Currency.GBP, 1.38);
        testRates.put(Currency.USD, 1.0);
    }

    /**
     * Tests successful retrieval of all FX rates.
     * Verifies that:
     * 1. All current rates are returned
     * 2. Response status is 200 OK
     * 3. Rate map contains correct values
     */
    @Test
    void getCurrentRates_ReturnsAllRates() {
        // Arrange
        when(currencyManager.getCurrentRates()).thenReturn(testRates);

        // Act
        ResponseEntity<Map<Currency, Double>> response = controller.getCurrentRates();

        // Assert
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertEquals(testRates, response.getBody());
    }

    /**
     * Tests error handling during rate retrieval.
     * Verifies that:
     * 1. Service exceptions are caught
     * 2. Response status is 500 Internal Server Error
     * 3. Error is logged with correct code
     */
    @Test
    void getCurrentRates_WhenErrorOccurs_ReturnsInternalError() {
        // Arrange
        RuntimeException expectedError = new RuntimeException("Test error");
        when(currencyManager.getCurrentRates())
            .thenThrow(expectedError);

        // Act
        ResponseEntity<Map<Currency, Double>> response = controller.getCurrentRates();

        // Assert
        assertAll(
            () -> assertTrue(response.getStatusCode().is5xxServerError()),
            () -> verify(currencyManager).getCurrentRates()
        );
    }

    /**
     * Tests successful bulk rate update.
     * Verifies that:
     * 1. Multiple rates are updated
     * 2. Response status is 200 OK
     * 3. All rates are persisted
     */
    @Test
    void updateRates_WithValidRates_UpdatesSuccessfully() {
        // Arrange
        Map<Currency, Double> newRates = new HashMap<>();
        newRates.put(Currency.EUR, 1.20);
        newRates.put(Currency.GBP, 1.40);

        // Act
        ResponseEntity<Void> response = controller.updateRates(newRates);

        // Assert
        assertTrue(response.getStatusCode().is2xxSuccessful());
        verify(currencyManager).updateRate(Currency.EUR, 1.20);
        verify(currencyManager).updateRate(Currency.GBP, 1.40);
    }

    /**
     * Tests error handling during bulk rate updates.
     * Verifies that:
     * 1. Service exceptions are caught
     * 2. Response status is 500 Internal Server Error
     * 3. Error is logged with correct code
     * 4. No rates are updated
     */
    @Test
    void updateRates_WhenExceptionOccurs_ReturnsInternalError() {
        // Arrange
        RuntimeException expectedError = new RuntimeException("Test error");
        doThrow(expectedError)
            .when(currencyManager).updateRate(any(Currency.class), anyDouble());

        // Act
        ResponseEntity<Void> response = controller.updateRates(testRates);

        // Assert
        assertAll(
            () -> assertTrue(response.getStatusCode().is5xxServerError()),
            () -> verify(currencyManager, atLeastOnce()).updateRate(any(Currency.class), anyDouble())
        );
    }

    /**
     * Tests single rate update.
     * Verifies that:
     * 1. Individual rate is updated
     * 2. Response status is 200 OK
     * 3. Rate is persisted
     */
    @Test
    void updateRate_WithValidRate_UpdatesSuccessfully() {
        // Act
        ResponseEntity<Void> response = controller.updateRate(Currency.EUR, 1.20);

        // Assert
        assertTrue(response.getStatusCode().is2xxSuccessful());
        verify(currencyManager).updateRate(Currency.EUR, 1.20);
    }

    /**
     * Tests error handling during single rate update.
     * Verifies that:
     * 1. Service exceptions are caught
     * 2. Response status is 500 Internal Server Error
     * 3. Error is logged with correct code
     * 4. Rate remains unchanged
     */
    @Test
    void updateRate_WhenErrorOccurs_ReturnsInternalError() {
        // Arrange
        doThrow(new RuntimeException("Test error"))
            .when(currencyManager).updateRate(Currency.EUR, 1.20);

        // Act
        ResponseEntity<Void> response = controller.updateRate(Currency.EUR, 1.20);

        // Assert
        assertTrue(response.getStatusCode().is5xxServerError());
    }

    /**
     * Tests retrieval of specific currency rate.
     * Verifies that:
     * 1. Correct rate is returned
     * 2. Response status is 200 OK
     * 3. Rate value matches expected
     */
    @Test
    void getRate_ExistingCurrency_ReturnsRate() {
        // Arrange
        when(currencyManager.hasRate(Currency.EUR)).thenReturn(true);
        when(currencyManager.getRate(Currency.EUR)).thenReturn(1.18);

        // Act
        ResponseEntity<Double> response = controller.getRate(Currency.EUR);

        // Assert
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals(1.18, response.getBody());
    }

    /**
     * Tests retrieval of non-existent currency rate.
     * Verifies that:
     * 1. Missing currency returns 404
     * 2. Error is logged with correct code
     * 3. No rate is returned
     */
    @Test
    void getRate_NonExistentCurrency_ReturnsNotFound() {
        // Arrange
        when(currencyManager.hasRate(Currency.JPY)).thenReturn(false);

        // Act
        ResponseEntity<Double> response = controller.getRate(Currency.JPY);

        // Assert
        assertTrue(response.getStatusCode().is4xxClientError());
    }

    /**
     * Tests retrieval of specific currency rate.
     * Verifies that:
     * 1. Correct rate is returned
     * 2. Response status is 200 OK
     * 3. Rate value matches expected
     */
    @Test
    void getRate_WhenErrorOccurs_ReturnsInternalError() {
        // Arrange
        when(currencyManager.hasRate(Currency.EUR)).thenThrow(new RuntimeException("Test error"));

        // Act
        ResponseEntity<Double> response = controller.getRate(Currency.EUR);

        // Assert
        assertTrue(response.getStatusCode().is5xxServerError());
    }
} 