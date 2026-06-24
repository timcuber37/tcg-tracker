package com.tcgtracker.query.cassandra;

import java.util.List;

import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.core.mapping.MapId;

public interface CardsBySetRepository extends CassandraRepository<CardsBySet, MapId> {
    // set_name is the full partition key, so this needs no ALLOW FILTERING.
    List<CardsBySet> findBySetName(String setName);
}
