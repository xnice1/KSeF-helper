import { Navigate, Route, Routes } from "react-router-dom";
import { AppLayout } from "./components/AppLayout";
import { useAuth } from "./auth/AuthProvider";
import { LandingPage } from "./pages/LandingPage";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";
import { DashboardPage } from "./pages/DashboardPage";
import { CompaniesPage } from "./pages/CompaniesPage";
import { InvoiceArchivePage } from "./pages/InvoiceArchivePage";
import { InvoiceDetailsPage } from "./pages/InvoiceDetailsPage";
import { InvoiceUploadPage } from "./pages/InvoiceUploadPage";
import { ValidationPage } from "./pages/ValidationPage";

function Protected() {
  const { auth, loading } = useAuth();
  if (loading) {
    return <div className="flex min-h-screen items-center justify-center bg-paper text-neutral-700">Loading KSeF Helper...</div>;
  }
  return auth ? <AppLayout /> : <Navigate to="/login" replace />;
}

function Public({ children }: { children: JSX.Element }) {
  const { auth } = useAuth();
  return auth ? <Navigate to="/app" replace /> : children;
}

export function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<Public><LoginPage /></Public>} />
      <Route path="/register" element={<Public><RegisterPage /></Public>} />
      <Route path="/app" element={<Protected />}>
        <Route index element={<DashboardPage />} />
        <Route path="companies" element={<CompaniesPage />} />
        <Route path="upload" element={<InvoiceUploadPage />} />
        <Route path="invoices" element={<InvoiceArchivePage />} />
        <Route path="invoices/:id" element={<InvoiceDetailsPage />} />
        <Route path="validation" element={<ValidationPage />} />
        <Route path="validation/:id" element={<ValidationPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
