# Legal Document Pack

Effective draft date: 26 June 2026

These documents are launch templates for hosted KSeF Helper. They are not legal advice and must be reviewed by a qualified lawyer before public paid use.

Before launch, replace every bracketed placeholder:

- `[legal company name]`
- `[registered address]`
- `[business registration number]`
- `[tax identification number]`
- `[support email]`
- `[privacy email]`
- `[security email]`
- `[billing support email]`
- `[billing provider]`
- `[hosting/storage provider]`
- `[SMTP provider]`
- `[monitoring/error provider]`
- `[location]`
- `[governing law and court]`

The current product promise these documents protect is:

> KSeF Helper helps businesses validate, preview, archive, export, and delete FA(3) XML invoices before submission. It is not an official government, Ministry of Finance, tax, legal, accounting, or KSeF certification tool.

Public legal pages to expose before accepting payment:

- Terms of Service: `docs/legal/terms-of-service.md`
- Privacy Policy: `docs/legal/privacy-policy.md`
- Data Processing Agreement: `docs/legal/data-processing-agreement.md`
- Retention and Deletion Policy: `docs/legal/retention-deletion-policy.md`
- Support and Contact: `docs/legal/support.md`

Operational assumptions reflected in these drafts:

- The service is sold business-to-business.
- Customers control invoice/customer content and KSeF Helper processes it for them.
- KSeF Helper controls account, security, support, operational, and billing records.
- Production uses S3-compatible object storage, PostgreSQL, SMTP, Redis rate limiting, monitoring, structured logs, and audit retention.
- The service does not submit invoices to KSeF yet.

Reference context used while drafting:

- Official FA(3) structure page from the Polish KSeF portal.
- Official KSeF mandatory-scope page from the Polish KSeF portal.
- GDPR Regulation (EU) 2016/679, especially Articles 28, 32, 33, and 34.
