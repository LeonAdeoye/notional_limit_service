package com.trading.repository;

import com.trading.model.DeskNotionalLimit;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface DeskNotionalLimitRepository extends MongoRepository<DeskNotionalLimit, UUID> {
} 