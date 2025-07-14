package com.flipkart.grayskull.repositories;

import com.flipkart.grayskull.models.db.Checkpoint;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuditCheckpointRepository extends MongoRepository<Checkpoint, String> {
    Optional<Checkpoint> getCheckpointByName(String name);
}
