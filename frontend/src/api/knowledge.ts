import { request } from "./http";

export interface KnowledgeDoc {
  id: number;
  filename: string;
  uploadedAt: string;
  chunkCount: number;
}

/** Milliseconds spent in each stage of the ingestion pipeline for one upload. */
export interface EtlTimings {
  readMs: number;
  splitMs: number;
  embedMs: number;
  storeMs: number;
  chunks: number;
}

export interface IngestResult {
  id: number;
  filename: string;
  chunkCount: number;
  etl: EtlTimings;
}

export interface SearchHit {
  source: string;
  page: number | null;
  score: number;
  text: string;
}

export const fetchKnowledge = (token: string) => request<KnowledgeDoc[]>(token, "/api/admin/knowledge");

export function uploadKnowledge(token: string, file: File) {
  const body = new FormData();
  body.append("file", file);
  return request<IngestResult>(token, "/api/admin/knowledge", { method: "POST", body });
}

export const deleteKnowledge = (token: string) => request<void>(token, "/api/admin/knowledge", { method: "DELETE" });

export const searchKnowledge = (token: string, query: string) =>
  request<SearchHit[]>(token, "/api/admin/knowledge/search", { method: "POST", body: JSON.stringify({ query }) });

export interface Chunking {
  chunkTokens: number;
  defaultChunkTokens: number;
  documents: number;
  chunks: number;
}

export const fetchChunking = (token: string) => request<Chunking>(token, "/api/admin/knowledge/chunking");

/** Loads the bundled documents again at the new size; uploaded documents are dropped. */
export const setChunkSize = (token: string, chunkTokens: number) =>
  request<Chunking>(token, "/api/admin/knowledge/chunking", { method: "PUT", body: JSON.stringify({ chunkTokens }) });
