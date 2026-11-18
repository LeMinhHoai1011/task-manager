import {useState} from "react";
import {useAccess} from "../auth/AccessContext";
import {apiFetch} from "../api/client";
import ResourcePicker from "../components/ResourcePicker";
import MemberPicker from "../components/MemberPicker";
import {useEffect} from "react";
export default function ProjectRoles(){
  const access=useAccess();
  const [project,setProject]=useState("");
  const [target,setTarget]=useState("");
  const [name,setName]=useState("Điều phối viên"),[metadata,setMetadata]=useState(false),[assignment,setAssignment]=useState(false);
  const [message,setMessage]=useState(""),[busy,setBusy]=useState(false);
  useEffect(()=>{let stale=false;setMetadata(false);setAssignment(false);setName("Thành viên");if(project&&target)void apiFetch<{data:{name:string;permissions:string[]}}>(`/projects/${project}/members/${target}/custom-role`).then(r=>{if(!stale){setName(r.data.name);setMetadata(r.data.permissions.includes("TASK_METADATA_WRITE"));setAssignment(r.data.permissions.includes("TASK_ASSIGNMENT_WRITE"));}}).catch(()=>{if(!stale)setMessage("Không có quyền xem vai trò tùy chỉnh");});return()=>{stale=true;};},[project,target]);
  async function save(revoke=false){setBusy(true);setMessage("");try{await apiFetch(`/projects/${project}/members/${target}/custom-role`,{method:"PUT",body:{name,permissions:revoke?[]:[...(metadata?["TASK_METADATA_WRITE"]:[]),...(assignment?["TASK_ASSIGNMENT_WRITE"]:[])]}});setMessage(revoke?"Đã thu hồi vai trò tùy chỉnh":"Đã lưu vai trò tùy chỉnh");}catch(e:any){setMessage(e.uiMessage??"Không có quyền thay đổi vai trò hoặc dữ liệu không hợp lệ");}finally{setBusy(false);}}
  if(!access.loading&&!access.projects.some(p=>p.canManage))return <section className="card"><h1>Phân quyền dự án</h1><p>Chỉ quản lý dự án được cấp và thu hồi quyền riêng cho thành viên. Bạn không quản lý dự án đang hoạt động nào.</p></section>;
  return <section className="personal-tasks mx-auto max-w-3xl space-y-4"><h1 className="page-title">Vai trò tùy chỉnh theo dự án</h1><p>Quản lý dự án có thể cấp quyền sửa công việc hoặc giao việc cho thành viên. Quyền duyệt và quản lý vai trò được kiểm tra riêng.</p><ResourcePicker kind="projects" label="Dự án" value={project} disabled={busy} onChange={v=>{setProject(v);setTarget("");}} allowedIds={access.projects.filter(p=>p.canManage).map(p=>p.id)}/><MemberPicker key={project} label="Thành viên" projectIds={project?[project]:[]} value={target} onChange={setTarget} disabled={!project||busy} memberOnly/><label>Tên vai trò<input maxLength={100} value={name} onChange={e=>setName(e.target.value)}/></label><label><input type="checkbox" checked={metadata} onChange={e=>setMetadata(e.target.checked)}/>Sửa thông tin công việc</label><label><input type="checkbox" checked={assignment} onChange={e=>setAssignment(e.target.checked)}/>Giao và chuyển giao công việc</label><div className="flex gap-3"><button disabled={busy||!target||!name.trim()} onClick={()=>void save()}>Lưu vai trò</button><button disabled={busy||!target} onClick={()=>void save(true)}>Thu hồi vai trò</button></div>{message&&<p role="status">{message}</p>}</section>;
}
