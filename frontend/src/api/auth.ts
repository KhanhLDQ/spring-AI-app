export type Role = "USER" | "ADMIN";

export interface Session {
  token: string;
  user: { username: string; role: Role };
}

interface LoginResponse extends Session {
  expiresIn: number;
}

export type DemoAccount = "an" | "admin";

export async function quickLogin(username: DemoAccount): Promise<Session> {
  const response = await fetch("/api/auth/quick-login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username }),
  });
  if (!response.ok) {
    throw new Error(`Login failed (${response.status})`);
  }
  const { token, user }: LoginResponse = await response.json();
  return { token, user };
}
