-- V12 F3 workers (ADDITIVE / SAFE ONLY).
-- No table/column/index dropped, no data deleted, no V1..V11 file touched.
-- Every statement is re-runnable (IF NOT EXISTS / catalog-guarded DO blocks).
--
-- 1) outbox_event relay columns: claim (owner + expiry), retry/backoff,
--    terminal DEAD, handler dispatch key. `published` is kept for compat and
--    is set when a row reaches DISPATCHED.
-- 2) outbox_dispatch ledger: (event_id, handler) PK gives the dedup
--    (event,handler) index: a claimed row whose ledger entry already exists
--    is SKIPped, never re-dispatched.
-- 3) background_job worker columns: lease owner/expiry, attempts/max,
--    structured error_code, started/finished, retry backoff, per-kind timeout.
-- 4) report_export_job worker columns: private object key + size + error.

-- ---------------------------------------------------------------- outbox
ALTER TABLE outbox_event ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'PENDING';
ALTER TABLE outbox_event ADD COLUMN IF NOT EXISTS handler TEXT;
ALTER TABLE outbox_event ADD COLUMN IF NOT EXISTS attempts INT NOT NULL DEFAULT 0;
ALTER TABLE outbox_event ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE outbox_event ADD COLUMN IF NOT EXISTS claimed_by TEXT;
ALTER TABLE outbox_event ADD COLUMN IF NOT EXISTS claimed_until TIMESTAMPTZ;
ALTER TABLE outbox_event ADD COLUMN IF NOT EXISTS last_error TEXT;

-- Backfill: handler defaults to the event type (dispatch key); rows already
-- published by earlier phases read as DISPATCHED.
DO $$
BEGIN
  UPDATE outbox_event SET handler = type WHERE handler IS NULL;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;
DO $$
BEGIN
  UPDATE outbox_event SET status = 'DISPATCHED' WHERE published AND status = 'PENDING';
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'outbox_event_status_check') THEN
    ALTER TABLE outbox_event
      ADD CONSTRAINT outbox_event_status_check
      CHECK (status IN ('PENDING','DISPATCHED','DEAD')) NOT VALID;
  END IF;
END $$;
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_constraint
             WHERE conname = 'outbox_event_status_check' AND NOT convalidated) THEN
    ALTER TABLE outbox_event VALIDATE CONSTRAINT outbox_event_status_check;
  END IF;
END $$;

-- Relay claim scan: due PENDING rows first.
CREATE INDEX IF NOT EXISTS ix_outbox_claim ON outbox_event (status, next_attempt_at);
-- Handler-scoped idempotency: same (handler, key) never enqueued twice.
CREATE UNIQUE INDEX IF NOT EXISTS uq_outbox_handler_dedup
  ON outbox_event (handler, idempotency_key) WHERE idempotency_key IS NOT NULL;

-- Dispatch ledger: exactly-once consumer evidence per (event, handler).
CREATE TABLE IF NOT EXISTS outbox_dispatch (
  event_id UUID NOT NULL REFERENCES outbox_event(id) ON DELETE CASCADE,
  handler TEXT NOT NULL,
  dispatched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (event_id, handler)
);

-- ---------------------------------------------------------------- jobs
ALTER TABLE background_job ADD COLUMN IF NOT EXISTS owner TEXT;
ALTER TABLE background_job ADD COLUMN IF NOT EXISTS lease_expires_at TIMESTAMPTZ;
ALTER TABLE background_job ADD COLUMN IF NOT EXISTS max_attempts INT NOT NULL DEFAULT 5;
ALTER TABLE background_job ADD COLUMN IF NOT EXISTS error_code TEXT;
ALTER TABLE background_job ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ;
ALTER TABLE background_job ADD COLUMN IF NOT EXISTS finished_at TIMESTAMPTZ;
ALTER TABLE background_job ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE background_job ADD COLUMN IF NOT EXISTS timeout_ms BIGINT NOT NULL DEFAULT 300000;

-- Worker claim scan: QUEUED rows + due FAILED retries.
CREATE INDEX IF NOT EXISTS ix_bgjob_claim ON background_job (status, next_attempt_at);

-- ---------------------------------------------------------------- export
ALTER TABLE report_export_job ADD COLUMN IF NOT EXISTS object_key TEXT;
ALTER TABLE report_export_job ADD COLUMN IF NOT EXISTS file_size_bytes BIGINT;
ALTER TABLE report_export_job ADD COLUMN IF NOT EXISTS error_code TEXT;
ALTER TABLE report_export_job ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
