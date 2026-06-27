package com.tcgtracker.consumer

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.SimpleStatement
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

/**
 * Read-side projector: each consumed event JSON must turn into the right Cassandra
 * statement against the right read model. CqlSession is mocked, so these specs assert
 * the routing + bound values of the projection without a live cluster.
 */
class CassandraProjectorSpec extends Specification {

    CqlSession cql = Mock()
    SimpleMeterRegistry metrics = new SimpleMeterRegistry()
    CassandraProjector projector = new CassandraProjector(cql, metrics)

    def "card_added_to_collection inserts into collection_by_user with the event's values"() {
        given:
        def json = '''{"event_type":"card_added_to_collection","user_id":"u1","collection_id":"c1",
            "card_id":"pw1","card_name":"Pikachu","set_name":"Base","rarity":"Rare",
            "condition":"Near Mint","market_price_usd":9.99}'''

        when:
        projector.onEvent(json)

        then:
        1 * cql.execute({ SimpleStatement st ->
            st.query.startsWith("INSERT INTO collection_by_user") &&
            st.positionalValues[0] == "u1" &&
            st.positionalValues[1] == "c1" &&
            st.positionalValues[2] == "pw1" &&
            st.positionalValues[3] == "Pikachu" &&
            st.positionalValues[4] == "Base" &&
            st.positionalValues[5] == "Rare" &&
            st.positionalValues[6] == "Near Mint" &&
            st.positionalValues[7] == new BigDecimal("9.99")
        })
    }

    def "a null market price is projected as a null bound value, not a parse failure"() {
        given:
        def json = '''{"event_type":"card_added_to_collection","user_id":"u1","collection_id":"c1",
            "card_id":"pw1","card_name":"Pikachu","set_name":"Base","rarity":"Rare",
            "condition":"Near Mint","market_price_usd":null}'''

        when:
        projector.onEvent(json)

        then:
        1 * cql.execute({ SimpleStatement st -> st.positionalValues[7] == null })
    }

    def "card_removed_from_collection deletes the row by user + collection id"() {
        given:
        def json = '''{"event_type":"card_removed_from_collection","user_id":"u1","collection_id":"c1"}'''

        when:
        projector.onEvent(json)

        then:
        1 * cql.execute({ SimpleStatement st ->
            st.query.startsWith("DELETE FROM collection_by_user") &&
            st.positionalValues[0] == "u1" &&
            st.positionalValues[1] == "c1"
        })
    }

    def "card_placed_in_binder inserts into binder_by_user with integer page + slot"() {
        given:
        def json = '''{"event_type":"card_placed_in_binder","user_id":"u1","page_number":2,"slot_index":5,
            "card_id":"pw1","card_name":"Pikachu","set_name":"Base","rarity":"Rare"}'''

        when:
        projector.onEvent(json)

        then:
        1 * cql.execute({ SimpleStatement st ->
            st.query.startsWith("INSERT INTO binder_by_user") &&
            st.positionalValues[1] == 2 &&
            st.positionalValues[2] == 5
        })
    }

    def "an unrelated event type is ignored — no Cassandra write"() {
        given:
        def json = '''{"event_type":"something_else","user_id":"u1"}'''

        when:
        projector.onEvent(json)

        then:
        0 * cql.execute(_)
    }

    def "each projected event increments the tcg.projections counter, tagged by event_type"() {
        when:
        projector.onEvent('''{"event_type":"card_added_to_collection","user_id":"u1","collection_id":"c1",
            "card_id":"pw1","card_name":"Pikachu","set_name":"Base","rarity":"Rare","condition":"NM","market_price_usd":1.0}''')
        projector.onEvent('''{"event_type":"card_removed_from_collection","user_id":"u1","collection_id":"c1"}''')
        projector.onEvent('''{"event_type":"something_else","user_id":"u1"}''')

        then: "only the two recognised events are counted, under their own type tag"
        metrics.counter("tcg.projections", "event_type", "card_added_to_collection").count() == 1.0d
        metrics.counter("tcg.projections", "event_type", "card_removed_from_collection").count() == 1.0d
        metrics.find("tcg.projections").tag("event_type", "something_else").counter() == null
    }

    def "malformed JSON is swallowed (logged) rather than propagated to the Kafka listener"() {
        when:
        projector.onEvent("not json at all")

        then:
        notThrown(Exception)
        0 * cql.execute(_)
    }
}
