import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Search, Trash2 } from "lucide-react";
import { FormEvent, useState } from "react";
import { Link } from "react-router-dom";
import { api, InvoiceFilters } from "../api/client";
import { EmptyState } from "../components/EmptyState";
import { StatusBadge } from "../components/StatusBadge";
import { useAuth } from "../auth/AuthProvider";
import { hasPermission } from "../auth/permissions";

export function InvoiceArchivePage() {
  const { auth } = useAuth();
  const canUpload = hasPermission(auth?.organization?.role, "uploadInvoices");
  const canDelete = hasPermission(auth?.organization?.role, "deleteInvoices");
  const queryClient = useQueryClient();
  const [filters, setFilters] = useState<InvoiceFilters>({});
  const [draft, setDraft] = useState<InvoiceFilters>({});
  const { data: invoices = [], isLoading } = useQuery({ queryKey: ["invoices", filters], queryFn: () => api.invoices(filters) });
  const { data: companies = [] } = useQuery({ queryKey: ["companies"], queryFn: api.companies });
  const remove = useMutation({
    mutationFn: api.deleteInvoice,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["invoices"] })
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    setFilters(draft);
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-ink">Invoice archive</h1>
          <p className="mt-1 text-sm text-neutral-600">Search and filter uploaded XML invoices.</p>
        </div>
        {canUpload ? (
          <Link className="focus-ring rounded-md bg-emerald-700 px-4 py-2 text-sm font-semibold text-white" to="/app/upload">
            Upload XML
          </Link>
        ) : null}
      </div>
      <form className="grid gap-3 rounded-lg border border-line bg-white p-4 shadow-soft md:grid-cols-4" onSubmit={submit}>
        <Input label="Invoice number" value={draft.invoiceNumber} onChange={(invoiceNumber) => setDraft({ ...draft, invoiceNumber })} />
        <Input label="Seller NIP" value={draft.sellerNip} onChange={(sellerNip) => setDraft({ ...draft, sellerNip })} />
        <Input label="Buyer NIP" value={draft.buyerNip} onChange={(buyerNip) => setDraft({ ...draft, buyerNip })} />
        <Input label="Currency" value={draft.currency} onChange={(currency) => setDraft({ ...draft, currency })} />
        <Input label="Issue from" type="date" value={draft.dateFrom} onChange={(dateFrom) => setDraft({ ...draft, dateFrom })} />
        <Input label="Issue to" type="date" value={draft.dateTo} onChange={(dateTo) => setDraft({ ...draft, dateTo })} />
        <Input label="Uploaded from" type="date" value={draft.uploadedFrom} onChange={(uploadedFrom) => setDraft({ ...draft, uploadedFrom })} />
        <Input label="Uploaded to" type="date" value={draft.uploadedTo} onChange={(uploadedTo) => setDraft({ ...draft, uploadedTo })} />
        <Input label="Min gross" type="number" value={draft.minGrossAmount} onChange={(minGrossAmount) => setDraft({ ...draft, minGrossAmount })} />
        <Input label="Max gross" type="number" value={draft.maxGrossAmount} onChange={(maxGrossAmount) => setDraft({ ...draft, maxGrossAmount })} />
        <label className="block">
          <span className="text-sm font-semibold text-neutral-700">Company</span>
          <select className="focus-ring mt-1 w-full rounded-md border border-line px-3 py-2" value={draft.companyId ?? ""} onChange={(e) => setDraft({ ...draft, companyId: e.target.value })}>
            <option value="">Any</option>
            {companies.map((company) => (
              <option key={company.id} value={company.id}>{company.name}</option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="text-sm font-semibold text-neutral-700">Status</span>
          <select className="focus-ring mt-1 w-full rounded-md border border-line px-3 py-2" value={draft.status ?? ""} onChange={(e) => setDraft({ ...draft, status: e.target.value })}>
            <option value="">Any</option>
            <option value="UPLOADED">Uploaded</option>
            <option value="VALID">Valid</option>
            <option value="WARNING">Warning</option>
            <option value="INVALID">Invalid</option>
            <option value="ARCHIVED">Archived</option>
          </select>
        </label>
        <button className="focus-ring inline-flex items-center justify-center gap-2 rounded-md bg-emerald-700 px-4 py-2 text-sm font-semibold text-white md:col-span-4">
          <Search size={16} />
          Apply filters
        </button>
      </form>
      <section className="rounded-lg border border-line bg-white p-5 shadow-soft">
        {isLoading ? (
          <p className="text-sm text-neutral-600">Loading invoices...</p>
        ) : invoices.length === 0 ? (
          <EmptyState title="No matching invoices">Upload an invoice XML or relax the filters.</EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[900px] text-left text-sm">
              <thead className="border-b border-line text-neutral-500">
                <tr>
                  <th className="py-3 pr-4">Invoice</th>
                  <th className="py-3 pr-4">Seller</th>
                  <th className="py-3 pr-4">Buyer</th>
                  <th className="py-3 pr-4">Issue date</th>
                  <th className="py-3 pr-4">Gross</th>
                  <th className="py-3 pr-4">Status</th>
                  <th className="py-3 pr-4">Uploaded</th>
                  <th className="py-3 pr-4"></th>
                </tr>
              </thead>
              <tbody>
                {invoices.map((invoice) => (
                  <tr className="border-b border-line last:border-0" key={invoice.id}>
                    <td className="py-3 pr-4">
                      <Link className="font-semibold text-emerald-700" to={`/app/invoices/${invoice.id}`}>
                        {invoice.invoiceNumber ?? "No number"}
                      </Link>
                    </td>
                    <td className="py-3 pr-4">{invoice.sellerName ?? "-"}</td>
                    <td className="py-3 pr-4">{invoice.buyerName ?? "-"}</td>
                    <td className="py-3 pr-4">{invoice.issueDate ?? "-"}</td>
                    <td className="py-3 pr-4">{money(invoice.grossAmount, invoice.currency)}</td>
                    <td className="py-3 pr-4"><StatusBadge status={invoice.status} /></td>
                    <td className="py-3 pr-4">{new Date(invoice.createdAt).toLocaleDateString()}</td>
                    <td className="py-3 pr-4">
                      {canDelete ? (
                      <button className="focus-ring rounded-md border border-line p-2 text-rose-700" onClick={() => remove.mutate(invoice.id)} title="Delete">
                        <Trash2 size={16} />
                      </button>
                      ) : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}

function Input({ label, type = "text", value, onChange }: { label: string; type?: string; value?: string; onChange: (value: string) => void }) {
  return (
    <label className="block">
      <span className="text-sm font-semibold text-neutral-700">{label}</span>
      <input className="focus-ring mt-1 w-full rounded-md border border-line px-3 py-2" type={type} value={value ?? ""} onChange={(event) => onChange(event.target.value)} />
    </label>
  );
}

function money(value?: number | null, currency?: string | null) {
  if (value == null) return "-";
  return `${value.toFixed(2)} ${currency ?? ""}`.trim();
}
