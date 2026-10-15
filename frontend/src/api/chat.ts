import { fetchEventSource } from "@microsoft/fetch-event-source";
import { request, UnauthorizedError } from "./http";

export { UnauthorizedError };

export type StepKind = "cache" | "cachewrite" | "memory" | "route" | "rag" | "model" | "tool";

/** Pipeline parts the chat screen can switch off for a message; the backend accepts exactly these names. */
export type Switch = "memory" | "cache" | "rag" | "tools";
/** In the order the demo turns them on: memory, then RAG, then the cache (it only stores RAG answers), then tools. */
export const SWITCHES: Switch[] = ["memory", "rag", "cache", "tools"];

export interface TraceStep {
  kind: StepKind;
  title: string;
  concept: string;
  startMs: number;
  durMs: number;
  detail: Record<string, unknown>;
}

/** One retrieved chunk as the `rag` step reports it; page is null for Markdown sources. */
export interface RagChunk {
  source: string;
  page: number | null;
  score: number;
  text: string;
}

export interface RagDetail {
  chunks: RagChunk[];
  threshold: number;
  topK: number;
}

export interface TurnStats {
  ms: number;
  tokensIn: number;
  tokensOut: number;
  cost: number;
  cache: "HIT" | "MISS" | "OFF";
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
  off: Switch[],
  handlers: StreamHandlers,
  signal: AbortSignal,
): Promise<void> {
  await fetchEventSource("/api/chat/stream", {
    method: "POST",
    headers: jsonHeaders(token),
    body: JSON.stringify({ conversationId, message, off }),
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
