-- 02-grants-tables.sql — per-table GRANTs + audit append-only protection.
--
-- RUN ORDER (this file fixes the V9/F1 "REVOKE before migration" drift):
--   1. Flyway migrations V1..V10 (tables must exist first).
--   2. infra/init-db/01-roles.sql (roles must exist first; re-runnable).
--   3. THIS file (grants reference both roles and tables).
-- Re-running this file is always safe (all statements are guarded).
-- Manual run: psql -f infra/init-db/02-grants-tables.sql
--
-- Least-privilege split (DEC-43):
--   stm_app     web/API role: full DML on business tables (audit append-only).
--   stm_worker  background worker: DML on job/outbox/notify tables,
--               read-only on business tables it consumes.
--   stm_gateway file gateway: read file content tables, append access log.
--   stm_migrate Flyway owner: untouched here (owns schema/objects).

-- Connectivity (harmless if already granted).
DO $$
DECLARE
  r TEXT;
BEGIN
  FOREACH r IN ARRAY ARRAY['stm_app', 'stm_worker', 'stm_gateway'] LOOP
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
      EXECUTE format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), r);
      EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', r);
    ELSE
      RAISE NOTICE 'Role % missing (run 01-roles.sql first) — skipping grants.', r;
    END IF;
  END LOOP;
END
$$;

-- stm_app: full DML on every application table + sequence usage.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'stm_app') THEN
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO stm_app;
    GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO stm_app;
  END IF;
END
$$;

-- stm_worker: DML on queue/notify tables, read-only elsewhere.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'stm_worker') THEN
    GRANT SELECT ON ALL TABLES IN SCHEMA public TO stm_worker;
    GRANT INSERT, UPDATE, DELETE ON
      background_job, outbox_event, notification, delivery_attempt,
      metric_daily_snapshot, report_export_job,
      ai_job, ai_suggestion, ai_interaction,
      reminder_schedule, realtime_cursor
      TO stm_worker;
    GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO stm_worker;
  END IF;
END
$$;

-- stm_gateway: read file content, consume tickets, append access evidence.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'stm_gateway') THEN
    GRANT SELECT ON
      file_object, file_version, attachment, stream_ticket, upload_session
      TO stm_gateway;
    GRANT SELECT, UPDATE ON stream_ticket TO stm_gateway;
    GRANT SELECT, INSERT ON file_access_event TO stm_gateway;
    GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO stm_gateway;
  END IF;
END
$$;

-- Audit append-only (post-migration order: tables are guaranteed to exist
-- when this file runs, unlike the premature REVOKE path in 01-roles.sql).
-- stm_app / stm_worker / stm_gateway keep INSERT+SELECT; UPDATE/DELETE revoked.
DO $$
DECLARE
  t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['audit_event', 'file_access_event'] LOOP
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = t) THEN
      EXECUTE format('GRANT SELECT, INSERT ON %I TO stm_app, stm_worker, stm_gateway', t);
      EXECUTE format('REVOKE UPDATE, DELETE ON %I FROM stm_app, stm_worker, stm_gateway', t);
    ELSE
      RAISE NOTICE 'Table % missing (run migrations first) — skipping audit guard.', t;
    END IF;
  END LOOP;
END
$$;

-- Future tables created by the migration owner keep the same split.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'stm_migrate')
     AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'stm_app') THEN
    ALTER DEFAULT PRIVILEGES FOR ROLE stm_migrate IN SCHEMA public
      GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO stm_app;
    ALTER DEFAULT PRIVILEGES FOR ROLE stm_migrate IN SCHEMA public
      GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO stm_app;
  ELSE
    RAISE NOTICE 'stm_migrate/stm_app missing — skipping ALTER DEFAULT PRIVILEGES.';
  END IF;
END
$$;
