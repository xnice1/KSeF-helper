import { FormEvent, useState } from "react";
import { api, ApiError } from "../api/client";
import { AccountPage } from "./ForgotPasswordPage";

export function RequestVerificationPage() {
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      const response = await api.requestVerification(email);
      setMessage(response.message);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Request failed.");
    }
  }

  return (
    <AccountPage title="Resend verification email">
      <form className="space-y-4" onSubmit={submit}>
        <label className="block">
          <span className="text-sm font-semibold text-neutral-700">Email</span>
          <input className="focus-ring mt-1 w-full rounded-md border border-line px-3 py-2" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
        </label>
        {message ? <p className="rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-800">{message}</p> : null}
        {error ? <p className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p> : null}
        <button className="focus-ring w-full rounded-md bg-emerald-700 px-4 py-2 font-semibold text-white">Send verification link</button>
      </form>
    </AccountPage>
  );
}
