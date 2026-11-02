CREATE TABLE task_team (
  task_id UUID PRIMARY KEY REFERENCES task(id),
  team_id UUID NOT NULL REFERENCES workspace_team(id),
  linked_by UUID NOT NULL REFERENCES app_user(id),
  linked_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_task_team_team ON task_team(team_id, task_id);
