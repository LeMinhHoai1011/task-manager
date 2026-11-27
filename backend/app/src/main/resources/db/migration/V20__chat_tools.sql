ALTER TABLE project_message ADD COLUMN reply_to_id UUID REFERENCES project_message(id) ON DELETE SET NULL;
ALTER TABLE project_message ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE;
CREATE UNIQUE INDEX uq_project_chat_pin ON project_message(project_id) WHERE pinned;
