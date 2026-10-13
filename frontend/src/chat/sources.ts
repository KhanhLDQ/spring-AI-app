import type { RagChunk, RagDetail, TraceStep } from "../api/chat";

/** Chunks the model was given; a cached answer carries the chunks of the answer it replays instead. */
export const ragChunks = (steps: TraceStep[]): RagChunk[] =>
  steps.flatMap((step) => {
    if (step.kind === "rag") {
      return (step.detail as unknown as RagDetail).chunks ?? [];
    }
    return step.kind === "cache" ? ((step.detail.sources as RagChunk[] | undefined) ?? []) : [];
  });

export const sourceLabel = (chunk: RagChunk) => (chunk.page ? `${chunk.source} · p.${chunk.page}` : chunk.source);

/** One entry per distinct file and page, in retrieval order, numbered the way the chips show them. */
export function citedSources(steps: TraceStep[]): { label: string; text: string }[] {
  // A turn that called a tool was an action, not a lookup; the chunks retrieval added anyway are noise as citations.
  if (steps.some((step) => step.kind === "tool")) {
    return [];
  }
  const seen = new Map<string, { label: string; text: string }>();
  for (const chunk of ragChunks(steps)) {
    const label = sourceLabel(chunk);
    if (!seen.has(label)) {
      seen.set(label, { label, text: chunk.text });
    }
  }
  return [...seen.values()];
}
