import { Fragment, useCallback, useEffect, useRef, useState, type CSSProperties, type FormEvent } from "react";
import { Markdown } from "../chat/Markdown";
import { fetchMyOrders } from "../api/orders";
import { fetchProducts } from "../api/products";
import { usePolling } from "../api/usePolling";
import { useAuth } from "../auth/AuthContext";
import { CacheChip } from "../chat/CacheChip";
import { OrderCard } from "../chat/OrderCard";
import { RevenueCard } from "../chat/RevenueCard";
import { SourceChips } from "../chat/SourceChips";
import { ProductCards } from "../chat/ProductCards";
import { ToolChips } from "../chat/ToolChips";
import { toolCalls } from "../chat/toolSteps";
import { TurnMeta } from "../chat/TurnMeta";
import { useChat } from "../chat/useChat";
import { SWITCH_LABELS, SWITCHES, type Switch } from "../api/chat";
import { Inspector } from "../inspector/Inspector";
import { Resizer, usePanelSize } from "../layout/Resizer";

export function ChatPage({ onOpenOrders }: { onOpenOrders: () => void }) {
  const { session, logout } = useAuth();
  const { turns, send, busy, ready, newChat } = useChat(session!.token, logout);
  const loadMine = useCallback(() => fetchMyOrders(session!.token), [session]);
  const { data: orders } = usePolling(loadMine, logout);
  const loadProducts = useCallback(() => fetchProducts(session!.token), [session]);
  const showsProducts = turns.some((t) => toolCalls(t.steps).some((c) => c.listsProducts || c.productSku));
  const { data: products } = usePolling(loadProducts, logout, showsProducts);
  const [draft, setDraft] = useState("");
  const end = useRef<HTMLDivElement>(null);
  const [selected, setSelected] = useState(0);
  const inspectorWidth = usePanelSize("kefir.size.inspector", 435, 300, 900);
  // The demo starts from a bare model and turns the advisors on one by one, so a new page opens with all off.
  const [off, setOff] = useState<ReadonlySet<Switch>>(() => new Set(SWITCHES));
  const toggle = (name: Switch) =>
    setOff((now) => {
      const next = new Set(now);
      if (next.has(name)) {
        next.delete(name);
      } else {
        next.add(name);
      }
      return next;
    });
  const offList = () => SWITCHES.filter((s) => off.has(s));

  // A new turn pulls the Inspector to it; the user can then step back through earlier turns.
  useEffect(() => {
    setSelected(Math.max(0, turns.length - 1));
  }, [turns.length]);

  const select = useCallback(
    (index: number) => setSelected(Math.min(Math.max(index, 0), Math.max(turns.length - 1, 0))),
    [turns.length],
  );

  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.target instanceof HTMLInputElement) {
        return;
      }
      if (event.key === "ArrowLeft") {
        select(selected - 1);
      } else if (event.key === "ArrowRight") {
        select(selected + 1);
      }
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [selected, select]);

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
    void send(question, offList());
  }

  return (
    <div className="chat" style={{ "--ins-w": `${inspectorWidth.value}px` } as CSSProperties}>
      <div className="convo">
        <header className="convo-head">
          <div>
            <b>Kefir AI</b>
            <small>
              {session!.user.username} · {session!.user.role}
            </small>
          </div>
          <div className="head-actions">
            <div className="switches" role="group" aria-label="Advisors for the next message">
              {SWITCHES.map((name) => (
                <button key={name} type="button" className="sw" aria-pressed={!off.has(name)} onClick={() => toggle(name)}>
                  {SWITCH_LABELS[name]}
                </button>
              ))}
              <button type="button" className="sw all" onClick={() => setOff(off.size === SWITCHES.length ? new Set() : new Set(SWITCHES))}>
                {off.size === SWITCHES.length ? "All on" : "All off"}
              </button>
            </div>
            <button className="btn sm" onClick={() => void newChat()} disabled={busy}>
              New chat
            </button>
          </div>
        </header>

        <div className="msgs" aria-live="polite">
          <div className="msgs-in">
            {turns.length === 0 && <p className="empty">Ask about our kefir flavours, shelf life, delivery or prices.</p>}
            {turns.map((turn, index) => (
              <div
                className="turn"
                key={turn.id}
                aria-selected={index === selected}
                onClick={() => setSelected(index)}
              >
                <div className="u">
                  <div className="u-pill">{turn.question}</div>
                </div>
                <div className="b">
                  <div className="b-av" aria-hidden="true">K</div>
                  <div className="b-main">
                    <CacheChip stats={turn.stats} steps={turn.steps} />
                    <ToolChips calls={toolCalls(turn.steps)} />
                    {turn.answer && <div className="b-text"><Markdown text={turn.answer} /></div>}
                    {turn.status === "done" && <SourceChips steps={turn.steps} />}
                    {turn.status === "streaming" && !turn.answer && (
                      <span className="dots" aria-label="Thinking">
                        <i /><i /><i />
                      </span>
                    )}
                    {toolCalls(turn.steps).map((call, i) => {
                      const order = orders?.find((o) => o.id === call.orderId);
                      const looked = products?.filter((p) => p.sku === call.productSku) ?? [];
                      return (
                        <Fragment key={i}>
                          {order && <OrderCard order={order} onOpenOrders={onOpenOrders} />}
                          {call.revenue && <RevenueCard range={call.revenue} />}
                          {call.listsProducts && products && <ProductCards products={products} />}
                          {looked.length > 0 && <ProductCards products={looked} />}
                        </Fragment>
                      );
                    })}
                    {turn.stats && <TurnMeta stats={turn.stats} off={turn.off} />}
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
      <Resizer size={inspectorWidth} axis="x" direction={-1} label="Inspector width" className="ins-resizer" />
      <Inspector turns={turns} selected={selected} onSelect={select} />
    </div>
  );
}
