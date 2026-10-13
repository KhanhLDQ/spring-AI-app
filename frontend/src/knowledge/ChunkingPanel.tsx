import { useEffect, useState } from "react";
import { fetchChunking, setChunkSize, type Chunking } from "../api/knowledge";
import { ApiError, UnauthorizedError } from "../api/http";

const SIZES = [50, 100, 200, 400, 800];

interface Props {
  token: string;
  /** Called after the knowledge base was rebuilt, so the page can refresh its table. */
  onRebuilt: () => void;
  onUnauthorized: () => void;
}

/** Smaller chunks are more specific but carry less context; this lets a talk show the effect on retrieval scores. */
export function ChunkingPanel({ token, onRebuilt, onUnauthorized }: Props) {
  const [state, setState] = useState<Chunking | null>(null);
  const [choice, setChoice] = useState<number | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchChunking(token)
      .then((c) => {
        setState(c);
        setChoice(c.chunkTokens);
      })
      .catch((e) => (e instanceof UnauthorizedError ? onUnauthorized() : setError("Could not load the chunk size.")));
  }, [token, onUnauthorized]);

  async function apply() {
    if (choice === null) return;
    setConfirming(false);
    setBusy(true);
    setError(null);
    try {
      setState(await setChunkSize(token, choice));
      onRebuilt();
    } catch (e) {
      if (e instanceof UnauthorizedError) {
        onUnauthorized();
        return;
      }
      setError(e instanceof ApiError ? e.message : "Re-chunking failed. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  const changed = state !== null && choice !== null && choice !== state.chunkTokens;
  return (
    <section className="panel" aria-label="Chunk size">
      <div className="p-head">
        <h2>Chunk size</h2>
        <small className="muted">TokenTextSplitter</small>
      </div>
      <div className="chunking">
        <p>
          {state ? (
            <>
              <b>{state.chunks}</b> chunks from {state.documents} documents at <b>{state.chunkTokens}</b> tokens
            </>
          ) : (
            "Loading…"
          )}
        </p>
        <div className="seg" role="group" aria-label="Chunk size in tokens">
          {SIZES.map((size) => (
            <button
              key={size}
              type="button"
              className={size === choice ? "on" : ""}
              disabled={busy || state === null}
              onClick={() => setChoice(size)}
            >
              {size}
            </button>
          ))}
        </div>
        {confirming ? (
          <span className="confirm">
            Reload the bundled documents and drop uploaded ones?
            <button className="btn sm primary" onClick={() => void apply()}>
              Yes, re-chunk
            </button>
            <button className="btn sm" onClick={() => setConfirming(false)}>
              Cancel
            </button>
          </span>
        ) : (
          <button className="btn sm primary" disabled={!changed || busy} onClick={() => setConfirming(true)}>
            {busy ? "Re-chunking…" : "Re-chunk bundled documents"}
          </button>
        )}
        <span className="muted">
          Changing the size only affects new chunks, so the shop documents are split and embedded again (a few seconds).
          Then run Test retrieval to compare scores.
        </span>
        {error && <p className="form-error" role="alert">{error}</p>}
      </div>
    </section>
  );
}
