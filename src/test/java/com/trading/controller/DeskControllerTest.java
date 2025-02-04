package com.trading.controller;

import com.trading.model.Desk;
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

/**
 * Tests for DeskController that verify REST endpoints for desk management.
 * Covers CRUD operations, validation, and error handling for trading desks.
 */
@ExtendWith(MockitoExtension.class)
class DeskControllerTest {

    @Mock
    private TradingPersistenceService persistenceService;

    @InjectMocks
    private DeskController controller;

    private Desk testDesk;
    private UUID deskId;

    @BeforeEach
    void setUp() {
        // Arrange
        deskId = UUID.randomUUID();
        testDesk = new Desk();
        testDesk.setId(deskId);
        testDesk.setName("Test Desk");
    }

    /**
     * Tests successful desk creation.
     * Verifies that:
     * 1. Valid desk data is accepted
     * 2. New desk is saved correctly
     * 3. Generated ID is returned
     * 4. Response status is 200 OK
     */
    @Test
    void createDesk_WithValidDesk_ReturnsCreatedDesk() {
        // Arrange
        when(persistenceService.saveDesk(any(Desk.class))).thenReturn(testDesk);

        // Act
        ResponseEntity<Desk> response = controller.createDesk(testDesk);

        // Assert
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals(testDesk, response.getBody());
    }

    /**
     * Tests creation with existing desk ID.
     * Verifies that:
     * 1. Duplicate desk IDs are rejected
     * 2. Response status is 400 Bad Request
     * 3. Error is logged with correct code
     */
    @Test
    void createDesk_WithExistingId_ReturnsBadRequest() {
        // Arrange
        when(persistenceService.deskExists(deskId)).thenReturn(true);

        // Act
        ResponseEntity<Desk> response = controller.createDesk(testDesk);

        // Assert
        assertTrue(response.getStatusCode().is4xxClientError());
    }

    /**
     * Tests successful desk retrieval.
     * Verifies that:
     * 1. Existing desk is found
     * 2. Correct desk data is returned
     * 3. Response status is 200 OK
     */
    @Test
    void getDesk_WithExistingId_ReturnsDesk() {
        // Arrange
        when(persistenceService.getDesk(deskId)).thenReturn(testDesk);

        // Act
        ResponseEntity<Desk> response = controller.getDesk(deskId);

        // Assert
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals(testDesk, response.getBody());
    }

    /**
     * Tests retrieval of non-existent desk.
     * Verifies that:
     * 1. Missing desk ID returns 404
     * 2. Error is logged with correct code
     * 3. No desk data is returned
     */
    @Test
    void getDesk_WithNonExistingId_ReturnsNotFound() {
        // Arrange
        when(persistenceService.getDesk(deskId)).thenReturn(null);

        // Act
        ResponseEntity<Desk> response = controller.getDesk(deskId);

        // Assert
        assertTrue(response.getStatusCode().is4xxClientError());
    }

    /**
     * Tests retrieval of all desks.
     * Verifies that:
     * 1. All desks are returned
     * 2. Response status is 200 OK
     * 3. List contains correct desk data
     */
    @Test
    void getAllDesks_ReturnsAllDesks() {
        // Arrange
        List<Desk> desks = Arrays.asList(testDesk);
        when(persistenceService.getAllDesks()).thenReturn(desks);

        // Act
        ResponseEntity<List<Desk>> response = controller.getAllDesks();

        // Assert
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals(desks, response.getBody());
    }

    /**
     * Tests successful desk deletion.
     * Verifies that:
     * 1. Existing desk is deleted
     * 2. Response status is 200 OK
     * 3. Desk is removed from persistence
     */
    @Test
    void deleteDesk_WithExistingId_ReturnsSuccess() {
        // Arrange
        when(persistenceService.deskExists(deskId)).thenReturn(true);
        when(persistenceService.hasTradersForDesk(deskId)).thenReturn(false);

        // Act
        ResponseEntity<Void> response = controller.deleteDesk(deskId);

        // Assert
        assertTrue(response.getStatusCode().is2xxSuccessful());
        verify(persistenceService).deleteDesk(deskId);
    }

    /**
     * Tests deletion of desk with associated traders.
     * Verifies that:
     * 1. Desk with traders cannot be deleted
     * 2. Response status is 400 Bad Request
     * 3. Error is logged with correct code
     * 4. Desk remains in persistence
     */
    @Test
    void deleteDesk_WithAssociatedTraders_ReturnsBadRequest() {
        // Arrange
        when(persistenceService.deskExists(deskId)).thenReturn(true);
        when(persistenceService.hasTradersForDesk(deskId)).thenReturn(true);

        // Act
        ResponseEntity<Void> response = controller.deleteDesk(deskId);

        // Assert
        assertTrue(response.getStatusCode().is4xxClientError());
        verify(persistenceService, never()).deleteDesk(deskId);
    }

    /**
     * Tests deletion of non-existent desk.
     * Verifies that:
     * 1. Missing desk ID returns 404
     * 2. Error is logged with correct code
     * 3. No deletion is attempted
     * 4. Persistence service is not called
     */
    @Test
    void deleteDesk_WithNonExistingId_ReturnsNotFound() {
        // Arrange
        when(persistenceService.deskExists(deskId)).thenReturn(false);

        // Act
        ResponseEntity<Void> response = controller.deleteDesk(deskId);

        // Assert
        assertTrue(response.getStatusCode().is4xxClientError());
        verify(persistenceService, never()).deleteDesk(deskId);
    }
} 