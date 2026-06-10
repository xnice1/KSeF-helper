import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Download, FileWarning, RefreshCw } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api/client";
import { StatusBadge } from "../components/StatusBadge";
import { useAuth } from "../auth/AuthProvider";
import { hasPermission } from "../auth/permissions";

export function InvoiceDetailsPage() {
  const { auth } = useAuth();
  const canRevalidate = hasPermission(auth?.organization?.role, "revalidateInvoices");
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const { data: preview, isLoading } = useQuery({
    queryKey: ["invoice-preview", id],
    queryFn: () => api.invoicePreview(id!),
    enabled: Boolean(id)
  });
  const download = useMutation({
    mutationFn: async () => {
      const file = await api.downloadOriginalFile(id!);
      const url = URL.createObjectURL(file.blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = file.filename;
      a.click();
      URL.revokeObjectURL(url);
    }
  });
  const revalidate = useMutation({
    mutationFn: () => api.revalidateInvoice(id!),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["invoice-preview", id] }),
        queryClient.invalidateQueries({ queryKey: ["validation-report", id] }),
        queryClient.invalidateQueries({ queryKey: ["invoices"] })
      ]);
    }
  });

  if (isLoading || !preview) {
    return <p className="text-sm text-neutral-600">Loading invoice preview...</p>;
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-ink">{preview.header.invoiceNumber ?? "Invoice preview"}</h1>
          <p className="mt-1 text-sm text-neutral-600">Uploaded {new Date(preview.uploadedAt).toLocaleString()}</p>
        </div>
        <div className="flex gap-2">
          {canRevalidate ? <button
            className="focus-ring inline-flex items-center gap-2 rounded-md border border-line bg-white px-4 py-2 text-sm font-semibold text-neutral-700"
            disabled={revalidate.isPending}
            onClick={() => revalidate.mutate()}
          >
            <RefreshCw className={revalidate.isPending ? "animate-spin" : ""} size={16} />
            {revalidate.isPending ? "Revalidating..." : "Revalidate"}
          </button> : null}
          <Link className="focus-ring rounded-md border border-line bg-white px-4 py-2 text-sm font-semibold text-neutral-700" to={`/app/validation/${preview.id}`}>
            Validation
          </Link>
          <button className="focus-ring inline-flex items-center gap-2 rounded-md bg-emerald-700 px-4 py-2 text-sm font-semibold text-white" onClick={() => download.mutate()}>
            <Download size={16} />
            XML
          </button>
        </div>
      </div>
      {revalidate.error ? <p className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">{revalidate.error.message}</p> : null}

      <section className="rounded-lg border border-line bg-white p-6 shadow-soft">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line pb-5">
          <div>
            <p className="text-sm font-semibold text-neutral-500">Invoice</p>
            <p className="text-xl font-bold text-ink">{preview.header.invoiceNumber ?? "-"}</p>
          </div>
          <StatusBadge status={preview.validation.status} />
        </div>
        <div className="grid gap-5 py-5 md:grid-cols-2">
          <Party title="Seller" name={preview.seller.name} nip={preview.seller.nip} />
          <Party title="Buyer" name={preview.buyer.name} nip={preview.buyer.nip} />
        </div>
        <div className="grid gap-4 border-y border-line py-5 sm:grid-cols-4">
          <Info label="Issue date" value={preview.header.issueDate} />
          <Info label="Sale date" value={preview.header.saleDate} />
          <Info label="Payment" value={preview.payment.paymentMethod} />
          <Info label="Bank account" value={preview.payment.bankAccount} />
        </div>
        <div className="mt-5 overflow-x-auto">
          <table className="w-full min-w-[780px] text-left text-sm">
            <thead className="border-b border-line text-neutral-500">
              <tr>
                <th className="py-3 pr-4">Item</th>
                <th className="py-3 pr-4">Qty</th>
                <th className="py-3 pr-4">Unit</th>
                <th className="py-3 pr-4">Net</th>
                <th className="py-3 pr-4">VAT %</th>
                <th className="py-3 pr-4">VAT</th>
                <th className="py-3 pr-4">Gross</th>
              </tr>
            </thead>
            <tbody>
              {preview.items.map((item) => (
                <tr className="border-b border-line last:border-0" key={item.id}>
                  <td className="py-3 pr-4 font-semibold">{item.name ?? "-"}</td>
                  <td className="py-3 pr-4">{item.quantity ?? "-"}</td>
                  <td className="py-3 pr-4">{amount(item.unitPrice)}</td>
                  <td className="py-3 pr-4">{amount(item.netAmount)}</td>
                  <td className="py-3 pr-4">{item.vatRate ?? "-"}</td>
                  <td className="py-3 pr-4">{amount(item.vatAmount)}</td>
                  <td className="py-3 pr-4">{amount(item.grossAmount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="mt-6 flex justify-end">
          <div className="w-full max-w-sm rounded-lg bg-paper p-4">
            <Info label="Net amount" value={money(preview.totals.netAmount, preview.header.currency)} />
            <Info label="VAT amount" value={money(preview.totals.vatAmount, preview.header.currency)} />
            <Info label="Gross amount" value={money(preview.totals.grossAmount, preview.header.currency)} strong />
          </div>
        </div>
      </section>

      <section className="rounded-lg border border-line bg-white p-5 shadow-soft">
        <div className="flex items-center gap-2">
          <FileWarning className="text-amber-700" size={20} />
          <h2 className="font-bold text-ink">Validation messages</h2>
        </div>
        <div className="mt-4 space-y-3">
          {preview.validation.messages.length === 0 ? (
            <p className="text-sm text-neutral-600">No validation messages.</p>
          ) : (
            preview.validation.messages.map((message) => (
              <div className="rounded-md border border-line p-4" key={`${message.code}-${message.fieldPath}`}>
                <StatusBadge status={message.severity} />
                <p className="mt-2 font-semibold text-ink">{message.message}</p>
                {message.suggestion ? <p className="mt-1 text-sm text-neutral-600">{message.suggestion}</p> : null}
              </div>
            ))
          )}
        </div>
      </section>
    </div>
  );
}

function Party({ title, name, nip }: { title: string; name?: string | null; nip?: string | null }) {
  return (
    <div className="rounded-lg border border-line p-4">
      <p className="text-sm font-semibold text-neutral-500">{title}</p>
      <p className="mt-2 font-bold text-ink">{name ?? "-"}</p>
      <p className="text-sm text-neutral-600">NIP {nip ?? "-"}</p>
    </div>
  );
}

function Info({ label, value, strong }: { label: string; value?: string | null; strong?: boolean }) {
  return (
    <div className="mb-2 flex justify-between gap-4 last:mb-0">
      <span className="text-sm text-neutral-500">{label}</span>
      <span className={strong ? "font-bold text-ink" : "font-semibold text-neutral-700"}>{value ?? "-"}</span>
    </div>
  );
}

function amount(value?: number | null) {
  return value == null ? "-" : value.toFixed(2);
}

function money(value?: number | null, currency?: string | null) {
  if (value == null) return "-";
  return `${value.toFixed(2)} ${currency ?? ""}`.trim();
}
