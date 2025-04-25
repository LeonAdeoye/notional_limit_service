package com.trading.service;

import com.trading.messaging.AmpsMessageOutboundProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
public class InitializationService {
    @Autowired
    AmpsMessageOutboundProcessor ampsMessageOutboundProcessor;

    @PostConstruct
    public void initialize() {
        ampsMessageOutboundProcessor.publishInitialMessage("");
    }

}
