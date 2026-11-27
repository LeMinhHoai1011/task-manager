import {useCallback, useEffect, useState} from "react";
import {Link} from "react-router-dom";
import {apiFetch, unwrapData} from "../api/client";
import {useRealtimeEvent} from "../realtime/RealtimeContext";

export const attentionFilters = {
  overdue: "Quá hạn",
  blocked: "Đang bị chặn",
  approval: "Chờ duyệt",
  unassigned: "Chưa có người phụ trách chính",
} as const;

export default function ProjectAttention({projectId}: {projectId: string}) {
  const [revision, setRevision] = useState(0);
  const [counts, setCounts] = useState<Record<string, number> | null>(null);
  const [error, setError] = useState(false);
  const refresh = useCallback(() => setRevision(value => value + 1), []);
  useRealtimeEvent(["task", "workflow"], refresh);

  useEffect(() => {
    const request = new AbortController();
    setCounts(null);
    setError(false);
    Promise.all(Object.keys(attentionFilters).map(async attention => {
      const response = await apiFetch("/tasks", {
        query: {project_id: projectId, attention, limit: 1},
        signal: request.signal,
      });
      const total = unwrapData<{total: number}>(response)?.total;
      if (typeof total !== "number") throw new Error("Missing count");
      return [attention, total] as const;
    })).then(entries => {
      if (!request.signal.aborted) setCounts(Object.fromEntries(entries));
    }).catch(() => {
      if (!request.signal.aborted) setError(true);
    });
    return () => request.abort();
  }, [projectId, revision]);

  return <section className="card p-5">
    <h2 className="section-title">Cần xử lý</h2>
    {error ? <p role="alert">Không tải được số liệu. <button onClick={refresh}>Thử lại</button></p>
      : !counts ? <p role="status">Đang tải số liệu…</p>
      : <ul className="mt-3 grid gap-3 sm:grid-cols-2">
        {Object.entries(attentionFilters).map(([key, label]) => <li key={key}>
          <Link className="flex justify-between gap-2 rounded border p-3 hover:underline"
            to={`/tasks?project_id=${projectId}&attention=${key}`}>
            <span>{label}</span><strong>{counts[key]}</strong>
          </Link>
        </li>)}
      </ul>}
  </section>;
}
