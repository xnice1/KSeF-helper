import { useMutation, useQuery } from "@tanstack/react-query";
import { Crown, Download, LogOut, ShieldCheck, Trash2, UserMinus } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, ApiError } from "../api/client";
import { useAuth } from "../auth/AuthProvider";
import { hasPermission } from "../auth/permissions";
import type { MembershipRole } from "../types/api";

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
  const { auth, clearSession, refreshSession } = useAuth();
  const navigate = useNavigate();
  const canManageData = hasPermission(auth?.organization?.role, "manageDataLifecycle");
  const canViewMembers = hasPermission(auth?.organization?.role, "viewMembers");
  const canManageMembers = hasPermission(auth?.organization?.role, "manageMembers");
  const [organizationPassword, setOrganizationPassword] = useState("");
  const [organizationConfirmation, setOrganizationConfirmation] = useState("");
  const [accountPassword, setAccountPassword] = useState("");
  const [accountConfirmation, setAccountConfirmation] = useState("");

  const audit = useQuery({
    queryKey: ["audit-events", auth?.organization?.id],
    queryFn: api.auditEvents,
    enabled: canManageData
  });
  const accountAudit = useQuery({
    queryKey: ["account-audit-events", auth?.user.id],
    queryFn: api.accountAuditEvents
  });
  const members = useQuery({
    queryKey: ["organization-members", auth?.organization?.id],
    queryFn: api.organizationMembers,
    enabled: canViewMembers
  });
  const changeRole = useMutation({
    mutationFn: ({ membershipId, role }: { membershipId: string; role: MembershipRole }) =>
      api.changeMemberRole(auth!.organization!.id, membershipId, role),
    onSuccess: async () => {
      await members.refetch();
      await refreshSession();
    }
  });
  const removeMember = useMutation({
    mutationFn: (membershipId: string) => api.removeMember(auth!.organization!.id, membershipId),
    onSuccess: () => members.refetch()
  });
  const transferOwnership = useMutation({
    mutationFn: (membershipId: string) => api.transferOwnership(auth!.organization!.id, membershipId),
    onSuccess: async () => {
      await refreshSession();
      navigate("/settings", { replace: true });
    }
  });
  const leaveOrganization = useMutation({
    mutationFn: () => api.leaveOrganization(auth!.organization!.id),
    onSuccess: async () => {
      await refreshSession();
      navigate("/", { replace: true });
    }
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

      <section className="rounded-lg border border-line bg-white p-5 shadow-soft">
        <div className="flex items-center gap-2">
          <ShieldCheck size={19} className="text-emerald-700" />
          <h2 className="font-bold text-ink">Account security history</h2>
        </div>
        <div className="mt-4 overflow-x-auto">
          {accountAudit.isLoading ? (
            <p className="text-sm text-neutral-600">Loading security events...</p>
          ) : accountAudit.data?.length ? (
            <table className="w-full min-w-[560px] text-left text-sm">
              <thead className="border-b border-line text-neutral-600">
                <tr>
                  <th className="px-2 py-2 font-semibold">Time</th>
                  <th className="px-2 py-2 font-semibold">Event</th>
                  <th className="px-2 py-2 font-semibold">Target</th>
                </tr>
              </thead>
              <tbody>
                {accountAudit.data.map((event) => (
                  <tr className="border-b border-line last:border-0" key={event.id}>
                    <td className="px-2 py-3 text-neutral-600">{new Date(event.occurredAt).toLocaleString()}</td>
                    <td className="px-2 py-3 font-semibold text-ink">{eventLabel(event.eventType)}</td>
                    <td className="px-2 py-3 text-neutral-600">
                      {event.targetType ?? "account"}{event.targetId ? ` ${event.targetId}` : ""}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p className="text-sm text-neutral-600">No account security events have been recorded yet.</p>
          )}
        </div>
      </section>

      {canViewMembers ? (
        <section className="rounded-lg border border-line bg-white p-5 shadow-soft">
          <h2 className="font-bold text-ink">Organization members</h2>
          <div className="mt-4 overflow-x-auto">
            {members.isLoading ? (
              <p className="text-sm text-neutral-600">Loading members...</p>
            ) : members.data?.length ? (
              <table className="w-full min-w-[680px] text-left text-sm">
                <thead className="border-b border-line text-neutral-600">
                  <tr>
                    <th className="px-2 py-2 font-semibold">Member</th>
                    <th className="px-2 py-2 font-semibold">Email</th>
                    <th className="px-2 py-2 font-semibold">Role</th>
                    {canManageMembers ? <th className="px-2 py-2 text-right font-semibold">Actions</th> : null}
                  </tr>
                </thead>
                <tbody>
                  {members.data.map((member) => {
                    const isCurrentUser = member.userId === auth?.user.id;
                    return (
                      <tr className="border-b border-line last:border-0" key={member.id}>
                        <td className="px-2 py-3 font-semibold text-ink">
                          {member.fullName}{isCurrentUser ? " (you)" : ""}
                        </td>
                        <td className="px-2 py-3 text-neutral-600">{member.email}</td>
                        <td className="px-2 py-3">
                          {canManageMembers ? (
                            <select
                              aria-label={`Role for ${member.fullName}`}
                              className="focus-ring rounded-md border border-line px-2 py-1.5"
                              disabled={changeRole.isPending}
                              value={member.role}
                              onChange={(event) =>
                                changeRole.mutate({
                                  membershipId: member.id,
                                  role: event.target.value as MembershipRole
                                })
                              }
                            >
                              <option value="OWNER">Owner</option>
                              <option value="ACCOUNTANT">Accountant</option>
                              <option value="EMPLOYEE">Employee</option>
                              <option value="CLIENT">Client</option>
                            </select>
                          ) : (
                            member.role
                          )}
                        </td>
                        {canManageMembers ? (
                          <td className="px-2 py-3">
                            <div className="flex justify-end gap-2">
                              {!isCurrentUser && member.role !== "OWNER" ? (
                                <button
                                  className="focus-ring inline-flex items-center gap-1 rounded-md border border-line px-2.5 py-1.5 font-semibold text-neutral-700"
                                  disabled={transferOwnership.isPending}
                                  onClick={() => {
                                    if (window.confirm(`Transfer ownership to ${member.fullName}? Your role will become Accountant.`)) {
                                      transferOwnership.mutate(member.id);
                                    }
                                  }}
                                >
                                  <Crown size={15} />
                                  Transfer
                                </button>
                              ) : null}
                              {!isCurrentUser ? (
                                <button
                                  aria-label={`Remove ${member.fullName}`}
                                  className="focus-ring inline-flex items-center rounded-md border border-rose-200 p-2 text-rose-700"
                                  disabled={removeMember.isPending}
                                  title="Remove member"
                                  onClick={() => {
                                    if (window.confirm(`Remove ${member.fullName} from this organization?`)) {
                                      removeMember.mutate(member.id);
                                    }
                                  }}
                                >
                                  <UserMinus size={16} />
                                </button>
                              ) : null}
                            </div>
                          </td>
                        ) : null}
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            ) : (
              <p className="text-sm text-neutral-600">No organization members were found.</p>
            )}
          </div>
          {changeRole.error || removeMember.error || transferOwnership.error ? (
            <p className="mt-3 text-sm text-rose-700">
              {errorMessage(changeRole.error ?? removeMember.error ?? transferOwnership.error)}
            </p>
          ) : null}
        </section>
      ) : null}

      <section className="rounded-lg border border-line bg-white p-5 shadow-soft">
        <h2 className="font-bold text-ink">Leave organization</h2>
        <p className="mt-1 text-sm text-neutral-600">
          Removes your membership from {auth?.organization?.name}. The last owner must transfer ownership first.
        </p>
        <button
          className="focus-ring mt-4 inline-flex items-center gap-2 rounded-md border border-line px-4 py-2 text-sm font-semibold text-neutral-700 disabled:opacity-60"
          disabled={leaveOrganization.isPending}
          onClick={() => {
            if (window.confirm(`Leave ${auth?.organization?.name}?`)) {
              leaveOrganization.mutate();
            }
          }}
        >
          <LogOut size={17} />
          {leaveOrganization.isPending ? "Leaving..." : "Leave organization"}
        </button>
        {leaveOrganization.error ? (
          <p className="mt-3 text-sm text-rose-700">{errorMessage(leaveOrganization.error)}</p>
        ) : null}
      </section>

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
