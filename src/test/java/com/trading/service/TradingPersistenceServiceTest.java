package com.trading.service;

import com.trading.model.Desk;
import com.trading.model.DeskLimits;
import com.trading.model.Trader;
import com.trading.repository.DeskLimitsRepository;
import com.trading.repository.DeskRepository;
import com.trading.repository.TraderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradingPersistenceServiceTest {

    @Mock
    private DeskRepository deskRepository;
    
    @Mock
    private TraderRepository traderRepository;
    
    @Mock
    private DeskLimitsRepository limitsRepository;

    @InjectMocks
    private TradingPersistenceService service;

    private Desk testDesk;
    private Trader testTrader;
    private DeskLimits testLimits;
    private UUID deskId;
    private UUID traderId;

    @BeforeEach
    void setUp() {
        deskId = UUID.randomUUID();
        traderId = UUID.randomUUID();
        
        testDesk = new Desk();
        testDesk.setId(deskId);
        testDesk.setName("Test Desk");

        testTrader = new Trader(traderId, "Test Trader", deskId);

        testLimits = new DeskLimits(deskId, deskId, 1000000, 1000000, 2000000);
    }

    @Test
    void initializeCaches_LoadsAllData() {
        when(deskRepository.findAll()).thenReturn(Arrays.asList(testDesk));
        when(traderRepository.findAll()).thenReturn(Arrays.asList(testTrader));
        when(limitsRepository.findAll()).thenReturn(Arrays.asList(testLimits));

        service.initializeCaches();

        assertTrue(service.deskExists(deskId));
        assertTrue(service.traderExists(traderId));
        assertTrue(service.limitsExist(deskId));
    }

    @Test
    void saveDesk_CreatesNewDeskWithLimits() {
        when(deskRepository.save(any(Desk.class))).thenReturn(testDesk);
        when(limitsRepository.save(any(DeskLimits.class))).thenReturn(testLimits);

        Desk savedDesk = service.saveDesk(testDesk);

        assertNotNull(savedDesk);
        assertEquals(deskId, savedDesk.getId());
        verify(limitsRepository).save(any(DeskLimits.class));
    }

    @Test
    void saveTrader_UpdatesDeskTradersCache() {
        when(traderRepository.save(any(Trader.class))).thenReturn(testTrader);

        Trader savedTrader = service.saveTrader(testTrader);

        assertNotNull(savedTrader);
        assertEquals(traderId, savedTrader.id());
        List<Trader> deskTraders = service.getDeskTraders(deskId);
        assertTrue(deskTraders.contains(savedTrader));
    }

    @Test
    void deleteDesk_RemovesAllRelatedData() {
        // Arrange
        when(traderRepository.save(any(Trader.class))).thenReturn(testTrader);
        when(limitsRepository.save(any(DeskLimits.class))).thenReturn(testLimits);
        when(deskRepository.save(any(Desk.class))).thenReturn(testDesk);

        service.saveDesk(testDesk);
        service.saveTrader(testTrader);
        service.saveLimits(testLimits);

        // Act
        service.deleteDesk(deskId);

        // Assert
        verify(deskRepository).deleteById(deskId);
        verify(limitsRepository).deleteById(deskId);
        assertFalse(service.deskExists(deskId));
        assertTrue(service.getDeskTraders(deskId).isEmpty());
        assertFalse(service.limitsExist(deskId));
    }

    @Test
    void deleteTrader_UpdatesDeskTradersCache() {
        when(traderRepository.save(any(Trader.class))).thenReturn(testTrader);
        service.saveTrader(testTrader);

        service.deleteTrader(traderId);

        verify(traderRepository).deleteById(traderId);
        assertFalse(service.traderExists(traderId));
        assertTrue(service.getDeskTraders(deskId).isEmpty());
    }

    @Test
    void saveLimits_UpdatesCache() {
        when(limitsRepository.save(any(DeskLimits.class))).thenReturn(testLimits);

        DeskLimits savedLimits = service.saveLimits(testLimits);

        assertNotNull(savedLimits);
        assertEquals(deskId, savedLimits.id());
        assertTrue(service.limitsExist(deskId));
    }

    @Test
    void getLimits_ReturnsCorrectLimits() {
        when(limitsRepository.save(testLimits)).thenReturn(testLimits);
        service.saveLimits(testLimits);

        DeskLimits retrievedLimits = service.getLimits(deskId);

        assertNotNull(retrievedLimits);
        assertEquals(testLimits.buyNotionalLimit(), retrievedLimits.buyNotionalLimit());
        assertEquals(testLimits.sellNotionalLimit(), retrievedLimits.sellNotionalLimit());
    }

    @Test
    void hasTradersForDesk_ReturnsCorrectStatus() {
        when(traderRepository.save(testTrader)).thenReturn(testTrader);
        assertFalse(service.hasTradersForDesk(deskId));
        
        service.saveTrader(testTrader);
        
        assertTrue(service.hasTradersForDesk(deskId));
    }

    @Test
    void getAllDesks_ReturnsAllDesks() {
        // Arrange
        UUID id = UUID.randomUUID();
        DeskLimits testLimits = new DeskLimits(id, id, 0,0,0);
        when(limitsRepository.save(testLimits)).thenReturn(testLimits);
        when(deskRepository.save(testDesk)).thenReturn(testDesk);
        // Act
        service.saveDesk(testDesk);
        // Assert
        List<Desk> allDesks = service.getAllDesks();
        assertFalse(allDesks.isEmpty());
        assertEquals(1, allDesks.size());
        assertEquals(testDesk, allDesks.get(0));
    }
} 