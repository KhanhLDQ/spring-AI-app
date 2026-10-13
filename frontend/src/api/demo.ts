import { request } from "./http";

export interface Thresholds {
  cache: number;
  rag: number;
  route: number;
}

export interface AppConfig {
  model: string;
  pricing: { inPerMTok: number; outPerMTok: number };
  budget: { usedUsd: number; limitUsd: number };
  thresholds: Thresholds;
  profile: string;
}

export type DemoAction = "reset" | "reseed" | "clear-cache" | "clear-vectors" | "clear-memory";

export const fetchConfig = (token: string) => request<AppConfig>(token, "/api/config");

export const runDemoAction = (token: string, action: DemoAction) =>
  request<void>(token, `/api/admin/demo/${action}`, { method: "POST" });

export const setThresholds = (token: string, thresholds: Partial<Thresholds>) =>
  request<Thresholds>(token, "/api/admin/demo/threshold", { method: "PUT", body: JSON.stringify(thresholds) });
