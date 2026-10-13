import type { TraceStep, TurnStats } from "../api/chat";

/** Shown instead of tool chips when the whole answer came from the semantic cache. */
export function CacheChip({ stats, steps }: { stats?: TurnStats; steps: TraceStep[] }) {
  if (stats?.cache !== "HIT") {
    return null;
  }
  const score = Number(steps.find((step) => step.kind === "cache")?.detail.score ?? 0);
  return (
    <div className="chips">
      <span className="tchip cache">
        <svg className="ic" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M13 2L4 14h7l-1 8 9-12h-7z" />
        </svg>
        Answered from cache · {score.toFixed(2)}
      </span>
    </div>
  );
}
