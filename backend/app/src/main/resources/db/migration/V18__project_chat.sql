CREATE TABLE project_chat_state (
  project_id UUID PRIMARY KEY REFERENCES project(id),
  last_revision BIGINT NOT NULL DEFAULT 0 CHECK (last_revision >= 0)
);

CREATE TABLE project_message (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES project(id),
  seq BIGINT NOT NULL CHECK (seq > 0),
  changed_seq BIGINT NOT NULL CHECK (changed_seq >= seq),
  sender_user_id UUID NOT NULL REFERENCES app_user(id),
  client_message_id UUID NOT NULL,
  request_hash CHAR(64) NOT NULL,
  body TEXT NOT NULL CHECK (char_length(body) BETWEEN 1 AND 4000 AND btrim(body) <> ''),
  linked_task_id UUID REFERENCES task(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  deleted_at TIMESTAMPTZ,
  deleted_by UUID REFERENCES app_user(id),
  delete_reason VARCHAR(500),
  UNIQUE (project_id, seq),
  UNIQUE (project_id, sender_user_id, client_message_id)
);
CREATE INDEX ix_project_message_changes ON project_message(project_id, changed_seq);
CREATE INDEX ix_project_message_rate ON project_message(project_id, sender_user_id, created_at);

CREATE TABLE project_message_read (
  project_id UUID NOT NULL REFERENCES project(id),
  user_id UUID NOT NULL REFERENCES app_user(id),
  last_read_seq BIGINT NOT NULL DEFAULT 0 CHECK (last_read_seq >= 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (project_id, user_id)
);
