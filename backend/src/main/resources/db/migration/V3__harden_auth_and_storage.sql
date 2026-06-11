-- noinspection SqlDialectInspectionForFile,SqlNoDataSourceInspection,SqlResolve

-- noinspection SqlResolve
ALTER TABLE app_users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN platform_admin BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN credentials_changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- noinspection SqlResolve
CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    family_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    active_organization_id UUID REFERENCES organizations(id) ON DELETE SET NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by_id UUID REFERENCES auth_sessions(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_auth_sessions_user_id ON auth_sessions(user_id);
CREATE INDEX idx_auth_sessions_family_id ON auth_sessions(family_id);
CREATE INDEX idx_auth_sessions_expires_at ON auth_sessions(expires_at);

-- noinspection SqlResolve
CREATE TABLE account_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_account_tokens_user_type ON account_tokens(user_id, type);
CREATE INDEX idx_account_tokens_expires_at ON account_tokens(expires_at);

-- noinspection SqlResolve
CREATE TABLE storage_deletion_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    storage_key VARCHAR(1000) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_storage_deletion_tasks_pending
    ON storage_deletion_tasks(next_attempt_at)
    WHERE completed_at IS NULL;
