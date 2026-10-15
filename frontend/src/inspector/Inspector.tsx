import { SWITCH_LABELS, SWITCHES, type StepKind } from "../api/chat";
import type { Turn } from "../chat/useChat";
import { BehindTheScenes } from "./BehindTheScenes";
import { formatMs } from "./format";
import { StepCard } from "./StepCard";

interface Props {
  turns: Turn[];
  selected: number;
  onSelect: (index: number) => void;
}

/** Cards follow the order the demo introduces each part; the timing bar in each card still shows when it ran. */
const DEMO_ORDER: Record<StepKind, number> = { model: 0, memory: 1, route: 2, rag: 3, cache: 4, cachewrite: 5, tool: 6 };

export function Inspector({ turns, selected, onSelect }: Props) {
  const turn = turns[selected];
  const totalMs = turn ? Math.max(0, ...turn.steps.map((s) => s.startMs + s.durMs)) : 0;
  // Switched-off parts are listed once in a strip instead of one empty card each.
  const steps = turn
    ? turn.steps.filter((s) => !s.detail.off).sort((a, b) => DEMO_ORDER[a.kind] - DEMO_ORDER[b.kind])
    : [];
  const off = turn ? SWITCHES.filter((s) => turn.off.includes(s)) : [];

  return (
    <aside className="ins" aria-label="Spring AI Inspector">
      <div className="ins-head">
        <div>
          <b>Spring AI Inspector</b>
          <small>{turn ? `turn #${selected + 1}` : "no turn selected"}</small>
        </div>
        <div className="pager">
          <button onClick={() => onSelect(selected - 1)} disabled={selected <= 0} aria-label="Previous turn">
            ‹
          </button>
          <span>
            {turns.length ? selected + 1 : 0}/{turns.length}
          </span>
          <button onClick={() => onSelect(selected + 1)} disabled={selected >= turns.length - 1} aria-label="Next turn">
            ›
          </button>
        </div>
      </div>
      <div className="ins-body">
        {!turn ? (
          <div className="ins-empty">Send a message to see what Spring AI does at each step.</div>
        ) : (
          <>
            <div className="sec-h">
              <span>Behind the scenes</span>
              <small>Spring AI ↔ model</small>
            </div>
            <BehindTheScenes steps={turn.steps} stats={turn.stats} streaming={turn.status === "streaming"} />
            {off.length > 0 && (
              <div className="off-strip">
                <span>Switched off</span>
                {off.map((name) => (
                  <i key={name}>{SWITCH_LABELS[name]}</i>
                ))}
              </div>
            )}
            {steps.length > 0 && (
              <>
                <div className="sec-h">
                  <span>Pipeline</span>
                  <small>
                    0 – {formatMs(totalMs)}
                  </small>
                </div>
                {steps.map((step, i) => (
                  <StepCard key={i} step={step} totalMs={totalMs} />
                ))}
              </>
            )}
          </>
        )}
      </div>
    </aside>
  );
}
