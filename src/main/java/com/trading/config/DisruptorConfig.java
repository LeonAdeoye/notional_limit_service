package com.trading.config;

import com.lmax.disruptor.dsl.Disruptor;
import com.trading.service.disruptor.OrderEvent;
import com.trading.service.disruptor.OrderEventFactory;
import com.trading.service.disruptor.OrderEventHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Configuration for LMAX Disruptor components.
 */
@Configuration
public class DisruptorConfig {
    private static final int BUFFER_SIZE = 1024;

    @Bean
    public ThreadFactory disruptorThreadFactory() {
        return Executors.defaultThreadFactory();
    }

    @Bean
    public Disruptor<OrderEvent> disruptor(
            OrderEventFactory eventFactory,
            OrderEventHandler eventHandler,
            ThreadFactory threadFactory) {
        
        Disruptor<OrderEvent> disruptor = new Disruptor<>(
            eventFactory,
            BUFFER_SIZE,
            threadFactory
        );

        disruptor.handleEventsWith(eventHandler);
        return disruptor;
    }
} 