import { Link, Navigate, useParams } from "react-router-dom";

type LegalSection = {
  title: string;
  body: string[];
};

type LegalDocument = {
  slug: string;
  title: string;
  effectiveDate: string;
  description: string;
  sections: LegalSection[];
};

const documents = {
  terms: {
    slug: "terms",
    title: "Terms of Service",
    effectiveDate: "26 June 2026",
    description:
      "Business terms for hosted KSeF Helper use. These terms must be reviewed with counsel before public paid launch.",
    sections: [
      {
        title: "Service",
        body: [
          "KSeF Helper helps organizations upload FA(3)-style XML invoices, run technical and business validation checks, preview parsed invoice data, archive files, export data, and delete accounts or organizations.",
          "KSeF Helper is not an official KSeF, Ministry of Finance, tax authority, legal, accounting, or certification service.",
          "The service does not guarantee that an invoice will be accepted by KSeF or satisfy legal, tax, or accounting obligations."
        ]
      },
      {
        title: "Customer responsibilities",
        body: [
          "Customers are responsible for invoice content, legal review, accounting review, tax obligations, KSeF submission decisions, user access, and retention configuration.",
          "Customers must not attempt to access another customer's data, bypass security controls, upload harmful files, or use the service for unlawful purposes."
        ]
      },
      {
        title: "Data and ownership",
        body: [
          "Customers keep ownership of uploaded XML files, company records, validation outputs, exports, and organization content.",
          "KSeF Helper may process customer data only as needed to provide, secure, monitor, support, export, and delete the service."
        ]
      },
      {
        title: "Beta and validation limits",
        body: [
          "Alpha, beta, trial, or preview versions may contain defects and incomplete features.",
          "Validation messages are software-generated checks and do not replace professional review or the official KSeF system."
        ]
      },
      {
        title: "Missing launch details",
        body: [
          "Before launch, fill the operator legal name, registered address, tax number, governing law, billing terms, support email, privacy email, and security email in docs/legal/terms-of-service.md."
        ]
      }
    ]
  },
  privacy: {
    slug: "privacy",
    title: "Privacy Policy",
    effectiveDate: "26 June 2026",
    description:
      "Privacy summary for account, invoice, operational, security, support, and billing data processed by hosted KSeF Helper.",
    sections: [
      {
        title: "Roles",
        body: [
          "KSeF Helper is a controller for account, security, support, operational, website, and billing records.",
          "KSeF Helper is a processor for customer-uploaded invoice XML, company records, organization content, validation results, and exports."
        ]
      },
      {
        title: "Data processed",
        body: [
          "The service processes account details, organization memberships, authentication metadata, invoice XML content, parsed invoice fields, validation messages, audit events, logs, metrics, support messages, and billing references when billing is enabled.",
          "Invoice XML may contain names, addresses, tax identifiers, bank account data, invoice amounts, line items, VAT data, and other business or personal data."
        ]
      },
      {
        title: "Purpose",
        body: [
          "Data is processed to provide validation, preview, archive, export, deletion, authentication, security, support, monitoring, billing, and legal compliance functions.",
          "The service uses an essential HttpOnly refresh cookie for authentication. Advertising cookies are not part of the current product."
        ]
      },
      {
        title: "Retention and rights",
        body: [
          "Retention follows the Retention and Deletion Policy. The application supports invoice deletion, organization deletion, account deletion, bounded exports, audit redaction, and audit retention.",
          "Individuals may have access, correction, deletion, restriction, objection, portability, and complaint rights depending on applicable law."
        ]
      },
      {
        title: "Missing launch details",
        body: [
          "Before launch, fill the real controller identity, privacy contact, support contact, subprocessors, hosting regions, transfer mechanism, and billing provider in docs/legal/privacy-policy.md."
        ]
      }
    ]
  },
  dpa: {
    slug: "dpa",
    title: "Data Processing Agreement",
    effectiveDate: "26 June 2026",
    description:
      "Processor terms for customer-controlled invoice and organization content handled by KSeF Helper.",
    sections: [
      {
        title: "Processing relationship",
        body: [
          "For customer content, the customer is the controller and KSeF Helper is the processor.",
          "Customer content includes uploaded XML invoices, parsed invoice data, company records, validation outputs, exports, and organization metadata."
        ]
      },
      {
        title: "Instructions",
        body: [
          "KSeF Helper processes customer content according to the Terms, product settings, support requests, this DPA, and applicable law.",
          "Processing covers storage, parsing, validation, preview, search, export, backup, logging, security monitoring, support, deletion, and audit."
        ]
      },
      {
        title: "Security commitments",
        body: [
          "The DPA references access control, organization boundaries, least-privilege database roles, encrypted production object storage, hashed passwords, rotating refresh tokens, rate limits, audit logs, backups, and monitoring.",
          "Security incidents affecting customer content must be reported without undue delay."
        ]
      },
      {
        title: "Deletion and return",
        body: [
          "Customers may export organization data during the subscription.",
          "After deletion or termination, KSeF Helper deletes or anonymizes customer content according to the Retention and Deletion Policy unless law requires retention."
        ]
      },
      {
        title: "Missing launch details",
        body: [
          "Before launch, complete the actual subprocessor list, hosting locations, transfer mechanism, incident contact, security review status, and audit procedure in docs/legal/data-processing-agreement.md."
        ]
      }
    ]
  },
  retention: {
    slug: "retention",
    title: "Retention and Deletion Policy",
    effectiveDate: "26 June 2026",
    description:
      "How KSeF Helper retains, exports, deletes, redacts, and backs up hosted customer data.",
    sections: [
      {
        title: "Customer-controlled data",
        body: [
          "Customers may delete invoices, organizations, and accounts where permissions allow.",
          "Invoice deletion removes the invoice record and queues deletion of the stored XML object. Organization deletion removes organization-scoped tenant data."
        ]
      },
      {
        title: "Configurable retention",
        body: [
          "The backend supports INVOICE_RETENTION_DAYS. A value of 0 disables automatic invoice deletion; a positive value deletes invoices older than the configured period.",
          "Customers remain responsible for choosing retention periods that match their tax, accounting, and contractual obligations."
        ]
      },
      {
        title: "Audit and logs",
        body: [
          "Audit events record security, login, upload, download, deletion, organization, and admin activity.",
          "The backend supports AUDIT_PERSONAL_DATA_DAYS for personal-data redaction and AUDIT_RETENTION_DAYS for audit-event retention."
        ]
      },
      {
        title: "Backups and storage",
        body: [
          "Production storage is designed for S3-compatible object storage with deletion retries and dead-letter handling.",
          "Deleted data may remain in backups until the normal backup rotation removes it, unless earlier deletion is legally required and technically feasible."
        ]
      },
      {
        title: "Missing launch details",
        body: [
          "Before launch, set production retention values, backup rotation, restore testing cadence, export support process, and deletion support contact in docs/legal/retention-deletion-policy.md."
        ]
      }
    ]
  },
  support: {
    slug: "support",
    title: "Support and Contact",
    effectiveDate: "26 June 2026",
    description:
      "Support scope, contact channels, and the public non-government/non-certification disclaimer.",
    sections: [
      {
        title: "Contact channels",
        body: [
          "Support, privacy, security, and billing contact addresses must be filled before launch.",
          "Security issues should be sent to the dedicated security contact and should not include unauthorized access to another customer's data."
        ]
      },
      {
        title: "Support scope",
        body: [
          "Support covers account access, verification, password reset, organization roles, upload errors, validation software behavior, export and deletion requests, suspected bugs, and security reports.",
          "Support does not include tax advice, legal advice, accounting advice, official KSeF certification, or deciding whether an invoice should be submitted."
        ]
      },
      {
        title: "Response targets",
        body: [
          "Starter response targets are 1 business day for critical and high issues, 2 business days for normal issues, and 5 business days for low-priority requests.",
          "Final response commitments must be confirmed before paid launch."
        ]
      },
      {
        title: "Disclaimer",
        body: [
          "KSeF Helper is not an official KSeF, Ministry of Finance, government, tax, legal, accounting, or certification tool.",
          "The service helps validate, preview, archive, export, and delete FA(3) XML invoice data, but customers remain responsible for their own obligations."
        ]
      }
    ]
  }
} satisfies Record<string, LegalDocument>;

