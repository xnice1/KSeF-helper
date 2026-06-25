# Retention and Deletion Policy

Effective date: 26 June 2026

This policy is a template and must be reviewed before launch.

## 1. Scope

This policy explains how KSeF Helper retains, exports, deletes, redacts, and backs up customer data.

It applies to:

- accounts and users;
- organizations and memberships;
- company records;
- uploaded XML invoices;
- parsed invoice data and validation results;
- reports and exports;
- audit events;
- logs, metrics, and operational records;
- backups and object storage.

## 2. Customer-controlled records

Customers may delete invoices, organizations, and accounts through the application where permissions allow.

Invoice deletion removes the invoice database record and queues deletion of the original stored XML object.

Organization deletion removes organization-scoped tenant data, including memberships, companies, invoices, stored-file records, and validation records.

Account deletion removes or anonymizes account-owned data where possible and requires ownership transfer or organization deletion where the user is the sole owner.

## 3. Configurable invoice retention

The application supports automatic invoice retention through `INVOICE_RETENTION_DAYS`.

- `0` disables automatic invoice deletion.
- A positive value schedules deletion of invoices older than the configured number of days.

Customers should configure this according to their accounting, tax, and contractual retention obligations. KSeF Helper does not decide the customer's legal retention period.

## 4. Object storage deletion

Production storage is designed for S3-compatible object storage.

When an invoice is deleted, object deletion is processed through a retry queue. Failed storage deletions move to a dead-letter state for platform-admin review and requeue.

Production configuration requires permanent deletion of object versions when versioned S3 storage is used.

## 5. Audit events

Audit events record security, login, upload, download, deletion, organization, and admin actions.

The application supports:

- personal-data redaction after `AUDIT_PERSONAL_DATA_DAYS`;
- full audit-event deletion after `AUDIT_RETENTION_DAYS`;
- separate audit-maintenance database privileges.

Default development values are 90 days for personal-data redaction and 365 days for audit-event retention. Production must set explicit values.

Audit logs may be retained longer where needed for security, fraud prevention, dispute resolution, legal compliance, or incident investigation.

## 6. Logs and monitoring

Operational logs, metrics, traces, and error records are retained only as long as needed for reliability, security, debugging, legal compliance, and abuse prevention.

Logs should avoid storing full invoice XML. If sensitive data appears in logs during an incident or defect, it should be treated as confidential customer data and removed or redacted where practical.

## 7. Backups

Backups protect against data loss and disaster recovery events.

Deletion from live systems does not immediately remove matching data from immutable or rotating backups. Backup copies are removed through the normal backup retention cycle unless legally required sooner and technically feasible.

Restored backups must be reconciled with deletion records where practical.

## 8. Data export

Organization owners or authorized users may export organization data using bounded export features.

Exports may include invoice metadata, validation results, organization records, company records, and original XML files where authorized.

Export limits protect service availability and memory usage. Large exports may require multiple requests or support assistance.

## 9. Termination

After subscription termination, customer access may be disabled.

KSeF Helper should provide a reasonable export period before deletion where commercially and technically practical, unless suspension or immediate deletion is required by law, security, non-payment, or customer instruction.

## 10. Deletion exceptions

KSeF Helper may retain limited records where necessary for:

- legal compliance;
- tax and accounting records;
- fraud, abuse, or security investigation;
- dispute resolution;
- backup integrity;
- enforcing agreements;
- protecting the rights of customers, users, or KSeF Helper.

Retained records should be limited to what is necessary for the retained purpose.

## 11. Customer duties

Customers are responsible for:

- knowing their own tax, accounting, and record-retention obligations;
- exporting data before deleting an account or organization where needed;
- configuring retention settings according to their obligations;
- checking deletion results and contacting support if deletion appears incomplete.

## 12. Contact

Deletion and export requests: `[support email]`

Privacy requests: `[privacy email]`

Security concerns: `[security email]`
