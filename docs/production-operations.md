# Production Operations

## Required Runtime Configuration

Run the backend with `SPRING_PROFILES_ACTIVE=prod`. The application refuses production startup when it detects:

- the development JWT secret or a secret shorter than 256 bits
- wildcard or localhost CORS origins
- local file storage
- log-only email delivery
- insecure refresh cookies

Use a secrets manager supplied by the hosting platform. Do not commit production values.

## S3-Compatible Storage

Create a private bucket dedicated to one environment. Enable:

- bucket versioning
- default server-side encryption
- public-access blocking
- lifecycle rules for old non-current versions
- provider audit logs

The application sends `AES256` server-side-encryption requests by default and stores a SHA-256 checksum in object metadata and PostgreSQL. Use `S3_SERVER_SIDE_ENCRYPTION=aws:kms` with `S3_KMS_KEY_ID` when the provider supports KMS, and configure the workload identity with access to that key.

The application role requires only object read, write, delete, and head permissions for its bucket prefix. It must not have bucket-policy administration rights.

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

## Object Restore

Restore deleted or overwritten objects from the bucket's version history. The `stored_files.storage_path` value is the object key. After restore, download the invoice through the application; checksum verification will reject incorrect content.

Database backups and bucket versions must use compatible retention windows. Restoring only PostgreSQL can reference object versions that have already expired.

## Deletion Queue

Invoice deletion commits the database change and a `storage_deletion_tasks` record together. The backend deletes the object after commit and retries failed deletions. Monitor incomplete tasks:

```sql
-- noinspection SqlResolve
SELECT id, storage_key, attempts, next_attempt_at, last_error
FROM storage_deletion_tasks
WHERE completed_at IS NULL
ORDER BY next_attempt_at;
```

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
