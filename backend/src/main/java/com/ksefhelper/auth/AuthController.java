package com.ksefhelper.auth;

import com.ksefhelper.auth.dto.AuthResponse;
import com.ksefhelper.auth.dto.EmailRequest;
import com.ksefhelper.auth.dto.LoginRequest;
import com.ksefhelper.auth.dto.MessageResponse;
import com.ksefhelper.auth.dto.RegisterRequest;
import com.ksefhelper.auth.dto.ResetPasswordRequest;
import com.ksefhelper.auth.dto.TokenRequest;
import com.ksefhelper.security.ratelimit.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final RateLimitService rateLimitService;
    private final String refreshCookieName;
    private final boolean refreshCookieSecure;

    public AuthController(
            AuthService authService,
            RateLimitService rateLimitService,
            @Value("${app.auth.refresh-cookie-name}") String refreshCookieName,
            @Value("${app.auth.refresh-cookie-secure}") boolean refreshCookieSecure
    ) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
        this.refreshCookieName = refreshCookieName;
        this.refreshCookieSecure = refreshCookieSecure;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return authenticated(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        rateLimitService.checkLogin(request.email(), httpRequest.getRemoteAddr());
        return authenticated(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(value = "${app.auth.refresh-cookie-name}", required = false) String refreshToken
    ) {
        return authenticated(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(value = "${app.auth.refresh-cookie-name}", required = false) String refreshToken
    ) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
                .build();
    }

    @GetMapping("/me")
    public AuthResponse me() {
        return authService.me();
    }

    @PostMapping("/switch-organization/{organizationId}")
    public AuthResponse switchOrganization(
            @PathVariable UUID organizationId,
            @CookieValue(value = "${app.auth.refresh-cookie-name}", required = false) String refreshToken
    ) {
        return authService.switchOrganization(organizationId, refreshToken);
    }

    @PostMapping("/request-verification")
    public MessageResponse requestVerification(@Valid @RequestBody EmailRequest request) {
        authService.requestVerification(request.email());
        return genericEmailMessage();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<AuthResponse> verifyEmail(@Valid @RequestBody TokenRequest request) {
        return authenticated(authService.verifyEmail(request.token()));
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody EmailRequest request) {
        authService.requestPasswordReset(request.email());
        return genericEmailMessage();
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.password());
        return new MessageResponse("Password updated. Sign in with the new password.");
    }

    private ResponseEntity<AuthResponse> authenticated(AuthService.AuthResult result) {
        if (result.refreshToken() == null) {
            return ResponseEntity.ok(result.response());
        }
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookie(result.refreshToken(), result.refreshExpiresAt()).toString()
                )
                .body(result.response());
    }

    private ResponseCookie refreshCookie(String token, Instant expiresAt) {
        return ResponseCookie.from(refreshCookieName, token)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(Duration.between(Instant.now(), expiresAt))
                .build();
    }

    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from(refreshCookieName, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(Duration.ZERO)
                .build();
    }

    private MessageResponse genericEmailMessage() {
        return new MessageResponse("If the account is eligible, an email has been sent.");
    }
}
