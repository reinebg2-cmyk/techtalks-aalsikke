-- KAN-352: a task can have a default "halfway" reminder (moves with the task's times) plus custom ones.
ALTER TABLE reminders ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT FALSE;

-- KAN-352: reminders are now paused when schedule_items.reminder_enabled is false.
-- Until now the edit form never set that flag, so tasks with reminders could have it false.
-- Switch those on, so their reminders don't disappear from the bell and emails.
UPDATE schedule_items SET reminder_enabled = TRUE
WHERE reminder_enabled = FALSE
  AND EXISTS (SELECT 1 FROM reminders r WHERE r.schedule_item_id = schedule_items.id);