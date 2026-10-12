import { useEffect, useRef, useState, type FormEvent } from "react";
import { useAuth } from "../auth/AuthContext";
import { useChat } from "../chat/useChat";

export function ChatPage() {
  const { session, logout } = useAuth();
  const { turns, send, busy, ready, newChat } = useChat(session!.token, logout);
  const [draft, setDraft] = useState("");
  const end = useRef<HTMLDivElement>(null);

  useEffect(() => {
    end.current?.scrollIntoView({ block: "end" });
  }, [turns]);

  function submit(event: FormEvent) {
    event.preventDefault();
    const question = draft.trim();
    if (!question || busy || !ready) {
      return;
    }
    setDraft("");
    void send(question);
  }

  return (
    <div className="convo">
      <header className="convo-head">
        <div>
          <b>Kefir AI</b>
          <small>
            {session!.user.username} · {session!.user.role}
          </small>
        </div>
        <div className="head-actions">
          <button className="btn sm" onClick={() => void newChat()} disabled={busy}>
            New chat
          </button>
          <button className="btn sm" onClick={logout}>
            Log out
          </button>
        </div>
      </header>

      <div className="msgs" aria-live="polite">
        <div className="msgs-in">
          {turns.length === 0 && <p className="empty">Ask about our kefir flavours, shelf life, delivery or prices.</p>}
          {turns.map((turn) => (
            <div className="turn" key={turn.id}>
              <div className="u">
                <div className="u-pill">{turn.question}</div>
              </div>
              <div className="b">
                <div className="b-av" aria-hidden="true">K</div>
                <div className="b-main">
                  {turn.answer && <div className="b-text">{turn.answer}</div>}
                  {turn.status === "streaming" && !turn.answer && (
                    <span className="dots" aria-label="Thinking">
                      <i /><i /><i />
                    </span>
                  )}
                  {turn.status === "error" && (
                    <div className="b-error" role="alert">
                      {turn.error}
                    </div>
                  )}
                </div>
              </div>
            </div>
          ))}
          <div ref={end} />
        </div>
      </div>

      <form className="composer" onSubmit={submit}>
        <div className="composer-in">
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="Ask anything about Mayiu Kefir…"
            maxLength={2000}
            aria-label="Message"
            autoFocus
          />
          <button className="btn primary" type="submit" disabled={busy || !ready || draft.trim() === ""}>
            Send
          </button>
        </div>
      </form>
    </div>
  );
}
