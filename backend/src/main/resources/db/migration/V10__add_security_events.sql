CREATE TABLE security_events (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    actor_id TEXT,
    client_ip TEXT,
    path TEXT,
    details TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX idx_security_events_type_created ON security_events(type, created_at);
CREATE INDEX idx_security_events_actor_created ON security_events(actor_id, created_at);
