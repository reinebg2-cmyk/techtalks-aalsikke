-- Password reset tokens. Only a SHA-256 hash of the token is stored.
-- Next free version after V23__create_checklist_items.sql.
CREATE TABLE password_reset_tokens (
                                       id         BIGSERIAL    PRIMARY KEY,
                                       user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                                       email      VARCHAR(255) NOT NULL,
                                       token_hash VARCHAR(64)  NOT NULL UNIQUE,
                                       expires_at TIMESTAMPTZ  NOT NULL,
                                       used_at    TIMESTAMPTZ,
                                       created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens (user_id);