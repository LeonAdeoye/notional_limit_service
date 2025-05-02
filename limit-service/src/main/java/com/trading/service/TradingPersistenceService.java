package com.trading.service;

import com.trading.model.Trader;
import com.trading.repository.DeskRepository;
import com.trading.repository.TraderRepository;
import com.trading.model.Desk;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.ArrayList;


@Service
@RequiredArgsConstructor
public class TradingPersistenceService {
    private static final Logger log = LoggerFactory.getLogger(TradingPersistenceService.class);

    @Autowired
    private final DeskRepository deskRepository;
    @Autowired
    private final TraderRepository traderRepository;
    
    // In-memory caches
    private final Map<UUID, Desk> deskCache = new ConcurrentHashMap<>();
    private final Map<UUID, Trader> traderCache = new ConcurrentHashMap<>();
    private final Map<UUID, List<Trader>> deskTradersCache = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initializeCaches() {
        log.info("Initializing trading data caches from MongoDB");
        try {
            List<Desk> desks = deskRepository.findAll();
            deskCache.clear();
            desks.forEach(desk -> {
                deskCache.put(desk.getId(), desk);
                log.info("Loaded desk: {} into cache", desk);
            });
            log.info("Loaded {} desks into cache", desks.size());

            List<Trader> traders = traderRepository.findAll();
            traderCache.clear();
            traders.forEach(trader -> {
                traderCache.put(trader.getId(), trader);
                log.info("Loaded trader: {} into cache", trader);
            });

            deskTradersCache.clear();
            deskTradersCache.putAll(traders.stream()
                .collect(Collectors.groupingBy(Trader::getDeskId)));
            log.info("Loaded {} traders into cache", traders.size());
            
        } catch (Exception e) {
            log.error("ERR-201: Failed to initialize caches from MongoDB", e);
            throw new RuntimeException("Failed to initialize trading data", e);
        }
    }
    
    @Transactional
    public Desk saveDesk(Desk desk) {
        try {
            Desk savedDesk = deskRepository.save(desk);
            deskCache.put(savedDesk.getId(), savedDesk);
            log.info("Saved desk with ID: {} to MongoDB and cache", savedDesk.getId());
            return savedDesk;
        } catch (Exception e) {
            log.error("ERR-202: Failed to save desk: {}", desk.getId(), e);
            throw e;
        }
    }
    
    @Transactional
    public Trader saveTrader(Trader trader) {
        try {
            Trader savedTrader = traderRepository.save(trader);
            traderCache.put(savedTrader.getId(), savedTrader);
            
            // Update desk-traders cache
            deskTradersCache.computeIfAbsent(savedTrader.getDeskId(), k -> new java.util.ArrayList<>())
                .add(savedTrader);
            
            log.info("Saved trader with ID: {} to MongoDB and cache", savedTrader.getId());
            return savedTrader;
        } catch (Exception e) {
            log.error("ERR-203: Failed to save trader: {}", trader.getId(), e);
            throw e;
        }
    }
    
    @Transactional
    public void deleteDesk(UUID deskId) {
        try {
            deskRepository.deleteById(deskId);
            deskCache.remove(deskId);
            deskTradersCache.remove(deskId);
            log.info("Deleted desk and its limits with ID: {} from MongoDB and cache", deskId);
        } catch (Exception e) {
            log.error("ERR-204: Failed to delete desk: {}", deskId, e);
            throw e;
        }
    }
    
    @Transactional
    public void deleteTrader(UUID traderId) {
        try {
            Trader trader = traderCache.get(traderId);
            if (trader != null) {
                deskTradersCache.getOrDefault(trader.getDeskId(), new java.util.ArrayList<>())
                    .removeIf(t -> t.getId().equals(traderId));
            }
            
            traderRepository.deleteById(traderId);
            traderCache.remove(traderId);
            log.info("Deleted trader with ID: {} from MongoDB and cache", traderId);
        } catch (Exception e) {
            log.error("ERR-205: Failed to delete trader: {}", traderId, e);
            throw e;
        }
    }
    
    public Desk getDesk(UUID deskId) {
        return deskCache.get(deskId);
    }
    
    public Trader getTrader(UUID traderId) {
        return traderCache.get(traderId);
    }
    
    public List<Trader> getDeskTraders(UUID deskId) {
        return deskTradersCache.getOrDefault(deskId, new java.util.ArrayList<>());
    }
    
    public boolean deskExists(UUID deskId) {
        return deskCache.containsKey(deskId);
    }
    
    public boolean traderExists(UUID traderId) {
        return traderCache.containsKey(traderId);
    }
    
    public List<Desk> getAllDesks() {
        return new ArrayList<>(deskCache.values());
    }
    
    public boolean hasTradersForDesk(UUID deskId) {
        return !getDeskTraders(deskId).isEmpty();
    }

    public List<Trader> getAllTraders() {
        return new ArrayList<>(traderCache.values());
    }

    public Desk getDeskById(UUID deskId) {
        return deskCache.get(deskId);
    }
}