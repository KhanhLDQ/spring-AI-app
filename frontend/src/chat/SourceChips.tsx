import type { TraceStep } from "../api/chat";
import { citedSources } from "./sources";

/** The knowledge the model was given for this turn, so an answer can be traced back to a document and page. */
export function SourceChips({ steps }: { steps: TraceStep[] }) {
  const sources = citedSources(steps);
  if (sources.length === 0) {
    return null;
  }
  return (
    <div className="chips" aria-label="Sources">
      {sources.map((source, i) => (
        <span key={source.label} className="src" title={source.text}>
          <b>{i + 1}</b>
          {source.label}
        </span>
      ))}
    </div>
  );
}
