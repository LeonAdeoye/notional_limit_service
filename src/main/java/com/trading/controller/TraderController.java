package com.trading.controller;

import com.trading.model.Trader;
import com.trading.service.TradingPersistenceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * REST Controller for managing traders.
 * Provides endpoints for CRUD operations on traders and their desk assignments.
 * Ensures proper validation of trader-desk relationships.
 */
@RestController
@RequestMapping("/api/v1/traders")
@RequiredArgsConstructor
@Validated
public class TraderController {
    private static final Logger log = LoggerFactory.getLogger(TraderController.class);
    @Autowired
    private final TradingPersistenceService persistenceService;

    @PostMapping
    public ResponseEntity<Trader> createTrader(@Valid @RequestBody Trader trader) {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try {
            // Validate trader doesn't exist
            if (trader.getId() != null && persistenceService.traderExists(trader.getId())) {
                log.error("ERR-401: Trader already exists with ID: {}", trader.getId());
                return ResponseEntity.badRequest().build();
            }
            
            // Validate desk exists
            if (!persistenceService.deskExists(trader.getDeskId())) {
                log.error("ERR-402: Referenced desk not found with ID: {}", trader.getDeskId());
                return ResponseEntity.badRequest().build();
            }
            
            // Generate new ID if not provided
            if (trader.getId() == null) {
                trader.setId(UUID.randomUUID());
            }
            
            // Save and return new trader
            Trader savedTrader = persistenceService.saveTrader(trader);
            log.info("Successfully created trader with ID: {} for desk: {}", 
                    savedTrader.getId(), savedTrader.getDeskId());
            return new ResponseEntity<Trader>(savedTrader, HttpStatus.CREATED);
        } catch (Exception e) {
            log.error("ERR-403: Unexpected error creating trader", e);
            return ResponseEntity.internalServerError().build();
        } finally {
            MDC.remove("errorId");
        }
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Trader> getTrader(@NotNull @PathVariable UUID id) {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try {
            Trader trader = persistenceService.getTrader(id);
            if (trader == null) {
                log.error("ERR-404: Trader not found with ID: {}", id);
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(trader);
        } catch (Exception e) {
            log.error("ERR-405: Error retrieving trader: {}", id, e);
            return ResponseEntity.internalServerError().build();
        } finally {
            MDC.remove("errorId");
        }
    }
    
    @GetMapping("/desk/{deskId}")
    public ResponseEntity<List<Trader>> getTradersByDesk(@NotNull @PathVariable UUID deskId) {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try {
            if (!persistenceService.deskExists(deskId)) {
                log.error("ERR-406: Desk not found for trader lookup: {}", deskId);
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(persistenceService.getDeskTraders(deskId));
        } catch (Exception e) {
            log.error("ERR-407: Error retrieving traders for desk: {}", deskId, e);
            return ResponseEntity.internalServerError().build();
        } finally {
            MDC.remove("errorId");
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTrader(@NotNull @PathVariable UUID id) {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try {
            if (!persistenceService.traderExists(id)) {
                log.error("ERR-408: Trader not found for deletion: {}", id);
                return ResponseEntity.notFound().build();
            }
            persistenceService.deleteTrader(id);
            log.info("Successfully deleted trader: {}", id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("ERR-409: Error deleting trader: {}", id, e);
            return ResponseEntity.internalServerError().build();
        } finally {
            MDC.remove("errorId");
        }
    }
} 