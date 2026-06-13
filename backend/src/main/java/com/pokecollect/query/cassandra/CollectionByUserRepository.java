package com.pokecollect.query.cassandra;

import java.util.List;

import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.core.mapping.MapId;

public interface CollectionByUserRepository extends CassandraRepository<CollectionByUser, MapId> {
    // user_id is the full partition key. Used by the collection view in Phase 2.
    List<CollectionByUser> findByUserId(String userId);
}
