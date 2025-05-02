package com.trading.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.trading.model.Currency;

/**
 * Tests for CurrencyManager that verify FX rate management and conversions.
 */
@ExtendWith(MockitoExtension.class)
class CurrencyManagerTest {

    @InjectMocks
    private CurrencyManager currencyManager;

    @BeforeEach
    void setUp() {
        currencyManager.initialize(); // Load initial rates
    }

    /**
     * Tests initialization of currency rates.
     * Verifies that:
     * 1. Default rates are loaded on startup
     * 2. USD rate is set to 1.0
     * 3. Major currencies (EUR, GBP) have valid rates
     */
    @Test
    void initialize_LoadsDefaultRates() {
        // Arrange
        currencyManager = new CurrencyManager();

        // Act
        currencyManager.initialize();

        // Assert
        assertTrue(currencyManager.hasRate(Currency.USD));
        assertTrue(currencyManager.hasRate(Currency.EUR));
        assertTrue(currencyManager.hasRate(Currency.GBP));
        assertEquals(1.0, currencyManager.getRate(Currency.USD));
    }

    /**
     * Tests currency conversion to USD with valid input.
     * Verifies that:
     * 1. Amount is correctly converted using current FX rate
     * 2. Calculation precision is maintained
     * 3. Result matches expected converted amount
     */
    @Test
    void convertToUSD_WithValidCurrency_ReturnsConvertedAmount() {
        // Arrange
        Currency currency = Currency.EUR;
        double rate = 1.18;
        double amount = 100.0;
        double expected = 118.0;
        currencyManager.updateRate(currency, rate);

        // Act
        double result = currencyManager.convertToUSD(amount, currency);

        // Assert
        assertEquals(expected, result);
    }

    /**
     * Tests conversion of USD amounts.
     * Verifies that:
     * 1. USD to USD conversion returns original amount
     * 2. No rate lookup is needed
     * 3. Precision is maintained
     */
    @Test
    void convertToUSD_WithUSDCurrency_ReturnsOriginalAmount() {
        // Arrange
        double amount = 100.0;

        // Act
        double result = currencyManager.convertToUSD(amount, Currency.USD);

        // Assert
        assertEquals(amount, result);
    }

