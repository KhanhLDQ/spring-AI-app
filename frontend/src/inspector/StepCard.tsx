import type { RagDetail, StepKind, TraceStep } from "../api/chat";
import { sourceLabel } from "../chat/sources";
import { formatMs } from "./format";
import { HOW_SPRING_AI_DOES_IT } from "./howSpringAiDoesIt";

const ICONS: Record<StepKind, string> = {
  tool: "M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.6 2.6-2.4-.6-.6-2.4z",
  rag: "M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2zM4 19V5",
  memory: "M12 3l9 5-9 5-9-5zM3 13l9 5 9-5",
  cache: "M13 2L4 14h7l-1 8 9-12h-7z",
  cachewrite: "M13 2L4 14h7l-1 8 9-12h-7z",
  model: "M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5L18 18M6 18l2.5-2.5M15.5 8.5L18 6",
};

function StepBody({ step }: { step: TraceStep }) {
  const d = step.detail;
  if (step.kind === "model") {
    return (
      <p>
        <b>{String(d.model ?? "")}</b> · {String(d.tokensIn ?? 0)} in · {String(d.tokensOut ?? 0)} out
        {d.finishReason ? ` · ${String(d.finishReason)}` : ""}
      </p>
    );
  }
  if (step.kind === "tool") {
    return (
      <>
        <p>
          args <b>{String(d.args ?? "")}</b>
        </p>
        <span className="ret">→ {String(d.result ?? "")}</span>
      </>
    );
  }
  if (step.kind === "cache") {
    const score = Number(d.score ?? 0);
    const threshold = Number(d.threshold ?? 0);
    return d.hit ? (
      <>
        <p>
          Similarity <b>{score.toFixed(2)}</b> ≥ threshold <b>{threshold.toFixed(2)}</b>. Answered without calling the model.
        </p>
        <span className="note ok">Matched: {String(d.matched)}</span>
      </>
    ) : (
      <p>
        {d.matched ? (
          <>
            Closest cached question scored <b>{score.toFixed(2)}</b> &lt; threshold <b>{threshold.toFixed(2)}</b>. The chain continues.
          </>
        ) : (
          "Nothing cached for this role is close enough. The chain continues."
        )}
      </p>
    );
  }
  if (step.kind === "cachewrite") {
    return (
      <p>
        {d.stored ? "Stored for the next person who asks." : "Not stored"}
        {!d.stored && d.reason ? `: ${String(d.reason)}.` : ""}
      </p>
    );
  }
  if (step.kind === "memory") {
    const loaded = Number(d.messagesLoaded ?? 0);
    return (
      <p>
        <b>{loaded}</b> message(s) from this conversation added before the question · window <b>{String(d.windowSize ?? "")}</b>
        {loaded === 0 && " · a new conversation starts empty"}
      </p>
    );
  }
  if (step.kind === "rag") {
    return <RagBody detail={d as unknown as RagDetail} />;
  }
  return null;
}

function RagBody({ detail }: { detail: RagDetail }) {
  const { chunks, threshold, topK } = detail;
  return (
    <>
      <p>
        <b>{chunks.length}</b> chunk(s) added to the prompt · top-k <b>{topK}</b> · threshold <b>{threshold.toFixed(2)}</b>
      </p>
      {chunks.length === 0 && (
        <span className="note">No chunk reached the threshold, so the model got no knowledge for this message.</span>
      )}
      {chunks.map((chunk, i) => (
        <div className="chunk" key={i}>
          <q title={chunk.text}>{chunk.text}</q>
          <span className="score">{chunk.score.toFixed(2)}</span>
          <small>{sourceLabel(chunk)}</small>
          <div className="bar">
            <i style={{ width: `${Math.round(Math.min(Math.max(chunk.score, 0), 1) * 100)}%` }} />
          </div>
        </div>
      ))}
    </>
  );
}

export function StepCard({ step, totalMs }: { step: TraceStep; totalMs: number }) {
  const left = totalMs > 0 ? (step.startMs / totalMs) * 100 : 0;
  const width = totalMs > 0 ? (step.durMs / totalMs) * 100 : 0;
  return (
    <section className={`st st-${step.kind}`}>
      <div className="st-h">
        <svg className="ic" viewBox="0 0 24 24" aria-hidden="true">
          <path d={ICONS[step.kind]} />
        </svg>
        <b>{step.title}</b>
        <code className="cx">{step.concept}</code>
      </div>
      <div className="wf">
        <div className="track">
          <i style={{ left: `${left}%`, width: `${width}%` }} />
        </div>
        <span className="ms">{formatMs(step.durMs)}</span>
      </div>
      <div className="sb">
        <StepBody step={step} />
      </div>
      <details className="how">
        <summary>How Spring AI does it</summary>
        <pre className="code">{HOW_SPRING_AI_DOES_IT[step.kind]}</pre>
      </details>
    </section>
  );
}
