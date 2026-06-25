# Staging Deployment

The staging stack is a self-hosted Docker Compose deployment with:

- Caddy for automatic HTTPS and reverse proxying
- a static Nginx frontend
- the Spring Boot backend
- PostgreSQL with separate migration, runtime, and audit-maintenance roles
- Redis for distributed login and upload rate limits
- managed S3-compatible object storage
- external SMTP for account mail
- Sentry for application error tracking
- Prometheus and Alertmanager for operational metrics and email alerts

Only ports 80 and 443 are public.

## Host Prerequisites

Use a Linux host with:

- Docker Engine and Docker Compose v2
- at least 4 GB RAM
- persistent disk sized for PostgreSQL and monitoring retention
- inbound TCP 80 and TCP/UDP 443
- outbound HTTPS, SMTP, DNS, and ACME access

Create an `A` or `AAAA` DNS record for the staging domain before deployment. Caddy requests the certificate after the stack starts.

## Environment

Start from `.env.staging.example`. Generate every password independently. Generate the JWT secret with at least 32 random bytes encoded as Base64:

```bash
openssl rand -base64 48
```

Set `STAGING_SITE_ADDRESS` to the hostname Caddy should serve and `PUBLIC_BASE_URL` to its full HTTPS URL. Set `CORS_ALLOWED_ORIGINS` to a comma-separated list of trusted browser origins; for the bundled same-origin frontend, use the same value as `PUBLIC_BASE_URL`. The site address and public URL differ only in disposable CI smoke tests, where Caddy intentionally uses HTTP on a reserved `.test` hostname.

Do not reuse database role passwords. The bootstrap PostgreSQL account is only for database initialization and emergency administration. The backend uses:

- `DB_MIGRATION_ROLE` for Flyway
- `DB_RUNTIME_ROLE` for normal queries
- `DB_AUDIT_MAINTENANCE_ROLE` only for the audit-retention function

The role initializer runs only when the PostgreSQL data volume is empty. Changing role values in `.env` does not rotate an existing database. Rotate existing roles with controlled SQL and update the secret atomically.

Normal staging deployment requires managed S3-compatible storage with provider backups, encryption, and a bucket-scoped application identity. Set `S3_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_BUCKET`, and `S3_REGION`; keep `S3_PATH_STYLE_ACCESS=false` unless the provider requires it. The default `S3_SERVER_SIDE_ENCRYPTION=AES256` must not be disabled for real staging.

The bundled MinIO services are enabled only by the `smoke` profile for disposable CI tests. Do not enable that profile on a public staging or production host.

## Manual Deployment

```bash
cp .env.staging.example .env.staging
# edit .env.staging
docker compose --env-file .env.staging -f compose.staging.yml up -d --build
sh scripts/staging-smoke.sh https://staging.example.com
```

Inspect status and logs:

```bash
docker compose --env-file .env.staging -f compose.staging.yml ps
docker compose --env-file .env.staging -f compose.staging.yml logs backend caddy
```

## GitHub Deployment

Create a protected GitHub environment named `staging`. Add:

- `STAGING_HOST`
- `STAGING_SSH_USER`
- `STAGING_SSH_PRIVATE_KEY`
- `STAGING_SSH_KNOWN_HOSTS`: a trusted `known_hosts` entry provisioned out of band; do not generate it during deployment
- `STAGING_DOMAIN`
- `STAGING_ENV_FILE`: the complete staging `.env` content without image variables
- `STAGING_GHCR_USERNAME`
- `STAGING_GHCR_TOKEN`: read access to the repository container packages

The `Deploy staging` workflow runs after a successful `CI` workflow on `master`, and it can also be started manually. It:

1. Builds immutable backend and frontend images tagged with the Git commit SHA.
2. Pushes them to GitHub Container Registry.
3. Uploads the Compose and infrastructure files over SSH.
4. Pulls and starts the requested image SHA.
5. Runs public HTTPS smoke tests.

Protect the environment with required reviewers until deployment is routine.

CI also starts a disposable production-profile stack with Mailpit SMTP and runs the smoke test before an image is eligible for staging deployment.

## Readiness

Caddy exposes:

- `/health/live`
- `/health/ready`
- `/healthz` for the frontend

Backend readiness requires:

- Spring application readiness state
- PostgreSQL
- Redis
- the configured S3 bucket
- a Python/lxml/FA(3)-schema self-check
- data-lifecycle queue health

A failed storage dead letter produces `DEGRADED`, which remains HTTP 200 so the service can continue serving unaffected requests. Prometheus and application logs still alert operators.

## Logging And Error Tracking

Production-profile backend logs use Elastic Common Schema JSON. Every request accepts or creates `X-Request-ID`; the same value is returned in the response, added to log MDC, included in API error bodies, and attached to Sentry events.

Set `SENTRY_DSN`, `APP_ENVIRONMENT`, `APP_RELEASE`, and the desired `SENTRY_TRACES_SAMPLE_RATE`. Keep default PII collection disabled. Configure Sentry project alerts for new errors, regressions, and elevated event volume.

Caddy access logs are also JSON and include request duration and response status.

## Metrics And Alerts

Prometheus scrapes the internal backend `/actuator/prometheus` endpoint. It is not routed publicly by Caddy. Rules cover:

- backend unavailable
- sustained HTTP 5xx responses
- storage-deletion dead letters
- saturated validator capacity
- JVM heap pressure

Alertmanager sends alert and recovery emails through the configured alert SMTP account. Use a monitored operations mailbox or incident-management email integration.

## Smoke Tests

`scripts/staging-smoke.sh` verifies:

- frontend availability
- backend liveness and full readiness
- unauthenticated API protection
- request-ID propagation into headers and JSON errors
- configured CORS behavior

The tests do not create customer records or send email.

## Rollback

Set `IMAGE_TAG` to a previously deployed commit SHA and run:

```bash
docker compose --env-file .env -f compose.staging.yml pull backend frontend
docker compose --env-file .env -f compose.staging.yml up -d --no-build
```

Never roll application images back across an incompatible database migration without a tested database restore plan.
