package com.trading.controller;

import com.trading.model.Trader;
import com.trading.service.TradingPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TraderControllerTest {

    @Mock
    private TradingPersistenceService persistenceServiceMock;

    @InjectMocks
    private TraderController controllerMock;

    private Trader testTrader;
    private UUID traderId;
    private UUID deskId;

    @BeforeEach
    void setUp() {
        // Arrange
        traderId = UUID.randomUUID();
        deskId = UUID.randomUUID();
        testTrader = new Trader();
        testTrader.setId(traderId);
        testTrader.setDeskId(deskId);
        testTrader.setName("Test Trader");
    }

    @Test
    void createTrader_WithValidTrader_ReturnsCreatedTrader() {
        // Arrange
        when(persistenceServiceMock.deskExists(deskId)).thenReturn(true);
        when(persistenceServiceMock.saveTrader(any(Trader.class))).thenReturn(testTrader);

        // Act
        ResponseEntity<Trader> response = controllerMock.createTrader(testTrader);

        // Assert
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals(testTrader, response.getBody());
    }

    @Test
    void createTrader_WithExistingId_ReturnsBadRequest() {
        // Arrange
        when(persistenceServiceMock.traderExists(traderId)).thenReturn(true);

        // Act
        ResponseEntity<Trader> response = controllerMock.createTrader(testTrader);

        // Assert
        assertTrue(response.getStatusCode().is4xxClientError());
    }

    @Test
    void createTrader_WithNonExistingDesk_ReturnsBadRequest() {
        // Arrange
        when(persistenceServiceMock.deskExists(deskId)).thenReturn(false);

        // Act
        ResponseEntity<Trader> response = controllerMock.createTrader(testTrader);

        // Assert
        assertTrue(response.getStatusCode().is4xxClientError());
    }

    @Test
    void getTrader_WithExistingId_ReturnsTrader() {
        // Arrange
        when(persistenceServiceMock.getTrader(traderId)).thenReturn(testTrader);

        // Act
        ResponseEntity<Trader> response = controllerMock.getTrader(traderId);

        // Assert
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals(testTrader, response.getBody());
    }

    @Test
    void getTrader_WithNonExistingId_ReturnsNotFound() {
        // Arrange
        when(persistenceServiceMock.getTrader(traderId)).thenReturn(null);

        // Act
        ResponseEntity<Trader> response = controllerMock.getTrader(traderId);

        // Assert
        assertTrue(response.getStatusCode().is4xxClientError());
    }

    @Test
    void getTradersByDesk_WithExistingDesk_ReturnsTraders() {
        // Arrange
        List<Trader> traders = Arrays.asList(testTrader);
        when(persistenceServiceMock.deskExists(deskId)).thenReturn(true);
        when(persistenceServiceMock.getDeskTraders(deskId)).thenReturn(traders);

        // Act
        ResponseEntity<List<Trader>> response = controllerMock.getTradersByDesk(deskId);

        // Assert
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals(traders, response.getBody());
    }

    @Test
    void deleteTrader_WithExistingId_ReturnsSuccess() {
        // Arrange
        when(persistenceServiceMock.traderExists(traderId)).thenReturn(true);

        // Act
        ResponseEntity<Void> response = controllerMock.deleteTrader(traderId);

        // Assert
        assertTrue(response.getStatusCode().is2xxSuccessful());
        verify(persistenceServiceMock).deleteTrader(traderId);
    }

    @Test
    void deleteTrader_WithNonExistingId_ReturnsNotFound() {
        // Arrange
        when(persistenceServiceMock.traderExists(traderId)).thenReturn(false);

        // Act
        ResponseEntity<Void> response = controllerMock.deleteTrader(traderId);

        // Assert
        assertTrue(response.getStatusCode().is4xxClientError());
        verify(persistenceServiceMock, never()).deleteTrader(traderId);
    }
} 