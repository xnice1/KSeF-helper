package com.ksefhelper.auth;

import com.ksefhelper.auth.dto.AuthResponse;
import com.ksefhelper.auth.dto.LoginRequest;
import com.ksefhelper.auth.dto.RegisterRequest;
import com.ksefhelper.security.ratelimit.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final RateLimitService rateLimitService;

    public AuthController(AuthService authService, RateLimitService rateLimitService) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        rateLimitService.checkLogin(request.email(), httpRequest.getRemoteAddr());
        return authService.login(request);
    }

    @GetMapping("/me")
    public AuthResponse me() {
        return authService.me();
    }

    @PostMapping("/switch-organization/{organizationId}")
    public AuthResponse switchOrganization(@PathVariable UUID organizationId) {
        return authService.switchOrganization(organizationId);
    }
}
