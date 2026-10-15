CREATE TABLE reminders (
                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           schedule_item_id UUID NOT NULL REFERENCES schedule_items(id) ON DELETE CASCADE,
                           remind_at TIMESTAMP WITH TIME ZONE NOT NULL,
                           is_triggered BOOLEAN DEFAULT FALSE,
                           created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notifications (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               title VARCHAR(255) NOT NULL,
                               message TEXT NOT NULL,
                               is_read BOOLEAN DEFAULT FALSE,
                               created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);