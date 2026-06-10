import type {
  AuthResponse,
  Company,
  InvoicePreview,
  InvoiceSummary,
  InvoiceValidation,
  OrganizationType,
  UploadInvoiceResponse,
  ValidationReport
} from "../types/api";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080/api";
const TOKEN_KEY = "ksef-helper-token";

export type LoginPayload = {
  email: string;
  password: string;
};

export type RegisterPayload = LoginPayload & {
  firstName: string;
  lastName: string;
  organizationName: string;
  organizationType: OrganizationType;
};

export type CompanyPayload = {
  name: string;
  nip: string;
  regon?: string;
  street: string;
  city: string;
  postalCode: string;
  country: string;
};

export type InvoiceFilters = {
  invoiceNumber?: string;
  sellerNip?: string;
  buyerNip?: string;
  companyId?: string;
  currency?: string;
  dateFrom?: string;
  dateTo?: string;
  uploadedFrom?: string;
  uploadedTo?: string;
  status?: string;
  minGrossAmount?: string;
  maxGrossAmount?: string;
};

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY)
};

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (!(init.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const token = tokenStore.get();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(`${API_URL}${path}`, { ...init, headers });
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(response.status, body?.message ?? "Request failed.");
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

function query(params: Record<string, string | undefined>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== "") {
      search.set(key, value);
    }
  });
  const text = search.toString();
  return text ? `?${text}` : "";
}

export const api = {
  login: (payload: LoginPayload) =>
    request<AuthResponse>("/auth/login", { method: "POST", body: JSON.stringify(payload) }),
  register: (payload: RegisterPayload) =>
    request<AuthResponse>("/auth/register", { method: "POST", body: JSON.stringify(payload) }),
  me: () => request<AuthResponse>("/auth/me"),

  companies: () => request<Company[]>("/companies"),
  createCompany: (payload: CompanyPayload) =>
    request<Company>("/companies", { method: "POST", body: JSON.stringify(payload) }),
  updateCompany: (id: string, payload: CompanyPayload) =>
    request<Company>(`/companies/${id}`, { method: "PUT", body: JSON.stringify(payload) }),
  deleteCompany: (id: string) => request<void>(`/companies/${id}`, { method: "DELETE" }),

  invoices: (filters: InvoiceFilters = {}) => request<InvoiceSummary[]>(`/invoices${query(filters)}`),
  uploadInvoice: (file: File, companyId?: string) => {
    const body = new FormData();
    body.append("file", file);
    if (companyId) {
      body.append("companyId", companyId);
    }
    return request<UploadInvoiceResponse>("/invoices/upload", { method: "POST", body });
  },
  invoice: (id: string) => request<InvoiceSummary>(`/invoices/${id}`),
  invoicePreview: (id: string) => request<InvoicePreview>(`/invoices/${id}/preview`),
  invoiceValidation: (id: string) => request<InvoiceValidation>(`/invoices/${id}/validation`),
  revalidateInvoice: (id: string) =>
    request<InvoiceValidation>(`/invoices/${id}/revalidate`, { method: "POST" }),
  validationReport: (id: string) => request<ValidationReport>(`/reports/invoices/${id}/validation-report`),
  deleteInvoice: (id: string) => request<void>(`/invoices/${id}`, { method: "DELETE" }),
  downloadOriginalFile: async (id: string) => {
    const token = tokenStore.get();
    const response = await fetch(`${API_URL}/invoices/${id}/download-original`, {
      headers: token ? { Authorization: `Bearer ${token}` } : undefined
    });
    if (!response.ok) {
      throw new ApiError(response.status, "Original XML could not be downloaded.");
    }
    const disposition = response.headers.get("content-disposition") ?? "";
    const filename = disposition.match(/filename="(.+)"/)?.[1] ?? "invoice.xml";
    return { filename, blob: await response.blob() };
  }
};
