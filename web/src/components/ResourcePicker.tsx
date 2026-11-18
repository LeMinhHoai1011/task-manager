import {useEffect,useRef,useState} from "react";
import {apiFetch,unwrapPage} from "../api/client";
import {useAccess} from "../auth/AccessContext";
import SearchSelect,{type Choice} from "./SearchSelect";

type Row={id:string;name?:string;title?:string;project_id?:string};
type Props={kind:"projects"|"tasks";label:string;value:string;onChange:(value:string)=>void;projectId?:string;workspaceId?:string;disabled?:boolean;required?:boolean;excludeId?:string;allowedIds?:string[];selectedName?:string;placeholder?:string};
export default function ResourcePicker({kind,label,value,onChange,projectId,workspaceId,disabled,required,excludeId,allowedIds,selectedName,placeholder}:Props){
  const access=useAccess();
  const [rows,setRows]=useState<Row[]>([]),[cursor,setCursor]=useState<string|null>(null),[loading,setLoading]=useState(false),[error,setError]=useState("");
  const generation=useRef(0);
  async function load(next?:string|null,request=generation.current){
    setLoading(true);setError("");
    try{const page=unwrapPage<Row>(await apiFetch(`/${kind}`,{query:{limit:20,cursor:next??undefined,project_id:projectId||undefined,workspace_id:workspaceId||undefined,...(kind==="tasks"?{sort:"updated_at",order:"desc"}:{})}}));
      if(request!==generation.current)return;
      setRows(old=>next?[...old,...page.items.filter(row=>!old.some(item=>item.id===row.id))]:page.items);setCursor(page.nextCursor);
    }catch{if(request===generation.current)setError("Không tải được danh sách lựa chọn.");}
    finally{if(request===generation.current)setLoading(false);}
  }
  useEffect(()=>{const request=++generation.current;setRows([]);setCursor(null);void load(null,request);return()=>{generation.current++;};},[kind,projectId,workspaceId]);
  const options:Choice[]=rows.filter(row=>row.id!==excludeId&&(!allowedIds||allowedIds.includes(row.id))).map(row=>({id:row.id,name:row.title??row.name??"Chưa đặt tên",detail:kind==="tasks"?access.projects.find(p=>p.id===row.project_id)?.name:undefined}));
  if(value&&!options.some(o=>o.id===value)&&selectedName)options.unshift({id:value,name:selectedName});
  return <SearchSelect label={label} value={value} onChange={onChange} options={options} placeholder={placeholder??(kind==="projects"?(required?"Chọn dự án":"Tất cả dự án"):"Chọn công việc")} required={required} disabled={disabled} loading={loading} error={error} onRetry={()=>void load()} onMore={cursor?()=>void load(cursor):undefined}/>;
}
