import type { ToolCall } from "./toolSteps";

export function ToolChips({ calls }: { calls: ToolCall[] }) {
  if (calls.length === 0) {
    return null;
  }
  return (
    <div className="chips">
      {calls.map((call, i) => (
        <span key={i} className={`tchip${call.failed ? " err" : ""}`}>
          <svg className="ic" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.6 2.6-2.4-.6-.6-2.4z" />
          </svg>
          {call.name}({call.args}) {call.failed ? "refused" : "done"}
        </span>
      ))}
    </div>
  );
}
