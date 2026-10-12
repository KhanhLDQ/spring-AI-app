import type { TurnStats } from "../api/chat";
import { formatCost } from "../inspector/format";

const formatDuration = (ms: number) => (ms >= 1000 ? `${(ms / 1000).toFixed(2)} s` : `${Math.round(ms)} ms`);

export function TurnMeta({ stats }: { stats: TurnStats }) {
  return (
    <div className="meta">
      <span>{formatDuration(stats.ms)}</span>
      <span>
        {stats.tokensIn + stats.tokensOut} tok ({stats.tokensIn} in / {stats.tokensOut} out)
      </span>
      <span>{formatCost(stats.cost)}</span>
      <span className={stats.cache === "HIT" ? "hit" : undefined}>CACHE {stats.cache}</span>
    </div>
  );
}
