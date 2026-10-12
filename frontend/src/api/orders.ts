import { request } from "./http";

export type OrderStatus = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";

export interface Order {
  id: number;
  username: string;
  status: OrderStatus;
  createdAt: string;
  totalAmount: number;
  items: { product: string; size: string; quantity: number; unitPrice: number }[];
}

export const fetchMyOrders = (token: string) => request<Order[]>(token, "/api/orders/mine");
export const fetchAllOrders = (token: string) => request<Order[]>(token, "/api/admin/orders");
export const approveOrder = (token: string, id: number) =>
  request<Order>(token, `/api/admin/orders/${id}/approve`, { method: "POST" });
export const rejectOrder = (token: string, id: number) =>
  request<Order>(token, `/api/admin/orders/${id}/reject`, { method: "POST" });
