CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    actor_user_id UUID,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID NOT NULL,
    correlation_id UUID NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_logs_actor_user
        FOREIGN KEY (actor_user_id)
        REFERENCES users (id)
        ON DELETE SET NULL,

    CONSTRAINT chk_audit_logs_action_not_blank
        CHECK (BTRIM(action) <> ''),

    CONSTRAINT chk_audit_logs_entity_type_not_blank
        CHECK (BTRIM(entity_type) <> ''),

    CONSTRAINT chk_audit_logs_details_is_object
        CHECK (JSONB_TYPEOF(details) = 'object')
);

CREATE INDEX idx_audit_logs_entity_occurred_at
    ON audit_logs (entity_type, entity_id, occurred_at DESC);

CREATE INDEX idx_audit_logs_correlation_id
    ON audit_logs (correlation_id);

CREATE INDEX idx_audit_logs_actor_occurred_at
    ON audit_logs (actor_user_id, occurred_at DESC)
    WHERE actor_user_id IS NOT NULL;