import { useCallback, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { apiFetch, unwrapPage } from "../api/client";
import { t } from "../i18n";
import { useRealtimeEvent } from "../realtime/RealtimeContext";
import { Badge, Button, Card, EmptyState, ErrorAlert, Select, Spinner, TextInput } from "../components/ui";

// Filter: state/project/assignee/tag/blocked/depth/overdue + sort allowlist + cursor.
const SORT_ALLOW = ["created_at", "updated_at", "due_at", "priority"] as const;
type SortKey = (typeof SORT_ALLOW)[number];

const STATE_OPTIONS = ["TODO", "IN_PROGRESS", "PENDING_APPROVAL", "COMPLETED", "CANCELLED"] as const;

interface TaskRow {
  id: string;
  title: string;
  state: string;
  project_id?: string;
  blocked?: boolean;
  blocked_flag?: boolean;
  deadline?: string | null;
  due_at?: string | null;
}

export default function Tasks() {
  const [params] = useSearchParams();
  const [state, setState] = useState("");
  const [project, setProject] = useState(() => params.get("project") ?? "");
  const [assignee, setAssignee] = useState("");
  const [tag, setTag] = useState("");
  const [blocked, setBlocked] = useState("");
  const [depth, setDepth] = useState("");
  const [overdue, setOverdue] = useState("");
  const [sort, setSort] = useState<SortKey>("created_at");
  const [order, setOrder] = useState<"asc" | "desc">("desc");
  const [cursor, setCursor] = useState<string | null>(null);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [items, setItems] = useState<TaskRow[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(
    async (c: string | null) => {
      setErr(null);
      setLoading(true);
      try {
        // Giữ nguyên sort+order hiện tại: backend hỗ trợ legacy sort+order
        // (§7 unified sort) nên không đổi contract.
        const r = await apiFetch("/tasks", {
          query: {
            state: state || undefined,
            project_id: project || undefined,
            assignee_id: assignee || undefined,
            tag: tag || undefined,
            blocked: blocked || undefined,
            depth: depth || undefined,
            overdue: overdue || undefined,
            // sort allowlist: chỉ gửi key hợp lệ.
            sort: (SORT_ALLOW as readonly string[]).includes(sort) ? sort : "created_at",
            order,
            cursor: c || undefined,
          },
        });
        // Envelope canonical: data.items (+fallback legacy data.data).
        const page = unwrapPage<TaskRow>(r);
        setItems((prev) => (c ? [...prev, ...page.items] : page.items));
        setNextCursor(page.nextCursor);
      } catch (e: any) {
        setErr(e?.uiMessage ?? "Error");
      } finally {
        setLoading(false);
      }
    },
    [state, project, assignee, tag, blocked, depth, overdue, sort, order]
  );

  // Realtime: signal task/workflow -> refetch đúng scope qua API (không tin payload).
  useRealtimeEvent(["task", "workflow"], () => {
    void load(null);
  });

  useEffect(() => {
    setCursor(null);
    void load(null);
  }, [load]);

  const grouped = items.reduce<Record<string, TaskRow[]>>((acc, it) => {
    (acc[it.state] ??= []).push(it);
    return acc;
  }, {});

  return (
    <div className="mx-auto max-w-6xl">
      <h1 className="page-title">{t("nav.tasks")}</h1>
      <div className="toolbar-sticky filters">
        <Select aria-label={t("tasks.filter_state")} value={state} onChange={(e) => setState(e.target.value)}>
          <option value="">{`${t("tasks.filter_state")}: ${t("common.all")}`}</option>
          {STATE_OPTIONS.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </Select>
        <TextInput className="!w-32" placeholder={t("tasks.filter_project")} aria-label={t("tasks.filter_project")} value={project} onChange={(e) => setProject(e.target.value)} />
        <TextInput className="!w-32" placeholder={t("tasks.filter_assignee")} aria-label={t("tasks.filter_assignee")} value={assignee} onChange={(e) => setAssignee(e.target.value)} />
        <TextInput className="!w-28" placeholder={t("tasks.filter_tag")} aria-label={t("tasks.filter_tag")} value={tag} onChange={(e) => setTag(e.target.value)} />
        <Select aria-label={t("tasks.filter_blocked")} value={blocked} onChange={(e) => setBlocked(e.target.value)}>
          <option value="">{`${t("tasks.filter_blocked")} ${t("common.all")}`}</option>
          <option value="true">{t("common.yes")}</option>
          <option value="false">{t("common.no")}</option>
        </Select>
        <Select aria-label={t("tasks.filter_depth")} value={depth} onChange={(e) => setDepth(e.target.value)}>
          <option value="">{`${t("tasks.filter_depth")}: ${t("common.all")}`}</option>
          <option value="0">{t("tasks.filter_depth_top")}</option>
          <option value="1">{t("tasks.filter_depth_sub")}</option>
        </Select>
        <Select aria-label={t("tasks.filter_overdue")} value={overdue} onChange={(e) => setOverdue(e.target.value)}>
          <option value="">{`${t("tasks.filter_overdue")} ${t("common.all")}`}</option>
          <option value="true">{t("common.yes")}</option>
          <option value="false">{t("common.no")}</option>
        </Select>
        <Select value={sort} onChange={(e) => setSort(e.target.value as SortKey)}>
          {SORT_ALLOW.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </Select>
        <Select value={order} onChange={(e) => setOrder(e.target.value as "asc" | "desc")}>
          <option value="asc">asc</option>
          <option value="desc">desc</option>
        </Select>
      </div>
      {err && items.length > 0 && <ErrorAlert message={err} />}
      {loading && items.length === 0 ? (
        <Spinner />
      ) : err && items.length === 0 ? (
        <Card className="text-center">
          <ErrorAlert message={err} />
          <div className="mt-3 flex justify-center">
            <Button variant="secondary" onClick={() => void load(null)}>
              {t("common.retry")}
            </Button>
          </div>
        </Card>
      ) : items.length === 0 ? (
        <EmptyState title={t("tasks.empty_title")} description={t("tasks.empty_desc")} />
      ) : (
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {Object.entries(grouped).map(([st, rows]) => (
            <Card key={st} className="!p-3">
              <div className="mb-2 flex items-center justify-between">
                <Badge state={st}>{st}</Badge>
                <span className="text-xs text-slate-500">{rows.length}</span>
              </div>
              <ul className="flex flex-col gap-1.5">
                {rows.map((it) => {
                  const isBlocked = it.blocked ?? it.blocked_flag ?? false;
                  const due = it.deadline ?? it.due_at;
                  return (
                  <li key={it.id} className="rounded-lg border border-slate-100 bg-slate-50 px-2.5 py-2 hover:border-blue-200 hover:bg-blue-50/50">
                    <Link to={`/tasks/${it.id}`} className="font-medium text-slate-900 hover:text-primary">
                      {it.title}
                    </Link>
                    <div className="mt-1 flex items-center gap-1.5 text-xs text-slate-500">
                      <Badge state={it.state}>{it.state}</Badge>
                      {isBlocked && <Badge state="BLOCKED">{t("task.blocked")}</Badge>}
                      {due && <span>{String(due).slice(0, 10)}</span>}
                    </div>
                  </li>
                  );
                })}
              </ul>
            </Card>
          ))}
        </div>
      )}
      {nextCursor && (
        <div className="mt-4 flex justify-center">
          <Button
            variant="secondary"
            onClick={() => {
              setCursor(nextCursor);
              void load(nextCursor);
            }}
          >
            {t("common.more")}
          </Button>
        </div>
      )}
      {cursor && <p className="mt-2 hidden text-xs text-slate-400">{cursor}</p>}
    </div>
  );
}
