import { request } from "./http";

export interface RevenueSummary {
  from: string;
  to: string;
  revenueVnd: number;
  orders: number;
  bottles: number;
  topProducts: { product: string; size: string; bottles: number; revenueVnd: number }[];
  previous: { from: string; to: string; revenueVnd: number; orders: number; changePercent: number | null };
}

export const fetchRevenue = (token: string, range: { from?: string; to?: string }) => {
  const query = new URLSearchParams();
  if (range.from) query.set("from", range.from);
  if (range.to) query.set("to", range.to);
  const suffix = query.size > 0 ? `?${query}` : "";
  return request<RevenueSummary>(token, `/api/admin/revenue${suffix}`);
};
