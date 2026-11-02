import { useEffect, useState } from "react";
import { apiFetch,unwrapPage } from "../api/client";
type Team = { id: string; name: string; version: number };
type Member = { userId: string; displayName: string; role: string };
export default function Teams() {
  const [workspace, setWorkspace] = useState(""); const [name, setName] = useState("");
  const [teams, setTeams] = useState<Team[]>([]); const [selected, setSelected] = useState<Team | null>(null);
  const [members, setMembers] = useState<Member[]>([]); const [user, setUser] = useState("");
  const [role, setRole] = useState("MEMBER"); const [error, setError] = useState(""); const [busy, setBusy] = useState(false);
  const [taskId,setTaskId]=useState("");const [taskVersion,setTaskVersion]=useState("");
  const [pending,setPending]=useState<{id:string;title:string;is_primary:boolean}[]>([]);
  const [workspaces,setWorkspaces]=useState<{id:string;name:string}[]>([]);
  const [candidates,setCandidates]=useState<Member[]>([]);
  const [page,setPage]=useState(0),[memberPage,setMemberPage]=useState(0),[candidatePage,setCandidatePage]=useState(0);
  const [taskOptions,setTaskOptions]=useState<{id:string;title:string;version:number}[]>([]);
  const [taskCursor,setTaskCursor]=useState<string|null>(null);
  useEffect(()=>{void run(async()=>{let cursor:string|null=null;const rows:{id:string;name:string}[]=[];do{const p:{items:{id:string;name:string}[];nextCursor:string|null}=unwrapPage<{id:string;name:string}>(await apiFetch("/workspaces",{query:{cursor:cursor??undefined,limit:100}}));rows.push(...p.items);cursor=p.nextCursor;}while(cursor);setWorkspaces(rows);});},[]);
  async function loadCandidates(p:number){const r=await apiFetch<{data:Member[]}>(`/workspaces/${workspace}/team-candidates`,{query:{page:p,size:20}});setCandidates(r.data);setCandidatePage(p);setUser("");}
  async function run(action: () => Promise<void>) { setBusy(true); setError(""); try { await action(); } catch { setError("Không thực hiện được. Kiểm tra quyền và tải lại dữ liệu nếu có xung đột."); } finally { setBusy(false); } }
  async function loadTaskOptions(cursor?:string|null){const tasks=unwrapPage<{id:string;title:string;version:number}>(await apiFetch("/tasks",{query:{limit:20,cursor:cursor??undefined}}));setTaskOptions(old=>cursor?[...old,...tasks.items]:tasks.items);setTaskCursor(tasks.nextCursor);}
  async function load(p=0) { const r = await apiFetch<{data: Team[]}>(`/workspaces/${workspace}/teams`, {query: {page:p,size:20}}); setTeams(r.data);setPage(p); setSelected(null); setMembers([]);await loadCandidates(0);await loadTaskOptions(); }
  async function select(team: Team,p=0) { const r = await apiFetch<{data: Member[]}>(`/teams/${team.id}/members`, {query: {page:p,size:20}}); setSelected(team); setMembers(r.data);setMemberPage(p); }
  async function mutate(remove?: string) {
    if (!selected) return;
    const r = await apiFetch<{data: Team}>(`/teams/${selected.id}/members${remove ? `/${remove}` : ""}`, { method: remove ? "DELETE" : "POST", ifMatch: String(selected.version), ...(remove ? {} : {body: {userId: user, role}}) });
    setTeams(old => old.map(t => t.id === r.data.id ? r.data : t)); await select(r.data); setUser("");
  }
  return <section className="personal-tasks teams-page mx-auto max-w-3xl space-y-4"><h1 className="page-title">Nhóm trong Workspace</h1>
    <p>Quản lý thành viên và kiểm tra công việc cần chuyển giao trước khi rời nhóm.</p>
    <form onSubmit={e => { e.preventDefault(); void run(()=>load()); }}><label>Workspace<select required value={workspace} onChange={e => {setWorkspace(e.target.value);setSelected(null);setTeams([]);setMembers([]);setCandidates([]);}}><option value="">Chọn Workspace</option>{workspaces.map(w=><option key={w.id} value={w.id}>{w.name}</option>)}</select></label><button disabled={busy}>Tải nhóm</button></form>
    <form onSubmit={e => { e.preventDefault(); void run(async () => { await apiFetch(`/workspaces/${workspace}/teams`, {body: {name}}); setName(""); await load(); }); }}><label>Tên nhóm<input required maxLength={200} value={name} onChange={e => setName(e.target.value)} /></label><button disabled={busy || !workspace}>Tạo nhóm</button></form>
    {error && <p role="alert">{error}</p>}{busy && <p role="status">Đang xử lý…</p>}
    {workspace&&<><label>Chọn thành viên<select value={user} onChange={e=>setUser(e.target.value)}><option value="">Chọn thành viên</option>{candidates.map(m=><option key={m.userId} value={m.userId}>{m.displayName} ({m.role})</option>)}</select></label><button disabled={busy||candidatePage===0} onClick={()=>void run(()=>loadCandidates(candidatePage-1))}>Trang thành viên trước</button><button disabled={busy||candidates.length<20} onClick={()=>void run(()=>loadCandidates(candidatePage+1))}>Trang thành viên sau</button></>}
    {selected&&<><label>Chọn công việc<select value={taskId} onChange={e=>{const task=taskOptions.find(t=>t.id===e.target.value);setTaskId(task?.id??"");setTaskVersion(String(task?.version??""));}}><option value="">Chọn công việc</option>{taskOptions.map(task=><option key={task.id} value={task.id}>{task.title}</option>)}</select></label>{taskCursor&&<button disabled={busy} onClick={()=>void run(()=>loadTaskOptions(taskCursor))}>Tải thêm công việc</button>}</>}
    {selected && <section className="card"><h2>Liên kết công việc vào nhóm</h2><p>Chọn công việc ở trên rồi liên kết với nhóm. Người đang được giao phải thuộc nhóm.</p><form onSubmit={e=>{e.preventDefault();void run(async()=>{const current=await apiFetch<{data:{version:number}}>(`/tasks/${taskId}`);await apiFetch(`/tasks/${taskId}/team`,{method:"PUT",ifMatch:String(current.data.version),body:{teamId:selected.id}});setTaskId("");setTaskVersion("");});}}><button disabled={busy||!taskId||!taskVersion}>Liên kết công việc đã chọn</button></form><form onSubmit={e=>{e.preventDefault();void run(async()=>{const r=await apiFetch<{data:typeof pending}>(`/teams/${selected.id}/members/${user}/tasks`);setPending(r.data);});}}><button disabled={busy||!user}>Kiểm tra việc cần chuyển giao của thành viên đã chọn</button></form><ul>{pending.map(task=><li key={task.id}><a href={`/tasks/${task.id}`}>{task.title}</a> {task.is_primary?"— cần chuyển giao người phụ trách":"— cộng tác"}</li>)}</ul></section>}
    <ul>{teams.map(team => <li key={team.id}><button disabled={busy} onClick={() => void run(() => select(team))}>{team.name}</button></li>)}</ul>
    <button disabled={busy||page===0} onClick={()=>void run(()=>load(page-1))}>Nhóm trước</button><button disabled={busy||teams.length<20} onClick={()=>void run(()=>load(page+1))}>Nhóm sau</button>
    {selected&&<><button disabled={busy||memberPage===0} onClick={()=>void run(()=>select(selected,memberPage-1))}>Thành viên trước</button><button disabled={busy||members.length<20} onClick={()=>void run(()=>select(selected,memberPage+1))}>Thành viên sau</button></>}
    {selected && <><h2>{selected.name}</h2><form onSubmit={e => { e.preventDefault(); void run(() => mutate()); }}><label>Vai trò của thành viên đã chọn<select value={role} onChange={e => setRole(e.target.value)}>{["MEMBER", "MANAGER", "GUEST"].map(r => <option key={r}>{r}</option>)}</select></label><button disabled={busy||!user}>Thêm / đổi vai trò</button></form><ul>{members.map(m => <li key={m.userId}>{m.displayName} — {m.role} <button disabled={busy} onClick={() => void run(() => mutate(m.userId))}>Xóa khỏi nhóm</button></li>)}</ul></>}
  </section>;
}
