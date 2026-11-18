import {createContext,useContext,useEffect,useState,type ReactNode} from "react";
import {apiFetch,unwrapData} from "../api/client";
import {useAuth} from "./AuthContext";
import {useRealtimeEvent} from "../realtime/RealtimeContext";

export type WorkspaceAccess={id:string;name:string;role:string;status:string;canManage:boolean;canCreateProject:boolean;type?:string};
export type ProjectAccess={id:string;name:string;role:string;status:string;canManage:boolean;canCreateTask:boolean;canLogProgress:boolean;metadata:boolean;assignment:boolean};
export type TaskAccess={role:string;writable:boolean;metadata:boolean;assignment:boolean;structure:boolean;subtask:boolean;execute:boolean;comment:boolean;review:boolean;workflowActions:string[];files:boolean};
type Access={systemAdmin:boolean;workspaces:WorkspaceAccess[];projects:ProjectAccess[]};
export function interfaceProfile(access:Access) {
  if(access.systemAdmin)return {id:"admin",label:"Quản trị hệ thống",home:"/admin",description:"Quản lý tài khoản, phân quyền hệ thống và nhật ký. Quyền dự án được cấp riêng."};
  if(access.projects.some(p=>["OWNER","ADMIN","MANAGER"].includes(p.role))||access.workspaces.some(w=>w.type==="TEAM"&&["OWNER","ADMIN"].includes(w.role)))
    return {id:"manager",label:"Quản lý",home:"/dashboard",description:"Lập kế hoạch, giao việc, theo dõi tiến độ và duyệt kết quả trong phạm vi được giao."};
  if(access.projects.some(p=>p.role==="MEMBER")||access.workspaces.some(w=>w.type==="TEAM"&&w.role==="MEMBER"))
    return {id:"employee",label:"Nhân viên",home:"/tasks",description:"Xử lý việc được giao, cập nhật tiến độ và nộp kết quả cho quản lý."};
  if(access.projects.length||access.workspaces.some(w=>w.type==="TEAM"))
    return {id:"guest",label:"Khách chỉ đọc",home:"/projects",description:"Theo dõi dự án và công việc được chia sẻ. Liên hệ quản lý khi cần thay đổi."};
  return {id:"user",label:"Người dùng",home:"/my-tasks",description:"Quản lý công việc, ghi chú và lịch cá nhân. Chỉ thấy dự án khi được cấp quyền."};
}
const empty:Access={systemAdmin:false,workspaces:[],projects:[]};
const Context=createContext<Access&{loading:boolean;error:string;reload:()=>void}>({...empty,loading:true,error:"",reload:()=>{}});
export function AccessProvider({children}:{children:ReactNode}){
  const {user,token}=useAuth();
  const [value,setValue]=useState<Access>(empty),[loading,setLoading]=useState(true),[error,setError]=useState("");
  const [resolvedFor,setResolvedFor]=useState<string|null>(null);
  const [revision,setRevision]=useState(0);
  const reload=()=>setRevision(n=>n+1);
  useRealtimeEvent(["workspace","project"],reload);
  useEffect(()=>{
    let active=true;setValue(empty);setError("");setLoading(!!token);
    if(token)void apiFetch("/me/access").then(r=>{if(active)setValue(unwrapData<Access>(r)??empty);})
      .catch(()=>{if(active)setError("Không tải được quyền. Hãy tải lại trước khi thao tác.");})
      .finally(()=>{if(active){setResolvedFor(user?.id??"");setLoading(false);}});
    return()=>{active=false;};
  },[user?.id,token,revision]);
  useEffect(()=>{const refresh=()=>reload();window.addEventListener("focus",refresh);return()=>window.removeEventListener("focus",refresh);},[]);
  const awaitingIdentity=!!token&&resolvedFor!==(user?.id??"");
  return <Context.Provider value={{...(awaitingIdentity?empty:value),loading:loading||awaitingIdentity,error,reload}}>{children}</Context.Provider>;
}
export const useAccess=()=>useContext(Context);
export const roleLabel=(role?:string)=>({OWNER:"Chủ sở hữu",ADMIN:"Quản trị Workspace",MANAGER:"Quản lý dự án",MEMBER:"Thành viên",GUEST:"Khách chỉ đọc",VIEWER:"Người xem"}[role??""]??"Chưa có quyền");
