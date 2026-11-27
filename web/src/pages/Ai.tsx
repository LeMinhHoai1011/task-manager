import { useEffect, useRef, useState } from "react";
import { apiFetch, mutationOpts, newIdempotencyKey, unwrapData } from "../api/client";
import { useAccess } from "../auth/AccessContext";
import { statusLabel } from "../i18n/labels";
import AppIcon from "../components/AppIcon";

interface AiJob {
  id: string;
  feature: string;
  status: string;
  attempt?: number;
  maxAttempts?: number;
}

interface AiSuggestion {
  jobId: string;
  status: string;
  payload: string;
}

type ChatMsg =
  | { role: "user"; text: string; feature: string; projectId?: string; ts: number }
  | { role: "ai"; jobId: string; status: string; payload?: Record<string, unknown> | null; raw?: string; ts: number }
  | { role: "system"; text: string; ts: number };

function parsePayload(payload: string): Record<string, unknown> | null {
  try {
    const o = JSON.parse(payload);
    return o && typeof o === "object" && !Array.isArray(o) ? o : null;
  } catch {
    return null;
  }
}

const QUICK_ACTIONS = [
  { feature: "TASK_DRAFT", hint: "Soạn công việc mới" },
  { feature: "DAILY_PLANNER", hint: "Lên kế hoạch hôm nay" },
  { feature: "WEEKLY_REVIEW", hint: "Tổng kết tuần này" },
] as const;

