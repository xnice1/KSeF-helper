-- noinspection SqlDialectInspectionForFile,SqlNoDataSourceInspection,SqlResolve

ALTER TABLE audit_events
    ADD COLUMN redacted_at TIMESTAMPTZ;

CREATE OR REPLACE FUNCTION prevent_audit_event_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND current_setting('app.audit_redaction', TRUE) = 'on'
       AND NEW.id = OLD.id
       AND NEW.occurred_at = OLD.occurred_at
       AND NEW.actor_user_id IS NOT DISTINCT FROM OLD.actor_user_id
       AND NEW.organization_id IS NOT DISTINCT FROM OLD.organization_id
       AND NEW.event_type = OLD.event_type
       AND NEW.target_type IS NOT DISTINCT FROM OLD.target_type
       AND NEW.target_id IS NOT DISTINCT FROM OLD.target_id
       AND NEW.actor_email IS NULL
       AND NEW.ip_address IS NULL
       AND NEW.user_agent IS NULL
       AND NEW.metadata = '{}'
       AND NEW.redacted_at IS NOT NULL
    THEN
        RETURN NEW;
    END IF;

    IF TG_OP = 'DELETE'
       AND current_setting('app.audit_retention_cleanup', TRUE) = 'on'
    THEN
        RETURN OLD;
    END IF;

    RAISE EXCEPTION 'audit_events is append-only';
END;
$$;
