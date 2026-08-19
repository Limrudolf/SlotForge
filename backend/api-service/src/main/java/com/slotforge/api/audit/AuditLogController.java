package com.slotforge.api.audit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.slotforge.api.common.PageResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import static com.slotforge.api.common.config.OpenApiConfiguration.BEARER_AUTH;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@Tag(name = "Administration", description = "Admin-only operations")
public class AuditLogController {

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(
            summary = "List audit logs",
            description = "Returns newest audit entries first. Requires ADMIN."
    )
    @SecurityRequirement(name = BEARER_AUTH)
    public PageResponse<AuditLogResponse> list(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page must be zero or greater")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size must not exceed 100")
            int size
    ) {
        return auditService.list(page, size);
    }
}
