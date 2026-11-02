CREATE TABLE workspace_team (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES workspace(id),
  name VARCHAR(200) NOT NULL CHECK (length(trim(name)) > 0),
  created_by UUID NOT NULL REFERENCES app_user(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX ux_workspace_team_name ON workspace_team(workspace_id, lower(name));
CREATE INDEX ix_workspace_team_scope ON workspace_team(workspace_id, created_at);
CREATE TABLE team_member (
  team_id UUID NOT NULL REFERENCES workspace_team(id),
  user_id UUID NOT NULL REFERENCES app_user(id),
  role VARCHAR(20) NOT NULL CHECK (role IN ('MANAGER','MEMBER','GUEST')),
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','REMOVED')),
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(team_id, user_id)
);
CREATE INDEX ix_team_member_user ON team_member(user_id, status);
