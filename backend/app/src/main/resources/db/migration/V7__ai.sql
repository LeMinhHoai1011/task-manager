-- V7 AI minimal MANUAL (no auto-mutate). Idempotent.
-- Baseline: Nhom 07 + 05 S8 + 04 AI-01..05 + 03 EP-194..206 (de xuat).
-- NGUYEN TAC BAT BIEN: AI KHONG BAO GIO viet truc tiep task / approval_decision /
-- task.state. Moi thay doi task di qua TaskService/WorkflowService use case chuan
-- voi actor=user sau khi user accept draft (xem vn.smarttask.ai.service.AiService).

-- Manifest: khai bao truong duoc phep / mau cam / scope cho tung feature.
CREATE TABLE IF NOT EXISTS ai_context_manifest (
  code TEXT PRIMARY KEY,
  allowed_fields TEXT NOT NULL DEFAULT '[]',
  forbidden_patterns TEXT NOT NULL DEFAULT '[]',
  scope_rules TEXT NOT NULL DEFAULT '{}',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Cau hinh model (Ollama): chi SA co grant OLLAMA_CONFIG_MANAGE + reauth moi sua.
CREATE TABLE IF NOT EXISTS ai_model_configuration (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL,
  updated_by UUID,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO ai_model_configuration(key, value) VALUES
  ('ollama.base_url', 'http://localhost:11434'),
  ('ollama.model', 'llama3.1:8b'),
  ('ollama.timeout_ms', '30000'),
  ('ai.max_attempts', '3')
ON CONFLICT (key) DO NOTHING;

-- Job: pipeline QUEUED -> CONTEXT_BUILDING -> RUNNING -> VALIDATING
--        -> REAUTHORIZING -> COMPLETED, nhanh FAILED_TIMEOUT / FAILED_OLLAMA
--        (retry khi attempt < max_attempts), FAILED_VALIDATION /
--        CANCELLED_REVOKED (khong retry).
CREATE TABLE IF NOT EXISTS ai_job (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  principal_user_id UUID NOT NULL,
  feature TEXT NOT NULL CHECK (feature IN ('TASK_DRAFT','SUBTASK_SUGGEST','PRIORITY_SUGGEST',
    'DEADLINE_SUGGEST','ASSIGNEE_SUGGEST','NATURAL_SEARCH','DAILY_PLANNER','WEEKLY_REVIEW')),
  status TEXT NOT NULL DEFAULT 'QUEUED'
    CHECK (status IN ('QUEUED','CONTEXT_BUILDING','RUNNING','VALIDATING','REAUTHORIZING',
      'COMPLETED','FAILED_TIMEOUT','FAILED_OLLAMA','FAILED_VALIDATION','CANCELLED_REVOKED')),
  idempotency_key TEXT NOT NULL UNIQUE,
  input_hint TEXT,
  context_snapshot_ref TEXT,
  output_ref TEXT,
  project_id UUID,
  attempt INT NOT NULL DEFAULT 1,
  max_attempts INT NOT NULL DEFAULT 3,
  timeout_ms BIGINT NOT NULL DEFAULT 30000,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_ai_job_principal ON ai_job (principal_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_ai_job_status ON ai_job (status);

-- Suggestion: draft JSON, chi chuyen ACCEPTED/REJECTED/REDACTED/EXPIRED bang
-- hanh dong tuong minh cua user. Khong co duong dan tu dong thanh task.
CREATE TABLE IF NOT EXISTS ai_suggestion (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  job_id UUID NOT NULL UNIQUE REFERENCES ai_job(id) ON DELETE CASCADE,
  payload TEXT NOT NULL DEFAULT '{}',
  status TEXT NOT NULL DEFAULT 'DRAFT'
    CHECK (status IN ('DRAFT','ACCEPTED','REJECTED','REDACTED','EXPIRED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_ai_suggestion_job ON ai_suggestion (job_id);

-- Interaction: tham chieu prompt trong Object Storage private + retention.
CREATE TABLE IF NOT EXISTS ai_interaction (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  job_id UUID NOT NULL REFERENCES ai_job(id) ON DELETE CASCADE,
  prompt_ref TEXT NOT NULL,
  retention_days INT NOT NULL DEFAULT 30,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_ai_interaction_job ON ai_interaction (job_id);