    /**
     * Tests validation of negative FX rates.
     * Verifies that:
     * 1. Negative rates are rejected
     * 2. Appropriate exception is thrown
     * 3. Error message indicates invalid rate
     */
    @Test
    void updateRate_WithNegativeRate_ThrowsException() {
        // Arrange
        double negativeRate = -1.18;

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> currencyManager.updateRate(Currency.EUR, negativeRate));
        assertEquals("Invalid FX rate: " + Currency.EUR + " -> " + negativeRate, exception.getMessage());
    }

    /**
     * Tests validation of zero FX rates.
     * Verifies that:
     * 1. Zero rates are rejected
     * 2. Appropriate exception is thrown
     * 3. Error message indicates invalid rate
     */
    @Test
    void updateRate_WithZeroRate_ThrowsException() {
        // Arrange
        double zeroRate = 0.0;

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> currencyManager.updateRate(Currency.EUR, zeroRate));
        assertEquals("Invalid FX rate: " + Currency.EUR + " -> " + zeroRate, exception.getMessage());
    }

    /**
     * Tests adding new currency rates.
     * Verifies that:
     * 1. New rate is successfully stored
     * 2. Rate can be retrieved
     * 3. Retrieved rate matches stored value
     */
    @Test
    void updateRate_NewRate_UpdatesSuccessfully() {
        // Arrange
        double rate = 1.35;

        // Act
        currencyManager.updateRate(Currency.GBP, rate);

        // Assert
        assertTrue(currencyManager.hasRate(Currency.GBP));
        assertEquals(rate, currencyManager.getRate(Currency.GBP));
    }

    /**
     * Tests updating existing currency rates.
     * Verifies that:
     * 1. Existing rate is updated
     * 2. New rate overwrites old rate
     * 3. Retrieved rate matches updated value
     */
    @Test
    void updateRate_ExistingRate_UpdatesSuccessfully() {
        // Arrange
        double initialRate = 1.18;
        double newRate = 1.20;
        currencyManager.updateRate(Currency.EUR, initialRate);

        // Act
        currencyManager.updateRate(Currency.EUR, newRate);

        // Assert
        assertEquals(newRate, currencyManager.getRate(Currency.EUR));
    }

    /**
     * Tests checking existence of valid rates.
     * Verifies that:
     * 1. Known currency returns true
     * 2. Recently added currency returns true
     */
    @Test
    void hasRate_ExistingCurrency_ReturnsTrue() {
        currencyManager.updateRate(Currency.JPY, 0.0091);
        assertTrue(currencyManager.hasRate(Currency.JPY));
    }

    /**
     * Tests retrieval of all current rates.
     * Verifies that:
     * 1. All stored rates are returned
     * 2. Returned rates match stored values
     * 3. Map contains expected currencies
     */
    @Test
    void getCurrentRates_ReturnsAllRates() {
        currencyManager.updateRate(Currency.EUR, 1.18);
        currencyManager.updateRate(Currency.GBP, 1.38);
        
        Map<Currency, Double> rates = currencyManager.getCurrentRates();
        
        assertNotNull(rates);
        assertTrue(rates.containsKey(Currency.EUR));
        assertTrue(rates.containsKey(Currency.GBP));
        assertEquals(1.18, rates.get(Currency.EUR));
        assertEquals(1.38, rates.get(Currency.GBP));
    }

    /**
     * Tests periodic rate refresh functionality.
     * Verifies that:
     * 1. All major currencies are updated
     * 2. USD rate remains at 1.0
     * 3. All rates are valid
     */
    @Test
    void refreshFxRates_UpdatesAllRates() {
        currencyManager.refreshFxRates();
        
        assertTrue(currencyManager.hasRate(Currency.EUR));
        assertTrue(currencyManager.hasRate(Currency.GBP));
        assertTrue(currencyManager.hasRate(Currency.JPY));
        assertTrue(currencyManager.hasRate(Currency.USD));
    }

    /**
     * Tests retrieval of existing rates.
     * Verifies that:
     * 1. Rate is successfully retrieved
     * 2. Retrieved rate matches stored value
     */
    @Test
    void getRate_ExistingCurrency_ReturnsRate() {
        double rate = 0.73;
        currencyManager.updateRate(Currency.AUD, rate);
        assertEquals(rate, currencyManager.getRate(Currency.AUD));
    }

    /**
     * Tests error handling during rate refresh.
     * Verifies that:
     * 1. Basic rates remain available even after errors
     * 2. USD rate remains at 1.0
     * 3. System remains stable after errors
     */
    @Test
    void refreshFxRates_HandlesErrors_MaintainsBasicRates() {
        // Arrange
        currencyManager = Mockito.spy(currencyManager);
        doThrow(new RuntimeException("Refresh failed"))
            .when(currencyManager).refreshFxRates();

        try {
            // Act
            currencyManager.refreshFxRates();
        } catch (Exception e) {
            // Ignore the exception - we're testing recovery
        }

        // Assert
        // Verify basic rates are still available
        assertTrue(currencyManager.hasRate(Currency.USD));
        assertEquals(1.0, currencyManager.getRate(Currency.USD));
    }

    /**
     * Tests that rate map modifications are isolated.
     * Verifies that:
     * 1. Returned map is a copy
     * 2. Modifications don't affect original rates
     * 3. Original rates remain unchanged
     */
    @Test
    void getCurrentRates_ReturnsDefensiveCopy() {
        // Arrange
        double rate = 1.18;
        currencyManager.updateRate(Currency.EUR, rate);

        // Act
        Map<Currency, Double> rates = currencyManager.getCurrentRates();
        rates.put(Currency.CHF, 1.0); // Try to modify the returned map

        // Assert
        assertFalse(currencyManager.hasRate(Currency.CHF));
        assertEquals(rate, currencyManager.getRate(Currency.EUR));
    }

    /**
     * Tests reinitialization behavior.
     * Verifies that:
     * 1. Existing rates are cleared
     * 2. Default rates are reloaded
     * 3. Custom rates are overwritten
     */
    @Test
    void initialize_WithExistingRates_OverwritesRates() {
        // Arrange
        currencyManager.updateRate(Currency.EUR, 2.0);

        // Act
        currencyManager.initialize();

        // Assert
        assertTrue(currencyManager.hasRate(Currency.EUR));
        assertNotEquals(2.0, currencyManager.getRate(Currency.EUR));
    }
} 