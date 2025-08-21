package com.trading.controller;

import com.trading.model.TraderNotionalLimit;
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

@RestController
@RequestMapping("/traders")
@RequiredArgsConstructor
@Validated
public class TraderController {
    private static final Logger log = LoggerFactory.getLogger(TraderController.class);
    @Autowired
    private final TradingPersistenceService persistenceService;
    
    @GetMapping("/{id}")
    public ResponseEntity<TraderNotionalLimit> getTrader(@NotNull @PathVariable UUID id)
    {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try
        {
            TraderNotionalLimit trader = persistenceService.getTraderNotionalLimit(id);
            if (trader == null)
            {
                log.error("ERR-404: Trader not found with ID: {}", id);
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(trader);
        }
        catch (Exception e)
        {
            log.error("ERR-405: Error retrieving trader: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
        finally
        {
            MDC.remove("errorId");
        }
    }

    @GetMapping()
    public ResponseEntity<List<TraderNotionalLimit>> getAllTradersNotionalLimits()
    {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);

        try
        {
            List<TraderNotionalLimit> traders = persistenceService.getAllTraderNotionalLimits();
            return ResponseEntity.ok(traders);
        }
        catch (Exception e)
        {
            log.error("ERR-405: Error retrieving all traders.", e);
            return ResponseEntity.internalServerError().build();
        }
        finally
        {
            MDC.remove("errorId");
        }
    }
    
    @GetMapping("/desk/{deskId}")
    public ResponseEntity<List<TraderNotionalLimit>> getDeskTraderNotionalLimitsByDeskId(@NotNull @PathVariable UUID deskId)
    {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try
        {
            if (!persistenceService.deskNotionalLimitExists(deskId))
            {
                log.error("ERR-406: Desk not found for trader lookup: {}", deskId);
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(persistenceService.getDeskTraderNotionalLimits(deskId));
        }
        catch (Exception e)
        {
            log.error("ERR-407: Error retrieving traders for desk: {}", deskId, e);
            return ResponseEntity.internalServerError().build();
        }
        finally
        {
            MDC.remove("errorId");
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTrader(@NotNull @PathVariable UUID id)
    {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try
        {
            if (!persistenceService.traderNotionalLimitExists(id))
            {
                log.error("ERR-408: Trader not found for deletion: {}", id);
                return ResponseEntity.notFound().build();
            }
            persistenceService.deleteTraderNotionalLimit(id);
            log.info("Successfully deleted trader: {}", id);
            return ResponseEntity.ok().build();
        }
        catch (Exception e)
        {
            log.error("ERR-409: Error deleting trader: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
        finally
        {
            MDC.remove("errorId");
        }
    }
} 