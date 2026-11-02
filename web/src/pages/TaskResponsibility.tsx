import { useEffect, useState } from "react";
import { apiFetch } from "../api/client";
type Candidate={userId:string;displayName:string};
export default function TaskResponsibility({ id, version, onChanged }: { id: string; version: string; onChanged: () => void }) {
  const [userId,setUserId]=useState(""); const [busy,setBusy]=useState(false); const [error,setError]=useState("");
  const [candidates,setCandidates]=useState<Candidate[]|null>(null); const [primary,setPrimary]=useState<string|null>(null);
  useEffect(()=>{let active=true;setCandidates(null);setError("");
    void apiFetch<{data:{primaryUserId:string|null}}>(`/tasks/${id}/primary-assignee`).then(r=>{if(active)setPrimary(r.data.primaryUserId);}).catch(()=>{if(active)setError("Không tải được người phụ trách.");});
    void apiFetch<{data:Candidate[]}>(`/tasks/${id}/assignment-candidates`).then(r=>{if(active)setCandidates(r.data);}).catch(()=>{if(active)setCandidates(null);});
    return()=>{active=false;};
  },[id,version]);
  return <section className="card space-y-3"><h2>Người chịu trách nhiệm chính</h2>
    <p>{primary ? candidates?.find(c=>c.userId===primary)?.displayName ?? primary : "Chưa chỉ định"}</p>
    {candidates && <><p>Người cũ vẫn là cộng tác viên sau chuyển giao.</p><form onSubmit={async e => {
      e.preventDefault();setBusy(true);setError("");
      try{await apiFetch(`/tasks/${id}/primary-assignee`,{method:"POST",ifMatch:version,body:{userId}});setUserId("");onChanged();}
      catch{setError("Không chuyển giao được. Hãy tải lại quyền và version hiện tại.");}finally{setBusy(false);}
    }}><label>Người phụ trách mới<select required value={userId} onChange={e=>setUserId(e.target.value)}><option value="">Chọn thành viên đủ quyền</option>{candidates.map(c=><option key={c.userId} value={c.userId}>{c.displayName}</option>)}</select></label><button className="btn btn-secondary" disabled={busy||!userId}>Chuyển giao</button></form></>}
    {error&&<p role="alert">{error}</p>}
  </section>;
}
