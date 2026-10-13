import { useCallback, useRef, useState, type DragEvent } from "react";
import { deleteKnowledge, fetchKnowledge, uploadKnowledge, type IngestResult } from "../api/knowledge";
import { ApiError, UnauthorizedError } from "../api/http";
import { usePolling } from "../api/usePolling";
import { useAuth } from "../auth/AuthContext";
import { formatWhen } from "../orders/format";
import { EtlPipeline } from "./EtlPipeline";
import { RetrievalTest } from "./RetrievalTest";

export function KnowledgePage() {
  const { session, logout } = useAuth();
  const token = session!.token;
  const load = useCallback(() => fetchKnowledge(token), [token]);
  const { data: docs, error, reload } = usePolling(load, logout);
  const [uploading, setUploading] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [confirmingWipe, setConfirmingWipe] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [lastUpload, setLastUpload] = useState<IngestResult | null>(null);
  const picker = useRef<HTMLInputElement>(null);

  async function run(action: () => Promise<void>) {
    setActionError(null);
    try {
      await action();
    } catch (e) {
      if (e instanceof UnauthorizedError) {
        logout();
        return;
      }
      setActionError(e instanceof ApiError ? e.message : "The action failed. Please try again.");
    }
    reload();
  }

  async function upload(file: File | undefined) {
    if (!file || uploading) {
      return;
    }
    setUploading(true);
    await run(async () => setLastUpload(await uploadKnowledge(token, file)));
    setUploading(false);
  }

  async function wipe() {
    setConfirmingWipe(false);
    await run(async () => {
      await deleteKnowledge(token);
      setLastUpload(null);
    });
  }

  function onDrop(event: DragEvent) {
    event.preventDefault();
    setDragging(false);
    void upload(event.dataTransfer.files[0]);
  }

  const chunkTotal = (docs ?? []).reduce((sum, d) => sum + d.chunkCount, 0);

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>Knowledge</h1>
          <p>Documents are split into chunks, embedded and stored in pgvector. The chat retrieves from them on every message.</p>
        </div>
      </div>
      {(error || actionError) && <p className="form-error" role="alert">{actionError ?? error}</p>}

      <div
        className={`drop${dragging ? " over" : ""}`}
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
      >
        <div>
          <b>{uploading ? "Processing…" : "Upload a document"}</b>
          <br />
          <span className="muted">PDF with a text layer, or Markdown (.md). Up to 10 MB and 50 pages. Drop a file here or choose one.</span>
        </div>
        <input
          ref={picker}
          type="file"
          accept=".pdf,.md,application/pdf,text/markdown"
          hidden
          onChange={(e) => {
            void upload(e.target.files?.[0]);
            e.target.value = "";
          }}
        />
        <button className="btn primary" disabled={uploading} onClick={() => picker.current?.click()}>
          Choose file
        </button>
      </div>

      {lastUpload && <EtlPipeline result={lastUpload} />}

      <div className="two">
        <section className="panel" aria-label="Loaded documents">
          <div className="p-head">
            <h2>
              Loaded documents · {docs?.length ?? 0} · {chunkTotal} chunks
            </h2>
            {confirmingWipe ? (
              <span className="confirm">
                Delete every chunk and the semantic cache?
                <button className="btn sm danger" onClick={wipe}>
                  Yes, delete
                </button>
                <button className="btn sm" onClick={() => setConfirmingWipe(false)}>
                  Cancel
                </button>
              </span>
            ) : (
              <button className="btn sm danger" disabled={!docs?.length} onClick={() => setConfirmingWipe(true)}>
                Clear vector store
              </button>
            )}
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>File</th>
                  <th className="r">Chunks</th>
                  <th>Loaded</th>
                </tr>
              </thead>
              <tbody>
                {docs?.map((doc) => (
                  <tr key={doc.id}>
                    <td className="mono">{doc.filename}</td>
                    <td className="r num">{doc.chunkCount}</td>
                    <td className="num muted">{formatWhen(doc.uploadedAt)}</td>
                  </tr>
                ))}
                {docs && docs.length === 0 && (
                  <tr>
                    <td colSpan={3} className="empty">
                      The vector store is empty. Upload a document to begin.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
        <RetrievalTest token={token} onUnauthorized={logout} />
      </div>
    </div>
  );
}
