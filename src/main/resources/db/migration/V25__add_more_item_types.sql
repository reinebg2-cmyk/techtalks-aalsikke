ALTER TABLE schedule_items DROP CONSTRAINT schedule_items_item_type_check;

ALTER TABLE schedule_items
    ADD CONSTRAINT schedule_items_item_type_check
        CHECK (item_type IN (
                             'ASSIGNMENT', 'EXAM', 'CLASS', 'STUDY', 'SHIFT', 'PERSONAL',
                             'PROJECT', 'QUIZ', 'LAB', 'PRESENTATION',
                             'MEETING', 'WORK_TASK', 'PAYMENT_DUE',
                             'APPOINTMENT', 'ERRAND', 'EVENT'
            ));