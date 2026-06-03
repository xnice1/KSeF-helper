import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import { z } from "zod";
import { ApiError } from "../api/client";
import { useAuth } from "../auth/AuthProvider";
import type { OrganizationType } from "../types/api";

const schema = z.object({
  email: z.string().email(),
  password: z.string().min(8, "Password must have at least 8 characters."),
  firstName: z.string().min(1),
  lastName: z.string().min(1),
  organizationName: z.string().min(1),
  organizationType: z.enum(["FREELANCER", "BUSINESS", "ACCOUNTING_OFFICE"])
});

type FormValues = z.infer<typeof schema>;

export function RegisterPage() {
  const { register: registerAccount } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, formState } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      email: "",
      password: "",
      firstName: "",
      lastName: "",
      organizationName: "",
      organizationType: "BUSINESS"
    }
  });

  async function onSubmit(values: FormValues) {
    setError(null);
    try {
      await registerAccount({ ...values, organizationType: values.organizationType as OrganizationType });
      navigate("/app");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Registration failed.");
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-paper px-4 py-8">
      <div className="w-full max-w-2xl rounded-lg border border-line bg-white p-8 shadow-soft">
        <Link className="text-lg font-bold text-ink" to="/">
          KSeF Helper
        </Link>
        <h1 className="mt-6 text-2xl font-bold text-ink">Create workspace</h1>
        <form className="mt-6 grid gap-4 sm:grid-cols-2" onSubmit={handleSubmit(onSubmit)}>
          <Field label="First name" error={formState.errors.firstName?.message}>
            <input className="focus-ring w-full rounded-md border border-line px-3 py-2" {...register("firstName")} />
          </Field>
          <Field label="Last name" error={formState.errors.lastName?.message}>
            <input className="focus-ring w-full rounded-md border border-line px-3 py-2" {...register("lastName")} />
          </Field>
          <Field label="Email" error={formState.errors.email?.message}>
            <input className="focus-ring w-full rounded-md border border-line px-3 py-2" type="email" {...register("email")} />
          </Field>
          <Field label="Password" error={formState.errors.password?.message}>
            <input className="focus-ring w-full rounded-md border border-line px-3 py-2" type="password" {...register("password")} />
          </Field>
          <Field label="Organization name" error={formState.errors.organizationName?.message}>
            <input className="focus-ring w-full rounded-md border border-line px-3 py-2" {...register("organizationName")} />
          </Field>
          <Field label="Organization type" error={formState.errors.organizationType?.message}>
            <select className="focus-ring w-full rounded-md border border-line px-3 py-2" {...register("organizationType")}>
              <option value="BUSINESS">Business</option>
              <option value="FREELANCER">Freelancer</option>
              <option value="ACCOUNTING_OFFICE">Accounting office</option>
            </select>
          </Field>
          {error ? <p className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700 sm:col-span-2">{error}</p> : null}
          <button className="focus-ring rounded-md bg-emerald-700 px-4 py-2 font-semibold text-white sm:col-span-2" disabled={formState.isSubmitting}>
            {formState.isSubmitting ? "Creating..." : "Create account"}
          </button>
        </form>
        <p className="mt-4 text-sm text-neutral-600">
          Already have an account?{" "}
          <Link className="font-semibold text-emerald-700" to="/login">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
}

function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="text-sm font-semibold text-neutral-700">{label}</span>
      <span className="mt-1 block">{children}</span>
      {error ? <span className="mt-1 block text-sm text-rose-700">{error}</span> : null}
    </label>
  );
}
