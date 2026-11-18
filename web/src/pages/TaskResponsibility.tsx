import { useEffect, useRef, useState } from "react";
import SearchSelect from "../components/SearchSelect";
import { apiFetch } from "../api/client";
type Candidate={userId:string;displayName:string;username?:string};
export default function TaskResponsibility({ id, version, onChanged, canAssign=false }: { canAssign?:boolean; id: string; version: string; onChanged: () => void }) {
  const [userId,setUserId]=useState(""); const [busy,setBusy]=useState(false); const [error,setError]=useState("");
  const [candidates,setCandidates]=useState<Candidate[]|null>(null); const [primary,setPrimary]=useState<string|null>(null); const [primaryName,setPrimaryName]=useState("");
  const generation=useRef(0);
  useEffect(()=>{let active=true;generation.current++;setBusy(false);setCandidates(null);setPrimary(null);setPrimaryName("");setUserId("");setError("");
    void apiFetch<{data:{primaryUserId:string|null;primaryDisplayName?:string}}>(`/tasks/${id}/primary-assignee`).then(r=>{if(active){setPrimary(r.data.primaryUserId);setPrimaryName(r.data.primaryDisplayName??"");}}).catch(()=>{if(active)setError("Không tải được người phụ trách.");});
    return()=>{active=false;generation.current++;};
  },[id,version]);
  async function chooseAssignee(){
    if(busy||!canAssign)return;setBusy(true);setError("");
    const request=generation.current;
    try{const result=await apiFetch<{data:Candidate[]}>(`/tasks/${id}/assignment-candidates`);if(request===generation.current)setCandidates(result.data);}
    catch{if(request===generation.current){setCandidates(null);setError("Bạn không có quyền chuyển giao hoặc danh sách thành viên chưa tải được.");}}
    finally{if(request===generation.current)setBusy(false);}
  }
  return <section className="card space-y-3"><h2>Người chịu trách nhiệm chính</h2>
    <p>{primary ? primaryName||candidates?.find(c=>c.userId===primary)?.displayName||"Đã chỉ định người phụ trách" : "Chưa chỉ định"}</p>
    {canAssign&&!candidates&&<button className="btn btn-secondary" disabled={busy} onClick={()=>void chooseAssignee()}>{busy?"Đang tải...":"Chuyển giao người phụ trách"}</button>}
    {canAssign&&candidates && <><p>Người cũ vẫn là cộng tác viên sau chuyển giao.</p><form onSubmit={async e => {
      e.preventDefault();setBusy(true);setError("");
      try{await apiFetch(`/tasks/${id}/primary-assignee`,{method:"POST",ifMatch:version,body:{userId}});setUserId("");onChanged();}
      catch{setError("Không chuyển giao được. Hãy tải lại quyền và dữ liệu hiện tại.");}finally{setBusy(false);}
    }}><SearchSelect required label="Người phụ trách mới" disabled={busy} value={userId} onChange={setUserId} options={candidates.map(c=>({id:c.userId,name:c.displayName||c.username||"Thành viên",detail:c.username?`@${c.username}`:`Mã phân biệt: …${c.userId.slice(-8)}`}))}/><button className="btn btn-secondary" disabled={busy||!userId}>Chuyển giao</button></form></>}
    {error&&<p role="alert">{error}</p>}
  </section>;
}
