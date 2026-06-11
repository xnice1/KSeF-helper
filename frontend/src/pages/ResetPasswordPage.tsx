import { FormEvent, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api, ApiError } from "../api/client";
import { AccountPage } from "./ForgotPasswordPage";

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const token = searchParams.get("token") ?? "";

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      const response = await api.resetPassword(token, password);
      setMessage(response.message);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Password reset failed.");
    }
  }

  return (
    <AccountPage title="Choose a new password">
      <form className="space-y-4" onSubmit={submit}>
        <label className="block">
          <span className="text-sm font-semibold text-neutral-700">New password</span>
          <input className="focus-ring mt-1 w-full rounded-md border border-line px-3 py-2" type="password" minLength={8} maxLength={128} value={password} onChange={(event) => setPassword(event.target.value)} required />
        </label>
        {message ? <p className="rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-800">{message}</p> : null}
        {error ? <p className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p> : null}
        <button className="focus-ring w-full rounded-md bg-emerald-700 px-4 py-2 font-semibold text-white" disabled={!token}>Update password</button>
      </form>
    </AccountPage>
  );
}
