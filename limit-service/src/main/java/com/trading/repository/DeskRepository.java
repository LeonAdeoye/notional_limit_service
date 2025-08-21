package com.trading.repository;

import com.trading.model.Desk;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.UUID;

public interface DeskRepository extends MongoRepository<Desk, UUID>
{
}
