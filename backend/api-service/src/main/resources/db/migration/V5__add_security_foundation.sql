INSERT INTO roles (name)
VALUES
    ('CUSTOMER'),
    ('ORGANIZER'),
    ('ADMIN')
ON CONFLICT (name) DO NOTHING;


ALTER TABLE events
    ADD COLUMN organizer_id UUID;

ALTER TABLE events
    ADD CONSTRAINT fk_events_organizer
        FOREIGN KEY (organizer_id)
        REFERENCES users (id)
        ON DELETE RESTRICT;

CREATE INDEX idx_events_organizer_id
    ON events (organizer_id);


CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL,
    family_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,

    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revocation_reason VARCHAR(100),
    replaced_by_token_id UUID,

    CONSTRAINT uq_refresh_tokens_token_hash
        UNIQUE (token_hash),

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_refresh_tokens_replacement
        FOREIGN KEY (replaced_by_token_id)
        REFERENCES refresh_tokens (id)
        ON DELETE SET NULL,

    CONSTRAINT chk_refresh_tokens_expiry
        CHECK (expires_at > created_at),

    CONSTRAINT chk_refresh_tokens_revocation
        CHECK (
            (revoked_at IS NULL AND revocation_reason IS NULL)
            OR
            (revoked_at IS NOT NULL AND revocation_reason IS NOT NULL)
        )
);

CREATE INDEX idx_refresh_tokens_user_id
    ON refresh_tokens (user_id);

CREATE INDEX idx_refresh_tokens_family_id
    ON refresh_tokens (family_id);

CREATE INDEX idx_refresh_tokens_active_user
    ON refresh_tokens (user_id, expires_at)
    WHERE revoked_at IS NULL;