import { useAuth } from "./auth/AuthContext";
import { ChatPage } from "./pages/ChatPage";
import { LoginPage } from "./pages/LoginPage";

export function App() {
  const { session } = useAuth();
  return session ? <ChatPage /> : <LoginPage />;
}
