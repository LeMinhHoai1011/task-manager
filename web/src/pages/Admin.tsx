import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { apiFetch, mutationOpts, newIdempotencyKey, unwrapData, unwrapList } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { Button, EmptyState, ErrorAlert, Spinner } from "../components/ui";
type Account = {
    user_id: string;
    username: string;
    email: string;
    display_name: string;
    status: string;
    locked: boolean;
    locked_until: string | null;
    protected_account: boolean;
    must_change_password: boolean;
    created_at: string;
};
type Directory = {
    content: Account[];
    totalElements: number;
    totalPages: number;
};
type Action = "approve" | "reject" | "lock" | "unlock" | "password";
const statusLabel: Record<string, string> = { ACTIVE: "Đang hoạt động", PENDING: "Chờ duyệt", REJECTED: "Đã từ chối", LOCKED: "Đang khóa" };
const blank = { username: "", email: "", display_name: "", temporary_password: "" };
export default function Admin() {
    const { user } = useAuth();
    const [tab, setTab] = useState("accounts"), [query, setQuery] = useState(""), [search, setSearch] = useState(""), [status, setStatus] = useState(""), [page, setPage] = useState(0);
    const [directory, setDirectory] = useState<Directory>({ content: [], totalElements: 0, totalPages: 0 });
    const [loading, setLoading] = useState(true), [error, setError] = useState<string | null>(null), [notice, setNotice] = useState<string | null>(null), [revision, setRevision] = useState(0);
    const [selected, setSelected] = useState<Account | null>(null), [action, setAction] = useState<Action | null>(null), [password, setPassword] = useState(""), [busy, setBusy] = useState(false);
    const [creating, setCreating] = useState(false), [form, setForm] = useState(blank);
    const [audit, setAudit] = useState<Record<string, unknown>[]>([]), [settings, setSettings] = useState<Record<string, unknown>[]>([]);
    const sending = useRef(false);
    useEffect(()=>{if(action)document.getElementById("account-action")?.focus();},[action]);
    useEffect(() => {
        let live = true;
        setLoading(true);
        setError(null);
        const request = tab === "accounts" ? apiFetch("/accounts/directory", { query: { q: search, status, page, size: 25 } }) : apiFetch(tab === "audit" ? "/admin/audit" : "/admin/settings");
        request.then(res => { if (!live)
            return; if (tab === "accounts")
            setDirectory(unwrapData<Directory>(res) ?? { content: [], totalElements: 0, totalPages: 0 });
        else if (tab === "audit")
            setAudit(unwrapList(res));
        else
            setSettings(unwrapList(res)); })
            .catch(e => { if (live)
            setError(e.uiMessage ?? "Không tải được dữ liệu. Hãy thử lại."); }).finally(() => { if (live)
            setLoading(false); });
        return () => { live = false; };
    }, [tab, search, status, page, revision]);
    async function submitAction() {
        if (!selected || !action || sending.current)
            return;
        sending.current = true;
        setBusy(true);
        setError(null);
        try {
            await apiFetch(`/accounts/${selected.user_id}/${action}`, { ...mutationOpts({ idempotencyKey: newIdempotencyKey() }), method: action === "password" ? "PUT" : "POST", body: action === "password" ? { temporary_password: password } : {} });
            setNotice(action === "password" ? "Đã đặt mật khẩu tạm và thu hồi các phiên đăng nhập. Người dùng phải đổi mật khẩu khi đăng nhập lại." : "Đã cập nhật tài khoản.");
            setAction(null);
            setPassword("");
            setRevision(n => n + 1);
        }
        catch (e: any) {
            setError(e.uiMessage ?? "Không thể cập nhật tài khoản.");
        }
        finally {
            sending.current = false;
            setBusy(false);
        }
    }
    async function create(event: React.FormEvent) {
        event.preventDefault();
        if (sending.current)
            return;
        sending.current = true;
        setBusy(true);
        setError(null);
        try {
            await apiFetch("/accounts", { ...mutationOpts({ idempotencyKey: newIdempotencyKey() }), method: "POST", body: form });
            setForm(blank);
            setCreating(false);
            setPage(0);
            setSearch("");
            setQuery("");
            setStatus("");
            setRevision(n => n + 1);
            setNotice("Đã tạo tài khoản. Hãy chuyển mật khẩu tạm cho người dùng qua kênh riêng; người dùng phải đổi mật khẩu khi đăng nhập.");
        }
        catch (e: any) {
            setError(e.uiMessage ?? "Không thể tạo tài khoản.");
        }
        finally {
            sending.current = false;
            setBusy(false);
        }
    }
    function choose(account: Account, next: Action) { setSelected(account); setAction(next); setPassword(""); setError(null); setNotice(null); }
    const actions: Record<Action, string> = { approve: "Duyệt tài khoản", reject: "Từ chối tài khoản", lock: "Khóa tài khoản", unlock: "Mở khóa tài khoản", password: "Đặt mật khẩu tạm" };
    return <div className="workspace-page">
    <header className="workspace-heading"><div><p className="eyebrow">QUẢN TRỊ HỆ THỐNG</p><h1>Quản lý tài khoản</h1><p>Tài khoản, quyền truy cập và lịch sử hoạt động trong cùng một nơi.</p></div><Button onClick={() => { setCreating(!creating); setAction(null); }}>+ Tạo tài khoản</Button></header>
    <nav className="workspace-tabs" aria-label="Nội dung quản trị">{[["accounts", "Tài khoản"], ["audit", "Nhật ký hoạt động"], ["settings", "Cấu hình hệ thống"]].map(([key, label]) => <button key={key} aria-current={tab === key ? "page" : undefined} onClick={() => { setTab(key); setAction(null); }}>{label}</button>)}</nav>
    <ErrorAlert message={error}/>{notice && <p role="status" className="success-notice">{notice}</p>}
    {creating && <form className="work-panel space-y-4" onSubmit={create}><h2>Tạo tài khoản mới</h2><p className="text-sm text-slate-500">Tài khoản mới có không gian cá nhân. Vai trò quản lý, nhân viên và khách được gán theo thành viên của từng nhóm / dự án.</p><div className="form-grid">{([['username', 'Tên đăng nhập', 'text'], ['display_name', 'Tên hiển thị', 'text'], ['email', 'Email', 'email'], ['temporary_password', 'Mật khẩu tạm', 'password']] as const).map(([key, label, type]) => <label key={key}>{label}<input className="input" required type={type} autoComplete={key === "temporary_password" ? "new-password" : "off"} minLength={key === "temporary_password" ? 8 : undefined} maxLength={key === "username" ? 50 : key === "display_name" ? 100 : key === "email" ? 254 : 72} value={form[key]} onChange={e => setForm({ ...form, [key]: e.target.value })}/></label>)}</div><div className="flex gap-2"><Button disabled={busy} type="submit">{busy ? "Đang lưu…" : "Tạo tài khoản"}</Button><Button variant="secondary" type="button" disabled={busy} onClick={() => { setCreating(false); setForm(blank); }}>Hủy</Button></div></form>}
    {tab === "accounts" && <section className="work-panel !p-0 overflow-hidden" aria-label="Danh sách tài khoản"><div className="directory-toolbar"><form className="flex flex-1 gap-2" onSubmit={e => { e.preventDefault(); setSearch(query); setPage(0); }}><input aria-label="Tìm tài khoản" className="input" placeholder="Tìm theo tên, tên đăng nhập hoặc email…" value={query} onChange={e => setQuery(e.target.value)}/><Button variant="secondary">Tìm</Button></form><select className="select" aria-label="Trạng thái tài khoản" value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}><option value="">Tất cả trạng thái</option>{Object.entries(statusLabel).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><span className="text-sm text-slate-500">{directory.totalElements} tài khoản</span></div>
      {loading ? <div className="p-6"><Spinner /></div> : <><div className="overflow-x-auto"><table className="table directory-table"><thead><tr><th>Tài khoản</th><th>Trạng thái</th><th>Ngày tạo</th><th>Thao tác</th></tr></thead><tbody>{directory.content.map(a => { const protectedAccount = a.protected_account || a.user_id === user?.id; return <tr key={a.user_id}><td><div className="flex items-center gap-3"><span className="person-avatar" aria-hidden="true">{(a.display_name || a.username).slice(0, 1).toUpperCase()}</span><div><strong>{a.display_name || a.username}</strong><div className="text-xs text-slate-500">@{a.username} · {a.email}</div>{a.must_change_password && <div className="text-xs text-amber-700">Cần đổi mật khẩu</div>}</div></div></td><td><span className={`status-pill ${a.locked ? 'status-paused' : a.status === 'ACTIVE' ? 'status-active' : 'status-pending'}`}>{statusLabel[a.locked ? "LOCKED" : a.status] ?? a.status}</span>{protectedAccount && <p className="mt-1 text-xs text-slate-500">{a.user_id === user?.id ? "Bạn" : "Được bảo vệ"}</p>}</td><td>{new Date(a.created_at).toLocaleDateString("vi-VN")}</td><td><div className="flex flex-wrap gap-2">{a.status === "PENDING" && <><Button size="sm" onClick={() => choose(a, "approve")}>Duyệt</Button><Button size="sm" variant="secondary" disabled={protectedAccount} onClick={() => choose(a, "reject")}>Từ chối</Button></>}{a.status === "ACTIVE" && <><Button size="sm" variant="secondary" disabled={protectedAccount} onClick={() => choose(a, a.locked ? "unlock" : "lock")}>{a.locked ? "Mở khóa" : "Khóa"}</Button><Button size="sm" variant="ghost" disabled={protectedAccount} onClick={() => choose(a, "password")}>Mật khẩu tạm</Button></>}</div></td></tr>; })}</tbody></table></div>{!directory.content.length && <EmptyState title="Không có tài khoản phù hợp"/>}<footer className="directory-toolbar"><span className="text-sm text-slate-500">Trang {page + 1} / {Math.max(directory.totalPages, 1)}</span><div className="flex gap-2"><Button variant="secondary" disabled={!page} onClick={() => setPage(n => n - 1)}>Trước</Button><Button variant="secondary" disabled={page + 1 >= directory.totalPages} onClick={() => setPage(n => n + 1)}>Sau</Button></div></footer></>}
      <div className="scope-explanation">Quyền quản trị hệ thống không tự cấp quyền đọc nội dung dự án. Quản lý vai trò thành viên trong <Link to="/workspaces">Không gian làm việc</Link> và từng dự án.</div></section>}
    {tab !== "accounts" && <section className="work-panel"><h2>{tab === "audit" ? "Nhật ký hoạt động" : "Cấu hình hệ thống"}</h2>{loading ? <Spinner /> : <div className="overflow-x-auto"><table className="table"><thead><tr>{(tab === "audit" ? ["Người thực hiện", "Hành động", "Kết quả", "Thời gian"] : ["Tên cấu hình", "Giá trị"]).map(label => <th key={label}>{label}</th>)}</tr></thead><tbody>{(tab === "audit" ? audit : settings).map((row, index) => <tr key={String(row.id ?? row.key ?? index)}>{tab === "audit" ? <><td className="break-all"><strong>{String(row.actor_display_name ?? row.actor_user_id ?? "Hệ thống")}</strong></td><td className="break-all">{String(row.action ?? "—")}</td><td><span className={`status-pill ${row.outcome === "ALLOWED" ? "status-active" : row.outcome === "DENIED" ? "status-paused" : "status-pending"}`}>{String(row.outcome ?? "—")}</span></td><td className="text-xs text-slate-500 whitespace-nowrap">{row.created_at ? new Date(String(row.created_at)).toLocaleString("vi-VN") : "—"}</td></> : <><td className="break-all">{String(row.key ?? "—")}</td><td className="break-all">{String(row.value ?? "—")}</td></>}</tr>)}</tbody></table>{!(tab === "audit" ? audit : settings).length && !error && <EmptyState title="Chưa có dữ liệu"/>}</div>}<Button className="mt-4" variant="secondary" onClick={() => setRevision(n => n + 1)}>Tải lại</Button></section>}
    {action && selected && <section id="account-action" tabIndex={-1} className="work-panel border-l-4 !border-l-primary" aria-label="Xác nhận thao tác tài khoản"><h2>{actions[action]} · {selected.username}</h2><p className="mt-2 text-sm text-slate-500">{action === "password" ? "Mật khẩu tạm do bạn chuyển cho người dùng qua kênh riêng. Hệ thống thu hồi các phiên đăng nhập và yêu cầu đổi mật khẩu." : action === "lock" ? "Người dùng sẽ bị thu hồi các phiên đăng nhập và không thể đăng nhập cho đến khi được mở khóa." : "Kiểm tra tài khoản trước khi xác nhận thao tác."}</p><form onSubmit={e => { e.preventDefault(); void submitAction(); }}>{action === "password" && <label className="mt-3 block max-w-md">Mật khẩu tạm<input className="input" type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={password} onChange={e => setPassword(e.target.value)}/></label>}<div className="mt-4 flex gap-2"><Button type="submit" disabled={busy}>{busy ? "Đang xử lý…" : "Xác nhận"}</Button><Button type="button" variant="secondary" disabled={busy} onClick={() => { setAction(null); setPassword(""); }}>Hủy</Button></div></form></section>}
  </div>;
}
