ALTER TABLE items ADD COLUMN variant_note TEXT;
ALTER TABLE items ADD COLUMN seller TEXT;
ALTER TABLE items ADD COLUMN availability TEXT;
ALTER TABLE items ADD COLUMN price_checked_at TEXT;
ALTER TABLE items ADD COLUMN source_url TEXT;
ALTER TABLE items ADD COLUMN claim_token_hash TEXT;
ALTER TABLE lists ADD COLUMN source_url TEXT;
ALTER TABLE lists ADD COLUMN event_location TEXT;
