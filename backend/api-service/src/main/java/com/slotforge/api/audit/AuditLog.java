package com.slotforge.api.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 100)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 100)
    private AuditEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> details;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditLog() {
        // Required by JPA.
    }

    public AuditLog(
            UUID actorUserId,
            AuditAction action,
            AuditEntityType entityType,
            UUID entityId,
            UUID correlationId,
            Map<String, Object> details
    ) {
        this.actorUserId = actorUserId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.correlationId = correlationId;
        this.details = Map.copyOf(details);
    }

    public UUID getId() { return id; }
    public UUID getActorUserId() { return actorUserId; }
    public AuditAction getAction() { return action; }
    public AuditEntityType getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public UUID getCorrelationId() { return correlationId; }
    public Map<String, Object> getDetails() { return Map.copyOf(details); }
    public Instant getOccurredAt() { return occurredAt; }
}
