CREATE TABLE event_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    event_id UUID NOT NULL,
    venue_id UUID NOT NULL,

    start_time_utc TIMESTAMPTZ NOT NULL,
    end_time_utc TIMESTAMPTZ NOT NULL,
    display_timezone VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_event_sessions_event
        FOREIGN KEY (event_id)
        REFERENCES events (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_event_sessions_venue
        FOREIGN KEY (venue_id)
        REFERENCES venues (id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_event_sessions_time_range
        CHECK (end_time_utc > start_time_utc),

    CONSTRAINT chk_event_sessions_display_timezone_not_blank
        CHECK (BTRIM(display_timezone) <> ''),

    CONSTRAINT chk_event_sessions_status
        CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED'))
);

CREATE TABLE booking_slots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    event_session_id UUID NOT NULL,
    total_capacity INTEGER NOT NULL,
    remaining_capacity INTEGER NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_booking_slots_event_session
        UNIQUE (event_session_id),

    CONSTRAINT fk_booking_slots_event_session
        FOREIGN KEY (event_session_id)
        REFERENCES event_sessions (id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_booking_slots_total_capacity_positive
        CHECK (total_capacity > 0),

    CONSTRAINT chk_booking_slots_remaining_capacity_non_negative
        CHECK (remaining_capacity >= 0),

    CONSTRAINT chk_booking_slots_remaining_within_total
        CHECK (remaining_capacity <= total_capacity)
);

CREATE INDEX idx_event_sessions_event_start_id
    ON event_sessions (event_id, start_time_utc, id);

CREATE INDEX idx_event_sessions_venue_start_id
    ON event_sessions (venue_id, start_time_utc, id);