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
- BCrypt password hashing
- XML validation with `SchemaFactory`
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

## MVP Features

- User registration and login
- Automatic organization creation after registration
- Organization roles: `OWNER`, `ACCOUNTANT`, `CLIENT`, `EMPLOYEE`
- Company profile CRUD
- XML invoice upload
- Original XML file storage
- SHA-256 checksum calculation
- Placeholder XSD validation from `backend/src/main/resources/xsd/ksef-placeholder.xsd`
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
JWT_EXPIRATION_MS=86400000
FILE_STORAGE_PATH=uploads
MAX_FILE_SIZE=10MB
MAX_REQUEST_SIZE=10MB
MAX_UPLOAD_BYTES=10485760
```

Frontend:

```text
VITE_API_URL=http://localhost:8080/api
```

## Troubleshooting

If the backend fails with `FATAL: password authentication failed for user "ksef"`, it is usually connecting to a different local PostgreSQL instance. The development Docker database is exposed on `localhost:55432`, not the default PostgreSQL port `5432`.

For IntelliJ, set these run configuration environment variables:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/ksef_helper
SPRING_DATASOURCE_USERNAME=ksef
SPRING_DATASOURCE_PASSWORD=ksef
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
- `GET /api/auth/me`

Organizations:

- `GET /api/organizations/current`
- `GET /api/organizations/current/members`
- `POST /api/organizations`
- `GET /api/organizations/{id}/members`
- `POST /api/organizations/{id}/invite`

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

The included XSD is a placeholder for MVP development. Replace it with the official FA(3) schema when implementing production-grade KSeF validation.

## Roadmap

- Official KSeF API integration
- Bulk ZIP upload
- PDF validation report export
- Accountant-client collaboration
- Email notifications
- Stripe or Polish payment integration
- S3-compatible file storage
- Audit logs
- Subscription plans and usage limits

## License

MIT License. See `LICENSE`.
