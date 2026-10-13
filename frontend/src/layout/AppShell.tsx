import type { ReactNode } from "react";
import type { DemoAccount } from "../api/auth";
import { useAuth } from "../auth/AuthContext";

export type View = "chat" | "orders" | "admin-orders" | "knowledge" | "control";

const ICONS = {
  chat: "M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z",
  orders: "M6 3h12l2 5H4zM4 8v12h16V8M9 12h6",
  approve: "M9 11l3 3 8-8M20 12v7a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1h9",
  control: "M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0M14 4v4M8 10v4M16 16v4",
  knowledge: "M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2zM4 19V5M9 8h6",
};

interface Props {
  view: View;
  onNavigate: (view: View) => void;
  children: ReactNode;
}

function NavItem({ icon, label, active, onClick }: { icon: string; label: string; active: boolean; onClick: () => void }) {
  return (
    <button className="nav-item" aria-current={active ? "page" : undefined} onClick={onClick}>
      <svg className="ic" viewBox="0 0 24 24" aria-hidden="true">
        <path d={icon} />
      </svg>
      {label}
    </button>
  );
}

export function AppShell({ view, onNavigate, children }: Props) {
  const { session, login, logout } = useAuth();
  const user = session!.user;
  const isAdmin = user.role === "ADMIN";

  // Switching account is a fresh login, so the demo can jump between a customer and an admin in one click.
  const switchTo = (account: DemoAccount) => {
    if (account !== user.username) {
      void login(account);
    }
  };

  return (
    <div className="shell">
      <aside className="side" aria-label="Navigation">
        <div className="brand">
          <div className="brand-mark" aria-hidden="true">K</div>
          <div>
            <div className="brand-name">Kefir AI</div>
            <div className="brand-sub">Spring AI demo</div>
          </div>
        </div>
        <div className="who">
          <div className="who-row">
            <div className="avatar">{user.username[0].toUpperCase()}</div>
            <div>
              <div className="who-name">{user.username}</div>
              <div className="who-role">{isAdmin ? "Administrator" : "Customer"}</div>
            </div>
          </div>
          <div className="seg" role="group" aria-label="Switch account">
            <button aria-pressed={!isAdmin} onClick={() => switchTo("an")}>User</button>
            <button aria-pressed={isAdmin} onClick={() => switchTo("admin")}>Admin</button>
          </div>
        </div>
        <nav className="nav-group" aria-label="Customer">
          <div className="nav-label">Customer</div>
          <NavItem icon={ICONS.chat} label="Chat" active={view === "chat"} onClick={() => onNavigate("chat")} />
          <NavItem icon={ICONS.orders} label="My orders" active={view === "orders"} onClick={() => onNavigate("orders")} />
        </nav>
        {isAdmin && (
          <nav className="nav-group" aria-label="Administration">
            <div className="nav-label">Administration</div>
            <NavItem
              icon={ICONS.approve}
              label="Approve orders"
              active={view === "admin-orders"}
              onClick={() => onNavigate("admin-orders")}
            />
            <NavItem
              icon={ICONS.knowledge}
              label="Knowledge"
              active={view === "knowledge"}
              onClick={() => onNavigate("knowledge")}
            />
            <NavItem
              icon={ICONS.control}
              label="Control panel"
              active={view === "control"}
              onClick={() => onNavigate("control")}
            />
          </nav>
        )}
        <div className="side-foot">
          <button className="link-btn" onClick={logout}>Log out</button>
        </div>
      </aside>
      <main className="main">{children}</main>
    </div>
  );
}
