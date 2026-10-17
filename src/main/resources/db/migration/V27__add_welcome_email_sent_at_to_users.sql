-- KAN-369: the welcome email goes out once, on the first successful login after registering.
ALTER TABLE users ADD COLUMN welcome_email_sent_at TIMESTAMP WITH TIME ZONE;

-- Accounts that are already verified have logged in before: don't send them a welcome email now.
UPDATE users SET welcome_email_sent_at = CURRENT_TIMESTAMP WHERE email_verified = true;