import { useCallback, useEffect, useState } from "react";
import { fetchConfig, runDemoAction, setThresholds, type DemoAction } from "../api/demo";
import { ApiError, UnauthorizedError } from "../api/http";
import { usePolling } from "../api/usePolling";
import { useAuth } from "../auth/AuthContext";

interface ActionSpec {
  action: DemoAction;
  label: string;
  hint: string;
  warning: string;
}

const ACTIONS: ActionSpec[] = [
  { action: "reset", label: "Reset everything", hint: "Orders, stock, knowledge, cache, memory and thresholds back to the start of the demo.", warning: "Reset the whole demo?" },
  { action: "reseed", label: "Reseed orders", hint: "Six sample orders and full shelves. Chat memory and knowledge stay as they are.", warning: "Replace every order with the samples?" },
  { action: "clear-cache", label: "Clear semantic cache", hint: "The next question goes to the model again.", warning: "Delete every cached answer?" },
  { action: "clear-vectors", label: "Clear vector store", hint: "Removes all documents and chunks (and the cache built on them).", warning: "Delete all knowledge?" },
  { action: "clear-memory", label: "Clear chat memory", hint: "Every conversation forgets what was said.", warning: "Delete all conversation memory?" },
];

function Slider({ label, hint, value, onChange, onCommit }: { label: string; hint: string; value: number; onChange: (v: number) => void; onCommit: (v: number) => void }) {
  return (
    <div className="slider">
      <div className="slider-top">
        <b>{label}</b>
        <span className="num">{value.toFixed(2)}</span>
      </div>
      <input
        type="range"
        min={0}
        max={1}
        step={0.01}
        value={value}
        aria-label={label}
        onChange={(e) => onChange(Number(e.target.value))}
        onPointerUp={(e) => onCommit(Number(e.currentTarget.value))}
        onKeyUp={(e) => onCommit(Number(e.currentTarget.value))}
      />
      <span className="muted">{hint}</span>
    </div>
  );
}

export function ControlPage() {
  const { session, logout } = useAuth();
  const token = session!.token;
  const load = useCallback(() => fetchConfig(token), [token]);
  const { data: config, error, reload } = usePolling(load, logout);
  const [cache, setCache] = useState<number | null>(null);
  const [rag, setRag] = useState<number | null>(null);
  const [pending, setPending] = useState<DemoAction | null>(null);
  const [done, setDone] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  // Polling keeps the budget live; the sliders are seeded once so a refresh never moves a handle under the cursor.
  useEffect(() => {
    if (config && cache === null) {
      setCache(config.thresholds.cache);
      setRag(config.thresholds.rag);
    }
  }, [config, cache]);

  async function run(task: () => Promise<void>, message: string) {
    setActionError(null);
    setDone(null);
    try {
      await task();
      setDone(message);
    } catch (e) {
      if (e instanceof UnauthorizedError) {
        logout();
        return;
      }
      setActionError(e instanceof ApiError ? e.message : "The action failed. Please try again.");
    }
    reload();
  }

  async function confirm(spec: ActionSpec) {
    setPending(null);
    await run(async () => {
      await runDemoAction(token, spec.action);
      if (spec.action === "reset") {
        const fresh = await fetchConfig(token);
        setCache(fresh.thresholds.cache);
        setRag(fresh.thresholds.rag);
      }
    }, `${spec.label}: done.`);
  }

  const commit = (key: "cache" | "rag") => (value: number) =>
    run(async () => void (await setThresholds(token, { [key]: value })), `${key === "cache" ? "Cache" : "RAG"} threshold set to ${value.toFixed(2)}.`);

  const used = config?.budget.usedUsd ?? 0;
  const limit = config?.budget.limitUsd ?? 0;
  const usedPct = limit > 0 ? Math.min(100, (used / limit) * 100) : 0;

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>Control panel</h1>
          <p>Put the demo back in a known state, and move the similarity thresholds live while the audience watches.</p>
        </div>
      </div>
      {(error || actionError) && <p className="form-error" role="alert">{actionError ?? error}</p>}
      {done && <p className="notice" role="status">{done}</p>}

      <div className="two">
        <section className="panel" aria-label="Thresholds">
          <div className="p-head">
            <h2>Similarity thresholds</h2>
          </div>
          <div className="ctl-body">
            {cache !== null && rag !== null ? (
              <>
                <Slider label="Semantic cache" value={cache} onChange={setCache} onCommit={commit("cache")}
                  hint="Above this score a new question counts as already answered. Too low and a different question gets the wrong cached answer." />
                <Slider label="RAG retrieval" value={rag} onChange={setRag} onCommit={commit("rag")}
                  hint="Chunks scoring below this are not given to the model. Too high and the bot says it does not know." />
              </>
            ) : (
              <p className="muted">Loading…</p>
            )}
          </div>
        </section>

        <section className="panel" aria-label="Runtime">
          <div className="p-head">
            <h2>Runtime</h2>
          </div>
          <dl className="ctl-body kv">
            <dt>Model</dt>
            <dd className="mono">{config?.model ?? "…"}</dd>
            <dt>Profile</dt>
            <dd className="mono">{config?.profile ?? "…"}</dd>
            <dt>Price per 1M tokens</dt>
            <dd className="num">{config ? `$${config.pricing.inPerMTok} in · $${config.pricing.outPerMTok} out` : "…"}</dd>
            <dt>Spent</dt>
            <dd>
              <span className="num">${used.toFixed(4)}</span> <span className="muted">of ${limit.toFixed(2)}</span>
              <div className="bar" role="progressbar" aria-valuemin={0} aria-valuemax={limit} aria-valuenow={used} aria-label="Budget used">
                <i style={{ width: `${usedPct}%` }} />
              </div>
            </dd>
          </dl>
        </section>
      </div>

      <section className="panel" aria-label="Reset and clear">
        <div className="p-head">
          <h2>Reset and clear</h2>
        </div>
        <ul className="actions">
          {ACTIONS.map((spec) => (
            <li key={spec.action}>
              <div>
                <b>{spec.label}</b>
                <br />
                <span className="muted">{spec.hint}</span>
              </div>
              {pending === spec.action ? (
                <span className="confirm">
                  {spec.warning}
                  <button className="btn sm danger" onClick={() => void confirm(spec)}>Yes</button>
                  <button className="btn sm" onClick={() => setPending(null)}>Cancel</button>
                </span>
              ) : (
                <button className={`btn sm${spec.action === "reset" ? " primary" : ""}`} onClick={() => setPending(spec.action)}>
                  {spec.label}
                </button>
              )}
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
