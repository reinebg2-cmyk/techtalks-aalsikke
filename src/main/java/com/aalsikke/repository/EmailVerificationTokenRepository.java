package com.aalsikke.repository;

import com.aalsikke.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /** The most recent link sent to this user (used for the resend cooldown). */
    Optional<EmailVerificationToken> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    /** KAN-362: does the user still have a usable link for this email (not used, not expired)? */
    boolean existsByUserIdAndEmailIgnoreCaseAndUsedAtIsNullAndExpiresAtAfter(UUID userId, String email, OffsetDateTime now);

    /** KAN-362: after a successful verification, mark every still-open link for this user as used. */
    @Modifying
    @Query("""
            UPDATE EmailVerificationToken t
            SET t.usedAt = :now
            WHERE t.userId = :userId AND t.usedAt IS NULL
            """)
    int invalidateOpenTokens(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);
}