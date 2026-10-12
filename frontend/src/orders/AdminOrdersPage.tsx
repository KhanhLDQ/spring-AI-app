import { useCallback, useState } from "react";
import { fetchAllOrders, approveOrder, rejectOrder, type OrderStatus } from "../api/orders";
import { ApiError, UnauthorizedError } from "../api/http";
import { useAuth } from "../auth/AuthContext";
import { countItems, describeItems, formatVnd } from "./format";
import { StatusPill } from "./StatusPill";
import { useOrders } from "./useOrders";

type Filter = "ALL" | OrderStatus;

const FILTERS: { id: Filter; label: string }[] = [
  { id: "ALL", label: "All" },
  { id: "PENDING", label: "Pending" },
  { id: "APPROVED", label: "Approved" },
  { id: "REJECTED", label: "Rejected" },
  { id: "CANCELLED", label: "Cancelled" },
];

export function AdminOrdersPage() {
  const { session, logout } = useAuth();
  const token = session!.token;
  const load = useCallback(() => fetchAllOrders(token), [token]);
  const { orders, error, reload } = useOrders(load, logout);
  const [filter, setFilter] = useState<Filter>("ALL");
  const [actionError, setActionError] = useState<string | null>(null);

  async function decide(id: number, action: typeof approveOrder) {
    setActionError(null);
    try {
      await action(token, id);
    } catch (e) {
      if (e instanceof UnauthorizedError) {
        logout();
        return;
      }
      setActionError(e instanceof ApiError ? e.message : "The action failed. Please try again.");
    }
    reload();
  }

  const count = (f: Filter) => (orders ?? []).filter((o) => f === "ALL" || o.status === f).length;
  const visible = (orders ?? []).filter((o) => filter === "ALL" || o.status === filter);

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>Approve orders</h1>
          <p>Every customer's orders. Approve or reject the ones that are still pending.</p>
        </div>
        <div className="filters" role="group" aria-label="Filter by status">
          {FILTERS.map((f) => (
            <button key={f.id} className="chip" aria-pressed={filter === f.id} onClick={() => setFilter(f.id)}>
              {f.label} · {count(f.id)}
            </button>
          ))}
        </div>
      </div>
      {(error || actionError) && <p className="form-error" role="alert">{actionError ?? error}</p>}
      <div className="panel table-wrap">
        <table>
          <thead>
            <tr>
              <th>Order</th>
              <th>Customer</th>
              <th>Product</th>
              <th className="r">Qty</th>
              <th className="r">Total</th>
              <th>Status</th>
              <th className="r">Actions</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((order) => (
              <tr key={order.id}>
                <td className="mono num">#{order.id}</td>
                <td>{order.username}</td>
                <td>{describeItems(order)}</td>
                <td className="r num">{countItems(order)}</td>
                <td className="r num">{formatVnd(order.totalAmount)}</td>
                <td>
                  <StatusPill status={order.status} />
                </td>
                <td className="r">
                  {order.status === "PENDING" ? (
                    <>
                      <button className="btn sm" onClick={() => decide(order.id, approveOrder)}>
                        Approve
                      </button>{" "}
                      <button className="btn sm danger" onClick={() => decide(order.id, rejectOrder)}>
                        Reject
                      </button>
                    </>
                  ) : (
                    <span className="muted">—</span>
                  )}
                </td>
              </tr>
            ))}
            {orders && visible.length === 0 && (
              <tr>
                <td colSpan={7} className="empty">
                  No orders with this status.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
