import type { OrderStatus } from "../api/orders";

export function StatusPill({ status }: { status: OrderStatus }) {
  return <span className={`pill ${status}`}>{status}</span>;
}
