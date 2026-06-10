import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, CheckCircle2, FileText, XCircle } from "lucide-react";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import { EmptyState } from "../components/EmptyState";
import { StatusBadge } from "../components/StatusBadge";
import type { InvoiceStatus } from "../types/api";
import { useAuth } from "../auth/AuthProvider";
import { hasPermission } from "../auth/permissions";

const cards: { label: string; status?: InvoiceStatus; icon: typeof FileText; tone: string }[] = [
  { label: "Total invoices", icon: FileText, tone: "text-neutral-700" },
  { label: "Valid", status: "VALID", icon: CheckCircle2, tone: "text-emerald-700" },
  { label: "Warnings", status: "WARNING", icon: AlertTriangle, tone: "text-amber-700" },
  { label: "Invalid", status: "INVALID", icon: XCircle, tone: "text-rose-700" }
];

export function DashboardPage() {
  const { auth } = useAuth();
  const { data: invoices = [], isLoading } = useQuery({ queryKey: ["invoices"], queryFn: () => api.invoices() });
  const latest = invoices.slice(0, 5);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-ink">Dashboard</h1>
          <p className="mt-1 text-sm text-neutral-600">Your invoice validation overview.</p>
        </div>
        {hasPermission(auth?.organization?.role, "uploadInvoices") ? (
          <Link className="focus-ring rounded-md bg-emerald-700 px-4 py-2 text-sm font-semibold text-white" to="/app/upload">
            Upload XML
          </Link>
        ) : null}
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {cards.map((card) => {
          const Icon = card.icon;
          const value = card.status ? invoices.filter((invoice) => invoice.status === card.status).length : invoices.length;
          return (
            <div className="rounded-lg border border-line bg-white p-5 shadow-soft" key={card.label}>
              <div className="flex items-center justify-between">
                <p className="text-sm font-semibold text-neutral-600">{card.label}</p>
                <Icon className={card.tone} size={22} />
              </div>
              <p className="mt-4 text-3xl font-bold text-ink">{isLoading ? "-" : value}</p>
            </div>
          );
        })}
      </div>

      <section className="rounded-lg border border-line bg-white p-5 shadow-soft">
        <div className="flex items-center justify-between gap-3">
          <h2 className="font-bold text-ink">Latest uploaded invoices</h2>
          <Link className="text-sm font-semibold text-emerald-700" to="/app/invoices">
            View archive
          </Link>
        </div>
        <div className="mt-4">
          {latest.length === 0 ? (
            <EmptyState title="No invoices yet">Upload your first XML invoice to see validation results here.</EmptyState>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px] text-left text-sm">
                <thead className="border-b border-line text-neutral-500">
                  <tr>
                    <th className="py-3 pr-4">Invoice</th>
                    <th className="py-3 pr-4">Seller</th>
                    <th className="py-3 pr-4">Buyer</th>
                    <th className="py-3 pr-4">Gross</th>
                    <th className="py-3 pr-4">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {latest.map((invoice) => (
                    <tr className="border-b border-line last:border-0" key={invoice.id}>
                      <td className="py-3 pr-4">
                        <Link className="font-semibold text-emerald-700" to={`/app/invoices/${invoice.id}`}>
                          {invoice.invoiceNumber ?? "No number"}
                        </Link>
                      </td>
                      <td className="py-3 pr-4">{invoice.sellerName ?? "-"}</td>
                      <td className="py-3 pr-4">{invoice.buyerName ?? "-"}</td>
                      <td className="py-3 pr-4">{money(invoice.grossAmount, invoice.currency)}</td>
                      <td className="py-3 pr-4"><StatusBadge status={invoice.status} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </section>
    </div>
  );
}

function money(value?: number | null, currency?: string | null) {
  if (value == null) return "-";
  return `${value.toFixed(2)} ${currency ?? ""}`.trim();
}
