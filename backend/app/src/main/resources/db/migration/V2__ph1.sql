-- V2 PH-1 additions (manual, no-AI). Adds columns missing from V1.
-- Idempotent guards via DO blocks.

-- app_user
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS protected_flag BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS deleted_by UUID REFERENCES app_user(id);

-- user_session
ALTER TABLE user_session ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE user_session ADD COLUMN IF NOT EXISTS device_label TEXT;
ALTER TABLE user_session ADD COLUMN IF NOT EXISTS client_type TEXT;

-- user_refresh_token
ALTER TABLE user_refresh_token ADD COLUMN IF NOT EXISTS session_id UUID REFERENCES user_session(id) ON DELETE CASCADE;
ALTER TABLE user_refresh_token ADD COLUMN IF NOT EXISTS rotated_from_id UUID REFERENCES user_refresh_token(id);

-- reauth_ticket + recovery_code
CREATE TABLE IF NOT EXISTS reauth_ticket (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  action TEXT NOT NULL,
  target_id UUID,
  used BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE IF NOT EXISTS recovery_code (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  code_hash TEXT NOT NULL,
  used BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ
);

-- workspace
ALTER TABLE workspace ADD COLUMN IF NOT EXISTS type TEXT NOT NULL DEFAULT 'TEAM';
ALTER TABLE workspace ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE workspace ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1;
ALTER TABLE workspace ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE workspace ADD COLUMN IF NOT EXISTS deleted_by UUID REFERENCES app_user(id);

-- workspace_membership: allow GUEST + status
ALTER TABLE workspace_membership ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'ACTIVE';
DO $$
BEGIN
  ALTER TABLE workspace_membership DROP CONSTRAINT IF EXISTS workspace_membership_role_check;
  ALTER TABLE workspace_membership ADD CONSTRAINT workspace_membership_role_check
    CHECK (role IN ('OWNER','ADMIN','MEMBER','GUEST','VIEWER'));
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- workspace_invitation
ALTER TABLE workspace_invitation ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'PENDING';

-- project
ALTER TABLE project ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'PLANNING';
ALTER TABLE project ADD COLUMN IF NOT EXISTS previous_status TEXT;
ALTER TABLE project ADD COLUMN IF NOT EXISTS approval_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE project ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1;
ALTER TABLE project ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE project ADD COLUMN IF NOT EXISTS deleted_by UUID REFERENCES app_user(id);

-- project_membership: MANAGER/MEMBER/GUEST + status
ALTER TABLE project_membership ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'ACTIVE';
DO $$
BEGIN
  ALTER TABLE project_membership DROP CONSTRAINT IF EXISTS project_membership_role_check;
  ALTER TABLE project_membership ADD CONSTRAINT project_membership_role_check
    CHECK (role IN ('MANAGER','MEMBER','GUEST','OWNER','ADMIN','VIEWER'));
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- audit_event
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS target_type TEXT;
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS target_id UUID;
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS before_json JSONB;
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS after_json JSONB;
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS reason TEXT;
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS outcome TEXT NOT NULL DEFAULT 'ALLOWED';
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS scope_type TEXT;
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS scope_id UUID;
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS occurred_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- outbox_event
ALTER TABLE outbox_event ADD COLUMN IF NOT EXISTS correlation_id UUID;
ALTER TABLE outbox_event ADD COLUMN IF NOT EXISTS idempotency_key TEXT;

-- background_job: widen statuses
ALTER TABLE background_job ADD COLUMN IF NOT EXISTS correlation_id UUID;
ALTER TABLE background_job ADD COLUMN IF NOT EXISTS idempotency_key TEXT;
DO $$
BEGIN
  ALTER TABLE background_job ALTER COLUMN status SET DEFAULT 'QUEUED';
EXCEPTION WHEN OTHERS THEN NULL;
END $$;
