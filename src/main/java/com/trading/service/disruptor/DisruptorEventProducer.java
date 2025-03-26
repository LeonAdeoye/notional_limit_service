package com.trading.service.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.trading.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DisruptorEventProducer
{
    private static final Logger logger = LoggerFactory.getLogger(DisruptorEventProducer.class);
    private final RingBuffer<OrderEvent> ringBuffer;
    public DisruptorEventProducer(RingBuffer<OrderEvent> ringBuffer)
    {
        this.ringBuffer = ringBuffer;
    }
    public void onData(Order order)
    {
        long sequence  = ringBuffer.next();
        try
        {
            OrderEvent event = ringBuffer.get(sequence);
            event.setOrder(order);
        }
        finally
        {
            ringBuffer.publish(sequence);
        }

    }
}
