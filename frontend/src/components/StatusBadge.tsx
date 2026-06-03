import type { InvoiceStatus, ValidationSeverity, ValidationStatus } from "../types/api";

type Status = InvoiceStatus | ValidationStatus | ValidationSeverity;

const styles: Record<string, string> = {
  VALID: "bg-emerald-100 text-emerald-800 border-emerald-200",
  INVALID: "bg-rose-100 text-rose-800 border-rose-200",
  WARNING: "bg-amber-100 text-amber-800 border-amber-200",
  ERROR: "bg-rose-100 text-rose-800 border-rose-200",
  INFO: "bg-sky-100 text-sky-800 border-sky-200",
  UPLOADED: "bg-neutral-100 text-neutral-700 border-neutral-200",
  ARCHIVED: "bg-stone-100 text-stone-700 border-stone-200"
};

export function StatusBadge({ status }: { status: Status }) {
  return (
    <span className={`inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-semibold ${styles[status]}`}>
      {status}
    </span>
  );
}
