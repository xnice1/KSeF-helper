import type {
  AuthResponse,
  AuditEvent,
  Company,
  InvoicePreview,
  InvoiceSummary,
  InvoiceValidation,
  OrganizationType,
  MessageResponse,
  UploadInvoiceResponse,
  ValidationReport
} from "../types/api";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080/api";
let accessToken: string | null = null;
let refreshPromise: Promise<AuthResponse> | null = null;

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
  get: () => accessToken,
  set: (token: string | null) => {
    accessToken = token;
  },
  clear: () => {
    accessToken = null;
  }
};

async function request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const headers = new Headers(init.headers);
  if (!(init.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const token = tokenStore.get();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(`${API_URL}${path}`, { ...init, headers, credentials: "include" });
  if (response.status === 401 && retry && !path.startsWith("/auth/")) {
    await refreshAccess();
    return request<T>(path, init, false);
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(response.status, body?.message ?? "Request failed.");
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

async function refreshAccess(): Promise<AuthResponse> {
  if (!refreshPromise) {
    refreshPromise = request<AuthResponse>("/auth/refresh", { method: "POST" }, false)
      .then((response) => {
        tokenStore.set(response.token);
        return response;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

async function download(path: string, fallbackFilename: string, retry = true) {
  const token = tokenStore.get();
  const response = await fetch(`${API_URL}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    credentials: "include"
  });
  if (response.status === 401 && retry) {
    await refreshAccess();
    return download(path, fallbackFilename, false);
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(response.status, body?.message ?? "Download failed.");
  }
  const disposition = response.headers.get("content-disposition") ?? "";
  const encodedFilename = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  const plainFilename = disposition.match(/filename="([^"]+)"/i)?.[1];
  const filename = encodedFilename ? decodeURIComponent(encodedFilename) : plainFilename ?? fallbackFilename;
  return { filename, blob: await response.blob() };
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
  refresh: refreshAccess,
  logout: () => request<void>("/auth/logout", { method: "POST" }, false),
  me: () => request<AuthResponse>("/auth/me"),
  switchOrganization: (organizationId: string) =>
    request<AuthResponse>(`/auth/switch-organization/${organizationId}`, { method: "POST" }),
  requestVerification: (email: string) =>
    request<MessageResponse>("/auth/request-verification", {
      method: "POST",
      body: JSON.stringify({ email })
    }),
  verifyEmail: (token: string) =>
    request<AuthResponse>("/auth/verify-email", {
      method: "POST",
      body: JSON.stringify({ token })
    }),
  forgotPassword: (email: string) =>
    request<MessageResponse>("/auth/forgot-password", {
      method: "POST",
      body: JSON.stringify({ email })
    }),
  resetPassword: (token: string, password: string) =>
    request<MessageResponse>("/auth/reset-password", {
      method: "POST",
      body: JSON.stringify({ token, password })
    }),
  auditEvents: () => request<AuditEvent[]>("/organizations/current/audit-events"),
  exportOrganization: () => download("/organizations/current/export", "ksef-helper-export.zip"),
  deleteOrganization: (password: string, confirmation: string) =>
    request<void>("/organizations/current", {
      method: "DELETE",
      body: JSON.stringify({ password, confirmation })
    }),
  deleteAccount: (password: string, confirmation: string) =>
    request<void>("/account", {
      method: "DELETE",
      body: JSON.stringify({ password, confirmation })
    }),

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
  downloadOriginalFile: (id: string) => download(`/invoices/${id}/download-original`, "invoice.xml")
};
