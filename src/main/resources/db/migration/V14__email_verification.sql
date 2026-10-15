-- V14 (KAN-262): email verification.

-- Accounts that exist before this feature count as verified, so nobody is locked out.
UPDATE users SET email_verified = TRUE WHERE email_verified = FALSE;

-- One row per verification link sent. Only a SHA-256 hash of the token is stored,
-- so a database leak can't be used to verify someone else's email.
CREATE TABLE email_verification_tokens (
                                           id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                           user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                           token_hash VARCHAR(64) NOT NULL UNIQUE,
                                           email      VARCHAR(255) NOT NULL,              -- the address the link was sent to
                                           expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
                                           used_at    TIMESTAMP WITH TIME ZONE,           -- set when used, or when a newer link replaces it
                                           created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_email_verification_tokens_user ON email_verification_tokens (user_id);