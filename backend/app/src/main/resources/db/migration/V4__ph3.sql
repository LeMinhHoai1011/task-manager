-- V4 PH-3 Collaboration & Files (manual, no-AI). Idempotent guards via IF NOT EXISTS / ADD COLUMN IF NOT EXISTS.
-- Extends V1 attachment/file_version/file_object/upload_session/comment/activity_event.

-- attachment: lifecycle + soft-delete group
ALTER TABLE attachment ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'AVAILABLE';
ALTER TABLE attachment ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1;
ALTER TABLE attachment ADD COLUMN IF NOT EXISTS target_type TEXT NOT NULL DEFAULT 'TASK';
ALTER TABLE attachment ADD COLUMN IF NOT EXISTS target_id UUID;
ALTER TABLE attachment ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE attachment ADD COLUMN IF NOT EXISTS deleted_by UUID REFERENCES app_user(id);
ALTER TABLE attachment ADD COLUMN IF NOT EXISTS delete_group_id UUID;
DO $$
BEGIN
  ALTER TABLE attachment DROP CONSTRAINT IF EXISTS attachment_status_check;
  ALTER TABLE attachment ADD CONSTRAINT attachment_status_check
    CHECK (status IN ('AVAILABLE','UPLOADING','QUARANTINED','BLOCKED','DELETED'));
EXCEPTION WHEN OTHERS THEN NULL;
END $$;
CREATE INDEX IF NOT EXISTS ix_attachment_target ON attachment (target_type, target_id) WHERE deleted_at IS NULL;

-- file_version: append-only, monotonic per attachment, never overwrite
ALTER TABLE file_version ADD COLUMN IF NOT EXISTS change_note TEXT;
ALTER TABLE file_version ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES app_user(id);
ALTER TABLE file_version ADD COLUMN IF NOT EXISTS checksum_sha256 CHAR(64);
ALTER TABLE file_version ADD COLUMN IF NOT EXISTS mime_type TEXT;
-- uq (attachment_id, version_no) already in V1.

-- file_object: content store + scan state (UNSUPPORTED when no engine)
ALTER TABLE file_object ADD COLUMN IF NOT EXISTS checksum_sha256 CHAR(64);
ALTER TABLE file_object ADD COLUMN IF NOT EXISTS content_type TEXT;
ALTER TABLE file_object ADD COLUMN IF NOT EXISTS scan_status TEXT NOT NULL DEFAULT 'UNSUPPORTED';
DO $$
BEGIN
  ALTER TABLE file_object DROP CONSTRAINT IF EXISTS file_object_scan_check;
  ALTER TABLE file_object ADD CONSTRAINT file_object_scan_check
    CHECK (scan_status IN ('PENDING','CLEAN','INFECTED','UNSUPPORTED'));
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- upload_session: staging flow
ALTER TABLE upload_session ADD COLUMN IF NOT EXISTS owner_user_id UUID REFERENCES app_user(id);
ALTER TABLE upload_session ADD COLUMN IF NOT EXISTS task_id UUID REFERENCES task(id) ON DELETE CASCADE;
ALTER TABLE upload_session ADD COLUMN IF NOT EXISTS file_name TEXT;
ALTER TABLE upload_session ADD COLUMN IF NOT EXISTS mime_type TEXT;
ALTER TABLE upload_session ADD COLUMN IF NOT EXISTS declared_size BIGINT;
ALTER TABLE upload_session ADD COLUMN IF NOT EXISTS staging_key TEXT;
ALTER TABLE upload_session ADD COLUMN IF NOT EXISTS idempotency_key TEXT;
DO $$
BEGIN
  ALTER TABLE upload_session DROP CONSTRAINT IF EXISTS upload_session_status_check;
  ALTER TABLE upload_session ADD CONSTRAINT upload_session_status_check
    CHECK (status IN ('PENDING','UPLOADING','SCAN_PENDING','READY','COMMITTED','EXPIRED','CANCELLED'));
EXCEPTION WHEN OTHERS THEN NULL;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uq_upload_idem ON upload_session (idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_upload_expiry ON upload_session (expires_at) WHERE status NOT IN ('COMMITTED','EXPIRED','CANCELLED');

-- file_access_event: append-only security evidence (ALLOWED + DENIED)
CREATE TABLE IF NOT EXISTS file_access_event (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  attachment_id UUID REFERENCES attachment(id) ON DELETE SET NULL,
  file_version_id UUID REFERENCES file_version(id) ON DELETE SET NULL,
  actor_user_id UUID REFERENCES app_user(id),
  action TEXT NOT NULL CHECK (action IN ('PREVIEW','DOWNLOAD','STREAM','UPLOAD','COMMIT','RESTORE','DELETE')),
  outcome TEXT NOT NULL CHECK (outcome IN ('ALLOWED','DENIED')),
  reason TEXT,
  correlation_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_file_access_att ON file_access_event (attachment_id, created_at DESC);

-- stream_ticket: single-use, TTL 30s
CREATE TABLE IF NOT EXISTS stream_ticket (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  attachment_id UUID NOT NULL REFERENCES attachment(id) ON DELETE CASCADE,
  file_version_id UUID REFERENCES file_version(id) ON DELETE CASCADE,
  ticket_hash TEXT NOT NULL UNIQUE,
  actor_user_id UUID REFERENCES app_user(id),
  used BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_ticket_expiry ON stream_ticket (expires_at) WHERE used = FALSE;

-- activity_event: ensure PH-3 columns (V1 has task_id/project_id/kind/payload)
ALTER TABLE activity_event ADD COLUMN IF NOT EXISTS target_type TEXT;
ALTER TABLE activity_event ADD COLUMN IF NOT EXISTS target_id UUID;
ALTER TABLE activity_event ADD COLUMN IF NOT EXISTS workspace_id UUID REFERENCES workspace(id) ON DELETE CASCADE;
ALTER TABLE activity_event ADD COLUMN IF NOT EXISTS summary_key TEXT;
ALTER TABLE activity_event ADD COLUMN IF NOT EXISTS occurred_at TIMESTAMPTZ;
ALTER TABLE activity_event ADD COLUMN IF NOT EXISTS correlation_id UUID;
DO $$
BEGIN
  UPDATE activity_event SET occurred_at = created_at WHERE occurred_at IS NULL;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;
CREATE INDEX IF NOT EXISTS ix_activity_target_time ON activity_event (target_type, target_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_activity_project_time ON activity_event (project_id, created_at DESC);

-- comment body length guard (8000)
DO $$
BEGIN
  ALTER TABLE comment DROP CONSTRAINT IF EXISTS comment_body_len_check;
  ALTER TABLE comment ADD CONSTRAINT comment_body_len_check
    CHECK (char_length(body) BETWEEN 1 AND 8000);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- system_setting seeds for files (V1 table is key/value JSONB)
INSERT INTO system_setting(key, value) VALUES
  ('file.max_upload_mb', '50'),
  ('file.allowed_content_types', '["application/pdf","image/png","image/jpeg","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/zip","text/csv"]'),
  ('file.scan_enabled', 'false')
ON CONFLICT (key) DO NOTHING;
