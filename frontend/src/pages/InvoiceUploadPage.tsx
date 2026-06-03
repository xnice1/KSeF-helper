import { useMutation, useQuery } from "@tanstack/react-query";
import { UploadCloud } from "lucide-react";
import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { StatusBadge } from "../components/StatusBadge";

export function InvoiceUploadPage() {
  const navigate = useNavigate();
  const [file, setFile] = useState<File | null>(null);
  const [companyId, setCompanyId] = useState("");
  const { data: companies = [] } = useQuery({ queryKey: ["companies"], queryFn: api.companies });
  const upload = useMutation({
    mutationFn: () => {
      if (!file) throw new Error("Choose an XML file first.");
      return api.uploadInvoice(file, companyId || undefined);
    }
  });

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    const result = await upload.mutateAsync();
    navigate(`/app/invoices/${result.invoiceId}`);
  }

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="text-2xl font-bold text-ink">Upload invoice XML</h1>
      <p className="mt-1 text-sm text-neutral-600">MVP accepts one .xml file per upload.</p>
      <form className="mt-6 rounded-lg border border-line bg-white p-6 shadow-soft" onSubmit={onSubmit}>
        <label className="flex cursor-pointer flex-col items-center justify-center rounded-lg border border-dashed border-line bg-paper px-6 py-10 text-center hover:bg-emerald-50">
          <UploadCloud className="text-emerald-700" size={34} />
          <span className="mt-3 font-semibold text-ink">{file ? file.name : "Choose XML file"}</span>
          <span className="mt-1 text-sm text-neutral-600">Maximum 10 MB. ZIP and PDF are future roadmap items.</span>
          <input
            className="sr-only"
            type="file"
            accept=".xml,application/xml,text/xml"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
          />
        </label>
        <label className="mt-5 block">
          <span className="text-sm font-semibold text-neutral-700">Company profile</span>
          <select className="focus-ring mt-1 w-full rounded-md border border-line px-3 py-2" value={companyId} onChange={(event) => setCompanyId(event.target.value)}>
            <option value="">No company selected</option>
            {companies.map((company) => (
              <option key={company.id} value={company.id}>
                {company.name} · {company.nip}
              </option>
            ))}
          </select>
        </label>
        <button className="focus-ring mt-5 rounded-md bg-emerald-700 px-4 py-2 font-semibold text-white" disabled={!file || upload.isPending}>
          {upload.isPending ? "Validating..." : "Upload and validate"}
        </button>
        {upload.error ? <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">{upload.error.message}</p> : null}
        {upload.data ? (
          <div className="mt-4 rounded-md border border-line p-4">
            <div className="flex items-center gap-3">
              <StatusBadge status={upload.data.status} />
              <span className="font-semibold text-ink">{upload.data.invoiceNumber ?? "Uploaded invoice"}</span>
            </div>
            <Link className="mt-3 inline-block text-sm font-semibold text-emerald-700" to={`/app/invoices/${upload.data.invoiceId}`}>
              Open preview
            </Link>
          </div>
        ) : null}
      </form>
    </div>
  );
}
