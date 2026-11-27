CREATE TABLE personal_task_recurrence (
  source_task_id UUID PRIMARY KEY REFERENCES task(id) ON DELETE CASCADE,
  owner_user_id UUID NOT NULL REFERENCES app_user(id),
  title TEXT NOT NULL,
  description TEXT,
  notes TEXT,
  priority TEXT NOT NULL,
  interval_days INTEGER NOT NULL CHECK (interval_days IN (1, 7)),
  timezone TEXT NOT NULL,
  due_time TIME,
  next_date DATE NOT NULL,
  run_at TIMESTAMPTZ NOT NULL,
  stopped_at TIMESTAMPTZ
);

CREATE INDEX ix_personal_task_recurrence_due
  ON personal_task_recurrence (run_at, source_task_id) WHERE stopped_at IS NULL;
