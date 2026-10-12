import { fetchEventSource } from "@microsoft/fetch-event-source";
import { request, UnauthorizedError } from "./http";

export { UnauthorizedError };

export type StepKind = "cache" | "cachewrite" | "memory" | "rag" | "model" | "tool";

export interface TraceStep {
  kind: StepKind;
  title: string;
  concept: string;
  startMs: number;
  durMs: number;
  detail: Record<string, unknown>;
}

export interface TurnStats {
  ms: number;
  tokensIn: number;
  tokensOut: number;
  cost: number;
  cache: "HIT" | "MISS";
}

export interface StreamHandlers {
  onToken: (text: string) => void;
  onStep: (step: TraceStep) => void;
  onDone: (stats: TurnStats) => void;
  onError: (message: string) => void;
}

const jsonHeaders = (token: string) => ({
  "Content-Type": "application/json",
  Authorization: `Bearer ${token}`,
});

export async function newConversation(token: string): Promise<string> {
  const { conversationId } = await request<{ conversationId: string }>(token, "/api/chat/new", { method: "POST" });
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
      } else if (event.event === "step") {
        handlers.onStep(JSON.parse(event.data));
      } else if (event.event === "done") {
        handlers.onDone(JSON.parse(event.data));
      } else if (event.event === "error") {
        handlers.onError(JSON.parse(event.data).message);
      }
    },
    onerror(error) {
      throw error;
    },
  });
}
