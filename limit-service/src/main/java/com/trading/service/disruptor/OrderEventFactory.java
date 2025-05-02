package com.trading.service.disruptor;

import com.lmax.disruptor.EventFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderEventFactory implements EventFactory<OrderEvent> {
    @Override
    public OrderEvent newInstance() {
        return new OrderEvent();
    }
} 