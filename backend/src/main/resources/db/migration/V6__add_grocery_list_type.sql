-- flyway:executeInTransaction=false
PRAGMA foreign_keys=OFF;

CREATE TABLE lists_new (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    type TEXT NOT NULL CHECK (type IN ('WISH', 'TODO', 'GROCERY', 'CHORE', 'EVENT')),
    share_token TEXT UNIQUE,
    is_public INTEGER NOT NULL DEFAULT 0 CHECK (is_public IN (0, 1)),
    target_date TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

INSERT INTO lists_new (id, user_id, title, description, type, share_token, is_public, target_date, created_at)
SELECT id, user_id, title, description, type, share_token, is_public, target_date, created_at
FROM lists;

DROP TABLE lists;
ALTER TABLE lists_new RENAME TO lists;

CREATE INDEX idx_lists_user_id ON lists(user_id);
CREATE INDEX idx_lists_share_token ON lists(share_token);

PRAGMA foreign_keys=ON;

ALTER TABLE items ADD COLUMN quantity TEXT;
ALTER TABLE items ADD COLUMN category TEXT;
