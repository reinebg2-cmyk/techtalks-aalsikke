package com.aalsikke.repository;

import com.aalsikke.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Used for the resend cooldown. */
    Optional<PasswordResetToken> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Marks every still-open link of this user as used, so older links stop working. */
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now "
            + "where t.userId = :userId and t.usedAt is null")
    int invalidateOpenTokens(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);
}