export default function Ai() {
  const access = useAccess();
  const projects = access.projects;
  const [messages, setMessages] = useState<ChatMsg[]>([]);
  const [input, setInput] = useState("");
  const [projectId, setProjectId] = useState("");
  const [busy, setBusy] = useState(false);
  const [pollingJobId, setPollingJobId] = useState<string | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  function detectFeature(text: string): string {
    const lower = text.toLowerCase();
    if (lower.includes("kế hoạch") || lower.includes("hôm nay") || lower.includes("lên lịch")) return "DAILY_PLANNER";
    if (lower.includes("tổng kết") || lower.includes("tuần")) return "WEEKLY_REVIEW";
    if (lower.includes("ưu tiên")) return "PRIORITY_SUGGEST";
    if (lower.includes("deadline") || lower.includes("hạn") || lower.includes("thời hạn")) return "DEADLINE_SUGGEST";
    if (lower.includes("ai làm") || lower.includes("giao cho") || lower.includes("phụ trách")) return "ASSIGNEE_SUGGEST";
    if (lower.includes("chia nhỏ") || lower.includes("subtask") || lower.includes("việc con")) return "SUBTASK_SUGGEST";
    return "TASK_DRAFT";
  }

  async function send(overrideFeature?: string, overrideHint?: string) {
    const text = overrideHint ?? input.trim();
    if (!text || busy) return;

    const feature = overrideFeature ?? detectFeature(text);
    const userMsg: ChatMsg = { role: "user", text, feature, projectId: projectId || undefined, ts: Date.now() };
    setMessages((prev) => [...prev, userMsg]);
    setInput("");
    setBusy(true);

    try {
      const r = await apiFetch("/ai/request", {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: {
          feature,
          projectId: projectId.trim() || undefined,
          inputHint: text,
          idempotencyKey: newIdempotencyKey(),
        },
      });
      const job = unwrapData<AiJob>(r);
      if (!job?.id) throw new Error("empty job");

      setMessages((prev) => [
        ...prev,
        { role: "ai", jobId: job.id, status: job.status, ts: Date.now() },
      ]);
      setPollingJobId(job.id);
    } catch (e: any) {
      setMessages((prev) => [
        ...prev,
        { role: "system", text: e?.uiMessage ?? "Không gửi được yêu cầu. Hãy thử lại.", ts: Date.now() },
      ]);
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    if (!pollingJobId) return;
    let active = true;
    const poll = async () => {
      try {
        const jr = await apiFetch(`/ai/jobs/${pollingJobId}`);
        const job = unwrapData<AiJob>(jr);
        if (!active || !job) return;

        const terminal = ["COMPLETED", "FAILED_TIMEOUT", "FAILED_OLLAMA", "FAILED_VALIDATION", "CANCELLED_REVOKED"];
        if (terminal.includes(job.status)) {
          setPollingJobId(null);
          try {
            const sr = await apiFetch(`/ai/jobs/${pollingJobId}/suggestion`);
            const sug = unwrapData<AiSuggestion>(sr);
            if (sug && active) {
              const parsed = parsePayload(sug.payload);
              setMessages((prev) =>
                prev.map((m) =>
                  m.role === "ai" && m.jobId === pollingJobId
                    ? { ...m, status: job.status, payload: parsed, raw: parsed ? undefined : sug.payload }
                    : m
                )
              );
              return;
            }
          } catch {}
          if (active) {
            setMessages((prev) =>
              prev.map((m) =>
                m.role === "ai" && m.jobId === pollingJobId ? { ...m, status: job.status } : m
              )
            );
          }
        }
      } catch {}
    };
    const timer = setInterval(poll, 2500);
    void poll();
    return () => { active = false; clearInterval(timer); };
  }, [pollingJobId]);

  async function acceptJob(jobId: string) {
    try {
      await apiFetch(`/ai/jobs/${jobId}/accept`, {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: {},
      });
      setMessages((prev) => [
        ...prev,
        { role: "system", text: "Đã tạo công việc từ gợi ý AI.", ts: Date.now() },
      ]);
    } catch (e: any) {
      setMessages((prev) => [
        ...prev,
        { role: "system", text: e?.uiMessage ?? "Không thể áp dụng gợi ý.", ts: Date.now() },
      ]);
    }
  }

  async function rejectJob(jobId: string) {
    try {
      await apiFetch(`/ai/jobs/${jobId}/reject`, {
        ...mutationOpts({ idempotencyKey: newIdempotencyKey() }),
        method: "POST",
        body: {},
      });
      setMessages((prev) =>
        prev.map((m) =>
          m.role === "ai" && m.jobId === jobId ? { ...m, status: "REJECTED" } : m
        )
      );
    } catch {}
  }

  function handleKeyDown(e: React.KeyboardEvent) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      void send();
    }
  }

  const isTerminal = (s: string) =>
    ["COMPLETED", "REJECTED", "ACCEPTED", "APPLIED", "FAILED_TIMEOUT", "FAILED_OLLAMA", "FAILED_VALIDATION", "CANCELLED_REVOKED"].includes(s);

  return (
    <div className="mx-auto max-w-3xl flex flex-col h-[calc(100vh-5rem)]">
      {/* Header */}
      <div className="flex items-center gap-3 pb-3 border-b border-slate-200 dark:border-slate-800">
        <div className="w-10 h-10 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center">
          <AppIcon name="ai" size={20} className="text-white" />
        </div>
        <div>
          <h1 className="text-lg font-bold text-slate-900 dark:text-white">Trợ lý AI</h1>
          <p className="text-xs text-slate-500">Mô tả công việc bằng ngôn ngữ tự nhiên — AI soạn nháp, bạn xác nhận.</p>
        </div>
      </div>

      {/* Chat messages */}
      <div className="flex-1 overflow-y-auto py-4 space-y-4">
        {messages.length === 0 && (
          <div className="text-center pt-12 space-y-6">
            <div className="w-16 h-16 mx-auto rounded-full bg-gradient-to-br from-blue-100 to-purple-100 dark:from-blue-900/30 dark:to-purple-900/30 flex items-center justify-center">
              <AppIcon name="ai" size={32} className="text-blue-500" />
            </div>
            <div>
              <p className="text-base font-semibold text-slate-700 dark:text-slate-300">Xin chào! Tôi có thể giúp gì?</p>
              <p className="text-sm text-slate-500 mt-1">Mô tả công việc bạn cần làm, tôi sẽ soạn nháp cho bạn xem và duyệt.</p>
            </div>
            <div className="flex flex-wrap justify-center gap-2">
              {QUICK_ACTIONS.map((qa) => (
                <button
                  key={qa.feature}
                  onClick={() => void send(qa.feature, qa.hint)}
                  className="px-4 py-2 rounded-full border border-slate-200 dark:border-slate-700 text-sm text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 transition"
                >
                  {qa.hint}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.map((msg, i) => {
          if (msg.role === "user") {
            return (
              <div key={i} className="flex justify-end">
                <div className="max-w-[80%] rounded-2xl rounded-br-md bg-blue-600 text-white px-4 py-3">
                  <p className="text-sm whitespace-pre-wrap">{msg.text}</p>
                  <p className="text-[10px] text-blue-200 mt-1">{statusLabel(msg.feature)}{msg.projectId ? " · Dự án" : " · Cá nhân"}</p>
                </div>
              </div>
            );
          }

          if (msg.role === "system") {
            return (
              <div key={i} className="flex justify-center">
                <p className="text-xs text-slate-500 bg-slate-100 dark:bg-slate-800 rounded-full px-4 py-1.5">{msg.text}</p>
              </div>
            );
          }

          // AI message
          const done = isTerminal(msg.status);
          const failed = msg.status.startsWith("FAILED") || msg.status === "CANCELLED_REVOKED";
          const accepted = msg.status === "ACCEPTED" || msg.status === "APPLIED";
          const rejected = msg.status === "REJECTED";

          return (
            <div key={i} className="flex justify-start">
              <div className="max-w-[85%]">
                <div className="rounded-2xl rounded-bl-md bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 px-4 py-3 shadow-sm">
                  {!done && !msg.payload && (
                    <div className="flex items-center gap-2 text-sm text-slate-500">
                      <span className="h-2 w-2 rounded-full bg-blue-500 animate-pulse" />
                      Đang xử lý...
                    </div>
                  )}

                  {failed && (
                    <p className="text-sm text-red-600">Không thể tạo gợi ý. Hãy thử lại với mô tả khác.</p>
                  )}

                  {msg.payload && (
                    <div className="space-y-2">
                      <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Gợi ý từ AI</p>
                      <div className="space-y-1.5">
                        {Object.entries(msg.payload).map(([k, v]) => (
                          <div key={k} className="flex gap-2 text-sm">
                            <span className="font-medium text-slate-600 dark:text-slate-400 min-w-[100px]">{statusLabel(k)}:</span>
                            <span className="text-slate-800 dark:text-slate-200 whitespace-pre-wrap">{typeof v === "string" ? v : JSON.stringify(v)}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {msg.raw && !msg.payload && done && !failed && (
                    <p className="text-sm text-slate-700 dark:text-slate-300 whitespace-pre-wrap">{msg.raw}</p>
                  )}

                  {accepted && <p className="text-xs text-green-600 font-semibold mt-2">Đã áp dụng</p>}
                  {rejected && <p className="text-xs text-slate-400 mt-2">Đã bỏ qua</p>}

                  {/* Action buttons */}
                  {done && !failed && !accepted && !rejected && (msg.payload || msg.raw) && (
                    <div className="flex gap-2 mt-3 pt-3 border-t border-slate-100 dark:border-slate-700">
                      <button
                        onClick={() => void acceptJob(msg.jobId)}
                        className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-green-600 text-white text-xs font-semibold hover:bg-green-700 transition"
                      >
                        <AppIcon name="check" size={14} /> Chấp nhận & tạo
                      </button>
                      <button
                        onClick={() => void rejectJob(msg.jobId)}
                        className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-700 text-xs font-medium text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 transition"
                      >
                        Bỏ qua
                      </button>
                    </div>
                  )}
                </div>
              </div>
            </div>
          );
        })}
        <div ref={bottomRef} />
      </div>

      {/* Input bar */}
      <div className="border-t border-slate-200 dark:border-slate-800 pt-3 pb-2 space-y-2">
        {/* Project selector */}
        <div className="flex items-center gap-2 text-xs">
          <span className="text-slate-500">Tạo cho:</span>
          <select
            value={projectId}
            onChange={(e) => setProjectId(e.target.value)}
            className="rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2 py-1 text-xs text-slate-700 dark:text-slate-300"
          >
            <option value="">Công việc cá nhân</option>
            {projects.map((p) => (
              <option key={p.id} value={p.id}>{p.name}</option>
            ))}
          </select>
        </div>

        <div className="flex items-end gap-2">
          <textarea
            ref={inputRef}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Mô tả công việc cần làm... (Enter để gửi, Shift+Enter xuống dòng)"
            rows={1}
            className="flex-1 resize-none rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-4 py-3 text-sm text-slate-800 dark:text-slate-200 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500/40"
            style={{ minHeight: "44px", maxHeight: "120px" }}
            onInput={(e) => {
              const el = e.currentTarget;
              el.style.height = "44px";
              el.style.height = Math.min(el.scrollHeight, 120) + "px";
            }}
          />
          <button
            disabled={busy || !input.trim()}
            onClick={() => void send()}
            className="shrink-0 w-10 h-10 rounded-xl bg-blue-600 text-white flex items-center justify-center hover:bg-blue-700 disabled:opacity-40 transition"
          >
            <AppIcon name="send" size={18} />
          </button>
        </div>
      </div>
    </div>
  );
}
