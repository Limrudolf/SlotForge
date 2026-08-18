CREATE TABLE venues (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name VARCHAR(200) NOT NULL,
    address_line_1 VARCHAR(255) NOT NULL,
    address_line_2 VARCHAR(255),
    city VARCHAR(100) NOT NULL,
    region VARCHAR(100),
    postal_code VARCHAR(20),
    country_code VARCHAR(2) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_venues_name_not_blank
        CHECK (BTRIM(name) <> ''),

    CONSTRAINT chk_venues_address_line_1_not_blank
        CHECK (BTRIM(address_line_1) <> ''),

    CONSTRAINT chk_venues_city_not_blank
        CHECK (BTRIM(city) <> ''),

    CONSTRAINT chk_venues_country_code
        CHECK (
            CHAR_LENGTH(country_code) = 2
            AND country_code = UPPER(country_code)
        )
);

CREATE TABLE events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_events_name_not_blank
        CHECK (BTRIM(name) <> ''),

    CONSTRAINT chk_events_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED'))
);

CREATE INDEX idx_events_created_at_id
    ON events (created_at DESC, id DESC);

CREATE INDEX idx_events_status_created_at_id
    ON events (status, created_at DESC, id DESC);