type LegalSlug = keyof typeof documents;

const navItems: Array<{ slug: LegalSlug; label: string }> = [
  { slug: "terms", label: "Terms" },
  { slug: "privacy", label: "Privacy" },
  { slug: "dpa", label: "DPA" },
  { slug: "retention", label: "Retention" },
  { slug: "support", label: "Support" }
];

function isLegalSlug(slug: string | undefined): slug is LegalSlug {
  return Boolean(slug && Object.prototype.hasOwnProperty.call(documents, slug));
}

export function LegalPage() {
  const { slug } = useParams();
  if (!isLegalSlug(slug)) {
    return <Navigate to="/legal/terms" replace />;
  }

  const document = documents[slug];

  return (
    <div className="min-h-screen bg-paper">
      <header className="border-b border-line bg-white">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-4 px-4 py-4 sm:px-6">
          <Link to="/" className="focus-ring rounded-md text-lg font-bold text-ink">
            KSeF Helper
          </Link>
          <nav className="flex flex-wrap gap-2">
            {navItems.map((item) => (
              <Link
                key={item.slug}
                to={`/legal/${item.slug}`}
                className={`focus-ring rounded-md px-3 py-2 text-sm font-semibold ${
                  item.slug === slug ? "bg-emerald-700 text-white" : "text-neutral-700 hover:bg-neutral-100"
                }`}
              >
                {item.label}
              </Link>
            ))}
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-10 sm:px-6">
        <p className="text-sm font-semibold text-emerald-800">Effective {document.effectiveDate}</p>
        <h1 className="mt-3 text-3xl font-bold text-ink">{document.title}</h1>
        <p className="mt-4 max-w-3xl text-neutral-700">{document.description}</p>
        <div className="mt-6 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
          Draft template. Fill the placeholders in <span className="font-mono">docs/legal</span> and get legal review before
          public paid launch.
        </div>
        <div className="mt-8 space-y-6">
          {document.sections.map((section) => (
            <section key={section.title} className="rounded-lg border border-line bg-white p-5">
              <h2 className="text-lg font-bold text-ink">{section.title}</h2>
              <div className="mt-3 space-y-3 text-neutral-700">
                {section.body.map((paragraph) => (
                  <p key={paragraph}>{paragraph}</p>
                ))}
              </div>
            </section>
          ))}
        </div>
      </main>
    </div>
  );
}
