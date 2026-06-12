-- noinspection SqlDialectInspectionForFile,SqlNoDataSourceInspection,SqlResolve

-- Audit rows deliberately keep scalar identifiers instead of foreign keys so
-- security history survives user and organization deletion.
CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at TIMESTAMPTZ NOT NULL,
    actor_user_id UUID,
    actor_email VARCHAR(255),
    organization_id UUID,
    event_type VARCHAR(80) NOT NULL,
    target_type VARCHAR(80),
    target_id VARCHAR(255),
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    metadata TEXT NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_audit_events_organization_time
    ON audit_events(organization_id, occurred_at DESC);
CREATE INDEX idx_audit_events_actor_time
    ON audit_events(actor_user_id, occurred_at DESC);
CREATE INDEX idx_audit_events_type_time
    ON audit_events(event_type, occurred_at DESC);

CREATE FUNCTION prevent_audit_event_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$;

CREATE TRIGGER audit_events_no_update
    BEFORE UPDATE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_event_mutation();

CREATE TRIGGER audit_events_no_delete
    BEFORE DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_event_mutation();
