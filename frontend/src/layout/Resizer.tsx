import { useCallback, useRef, useState, type KeyboardEvent, type PointerEvent } from "react";

interface Size {
  value: number;
  set: (value: number) => void;
  reset: () => void;
  min: number;
  max: number;
}

const read = (key: string): number | null => {
  try {
    const raw = window.localStorage.getItem(key);
    return raw === null ? null : Number(raw);
  } catch {
    return null;
  }
};

/** A panel size in pixels, clamped, and remembered in this browser so a reload keeps the presenter's layout. */
export function usePanelSize(key: string, initial: number, min: number, max: number): Size {
  const clamp = useCallback((v: number) => Math.round(Math.min(max, Math.max(min, v))), [min, max]);
  const [value, setValue] = useState(() => {
    const saved = read(key);
    return saved !== null && Number.isFinite(saved) ? clamp(saved) : initial;
  });
  const set = useCallback(
    (v: number) => {
      const next = clamp(v);
      setValue(next);
      try {
        window.localStorage.setItem(key, String(next));
      } catch {
        // storage blocked: the size still applies until the page reloads
      }
    },
    [clamp, key],
  );
  const reset = useCallback(() => {
    setValue(initial);
    try {
      window.localStorage.removeItem(key);
    } catch {
      // ignore
    }
  }, [initial, key]);
  return { value, set, reset, min, max };
}

interface Props {
  size: Size;
  /** "x" drags sideways (a column edge), "y" drags up and down (a row edge). */
  axis: "x" | "y";
  /** +1 when dragging right/down makes the panel bigger, -1 when it makes it smaller. */
  direction: 1 | -1;
  label: string;
  className?: string;
}

const STEP = 16;

/** Drag handle for a panel edge; arrow keys resize too, double-click puts the default size back. */
export function Resizer({ size, axis, direction, label, className }: Props) {
  const start = useRef<{ pointer: number; value: number } | null>(null);

  function onPointerDown(event: PointerEvent<HTMLDivElement>) {
    event.preventDefault();
    try {
      event.currentTarget.setPointerCapture(event.pointerId);
    } catch {
      // pointer already gone; the drag simply ends on the next move
    }
    start.current = { pointer: axis === "x" ? event.clientX : event.clientY, value: size.value };
    document.body.classList.add(axis === "x" ? "resizing-x" : "resizing-y");
  }

  function onPointerMove(event: PointerEvent<HTMLDivElement>) {
    if (!start.current) {
      return;
    }
    const delta = (axis === "x" ? event.clientX : event.clientY) - start.current.pointer;
    size.set(start.current.value + direction * delta);
  }

  function onPointerUp(event: PointerEvent<HTMLDivElement>) {
    start.current = null;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    document.body.classList.remove("resizing-x", "resizing-y");
  }

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const grow = axis === "x" ? (direction === 1 ? "ArrowRight" : "ArrowLeft") : direction === 1 ? "ArrowDown" : "ArrowUp";
    const shrink = axis === "x" ? (direction === 1 ? "ArrowLeft" : "ArrowRight") : direction === 1 ? "ArrowUp" : "ArrowDown";
    if (event.key === grow || event.key === shrink) {
      // The chat page uses ← → to step through turns; keep those keys here while the handle has focus.
      event.preventDefault();
      event.stopPropagation();
      size.set(size.value + (event.key === grow ? STEP : -STEP));
    }
  }

  return (
    <div
      className={`resizer resizer-${axis}${className ? ` ${className}` : ""}`}
      role="separator"
      aria-orientation={axis === "x" ? "vertical" : "horizontal"}
      aria-label={label}
      aria-valuenow={size.value}
      aria-valuemin={size.min}
      aria-valuemax={size.max}
      tabIndex={0}
      title={`${label}: drag to resize, double-click to reset`}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerUp}
      onDoubleClick={size.reset}
      onKeyDown={onKeyDown}
    />
  );
}
