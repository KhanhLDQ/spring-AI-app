import { useState } from "react";
import { useAuth } from "./auth/AuthContext";
import { ControlPage } from "./control/ControlPage";
import { KnowledgePage } from "./knowledge/KnowledgePage";
import { AppShell, type View } from "./layout/AppShell";
import { AdminOrdersPage } from "./orders/AdminOrdersPage";
import { MyOrdersPage } from "./orders/MyOrdersPage";
import { ChatPage } from "./pages/ChatPage";
import { LoginPage } from "./pages/LoginPage";

function Workspace() {
  const { session } = useAuth();
  const [view, setView] = useState<View>("chat");
  const isAdmin = session!.user.role === "ADMIN";

  return (
    <AppShell view={view} onNavigate={setView}>
      {/* Chat stays mounted so the conversation survives a trip to another page. */}
      <div className="view" hidden={view !== "chat"}>
        <ChatPage onOpenOrders={() => setView("orders")} />
      </div>
      {view === "orders" && <MyOrdersPage />}
      {view === "admin-orders" && isAdmin && <AdminOrdersPage />}
      {view === "knowledge" && isAdmin && <KnowledgePage />}
      {view === "control" && isAdmin && <ControlPage />}
    </AppShell>
  );
}

export function App() {
  const { session } = useAuth();
  // Keyed by user so a change of account starts from the chat page with fresh state.
  return session ? <Workspace key={session.user.username} /> : <LoginPage />;
}
