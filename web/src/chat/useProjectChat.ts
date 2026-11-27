import {useCallback,useEffect,useRef,useState} from "react";
import {apiFetch,unwrapData} from "../api/client";
import {useRealtimeEvent,useRealtimeStatus} from "../realtime/RealtimeContext";
import {useAuth} from "../auth/AuthContext";

export type ChatMessage={id:string;seq:number;changed_seq:number;sender_id:string;sender_name:string;sender_username:string;client_message_id:string;body:string|null;deleted:boolean;created_at:string;linked_task:{id:string;title:string;state:string}|null;task_unavailable:boolean;reply_to?:{id:string;body:string|null};pinned?:boolean};
export type ChatAccess={read:boolean;write:boolean;moderate:boolean;reason?:string};
type Unread={count:number;last_read_seq:number};
type Page={items:ChatMessage[];revision:number;next_before:number|null;next_after:number|null;has_more:boolean;access:ChatAccess;unread:Unread};
type Failure={status?:number;uiMessage?:string};

export function mergeMessages(old:ChatMessage[],incoming:ChatMessage[]):ChatMessage[] {
  const map=new Map(old.map(row=>[row.id,row]));
  for(const row of incoming){const previous=map.get(row.id);if(!previous||previous.changed_seq<=row.changed_seq)map.set(row.id,row);}
  return [...map.values()].sort((a,b)=>a.seq-b.seq);
}

export default function useProjectChat(project:string,onDenied:()=>void) {
  const owner=useAuth().user?.id;
  const base=`/projects/${project}`;
  const [messages,setMessages]=useState<ChatMessage[]>([]),[access,setAccess]=useState<ChatAccess|null>(null);
  const [unread,setUnread]=useState<Unread>({count:0,last_read_seq:0}),[revision,setRevision]=useState<number|null>(null);
  const [before,setBefore]=useState<number|null>(null),[loading,setLoading]=useState(true),[olderBusy,setOlderBusy]=useState(false),[error,setError]=useState("");
  const current=useRef(0),watermark=useRef<number|null>(null),controller=useRef<AbortController|null>(null);
  const busy=useRef(false),again=useRef(false),olderLock=useRef(false),readBusy=useRef(false),wantedRead=useRef(0),readAt=useRef(0);
  const denied=useRef(onDenied);denied.current=onDenied;
  const connected=useRealtimeStatus();
  const fail=useCallback((failure:unknown,request:number)=>{
    if(request!==current.current)return;
    const e=failure as Failure;
    if([401,403,404].includes(e.status??0)){
      current.current++;controller.current?.abort();controller.current=new AbortController();
      busy.current=false;again.current=false;readBusy.current=false;olderLock.current=false;
      setLoading(false);setOlderBusy(false);
      setMessages([]);setAccess(null);setBefore(null);setUnread({count:0,last_read_seq:0});watermark.current=null;
      denied.current();setError("Bạn không còn quyền xem trao đổi này. Nội dung đã được đóng.");
    }else setError(e.uiMessage??"Chưa đồng bộ được tin nhắn. Nội dung đã tải vẫn được giữ; hãy thử lại.");
  },[]);
  const merge=useCallback((rows:ChatMessage[])=>{if(rows.length)setMessages(old=>mergeMessages(old,rows));},[]);
  const sync=useCallback(async()=>{
    if(busy.current){again.current=true;return;}
    busy.current=true;
    const request=current.current;
    try {
      let after=watermark.current,until:number|undefined;
      do{
        const page=unwrapData<Page>(await apiFetch(`${base}/messages`,{signal:controller.current?.signal,query:after==null?{limit:50}:{after,until,limit:100}}));
        if(request!==current.current)return;
        if(!page||!Array.isArray(page.items))throw new Error("Invalid chat page");
        merge(page.items);setAccess(page.access);setUnread(page.unread);readAt.current=Math.max(readAt.current,page.unread.last_read_seq);
        if(after==null){setBefore(page.next_before);watermark.current=page.revision;break;}
        if(page.next_after==null||page.next_after<after||page.has_more&&page.next_after===after)throw new Error("Invalid chat cursor");
        after=page.next_after;until=page.revision;watermark.current=after;
        if(!page.has_more)break;
      }while(request===current.current);
      setRevision(watermark.current);setError("");
    }catch(e){fail(e,request);}finally{
      if(request===current.current){busy.current=false;setLoading(false);if(again.current){again.current=false;void sync();}}
    }
  },[base,merge,fail]);
  useEffect(()=>{
    current.current++;controller.current=new AbortController();watermark.current=null;busy.current=false;again.current=false;readAt.current=0;wantedRead.current=0;readBusy.current=false;olderLock.current=false;
    setMessages([]);setAccess(null);setBefore(null);setRevision(null);setUnread({count:0,last_read_seq:0});setError("");setOlderBusy(false);setLoading(true);void sync();
    const resume=()=>{if(document.visibilityState==="visible")void sync();};
    const timer=window.setInterval(resume,15000);
    window.addEventListener("online",resume);window.addEventListener("focus",resume);document.addEventListener("visibilitychange",resume);
    return()=>{current.current++;controller.current?.abort();window.clearInterval(timer);window.removeEventListener("online",resume);window.removeEventListener("focus",resume);document.removeEventListener("visibilitychange",resume);};
  },[sync,owner]);
  useEffect(()=>{if(connected)void sync();},[connected,sync]);
  useRealtimeEvent(["chat"],event=>{if(event.scopeId===project)void sync();});
  useRealtimeEvent(["project","workspace","session"],event=>{if(event.scopeType!=="PROJECT"||event.scopeId===project)void sync();});
  const older=async()=>{
    if(before==null||olderLock.current)return;
    olderLock.current=true;setOlderBusy(true);const request=current.current;
    try{const page=unwrapData<Page>(await apiFetch(`${base}/messages`,{signal:controller.current?.signal,query:{before,limit:50}}));
      if(request!==current.current||!page)return;merge(page.items);setBefore(page.next_before);
    }catch(e){fail(e,request);}finally{if(request===current.current){olderLock.current=false;setOlderBusy(false);}}
  };
  const read=useCallback(async(seq:number)=>{
    const safe=Math.min(seq,watermark.current??0);wantedRead.current=Math.max(wantedRead.current,safe);
    if(readBusy.current||safe<=readAt.current)return;
    const request=current.current;readBusy.current=true;
    try{while(wantedRead.current>readAt.current&&request===current.current){
      const target=wantedRead.current;
      const result=unwrapData<Unread>(await apiFetch(`${base}/chat/read`,{method:"PUT",signal:controller.current?.signal,body:{last_read_seq:target}}));
      if(request!==current.current||!result)return;
      readAt.current=Math.max(target,result.last_read_seq);setUnread(result);
    }}catch(e){fail(e,request);}finally{if(request===current.current)readBusy.current=false;}
  },[base,fail]);
  return {messages,access,unread,revision,loading,olderBusy,before,error,connected,sync,merge,older,read,generation:current};
}
