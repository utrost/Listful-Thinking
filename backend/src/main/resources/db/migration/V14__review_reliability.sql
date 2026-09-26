ALTER TABLE items ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE items ADD COLUMN price_currency VARCHAR(3);
ALTER TABLE items ADD COLUMN import_status VARCHAR(16) NOT NULL DEFAULT 'NONE';
ALTER TABLE notifications ADD COLUMN delivery_key TEXT;
CREATE UNIQUE INDEX idx_notification_delivery ON notifications(delivery_key);
-- Remove bearer URLs from historical application audit rows. External logs need operator rotation.
UPDATE security_events SET path = '/api/v1/share/[redacted]' WHERE path LIKE '/api/v1/share/%';
-- Preserve the currency previously displayed for existing prices only.
UPDATE items SET price_currency = 'EUR' WHERE price IS NOT NULL;
UPDATE items SET import_status = 'FAILED', name = COALESCE(url, 'Imported item') WHERE name = 'Loading metadata…';
