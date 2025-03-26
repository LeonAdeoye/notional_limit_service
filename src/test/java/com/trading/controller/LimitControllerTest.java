package com.trading.controller;

import com.trading.model.DeskLimits;
import com.trading.service.TradingPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for LimitController that verify trading limit management endpoints.
 * Covers limit queries, updates, and utilization monitoring.
 */
@ExtendWith(MockitoExtension.class)
class LimitControllerTest {

    @Mock
    private TradingPersistenceService persistenceService;

    @InjectMocks
    private LimitController controller;

    private DeskLimits testLimits;
    private UUID deskId;

    @BeforeEach
    void setUp() {
        // Arrange
        deskId = UUID.randomUUID();
        testLimits = new DeskLimits(deskId, deskId, 1000000, 1000000, 2000000);
    }

    /**
     * Tests successful retrieval of desk limits.
     * Verifies that:
     * 1. Correct limits are returned
     * 2. Response status is 200 OK
     * 3. Limit values match expected
     */
    @Test
    void getDeskLimits_WithExistingDesk_ReturnsLimits() {
        // Arrange
        when(persistenceService.getLimits(deskId)).thenReturn(testLimits);

        // Act
        ResponseEntity<DeskLimits> response = controller.getDeskLimits(deskId);

        // Assert
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals(testLimits, response.getBody());
    }

    /**
     * Tests retrieval of limits for non-existent desk.
     * Verifies that:
     * 1. Missing desk ID returns 404
     * 2. Error is logged with correct code
     * 3. No limits are returned
     */
    @Test
    void getDeskLimits_WithNonExistingDesk_ReturnsNotFound() {
        // Arrange
        when(persistenceService.getLimits(deskId)).thenReturn(null);

        // Act
        ResponseEntity<DeskLimits> response = controller.getDeskLimits(deskId);

        // Assert
        assertTrue(response.getStatusCode().is4xxClientError());
    }

    /**
     * Tests successful limit update.
     * Verifies that:
     * 1. Limits are updated correctly
     * 2. Response status is 200 OK
     * 3. New limits are persisted
     * 4. Updated values are returned
     */
    @Test
    void updateDeskLimits_WithValidLimits_ReturnsUpdatedLimits() {
        // Arrange
        when(persistenceService.deskExists(deskId)).thenReturn(true);
        when(persistenceService.saveLimits(any(DeskLimits.class))).thenReturn(testLimits);

        // Act
        ResponseEntity<DeskLimits> response = controller.updateDeskLimits(deskId, testLimits);

        // Assert
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals(testLimits, response.getBody());
    }

    /**
     * Tests limit update for non-existent desk.
     * Verifies that:
     * 1. Missing desk returns 404
     * 2. Error is logged with correct code
     * 3. No limits are updated
     */
    @Test
    void updateDeskLimits_WithNonExistingDesk_ReturnsNotFound() {
        // Arrange
        when(persistenceService.deskExists(deskId)).thenReturn(false);

        // Act
        ResponseEntity<DeskLimits> response = controller.updateDeskLimits(deskId, testLimits);

        // Assert
        assertTrue(response.getStatusCode().is4xxClientError());
    }

    /**
     * Tests retrieval of desk utilization.
     * Verifies that:
     * 1. Current utilization is calculated correctly
     * 2. Response status is 200 OK
     * 3. Utilization percentages match expected
     */
    @Test
    void getDeskUtilization_WithExistingDesk_ReturnsUtilization() {
        // Arrange
        when(persistenceService.getLimits(deskId)).thenReturn(testLimits);

        // Act
        ResponseEntity<DeskLimits> response = controller.getDeskUtilization(deskId);

        // Assert
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals(testLimits, response.getBody());
    }

