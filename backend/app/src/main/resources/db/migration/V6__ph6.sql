-- V6 PH-6 Reports & Export (manual, no-AI). Idempotent.

-- metric_daily_snapshot: append-only per day
CREATE TABLE IF NOT EXISTS metric_daily_snapshot (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  scope_type TEXT NOT NULL CHECK (scope_type IN ('PROJECT','WORKSPACE','USER','SYSTEM')),
  scope_id UUID NOT NULL,
  as_of_date DATE NOT NULL,
  metric_code TEXT NOT NULL,
  value_numeric NUMERIC(18,4),
  value_int BIGINT,
  value_state TEXT NOT NULL CHECK (value_state IN ('OK','NOT_APPLICABLE','NO_PERMISSION','INSUFFICIENT_DATA','PARTIAL')),
  numerator INT,
  denominator INT,
  computed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  computed_by TEXT NOT NULL DEFAULT 'JOB' CHECK (computed_by IN ('JOB','MANUAL_RECOMPUTE')),
  job_id UUID,
  UNIQUE (scope_type, scope_id, as_of_date, metric_code)
);
CREATE INDEX IF NOT EXISTS ix_snapshot_scope_date ON metric_daily_snapshot (scope_type, scope_id, as_of_date DESC);

-- report_definition + seed (no R-07 ranking metric exists by design)
CREATE TABLE IF NOT EXISTS report_definition (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code TEXT NOT NULL UNIQUE,
  name_key TEXT NOT NULL,
  scope_type TEXT NOT NULL CHECK (scope_type IN ('PERSONAL','PROJECT','WORKSPACE','SYSTEM')),
  metric_codes TEXT[] NOT NULL,
  default_period TEXT,
  export_formats TEXT[] NOT NULL DEFAULT '{PDF,XLSX,CSV}',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  requires_reauth BOOLEAN NOT NULL DEFAULT FALSE,
  max_rows INT NOT NULL DEFAULT 100000,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO report_definition(code, name_key, scope_type, metric_codes) VALUES
  ('PERSONAL_SUMMARY', 'report.personal.summary', 'PERSONAL',
   '{PERSONAL_ASSIGNED,PERSONAL_CREATED,PERSONAL_MANAGED}'),
  ('PROJECT_PROGRESS', 'report.project.progress', 'PROJECT',
   '{PROGRESS_PERCENT,TASK_UNIQUE_TOTAL,TASK_ASSIGNMENT_COUNT,TASK_BY_STATE_TODO,TASK_BY_STATE_IN_PROGRESS,TASK_BY_STATE_PENDING_APPROVAL,TASK_BY_STATE_COMPLETED,TASK_BY_STATE_CANCELLED,TASK_BLOCKED_COUNT,TASK_OVERDUE_COUNT,TASK_OVERDUE_PENDING_APPROVAL_COUNT,MILESTONE_OPEN_COUNT,MILESTONE_OVERDUE_COUNT,APPROVAL_PENDING_COUNT,APPROVAL_APPROVED_COUNT,APPROVAL_REJECTED_COUNT,TIMELOG_MINUTES_TOTAL,FILE_STORAGE_BYTES,FILE_COUNT}'),
  ('WORKSPACE_OVERVIEW', 'report.workspace.overview', 'WORKSPACE',
   '{PROGRESS_PERCENT,TASK_UNIQUE_TOTAL,TASK_ASSIGNMENT_COUNT,APPROVAL_PENDING_COUNT,TIMELOG_MINUTES_TOTAL}'),
  ('SYSTEM_HEALTH', 'report.system.health', 'SYSTEM',
   '{TASK_UNIQUE_TOTAL,APPROVAL_NO_APPROVER_COUNT}')
ON CONFLICT (code) DO NOTHING;

-- report_export_job: durable job + TTL 24h + double authz evidence
CREATE TABLE IF NOT EXISTS report_export_job (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  background_job_id UUID REFERENCES background_job(id) ON DELETE SET NULL,
  report_code TEXT NOT NULL REFERENCES report_definition(code),
  scope_type TEXT NOT NULL,
  scope_id UUID,
  period_from DATE NOT NULL,
  period_to DATE NOT NULL,
  format TEXT NOT NULL CHECK (format IN ('PDF','XLSX','CSV')),
  requested_by UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  status TEXT NOT NULL DEFAULT 'QUEUED'
    CHECK (status IN ('QUEUED','RUNNING','REAUTHORIZING','GENERATED','DELIVERED','FAILED','CANCELLED_REVOKED','EXPIRED')),
  attachment_id UUID REFERENCES attachment(id) ON DELETE SET NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  row_count BIGINT,
  authorized_at_generation TIMESTAMPTZ,
  authorized_at_delivery TIMESTAMPTZ,
  idempotency_key TEXT NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_export_expiry ON report_export_job (expires_at)
  WHERE status IN ('GENERATED','DELIVERED');
CREATE INDEX IF NOT EXISTS ix_export_user ON report_export_job (requested_by, created_at DESC);

INSERT INTO system_setting(key, value) VALUES
  ('report.export_ttl_hours', '24')
ON CONFLICT (key) DO NOTHING;
