package com.flipkart.grayskull.spi.repositories;

import com.flipkart.grayskull.models.db.Checkpoint;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface AuditCheckpointRepository extends CrudRepository<Checkpoint, String> {
    Optional<Checkpoint> getCheckpointByName(String name);
}
