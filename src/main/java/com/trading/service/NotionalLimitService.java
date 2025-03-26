package com.trading.service;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.trading.model.Order;
import com.trading.service.disruptor.DisruptorService;
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
    DisruptorService disruptorService;

    @PostConstruct
    public void initialize() {
        disruptorService.start("NotionalLimitService", orderEventHandler);
    }

    @PreDestroy
    public void shutdown() {
        disruptorService.stop();
    }

    public void processOrder(Order order) {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try {
            if(!isValidOrder(order)) {
                log.error("Invalid order: {}", order);
                return;
            }
            disruptorService.push(order);
        } finally {
            MDC.remove("errorId");
        }
    }
    
    private static boolean isValidOrder(Order order) {
        if (order.quantity() <= 0)
            return false;
        if (order.price() <= 0)
            return false;
        return true;
    }
} 