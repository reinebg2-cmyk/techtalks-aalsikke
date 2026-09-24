-- V11: allow deadline-only items (a task with a deadline but no time slot yet),
-- which are scheduled later via time-blocking (S3-12).

ALTER TABLE schedule_items ALTER COLUMN start_time DROP NOT NULL;
ALTER TABLE schedule_items ALTER COLUMN end_time   DROP NOT NULL;

-- Start and end come together: both set, or both empty.
ALTER TABLE schedule_items
    ADD CONSTRAINT chk_schedule_items_times_together
        CHECK ((start_time IS NULL) = (end_time IS NULL));

-- Every item must have a time slot or a deadline (never completely empty).
ALTER TABLE schedule_items
    ADD CONSTRAINT chk_schedule_items_time_or_deadline
        CHECK (start_time IS NOT NULL OR deadline IS NOT NULL);

-- End must be after start. NOT VALID = enforced for new and updated rows only,
-- so old test data on someone's local database cannot make this migration fail.
ALTER TABLE schedule_items
    ADD CONSTRAINT chk_schedule_items_end_after_start
        CHECK (start_time IS NULL OR end_time > start_time) NOT VALID;