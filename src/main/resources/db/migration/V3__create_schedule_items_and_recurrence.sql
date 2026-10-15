CREATE TABLE schedule_items (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                course_id UUID REFERENCES courses(id) ON DELETE SET NULL,
                                workplace_id UUID REFERENCES workplaces(id) ON DELETE SET NULL,
                                title VARCHAR(255) NOT NULL,
                                description TEXT,
                                item_type VARCHAR(30) NOT NULL CHECK (item_type IN ('ASSIGNMENT', 'EXAM', 'CLASS', 'STUDY', 'SHIFT', 'PERSONAL')),
                                priority VARCHAR(20) DEFAULT 'MEDIUM' CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
                                start_time TIMESTAMP WITH TIME ZONE NOT NULL,
                                end_time TIMESTAMP WITH TIME ZONE NOT NULL,
                                is_recurring BOOLEAN DEFAULT FALSE,
                                created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE recurrence_rules (
                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  schedule_item_id UUID NOT NULL REFERENCES schedule_items(id) ON DELETE CASCADE,
                                  frequency VARCHAR(20) NOT NULL CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY')),
                                  interval_value INT DEFAULT 1,
                                  days_of_week VARCHAR(50),
                                  until_date DATE
);