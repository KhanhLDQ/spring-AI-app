import { useCallback, useEffect, useState } from "react";
import { UnauthorizedError } from "../api/http";
import type { Order } from "../api/orders";

const POLL_MS = 2000;

/** Keeps a list of orders fresh, so an order the bot just created shows up without a page reload. */
export function useOrders(load: () => Promise<Order[]>, onUnauthorized: () => void) {
  const [orders, setOrders] = useState<Order[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloads, setReloads] = useState(0);

  useEffect(() => {
    let stopped = false;
    let timer: number | undefined;
    async function tick() {
      try {
        const latest = await load();
        if (!stopped) {
          setOrders(latest);
          setError(null);
        }
      } catch (e) {
        if (stopped) {
          return;
        }
        if (e instanceof UnauthorizedError) {
          onUnauthorized();
          return;
        }
        setError("Could not load orders. Retrying…");
      }
      if (!stopped) {
        timer = window.setTimeout(tick, POLL_MS);
      }
    }
    void tick();
    return () => {
      stopped = true;
      window.clearTimeout(timer);
    };
  }, [load, onUnauthorized, reloads]);

  const reload = useCallback(() => setReloads((n) => n + 1), []);
  return { orders, error, reload };
}
