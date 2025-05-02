package com.trading.service.disruptor;

import com.trading.model.Order;
import lombok.Data;
import org.springframework.stereotype.Component;

@Component
@Data
public class OrderEvent {
    private Order order;
    private String errorId;
} 