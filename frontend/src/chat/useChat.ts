import { useCallback, useEffect, useRef, useState } from "react";
import { newConversation, streamChat, UnauthorizedError } from "../api/chat";

export interface Turn {
  id: number;
  question: string;
  answer: string;
  status: "streaming" | "done" | "error";
  error?: string;
}

export function useChat(token: string, onUnauthorized: () => void) {
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [turns, setTurns] = useState<Turn[]>([]);
  const abort = useRef<AbortController | null>(null);
  const nextId = useRef(1);

  const startConversation = useCallback(async () => {
    abort.current?.abort();
    setTurns([]);
    try {
      setConversationId(await newConversation(token));
    } catch (e) {
      if (e instanceof UnauthorizedError) {
        onUnauthorized();
      }
    }
  }, [token, onUnauthorized]);

  useEffect(() => {
    void startConversation();
    return () => {
      abort.current?.abort();
    };
  }, [startConversation]);

  const update = (id: number, change: (turn: Turn) => Turn) =>
    setTurns((all) => all.map((turn) => (turn.id === id ? change(turn) : turn)));

  const send = useCallback(
    async (question: string) => {
      if (!conversationId) {
        return;
      }
      const id = nextId.current++;
      setTurns((all) => [...all, { id, question, answer: "", status: "streaming" }]);
      const controller = new AbortController();
      abort.current = controller;
      try {
        await streamChat(
          token,
          conversationId,
          question,
          {
            onToken: (text) => update(id, (t) => ({ ...t, answer: t.answer + text })),
            onError: (message) => update(id, (t) => ({ ...t, status: "error", error: message })),
          },
          controller.signal,
        );
        update(id, (t) => (t.status === "streaming" ? { ...t, status: "done" } : t));
      } catch (e) {
        if (controller.signal.aborted) {
          return;
        }
        if (e instanceof UnauthorizedError) {
          onUnauthorized();
          return;
        }
        update(id, (t) => ({ ...t, status: "error", error: "Could not reach the server. Please try again." }));
      }
    },
    [conversationId, token, onUnauthorized],
  );

  const busy = turns.some((turn) => turn.status === "streaming");
  return { turns, send, busy, ready: conversationId !== null, newChat: startConversation };
}
