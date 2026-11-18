import {useEffect,useState} from "react";
import {apiFetch,unwrapData} from "../api/client";
import {roleLabel} from "../auth/AccessContext";
import SearchSelect,{type Choice} from "./SearchSelect";

export type ProjectMember={user_id:string;role:string;display_name?:string;username?:string};
type Candidate={userId:string;displayName:string;username?:string};
export default function MemberPicker({projectIds=[],taskIds=[],value,onChange,label="Người phụ trách",disabled,memberOnly=false}:{projectIds?:string[];taskIds?:string[];value:string;onChange:(id:string)=>void;label?:string;disabled?:boolean;memberOnly?:boolean}){
  const [options,setOptions]=useState<Choice[]>([]),[loading,setLoading]=useState(false),[error,setError]=useState(""),[revision,setRevision]=useState(0);
  const [requested,setRequested]=useState(false);
  const projectKey=[...new Set(projectIds)].sort().join(","),taskKey=[...new Set(taskIds)].sort().join(",");
  useEffect(()=>{
    if(!requested)return;
    let current=true;setOptions([]);setError("");setLoading(true);
    async function load(){try{
      const sources=taskKey?taskKey.split(","):projectKey?projectKey.split(","):[];
      const lists:Choice[][]=[];
      // Tuần tự để không tạo hàng trăm request đồng thời khi chọn nhiều công việc.
      for(const id of sources){
        if(!current)return;
        if(taskKey){const rows=unwrapData<Candidate[]>(await apiFetch(`/tasks/${id}/collaboration-candidates`))??[];lists.push(rows.map(row=>({id:row.userId,name:row.displayName||"Thành viên",detail:row.username?`@${row.username}`:`Mã phân biệt: …${row.userId.slice(-8)}`})));}
        else{const rows=unwrapData<ProjectMember[]>(await apiFetch(`/projects/${id}/members`))??[];lists.push(rows.filter(row=>!memberOnly||row.role==="MEMBER").map(row=>({id:row.user_id,name:row.display_name||row.username||"Thành viên",detail:`${row.username?`@${row.username} · `:`…${row.user_id.slice(-8)} · `}${roleLabel(row.role)}`})));}
      }
      if(current)setOptions(taskKey?(lists[0]??[]).filter(o=>lists.every(list=>list.some(candidate=>candidate.id===o.id))):[...new Map(lists.flat().map(o=>[o.id,o])).values()]);
    }catch{if(current)setError("Không tải được thành viên. Kiểm tra quyền và thử lại.");}finally{if(current)setLoading(false);}}
    void load();return()=>{current=false;};
  },[projectKey,taskKey,memberOnly,revision,requested]);
  return <SearchSelect label={label} value={value} onChange={onChange} onOpen={()=>setRequested(true)} options={options} loading={loading} disabled={disabled} error={error} onRetry={()=>setRevision(n=>n+1)} placeholder={taskKey?"Chọn người phù hợp với mọi công việc":"Chọn theo tên hoặc tên đăng nhập"}/>;
}
