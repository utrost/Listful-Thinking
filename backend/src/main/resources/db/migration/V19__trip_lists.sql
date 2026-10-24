-- flyway:executeInTransaction=false
PRAGMA foreign_keys=OFF;

CREATE TABLE lists_new (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    type TEXT NOT NULL CHECK (type IN ('WISH', 'TODO', 'GROCERY', 'CHORE', 'EVENT', 'TRIP')),
    share_token TEXT UNIQUE,
    is_public INTEGER NOT NULL DEFAULT 0 CHECK (is_public IN (0, 1)),
    target_date TEXT,
    created_at TEXT NOT NULL,
    public_share_mode TEXT NOT NULL DEFAULT 'VIEW' CHECK (public_share_mode IN ('VIEW', 'WISH_CLAIM', 'SIGNUP')),
    archived INTEGER NOT NULL DEFAULT 0,
    is_template INTEGER NOT NULL DEFAULT 0,
    deleted_at TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    public_share_expires_at TEXT,
    source_url TEXT,
    event_location TEXT,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

INSERT INTO lists_new (id, user_id, title, description, type, share_token, is_public, target_date, created_at,
    public_share_mode, archived, is_template, deleted_at, version, public_share_expires_at, source_url, event_location)
SELECT id, user_id, title, description, type, share_token, is_public, target_date, created_at,
    public_share_mode, archived, is_template, deleted_at, version, public_share_expires_at, source_url, event_location
FROM lists;

DROP TABLE lists;
ALTER TABLE lists_new RENAME TO lists;
CREATE INDEX idx_lists_user_id ON lists(user_id);
CREATE INDEX idx_lists_share_token ON lists(share_token);
CREATE INDEX idx_lists_lifecycle ON lists(user_id, deleted_at, archived, is_template);
PRAGMA foreign_keys=ON;

ALTER TABLE items ADD COLUMN suggested_by_name TEXT;
