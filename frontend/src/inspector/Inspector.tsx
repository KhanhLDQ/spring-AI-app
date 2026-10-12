import type { Turn } from "../chat/useChat";
import { formatCost, formatMs } from "./format";
import { StepCard } from "./StepCard";

interface Props {
  turns: Turn[];
  selected: number;
  onSelect: (index: number) => void;
}

export function Inspector({ turns, selected, onSelect }: Props) {
  const turn = turns[selected];
  const stats = turn?.stats;
  const totalMs = turn ? Math.max(0, ...turn.steps.map((s) => s.startMs + s.durMs)) : 0;
  const tokens = stats ? stats.tokensIn + stats.tokensOut : 0;
  const placeholder = turn?.status === "streaming" ? "…" : "—";

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
            <div className="tiles">
              <div className="tile">
                <span>Latency</span>
                <b>{stats ? formatMs(stats.ms) : placeholder}</b>
              </div>
              <div className="tile">
                <span>Tokens</span>
                <b>{stats ? tokens : placeholder}</b>
                {stats && tokens > 0 ? (
                  <>
                    <div className="tbar">
                      <i style={{ width: `${(stats.tokensIn / tokens) * 100}%`, background: "var(--k-rag)" }} />
                      <i style={{ flex: 1, background: "var(--k-model)" }} />
                    </div>
                    <small>
                      {stats.tokensIn} in · {stats.tokensOut} out
                    </small>
                  </>
                ) : (
                  stats && <small>no model call</small>
                )}
              </div>
              <div className="tile">
                <span>Cost</span>
                <b>{stats ? formatCost(stats.cost) : placeholder}</b>
              </div>
              <div className={`tile${stats?.cache === "HIT" ? " hit" : ""}`}>
                <span>Cache</span>
                <b>{stats ? stats.cache : placeholder}</b>
              </div>
            </div>
            {turn.steps.length > 0 && (
              <>
                <div className="axis">
                  <span>0</span>
                  <span>{formatMs(totalMs / 2)}</span>
                  <span>{formatMs(totalMs)}</span>
                </div>
                {turn.steps.map((step, i) => (
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
