CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL,
    event_session_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_bookings_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_bookings_event_session
        FOREIGN KEY (event_session_id)
        REFERENCES event_sessions (id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_bookings_status
        CHECK (
            status IN (
                'PENDING_PAYMENT',
                'CONFIRMED',
                'CANCELLED',
                'EXPIRED',
                'FAILED'
            )
        )
);

CREATE TABLE booking_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    booking_id UUID NOT NULL,
    booking_slot_id UUID NOT NULL,
    quantity INTEGER NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_booking_items_booking
        FOREIGN KEY (booking_id)
        REFERENCES bookings (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_booking_items_booking_slot
        FOREIGN KEY (booking_slot_id)
        REFERENCES booking_slots (id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_booking_items_booking_slot
        UNIQUE (booking_id, booking_slot_id),

    CONSTRAINT chk_booking_items_quantity_positive
        CHECK (quantity > 0)
);

CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    booking_id UUID NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_idempotency_keys_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_idempotency_keys_booking
        FOREIGN KEY (booking_id)
        REFERENCES bookings (id)
        ON DELETE CASCADE,

    CONSTRAINT uq_idempotency_keys_user_key
        UNIQUE (user_id, idempotency_key),

    CONSTRAINT uq_idempotency_keys_booking
        UNIQUE (booking_id),

    CONSTRAINT chk_idempotency_keys_key_not_blank
        CHECK (BTRIM(idempotency_key) <> ''),

    CONSTRAINT chk_idempotency_keys_fingerprint_sha256
        CHECK (request_fingerprint ~ '^[0-9a-f]{64}$')
);

CREATE TABLE booking_state_transitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    booking_id UUID NOT NULL,
    from_state VARCHAR(30),
    to_state VARCHAR(30) NOT NULL,
    changed_by_user_id UUID,
    reason VARCHAR(255),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_booking_state_transitions_booking
        FOREIGN KEY (booking_id)
        REFERENCES bookings (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_booking_state_transitions_changed_by_user
        FOREIGN KEY (changed_by_user_id)
        REFERENCES users (id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_booking_state_transitions_from_state
        CHECK (
            from_state IS NULL
            OR from_state IN (
                'PENDING_PAYMENT',
                'CONFIRMED',
                'CANCELLED',
                'EXPIRED',
                'FAILED'
            )
        ),

    CONSTRAINT chk_booking_state_transitions_to_state
        CHECK (
            to_state IN (
                'PENDING_PAYMENT',
                'CONFIRMED',
                'CANCELLED',
                'EXPIRED',
                'FAILED'
            )
        ),

    CONSTRAINT chk_booking_state_transitions_state_changed
        CHECK (
            from_state IS NULL
            OR from_state <> to_state
        ),

    CONSTRAINT chk_booking_state_transitions_reason_not_blank
        CHECK (
            reason IS NULL
            OR BTRIM(reason) <> ''
        )
);

CREATE INDEX idx_bookings_user_created_id
    ON bookings (user_id, created_at DESC, id DESC);

CREATE INDEX idx_bookings_session_status
    ON bookings (event_session_id, status);

CREATE INDEX idx_booking_items_slot
    ON booking_items (booking_slot_id);

CREATE INDEX idx_booking_state_transitions_booking_occurred_id
    ON booking_state_transitions (booking_id, occurred_at, id);