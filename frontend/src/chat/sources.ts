import type { RagChunk, RagDetail, TraceStep } from "../api/chat";

export const ragChunks = (steps: TraceStep[]): RagChunk[] =>
  steps.filter((step) => step.kind === "rag").flatMap((step) => (step.detail as unknown as RagDetail).chunks ?? []);

export const sourceLabel = (chunk: RagChunk) => (chunk.page ? `${chunk.source} · p.${chunk.page}` : chunk.source);

/** One entry per distinct file and page, in retrieval order, numbered the way the chips show them. */
export function citedSources(steps: TraceStep[]): { label: string; text: string }[] {
  const seen = new Map<string, { label: string; text: string }>();
  for (const chunk of ragChunks(steps)) {
    const label = sourceLabel(chunk);
    if (!seen.has(label)) {
      seen.set(label, { label, text: chunk.text });
    }
  }
  return [...seen.values()];
}
