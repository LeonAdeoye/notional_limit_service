package com.trading.service;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.trading.messaging.AmpsMessageProcessor;
import com.trading.model.Desk;
import com.trading.model.Order;
import com.trading.model.TradeSide;
import com.trading.model.Trader;
import com.trading.repository.DeskRepository;
import com.trading.repository.TraderRepository;
import com.trading.service.disruptor.OrderEvent;
import com.trading.service.disruptor.OrderEventFactory;
import com.trading.service.disruptor.OrderEventHandler;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Service responsible for managing notional limits using LMAX Disruptor.
 * Provides high-performance, sequential processing of orders.
 */
@Service
@RequiredArgsConstructor
public class NotionalLimitService {
    private static final Logger log = LoggerFactory.getLogger(NotionalLimitService.class);
    
    @Value("${app.disruptor.buffer-size:1024}")
    private int bufferSize=1024;
    
    @Autowired
    private final TradingPersistenceService persistenceService;
    
    @Autowired
    private final CurrencyManager currencyManager;
    
    @Autowired
    private final AmpsMessageProcessor ampsMessageProcessor;
    
    @Autowired
    private final OrderEventFactory orderEventFactory;
    
    @Autowired
    private final OrderEventHandler orderEventHandler;
    
    // Disruptor components
    @Autowired
    private Disruptor<OrderEvent> disruptor;
    @Autowired
    private RingBuffer<OrderEvent> ringBuffer;

    /**
     * Initializes the Disruptor with a single event handler.
     * Called automatically after bean construction.
     */
    @PostConstruct
    public void initialize() {
        ThreadFactory threadFactory = Executors.defaultThreadFactory();
        
        disruptor = new Disruptor<>(
            orderEventFactory,
            bufferSize,
            threadFactory
        );
        
        disruptor.handleEventsWith(orderEventHandler);
        ringBuffer = disruptor.start();
    }

    /**
     * Shuts down the Disruptor gracefully.
     * Called automatically before bean destruction.
     */
    @PreDestroy
    public void shutdown() {
        if (disruptor != null) {
            disruptor.shutdown();
        }
    }

    /**
     * Entry point for processing orders.
     * Validates order and publishes to Disruptor ring buffer.
     */
    public void processOrder(Order order) {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try {
            // Validate order before publishing
            validateOrder(order);
            
            // Publish order to ring buffer
            long sequence = ringBuffer.next();
            try {
                OrderEvent event = ringBuffer.get(sequence);
                event.setOrder(order);
                event.setErrorId(errorId);
            } finally {
                ringBuffer.publish(sequence);
            }
        } finally {
            MDC.remove("errorId");
        }
    }
    
    /**
     * Validates basic order properties before processing.
     * @throws IllegalArgumentException if validation fails
     */
    private void validateOrder(Order order) {
        if (order.getQuantity() <= 0) {
            throw new IllegalArgumentException("Invalid order quantity");
        }
        if (order.getPrice() <= 0) {
            throw new IllegalArgumentException("Invalid order price");
        }
    }
    
    private double calculateUSDNotional(Order order) {
        double localNotional = order.getNotionalValue();
        double usdNotional = currencyManager.convertToUSD(localNotional, order.getCurrency());
        log.debug("Converted notional value from {} {} to {} USD", 
                localNotional, order.getCurrency(), usdNotional);
        return usdNotional;
    }
    
    private void updateDeskLimits(Desk desk, TradeSide side, double notionalValueUSD) {
        if (side == TradeSide.BUY) {
            desk.setCurrentBuyNotional(desk.getCurrentBuyNotional() + notionalValueUSD);
            log.debug("Updated buy notional for desk: {} to: {}", desk.getId(), desk.getCurrentBuyNotional());
        } else {
            desk.setCurrentSellNotional(desk.getCurrentSellNotional() + notionalValueUSD);
            log.debug("Updated sell notional for desk: {} to: {}", desk.getId(), desk.getCurrentSellNotional());
        }
    }
    
    private void checkLimitBreaches(Desk desk) {
        if (desk.getBuyUtilizationPercentage() > 100) {
            String message = String.format("ERR-004: Buy limit breached for desk: %s", desk.getId());
            log.error(message);
            ampsMessageProcessor.publishLimitBreach(message);
        }
        if (desk.getSellUtilizationPercentage() > 100) {
            String message = String.format("ERR-005: Sell limit breached for desk: %s", desk.getId());
            log.error(message);
            ampsMessageProcessor.publishLimitBreach(message);
        }
        if (desk.getGrossUtilizationPercentage() > 100) {
            String message = String.format("ERR-006: Gross limit breached for desk: %s", desk.getId());
            log.error(message);
            ampsMessageProcessor.publishLimitBreach(message);
        }
    }
} 