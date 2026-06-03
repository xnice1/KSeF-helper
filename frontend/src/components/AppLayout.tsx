import { Building2, FileCheck2, FileText, LayoutDashboard, LogOut, Upload } from "lucide-react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";

const links = [
  { to: "/app", label: "Dashboard", icon: LayoutDashboard },
  { to: "/app/upload", label: "Upload", icon: Upload },
  { to: "/app/invoices", label: "Archive", icon: FileText },
  { to: "/app/validation", label: "Validation", icon: FileCheck2 },
  { to: "/app/companies", label: "Companies", icon: Building2 }
];

export function AppLayout() {
  const { auth, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-paper">
      <header className="border-b border-line bg-white">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-4 px-4 py-4 sm:px-6">
          <div>
            <p className="text-lg font-bold text-ink">KSeF Helper</p>
            <p className="text-sm text-neutral-600">{auth?.organization.name}</p>
          </div>
          <button
            className="focus-ring inline-flex items-center gap-2 rounded-md border border-line px-3 py-2 text-sm font-medium text-neutral-700 hover:bg-neutral-50"
            onClick={() => {
              logout();
              navigate("/");
            }}
          >
            <LogOut size={16} />
            Sign out
          </button>
        </div>
      </header>
      <div className="mx-auto grid max-w-7xl gap-6 px-4 py-6 sm:px-6 lg:grid-cols-[220px_1fr]">
        <nav className="flex gap-2 overflow-x-auto lg:flex-col lg:overflow-visible">
          {links.map((link) => {
            const Icon = link.icon;
            return (
              <NavLink
                key={link.to}
                to={link.to}
                end={link.to === "/app"}
                className={({ isActive }) =>
                  `focus-ring inline-flex min-w-max items-center gap-2 rounded-md px-3 py-2 text-sm font-semibold ${
                    isActive ? "bg-emerald-700 text-white" : "text-neutral-700 hover:bg-white"
                  }`
                }
              >
                <Icon size={17} />
                {link.label}
              </NavLink>
            );
          })}
        </nav>
        <main>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
