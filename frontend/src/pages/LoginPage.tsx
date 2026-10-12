import { useState } from "react";
import type { DemoAccount } from "../api/auth";
import { useAuth } from "../auth/AuthContext";

const ACCOUNTS: { id: DemoAccount; label: string; initial: string; description: string }[] = [
  { id: "an", label: "Login as User", initial: "A", description: "Ask questions, place and edit your own orders" },
  { id: "admin", label: "Login as Admin", initial: "Q", description: "Approve orders, ask for revenue, manage knowledge" },
];

export function LoginPage() {
  const { login } = useAuth();
  const [pending, setPending] = useState<DemoAccount | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function signIn(account: DemoAccount) {
    setPending(account);
    setError(null);
    try {
      await login(account);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Login failed");
      setPending(null);
    }
  }

  return (
    <div className="login">
      <section className="login-art">
        <div className="brand">
          <div className="brand-mark" aria-hidden="true">K</div>
          <div className="brand-name">Kefir AI</div>
        </div>
        <div>
          <h1>See what Spring AI does behind every answer.</h1>
          <p>A demo shop assistant with an Inspector that traces each model call, tool call and retrieval step.</p>
        </div>
      </section>
      <section className="login-form" aria-label="Sign in">
        <h2>Choose a demo account</h2>
        {ACCOUNTS.map((account) => (
          <button key={account.id} className="role-btn" disabled={pending !== null} onClick={() => signIn(account.id)}>
            <div className="avatar">{account.initial}</div>
            <div>
              <b>{pending === account.id ? "Signing in…" : account.label}</b>
              <span>{account.description}</span>
            </div>
          </button>
        ))}
        {error && <p className="form-error" role="alert">{error}</p>}
      </section>
    </div>
  );
}
