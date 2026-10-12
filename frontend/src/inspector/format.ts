export const formatMs = (ms: number) => `${Math.round(ms).toLocaleString("en-US")} ms`;
export const formatCost = (usd: number) => `$${usd.toFixed(4)}`;
