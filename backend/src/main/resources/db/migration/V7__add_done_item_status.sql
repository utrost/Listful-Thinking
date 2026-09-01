-- flyway:executeInTransaction=false
PRAGMA foreign_keys=OFF;

CREATE TABLE items_new (
    id TEXT PRIMARY KEY,
    list_id TEXT NOT NULL,
    name TEXT NOT NULL,
    url TEXT,
    image_url TEXT,
    description TEXT,
    price NUMERIC,
    status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLAIMED', 'PURCHASED', 'DONE')),
    due_date TEXT,
    recurrence_rule TEXT,
    reserved_by_guest TEXT,
    quantity TEXT,
    category TEXT,
    FOREIGN KEY (list_id) REFERENCES lists(id) ON DELETE CASCADE
);

INSERT INTO items_new (id, list_id, name, url, image_url, description, price, status, due_date, recurrence_rule, reserved_by_guest, quantity, category)
SELECT id, list_id, name, url, image_url, description, price, status, due_date, recurrence_rule, reserved_by_guest, quantity, category
FROM items;

DROP TABLE items;
ALTER TABLE items_new RENAME TO items;

CREATE INDEX idx_items_list_id ON items(list_id);

PRAGMA foreign_keys=ON;
