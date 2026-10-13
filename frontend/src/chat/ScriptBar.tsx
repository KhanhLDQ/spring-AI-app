import type { Role } from "../api/auth";

interface Script {
  tag: string;
  message: string;
  adminOnly?: boolean;
}

/** The demo flow in the order it teaches: each message is sent exactly as typed, so the real pipeline runs. */
const SCRIPTS: Script[] = [
  { tag: "RAG", message: "Kefir mật ong để được bao lâu?" },
  { tag: "Cache", message: "Kefir mật ong bảo quản được bao lâu vậy?" },
  { tag: "No answer", message: "Shop giao ra Hà Nội không?" },
  { tag: "RAG + tool", message: "Kefir mật ong 330ml giá bao nhiêu, còn hàng không?" },
  { tag: "Tool", message: "Cho mình đặt 2 chai Kefir Mật Ong 330ml" },
  { tag: "Memory", message: "Đổi thành 4 chai" },
  { tag: "Tool", message: "Huỷ đơn vừa đặt" },
  { tag: "Stock", message: "Đặt 500 chai Kefir Mật Ong 330ml" },
  { tag: "PDF", message: "Ưu đãi tháng 10 có gì?" },
  { tag: "Guardrail", message: "Doanh thu tháng 9 là bao nhiêu?" },
  { tag: "Admin", message: "Duyệt đơn #1031", adminOnly: true },
  { tag: "Revenue", message: "Doanh thu 60 ngày qua là bao nhiêu?", adminOnly: true },
];

interface Props {
  role: Role;
  disabled: boolean;
  used: ReadonlySet<string>;
  onRun: (message: string) => void;
}

export function ScriptBar({ role, disabled, used, onRun }: Props) {
  return (
    <div className="script">
      <div className="script-in">
        <span className="script-lbl">Demo scripts</span>
        {SCRIPTS.filter((s) => !s.adminOnly || role === "ADMIN").map((s) => (
          <button key={s.message} type="button" className="sc" disabled={disabled} onClick={() => onRun(s.message)}>
            <em>{s.tag}</em>
            {s.message}
            {used.has(s.message) && " ✓"}
          </button>
        ))}
      </div>
    </div>
  );
}
