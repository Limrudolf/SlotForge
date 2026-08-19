package com.slotforge.api.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID actorUserId,
        AuditAction action,
        AuditEntityType entityType,
        UUID entityId,
        UUID correlationId,
        Map<String, Object> details,
        Instant occurredAt
) {
    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getActorUserId(),
                auditLog.getAction(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getCorrelationId(),
                auditLog.getDetails(),
                auditLog.getOccurredAt()
        );
    }
}
