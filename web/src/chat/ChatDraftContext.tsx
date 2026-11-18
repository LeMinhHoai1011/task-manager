import {createContext,useCallback,useContext,useEffect,useRef,useState,type ReactNode} from "react";
import {useAuth} from "../auth/AuthContext";
import {useAccess} from "../auth/AccessContext";

export type PendingMessage={clientId:string;body:string;taskId:string;status:"sending"|"failed";error?:string};
type Draft={body:string;taskId:string;pending:PendingMessage[];retry?:Pick<PendingMessage,"clientId"|"body"|"taskId">};
const empty:Draft={body:"",taskId:"",pending:[]};
type Store={owner:string;drafts:Record<string,Draft>};
const Context=createContext<{drafts:Record<string,Draft>;update:(project:string,change:(draft:Draft)=>Draft)=>void}|null>(null);

/** Only page memory: chat text never enters persistent browser storage. */
export function ChatDraftProvider({children}:{children:ReactNode}) {
  const {user}=useAuth(),owner=user?.id??"";
  const access=useAccess();
  const permissionKey=access.projects.filter(p=>["OWNER","ADMIN","MANAGER","MEMBER"].includes(p.role)).map(p=>p.id).sort().join(",");
  const allowed=useRef(new Set<string>()),identity=useRef(owner);
  if(identity.current!==owner)allowed.current=new Set();
  identity.current=owner;
  if(!access.loading)allowed.current=new Set(permissionKey?permissionKey.split(","):[]);
  const [store,setStore]=useState<Store>({owner,drafts:{}});
  useEffect(()=>{
    if(access.loading)return;
    setStore(old=>{
      const entries=old.owner===owner?Object.entries(old.drafts).filter(([project])=>allowed.current.has(project)):[];
      if(old.owner===owner&&entries.length===Object.keys(old.drafts).length)return old;
      return {owner,drafts:Object.fromEntries(entries)};
    });
  },[owner,permissionKey,access.loading]);
  const update=useCallback((project:string,change:(draft:Draft)=>Draft)=>{
    if(!owner||identity.current!==owner||!allowed.current.has(project))return;
    setStore(old=>{
      if(identity.current!==owner||!allowed.current.has(project))return old;
      const drafts=old.owner===owner?old.drafts:{};
      const previous=drafts[project]??empty,next=change(previous);
      return previous===next&&old.owner===owner?old:{owner,drafts:{...drafts,[project]:next}};
    });
  },[owner]);
  return <Context.Provider value={{drafts:store.owner===owner?store.drafts:{},update}}>{children}</Context.Provider>;
}
export function useChatDraft(project:string) {
  const store=useContext(Context);
  if(!store)throw new Error("ChatDraftProvider required");
  const update=useCallback((change:(draft:Draft)=>Draft)=>store.update(project,change),[project,store.update]);
  return {draft:store.drafts[project]??empty,update};
}
