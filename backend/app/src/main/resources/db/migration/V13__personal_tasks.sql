-- Existing tasks remain PROJECT tasks with their project links unchanged.
ALTER TABLE task ADD COLUMN scope TEXT NOT NULL DEFAULT 'PROJECT';
ALTER TABLE task ADD COLUMN owner_user_id UUID REFERENCES app_user(id);
ALTER TABLE task ADD COLUMN notes TEXT;
ALTER TABLE task ALTER COLUMN project_id DROP NOT NULL;
ALTER TABLE task ADD CONSTRAINT task_scope_check CHECK (
  (scope = 'PROJECT' AND project_id IS NOT NULL) OR
  (scope = 'PERSONAL' AND project_id IS NULL AND owner_user_id IS NOT NULL
    AND parent_task_id IS NULL AND milestone_id IS NULL));
CREATE INDEX ix_task_personal_owner ON task(owner_user_id, created_at DESC)
  WHERE scope = 'PERSONAL' AND deleted_at IS NULL;
