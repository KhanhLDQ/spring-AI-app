import type { Order } from "../api/orders";

const money = new Intl.NumberFormat("vi-VN");
const when = new Intl.DateTimeFormat("en-GB", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" });

export const formatVnd = (amount: number) => `${money.format(amount)}₫`;
export const formatWhen = (iso: string) => when.format(new Date(iso));
export const describeItems = (order: Order) =>
  order.items.map((i) => `${i.product} ${i.size}`).join(", ");
export const countItems = (order: Order) => order.items.reduce((sum, i) => sum + i.quantity, 0);
