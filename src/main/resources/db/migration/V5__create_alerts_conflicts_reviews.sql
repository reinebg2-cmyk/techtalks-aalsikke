CREATE TABLE schedule_conflicts (
                                    id BIGSERIAL PRIMARY KEY,
                                    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                    item_one_id BIGINT NOT NULL REFERENCES schedule_items(id) ON DELETE CASCADE,
                                    item_two_id BIGINT NOT NULL REFERENCES schedule_items(id) ON DELETE CASCADE,
                                    resolved BOOLEAN DEFAULT FALSE,
                                    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE alerts (
                        id BIGSERIAL PRIMARY KEY,
                        user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                        message VARCHAR(255) NOT NULL,
                        is_read BOOLEAN DEFAULT FALSE,
                        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE daily_reviews (
                               id BIGSERIAL PRIMARY KEY,
                               user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               review_date DATE NOT NULL,
                               notes TEXT,
                               completion_rate NUMERIC(5,2) DEFAULT 0.00,
                               created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                               UNIQUE(user_id, review_date)
);