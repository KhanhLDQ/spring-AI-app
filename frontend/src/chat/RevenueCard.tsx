import { useEffect, useState } from "react";
import { ApiError } from "../api/http";
import { fetchRevenue, generateBrief, type RevenueBriefResult, type RevenueSummary } from "../api/revenue";
import { useAuth } from "../auth/AuthContext";
import { formatVnd } from "../orders/format";

export function RevenueCard({ range }: { range: { from?: string; to?: string } }) {
  const { session } = useAuth();
  const token = session!.token;
  const [summary, setSummary] = useState<RevenueSummary | null>(null);
  const [brief, setBrief] = useState<RevenueBriefResult | null>(null);
  const [briefing, setBriefing] = useState(false);
  const [briefError, setBriefError] = useState<string | null>(null);

  async function writeBrief() {
    setBriefing(true);
    setBriefError(null);
    try {
      setBrief(await generateBrief(token, { from: summary?.from, to: summary?.to }));
    } catch (e) {
      setBriefError(e instanceof ApiError ? e.message : "The brief could not be written. Please try again.");
    } finally {
      setBriefing(false);
    }
  }

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
      {!brief && (
        <button className="btn sm" disabled={briefing} onClick={() => void writeBrief()}>
          {briefing ? "Writing brief…" : "Generate brief"}
        </button>
      )}
      {briefError && <span className="form-error" role="alert">{briefError}</span>}
      {brief && <BriefSection result={brief} />}
    </div>
  );
}

function BriefSection({ result }: { result: RevenueBriefResult }) {
  const { brief } = result;
  return (
    <div className="brief">
      <b className="brief-head">{brief.headline}</b>
      <ul>
        {brief.highlights.map((h) => (
          <li key={h}>{h}</li>
        ))}
      </ul>
      <p className="brief-tip">{brief.suggestion}</p>
      <div className="meta">
        <span>{result.ms} ms</span>
        <span>
          {result.tokensIn + result.tokensOut} tok ({result.tokensIn} in / {result.tokensOut} out)
        </span>
        <span>${result.cost.toFixed(4)}</span>
      </div>
      <details>
        <summary>How Spring AI did it</summary>
        <p className="muted">
          <code>.responseEntity(RevenueBrief.class)</code> added this schema to the prompt, then parsed the model's reply into the record.
        </p>
        <pre>{result.schema}</pre>
        <pre>{result.raw}</pre>
      </details>
    </div>
  );
}
