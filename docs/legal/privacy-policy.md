# Privacy Policy

Effective date: 26 June 2026

Controller/operator: `[legal company name]`, trading as KSeF Helper, `[registered address]`.

Privacy contact: `[privacy email]`

This policy is a template and must be reviewed before launch.

## 1. Scope

This Privacy Policy explains how KSeF Helper processes personal data for the hosted service, website, support, security, operations, and billing.

KSeF Helper has two roles:

- Controller for account data, security logs, support records, operational records, website records, and billing records.
- Processor for invoice XML files, company records, organization content, validation outputs, and exports that customers upload or generate in the service.

For processor activity, the customer's instructions are governed by the Data Processing Agreement.

## 2. Data we process

Account and user data:

- name, email address, password hash, account status, email verification status;
- organization memberships, roles, invitations, and selected active organization;
- authentication sessions, refresh-token metadata, password reset and verification token metadata.

Customer content:

- uploaded XML invoices;
- seller, buyer, tax identifier, address, invoice number, dates, amounts, currency, VAT rate, line item, bank account, and other invoice data contained in uploaded files;
- validation messages, parsed previews, archive records, reports, and exports.

Operational and security data:

- IP address, user agent, request path, request ID, timestamps, rate-limit events, authentication failures, authorization failures, audit events, application logs, error traces, health metrics, storage deletion task status.

Support and business data:

- support messages, diagnostics, contact details, customer instructions, billing details, subscription status, and payment provider references where billing is enabled.

## 3. Purposes and legal bases

We process personal data to:

- provide accounts, organizations, invoice upload, validation, archive, export, and deletion features;
- authenticate users and protect accounts;
- enforce role permissions and organization isolation;
- send verification, password reset, security, support, and service messages;
- provide support and investigate issues;
- maintain audit logs, security logs, backups, monitoring, and service reliability;
- comply with legal, tax, accounting, and security obligations;
- bill customers and manage subscriptions when billing is enabled;
- improve the service using aggregated or non-identifying operational information.

Legal bases may include contract performance, legitimate interests, legal obligations, consent where required, and customer instructions where KSeF Helper acts as processor.

## 4. Cookies and local storage

The service uses an essential HttpOnly refresh cookie for authentication. This cookie is required to keep users signed in securely.

The frontend may keep short-lived access state in browser memory. The service should not use advertising cookies. If analytics or marketing cookies are added later, this policy and any cookie notice must be updated before use.

## 5. Sharing and subprocessors

KSeF Helper may use subprocessors for:

- hosting and compute;
- managed PostgreSQL or database hosting;
- S3-compatible object storage;
- email delivery;
- error tracking and monitoring;
- payment processing when billing is enabled;
- customer support tooling.

Before public launch, list actual subprocessors here:

| Subprocessor | Purpose | Location |
| --- | --- | --- |
| `[hosting/storage provider]` | Hosting and object storage | `[location]` |
| `[SMTP provider]` | Transactional email | `[location]` |
| `[monitoring/error provider]` | Monitoring and error tracking | `[location]` |
| `[billing provider]` | Payments and subscriptions | `[location]` |

## 6. International transfers

If personal data is transferred outside the European Economic Area, KSeF Helper will use appropriate safeguards such as adequacy decisions, Standard Contractual Clauses, or another valid transfer mechanism.

Actual transfer locations must be completed before launch.

## 7. Retention

Retention is described in the Retention and Deletion Policy.

Current application behavior supports:

- customer-controlled deletion of invoices, organizations, and accounts;
- configurable invoice retention;
- bounded organization export;
- audit personal-data redaction;
- audit-event retention;
- storage deletion retry and dead-letter handling;
- production S3 permanent object-version deletion when enabled.

Backups may retain deleted data for a limited backup rotation period and are not restored selectively except where technically and legally required.

## 8. Security

KSeF Helper uses technical and organizational measures designed to protect personal data, including:

- hashed passwords;
- short-lived access tokens and rotating HttpOnly refresh sessions;
- organization-scoped authorization checks;
- role-based permissions;
- upload size limits and rate limits;
- XXE-protected XML parsing;
- bounded validator execution;
- audit events;
- production S3-compatible encrypted storage;
- readiness checks, structured logs, monitoring, and error tracking;
- separate production database roles for runtime, migration, and audit maintenance.

Security measures may change as the service develops.

## 9. Data subject rights

Depending on applicable law, individuals may have rights to access, rectification, erasure, restriction, objection, portability, and complaint to a supervisory authority.

For customer-controlled invoice or organization content, requests should normally be sent to the customer organization that controls the data. If KSeF Helper receives such a request directly, it may redirect the requester to the customer or assist the customer according to the Data Processing Agreement.

## 10. Breach notice

If KSeF Helper becomes aware of a personal data breach affecting customer-controlled personal data, it will notify affected customers without undue delay as required by the Data Processing Agreement and applicable law.

## 11. Children

KSeF Helper is intended for business users and is not directed to children.

## 12. Changes

KSeF Helper may update this Privacy Policy. Material changes should be announced by email, in-app notice, or both.

## 13. Contact

Privacy: `[privacy email]`

Support: `[support email]`
