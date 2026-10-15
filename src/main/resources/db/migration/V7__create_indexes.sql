CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_phone ON users(phone_number);
CREATE INDEX idx_schedule_items_user_time ON schedule_items(user_id, start_time, end_time);
CREATE INDEX idx_reminders_trigger ON reminders(remind_at, is_triggered);
CREATE INDEX idx_notifications_user_read ON notifications(user_id, is_read);