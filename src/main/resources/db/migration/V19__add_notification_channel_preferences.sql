ALTER TABLE notification_preferences
    ADD COLUMN whatsapp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN sms_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN primary_channel VARCHAR(20) NOT NULL DEFAULT 'IN_APP';

ALTER TABLE notification_preferences
    ADD CONSTRAINT chk_notification_preferences_primary_channel
    CHECK (primary_channel IN ('WHATSAPP', 'SMS', 'IN_APP'));