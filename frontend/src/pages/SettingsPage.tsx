import { useMutation, useQuery } from "@tanstack/react-query";
import { Download, ShieldCheck, Trash2 } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, ApiError } from "../api/client";
import { useAuth } from "../auth/AuthProvider";
import { hasPermission } from "../auth/permissions";

function saveDownload(filename: string, blob: Blob) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

function eventLabel(value: string) {
  return value
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

export function SettingsPage() {
  const { auth, clearSession } = useAuth();
  const navigate = useNavigate();
  const canManageData = hasPermission(auth?.organization?.role, "manageDataLifecycle");
  const [organizationPassword, setOrganizationPassword] = useState("");
  const [organizationConfirmation, setOrganizationConfirmation] = useState("");
  const [accountPassword, setAccountPassword] = useState("");
  const [accountConfirmation, setAccountConfirmation] = useState("");

  const audit = useQuery({
    queryKey: ["audit-events", auth?.organization?.id],
    queryFn: api.auditEvents,
    enabled: canManageData
  });
  const exportData = useMutation({
    mutationFn: api.exportOrganization,
    onSuccess: ({ filename, blob }) => saveDownload(filename, blob)
  });
  const deleteOrganization = useMutation({
    mutationFn: () => api.deleteOrganization(organizationPassword, organizationConfirmation),
    onSuccess: () => {
      clearSession();
      navigate("/login", { replace: true });
    }
  });
  const deleteAccount = useMutation({
    mutationFn: () => api.deleteAccount(accountPassword, accountConfirmation),
    onSuccess: () => {
      clearSession();
      navigate("/", { replace: true });
    }
  });

  const errorMessage = (error: Error | null) =>
    error instanceof ApiError ? error.message : error?.message;

  return (
    <div className="space-y-6">
      <section className="rounded-lg border border-line bg-white p-5 shadow-soft">
        <h1 className="text-2xl font-bold text-ink">Settings</h1>
        <p className="mt-1 text-sm text-neutral-600">
          Manage exports, audit history, and permanent data deletion.
        </p>
      </section>

      {canManageData ? (
        <>
          <section className="rounded-lg border border-line bg-white p-5 shadow-soft">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div>
                <h2 className="font-bold text-ink">Organization data export</h2>
                <p className="mt-1 text-sm text-neutral-600">
                  Download organization records, validation results, audit history, and original XML files.
                </p>
              </div>
              <button
                className="focus-ring inline-flex items-center gap-2 rounded-md bg-emerald-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
                disabled={exportData.isPending}
                onClick={() => exportData.mutate()}
              >
                <Download size={17} />
                {exportData.isPending ? "Preparing..." : "Download export"}
              </button>
            </div>
            {exportData.error ? <p className="mt-3 text-sm text-rose-700">{errorMessage(exportData.error)}</p> : null}
          </section>

          <section className="rounded-lg border border-line bg-white p-5 shadow-soft">
            <div className="flex items-center gap-2">
              <ShieldCheck size={19} className="text-emerald-700" />
              <h2 className="font-bold text-ink">Audit history</h2>
            </div>
            <div className="mt-4 overflow-x-auto">
              {audit.isLoading ? (
                <p className="text-sm text-neutral-600">Loading audit events...</p>
              ) : audit.data?.length ? (
                <table className="w-full min-w-[640px] text-left text-sm">
                  <thead className="border-b border-line text-neutral-600">
                    <tr>
                      <th className="px-2 py-2 font-semibold">Time</th>
                      <th className="px-2 py-2 font-semibold">Event</th>
                      <th className="px-2 py-2 font-semibold">Actor</th>
                      <th className="px-2 py-2 font-semibold">Target</th>
                    </tr>
                  </thead>
                  <tbody>
                    {audit.data.map((event) => (
                      <tr className="border-b border-line last:border-0" key={event.id}>
                        <td className="px-2 py-3 text-neutral-600">{new Date(event.occurredAt).toLocaleString()}</td>
                        <td className="px-2 py-3 font-semibold text-ink">{eventLabel(event.eventType)}</td>
                        <td className="px-2 py-3 text-neutral-700">{event.actorEmail ?? "System"}</td>
                        <td className="px-2 py-3 text-neutral-600">
                          {event.targetType ?? "data"}{event.targetId ? ` ${event.targetId}` : ""}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              ) : (
                <p className="text-sm text-neutral-600">No audit events have been recorded yet.</p>
              )}
            </div>
          </section>

          <section className="rounded-lg border border-rose-200 bg-white p-5 shadow-soft">
            <h2 className="font-bold text-rose-800">Delete organization</h2>
            <p className="mt-1 text-sm text-neutral-600">
              Permanently deletes the organization, invoices, companies, memberships, and stored files.
            </p>
            <div className="mt-4 grid gap-3 sm:grid-cols-2">
              <label>
                <span className="text-sm font-semibold text-neutral-700">Password</span>
                <input
                  className="focus-ring mt-1 w-full rounded-md border border-line px-3 py-2"
                  type="password"
                  value={organizationPassword}
                  onChange={(event) => setOrganizationPassword(event.target.value)}
                />
              </label>
              <label>
                <span className="text-sm font-semibold text-neutral-700">
                  Type {auth?.organization?.name}
                </span>
                <input
                  className="focus-ring mt-1 w-full rounded-md border border-line px-3 py-2"
                  value={organizationConfirmation}
                  onChange={(event) => setOrganizationConfirmation(event.target.value)}
                />
              </label>
            </div>
            <button
              className="focus-ring mt-4 inline-flex items-center gap-2 rounded-md bg-rose-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
              disabled={deleteOrganization.isPending || !organizationPassword || !organizationConfirmation}
              onClick={() => deleteOrganization.mutate()}
            >
              <Trash2 size={17} />
              {deleteOrganization.isPending ? "Deleting..." : "Delete organization"}
            </button>
            {deleteOrganization.error ? (
              <p className="mt-3 text-sm text-rose-700">{errorMessage(deleteOrganization.error)}</p>
            ) : null}
          </section>
        </>
      ) : null}

      <section className="rounded-lg border border-rose-200 bg-white p-5 shadow-soft">
        <h2 className="font-bold text-rose-800">Delete account</h2>
        <p className="mt-1 text-sm text-neutral-600">
          Removes your user account. Sole owners must transfer ownership or delete shared organizations first.
        </p>
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <label>
            <span className="text-sm font-semibold text-neutral-700">Password</span>
            <input
              className="focus-ring mt-1 w-full rounded-md border border-line px-3 py-2"
              type="password"
              value={accountPassword}
              onChange={(event) => setAccountPassword(event.target.value)}
            />
          </label>
          <label>
            <span className="text-sm font-semibold text-neutral-700">Type DELETE</span>
            <input
              className="focus-ring mt-1 w-full rounded-md border border-line px-3 py-2"
              value={accountConfirmation}
              onChange={(event) => setAccountConfirmation(event.target.value)}
            />
          </label>
        </div>
        <button
          className="focus-ring mt-4 inline-flex items-center gap-2 rounded-md bg-rose-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
          disabled={deleteAccount.isPending || !accountPassword || accountConfirmation !== "DELETE"}
          onClick={() => deleteAccount.mutate()}
        >
          <Trash2 size={17} />
          {deleteAccount.isPending ? "Deleting..." : "Delete account"}
        </button>
        {deleteAccount.error ? <p className="mt-3 text-sm text-rose-700">{errorMessage(deleteAccount.error)}</p> : null}
      </section>
    </div>
  );
}
