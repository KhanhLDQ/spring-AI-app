import type { IngestResult } from "../api/knowledge";
import { formatMs } from "../inspector/format";

const STAGES = [
  { key: "readMs", label: "Read", concept: "DocumentReader" },
  { key: "splitMs", label: "Split", concept: "TokenTextSplitter" },
  { key: "embedMs", label: "Embed", concept: "EmbeddingModel" },
  { key: "storeMs", label: "Store", concept: "VectorStore" },
] as const;

/** The ingestion pipeline of the last upload, one box per stage with its share of the total time. */
export function EtlPipeline({ result }: { result: IngestResult }) {
  const { etl } = result;
  const total = Math.max(1, etl.readMs + etl.splitMs + etl.embedMs + etl.storeMs);
  return (
    <section className="panel etl" aria-label="Ingestion pipeline">
      <div className="p-head">
        <h2>Pipeline for {result.filename}</h2>
        <small className="muted">
          {etl.chunks} chunk(s) · {formatMs(total)} in total
        </small>
      </div>
      <ol className="etl-steps">
        {STAGES.map((stage) => (
          <li key={stage.key}>
            <b>{stage.label}</b>
            <code>{stage.concept}</code>
            <span className="num">{formatMs(etl[stage.key])}</span>
            <div className="bar">
              <i style={{ width: `${Math.max(2, (etl[stage.key] / total) * 100)}%` }} />
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}
