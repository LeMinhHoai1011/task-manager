import {createContext,useContext,useEffect,useState,type ReactNode} from "react";
import {apiFetch,unwrapData} from "../api/client";
import {useAuth} from "./AuthContext";
import {useRealtimeEvent} from "../realtime/RealtimeContext";

export type WorkspaceAccess={id:string;name:string;role:string;status:string;canManage:boolean;canCreateProject:boolean};
export type ProjectAccess={id:string;name:string;role:string;status:string;canManage:boolean;canCreateTask:boolean;canLogProgress:boolean;metadata:boolean;assignment:boolean};
export type TaskAccess={role:string;writable:boolean;metadata:boolean;assignment:boolean;structure:boolean;subtask:boolean;execute:boolean;comment:boolean;review:boolean;workflowActions:string[];files:boolean};
type Access={systemAdmin:boolean;workspaces:WorkspaceAccess[];projects:ProjectAccess[]};
const empty:Access={systemAdmin:false,workspaces:[],projects:[]};
const Context=createContext<Access&{loading:boolean;error:string;reload:()=>void}>({...empty,loading:true,error:"",reload:()=>{}});
export function AccessProvider({children}:{children:ReactNode}){
  const {user,token}=useAuth();
  const [value,setValue]=useState<Access>(empty),[loading,setLoading]=useState(true),[error,setError]=useState("");
  const [revision,setRevision]=useState(0);
  const reload=()=>setRevision(n=>n+1);
  useRealtimeEvent(["workspace","project"],reload);
  useEffect(()=>{
    let active=true;setValue(empty);setError("");setLoading(!!token);
    if(token)void apiFetch("/me/access").then(r=>{if(active)setValue(unwrapData<Access>(r)??empty);})
      .catch(()=>{if(active)setError("Không tải được quyền. Hãy tải lại trước khi thao tác.");})
      .finally(()=>{if(active)setLoading(false);});
    return()=>{active=false;};
  },[user?.id,token,revision]);
  useEffect(()=>{const refresh=()=>reload();window.addEventListener("focus",refresh);return()=>window.removeEventListener("focus",refresh);},[]);
  return <Context.Provider value={{...value,loading,error,reload}}>{children}</Context.Provider>;
}
export const useAccess=()=>useContext(Context);
export const roleLabel=(role?:string)=>({OWNER:"Chủ sở hữu",ADMIN:"Quản trị Workspace",MANAGER:"Quản lý dự án",MEMBER:"Thành viên",GUEST:"Khách chỉ đọc",VIEWER:"Người xem"}[role??""]??"Chưa có quyền");
