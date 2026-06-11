package com.ksefhelper.auth.mail;

import com.ksefhelper.users.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.mail.delivery", havingValue = "smtp")
public class SmtpAccountMailService implements AccountMailService {
    private final JavaMailSender mailSender;
    private final String frontendUrl;
    private final String from;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public SmtpAccountMailService(
            JavaMailSender mailSender,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${app.mail.from}") String from
    ) {
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl;
        this.from = from;
    }

    @Override
    public void sendVerification(User user, String token) {
        send(
                user.getEmail(),
                "Verify your KSeF Helper email",
                "Verify your email: " + frontendUrl + "/verify-email?token=" + token
        );
    }

    @Override
    public void sendPasswordReset(User user, String token) {
        send(
                user.getEmail(),
                "Reset your KSeF Helper password",
                "Reset your password: " + frontendUrl + "/reset-password?token=" + token
        );
    }

    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
