import { ArrowRight, FileCheck2, ShieldCheck, Upload } from "lucide-react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";

export function LandingPage() {
  const { auth } = useAuth();

  return (
    <div className="min-h-screen bg-paper">
      <header className="border-b border-line bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-4 sm:px-6">
          <div>
            <p className="text-lg font-bold text-ink">KSeF Helper</p>
            <p className="text-xs text-neutral-500">Preparation, validation, and invoice archive helper</p>
          </div>
          <div className="flex items-center gap-2">
            {auth ? (
              <Link className="focus-ring rounded-md bg-emerald-700 px-4 py-2 text-sm font-semibold text-white" to="/app">
                Open app
              </Link>
            ) : (
              <>
                <Link className="focus-ring rounded-md px-4 py-2 text-sm font-semibold text-neutral-700 hover:bg-neutral-100" to="/login">
                  Login
                </Link>
                <Link className="focus-ring rounded-md bg-emerald-700 px-4 py-2 text-sm font-semibold text-white" to="/register">
                  Register
                </Link>
              </>
            )}
          </div>
        </div>
      </header>
      <main>
        <section className="mx-auto grid max-w-6xl gap-10 px-4 py-14 sm:px-6 lg:grid-cols-[1fr_420px] lg:items-center">
          <div>
            <p className="mb-3 inline-flex rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-sm font-semibold text-emerald-800">
              MVP for FA(3)-style XML workflows
            </p>
            <h1 className="max-w-3xl text-4xl font-bold leading-tight text-ink sm:text-5xl">KSeF Helper</h1>
            <p className="mt-5 max-w-2xl text-lg text-neutral-700">
              Upload structured invoice XML, validate it, preview it like a normal invoice, and keep a searchable archive for your organization.
            </p>
            <div className="mt-7 flex flex-wrap gap-3">
              <Link className="focus-ring inline-flex items-center gap-2 rounded-md bg-emerald-700 px-5 py-3 font-semibold text-white" to="/register">
                Start workspace <ArrowRight size={18} />
              </Link>
              <Link className="focus-ring rounded-md border border-line bg-white px-5 py-3 font-semibold text-neutral-700" to="/login">
                I already have an account
              </Link>
            </div>
            <p className="mt-5 max-w-2xl text-sm text-neutral-500">
              KSeF Helper is not an official government tool and does not certify legal or accounting compliance.
            </p>
          </div>
          <div className="rounded-lg border border-line bg-white p-5 shadow-soft">
            <div className="rounded-md bg-paper p-4">
              <p className="text-sm font-semibold text-neutral-500">Latest upload</p>
              <div className="mt-4 space-y-3">
                <div className="flex items-center justify-between rounded-md bg-white p-3">
                  <span className="font-semibold text-ink">FV/2026/001</span>
                  <span className="rounded-full bg-emerald-100 px-2.5 py-1 text-xs font-semibold text-emerald-800">VALID</span>
                </div>
                <div className="grid grid-cols-2 gap-3 text-sm">
                  <div className="rounded-md bg-white p-3">
                    <p className="text-neutral-500">Seller</p>
                    <p className="font-semibold">Example Seller</p>
                  </div>
                  <div className="rounded-md bg-white p-3">
                    <p className="text-neutral-500">Gross</p>
                    <p className="font-semibold">123.00 PLN</p>
                  </div>
                </div>
                <div className="rounded-md bg-white p-3 text-sm text-neutral-700">
                  No blocking errors. Buyer and seller NIP detected.
                </div>
              </div>
            </div>
          </div>
        </section>
        <section className="border-t border-line bg-white">
          <div className="mx-auto grid max-w-6xl gap-4 px-4 py-10 sm:px-6 md:grid-cols-3">
            {[
              { icon: Upload, title: "XML upload", text: "Store original invoice XML and checksum each file." },
              { icon: FileCheck2, title: "Validation", text: "Technical schema checks plus readable business warnings." },
              { icon: ShieldCheck, title: "Organization scope", text: "Each user sees only their own organization’s data." }
            ].map((item) => {
              const Icon = item.icon;
              return (
                <div className="rounded-lg border border-line p-5" key={item.title}>
                  <Icon className="text-emerald-700" size={24} />
                  <p className="mt-4 font-bold text-ink">{item.title}</p>
                  <p className="mt-2 text-sm text-neutral-600">{item.text}</p>
                </div>
              );
            })}
          </div>
        </section>
      </main>
    </div>
  );
}
