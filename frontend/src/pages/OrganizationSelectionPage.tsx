import { Building2, LogOut } from "lucide-react";
import { useState } from "react";
import { useAuth } from "../auth/AuthProvider";

export function OrganizationSelectionPage() {
  const { auth, switchOrganization, logout } = useAuth();
  const [switchingId, setSwitchingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function select(organizationId: string) {
    setError(null);
    setSwitchingId(organizationId);
    try {
      await switchOrganization(organizationId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Organization could not be selected.");
    } finally {
      setSwitchingId(null);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-paper px-4 py-8">
      <section className="w-full max-w-xl rounded-lg border border-line bg-white p-6 shadow-soft">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold text-ink">Select organization</h1>
            <p className="mt-1 text-sm text-neutral-600">Choose the workspace you want to use.</p>
          </div>
          <button className="focus-ring rounded-md border border-line p-2 text-neutral-700" onClick={logout} title="Sign out">
            <LogOut size={18} />
          </button>
        </div>
        <div className="mt-6 space-y-3">
          {auth?.organizations.map((organization) => (
            <button
              className="focus-ring flex w-full items-center justify-between gap-4 rounded-lg border border-line p-4 text-left hover:bg-emerald-50"
              disabled={switchingId !== null}
              key={organization.id}
              onClick={() => select(organization.id)}
            >
              <span className="flex items-center gap-3">
                <Building2 className="text-emerald-700" size={22} />
                <span>
                  <span className="block font-bold text-ink">{organization.name}</span>
                  <span className="block text-sm text-neutral-600">{organization.role}</span>
                </span>
              </span>
              <span className="text-sm font-semibold text-emerald-700">
                {switchingId === organization.id ? "Opening..." : "Open"}
              </span>
            </button>
          ))}
        </div>
        {error ? <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p> : null}
      </section>
    </div>
  );
}
