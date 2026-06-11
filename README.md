# KSeF Helper

KSeF Helper is a production-style MVP for a SaaS application that helps Polish freelancers, small businesses, and accounting offices prepare for KSeF-style structured invoice workflows.

It lets users register, create an organization, manage company profiles, upload XML invoices, validate them, preview invoice data in a readable layout, store invoices in an archive, and review human-friendly validation messages.

This is not an official KSeF, Ministry of Finance, or government tool. It does not certify legal, accounting, or tax compliance. The MVP focuses on local XML validation and invoice management. Live KSeF API submission is intentionally left for a later phase.

## Tech Stack

Backend:

- Java 21
- Spring Boot 3
- Spring Web
- Spring Security
- Spring Data JPA
- PostgreSQL
- Flyway
- Maven
- JWT authentication
- Rotating HttpOnly refresh sessions with replay detection
- Email verification and password reset token flows
- Account disable and refresh-session revocation
- BCrypt password hashing
- Official FA(3) XSD validation with an isolated Python `lxml` worker
- XML field extraction with safe DOM + XPath parsing

Frontend:

- React
- TypeScript
- Vite
- Tailwind CSS
- React Router
- TanStack Query
- React Hook Form
- Zod

Infrastructure:

- Docker Compose
- PostgreSQL container
- Local disk XML storage for development
- S3-compatible encrypted object storage for production

## MVP Features

- User registration and login
- Automatic organization creation after registration
- Organization roles: `OWNER`, `ACCOUNTANT`, `CLIENT`, `EMPLOYEE`
- Explicit organization switching for users who belong to multiple workspaces
- Organization-scoped JWT context with database-backed role authorization
- Company profile CRUD
- XML invoice upload
- Original XML file storage
- SHA-256 checksum calculation
- Official FA(3) XSD validation
- Official FA(3) schema files bundled under `backend/src/main/resources/xsd/`
- FA(3) parser support for common official fields such as `Podmiot1`, `Podmiot2`, `P_1`, `P_2`, `P_13_*`, `P_14_*`, `P_15`, and `FaWiersz`
- Defensive XML parsing with XXE protections
- Business validation for missing invoice fields and inconsistent totals
- Invoice archive with filters
- Human-readable invoice preview
- Validation report JSON endpoint
- React dashboard, archive, upload, company, preview, and validation pages

## XML Security

User-uploaded XML is parsed with external entity processing disabled. The backend disables:

- external general entities
- external parameter entities
- external DTD loading
- DOCTYPE declarations
- XInclude
- external schema and DTD access during validation

This is important because XML uploads can otherwise expose the application to XXE attacks.

## Run Locally With Docker

From the repository root:

```bash
docker compose up --build
```

Then open:

- Frontend: http://localhost:5173
- Backend API: http://localhost:8080
- PostgreSQL: localhost:55432

Default database values:

```text
database: ksef_helper
user: ksef
password: ksef
```

## Run Locally Without Docker

Start PostgreSQL with the same values used by Docker Compose:

```text
host: localhost
port: 55432
database: ksef_helper
user: ksef
password: ksef
```

Run the backend:

```bash
cd backend
mvn spring-boot:run
```

Run the frontend:

```bash
cd frontend
npm install
npm run dev
```

## Environment Variables

Backend:

