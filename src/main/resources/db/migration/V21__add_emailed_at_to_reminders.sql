-- KAN-336: email reminders. emailed_at is set once the reminder email is sent, so it is never sent twice.
ALTER TABLE reminders ADD COLUMN emailed_at TIMESTAMP WITH TIME ZONE;

-- Reminders already in the past when this ships are not emailed (avoids a burst of old emails on first run).
UPDATE reminders SET emailed_at = CURRENT_TIMESTAMP WHERE remind_at <= CURRENT_TIMESTAMP;