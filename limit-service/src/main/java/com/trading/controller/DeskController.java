package com.trading.controller;

import com.trading.model.Desk;
import com.trading.service.TradingPersistenceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * REST Controller for managing trading desks.
 * Provides endpoints for CRUD operations on desks and their configurations.
 * All operations are audited and error-tracked using MDC context.
 */
@RestController
@RequestMapping("/api/v1/desks")
@RequiredArgsConstructor
@Validated
public class DeskController {
    private static final Logger log = LoggerFactory.getLogger(DeskController.class);
    @Autowired
    private final TradingPersistenceService tradingPersistenceService;

    @PostMapping
    public ResponseEntity<Desk> createDesk(@Valid @RequestBody Desk desk) {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try {
            if (desk.getId() != null && tradingPersistenceService.deskExists(desk.getId())) {
                log.error("ERR-411: Desk already exists with ID: {}", desk.getId());
                return ResponseEntity.badRequest().build();
            }
            
            if (desk.getId() == null) {
                desk.setId(UUID.randomUUID());
            }
            
            Desk savedDesk = tradingPersistenceService.saveDesk(desk);
            log.info("Successfully created desk with ID: {}", savedDesk.getId());
            return new ResponseEntity<>(savedDesk, HttpStatus.CREATED);
        } catch (Exception e) {
            log.error("ERR-412: Unexpected error creating desk", e);
            return ResponseEntity.internalServerError().build();
        } finally {
            MDC.remove("errorId");
        }
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Desk> getDesk(@NotNull @PathVariable UUID id) {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try {
            Desk desk = tradingPersistenceService.getDesk(id);
            if (desk == null) {
                log.error("ERR-413: Desk not found with ID: {}", id);
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(desk);
        } catch (Exception e) {
            log.error("ERR-414: Error retrieving desk: {}", id, e);
            return ResponseEntity.internalServerError().build();
        } finally {
            MDC.remove("errorId");
        }
    }
    
    @GetMapping
    public ResponseEntity<List<Desk>> getAllDesks() {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try {
            return ResponseEntity.ok(tradingPersistenceService.getAllDesks());
        } catch (Exception e) {
            log.error("ERR-415: Error retrieving all desks", e);
            return ResponseEntity.internalServerError().build();
        } finally {
            MDC.remove("errorId");
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDesk(@NotNull @PathVariable UUID id) {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try {
            if (!tradingPersistenceService.deskExists(id)) {
                log.error("ERR-416: Desk not found for deletion: {}", id);
                return ResponseEntity.notFound().build();
            }
            if (tradingPersistenceService.hasTradersForDesk(id)) {
                log.error("ERR-417: Cannot delete desk with ID: {} as it has associated traders", id);
                return ResponseEntity.badRequest().build();
            }
            tradingPersistenceService.deleteDesk(id);
            log.info("Successfully deleted desk: {}", id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("ERR-418: Error deleting desk: {}", id, e);
            return ResponseEntity.internalServerError().build();
        } finally {
            MDC.remove("errorId");
        }
    }
} 