```text
SERVER_PORT=8080
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/ksef_helper
SPRING_DATASOURCE_USERNAME=ksef
SPRING_DATASOURCE_PASSWORD=ksef
JWT_SECRET=base64-encoded-secret-at-least-256-bit
JWT_ACCESS_EXPIRATION=10m
REFRESH_TOKEN_EXPIRATION=30d
REFRESH_COOKIE_NAME=ksef-helper-refresh
REFRESH_COOKIE_SECURE=false
EMAIL_VERIFICATION_REQUIRED=false
EMAIL_VERIFICATION_EXPIRATION=24h
PASSWORD_RESET_EXPIRATION=30m
FRONTEND_URL=http://localhost:5173
CORS_ALLOWED_ORIGINS=http://localhost:5173
FORWARD_HEADERS_STRATEGY=none
MAIL_DELIVERY=log
MAIL_FROM=no-reply@localhost
FILE_STORAGE_TYPE=local
FILE_STORAGE_PATH=uploads
MAX_FILE_SIZE=10MB
MAX_REQUEST_SIZE=10MB
MAX_UPLOAD_BYTES=10485760
XML_XSD_PATH=classpath:xsd/schemat_fa_vat-3-_v1-0.xsd
XML_VALIDATOR_COMMAND=python3
XML_VALIDATION_TIMEOUT=10s
RATE_LIMIT_ENABLED=true
LOGIN_RATE_LIMIT_MAX_ATTEMPTS=5
LOGIN_RATE_LIMIT_MAX_ATTEMPTS_PER_IP=30
LOGIN_RATE_LIMIT_WINDOW=1m
UPLOAD_RATE_LIMIT_MAX_REQUESTS=20
UPLOAD_RATE_LIMIT_WINDOW=1m
```

Production additionally requires:

```text
SPRING_PROFILES_ACTIVE=prod
JWT_SECRET=unique-base64-secret-at-least-256-bit
CORS_ALLOWED_ORIGINS=https://app.example.com
FRONTEND_URL=https://app.example.com
SMTP_HOST=smtp.example.com
SMTP_PORT=587
SMTP_USERNAME=...
SMTP_PASSWORD=...
MAIL_FROM=no-reply@example.com
S3_BUCKET=ksef-helper-production
S3_REGION=eu-central-1
# Optional with S3_SERVER_SIDE_ENCRYPTION=aws:kms
S3_KMS_KEY_ID=...
AWS_ACCESS_KEY_ID=...
AWS_SECRET_ACCESS_KEY=...
```

For MinIO, Cloudflare R2, or another S3-compatible provider, also set `S3_ENDPOINT` and, when required, `S3_PATH_STYLE_ACCESS=true`.

Frontend:

```text
VITE_API_URL=http://localhost:8080/api
```

Login attempts are limited by both client address and normalized email address. Invoice uploads are limited per user and organization. Rejected requests return HTTP `429` with a `Retry-After` header.

The built-in limiter stores counters in the backend process and is suitable for local development and a single backend instance. A multi-instance production deployment must use a shared limiter such as Redis or enforce equivalent limits at a trusted API gateway. Configure Spring's forwarded-header handling when a reverse proxy is responsible for supplying the real client address.

Access tokens are kept in browser memory and expire after ten minutes by default. Refresh tokens are opaque, hashed in PostgreSQL, sent only as `HttpOnly; SameSite=Strict` cookies, rotated after every refresh, and revoked as a family when reuse is detected. Logout revokes the refresh session. Password resets and account disabling revoke all refresh sessions and invalidate existing access tokens through account-state checks.

With `MAIL_DELIVERY=log`, verification and reset links are written to backend logs for local development only. Production startup requires the `prod` profile, SMTP delivery, secure refresh cookies, an explicit production CORS origin, a unique JWT secret, and S3 storage.

## Troubleshooting

If the backend fails with `FATAL: password authentication failed for user "ksef"`, it is usually connecting to a different local PostgreSQL instance. The development Docker database is exposed on `localhost:55432`, not the default PostgreSQL port `5432`.

