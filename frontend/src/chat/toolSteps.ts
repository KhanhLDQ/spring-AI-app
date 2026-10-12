import type { TraceStep } from "../api/chat";

/** Tools whose result is a single order, shown as a card that stays in sync with the order's status. */
const ORDER_TOOLS = new Set(["createOrder", "updateOrderQuantity", "cancelOrder"]);

export interface ToolCall {
  name: string;
  args: string;
  failed: boolean;
  orderId?: number;
}

function summarizeArgs(raw: unknown): string {
  try {
    return Object.values(JSON.parse(String(raw ?? "{}"))).join(", ");
  } catch {
    return String(raw ?? "");
  }
}

export function toolCalls(steps: TraceStep[]): ToolCall[] {
  return steps
    .filter((step) => step.kind === "tool")
    .map((step) => {
      const name = String(step.detail.name);
      const result = String(step.detail.result ?? "");
      // A tool that refuses throws, and the framework hands the model plain text instead of JSON.
      const failed = !/^[[{]/.test(result);
      // The result is abbreviated for the Inspector, so read the id from its start rather than parsing it whole.
      const id = ORDER_TOOLS.has(name) && !failed ? /"id":\s*(\d+)/.exec(result)?.[1] : undefined;
      return { name, args: summarizeArgs(step.detail.args), failed, orderId: id ? Number(id) : undefined };
    });
}
