import type { Order } from "../api/orders";
import { countItems, describeItems, formatVnd } from "../orders/format";
import { StatusPill } from "../orders/StatusPill";

export function OrderCard({ order, onOpenOrders }: { order: Order; onOpenOrders: () => void }) {
  return (
    <div className="card">
      <div className="card-top">
        <b>Order #{order.id}</b>
        <StatusPill status={order.status} />
      </div>
      <dl className="kv">
        <dt>Product</dt>
        <dd>{describeItems(order)}</dd>
        <dt>Quantity</dt>
        <dd>{countItems(order)}</dd>
        <dt>Total</dt>
        <dd>{formatVnd(order.totalAmount)}</dd>
      </dl>
      <button className="btn sm" onClick={onOpenOrders}>
        View my orders
      </button>
    </div>
  );
}
