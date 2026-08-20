package com.slotforge.api.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.slotforge.api.TestcontainersConfiguration;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PaymentSchemaIntegrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationCreatesPaymentTablesAndBookingExpiryColumn() {
        List<String> tables = jdbcTemplate.queryForList(
                """
                select table_name
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in ('payment_intents', 'payment_events')
                order by table_name
                """,
                String.class
        );

        assertEquals(
                List.of("payment_events", "payment_intents"),
                tables
        );

        String nullable = jdbcTemplate.queryForObject(
                """
                select is_nullable
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'bookings'
                  and column_name = 'payment_expires_at'
                """,
                String.class
        );

        assertEquals("NO", nullable);
    }

    @Test
    void migrationCreatesPaymentUniquenessConstraints() {
        List<String> constraints = jdbcTemplate.queryForList(
                """
                select constraint_name
                from information_schema.table_constraints
                where table_schema = 'public'
                  and constraint_type = 'UNIQUE'
                  and constraint_name in (
                      'uq_payment_intents_booking',
                      'uq_payment_events_external_event'
                  )
                order by constraint_name
                """,
                String.class
        );

        assertEquals(
                List.of(
                        "uq_payment_events_external_event",
                        "uq_payment_intents_booking"
                ),
                constraints
        );
    }

    @Test
    void migrationCreatesPaymentWorkflowIndexes() {
        List<String> indexes = jdbcTemplate.queryForList(
                """
                select indexname
                from pg_indexes
                where schemaname = 'public'
                  and indexname in (
                      'idx_bookings_payment_expiry',
                      'idx_payment_events_intent_received'
                  )
                order by indexname
                """,
                String.class
        );

        assertEquals(
                List.of(
                        "idx_bookings_payment_expiry",
                        "idx_payment_events_intent_received"
                ),
                indexes
        );

        String expiryIndexDefinition = jdbcTemplate.queryForObject(
                """
                select indexdef
                from pg_indexes
                where schemaname = 'public'
                  and indexname = 'idx_bookings_payment_expiry'
                """,
                String.class
        );

        assertTrue(expiryIndexDefinition.contains(
                "WHERE ((status)::text = 'PENDING_PAYMENT'::text)"
        ));
    }
}
