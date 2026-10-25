-- V10 F2.1 integrity hardening (F2.1 Database Audit).
-- ADDITIVE / SAFE ONLY: no table/column/index dropped, no data deleted,
-- no V1..V9 file touched. Every statement is re-runnable
-- (IF NOT EXISTS / catalog-guarded DO blocks).
--
-- Precondition for the new UNIQUE INDEX builds: no duplicate rows exist.
-- True on fresh installs and on the empty V9-upgrade path (verified during
-- F2.1 against PostgreSQL 16). On a populated production copy, de-duplicate
-- first, then apply.
--
-- Deliberately NOT in V10 (documented drift, service-level only):
--   * last-manager guard, project guest-check, task dependency cycle,
--     self-approval ban -> enforced in ProjectService/TaskService/
--     WorkflowService, covered by unit tests, NOT by DB triggers
--     (cross-table triggers were judged too risky / hard to test here).
--   * timelog 30-day window -> TaskService guard + V3 work_date CHECK stay.
--   * ai_job FK stays NOT VALID (no full-scan lock); the remaining
--     VALIDATE CONSTRAINT step is documented below and NOT_TESTED here.

-- 1) ChecklistItem UQ(task_id, position): missing on the SQL side,
--    JPA entity already declares uniqueConstraints(task_id, position).
CREATE UNIQUE INDEX IF NOT EXISTS uq_checklist_task_position
  ON checklist_item (task_id, position);

-- 2) Personal-workspace 1-1: at most one live PERSONAL workspace per owner.
--    Matches IdentityService.ensurePersonalWorkspace
--    (countByOwnerUserIdAndTypeAndStatusNot(..., "PERSONAL", "DELETED")).
CREATE UNIQUE INDEX IF NOT EXISTS uq_personal_workspace_owner
  ON workspace (owner_user_id)
  WHERE type = 'PERSONAL' AND status <> 'DELETED';

-- 2b) Live-grant NULL-scope hardening: the V9 uq_live_grant btree treats
--    NULL scope_id as distinct, so duplicate live GLOBAL grants (scope_id NULL)
--    slipped through. This additive partial index closes the gap with
--    COALESCE (V9 file untouched).
CREATE UNIQUE INDEX IF NOT EXISTS uq_live_grant_noscope
  ON global_permission_grant
    (user_id, code, scope_type, (COALESCE(scope_id, '00000000-0000-0000-0000-000000000000'::uuid)))
  WHERE revoked_at IS NULL;

-- 3) Outbox + background-job idempotency: one row per non-null key.
--    NULL keys stay repeatable (partial index).
CREATE UNIQUE INDEX IF NOT EXISTS uq_outbox_idem_key
  ON outbox_event (idempotency_key)
  WHERE idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_bgjob_idem_key
  ON background_job (idempotency_key)
  WHERE idempotency_key IS NOT NULL;

-- 4) Ticket expiry sanity: expires_at must be after created_at.
--    TTL length itself (stream 30s / realtime 60s) stays service-enforced
--    because the two ticket tables have different TTLs.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stream_ticket_expiry_check') THEN
    ALTER TABLE stream_ticket
      ADD CONSTRAINT stream_ticket_expiry_check CHECK (expires_at > created_at) NOT VALID;
  END IF;
END $$;
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_constraint
             WHERE conname = 'stream_ticket_expiry_check' AND NOT convalidated) THEN
    ALTER TABLE stream_ticket VALIDATE CONSTRAINT stream_ticket_expiry_check;
  END IF;
END $$;
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'realtime_ticket_expiry_check') THEN
    ALTER TABLE realtime_ticket
      ADD CONSTRAINT realtime_ticket_expiry_check CHECK (expires_at > created_at) NOT VALID;
  END IF;
END $$;
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_constraint
             WHERE conname = 'realtime_ticket_expiry_check' AND NOT convalidated) THEN
    ALTER TABLE realtime_ticket VALIDATE CONSTRAINT realtime_ticket_expiry_check;
  END IF;
END $$;

-- 5) Export scope domain (matches report_definition scope CHECK
--    and DashboardComposer.Scope PERSONAL/PROJECT/WORKSPACE/SYSTEM).
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'report_export_job_scope_check') THEN
    ALTER TABLE report_export_job
      ADD CONSTRAINT report_export_job_scope_check
      CHECK (scope_type IN ('PERSONAL','PROJECT','WORKSPACE','SYSTEM')) NOT VALID;
  END IF;
END $$;
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_constraint
             WHERE conname = 'report_export_job_scope_check' AND NOT convalidated) THEN
    ALTER TABLE report_export_job VALIDATE CONSTRAINT report_export_job_scope_check;
  END IF;
END $$;

-- 6) AiJob.principal FK (was missing on the SQL side).
--    NOT VALID avoids a full table scan / long lock on existing deployments.
--    REMAINING (NOT_TESTED): run once in a maintenance window:
--      ALTER TABLE ai_job VALIDATE CONSTRAINT fk_ai_job_principal;
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_ai_job_principal') THEN
    ALTER TABLE ai_job
      ADD CONSTRAINT fk_ai_job_principal
      FOREIGN KEY (principal_user_id) REFERENCES app_user (id) ON DELETE CASCADE
      NOT VALID;
  END IF;
END $$;
