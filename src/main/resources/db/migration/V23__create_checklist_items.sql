-- KAN-342: a checklist (sub-steps) inside a task. Deleting the task deletes its checklist.
CREATE TABLE checklist_items (
                                 id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 schedule_item_id UUID NOT NULL REFERENCES schedule_items(id) ON DELETE CASCADE,
                                 text             VARCHAR(200) NOT NULL,
                                 done             BOOLEAN NOT NULL DEFAULT FALSE,
                                 position         INT NOT NULL,
                                 created_at       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_checklist_items_task ON checklist_items (schedule_item_id, position);