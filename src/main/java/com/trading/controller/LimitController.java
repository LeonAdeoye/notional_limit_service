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

    @GetMapping("/desk/{id}")
    public ResponseEntity<DeskLimits> getDeskLimits(@NotNull @PathVariable UUID deskLimitsId) {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try {
            DeskLimits deskLimits = persistenceService.getLimits(deskLimitsId);
            if (deskLimits == null) {
                log.error("ERR-421: Limits not found for desk: {}", deskLimitsId);
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(deskLimits);
        } catch (Exception e) {
            log.error("ERR-422: Error retrieving desk limits: {}", deskLimitsId, e);
            return ResponseEntity.internalServerError().build();
        } finally {
            MDC.remove("errorId");
        }
    }
    
    @PutMapping("/desk/{id}")
    public ResponseEntity<DeskLimits> updateDeskLimits(
            @NotNull @PathVariable UUID deskId,
            @Valid @RequestBody DeskLimits deskLimits) {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try {
            if (deskLimits.buyNotionalLimit() <= 0 ||
                deskLimits.sellNotionalLimit() <= 0 ||
                deskLimits.grossNotionalLimit() <= 0) {
                log.error("ERR-427: Negative or zero limits not allowed for desk: {}", deskId);
                return ResponseEntity.badRequest().build();
            }

            if (!persistenceService.deskExists(deskId)) {
                log.error("ERR-423: Desk not found for limit update: {}", deskId);
                return ResponseEntity.notFound().build();
            }

            try {
                DeskLimits savedLimits = persistenceService.saveLimits(new DeskLimits(deskId, deskId,
                        deskLimits.buyNotionalLimit(), deskLimits.sellNotionalLimit(), deskLimits.grossNotionalLimit()));
                log.info("Successfully updated limits for desk: {}", deskId);
                return ResponseEntity.ok(savedLimits);
            } catch (Exception e) {
                log.error("ERR-424: Error updating desk limits: {}", deskId, e.getMessage());
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