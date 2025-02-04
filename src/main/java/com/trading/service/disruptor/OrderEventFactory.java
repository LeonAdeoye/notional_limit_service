package com.trading.service.disruptor;

import com.lmax.disruptor.EventFactory;
import org.springframework.stereotype.Component;

/**
 * Factory class for creating new OrderEvent instances.
 * Required by the LMAX Disruptor to pre-populate the ring buffer.
 */
@Component
public class OrderEventFactory implements EventFactory<OrderEvent> {
    
    /**
     * Creates a new OrderEvent instance.
     * Called by the Disruptor to pre-populate the ring buffer.
     */
    @Override
    public OrderEvent newInstance() {
        return new OrderEvent();
    }
} 