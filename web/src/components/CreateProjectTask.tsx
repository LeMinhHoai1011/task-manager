import {useEffect,useRef,useState} from "react";
import {useAccess} from "../auth/AccessContext";
import {Link} from "react-router-dom";
import ResourcePicker from "./ResourcePicker";
import {priorityLabel} from "./ui";
import {apiFetch,newIdempotencyKey} from "../api/client";
export default function CreateProjectTask({onCreated,projectId,projectName,initialOpen=false}:{onCreated:()=>void;projectId?:string;projectName?:string;initialOpen?:boolean}){
  const access=useAccess();
  const [open,setOpen]=useState(initialOpen),[project,setProject]=useState(""),[title,setTitle]=useState(""),[description,setDescription]=useState(""),[priority,setPriority]=useState("MEDIUM"),[due,setDue]=useState("");
  const selectedProject=projectId||project;
  const [busy,setBusy]=useState(false),[message,setMessage]=useState(""),[created,setCreated]=useState("");
  const pending=useRef<{signature:string;key:string}|null>(null);
  useEffect(()=>{if(initialOpen)setOpen(true);},[initialOpen]);
  async function save(){if(busy||!selectedProject||!title.trim())return;setBusy(true);setMessage("");try{const body={project_id:selectedProject,title:title.trim(),description,priority,...(due?{due_at:new Date(due).toISOString(),due_timezone:Intl.DateTimeFormat().resolvedOptions().timeZone}:{})};const signature=JSON.stringify(body);if(pending.current?.signature!==signature)pending.current={signature,key:newIdempotencyKey()};const r=await apiFetch<{data:{id:string}}>("/tasks",{method:"POST",idempotencyKey:pending.current.key,body});setCreated(r.data.id);setTitle("");setDescription("");setDue("");pending.current=null;setMessage("Đã tạo công việc");onCreated();}catch(e:any){setMessage(e.uiMessage??"Không tạo được công việc. Có thể thử lại với cùng nội dung.");}finally{setBusy(false);}}
  return <section className="personal-tasks teams-page my-4">
    <button type="button" className="btn btn-primary" aria-expanded={open} onClick={()=>setOpen(v=>!v)}>{open?"Đóng tạo công việc":"Tạo công việc dự án"}</button>
    {open&&<form className="card mt-3 create-task-form" onSubmit={e=>{e.preventDefault();void save();}}>
      <h2>Tạo công việc dự án</h2><p className="text-sm text-slate-500">Nhập tên công việc để bắt đầu. Bạn có thể bổ sung thông tin sau.</p>
      {projectId?<p className="text-sm" role="note">Dự án hiện tại: <strong>{projectName||access.projects.find(p=>p.id===projectId)?.name||"Dự án đang mở"}</strong></p>:<ResourcePicker kind="projects" required label="Dự án giao việc" disabled={busy} value={project} onChange={setProject} allowedIds={access.projects.filter(p=>p.canCreateTask).map(p=>p.id)}/>}
      <label>Tiêu đề công việc dự án *<input required disabled={busy} maxLength={200} placeholder="Ví dụ: Hoàn thiện trang đăng nhập" value={title} onChange={e=>setTitle(e.target.value)}/></label>
      <label>Hạn hoàn thành (không bắt buộc)<input disabled={busy} type="datetime-local" value={due} onChange={e=>setDue(e.target.value)}/></label>
      <details className="form-more"><summary>Thông tin thêm</summary><label>Mô tả công việc<textarea disabled={busy} value={description} onChange={e=>setDescription(e.target.value)}/></label><label>Ưu tiên<select disabled={busy} value={priority} onChange={e=>setPriority(e.target.value)}>{["LOW","MEDIUM","HIGH","URGENT"].map(p=><option key={p} value={p}>{priorityLabel(p)}</option>)}</select></label></details>
      <button className="btn btn-primary" disabled={busy||!selectedProject||!title.trim()}>{busy?"Đang tạo…":"Tạo công việc"}</button>
    </form>}
    {message&&<p role="status">{message} {created&&<Link to={`/tasks/${created}`}>Mở chi tiết</Link>}</p>}
  </section>;
}
