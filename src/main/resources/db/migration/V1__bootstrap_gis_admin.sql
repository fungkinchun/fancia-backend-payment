DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'gis_admin') THEN
        CREATE ROLE gis_admin WITH LOGIN PASSWORD '${gis_admin_password}';
    END IF;
END
$$;

GRANT gis_admin TO CURRENT_USER;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'rds_superuser') THEN
        EXECUTE 'GRANT rds_superuser TO gis_admin';
    ELSIF current_setting('is_superuser') = 'on' THEN
        EXECUTE 'ALTER ROLE gis_admin SUPERUSER';
    END IF;
END
$$;

GRANT USAGE, CREATE ON SCHEMA public TO gis_admin;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO gis_admin;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO gis_admin;

SET ROLE gis_admin;

CREATE EXTENSION IF NOT EXISTS postgis;

RESET ROLE;
