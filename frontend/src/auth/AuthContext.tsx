import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import { quickLogin, type DemoAccount, type Session } from "../api/auth";

interface AuthState {
  session: Session | null;
  login: (account: DemoAccount) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

// The token lives in memory only: a refresh or expiry sends the user back to the login screen.
export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);

  const login = useCallback(async (account: DemoAccount) => {
    setSession(await quickLogin(account));
  }, []);
  const logout = useCallback(() => setSession(null), []);

  const value = useMemo(() => ({ session, login, logout }), [session, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return context;
}
