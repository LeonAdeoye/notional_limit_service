package com.trading.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.crankuptheamps.client.Client;
import org.mockito.Mockito;
import com.trading.service.disruptor.OrderEventFactory;
import com.trading.service.disruptor.OrderEventHandler;
import com.trading.service.disruptor.OrderEvent;
import com.trading.messaging.AmpsMessageProcessor;
import com.trading.service.CurrencyManager;
import com.trading.service.TradingPersistenceService;
import com.lmax.disruptor.dsl.Disruptor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Test configuration for providing mock beans in unit tests.
 */
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public ObjectMapper testObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @Primary
    public Client testAmpsClient() {
        return Mockito.mock(Client.class);
    }

    @Bean
    @Primary
    public OrderEventFactory testOrderEventFactory() {
        return new OrderEventFactory();
    }

    @Bean
    @Primary
    public OrderEventHandler testOrderEventHandler(
            TradingPersistenceService persistenceService,
            CurrencyManager currencyManager,
            AmpsMessageProcessor ampsMessageProcessor) {
        return new OrderEventHandler(persistenceService, currencyManager, ampsMessageProcessor);
    }

    @Bean
    @Primary
    public Disruptor<OrderEvent> testDisruptor(
            OrderEventFactory eventFactory,
            OrderEventHandler eventHandler) {
        ThreadFactory threadFactory = Executors.defaultThreadFactory();
        Disruptor<OrderEvent> disruptor = new Disruptor<>(
            eventFactory,
            1024,
            threadFactory
        );
        disruptor.handleEventsWith(eventHandler);
        return disruptor;
    }
} 