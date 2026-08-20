ALTER TABLE event_sessions
    ADD COLUMN unit_price_minor BIGINT,
    ADD COLUMN currency VARCHAR(3);

UPDATE event_sessions
SET unit_price_minor = 10000,
    currency = 'SEK';

ALTER TABLE event_sessions
    ALTER COLUMN unit_price_minor SET NOT NULL,
    ALTER COLUMN currency SET NOT NULL,
    ADD CONSTRAINT chk_event_sessions_unit_price_positive
        CHECK (unit_price_minor > 0),
    ADD CONSTRAINT chk_event_sessions_currency
        CHECK (currency ~ '^[A-Z]{3}$');

ALTER TABLE booking_items
    ADD COLUMN unit_price_minor BIGINT,
    ADD COLUMN currency VARCHAR(3);

UPDATE booking_items AS item
SET unit_price_minor = session.unit_price_minor,
    currency = session.currency
FROM bookings AS booking
JOIN event_sessions AS session
    ON session.id = booking.event_session_id
WHERE item.booking_id = booking.id;

ALTER TABLE booking_items
    ALTER COLUMN unit_price_minor SET NOT NULL,
    ALTER COLUMN currency SET NOT NULL,
    ADD CONSTRAINT chk_booking_items_unit_price_positive
        CHECK (unit_price_minor > 0),
    ADD CONSTRAINT chk_booking_items_currency
        CHECK (currency ~ '^[A-Z]{3}$');
