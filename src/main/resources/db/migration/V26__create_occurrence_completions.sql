-- KAN-363: which weekly repeats of a recurring item are done. One row per completed repeat.
-- The base item's own status stays on schedule_items; deleting the item deletes these rows.
CREATE TABLE occurrence_completions (
                                        id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                        schedule_item_id UUID NOT NULL REFERENCES schedule_items(id) ON DELETE CASCADE,
                                        occurrence_date  DATE NOT NULL,
                                        completed_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                        CONSTRAINT uq_occurrence_completions_item_date UNIQUE (schedule_item_id, occurrence_date)
);