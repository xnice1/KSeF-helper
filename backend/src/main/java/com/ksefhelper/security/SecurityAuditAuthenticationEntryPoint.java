package com.ksefhelper.security;

import com.ksefhelper.audit.AuditEventService;
import com.ksefhelper.audit.AuditEventType;
import com.ksefhelper.common.web.ApiErrorWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.Map;

@Component
public class SecurityAuditAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final Logger log = LoggerFactory.getLogger(SecurityAuditAuthenticationEntryPoint.class);

    private final AuditEventService auditEventService;
    private final ApiErrorWriter apiErrorWriter;

    public SecurityAuditAuthenticationEntryPoint(
            AuditEventService auditEventService,
            ApiErrorWriter apiErrorWriter
    ) {
        this.auditEventService = auditEventService;
        this.apiErrorWriter = apiErrorWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        try {
            auditEventService.recordSecurity(
                    AuditEventType.AUTHENTICATION_FAILED,
                    "http_request",
                    request.getRequestURI(),
                    Map.of("method", request.getMethod())
            );
        } catch (RuntimeException auditFailure) {
            log.error("Authentication failure could not be audited path={}", request.getRequestURI(), auditFailure);
        }
        apiErrorWriter.write(request, response, HttpStatus.UNAUTHORIZED, "Authentication is required.");
    }
}
