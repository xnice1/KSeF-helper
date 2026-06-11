import { FormEvent, useState } from "react";
import { Link } from "react-router-dom";
import { api, ApiError } from "../api/client";

export function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      const response = await api.forgotPassword(email);
      setMessage(response.message);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Request failed.");
    }
  }

  return (
    <AccountPage title="Reset password">
      <form className="space-y-4" onSubmit={submit}>
        <label className="block">
          <span className="text-sm font-semibold text-neutral-700">Email</span>
          <input className="focus-ring mt-1 w-full rounded-md border border-line px-3 py-2" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
        </label>
        {message ? <p className="rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-800">{message}</p> : null}
        {error ? <p className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p> : null}
        <button className="focus-ring w-full rounded-md bg-emerald-700 px-4 py-2 font-semibold text-white">Send reset link</button>
      </form>
    </AccountPage>
  );
}

export function AccountPage({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-paper px-4 py-8">
      <div className="w-full max-w-md rounded-lg border border-line bg-white p-8 shadow-soft">
        <Link className="text-lg font-bold text-ink" to="/">KSeF Helper</Link>
        <h1 className="mt-6 text-2xl font-bold text-ink">{title}</h1>
        <div className="mt-6">{children}</div>
        <Link className="mt-5 inline-block text-sm font-semibold text-emerald-700" to="/login">Back to login</Link>
      </div>
    </div>
  );
}
