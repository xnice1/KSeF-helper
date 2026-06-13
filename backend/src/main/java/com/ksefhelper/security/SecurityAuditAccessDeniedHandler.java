package com.ksefhelper.security;

import com.ksefhelper.audit.AuditEventService;
import com.ksefhelper.audit.AuditEventType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class SecurityAuditAccessDeniedHandler implements AccessDeniedHandler {
    private static final Logger log = LoggerFactory.getLogger(SecurityAuditAccessDeniedHandler.class);

    private final AuditEventService auditEventService;

    public SecurityAuditAccessDeniedHandler(AuditEventService auditEventService) {
        this.auditEventService = auditEventService;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        try {
            auditEventService.recordSecurity(
                    AuditEventType.AUTHORIZATION_FAILED,
                    "http_request",
                    request.getRequestURI(),
                    Map.of("method", request.getMethod())
            );
        } catch (RuntimeException auditFailure) {
            log.error("Authorization failure could not be audited path={}", request.getRequestURI(), auditFailure);
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }
}
