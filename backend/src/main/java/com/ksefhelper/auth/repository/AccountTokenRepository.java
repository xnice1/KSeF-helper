package com.ksefhelper.auth.repository;

import com.ksefhelper.auth.entity.AccountToken;
import com.ksefhelper.auth.entity.AccountTokenType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AccountTokenRepository extends JpaRepository<AccountToken, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from AccountToken token where token.tokenHash = :tokenHash and token.type = :type")
    Optional<AccountToken> findForUpdate(
            @Param("tokenHash") String tokenHash,
            @Param("type") AccountTokenType type
    );

    @Modifying
    @Query("""
            update AccountToken token
            set token.usedAt = :usedAt
            where token.user.id = :userId and token.type = :type and token.usedAt is null
            """)
    void invalidateActive(
            @Param("userId") UUID userId,
            @Param("type") AccountTokenType type,
            @Param("usedAt") Instant usedAt
    );
}
