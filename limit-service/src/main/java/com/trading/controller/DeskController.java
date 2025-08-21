package com.trading.controller;

import com.trading.model.DeskNotionalLimit;
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

@RestController
@RequestMapping("desks")
@RequiredArgsConstructor
@Validated
public class DeskController
{
    private static final Logger log = LoggerFactory.getLogger(DeskController.class);
    @Autowired
    private final TradingPersistenceService tradingPersistenceService;

    @GetMapping("/{id}")
    public ResponseEntity<DeskNotionalLimit> getDesk(@NotNull @PathVariable UUID id)
    {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try
        {
            DeskNotionalLimit deskNotionalLimit = tradingPersistenceService.getDeskNotionalLimit(id);
            if (deskNotionalLimit == null)
            {
                log.error("ERR-413: Desk not found with ID: {}", id);
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(deskNotionalLimit);
        }
        catch (Exception e)
        {
            log.error("ERR-414: Error retrieving desk: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
        finally
        {
            MDC.remove("errorId");
        }
    }
    
    @GetMapping
    public ResponseEntity<List<DeskNotionalLimit>> getAllDesks()
    {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try
        {
            return ResponseEntity.ok(tradingPersistenceService.getAllDeskNotionalLimits());
        }
        catch (Exception e)
        {
            log.error("ERR-415: Error retrieving all desks", e);
            return ResponseEntity.internalServerError().build();
        }
        finally
        {
            MDC.remove("errorId");
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDesk(@NotNull @PathVariable UUID id)
    {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try
        {
            if (!tradingPersistenceService.deskNotionalLimitExists(id))
            {
                log.error("ERR-416: Desk not found for deletion: {}", id);
                return ResponseEntity.notFound().build();
            }
            if (tradingPersistenceService.hasTradersForDesk(id))
            {
                log.error("ERR-417: Cannot delete desk with ID: {} as it has associated traders", id);
                return ResponseEntity.badRequest().build();
            }
            tradingPersistenceService.deleteDeskNotionalLimit(id);
            log.info("Successfully deleted desk: {}", id);
            return ResponseEntity.ok().build();
        }
        catch (Exception e)
        {
            log.error("ERR-418: Error deleting desk: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
        finally
        {
            MDC.remove("errorId");
        }
    }
} 