import {useCallback, useEffect, useState} from "react";
import {Link} from "react-router-dom";
import {apiFetch, unwrapData} from "../api/client";
import {useRealtimeEvent} from "../realtime/RealtimeContext";
import {Card, StatusBadge} from "./ui";

const buckets = [
  ["overdue", "Quá hạn"],
  ["today", "Hôm nay"],
  ["upcoming", "7 ngày tới"],
] as const;

type TodayPage = {
  items: {
    id: string;
    title: string;
    scope: string;
    state: string;
    due_at: string;
    due_date_only?: string;
    project_name?: string;
    responsibility?: string;
    blocked_flag: boolean;
  }[];
  has_more: boolean;
};

export default function TodayTasks() {
  const [bucket, setBucket] = useState<(typeof buckets)[number][0]>("today");
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [data, setData] = useState<TodayPage | null>(null);
  const [error, setError] = useState(false);
  const refresh = useCallback(() => setRevision(value => value + 1), []);
  useRealtimeEvent(["task", "workflow"], refresh);

  useEffect(() => {
    const request = new AbortController();
    setData(null);
    setError(false);
    apiFetch("/dashboard/today", {
      query: {bucket, page, timezone: Intl.DateTimeFormat().resolvedOptions().timeZone},
      signal: request.signal,
    }).then(response => {
      if (request.signal.aborted) return;
      const result = unwrapData<TodayPage>(response);
      if (!Array.isArray(result?.items)) throw new Error("Missing tasks");
      setData(result);
    }).catch(() => {
      if (!request.signal.aborted) setError(true);
    });
    return () => request.abort();
  }, [bucket, page, revision]);

  return <Card className="p-5">
    <h2 className="section-title">Hôm nay</h2>
    <div className="flex flex-wrap gap-2 my-3" role="group" aria-label="Khoảng hạn công việc">
      {buckets.map(([id, label]) => <button key={id} type="button"
        className={bucket === id ? "btn btn-primary" : "btn"} aria-pressed={bucket === id}
        onClick={() => {setBucket(id); setPage(0);}}>{label}</button>)}
    </div>
    {error ? <p role="alert">Không tải được công việc. <button onClick={refresh}>Thử lại</button></p>
      : !data ? <p role="status">Đang tải công việc…</p>
      : data.items.length === 0 ? <p>Không có việc trong khoảng hạn này.</p>
      : <ul className="divide-y divide-slate-200 dark:divide-slate-800">
        {data.items.map(task => <li className="flex items-center justify-between gap-3 py-3" key={task.id}>
          <div>
            <Link className="font-semibold hover:underline"
              to={task.scope === "PERSONAL" ? `/my-tasks/${task.id}` : `/tasks/${task.id}`}>{task.title}</Link>
            <p className="text-sm">
              {task.scope === "PERSONAL" ? "Việc riêng" : `${task.project_name ?? "Dự án"} · ${task.responsibility === "PRIMARY" ? "Phụ trách chính" : "Phối hợp"}`}
              {" · "}{task.due_date_only ?? new Date(task.due_at).toLocaleString("vi-VN")}
            </p>
          </div>
          <StatusBadge state={task.blocked_flag ? "BLOCKED" : task.state} />
        </li>)}
      </ul>}
    {(page > 0 || data?.has_more) && <div className="flex gap-2 mt-3">
      <button className="btn" disabled={page === 0} onClick={() => setPage(page - 1)}>Trang trước</button>
      <button className="btn" disabled={!data?.has_more} onClick={() => setPage(page + 1)}>Trang sau</button>
    </div>}
  </Card>;
}
