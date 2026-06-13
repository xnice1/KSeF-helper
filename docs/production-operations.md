# Production Operations

## Required Runtime Configuration

Run the backend with `SPRING_PROFILES_ACTIVE=prod`. The application refuses production startup when it detects:

- the development JWT secret or a secret shorter than 256 bits
- wildcard or localhost CORS origins
- local file storage
- log-only email delivery
- insecure refresh cookies
- reused database identities for migrations, runtime traffic, and audit maintenance

Use a secrets manager supplied by the hosting platform. Do not commit production values.

## Database Roles

Provision three login roles with separate credentials:

- `DB_MIGRATION_ROLE` owns the schema and is used only by Flyway during startup or a controlled migration job.
- `DB_RUNTIME_ROLE` handles normal application queries and cannot update or delete `audit_events`.
- `DB_AUDIT_MAINTENANCE_ROLE` can execute the security-definer audit-retention function but has no general table-write access.

Set the matching `DB_MIGRATION_PASSWORD`, `DB_RUNTIME_PASSWORD`, and `DB_AUDIT_MAINTENANCE_PASSWORD` secrets. The migration role must be able to grant privileges to the other two roles. Do not grant the runtime role membership in either privileged role.

For managed PostgreSQL, create these roles before the first production migration. Run migrations as the migration role, then start the application as the runtime role. Audit cleanup opens a separate connection using the maintenance role.

## S3-Compatible Storage

Create a private bucket dedicated to one environment. Enable:

- bucket versioning
- default server-side encryption
- public-access blocking
- lifecycle rules for old non-current versions
- provider audit logs

The application sends `AES256` server-side-encryption requests by default and stores a SHA-256 checksum in object metadata and PostgreSQL. Use `S3_SERVER_SIDE_ENCRYPTION=aws:kms` with `S3_KMS_KEY_ID` when the provider supports KMS, and configure the workload identity with access to that key.

Production enables `S3_PERMANENT_DELETE_VERSIONS`. Deletion enumerates and removes every version and delete marker for the exact object key instead of creating only a delete marker.

The application role requires object read, write, delete, head, `ListBucketVersions`, and `DeleteObjectVersion` permissions for its bucket prefix. It must not have bucket-policy administration rights.

## Database Backups

Take automated encrypted PostgreSQL backups at least daily and retain point-in-time recovery logs when the provider supports them. A manual logical backup can be created with:

```bash
pg_dump --format=custom --no-owner --file=ksef-helper.dump "$DATABASE_URL"
```

Restore into a new database first:

```bash
createdb ksef_helper_restore
pg_restore --clean --if-exists --no-owner --dbname=ksef_helper_restore ksef-helper.dump
```

Start a staging backend against the restored database and verify login, organization isolation, invoice listing, object downloads, and checksums before promoting a restore.

The automated restore drill is part of the backend test suite:

```bash
cd backend
mvn -Dtest=PostgresBackupRestoreIntegrationTest test
```

It starts PostgreSQL with Testcontainers, applies every Flyway migration, inserts tenant, invoice, stored-file, and audit data, runs `pg_dump`, restores into a new database, restores the corresponding object backup, verifies its checksum, and verifies the append-only audit trigger. Docker must be available locally; GitHub Actions provides it automatically.

## Object Restore

Restore deleted or overwritten objects from the bucket's version history. The `stored_files.storage_path` value is the object key. After restore, download the invoice through the application; checksum verification will reject incorrect content.

Database backups and bucket versions must use compatible retention windows. Restoring only PostgreSQL can reference object versions that have already expired.

## Data Retention

Set `INVOICE_RETENTION_DAYS` to the number of days invoices and their stored XML files may remain in the service. The default value is `0`, which disables automatic deletion. Set `RETENTION_POLL_INTERVAL` with a Spring duration such as `1h` or `15m`.

Expired invoices are deleted in batches of 100. A PostgreSQL advisory lock ensures only one application instance runs invoice retention at a time. One failed invoice is logged without stopping the remaining batch. Database deletion and creation of the storage-deletion task commit together; object deletion runs after commit and uses the normal retry queue. Every retention deletion creates an `INVOICE_RETENTION_DELETED` audit event.

Choose the value from the published retention policy and customer contract. Do not enable a shorter runtime value than the contractual retention period.

## Customer Export And Deletion

Organization owners can download a ZIP export from `GET /api/organizations/current/export`. The export contains:

- organization, membership, and company records
- invoice metadata and line items
- validation messages
- audit events
- original XML files with checksum verification

Exports use a repeatable-read database snapshot, page through records using `ORGANIZATION_EXPORT_PAGE_SIZE`, stream stored objects into a bounded temporary ZIP, and stream that file to the response. They are not assembled in JVM memory. Configure:

