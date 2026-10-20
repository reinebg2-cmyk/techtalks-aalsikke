-- KAN-381: reminders that don't belong to a task ("22/12 Coco's birthday, buy a gift").
-- Each reminder now stores its owner directly, so it no longer needs a task to find one.

ALTER TABLE reminders ADD COLUMN user_id UUID REFERENCES users(id) ON DELETE CASCADE;

-- Existing reminders: copy the owner from their task
UPDATE reminders r
SET user_id = s.user_id
    FROM schedule_items s
WHERE r.schedule_item_id = s.id;

ALTER TABLE reminders ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE reminders ALTER COLUMN schedule_item_id DROP NOT NULL;
ALTER TABLE reminders ADD COLUMN title VARCHAR(200);

-- Every reminder needs something to say: its task, or its own title
ALTER TABLE reminders ADD CONSTRAINT reminders_task_or_title
    CHECK (schedule_item_id IS NOT NULL OR title IS NOT NULL);

CREATE INDEX IF NOT EXISTS idx_reminders_user_id ON reminders(user_id);