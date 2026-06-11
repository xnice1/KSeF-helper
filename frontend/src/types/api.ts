export type OrganizationType = "FREELANCER" | "BUSINESS" | "ACCOUNTING_OFFICE";
export type InvoiceStatus = "UPLOADED" | "VALID" | "INVALID" | "WARNING" | "ARCHIVED";
export type ValidationStatus = "VALID" | "INVALID" | "WARNING";
export type ValidationSeverity = "ERROR" | "WARNING" | "INFO";
export type MembershipRole = "OWNER" | "ACCOUNTANT" | "CLIENT" | "EMPLOYEE";
export type OrganizationProfile = {
  id: string;
  name: string;
  type: OrganizationType;
  role: MembershipRole;
};

export type AuthResponse = {
  token: string | null;
  accessTokenExpiresAt: string | null;
  user: {
    id: string;
    email: string;
    firstName: string;
    lastName: string;
    emailVerified: boolean;
  };
  organization: OrganizationProfile | null;
  organizations: OrganizationProfile[];
};

export type MessageResponse = {
  message: string;
};

export type Company = {
  id: string;
  organizationId: string;
  name: string;
  nip: string;
  regon?: string | null;
  street: string;
  city: string;
  postalCode: string;
  country: string;
  createdAt: string;
  updatedAt: string;
};

export type InvoiceSummary = {
  id: string;
  companyId?: string | null;
  invoiceNumber?: string | null;
  sellerName?: string | null;
  sellerNip?: string | null;
  buyerName?: string | null;
  buyerNip?: string | null;
  issueDate?: string | null;
  saleDate?: string | null;
  currency?: string | null;
  netAmount?: number | null;
  vatAmount?: number | null;
  grossAmount?: number | null;
  status: InvoiceStatus;
  createdAt: string;
};

export type ValidationMessage = {
  severity: ValidationSeverity;
  code: string;
  fieldPath?: string | null;
  message: string;
  suggestion?: string | null;
};

export type InvoiceItem = {
  id: string;
  name?: string | null;
  quantity?: number | null;
  unitPrice?: number | null;
  netAmount?: number | null;
  vatRate?: string | null;
  vatAmount?: number | null;
  grossAmount?: number | null;
};

export type InvoicePreview = {
  id: string;
  header: {
    invoiceNumber?: string | null;
    issueDate?: string | null;
    saleDate?: string | null;
    currency?: string | null;
  };
  seller: {
    name?: string | null;
    nip?: string | null;
  };
  buyer: {
    name?: string | null;
    nip?: string | null;
  };
  payment: {
    paymentMethod?: string | null;
    bankAccount?: string | null;
  };
  totals: {
    netAmount?: number | null;
    vatAmount?: number | null;
    grossAmount?: number | null;
  };
  items: InvoiceItem[];
  validation: {
    status: InvoiceStatus;
    errors: number;
    warnings: number;
    messages: ValidationMessage[];
  };
  uploadedAt: string;
};

export type UploadInvoiceResponse = {
  invoiceId: string;
  status: InvoiceStatus;
  invoiceNumber?: string | null;
  validationMessages: ValidationMessage[];
};

export type InvoiceValidation = {
  invoiceId: string;
  status: ValidationStatus;
  createdAt: string;
  messages: ValidationMessage[];
};

export type ValidationReport = {
  invoice: InvoiceSummary;
  validationStatus: ValidationStatus;
  errors: ValidationMessage[];
  warnings: ValidationMessage[];
  suggestions: string[];
  generatedAt: string;
};
