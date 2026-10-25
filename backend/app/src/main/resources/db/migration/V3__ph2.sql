-- V3 PH-2 Task & Workflow (manual, no-AI). Extends V1 task/workflow/comment tables.
-- All DDL idempotent (ADD COLUMN IF NOT EXISTS / CREATE TABLE IF NOT EXISTS).

-- project: member-create flag
ALTER TABLE project ADD COLUMN IF NOT EXISTS allow_member_create_task BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE project ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;
ALTER TABLE project ADD COLUMN IF NOT EXISTS completed_by UUID REFERENCES app_user(id);

-- milestone: status for PJ-05 cascade
ALTER TABLE milestone ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'OPEN';
ALTER TABLE milestone ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ;
ALTER TABLE milestone ADD COLUMN IF NOT EXISTS cancelled_reason TEXT;
DO $$
BEGIN
  ALTER TABLE milestone DROP CONSTRAINT IF EXISTS milestone_status_check;
  ALTER TABLE milestone ADD CONSTRAINT milestone_status_check
    CHECK (status IN ('OPEN','CANCELLED','DONE'));
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- task: full PH-2 columns
ALTER TABLE task ADD COLUMN IF NOT EXISTS blocked_reason TEXT;
ALTER TABLE task ADD COLUMN IF NOT EXISTS start_at TIMESTAMPTZ;
ALTER TABLE task ADD COLUMN IF NOT EXISTS due_timezone TEXT;
ALTER TABLE task ADD COLUMN IF NOT EXISTS due_is_date_only BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE task ADD COLUMN IF NOT EXISTS milestone_id UUID REFERENCES milestone(id);
ALTER TABLE task ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE task ADD COLUMN IF NOT EXISTS deleted_by UUID REFERENCES app_user(id);
ALTER TABLE task ADD COLUMN IF NOT EXISTS delete_group_id UUID;
ALTER TABLE task ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;
ALTER TABLE task ADD COLUMN IF NOT EXISTS completed_by UUID REFERENCES app_user(id);
ALTER TABLE task ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ;
ALTER TABLE task ADD COLUMN IF NOT EXISTS cancelled_by UUID REFERENCES app_user(id);
ALTER TABLE task ADD COLUMN IF NOT EXISTS cancelled_reason TEXT;
ALTER TABLE task ADD COLUMN IF NOT EXISTS reopened_at TIMESTAMPTZ;
ALTER TABLE task ADD COLUMN IF NOT EXISTS reopened_by UUID REFERENCES app_user(id);
ALTER TABLE task ADD COLUMN IF NOT EXISTS reopened_reason TEXT;
ALTER TABLE task ADD COLUMN IF NOT EXISTS allow_member_create_task BOOLEAN NOT NULL DEFAULT FALSE;
DO $$
BEGIN
  ALTER TABLE task DROP CONSTRAINT IF EXISTS task_priority_check;
  ALTER TABLE task ADD CONSTRAINT task_priority_check
    CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT'));
EXCEPTION WHEN OTHERS THEN NULL;
END $$;
DO $$
BEGIN
  ALTER TABLE task DROP CONSTRAINT IF EXISTS task_blocked_reason_check;
  ALTER TABLE task ADD CONSTRAINT task_blocked_reason_check
    CHECK (NOT blocked_flag OR blocked_reason IS NOT NULL);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;
DO $$
BEGIN
  ALTER TABLE task DROP CONSTRAINT IF EXISTS task_subtask_milestone_check;
  ALTER TABLE task ADD CONSTRAINT task_subtask_milestone_check
    CHECK (depth = 0 OR milestone_id IS NULL);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;
-- rename parent_task_id stays; add parent_id alias view? keep parent_task_id as canonical.
-- depth CHECK already 0/1 in V1.

-- task_assignment: status + removal_reason
ALTER TABLE task_assignment ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE task_assignment ADD COLUMN IF NOT EXISTS removal_reason TEXT;
DO $$
BEGIN
  ALTER TABLE task_assignment DROP CONSTRAINT IF EXISTS task_assignment_status_check;
  ALTER TABLE task_assignment ADD CONSTRAINT task_assignment_status_check
    CHECK (status IN ('ACTIVE','REMOVED'));
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- task_tag
CREATE TABLE IF NOT EXISTS task_tag (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  task_id UUID NOT NULL REFERENCES task(id) ON DELETE CASCADE,
  tag TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (task_id, tag)
);

