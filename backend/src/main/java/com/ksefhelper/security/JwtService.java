package com.ksefhelper.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey signingKey;
    private final Duration accessExpiration;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-expiration}") Duration accessExpiration
    ) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessExpiration = accessExpiration;
    }

    public String generateToken(AppUserPrincipal principal) {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", principal.id().toString());
        claims.put("tokenVersion", principal.tokenVersion());
        if (principal.organizationId() != null) {
            claims.put("organizationId", principal.organizationId().toString());
        }
        return Jwts.builder()
                .subject(principal.getUsername())
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessExpiration)))
                .signWith(signingKey)
                .compact();
    }

    public Instant accessTokenExpiresAt() {
        return Instant.now().plus(accessExpiration);
    }

    public String extractUsername(String token) {
        return claims(token).getSubject();
    }

    public UUID extractOrganizationId(String token) {
        String value = claims(token).get("organizationId", String.class);
        return value == null ? null : UUID.fromString(value);
    }

    public boolean isValid(String token, UserDetails userDetails) {
        Claims claims = claims(token);
        String username = claims.getSubject();
        if (!username.equalsIgnoreCase(userDetails.getUsername()) || !claims.getExpiration().after(new Date())) {
            return false;
        }
        if (!userDetails.isEnabled()) {
            return false;
        }
        if (userDetails instanceof AppUserPrincipal principal) {
            Number tokenVersion = claims.get("tokenVersion", Number.class);
            return tokenVersion != null && tokenVersion.longValue() == principal.tokenVersion();
        }
        return true;
    }

    private Claims claims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
