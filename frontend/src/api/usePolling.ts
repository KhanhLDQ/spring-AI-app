import { useCallback, useEffect, useState } from "react";
import { UnauthorizedError } from "./http";

const POLL_MS = 2000;

/** Keeps data fresh (an order the bot just created, a stock level that just dropped) without a page reload. */
export function usePolling<T>(load: () => Promise<T>, onUnauthorized: () => void, enabled = true) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloads, setReloads] = useState(0);

  useEffect(() => {
    if (!enabled) {
      return;
    }
    let stopped = false;
    let timer: number | undefined;
    async function tick() {
      try {
        const latest = await load();
        if (!stopped) {
          setData(latest);
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
        setError("Could not refresh the data. Retrying…");
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
  }, [load, onUnauthorized, enabled, reloads]);

  const reload = useCallback(() => setReloads((n) => n + 1), []);
  return { data, error, reload };
}
