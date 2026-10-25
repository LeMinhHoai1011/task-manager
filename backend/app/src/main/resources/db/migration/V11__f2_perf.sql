-- V11 F2.3/F2.5 performance foundation (ADDITIVE / SAFE ONLY).
-- No table/column/index dropped, no data deleted, no V1..V10 file touched.
-- Every statement is re-runnable (IF NOT EXISTS). These are plain btree
-- indexes for the filter/sort/pagination paths converted in F2.5
-- (scoped WHERE + keyset/EXISTS queries) and the F2.3 staging cleanup.
--
-- Precondition for index builds: none (CREATE INDEX IF NOT EXISTS takes a
-- brief SHARE lock only; no dedup needed — these are non-unique).
--
-- Deliberately NOT indexed (documented):
--   * audit_event ordering stays on the PK btree (UUIDv7 ids are
--     time-ordered; admin audit pages by id DESC via keyset).
--   * file_version(attachment_id) and approval_round(task_id) are covered by
--     the V1 UNIQUE constraints (attachment_id, version_no) /
--     (task_id, round_no).
--   * report_export_job(requested_by, created_at) is covered by V6
--     ix_export_user.
--   * stream_ticket(ticket_hash) is covered by its V4 UNIQUE constraint.

-- task list scope: project page ordered by updated/created (keyset pairs).
CREATE INDEX IF NOT EXISTS ix_task_project_updated
  ON task (project_id, updated_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_task_project_created
  ON task (project_id, created_at DESC, id DESC) WHERE deleted_at IS NULL;
-- task list filters.
CREATE INDEX IF NOT EXISTS ix_task_project_state
  ON task (project_id, state) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_task_project_due
  ON task (project_id, due_at) WHERE deleted_at IS NULL AND due_at IS NOT NULL;
-- personal dashboard (created-by scope) + assignment visibility join.
CREATE INDEX IF NOT EXISTS ix_task_created_by
  ON task (created_by) WHERE deleted_at IS NULL;

-- assignment EXISTS / counts / actor scopes.
CREATE INDEX IF NOT EXISTS ix_assign_user_active
  ON task_assignment (user_id, status);
CREATE INDEX IF NOT EXISTS ix_assign_task_status
  ON task_assignment (task_id, status);

-- tag EXISTS filter.
CREATE INDEX IF NOT EXISTS ix_tag_task ON task_tag (task_id);
CREATE INDEX IF NOT EXISTS ix_tag_lookup ON task_tag (tag, task_id);

-- membership visibility scopes.
CREATE INDEX IF NOT EXISTS ix_pmember_user
  ON project_membership (user_id, status);
CREATE INDEX IF NOT EXISTS ix_wsmember_user
  ON workspace_membership (user_id, status);
CREATE INDEX IF NOT EXISTS ix_project_ws
  ON project (workspace_id);

-- calendar / reminder reads.
CREATE INDEX IF NOT EXISTS ix_reminder_user ON reminder_rule (user_id);
CREATE INDEX IF NOT EXISTS ix_reminder_task ON reminder_rule (task_id);

-- file reads.
CREATE INDEX IF NOT EXISTS ix_attachment_task
  ON attachment (task_id) WHERE deleted_at IS NULL;

-- comment / workflow / timelog task feeds.
CREATE INDEX IF NOT EXISTS ix_comment_task ON comment (task_id, created_at);
CREATE INDEX IF NOT EXISTS ix_workflow_task ON workflow_event (task_id, created_at);
CREATE INDEX IF NOT EXISTS ix_timelog_task ON time_log (task_id);

-- admin jobs newest-first page.
CREATE INDEX IF NOT EXISTS ix_bgjob_created ON background_job (created_at DESC);

-- F2.3 staging cleanup sweep (expired, never-committed sessions).
CREATE INDEX IF NOT EXISTS ix_upload_cleanup ON upload_session (expires_at)
  WHERE status IN ('PENDING', 'UPLOADING', 'SCAN_PENDING', 'READY');
CREATE INDEX IF NOT EXISTS ix_upload_task ON upload_session (task_id);
