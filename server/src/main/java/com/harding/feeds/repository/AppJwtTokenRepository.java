package com.harding.feeds.repository;

import com.harding.feeds.entity.AppJwtToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface AppJwtTokenRepository extends JpaRepository<AppJwtToken, Long> {

    Optional<AppJwtToken> findByJti(String jti);

    @Modifying
    @Query("UPDATE AppJwtToken t SET t.revoked = true, t.revokedAt = :revokedAt " +
           "WHERE t.user.id = :userId AND t.revoked = false")
    void revokeAllForUser(@Param("userId") Long userId, @Param("revokedAt") OffsetDateTime revokedAt);

    default void revokeAllForUser(Long userId) {
        revokeAllForUser(userId, OffsetDateTime.now());
    }

    @Modifying
    @Query("DELETE FROM AppJwtToken t WHERE t.expiresAt < :before")
    int deleteExpiredTokens(@Param("before") OffsetDateTime before);
}
