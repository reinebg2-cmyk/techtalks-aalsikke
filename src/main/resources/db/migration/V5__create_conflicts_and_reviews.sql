CREATE TABLE schedule_conflicts (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                    item_one_id UUID NOT NULL REFERENCES schedule_items(id) ON DELETE CASCADE,
                                    item_two_id UUID NOT NULL REFERENCES schedule_items(id) ON DELETE CASCADE,
                                    conflict_type VARCHAR(50),
                                    is_resolved BOOLEAN DEFAULT FALSE,
                                    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE daily_reviews (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               review_date DATE NOT NULL,
                               notes TEXT,
                               productivity_score INT CHECK (productivity_score BETWEEN 1 AND 5),
                               created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);