-- time_log
CREATE TABLE IF NOT EXISTS time_log (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  task_id UUID NOT NULL REFERENCES task(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  work_date DATE NOT NULL,
  minutes INT NOT NULL CHECK (minutes > 0),
  note TEXT,
  locked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (work_date <= CURRENT_DATE)
);

-- approval_round: PH-2 columns (V1 has submitter_user_id/status PENDING)
ALTER TABLE approval_round ADD COLUMN IF NOT EXISTS submitted_by UUID REFERENCES app_user(id);
ALTER TABLE approval_round ADD COLUMN IF NOT EXISTS round_outcome TEXT;
ALTER TABLE approval_round ADD COLUMN IF NOT EXISTS sla_breached BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE approval_round ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1;
DO $$
BEGIN
  UPDATE approval_round SET submitted_by = submitter_user_id WHERE submitted_by IS NULL;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;
ALTER TABLE approval_round ALTER COLUMN submitted_by SET NOT NULL;
DO $$
BEGIN
  ALTER TABLE approval_round DROP CONSTRAINT IF EXISTS approval_round_status_check;
  ALTER TABLE approval_round ADD CONSTRAINT approval_round_status_check
    CHECK (status IN ('OPEN','APPROVED','REJECTED','CANCELLED','PENDING'));
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- approval_designation
ALTER TABLE approval_designation ADD COLUMN IF NOT EXISTS type TEXT NOT NULL DEFAULT 'DEFAULT';
ALTER TABLE approval_designation ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMPTZ;
ALTER TABLE approval_designation ADD COLUMN IF NOT EXISTS revoked_reason TEXT;
DO $$
BEGIN
  ALTER TABLE approval_designation DROP CONSTRAINT IF EXISTS approval_designation_type_check;
  ALTER TABLE approval_designation ADD CONSTRAINT approval_designation_type_check
    CHECK (type IN ('DEFAULT','DESIGNATED'));
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- approval_decision: terminal single row per round
ALTER TABLE approval_decision ADD COLUMN IF NOT EXISTS outcome TEXT;
ALTER TABLE approval_decision ADD COLUMN IF NOT EXISTS reason TEXT;
ALTER TABLE approval_decision ADD COLUMN IF NOT EXISTS decided_by UUID REFERENCES app_user(id);
ALTER TABLE approval_decision ADD COLUMN IF NOT EXISTS decided_at TIMESTAMPTZ;
DO $$
BEGIN
  UPDATE approval_decision SET outcome = decision WHERE outcome IS NULL;
  UPDATE approval_decision SET decided_by = decider_user_id WHERE decided_by IS NULL;
  UPDATE approval_decision SET decided_at = created_at WHERE decided_at IS NULL;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;
DO $$
BEGIN
  ALTER TABLE approval_decision DROP CONSTRAINT IF EXISTS approval_decision_outcome_check;
  ALTER TABLE approval_decision ADD CONSTRAINT approval_decision_outcome_check
    CHECK (outcome IN ('APPROVED','REJECTED','CANCELLED'));
EXCEPTION WHEN OTHERS THEN NULL;
END $$;
DO $$
BEGIN
  ALTER TABLE approval_decision DROP CONSTRAINT IF EXISTS approval_decision_reject_reason_check;
  ALTER TABLE approval_decision ADD CONSTRAINT approval_decision_reject_reason_check
    CHECK (outcome <> 'REJECTED' OR reason IS NULL OR char_length(reason) >= 3);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uq_approval_decision_round ON approval_decision(round_id);

-- comment: depth/threading + soft delete
ALTER TABLE comment ADD COLUMN IF NOT EXISTS parent_comment_id UUID REFERENCES comment(id);
ALTER TABLE comment ADD COLUMN IF NOT EXISTS depth INT NOT NULL DEFAULT 0;
ALTER TABLE comment ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE comment ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE comment ADD COLUMN IF NOT EXISTS delete_reason TEXT;
DO $$
BEGIN
  ALTER TABLE comment DROP CONSTRAINT IF EXISTS comment_depth_check;
  ALTER TABLE comment ADD CONSTRAINT comment_depth_check CHECK (depth IN (0,1));
EXCEPTION WHEN OTHERS THEN NULL;
END $$;
