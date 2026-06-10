import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { RefreshCw } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api/client";
import { EmptyState } from "../components/EmptyState";
import { StatusBadge } from "../components/StatusBadge";

export function ValidationPage() {
  const { id } = useParams<{ id?: string }>();
  if (id) {
    return <ValidationReport invoiceId={id} />;
  }
  return <ValidationIndex />;
}

function ValidationIndex() {
  const { data: invoices = [], isLoading } = useQuery({ queryKey: ["invoices", "validation-index"], queryFn: () => api.invoices() });
  const attention = invoices.filter((invoice) => invoice.status === "INVALID" || invoice.status === "WARNING");

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-ink">Validation</h1>
        <p className="mt-1 text-sm text-neutral-600">Invoices needing review appear first.</p>
      </div>
      <section className="rounded-lg border border-line bg-white p-5 shadow-soft">
        {isLoading ? (
          <p className="text-sm text-neutral-600">Loading validation results...</p>
        ) : attention.length === 0 ? (
          <EmptyState title="No invoices need attention">Warnings and invalid invoices will appear here.</EmptyState>
        ) : (
          <div className="space-y-3">
            {attention.map((invoice) => (
              <Link className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-line p-4 hover:bg-paper" key={invoice.id} to={`/app/validation/${invoice.id}`}>
                <div>
                  <p className="font-bold text-ink">{invoice.invoiceNumber ?? "No invoice number"}</p>
                  <p className="text-sm text-neutral-600">{invoice.sellerName ?? "-"} to {invoice.buyerName ?? "-"}</p>
                </div>
                <StatusBadge status={invoice.status} />
              </Link>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

function ValidationReport({ invoiceId }: { invoiceId: string }) {
  const queryClient = useQueryClient();
  const { data: report, isLoading } = useQuery({
    queryKey: ["validation-report", invoiceId],
    queryFn: () => api.validationReport(invoiceId)
  });
  const revalidate = useMutation({
    mutationFn: () => api.revalidateInvoice(invoiceId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["validation-report", invoiceId] }),
        queryClient.invalidateQueries({ queryKey: ["invoice-preview", invoiceId] }),
        queryClient.invalidateQueries({ queryKey: ["invoices"] })
      ]);
    }
  });

  if (isLoading || !report) {
    return <p className="text-sm text-neutral-600">Loading validation report...</p>;
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-ink">Validation report</h1>
          <p className="mt-1 text-sm text-neutral-600">{report.invoice.invoiceNumber ?? "Invoice"} · Generated {new Date(report.generatedAt).toLocaleString()}</p>
        </div>
        <div className="flex items-center gap-2">
          <StatusBadge status={report.validationStatus} />
          <button
            className="focus-ring inline-flex items-center gap-2 rounded-md border border-line bg-white px-4 py-2 text-sm font-semibold text-neutral-700"
            disabled={revalidate.isPending}
            onClick={() => revalidate.mutate()}
          >
            <RefreshCw className={revalidate.isPending ? "animate-spin" : ""} size={16} />
            {revalidate.isPending ? "Revalidating..." : "Revalidate"}
          </button>
        </div>
      </div>
      {revalidate.error ? <p className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">{revalidate.error.message}</p> : null}
      <section className="grid gap-4 sm:grid-cols-3">
        <Metric label="Errors" value={report.errors.length} tone="text-rose-700" />
        <Metric label="Warnings" value={report.warnings.length} tone="text-amber-700" />
        <Metric label="Suggestions" value={report.suggestions.length} tone="text-emerald-700" />
      </section>
      <Messages title="Errors" messages={report.errors} />
      <Messages title="Warnings" messages={report.warnings} />
      <section className="rounded-lg border border-line bg-white p-5 shadow-soft">
        <h2 className="font-bold text-ink">Suggestions</h2>
        {report.suggestions.length === 0 ? (
          <p className="mt-3 text-sm text-neutral-600">No suggestions.</p>
        ) : (
          <ul className="mt-3 list-disc space-y-2 pl-5 text-sm text-neutral-700">
            {report.suggestions.map((suggestion) => (
              <li key={suggestion}>{suggestion}</li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function Metric({ label, value, tone }: { label: string; value: number; tone: string }) {
  return (
    <div className="rounded-lg border border-line bg-white p-5 shadow-soft">
      <p className="text-sm font-semibold text-neutral-600">{label}</p>
      <p className={`mt-3 text-3xl font-bold ${tone}`}>{value}</p>
    </div>
  );
}

function Messages({ title, messages }: { title: string; messages: { severity: "ERROR" | "WARNING" | "INFO"; code: string; message: string; suggestion?: string | null }[] }) {
  return (
    <section className="rounded-lg border border-line bg-white p-5 shadow-soft">
      <h2 className="font-bold text-ink">{title}</h2>
      {messages.length === 0 ? (
        <p className="mt-3 text-sm text-neutral-600">No {title.toLowerCase()}.</p>
      ) : (
        <div className="mt-4 space-y-3">
          {messages.map((message) => (
            <div className="rounded-md border border-line p-4" key={`${message.code}-${message.message}`}>
              <StatusBadge status={message.severity} />
              <p className="mt-2 font-semibold text-ink">{message.message}</p>
              {message.suggestion ? <p className="mt-1 text-sm text-neutral-600">{message.suggestion}</p> : null}
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
