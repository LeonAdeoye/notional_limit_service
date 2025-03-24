package com.trading.service;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.trading.model.Order;
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
import java.util.UUID;
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
    private final OrderEventFactory orderEventFactory;
    @Autowired
    private final OrderEventHandler orderEventHandler;
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
            if(!isValidOrder(order)) {
                log.error("Invalid order: {}", order);
                return;
            }

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
     * returns true if the order is valid, false otherwise.
     */
    private static boolean isValidOrder(Order order) {
        if (order.getQuantity() <= 0)
            return false;
        if (order.getPrice() <= 0)
            return false;
        return true;
    }
} 