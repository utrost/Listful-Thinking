ALTER TABLE lists ADD COLUMN public_share_mode TEXT NOT NULL DEFAULT 'VIEW' CHECK (public_share_mode IN ('VIEW', 'WISH_CLAIM', 'SIGNUP'));
UPDATE lists SET public_share_mode = 'WISH_CLAIM' WHERE type = 'WISH';
