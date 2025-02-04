package com.trading.controller;

import com.trading.model.DeskLimits;
import com.trading.service.TradingPersistenceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * REST Controller for managing trading limits.
 * Provides endpoints for querying and updating desk trading limits.
 * Monitors and reports limit utilization.
 */
@RestController
@RequestMapping("/api/v1/limits")
@RequiredArgsConstructor
@Validated
public class LimitController {
    private static final Logger log = LoggerFactory.getLogger(LimitController.class);
    @Autowired
    private final TradingPersistenceService persistenceService;
    
    /**
     * Retrieves current limits for a desk.
     * 
     * @param id The desk ID
     * @return Current buy, sell, and gross limits for the desk
     */
    @GetMapping("/desk/{id}")
    public ResponseEntity<DeskLimits> getDeskLimits(@NotNull @PathVariable UUID id) {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try {
            com.trading.model.DeskLimits limits = persistenceService.getLimits(id);
            if (limits == null) {
                log.error("ERR-421: Limits not found for desk: {}", id);
                return ResponseEntity.notFound().build();
            }
            return (ResponseEntity<DeskLimits>) ResponseEntity.ok(limits);
        } catch (Exception e) {
            log.error("ERR-422: Error retrieving desk limits: {}", id, e);
            return ResponseEntity.internalServerError().build();
        } finally {
            MDC.remove("errorId");
        }
    }
    
    @PutMapping("/desk/{id}")
    public ResponseEntity<DeskLimits> updateDeskLimits(
            @NotNull @PathVariable UUID id,
            @Valid @RequestBody DeskLimits limits) {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try {
            // Validate limits are positive
            if (limits.getBuyNotionalLimit() <= 0 || 
                limits.getSellNotionalLimit() <= 0 || 
                limits.getGrossNotionalLimit() <= 0) {
                log.error("ERR-427: Negative or zero limits not allowed for desk: {}", id);
                return ResponseEntity.badRequest().build();
            }

            if (!persistenceService.deskExists(id)) {
                log.error("ERR-423: Desk not found for limit update: {}", id);
                return ResponseEntity.notFound().build();
            }
            
            limits.setId(id);
            limits.setDeskId(id);
            try {
                DeskLimits savedLimits = persistenceService.saveLimits(limits);
                log.info("Successfully updated limits for desk: {}", id);
                return ResponseEntity.ok(savedLimits);
            } catch (Exception e) {
                log.error("ERR-424: Error updating desk limits: {}", id, e.getMessage());
                return ResponseEntity.internalServerError().build();
            }
        } finally {
            MDC.remove("errorId");
        }
    }
    
    @GetMapping("/desk/{id}/utilization")
    public ResponseEntity<DeskLimits> getDeskUtilization(@NotNull @PathVariable UUID id) {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try {
            DeskLimits limits = persistenceService.getLimits(id);
            if (limits == null) {
                log.error("ERR-425: Limits not found for utilization query: {}", id);
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(limits);
        } catch (Exception e) {
            log.error("ERR-426: Error retrieving desk utilization: {}", id, e);
            return ResponseEntity.internalServerError().build();
        } finally {
            MDC.remove("errorId");
        }
    }
} 