package com.trading.service;

import com.trading.model.Desk;
import com.trading.model.Trader;
import com.trading.model.DeskLimits;
import com.trading.repository.DeskRepository;
import com.trading.repository.TraderRepository;
import com.trading.repository.DeskLimitsRepository;
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
    @Autowired
    private final DeskLimitsRepository limitsRepository;
    
    // In-memory caches
    private final Map<UUID, Desk> deskCache = new ConcurrentHashMap<>();
    private final Map<UUID, Trader> traderCache = new ConcurrentHashMap<>();
    private final Map<UUID, List<Trader>> deskTradersCache = new ConcurrentHashMap<>();
    private final Map<UUID, DeskLimits> limitsCache = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initializeCaches() {
        log.info("Initializing trading data caches from MongoDB");
        try {
            // Load all desks
            List<Desk> desks = deskRepository.findAll();
            deskCache.clear();
            desks.forEach(desk -> deskCache.put(desk.getId(), desk));
            log.info("Loaded {} desks into cache", desks.size());
            
            // Load all traders
            List<Trader> traders = traderRepository.findAll();
            traderCache.clear();
            traders.forEach(trader -> traderCache.put(trader.id(), trader));
            
            // Group traders by desk
            deskTradersCache.clear();
            deskTradersCache.putAll(traders.stream()
                .collect(Collectors.groupingBy(Trader::deskId)));
            log.info("Loaded {} traders into cache", traders.size());
            
            // Load all limits
            List<DeskLimits> limits = limitsRepository.findAll();
            limitsCache.clear();
            limits.forEach(limit -> limitsCache.put(limit.deskId(), limit));
            log.info("Loaded {} desk limits into cache", limits.size());
            
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
            
            // Initialize limits if they don't exist
            if (!limitsExist(savedDesk.getId()))
                saveLimits(new DeskLimits(savedDesk.getId(), savedDesk.getId(), desk.getBuyNotionalLimit(),
                        desk.getSellNotionalLimit(), desk.getGrossNotionalLimit()));
            
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
            traderCache.put(savedTrader.id(), savedTrader);
            
            // Update desk-traders cache
            deskTradersCache.computeIfAbsent(savedTrader.deskId(), k -> new java.util.ArrayList<>())
                .add(savedTrader);
            
            log.info("Saved trader with ID: {} to MongoDB and cache", savedTrader.id());
            return savedTrader;
        } catch (Exception e) {
            log.error("ERR-203: Failed to save trader: {}", trader.id(), e);
            throw e;
        }
    }
    
    @Transactional
    public void deleteDesk(UUID deskId) {
        try {
            deskRepository.deleteById(deskId);
            deskCache.remove(deskId);
            deskTradersCache.remove(deskId);
            deleteLimits(deskId);
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
                deskTradersCache.getOrDefault(trader.deskId(), new java.util.ArrayList<>())
                    .removeIf(t -> t.id().equals(traderId));
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
    
    @Transactional
    public DeskLimits saveLimits(DeskLimits limits) {
        try {
            DeskLimits savedLimits = limitsRepository.save(limits);
            limitsCache.put(savedLimits.deskId(), savedLimits);
            log.info("Saved limits for desk ID: {} to MongoDB and cache", savedLimits.deskId());
            return savedLimits;
        } catch (Exception e) {
            log.error("ERR-206: Failed to save limits for desk: {}", limits.deskId(), e);
            throw e;
        }
    }
    
    public DeskLimits getLimits(UUID deskId) {
        return limitsCache.get(deskId);
    }
    
    public boolean limitsExist(UUID deskId) {
        return limitsCache.containsKey(deskId);
    }
    
    @Transactional
    public void deleteLimits(UUID deskId) {
        try {
            limitsRepository.deleteById(deskId);
            limitsCache.remove(deskId);
            log.info("Deleted limits for desk ID: {} from MongoDB and cache", deskId);
        } catch (Exception e) {
            log.error("ERR-207: Failed to delete limits for desk: {}", deskId, e);
            throw e;
        }
    }
} 