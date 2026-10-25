-- V5 PH-4 Calendar & Notifications (manual, no-AI). Idempotent.

-- app_user profile: timezone + quiet hours (single per user, DEC-52 option a)
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS timezone TEXT NOT NULL DEFAULT 'UTC';
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS locale TEXT NOT NULL DEFAULT 'vi-VN';
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS quiet_hours_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS quiet_start TIME;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS quiet_end TIME;

-- reminder_rule: owner + pause/resume
ALTER TABLE reminder_rule ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE reminder_rule ADD COLUMN IF NOT EXISTS paused BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE reminder_rule ADD COLUMN IF NOT EXISTS rule_kind TEXT NOT NULL DEFAULT 'ONE_SHOT';
ALTER TABLE reminder_rule ADD COLUMN IF NOT EXISTS digest_sent BOOLEAN NOT NULL DEFAULT FALSE;
DO $$
BEGIN
  ALTER TABLE reminder_rule DROP CONSTRAINT IF EXISTS reminder_rule_status_check;
  ALTER TABLE reminder_rule ADD CONSTRAINT reminder_rule_status_check
    CHECK (status IN ('ACTIVE','PAUSED','FIRED','CANCELLED'));
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- reminder_schedule: computed fire rows (recomputed on pause/resume)
CREATE TABLE IF NOT EXISTS reminder_schedule (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  reminder_rule_id UUID NOT NULL REFERENCES reminder_rule(id) ON DELETE CASCADE,
  fire_at TIMESTAMPTZ NOT NULL,
  status TEXT NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED','SENT','SKIPPED_PAUSED','CANCELLED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (reminder_rule_id, fire_at)
);

-- task_deadline_change: append-only history (deadline instant never rewritten)
CREATE TABLE IF NOT EXISTS task_deadline_change (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  task_id UUID NOT NULL REFERENCES task(id) ON DELETE CASCADE,
  old_due_at TIMESTAMPTZ,
  new_due_at TIMESTAMPTZ,
  timezone TEXT,
  actor_user_id UUID REFERENCES app_user(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- calendar_export_record: append-only
CREATE TABLE IF NOT EXISTS calendar_export_record (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  range_from TIMESTAMPTZ NOT NULL,
  range_to TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- notification: full PH-4 columns (V1 has user_id/kind/payload/read/created_at)
ALTER TABLE notification ADD COLUMN IF NOT EXISTS seq BIGSERIAL UNIQUE;
ALTER TABLE notification ADD COLUMN IF NOT EXISTS type TEXT;
ALTER TABLE notification ADD COLUMN IF NOT EXISTS severity TEXT NOT NULL DEFAULT 'NORMAL';
ALTER TABLE notification ADD COLUMN IF NOT EXISTS scope_type TEXT NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE notification ADD COLUMN IF NOT EXISTS scope_id UUID;
ALTER TABLE notification ADD COLUMN IF NOT EXISTS title_key TEXT;
ALTER TABLE notification ADD COLUMN IF NOT EXISTS body_key TEXT;
ALTER TABLE notification ADD COLUMN IF NOT EXISTS deep_link TEXT;
ALTER TABLE notification ADD COLUMN IF NOT EXISTS redacted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE notification ADD COLUMN IF NOT EXISTS redacted_at TIMESTAMPTZ;
ALTER TABLE notification ADD COLUMN IF NOT EXISTS redaction_reason TEXT;
ALTER TABLE notification ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ;
ALTER TABLE notification ADD COLUMN IF NOT EXISTS occurred_at TIMESTAMPTZ;
ALTER TABLE notification ADD COLUMN IF NOT EXISTS dedup_token TEXT;
ALTER TABLE notification ADD COLUMN IF NOT EXISTS quiet_hours_deferred_until TIMESTAMPTZ;
ALTER TABLE notification ADD COLUMN IF NOT EXISTS session_id_bound UUID REFERENCES user_session(id) ON DELETE SET NULL;
DO $$
BEGIN
  UPDATE notification SET type = kind WHERE type IS NULL;
  UPDATE notification SET occurred_at = created_at WHERE occurred_at IS NULL;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;
DO $$
BEGIN
  ALTER TABLE notification DROP CONSTRAINT IF EXISTS notification_severity_check;
  ALTER TABLE notification ADD CONSTRAINT notification_severity_check
    CHECK (severity IN ('NORMAL','IMPORTANT','SECURITY_CRITICAL'));
EXCEPTION WHEN OTHERS THEN NULL;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_dedup ON notification (dedup_token) WHERE dedup_token IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_notification_user_unread ON notification (user_id, occurred_at DESC)
  WHERE read_at IS NULL AND redacted = FALSE;
CREATE INDEX IF NOT EXISTS ix_notification_scope ON notification (scope_type, scope_id);
-- backfill seq for BIGSERIAL on existing rows happens automatically via default on insert only;
-- existing NULL seq rows: assign now
DO $$
BEGIN
  UPDATE notification SET seq = nextval(pg_get_serial_sequence('notification','seq')) WHERE seq IS NULL;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- notification_preference: mute-per-type only (quiet hours live on app_user)
CREATE TABLE IF NOT EXISTS notification_preference (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  type TEXT NOT NULL,
  channel_in_app BOOLEAN NOT NULL DEFAULT TRUE,
  channel_push BOOLEAN NOT NULL DEFAULT TRUE,
  channel_email BOOLEAN NOT NULL DEFAULT FALSE,
  muted BOOLEAN NOT NULL DEFAULT FALSE,
  UNIQUE (user_id, type)
);

-- device_push_registration: provider stub (no real FCM/APNs in V1)
CREATE TABLE IF NOT EXISTS device_push_registration (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  session_id UUID NOT NULL REFERENCES user_session(id) ON DELETE CASCADE,
  platform TEXT NOT NULL CHECK (platform IN ('ANDROID_FCM','IOS_APNS')),
  push_token_hash TEXT NOT NULL UNIQUE,
  device_label TEXT,
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','UNREGISTERED','INVALID')),
  registered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  unregistered_at TIMESTAMPTZ,
  last_success_at TIMESTAMPTZ,
  failure_count INT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS ix_push_session ON device_push_registration (session_id) WHERE status = 'ACTIVE';

-- delivery_attempt: append-only provider stub log
CREATE TABLE IF NOT EXISTS delivery_attempt (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  notification_id UUID NOT NULL REFERENCES notification(id) ON DELETE CASCADE,
  channel TEXT NOT NULL CHECK (channel IN ('IN_APP','PUSH','EMAIL')),
  provider TEXT,
  status TEXT NOT NULL CHECK (status IN ('QUEUED','SENT','FAILED_PERMANENT','FAILED_RETRYABLE')),
  attempt_no INT NOT NULL DEFAULT 1,
  error_code TEXT,
  error_detail TEXT,
  dedup_token TEXT NOT NULL,
  correlation_id UUID,
  attempted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (notification_id, channel, attempt_no)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_delivery_dedup ON delivery_attempt (dedup_token);

-- realtime_cursor: ARCH-09 resync
CREATE TABLE IF NOT EXISTS realtime_cursor (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  session_id UUID REFERENCES user_session(id) ON DELETE CASCADE,
  last_seen_seq BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (session_id)
);

INSERT INTO system_setting(key, value) VALUES
  ('notification.quiet_hours_default_enabled', 'false'),
  ('notification.push_configured', 'false'),
  ('notification.email_configured', 'false')
ON CONFLICT (key) DO NOTHING;
