-- noinspection SqlDialectInspectionForFile,SqlNoDataSourceInspection,SqlResolve

CREATE TABLE audit_maintenance_state (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    last_completed_at TIMESTAMPTZ,
    last_redacted_count BIGINT NOT NULL DEFAULT 0,
    last_deleted_count BIGINT NOT NULL DEFAULT 0
);

INSERT INTO audit_maintenance_state(id)
VALUES (1);

CREATE OR REPLACE FUNCTION run_audit_retention(
    personal_data_cutoff TIMESTAMPTZ,
    full_retention_cutoff TIMESTAMPTZ
)
RETURNS TABLE(redacted_count BIGINT, deleted_count BIGINT)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    redacted BIGINT;
    deleted BIGINT;
BEGIN
    IF NOT pg_try_advisory_xact_lock(4976531021) THEN
        RETURN QUERY SELECT 0::BIGINT, 0::BIGINT;
        RETURN;
    END IF;

    IF personal_data_cutoff > CURRENT_TIMESTAMP
       OR full_retention_cutoff > personal_data_cutoff
    THEN
        RAISE EXCEPTION 'invalid audit retention cutoffs';
    END IF;

    PERFORM set_config('app.audit_redaction', 'on', TRUE);
    UPDATE public.audit_events
    SET actor_email = NULL,
        ip_address = NULL,
        user_agent = NULL,
        metadata = '{}',
        redacted_at = CURRENT_TIMESTAMP
    WHERE occurred_at < personal_data_cutoff
      AND redacted_at IS NULL;
    GET DIAGNOSTICS redacted = ROW_COUNT;

    PERFORM set_config('app.audit_retention_cleanup', 'on', TRUE);
    DELETE FROM public.audit_events
    WHERE occurred_at < full_retention_cutoff;
    GET DIAGNOSTICS deleted = ROW_COUNT;

    UPDATE public.audit_maintenance_state
    SET last_completed_at = CURRENT_TIMESTAMP,
        last_redacted_count = redacted,
        last_deleted_count = deleted
    WHERE id = 1;

    RETURN QUERY SELECT redacted, deleted;
END;
$$;

REVOKE ALL ON FUNCTION run_audit_retention(TIMESTAMPTZ, TIMESTAMPTZ) FROM PUBLIC;
REVOKE ALL ON FUNCTION run_audit_retention(TIMESTAMPTZ, TIMESTAMPTZ) FROM "${runtimeRole}";
GRANT EXECUTE ON FUNCTION run_audit_retention(TIMESTAMPTZ, TIMESTAMPTZ)
    TO "${auditMaintenanceRole}";

GRANT USAGE ON SCHEMA public TO "${runtimeRole}";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO "${runtimeRole}";
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO "${runtimeRole}";
REVOKE UPDATE, DELETE, TRUNCATE ON TABLE audit_events FROM "${runtimeRole}";
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON TABLE audit_maintenance_state FROM "${runtimeRole}";
GRANT SELECT ON TABLE audit_maintenance_state TO "${runtimeRole}";

GRANT USAGE ON SCHEMA public TO "${auditMaintenanceRole}";

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "${runtimeRole}";
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO "${runtimeRole}";
