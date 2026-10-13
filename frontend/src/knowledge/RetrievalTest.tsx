import { useState, type FormEvent } from "react";
import { searchKnowledge, type SearchHit } from "../api/knowledge";
import { ApiError, UnauthorizedError } from "../api/http";
import { sourceLabel } from "../chat/sources";

interface Props {
  token: string;
  onUnauthorized: () => void;
}

/** Runs the similarity search alone, so a lecture can show what retrieval returns before any model is involved. */
export function RetrievalTest({ token, onUnauthorized }: Props) {
  const [query, setQuery] = useState("How long does honey kefir keep?");
  const [hits, setHits] = useState<SearchHit[] | null>(null);
  const [tookMs, setTookMs] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function search(event: FormEvent) {
    event.preventDefault();
    if (!query.trim() || busy) {
      return;
    }
    setBusy(true);
    setError(null);
    const started = performance.now();
    try {
      setHits(await searchKnowledge(token, query.trim()));
      setTookMs(performance.now() - started);
    } catch (e) {
      if (e instanceof UnauthorizedError) {
        onUnauthorized();
        return;
      }
      setError(e instanceof ApiError ? e.message : "The search failed. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel" aria-label="Test retrieval">
      <div className="p-head">
        <h2>Test retrieval</h2>
        <small className="muted">similaritySearch, no LLM call</small>
      </div>
      <form className="search" onSubmit={search}>
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          maxLength={500}
          aria-label="Search query"
        />
        <button className="btn primary" type="submit" disabled={busy || !query.trim()}>
          Search
        </button>
      </form>
      {error && <p className="form-error pad" role="alert">{error}</p>}
      {hits && (
        <div className="results">
          <small className="muted">
            Top {hits.length} of the knowledge base, {Math.round(tookMs)} ms, no similarity threshold applied.
          </small>
          {hits.length === 0 && <div className="empty">The knowledge base is empty.</div>}
          {hits.map((hit, i) => (
            <div className="chunk" key={i}>
              <q>{hit.text}</q>
              <span className="score">{hit.score.toFixed(2)}</span>
              <small>{sourceLabel(hit)}</small>
              <div className="bar">
                <i style={{ width: `${Math.round(Math.min(Math.max(hit.score, 0), 1) * 100)}%` }} />
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
