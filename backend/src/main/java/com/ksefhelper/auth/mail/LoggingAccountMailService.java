package com.ksefhelper.auth.mail;

import com.ksefhelper.users.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.mail.delivery", havingValue = "log", matchIfMissing = true)
public class LoggingAccountMailService implements AccountMailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingAccountMailService.class);

    private final String frontendUrl;

    public LoggingAccountMailService(@Value("${app.frontend-url}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void sendVerification(User user, String token) {
        LOGGER.info("Development verification link for {}: {}/verify-email?token={}", user.getEmail(), frontendUrl, token);
    }

    @Override
    public void sendPasswordReset(User user, String token) {
        LOGGER.info("Development password reset link for {}: {}/reset-password?token={}", user.getEmail(), frontendUrl, token);
    }
}
