package com.trading.repository;

import com.trading.model.TraderNotionalLimit;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface TraderNotionalLimitRepository extends MongoRepository<TraderNotionalLimit, UUID> {
    List<TraderNotionalLimit> findByDeskId(UUID deskId);
    boolean existsByDeskId(UUID deskId);
} 