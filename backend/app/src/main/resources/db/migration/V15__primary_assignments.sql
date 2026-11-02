ALTER TABLE task_assignment ADD COLUMN is_primary BOOLEAN NOT NULL DEFAULT FALSE;
-- Preserve existing collaborators. Pick one deterministic eligible active assignee.
WITH ranked AS (
  SELECT a.id, row_number() OVER (PARTITION BY a.task_id ORDER BY a.assigned_at,a.id) AS rank
  FROM task_assignment a JOIN task t ON t.id=a.task_id
  JOIN project_membership m ON m.project_id=t.project_id AND m.user_id=a.user_id
  JOIN app_user u ON u.id=a.user_id
  WHERE a.status='ACTIVE' AND m.status='ACTIVE'
    AND m.role IN ('MEMBER','MANAGER','OWNER','ADMIN') AND u.active=TRUE AND u.status='ACTIVE'
)
UPDATE task_assignment SET is_primary=TRUE WHERE id IN (SELECT id FROM ranked WHERE rank=1);
CREATE UNIQUE INDEX ux_task_one_primary ON task_assignment(task_id) WHERE is_primary AND status='ACTIVE';
