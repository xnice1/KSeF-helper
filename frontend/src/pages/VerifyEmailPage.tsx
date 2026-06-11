import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api, ApiError, tokenStore } from "../api/client";
import { AccountPage } from "./ForgotPasswordPage";

export function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const [error, setError] = useState<string | null>(null);
  const token = searchParams.get("token") ?? "";

  useEffect(() => {
    if (!token) {
      setError("Verification token is missing.");
      return;
    }
    api.verifyEmail(token)
      .then((response) => {
        tokenStore.set(response.token);
        window.location.assign("/app");
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Verification failed."));
  }, [token]);

  return (
    <AccountPage title="Verify email">
      <p className="text-neutral-700">{error ?? "Verifying your email..."}</p>
    </AccountPage>
  );
}
