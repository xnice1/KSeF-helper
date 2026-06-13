package com.ksefhelper.audit;

import com.ksefhelper.audit.dto.AuditEventResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit-events")
public class AuditAdminController {
    private final AuditQueryService auditQueryService;

    public AuditAdminController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    public List<AuditEventResponse> events() {
        return auditQueryService.platformEvents();
    }
}
