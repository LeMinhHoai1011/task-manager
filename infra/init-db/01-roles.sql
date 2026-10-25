-- 01-roles.sql — role DB tach theo module (DEC-43) + bao ve audit append-only.
--
-- GHI CHU AP DUNG: file nay chi co tac dung khi Postgres chay (hien tai
-- Docker daemon TAT). Khi DB chay, mount ./init-db vao
-- /docker-entrypoint-initdb.d (da cau hinh trong compose) de tu dong ap dung
-- cho database moi, hoac chay thu cong: psql -f infra/init-db/01-roles.sql
--
-- Quy uoc mat khau: thay bang secret that qua bien moi truong / vault truoc
-- khi dung o Test/Prod. Khong commit mat khau that.

-- Role ung dung theo module (toi thieu dac quyen).
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'stm_app') THEN
    CREATE ROLE stm_app LOGIN PASSWORD 'change_me_stm_app';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'stm_worker') THEN
    CREATE ROLE stm_worker LOGIN PASSWORD 'change_me_stm_worker';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'stm_gateway') THEN
    CREATE ROLE stm_gateway LOGIN PASSWORD 'change_me_stm_gateway';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'stm_migrate') THEN
    CREATE ROLE stm_migrate LOGIN PASSWORD 'change_me_stm_migrate';
  END IF;
END
$$;

-- Quyen co ban: stm_migrate so huu schema + chay migration (Flyway),
-- cac role con lai chi duoc cap quyen chi tiet trong migration tuong ung.
-- GRANT CONNECT + USAGE o day; GRANT tren tung bang do migration quy dinh.
GRANT CONNECT ON DATABASE smarttask TO stm_app, stm_worker, stm_gateway;
GRANT USAGE ON SCHEMA public TO stm_app, stm_worker, stm_gateway;

-- Bao ve audit append-only: thu hoi UPDATE/DELETE tren bang audit doi voi
-- cac role ung dung. Chi stm_migrate (va superuser) duoc thay doi khi can
-- bao tri co kiem soat (archive partition theo retention policy).
-- GHI CHU: cac lenh REVOKE nay yeu cau bang audit_event / security_event
-- da ton tai (do migration tao). Neu chay truoc migration, REVOKE se loi;
-- trong truong hop do, chay lai file nay sau migration.
DO $$
DECLARE
  t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['audit_event', 'security_event'] LOOP
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = t) THEN
      EXECUTE format('REVOKE UPDATE, DELETE ON %I FROM stm_app, stm_worker, stm_gateway', t);
    ELSE
      RAISE NOTICE 'Bang % chua ton tai (migration chua chay) — bo qua REVOKE, can chay lai sau migration.', t;
    END IF;
  END LOOP;
END
$$;
