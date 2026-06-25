package com.ksefhelper.common.web;

import io.sentry.Sentry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ErrorTracker {
    public void capture(Throwable error, HttpServletRequest request) {
        Sentry.withScope(scope -> {
            String requestId = RequestIdFilter.requestId(request);
            if (requestId != null) {
                scope.setTag("request_id", requestId);
            }
            scope.setTag("http.method", request.getMethod());
            scope.setExtra("http.path", request.getRequestURI());
            Sentry.captureException(error);
        });
    }
}