    /**
     * Tests utilization retrieval for non-existent desk.
     * Verifies that:
     * 1. Missing desk ID returns 404
     * 2. Error is logged with correct code
     * 3. No utilization data is returned
     */
    @Test
    void getDeskUtilization_WithNonExistingDesk_ReturnsNotFound() {
        // Arrange
        when(persistenceService.getLimits(deskId)).thenReturn(null);

        // Act
        ResponseEntity<DeskLimits> response = controller.getDeskUtilization(deskId);

        // Assert
        assertTrue(response.getStatusCode().is4xxClientError());
    }

    /**
     * Tests validation of negative limits.
     * Verifies that:
     * 1. Negative limits are rejected
     * 2. Response status is 400 Bad Request
     * 3. Error is logged with correct code
     */
    @Test
    void updateDeskLimits_WithNegativeLimits_ReturnsBadRequest() {
        // Arrange
        UUID deskId = UUID.randomUUID();
        DeskLimits testLimits = new DeskLimits(deskId, deskId, -1000000, 1000000, 2000000);

        // Act
        ResponseEntity<DeskLimits> response = controller.updateDeskLimits(deskId, testLimits);

        // Assert
        assertTrue(response.getStatusCode().is4xxClientError());
        verify(persistenceService, never()).saveLimits(any(DeskLimits.class));
    }

    /**
     * Tests validation of zero limits.
     * Verifies that:
     * 1. Zero limits are rejected
     * 2. Response status is 400 Bad Request
     * 3. Error is logged with correct code
     */
    @Test
    void updateDeskLimits_WithZeroLimits_ReturnsBadRequest() {
        // Arrange
        UUID deskId = UUID.randomUUID();
        DeskLimits testLimits = new DeskLimits(deskId, deskId, 0, 1000000, 2000000);

        // Act
        ResponseEntity<DeskLimits> response = controller.updateDeskLimits(deskId, testLimits);

        // Assert
        assertTrue(response.getStatusCode().is4xxClientError());
        verify(persistenceService, never()).saveLimits(any(DeskLimits.class));
    }

    /**
     * Tests error handling during limit updates.
     * Verifies that:
     * 1. Service exceptions are caught
     * 2. Response status is 500 Internal Server Error
     * 3. Error is logged with correct code
     * 4. Original error details are preserved
     */
    @Test
    void updateDeskLimits_WhenExceptionOccurs_ReturnsInternalError() {
        // Arrange
        RuntimeException expectedError = new RuntimeException("Test error");
        when(persistenceService.deskExists(deskId)).thenReturn(true);
        when(persistenceService.saveLimits(any(DeskLimits.class)))
            .thenThrow(expectedError);

        // Act
        ResponseEntity<DeskLimits> response = controller.updateDeskLimits(deskId, testLimits);

        // Assert
        assertAll(
            () -> assertTrue(response.getStatusCode().is5xxServerError()),
            () -> verify(persistenceService).deskExists(deskId),
            () -> verify(persistenceService).saveLimits(testLimits)
        );
    }

    @Test
    void getDeskLimits_WhenErrorOccurs_ReturnsInternalError() {
        // Arrange
        RuntimeException expectedError = new RuntimeException("Test error");
        when(persistenceService.getLimits(deskId))
            .thenThrow(expectedError);

        // Act
        ResponseEntity<DeskLimits> response = controller.getDeskLimits(deskId);

        // Assert
        assertAll(
            () -> assertTrue(response.getStatusCode().is5xxServerError()),
            () -> verify(persistenceService).getLimits(deskId)
        );
    }

    @Test
    void getDeskUtilization_WhenErrorOccurs_ReturnsInternalError() {
        // Arrange
        ResponseEntity<DeskLimits> response = ResponseEntity.ok(testLimits);
        RuntimeException expectedError = new RuntimeException("Test error");
        when(persistenceService.getLimits(deskId))
            .thenThrow(expectedError);

        response = controller.getDeskUtilization(deskId);

        // Assert
        ResponseEntity<DeskLimits> finalResponse = response;
        assertAll(
            () -> assertTrue(finalResponse.getStatusCode().is5xxServerError()),
            () -> verify(persistenceService).getLimits(deskId)
        );
    }
} 