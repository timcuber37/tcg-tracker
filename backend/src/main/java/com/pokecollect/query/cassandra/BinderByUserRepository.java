package com.pokecollect.query.cassandra;

import java.util.List;

import org.springframework.data.cassandra.core.mapping.MapId;
import org.springframework.data.cassandra.repository.CassandraRepository;

public interface BinderByUserRepository extends CassandraRepository<BinderByUser, MapId> {
    // user_id is the full partition key; rows come back ordered by page then slot.
    List<BinderByUser> findByUserId(String userId);
}
