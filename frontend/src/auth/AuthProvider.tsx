import { createContext, ReactNode, useContext, useEffect, useMemo, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api, LoginPayload, RegisterPayload, tokenStore } from "../api/client";
import type { AuthResponse } from "../types/api";

type AuthContextValue = {
  auth: AuthResponse | null;
  loading: boolean;
  login: (payload: LoginPayload) => Promise<void>;
  register: (payload: RegisterPayload) => Promise<AuthResponse>;
  switchOrganization: (organizationId: string) => Promise<void>;
  logout: () => Promise<void>;
  clearSession: () => void;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [auth, setAuth] = useState<AuthResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api
      .refresh()
      .then((response) => {
        if (response.token) {
          tokenStore.set(response.token);
          setAuth(response);
        }
      })
      .catch(() => {
        tokenStore.clear();
        queryClient.clear();
      })
      .finally(() => setLoading(false));
  }, [queryClient]);

  const value = useMemo<AuthContextValue>(
    () => ({
      auth,
      loading,
      login: async (payload) => {
        queryClient.clear();
        const response = await api.login(payload);
        if (!response.token) {
          throw new Error("Login did not return an access token.");
        }
        tokenStore.set(response.token);
        setAuth(response);
      },
      register: async (payload) => {
        queryClient.clear();
        const response = await api.register(payload);
        if (response.token) {
          tokenStore.set(response.token);
          setAuth(response);
        }
        return response;
      },
      switchOrganization: async (organizationId) => {
        const response = await api.switchOrganization(organizationId);
        if (!response.token) {
          throw new Error("Organization switch did not return an access token.");
        }
        tokenStore.set(response.token);
        queryClient.clear();
        setAuth(response);
      },
      logout: async () => {
        await api.logout().catch(() => undefined);
        tokenStore.clear();
        setAuth(null);
        queryClient.clear();
      },
      clearSession: () => {
        tokenStore.clear();
        setAuth(null);
        queryClient.clear();
      }
    }),
    [auth, loading, queryClient]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider.");
  }
  return context;
}
