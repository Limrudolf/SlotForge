package com.slotforge.api.audit;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slotforge.api.common.PageResponse;
import com.slotforge.api.security.CorrelationIdProvider;
import com.slotforge.api.security.CurrentActor;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final CorrelationIdProvider correlationIdProvider;

    public AuditService(
            AuditLogRepository auditLogRepository,
            CorrelationIdProvider correlationIdProvider
    ) {
        this.auditLogRepository = auditLogRepository;
        this.correlationIdProvider = correlationIdProvider;
    }

    public void record(
            CurrentActor actor,
            AuditAction action,
            AuditEntityType entityType,
            UUID entityId,
            Map<String, Object> details
    ) {
        auditLogRepository.save(new AuditLog(
                actor.userId(),
                action,
                entityType,
                entityId,
                correlationIdProvider.currentCorrelationId(),
                details
        ));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(int page, int size) {
        PageRequest request = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "occurredAt")
                        .and(Sort.by(Sort.Direction.DESC, "id"))
        );
        return PageResponse.from(
                auditLogRepository.findAll(request).map(AuditLogResponse::from)
        );
    }
}
