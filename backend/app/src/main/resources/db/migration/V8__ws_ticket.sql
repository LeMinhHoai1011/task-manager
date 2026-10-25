-- V8 F0 Security Foundation: short-lived single-use WS handshake tickets.
-- ADDITIVE ONLY: no existing table is altered.
CREATE TABLE IF NOT EXISTS realtime_ticket (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES user_session(id) ON DELETE CASCADE,
  ticket_hash TEXT NOT NULL UNIQUE,
  used BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_realtime_ticket_session ON realtime_ticket (session_id);
