package com.ksefhelper.auth.repository;

import com.ksefhelper.auth.entity.AuthSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AuthSession session where session.tokenHash = :tokenHash")
    Optional<AuthSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
            update AuthSession session
            set session.revokedAt = :revokedAt
            where session.familyId = :familyId and session.revokedAt is null
            """)
    void revokeFamily(@Param("familyId") UUID familyId, @Param("revokedAt") Instant revokedAt);

    @Modifying
    @Query("""
            update AuthSession session
            set session.revokedAt = :revokedAt
            where session.user.id = :userId and session.revokedAt is null
            """)
    void revokeAllForUser(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);
}
