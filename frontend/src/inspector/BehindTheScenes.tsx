import type { ModelDetail, PromptMessage, TraceStep, TurnStats } from "../api/chat";
import { formatMs } from "./format";

/**
 * The turn as Spring AI exchanged it with the model, built only from what the backend recorded: each ChatModel
 * observation (the prompt it was given, the finish reason and tool calls it returned), each tool observation, and
 * the chunks counted on the stream. Nothing here is inferred from the answer text.
 */
type Lane = "out" | "in" | "app";

interface Event {
  ms: number;
  /** Breaks ties at the same millisecond in the order things happen: the last chunk arrives, then the observation stops. */
  order?: number;
  lane: Lane;
  title: string;
  body: React.ReactNode;
}

const LANE_LABEL: Record<Lane, string> = { out: "App → Model", in: "Model → App", app: "App" };

function roles(messages: PromptMessage[]) {
  const count = new Map<string, number>();
  messages.forEach((m) => count.set(m.role, (count.get(m.role) ?? 0) + 1));
  return [...count].map(([role, n]) => (n > 1 ? `${role} ×${n}` : role)).join(" · ");
}

function RequestBody({ detail }: { detail: ModelDetail }) {
  const req = detail.request ?? {};
  const messages = req.messages ?? [];
  const tools = req.tools ?? [];
  return (
    <>
      <dl className="ins-kv">
        <dt>Messages</dt>
        <dd>{messages.length ? roles(messages) : "none"}</dd>
        <dt>Tools</dt>
        <dd>{tools.length ? tools.join(", ") : "none sent"}</dd>
        {req.maxTokens != null && (
          <>
            <dt>Limit</dt>
            <dd>{req.maxTokens} output tokens</dd>
          </>
        )}
      </dl>
      {messages.length > 0 && (
        <details className="bts-msgs">
          <summary>Prompt as sent</summary>
          {messages.map((m, i) => (
            <div className="bts-msg" key={i}>
              <span className={`role role-${m.role}`}>{m.role}</span>
              <small>{m.chars} chars{m.toolCalls?.length ? ` · asked for ${m.toolCalls.join(", ")}` : ""}</small>
              {m.text && <pre>{m.text}</pre>}
            </div>
          ))}
        </details>
      )}
    </>
  );
}

function ResponseBody({ detail }: { detail: ModelDetail }) {
  const calls = detail.toolCalls ?? [];
  return (
    <>
      <dl className="ins-kv">
        <dt>Finish</dt>
        <dd>{detail.finishReason ? <span className="pill">{detail.finishReason}</span> : "not reported"}</dd>
        <dt>Tokens</dt>
        <dd>
          {detail.tokensIn ?? 0} in · {detail.tokensOut ?? 0} out
        </dd>
        {calls.length > 0 && (
          <>
            <dt>Tool calls</dt>
            <dd>
              {calls.map((c, i) => (
                <div key={i}>
                  {c.name}({c.args})
                </div>
              ))}
            </dd>
          </>
        )}
      </dl>
      <span className={calls.length ? "note" : "note ok"}>
        {calls.length
          ? "Tool calls in the response: ToolCallingAdvisor runs them and calls the model again with the results."
          : "No tool call in the response: the tool loop ends here."}
      </span>
    </>
  );
}

function events(steps: TraceStep[], stats?: TurnStats): Event[] {
  const out: Event[] = [];
  const models = steps.filter((s) => s.kind === "model");
  models.forEach((s, i) => {
    const d = s.detail as ModelDetail;
    const round = models.length > 1 ? ` ${i + 1}` : "";
    out.push({ ms: s.startMs, lane: "out", title: `Request${round}`, body: <RequestBody detail={d} /> });
    out.push({ ms: s.startMs + s.durMs, order: 1, lane: "in", title: `Response${round}`, body: <ResponseBody detail={d} /> });
  });
  steps
    .filter((s) => s.kind === "tool")
    .forEach((s) =>
      out.push({
        ms: s.startMs,
        lane: "app",
        title: `@Tool ${String(s.detail.name ?? "")}`,
        body: (
          <>
            <p className="bts-p">
              Arguments from the model: <b>{String(s.detail.args ?? "")}</b>
            </p>
            <span className="ret">→ {String(s.detail.result ?? "")}</span>
          </>
        ),
      }),
    );
  const hit = steps.find((s) => s.kind === "cache" && s.detail.hit);
  if (hit) {
    out.push({ ms: hit.startMs, lane: "app", title: "Semantic cache hit", body: <p className="bts-p">Answered from the cache. No request was sent to the model.</p> });
  }
  const st = stats?.stream;
  // Measured on Sonnet (2026-10-16): with tools sent, the text of the turn reaches the stream only after the last
  // model round has finished, all within a few ms, instead of trickling in while the model writes.
  const withTools = models.some((s) => ((s.detail as ModelDetail).request?.tools ?? []).length > 0);
  if (st?.chunks) {
    const source = models.length ? "model" : "cache";
    out.push({
      ms: st.firstChunkMs ?? 0,
      lane: models.length ? "in" : "app",
      title: "First chunk",
      body: (
        <p className="bts-p">
          First of <b>{st.chunks}</b> chunks from the {source}; <b>{st.textChunks ?? 0}</b> carried text and went to the browser as token events.
          {withTools &&
            " With tools sent, ToolCallingAdvisor checks each round for tool calls before passing it on, so the text arrives only after the last round has finished."}
        </p>
      ),
    });
    (st.finishes ?? []).forEach((f) =>
      out.push({
        ms: f.ms,
        lane: models.length ? "in" : "app",
        title: `Chunk ${f.chunk} of ${st.chunks}`,
        body: (
          <p className="bts-p">
            Carries <span className="pill">{f.reason}</span>.{" "}
            {withTools ? "The last round ended without a tool call, so the tool loop is over." : "This is the moment the stream knows the model has stopped."}
          </p>
        ),
      }),
    );
  }
  if (stats) {
    out.push({ ms: stats.ms, order: 2, lane: "app", title: "Turn complete", body: <p className="bts-p">Steps and the done event are sent to the browser.</p> });
  }
  return out.sort((a, b) => a.ms - b.ms || (a.order ?? 0) - (b.order ?? 0));
}

export function BehindTheScenes({ steps, stats, streaming }: { steps: TraceStep[]; stats?: TurnStats; streaming: boolean }) {
  if (streaming && !stats) {
    return <div className="bts-wait">Recording the exchange with the model…</div>;
  }
  const list = events(steps, stats);
  if (!list.length) {
    return null;
  }
  return (
    <ol className="bts">
      {list.map((e, i) => (
        <li key={i} className={`bts-e lane-${e.lane}`}>
          <span className="bts-ms">{formatMs(e.ms)}</span>
          <div className="bts-card">
            <div className="bts-h">
              <span className="lane">{LANE_LABEL[e.lane]}</span>
              <b>{e.title}</b>
            </div>
            {e.body}
          </div>
        </li>
      ))}
    </ol>
  );
}
