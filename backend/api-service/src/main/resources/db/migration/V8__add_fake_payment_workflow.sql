ALTER TABLE bookings
    DROP CONSTRAINT chk_bookings_status;

ALTER TABLE booking_state_transitions
    DROP CONSTRAINT chk_booking_state_transitions_from_state;

ALTER TABLE booking_state_transitions
    DROP CONSTRAINT chk_booking_state_transitions_to_state;

UPDATE bookings
SET status = 'PAYMENT_FAILED'
WHERE status = 'FAILED';

UPDATE booking_state_transitions
SET from_state = 'PAYMENT_FAILED'
WHERE from_state = 'FAILED';

UPDATE booking_state_transitions
SET to_state = 'PAYMENT_FAILED'
WHERE to_state = 'FAILED';

ALTER TABLE bookings
    ADD CONSTRAINT chk_bookings_status
        CHECK (
            status IN (
                'PENDING_PAYMENT',
                'PAYMENT_AUTHORIZED',
                'CONFIRMED',
                'PAYMENT_FAILED',
                'CANCELLED',
                'EXPIRED'
            )
        );

ALTER TABLE booking_state_transitions
    ADD CONSTRAINT chk_booking_state_transitions_from_state
        CHECK (
            from_state IS NULL
            OR from_state IN (
                'PENDING_PAYMENT',
                'PAYMENT_AUTHORIZED',
                'CONFIRMED',
                'PAYMENT_FAILED',
                'CANCELLED',
                'EXPIRED'
            )
        );

ALTER TABLE booking_state_transitions
    ADD CONSTRAINT chk_booking_state_transitions_to_state
        CHECK (
            to_state IN (
                'PENDING_PAYMENT',
                'PAYMENT_AUTHORIZED',
                'CONFIRMED',
                'PAYMENT_FAILED',
                'CANCELLED',
                'EXPIRED'
            )
        );

ALTER TABLE bookings
    ADD COLUMN payment_expires_at TIMESTAMPTZ;

UPDATE bookings
SET payment_expires_at = created_at + INTERVAL '15 minutes';

ALTER TABLE bookings
    ALTER COLUMN payment_expires_at SET NOT NULL;

CREATE TABLE payment_intents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    booking_id UUID NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_payment_intents_booking
        FOREIGN KEY (booking_id)
        REFERENCES bookings (id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_payment_intents_booking
        UNIQUE (booking_id),

    CONSTRAINT chk_payment_intents_amount_positive
        CHECK (amount_minor > 0),

    CONSTRAINT chk_payment_intents_currency
        CHECK (currency ~ '^[A-Z]{3}$'),

    CONSTRAINT chk_payment_intents_status
        CHECK (
            status IN (
                'PENDING',
                'AUTHORIZED',
                'FAILED',
                'TIMED_OUT'
            )
        )
);

CREATE TABLE payment_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    payment_intent_id UUID NOT NULL,
    external_event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payment_events_payment_intent
        FOREIGN KEY (payment_intent_id)
        REFERENCES payment_intents (id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_payment_events_external_event
        UNIQUE (external_event_id),

    CONSTRAINT chk_payment_events_external_event_not_blank
        CHECK (BTRIM(external_event_id) <> ''),

    CONSTRAINT chk_payment_events_type
        CHECK (
            event_type IN (
                'AUTHORIZED',
                'FAILED',
                'TIMED_OUT'
            )
        )
);

CREATE INDEX idx_bookings_payment_expiry
    ON bookings (payment_expires_at, id)
    WHERE status = 'PENDING_PAYMENT';

CREATE INDEX idx_payment_events_intent_received
    ON payment_events (payment_intent_id, received_at, id);
