-- KAN-318: explicit time_type on schedule_items

ALTER TABLE schedule_items ADD COLUMN time_type VARCHAR(20);

UPDATE schedule_items SET time_type = CASE
                                          WHEN start_time IS NOT NULL AND end_time IS NOT NULL THEN 'TIME_BLOCKED'
                                          ELSE 'DEADLINE_ONLY'
    END;

ALTER TABLE schedule_items ALTER COLUMN time_type SET NOT NULL;

ALTER TABLE schedule_items ADD CONSTRAINT chk_schedule_items_time_type
    CHECK (time_type IN ('DEADLINE_ONLY', 'TIME_BLOCKED', 'START_ONLY'));

-- Replace the old inferred-type rules with per-type rules
ALTER TABLE schedule_items DROP CONSTRAINT chk_schedule_items_times_together;
ALTER TABLE schedule_items DROP CONSTRAINT chk_schedule_items_time_or_deadline;

ALTER TABLE schedule_items ADD CONSTRAINT chk_schedule_items_time_shape CHECK (
    (time_type = 'DEADLINE_ONLY' AND start_time IS NULL AND end_time IS NULL AND deadline IS NOT NULL) OR
    (time_type = 'TIME_BLOCKED'  AND start_time IS NOT NULL AND end_time IS NOT NULL) OR
    (time_type = 'START_ONLY'    AND start_time IS NOT NULL AND end_time IS NULL)
    );