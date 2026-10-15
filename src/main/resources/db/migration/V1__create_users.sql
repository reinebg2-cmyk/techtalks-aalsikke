CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       full_name VARCHAR(255) NOT NULL,
                       email VARCHAR(255) UNIQUE,
                       phone_number VARCHAR(50) UNIQUE,
                       date_of_birth DATE,
                       profile_mode VARCHAR(20) NOT NULL CHECK (profile_mode IN ('STUDENT', 'WORK', 'HYBRID')),
                       account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (account_status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
                       email_verified BOOLEAN NOT NULL DEFAULT FALSE,
                       phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
                       two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                       deleted_at TIMESTAMP WITH TIME ZONE,
                       CONSTRAINT chk_email_or_phone_present CHECK (email IS NOT NULL OR phone_number IS NOT NULL)
);