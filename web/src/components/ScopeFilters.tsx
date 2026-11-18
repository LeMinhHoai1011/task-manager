import {useEffect,useRef,useState} from "react";
import SearchSelect from "./SearchSelect";
import {apiFetch,unwrapPage} from "../api/client";
type Resource={id:string;name:string;workspace_id?:string};
type Props={scope:string;projectId:string;teamId:string;onChange:(scope:string,project:string,team:string)=>void};
export default function ScopeFilters({scope,projectId,teamId,onChange}:Props){
  const [workspaces,setWorkspaces]=useState<Resource[]>([]),[workspace,setWorkspace]=useState("");
  const [projects,setProjects]=useState<Resource[]>([]),[teams,setTeams]=useState<Resource[]>([]);
  const [wsCursor,setWsCursor]=useState<string|null>(null),[projectCursor,setProjectCursor]=useState<string|null>(null);
  const [teamPage,setTeamPage]=useState(0),[error,setError]=useState("");const generation=useRef(0);
  async function loadWorkspaces(cursor?:string|null){try{const page=unwrapPage<Resource>(await apiFetch("/workspaces",{query:{limit:20,cursor:cursor??undefined}}));setWorkspaces(old=>cursor?[...old,...page.items]:page.items);setWsCursor(page.nextCursor);}catch{setError("Không tải được không gian làm việc");}}
  async function loadProjects(cursor?:string|null,request=generation.current){try{const page=unwrapPage<Resource>(await apiFetch("/projects",{query:{workspaceId:workspace||undefined,limit:20,cursor:cursor??undefined}}));if(request!==generation.current)return;setProjects(old=>cursor?[...old,...page.items]:page.items);setProjectCursor(page.nextCursor);}catch{if(request===generation.current)setError("Không tải được dự án");}}
  async function loadTeams(page:number,request=generation.current){if(!workspace){setTeams([]);return;}try{const r=await apiFetch<{data:Resource[]}>(`/workspaces/${workspace}/teams`,{query:{page,size:20}});if(request!==generation.current)return;setTeams(r.data);setTeamPage(page);}catch{if(request===generation.current)setError("Không tải được nhóm");}}
  useEffect(()=>{void loadWorkspaces();},[]);
  useEffect(()=>{const current=++generation.current;setProjects([]);setProjectCursor(null);setTeams([]);setError("");void loadProjects(null,current);void loadTeams(0,current);return()=>{generation.current++;};},[workspace]);
  return <div className="flex flex-wrap items-end gap-3">
    <label>Phạm vi<select className="input" value={scope} onChange={e=>onChange(e.target.value,"","")}><option value="all">Tất cả</option><option value="personal">Cá nhân</option><option value="project">Dự án / nhóm</option></select></label>
    {scope!=="personal"&&<><SearchSelect label="Không gian làm việc" placeholder="Tất cả không gian" value={workspace} onChange={v=>{setWorkspace(v);onChange(scope,"","");}} options={workspaces.map(w=>({id:w.id,name:w.name}))} onMore={wsCursor?()=>void loadWorkspaces(wsCursor):undefined}/>
    <SearchSelect label="Dự án" placeholder="Tất cả dự án" value={projectId} onChange={v=>onChange(scope,v,teamId)} options={projects.map(p=>({id:p.id,name:p.name}))} onMore={projectCursor?()=>void loadProjects(projectCursor):undefined}/>
    {workspace&&<><label>Nhóm<select className="input" value={teamId} onChange={e=>onChange(scope,projectId,e.target.value)}><option value="">Tất cả nhóm</option>{teams.map(t=><option key={t.id} value={t.id}>{t.name}</option>)}</select></label><button className="btn btn-secondary" disabled={teamPage===0} onClick={()=>void loadTeams(teamPage-1)}>Nhóm trước</button><button className="btn btn-secondary" disabled={teams.length<20} onClick={()=>void loadTeams(teamPage+1)}>Nhóm sau</button></>}
    </>}{error&&<p role="alert">{error}</p>}
  </div>;
}
