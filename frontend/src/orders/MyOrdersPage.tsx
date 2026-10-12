import { useCallback } from "react";
import { fetchMyOrders } from "../api/orders";
import { useAuth } from "../auth/AuthContext";
import { countItems, describeItems, formatVnd, formatWhen } from "./format";
import { StatusPill } from "./StatusPill";
import { usePolling } from "../api/usePolling";

export function MyOrdersPage() {
  const { session, logout } = useAuth();
  const token = session!.token;
  const load = useCallback(() => fetchMyOrders(token), [token]);
  const { data: orders, error } = usePolling(load, logout);

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>My orders</h1>
          <p>Orders created by the bot appear here right away, without reloading the page.</p>
        </div>
        <span className="live">
          <i />
          Refreshes every 2 seconds
        </span>
      </div>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="panel table-wrap">
        <table>
          <thead>
            <tr>
              <th>Order</th>
              <th>Product</th>
              <th className="r">Qty</th>
              <th className="r">Total</th>
              <th>Status</th>
              <th>Created</th>
            </tr>
          </thead>
          <tbody>
            {orders?.map((order) => (
              <tr key={order.id}>
                <td className="mono num">#{order.id}</td>
                <td>{describeItems(order)}</td>
                <td className="r num">{countItems(order)}</td>
                <td className="r num">{formatVnd(order.totalAmount)}</td>
                <td>
                  <StatusPill status={order.status} />
                </td>
                <td className="num muted">{formatWhen(order.createdAt)}</td>
              </tr>
            ))}
            {orders?.length === 0 && (
              <tr>
                <td colSpan={6} className="empty">
                  No orders yet. Ask the bot to place one.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
