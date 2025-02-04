package com.trading.service.disruptor;

import com.trading.model.Order;
import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * Event class for the LMAX Disruptor that holds order information and context.
 * This class represents the data that flows through the Disruptor ring buffer.
 */
@Component
@Data
public class OrderEvent {
    // The order to be processed
    private Order order;
    
    // Unique identifier for error tracking and logging
    private String errorId;
} 