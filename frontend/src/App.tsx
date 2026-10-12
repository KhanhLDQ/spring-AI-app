import { useAuth } from "./auth/AuthContext";
import { LoginPage } from "./pages/LoginPage";

export function App() {
  const { session, logout } = useAuth();
  if (!session) {
    return <LoginPage />;
  }
  return (
    <main className="placeholder">
      <p>
        Signed in as <b>{session.user.username}</b> ({session.user.role})
      </p>
      <button className="btn" onClick={logout}>
        Log out
      </button>
    </main>
  );
}
