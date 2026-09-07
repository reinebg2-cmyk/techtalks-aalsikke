CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_tasks_user_id ON tasks(user_id);
CREATE INDEX idx_schedule_items_user_id ON schedule_items(user_id);
CREATE INDEX idx_schedule_items_time ON schedule_items(start_time, end_time);
CREATE INDEX idx_alerts_user_unread ON alerts(user_id, is_read);