- `ORGANIZATION_EXPORT_MAX_BYTES` for the total source object bytes.
- `ORGANIZATION_EXPORT_MAX_INVOICES` for the invoice count.
- `ORGANIZATION_EXPORT_MAX_RECORDS` for all exported database rows.
- `ORGANIZATION_EXPORT_MAX_ARCHIVE_BYTES` for the generated ZIP.

Larger exports must use a support-operated asynchronous process.

Organization deletion requires the account password and the exact organization name. Account deletion requires the account password and the confirmation text `DELETE`. Deletion removes database records, schedules every stored object for deletion, and records a surviving scalar audit event.

Owners can change roles, remove members, and transfer ownership from Settings. Members can leave an organization. The final owner cannot leave, be removed, or be demoted until another owner exists. Removed memberships immediately lose organization access, and active refresh-session organization scopes are cleared.

## Audit Events

`audit_events` is append-only for normal application and database operations. PostgreSQL triggers reject direct `UPDATE` and `DELETE`, and the table deliberately stores scalar user and organization identifiers without foreign keys so security history survives customer-data deletion.

Organization owners can view the latest 200 organization events from `GET /api/organizations/current/audit-events`. Users can view their latest 200 account events from `GET /api/account/audit-events`, and platform administrators can query `GET /api/admin/audit-events`. Events cover authentication successes and failures, authorization failures, refresh-token reuse, organization membership and ownership changes, invoice actions, retention deletion, account deletion, and platform-admin account status changes.

Set `AUDIT_PERSONAL_DATA_DAYS` and `AUDIT_RETENTION_DAYS` from the published privacy and retention policy. After the personal-data period, email, IP address, user agent, and metadata are redacted through `run_audit_retention`. After the full retention period, events are deleted by the same controlled function. Only the audit-maintenance database role can execute it, and the full period cannot be shorter than the personal-data period.

Back up audit events with PostgreSQL. Apply the same retention requirements to backups. Only the migration role may alter the append-only trigger, retention function, or table definition.

## Deletion Queue

Invoice deletion commits the database change and a `storage_deletion_tasks` record together. Workers atomically claim tasks with `FOR UPDATE SKIP LOCKED`, so multiple backend instances can process the queue. Failed deletions retry up to `STORAGE_CLEANUP_MAX_ATTEMPTS` and then move to a terminal failed state. Monitor incomplete and failed tasks:

```sql
-- noinspection SqlResolve
SELECT id, storage_key, attempts, next_attempt_at, last_error
FROM storage_deletion_tasks
WHERE completed_at IS NULL
ORDER BY next_attempt_at;
```

The application logs an `ACTION_REQUIRED` error every `STORAGE_CLEANUP_ALERT_INTERVAL` while dead letters exist. A platform administrator can inspect up to 100 failed tasks with:

```text
GET /api/admin/storage-deletions/failed
```

After correcting the storage or permission problem, requeue one task with:

```text
POST /api/admin/storage-deletions/{taskId}/requeue
```

Requeue resets the retry state, immediately invokes the worker after commit, and writes a `STORAGE_DELETION_REQUEUED` audit event. Requeue only after the underlying cause is understood.

## Validator Capacity

Each FA(3) upload uses an external Python validator. Configure:

- `XML_MAX_CONCURRENT_VALIDATIONS` to cap simultaneous validator processes.
- `XML_CAPACITY_ACQUIRE_TIMEOUT` to bound queue waiting.
- `XML_VALIDATION_TIMEOUT` for wall-clock execution.
- `XML_MEMORY_LIMIT_MB` and `XML_CPU_LIMIT_SECONDS` for per-process Linux resource limits.
- `XML_MAX_OUTPUT_BYTES` to bound captured process output.

The Python worker applies memory, CPU, and file-descriptor limits through the Unix `resource` module. On Windows, concurrency, wall-clock timeout, process termination, and output limits still apply, but production deployments should run the validator in Linux containers for enforceable memory and CPU limits.

## Health Monitoring

The public liveness and readiness endpoints are:

- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`

The `dataLifecycle` health component reports pending and failed storage deletions and, after the first run, the most recent successful audit-retention run. It becomes `DEGRADED` when a dead-letter task exists or the pending queue exceeds `STORAGE_CLEANUP_PENDING_WARNING_THRESHOLD`. Configure monitoring to alert on this component even though degraded health remains HTTP 200.

## Platform Administrator

Platform account enable/disable endpoints require `ROLE_PLATFORM_ADMIN`. Bootstrap the first administrator directly in the database:

```sql
-- noinspection SqlResolve
UPDATE app_users
SET platform_admin = TRUE
WHERE email = 'admin@example.com';
```

The administrator must sign in again after promotion. Restrict administrator accounts with strong unique passwords and operational access controls.

## Restore Drill

At least quarterly:

1. Restore PostgreSQL into an isolated environment.
2. Restore a representative set of object versions.
3. Start the backend using staging secrets.
4. Verify cross-organization isolation.
5. Download restored invoices and verify checksums.
6. Delete a test invoice and confirm its deletion task completes.
7. Record recovery time and any manual intervention.
