import type { Switch, TurnStats } from "../api/chat";
import { formatCost } from "../inspector/format";

const formatDuration = (ms: number) => (ms >= 1000 ? `${(ms / 1000).toFixed(2)} s` : `${Math.round(ms)} ms`);

export function TurnMeta({ stats, off }: { stats: TurnStats; off: Switch[] }) {
  return (
    <div className="meta">
      <span>{formatDuration(stats.ms)}</span>
      <span>
        {stats.tokensIn + stats.tokensOut} tok ({stats.tokensIn} in / {stats.tokensOut} out)
      </span>
      <span>{formatCost(stats.cost)}</span>
      <span className={stats.cache === "HIT" ? "hit" : undefined}>CACHE {stats.cache}</span>
      {off.length > 0 && <span className="off">OFF: {off.join(", ")}</span>}
    </div>
  );
}
