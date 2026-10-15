-- KAN-345: per-user switch for email notifications (reminder emails from KAN-336 respect it).
-- On by default so existing users start receiving reminder emails.
ALTER TABLE notification_preferences
    ADD COLUMN email_enabled BOOLEAN NOT NULL DEFAULT TRUE;