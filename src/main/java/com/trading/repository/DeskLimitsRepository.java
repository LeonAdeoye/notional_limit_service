package com.trading.repository;

import com.trading.model.DeskLimits;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeskLimitsRepository extends MongoRepository<DeskLimits, UUID> {
    Optional<DeskLimits> findByDeskId(UUID deskId);
    boolean existsByDeskId(UUID deskId);
} 