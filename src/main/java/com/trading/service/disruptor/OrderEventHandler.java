package com.trading.service.disruptor;

import com.lmax.disruptor.EventHandler;
import com.trading.messaging.AmpsMessageOutboundProcessor;
import com.trading.model.Desk;
import com.trading.model.Order;
import com.trading.model.Trader;
import com.trading.model.TradeSide;
import com.trading.service.CurrencyManager;
import com.trading.service.TradingPersistenceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Event handler for processing order events in the Disruptor.
 * Handles the actual business logic of processing orders and updating limits.
 * Ensures sequential processing of orders for thread safety.
 */
@Component
@RequiredArgsConstructor
public class OrderEventHandler implements EventHandler<OrderEvent> {
    private static final Logger log = LoggerFactory.getLogger(OrderEventHandler.class);
    
    @Autowired
    private final TradingPersistenceService persistenceService;
    
    @Autowired
    private final CurrencyManager currencyManager;

    @Autowired
    private final AmpsMessageOutboundProcessor ampsMessageOutboundProcessor;
    /**
     * Main event handling method called by the Disruptor.
     * Processes each order event in sequence.
     */
    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        try {
            // Set error context for logging
            MDC.put("errorId", event.getErrorId());
            processOrder(event.getOrder());
        } finally {
            // Clean up logging context
            MDC.remove("errorId");
        }
    }

    /**
     * Processes a single order by validating trader/desk and updating limits.
     * @throws IllegalArgumentException if trader or desk not found
     */
    private void processOrder(Order order) {
        // Validate and retrieve trader
        Trader trader = persistenceService.getTrader(order.traderId());
        if (trader == null) {
            log.error("ERR-001: Trader not found with ID: {}", order.traderId());
            throw new IllegalArgumentException("Trader not found");
        }

        // Validate and retrieve desk
        Desk desk = persistenceService.getDesk(trader.deskId());
        if (desk == null) {
            log.error("ERR-002: Desk not found with ID: {}", trader.deskId());
            throw new IllegalArgumentException("Desk not found");
        }

        // Calculate notional value and update limits
        double notionalValueUSD = calculateUSDNotional(order);
        updateDeskLimits(desk, order.side(), notionalValueUSD);
        checkLimitBreaches(desk);
        
        // Persist updated desk state
        persistenceService.saveDesk(desk);
        log.info("Successfully processed order for trader: {}, desk: {}", trader.id(), desk.getId());
    }

    /**
     * Converts order notional value to USD using current FX rates.
     */
    private double calculateUSDNotional(Order order) {
        double localNotional = order.getNotionalValue();
        return currencyManager.convertToUSD(localNotional, order.currency());
    }

    /**
     * Updates desk limits based on order side and notional value.
     */
    private void updateDeskLimits(Desk desk, TradeSide side, double notionalValueUSD) {
        if (side == TradeSide.BUY) {
            desk.setCurrentBuyNotional(desk.getCurrentBuyNotional() + notionalValueUSD);
            log.debug("Updated buy notional for desk: {} to: {} using: {} USD", desk.getId(), desk.getCurrentBuyNotional(), notionalValueUSD);
        } else if (side == TradeSide.SELL) {
            desk.setCurrentSellNotional(desk.getCurrentSellNotional() + notionalValueUSD);
            log.debug("Updated sell notional for desk: {} to: {} using: {} USD", desk.getId(), desk.getCurrentSellNotional(), notionalValueUSD);
        }
    }

    /**
     * Checks for and publishes any limit breaches.
     * Monitors buy, sell, and gross notional limits.
     */
    private void checkLimitBreaches(Desk desk) {
        // Check buy limit breach
        if (desk.getBuyUtilizationPercentage() > 100) {
            String message = String.format("ERR-004: Buy limit breached for desk: %s", desk.getId());
            log.error(message);
            ampsMessageOutboundProcessor.publishLimitBreach(message);
        }
        
        // Check sell limit breach
        if (desk.getSellUtilizationPercentage() > 100) {
            String message = String.format("ERR-005: Sell limit breached for desk: %s", desk.getId());
            log.error(message);
            ampsMessageOutboundProcessor.publishLimitBreach(message);
        }
        
        // Check gross limit breach
        if (desk.getGrossUtilizationPercentage() > 100) {
            String message = String.format("ERR-006: Gross limit breached for desk: %s", desk.getId());
            log.error(message);
            ampsMessageOutboundProcessor.publishLimitBreach(message);
        }
    }
} 