For IntelliJ, set these run configuration environment variables:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/ksef_helper
SPRING_DATASOURCE_USERNAME=ksef
SPRING_DATASOURCE_PASSWORD=ksef
XML_VALIDATOR_COMMAND=python
```

The selected Python installation must have `lxml` installed:

```text
python -m pip install lxml
```

If you previously created the Docker volume with another password, recreate only the app database volume:

```bash
docker compose down -v
docker compose up --build
```

If the backend fails with `Port 8080 was already in use`, another backend process is already running. Stop the old IntelliJ run with the red square button, or close the terminal/container using port `8080`. As a temporary workaround, run the backend on another port:

```text
SERVER_PORT=8081
```

## API Overview

Auth:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `POST /api/auth/request-verification`
- `POST /api/auth/verify-email`
- `POST /api/auth/forgot-password`
- `POST /api/auth/reset-password`
- `GET /api/auth/me`

Organizations:

- `GET /api/organizations/current`
- `GET /api/organizations/current/members`
- `POST /api/organizations`
- `GET /api/organizations/{id}/members`
- `POST /api/organizations/{id}/invite`

Authentication:

- `POST /api/auth/switch-organization/{organizationId}`

Users with multiple memberships receive an authenticated but unscoped token after login and must select an organization before using organization data endpoints.

## Role Permissions

| Capability | Owner | Accountant | Employee | Client |
| --- | --- | --- | --- | --- |
| View organization, companies, invoices and reports | Yes | Yes | Yes | Yes |
| View members | Yes | Yes | No | No |
| Invite members | Any role | Client/employee only | No | No |
| Manage companies | Yes | Yes | No | No |
| Upload and revalidate invoices | Yes | Yes | Yes | No |
| Delete invoices | Yes | Yes | No | No |
| Download original XML | Yes | Yes | Yes | Yes |

Companies:

- `GET /api/companies`
- `POST /api/companies`
- `GET /api/companies/{id}`
- `PUT /api/companies/{id}`
- `DELETE /api/companies/{id}`

Invoices:

- `POST /api/invoices/upload`
- `GET /api/invoices`
- `GET /api/invoices/{id}`
- `GET /api/invoices/{id}/preview`
- `GET /api/invoices/{id}/validation`
- `POST /api/invoices/{id}/revalidate`
- `GET /api/invoices/{id}/download-original`
- `DELETE /api/invoices/{id}`

Supported archive filters for `GET /api/invoices`:

```text
invoiceNumber
sellerNip
buyerNip
companyId
currency
dateFrom
dateTo
uploadedFrom
uploadedTo
status
minGrossAmount
maxGrossAmount
```

Reports:

- `GET /api/reports/invoices/{id}/validation-report`
- `GET /api/reports/monthly?year=2026&month=2`

## Sample XML

Sample files are under:

```text
backend/src/test/resources/sample-invoices/
```

Official FA(3) schema files are also bundled under:

```text
backend/src/main/resources/xsd/schemat_fa_vat-3-_v1-0.xsd
backend/src/main/resources/xsd/StrukturyDanych_v10-0E.xsd
backend/src/main/resources/xsd/ElementarneTypyDanych_v10-0E.xsd
backend/src/main/resources/xsd/KodyKrajow_v10-0E.xsd
```

Official FA(3) sample XML files are under:

```text
backend/src/test/resources/sample-invoices/fa3-official/
```

The runtime validates uploads against the official schema through a timeout-bound `lxml` worker. The Java XML parser still performs the initial well-formedness and XXE safety checks before the worker starts.

Production objects are stored under organization-scoped generated keys. Upload rollback removes the new object; failed rollback cleanup and post-commit invoice deletion use a persisted retry queue. Downloads and restores verify the stored SHA-256 checksum.

See [`docs/production-operations.md`](docs/production-operations.md) for backup, restore, S3 versioning, and platform-admin setup.

## Tests

Backend tests require a working Python installation with `lxml`. The upload integration tests also require Docker because they start a disposable PostgreSQL container.

```bash
cd backend
mvn test -Dxml.validator.command=python
```

On Linux, use `python3` if that is the installed executable. The test suite validates all 26 bundled official FA(3) examples against the XSD, parses and business-checks each example, and exercises invoice upload through the real HTTP API, PostgreSQL, Flyway, and filesystem storage.

The GitHub Actions workflow under `.github/workflows/ci.yml` runs the mandatory FA(3) validator tests and the frontend production build on every push and pull request.

## Roadmap

- Official KSeF API integration
- Bulk ZIP upload
- PDF validation report export
- Accountant-client collaboration
- Email notifications
- Stripe or Polish payment integration
- Audit logs
- Subscription plans and usage limits

## License

Proprietary License. All rights reserved. See `LICENSE`.
