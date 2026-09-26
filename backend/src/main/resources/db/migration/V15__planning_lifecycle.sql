ALTER TABLE lists ADD COLUMN archived INTEGER NOT NULL DEFAULT 0;
ALTER TABLE lists ADD COLUMN is_template INTEGER NOT NULL DEFAULT 0;
ALTER TABLE lists ADD COLUMN deleted_at TEXT;
ALTER TABLE items ADD COLUMN deleted_at TEXT;
CREATE INDEX idx_lists_lifecycle ON lists(user_id, deleted_at, archived, is_template);
CREATE INDEX idx_items_lifecycle_due ON items(deleted_at, due_date);
ALTER TABLE lists ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
