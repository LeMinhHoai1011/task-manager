-- V17: SmartTask V3.0 Features (Notes, Progress Logs, Web Push)

-- 1. Notes (Personal, Shared, Project, Task)
CREATE TABLE IF NOT EXISTS note (
  id UUID PRIMARY KEY,
  workspace_id UUID REFERENCES workspace(id) ON DELETE SET NULL,
  project_id UUID REFERENCES project(id) ON DELETE CASCADE,
  task_id UUID REFERENCES task(id) ON DELETE CASCADE,
  owner_user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  scope VARCHAR(20) NOT NULL DEFAULT 'PERSONAL' CHECK (scope IN ('PERSONAL','PROJECT','SHARED','TASK')),
  title VARCHAR(255) NOT NULL,
  content TEXT NOT NULL DEFAULT '',
  tags TEXT,
  is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
  version BIGINT NOT NULL DEFAULT 1,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS ix_note_owner ON note(owner_user_id, updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_note_project ON note(project_id, updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_note_task ON note(task_id, updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_note_pinned ON note(owner_user_id, is_pinned) WHERE deleted_at IS NULL AND is_pinned = TRUE;

-- 2. Project Progress Log (Manual logged progress vs computed task progress)
CREATE TABLE IF NOT EXISTS progress_log (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  update_date DATE NOT NULL,
  progress_percent INT NOT NULL CHECK (progress_percent BETWEEN 0 AND 100),
  summary TEXT NOT NULL,
  completed_work TEXT,
  blockers TEXT,
  next_plan TEXT,
  created_by UUID NOT NULL REFERENCES app_user(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS ix_progress_log_project ON progress_log(project_id, update_date DESC, created_at DESC);

-- 3. Web Push Platform Support in device_push_registration
ALTER TABLE device_push_registration DROP CONSTRAINT IF EXISTS device_push_registration_platform_check;
ALTER TABLE device_push_registration ADD CONSTRAINT device_push_registration_platform_check
  CHECK (platform IN ('ANDROID_FCM','IOS_APNS','WEB_PUSH'));

ALTER TABLE device_push_registration ADD COLUMN IF NOT EXISTS endpoint TEXT;
ALTER TABLE device_push_registration ADD COLUMN IF NOT EXISTS auth_key TEXT;
ALTER TABLE device_push_registration ADD COLUMN IF NOT EXISTS p256dh_key TEXT;
