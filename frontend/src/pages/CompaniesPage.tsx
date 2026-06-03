import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Edit2, Trash2 } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { api, CompanyPayload } from "../api/client";
import { EmptyState } from "../components/EmptyState";
import type { Company } from "../types/api";

const schema = z.object({
  name: z.string().min(1),
  nip: z.string().min(1),
  regon: z.string().optional(),
  street: z.string().min(1),
  city: z.string().min(1),
  postalCode: z.string().min(1),
  country: z.string().length(2)
});

type FormValues = z.infer<typeof schema>;

const empty: FormValues = { name: "", nip: "", regon: "", street: "", city: "", postalCode: "", country: "PL" };

export function CompaniesPage() {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<Company | null>(null);
  const { data: companies = [] } = useQuery({ queryKey: ["companies"], queryFn: api.companies });
  const { register, handleSubmit, reset, formState } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: empty });

  const save = useMutation({
    mutationFn: (payload: CompanyPayload) => (editing ? api.updateCompany(editing.id, payload) : api.createCompany(payload)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["companies"] });
      setEditing(null);
      reset(empty);
    }
  });

  const remove = useMutation({
    mutationFn: api.deleteCompany,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["companies"] })
  });

  function edit(company: Company) {
    setEditing(company);
    reset({
      name: company.name,
      nip: company.nip,
      regon: company.regon ?? "",
      street: company.street,
      city: company.city,
      postalCode: company.postalCode,
      country: company.country
    });
  }

  return (
    <div className="grid gap-6 xl:grid-cols-[1fr_380px]">
      <section className="rounded-lg border border-line bg-white p-5 shadow-soft">
        <h1 className="text-2xl font-bold text-ink">Companies</h1>
        <div className="mt-5">
          {companies.length === 0 ? (
            <EmptyState title="No company profiles">Create a company profile to connect uploads with a business record.</EmptyState>
          ) : (
            <div className="space-y-3">
              {companies.map((company) => (
                <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-line p-4" key={company.id}>
                  <div>
                    <p className="font-bold text-ink">{company.name}</p>
                    <p className="text-sm text-neutral-600">NIP {company.nip} · {company.city}, {company.country}</p>
                  </div>
                  <div className="flex gap-2">
                    <button className="focus-ring rounded-md border border-line p-2 text-neutral-700" onClick={() => edit(company)} title="Edit">
                      <Edit2 size={17} />
                    </button>
                    <button className="focus-ring rounded-md border border-line p-2 text-rose-700" onClick={() => remove.mutate(company.id)} title="Delete">
                      <Trash2 size={17} />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>

      <section className="rounded-lg border border-line bg-white p-5 shadow-soft">
        <h2 className="font-bold text-ink">{editing ? "Edit company" : "Create company"}</h2>
        <form className="mt-4 space-y-3" onSubmit={handleSubmit((values) => save.mutate(values))}>
          {[
            ["name", "Name"],
            ["nip", "NIP"],
            ["regon", "REGON"],
            ["street", "Street"],
            ["city", "City"],
            ["postalCode", "Postal code"],
            ["country", "Country"]
          ].map(([name, label]) => (
            <label className="block" key={name}>
              <span className="text-sm font-semibold text-neutral-700">{label}</span>
              <input className="focus-ring mt-1 w-full rounded-md border border-line px-3 py-2" {...register(name as keyof FormValues)} />
              {formState.errors[name as keyof FormValues]?.message ? (
                <span className="mt-1 block text-sm text-rose-700">{String(formState.errors[name as keyof FormValues]?.message)}</span>
              ) : null}
            </label>
          ))}
          <div className="flex gap-2">
            <button className="focus-ring rounded-md bg-emerald-700 px-4 py-2 text-sm font-semibold text-white" disabled={save.isPending}>
              {save.isPending ? "Saving..." : "Save"}
            </button>
            {editing ? (
              <button
                className="focus-ring rounded-md border border-line px-4 py-2 text-sm font-semibold text-neutral-700"
                type="button"
                onClick={() => {
                  setEditing(null);
                  reset(empty);
                }}
              >
                Cancel
              </button>
            ) : null}
          </div>
        </form>
      </section>
    </div>
  );
}
