package com.ksefhelper.users;

import com.ksefhelper.users.dto.AccountDeletionRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final AccountService accountService;
    private final String refreshCookieName;
    private final boolean refreshCookieSecure;

    public AccountController(
            AccountService accountService,
            @Value("${app.auth.refresh-cookie-name}") String refreshCookieName,
            @Value("${app.auth.refresh-cookie-secure}") boolean refreshCookieSecure
    ) {
        this.accountService = accountService;
        this.refreshCookieName = refreshCookieName;
        this.refreshCookieSecure = refreshCookieSecure;
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@Valid @RequestBody AccountDeletionRequest request) {
        accountService.deleteAccount(request.password(), request.confirmation());
        ResponseCookie expiredCookie = ResponseCookie.from(refreshCookieName, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(Duration.ZERO)
                .build();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .build();
    }
}
