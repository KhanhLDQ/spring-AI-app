import { useEffect, useState } from "react";
import { fetchRevenue, type RevenueSummary } from "../api/revenue";
import { useAuth } from "../auth/AuthContext";
import { formatVnd } from "../orders/format";

export function RevenueCard({ range }: { range: { from?: string; to?: string } }) {
  const { session } = useAuth();
  const token = session!.token;
  const [summary, setSummary] = useState<RevenueSummary | null>(null);

  useEffect(() => {
    let live = true;
    fetchRevenue(token, range)
      .then((s) => live && setSummary(s))
      .catch(() => live && setSummary(null));
    return () => {
      live = false;
    };
  }, [token, range.from, range.to]);

  if (!summary) return null;
  const change = summary.previous.changePercent;
  return (
    <div className="card">
      <div className="card-top">
        <b>Revenue</b>
        <span className="muted">
          {summary.from} → {summary.to}
        </span>
      </div>
      <div className="rev-total">{formatVnd(summary.revenueVnd)}</div>
      <dl className="kv">
        <dt>Approved orders</dt>
        <dd>{summary.orders}</dd>
        <dt>Bottles</dt>
        <dd>{summary.bottles}</dd>
        <dt>Previous period</dt>
        <dd>
          {formatVnd(summary.previous.revenueVnd)}
          {change === null ? " · nothing to compare" : ` · ${change >= 0 ? "▲" : "▼"} ${Math.abs(change)}%`}
        </dd>
      </dl>
      {summary.topProducts.length > 0 && (
        <ol className="rev-top">
          {summary.topProducts.map((p) => (
            <li key={`${p.product}${p.size}`}>
              <span>
                {p.product} {p.size}
              </span>
              <span className="num">
                {p.bottles} × · {formatVnd(p.revenueVnd)}
              </span>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
