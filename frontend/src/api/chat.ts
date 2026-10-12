import { fetchEventSource } from "@microsoft/fetch-event-source";

export class UnauthorizedError extends Error {}

export interface StreamHandlers {
  onToken: (text: string) => void;
  onError: (message: string) => void;
}

const jsonHeaders = (token: string) => ({
  "Content-Type": "application/json",
  Authorization: `Bearer ${token}`,
});

export async function newConversation(token: string): Promise<string> {
  const response = await fetch("/api/chat/new", { method: "POST", headers: jsonHeaders(token) });
  if (response.status === 401) {
    throw new UnauthorizedError();
  }
  if (!response.ok) {
    throw new Error(`Could not start a conversation (${response.status})`);
  }
  const { conversationId } = await response.json();
  return conversationId;
}

/** Resolves when the stream ends; the library retries on errors by default, so every failure is rethrown. */
export async function streamChat(
  token: string,
  conversationId: string,
  message: string,
  handlers: StreamHandlers,
  signal: AbortSignal,
): Promise<void> {
  await fetchEventSource("/api/chat/stream", {
    method: "POST",
    headers: jsonHeaders(token),
    body: JSON.stringify({ conversationId, message }),
    signal,
    openWhenHidden: true,
    async onopen(response) {
      if (response.status === 401) {
        throw new UnauthorizedError();
      }
      if (!response.ok) {
        throw new Error(`Chat request failed (${response.status})`);
      }
    },
    onmessage(event) {
      if (event.event === "token") {
        handlers.onToken(JSON.parse(event.data).text);
      } else if (event.event === "error") {
        handlers.onError(JSON.parse(event.data).message);
      }
    },
    onerror(error) {
      throw error;
    },
  });
}
