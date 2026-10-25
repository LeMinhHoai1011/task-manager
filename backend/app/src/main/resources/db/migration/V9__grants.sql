-- V9 F1 grant store: global permission grants (deny-by-default).
-- ADDITIVE ONLY: no existing table is altered.
CREATE TABLE IF NOT EXISTS global_permission_grant (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  code TEXT NOT NULL,
  scope_type TEXT NOT NULL DEFAULT 'GLOBAL',
  scope_id UUID NULL,
  granted_by UUID NULL REFERENCES app_user(id) ON DELETE SET NULL,
  reason TEXT NULL,
  expires_at TIMESTAMPTZ NULL,
  revoked_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- One live grant per (user, code, scope). Revoked rows stay as history.
CREATE UNIQUE INDEX IF NOT EXISTS uq_live_grant
  ON global_permission_grant (user_id, code, scope_type, scope_id)
  WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_grant_user ON global_permission_grant (user_id);
CREATE INDEX IF NOT EXISTS ix_grant_code ON global_permission_grant (code);
