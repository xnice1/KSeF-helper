import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import { z } from "zod";
import { ApiError } from "../api/client";
import { useAuth } from "../auth/AuthProvider";

const schema = z.object({
  email: z.string().email(),
  password: z.string().min(1)
});

type FormValues = z.infer<typeof schema>;

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, formState } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: "", password: "" }
  });

  async function onSubmit(values: FormValues) {
    setError(null);
    try {
      await login(values);
      navigate("/app");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Login failed.");
    }
  }

  return (
    <AuthShell title="Login" aside="Upload, validate, and archive invoices inside your organization workspace.">
      <form className="space-y-4" onSubmit={handleSubmit(onSubmit)}>
        <Field label="Email" error={formState.errors.email?.message}>
          <input className="focus-ring w-full rounded-md border border-line px-3 py-2" type="email" {...register("email")} />
        </Field>
        <Field label="Password" error={formState.errors.password?.message}>
          <input className="focus-ring w-full rounded-md border border-line px-3 py-2" type="password" {...register("password")} />
        </Field>
        {error ? <p className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p> : null}
        <button className="focus-ring w-full rounded-md bg-emerald-700 px-4 py-2 font-semibold text-white" disabled={formState.isSubmitting}>
          {formState.isSubmitting ? "Signing in..." : "Sign in"}
        </button>
        <p className="text-sm text-neutral-600">
          New here?{" "}
          <Link className="font-semibold text-emerald-700" to="/register">
            Create an account
          </Link>
        </p>
      </form>
    </AuthShell>
  );
}

function AuthShell({ title, aside, children }: { title: string; aside: string; children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-paper px-4 py-8">
      <div className="grid w-full max-w-5xl overflow-hidden rounded-lg border border-line bg-white shadow-soft md:grid-cols-[1fr_1.1fr]">
        <div className="bg-emerald-800 p-8 text-white">
          <Link className="text-lg font-bold" to="/">
            KSeF Helper
          </Link>
          <p className="mt-6 max-w-sm text-2xl font-bold">{aside}</p>
          <p className="mt-4 text-sm text-emerald-50">Not an official KSeF or government service.</p>
        </div>
        <div className="p-8">
          <h1 className="text-2xl font-bold text-ink">{title}</h1>
          <div className="mt-6">{children}</div>
        </div>
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
