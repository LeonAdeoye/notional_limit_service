package com.trading.service.disruptor;

import com.lmax.disruptor.EventHandler;
import com.trading.model.Order;

public interface DisruptorService
{
    void start(String name, EventHandler<OrderEvent> actionEventHandler);
    void stop();
    void push(Order order);
}