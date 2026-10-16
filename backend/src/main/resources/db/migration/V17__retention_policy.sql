ALTER TABLE lists ADD COLUMN public_share_expires_at TEXT;

-- The issue date of existing links was not recorded. Give them a full 56 days
-- from this upgrade instead of expiring them without notice.
UPDATE lists
SET public_share_expires_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '+56 days')
WHERE is_public = 1 AND share_token IS NOT NULL;
