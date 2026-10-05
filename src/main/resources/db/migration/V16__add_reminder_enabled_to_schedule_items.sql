-- KAN-307: reminder opt-in flag on schedule_items
ALTER TABLE schedule_items ADD COLUMN reminder_enabled BOOLEAN NOT NULL DEFAULT FALSE;