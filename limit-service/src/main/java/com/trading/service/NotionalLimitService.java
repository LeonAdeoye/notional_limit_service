package com.trading.service;

import com.trading.service.disruptor.DisruptorService;
import com.trading.service.disruptor.OrderEventHandler;
import com.trading.model.Order;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotionalLimitService {
    private static final Logger log = LoggerFactory.getLogger(NotionalLimitService.class);
    private static int countOfOrders = 0;
    @Autowired
    private final OrderEventHandler orderEventHandler;
    @Autowired
    private DisruptorService disruptorService;
    @Autowired
    private final InitializationService initializationService;
    @PostConstruct
    public void initialize() {
        disruptorService.start("NotionalLimitService", orderEventHandler);
    }
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down NotionalLimitService. Total orders processed: {}", countOfOrders);
        disruptorService.stop();
    }

    public void processOrder(Order order) {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        try {
            countOfOrders++;
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
        if (order.getQuantity() <= 0)
            return false;
        if (order.getPrice() <= 0)
            return false;
        return true;
    }
} 