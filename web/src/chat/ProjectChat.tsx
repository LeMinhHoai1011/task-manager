import {Fragment,useEffect,useRef,useState} from "react";
import {Link,useParams} from "react-router-dom";
import {apiFetch,unwrapData} from "../api/client";
import {useAuth} from "../auth/AuthContext";
import {useAccess} from "../auth/AccessContext";
import AppIcon from "../components/AppIcon";
import CreateProjectTask from "../components/CreateProjectTask";
import ResourcePicker from "../components/ResourcePicker";
import {Button,ErrorAlert,stateLabel} from "../components/ui";
import {useChatDraft,type PendingMessage} from "./ChatDraftContext";
import useProjectChat,{type ChatMessage} from "./useProjectChat";

const messageDay = new Intl.DateTimeFormat("vi-VN",{dateStyle:"full"});
const messageTime = new Intl.DateTimeFormat("vi-VN",{hour:"2-digit",minute:"2-digit"});

function sameGroup(earlier: ChatMessage | undefined, later: ChatMessage | undefined) {
  if (!earlier || !later || earlier.deleted || later.deleted || earlier.sender_id !== later.sender_id) return false;
  const start = new Date(earlier.created_at), end = new Date(later.created_at);
  return end.getTime() - start.getTime() < 5 * 60_000 && messageDay.format(start) === messageDay.format(end);
}

export function ProjectChatRoom() {
  const {id}=useParams();
  const {user}=useAuth();
  const {projects}=useAccess();
  const name=projects.find(project=>project.id===id)?.name;
  if(!id)return null;
  return <ProjectChat key={`${user?.id}:${id}`} projectId={id} projectName={name} standalone/>;
}

export default function ProjectChat({projectId,projectName,onUnread,standalone=false}:{
  projectId:string;projectName?:string;onUnread?:(count:number)=>void;standalone?:boolean;
}) {
  const {user}=useAuth(),{draft,update}=useChatDraft(projectId);
  const projectAccess=useAccess().projects.find(project=>project.id===projectId);
  const chat=useProjectChat(projectId,()=>update(()=>({body:"",taskId:"",pending:[]})));
  const [picker,setPicker]=useState(!!draft.taskId),[deleting,setDeleting]=useState<ChatMessage|null>(null),[reason,setReason]=useState(""),[deleteBusy,setDeleteBusy]=useState(false),[actionError,setActionError]=useState("");
  const [newCount,setNewCount]=useState(0);
  const [pinned,setPinned]=useState<ChatMessage>();
  const [search,setSearch]=useState("");
  const [results,setResults]=useState<ChatMessage[]|null>(null);
  const [searchPage,setSearchPage]=useState<{term:string;before?:number}>({term:""});
  const [preview,setPreview]=useState<ChatMessage>();
  const [taskSource,setTaskSource]=useState<ChatMessage>();
  const [assignees,setAssignees]=useState<{id:string;name:string}[]>([]);
  const [toolError,setToolError]=useState("");
  const [searching,setSearching]=useState(false);
  const log=useRef<HTMLDivElement>(null),end=useRef<HTMLDivElement>(null),atBottom=useRef(true),lastSeq=useRef(0),sending=useRef(new Set<string>()),deletingLock=useRef(false);
  const unreadDivider=useRef<HTMLParagraphElement>(null), positioned=useRef(false);
  const composeLock=useRef(false);
  const confirmation=useRef<HTMLFormElement>(null);
  useEffect(()=>{composeLock.current=false;},[draft.body,draft.taskId,draft.reply]);
  useEffect(()=>{if(deleting){confirmation.current?.scrollIntoView?.({block:"nearest"});confirmation.current?.querySelector<HTMLElement>('input,button[type="button"]')?.focus();}},[deleting]);
  const base=`/projects/${projectId}`;
  useEffect(()=>{
    if(!chat.access?.read){setPinned(undefined);setResults(null);setPreview(undefined);setTaskSource(undefined);return;}
    const request=new AbortController();
    void apiFetch(`${base}/chat/pinned`,{signal:request.signal}).then(response=>{
      const rows=unwrapData<ChatMessage[]>(response);
      if(!request.signal.aborted&&Array.isArray(rows))setPinned(rows[0]);
    }).catch(()=>{});
    return()=>request.abort();
  },[base,chat.access?.read,chat.revision]);

  async function findMessages(more=false){
    const q=more?searchPage.term:search.trim();
    if(searching||!q)return;
    setSearching(true);setToolError("");
    const generation=chat.generation.current;
    try{
      const rows=unwrapData<ChatMessage[]>(await apiFetch(`${base}/chat/search`,{query:{q,before:more?searchPage.before:undefined}}));
      if(generation===chat.generation.current&&Array.isArray(rows)){
        setResults(previous=>more?[...(previous??[]),...rows]:rows);
        setSearchPage({term:q,before:rows.length===50?rows[49].seq:undefined});
      }
    }catch{if(generation===chat.generation.current)setToolError("Chưa tìm được tin nhắn.");}
    finally{setSearching(false);}
  }
  async function openMessage(id:string){
    const node=document.getElementById(`chat-message-${id}`);
    if(node){node.scrollIntoView?.({block:"center"});node.focus({preventScroll:true});return;}
    const generation=chat.generation.current;
    try{
      const row=unwrapData<ChatMessage>(await apiFetch(`${base}/messages/${id}`));
      if(generation===chat.generation.current)setPreview(row);
    }catch{if(generation===chat.generation.current)setToolError("Chưa mở được tin gốc.");}
  }
  async function togglePin(message:ChatMessage){
    const generation=chat.generation.current;
    try{
      await apiFetch(`${base}/messages/${message.id}/pin`,{method:"PUT",body:{pinned:!message.pinned}});
      if(generation===chat.generation.current)void chat.sync();
    }catch{if(generation===chat.generation.current)setToolError("Chưa cập nhật được tin ghim.");}
  }
  async function createFrom(message:ChatMessage){
    const generation=chat.generation.current;
    setAssignees([]);setTaskSource(message);
    if(!chat.access?.moderate&&!projectAccess?.assignment)return;
    try{
      const rows=unwrapData<{user_id:string;display_name:string;role:string}[]>(await apiFetch(`${base}/members`));
      if(generation===chat.generation.current&&Array.isArray(rows))setAssignees(rows
        .filter(row=>row.role!=="GUEST").map(row=>({id:row.user_id,name:row.display_name})));
    }catch{if(generation===chat.generation.current)setToolError("Chưa tải được danh sách người phụ trách.");}
  }
  function markVisible(){
    const rect=end.current?.getBoundingClientRect();
    if(atBottom.current&&document.visibilityState==="visible"&&rect&&rect.height>0&&rect.top>=0&&rect.bottom<=window.innerHeight){
      const latest=chat.messages.filter(m=>m.seq<=(chat.revision??0)).slice(-1)[0]?.seq;
      if(latest)void chat.read(latest);
    }
  }
  useEffect(()=>{onUnread?.(chat.unread.count);},[chat.unread.count,onUnread]);
  useEffect(()=>{
    const ids=new Set(chat.messages.filter(m=>m.sender_id===user?.id).map(m=>m.client_message_id));
    update(old=>{
      const replyRemoved=old.reply&&chat.messages.some(message=>message.id===old.reply?.id&&message.deleted);
      if(!replyRemoved&&!old.pending.some(p=>ids.has(p.clientId)))return old;
      return {...old,reply:replyRemoved?undefined:old.reply,pending:old.pending.filter(p=>!ids.has(p.clientId))};
    });
    const latest=chat.messages[chat.messages.length-1]?.seq??0;
    const node=log.current;
    if(node && !positioned.current && chat.revision!=null){
      positioned.current=true;
      const divider=unreadDivider.current;
      node.scrollTop=divider ? node.scrollTop+divider.getBoundingClientRect().top-node.getBoundingClientRect().top-12 : node.scrollHeight;
      atBottom.current=node.scrollHeight-node.scrollTop-node.clientHeight<48;
    }
    else if(atBottom.current){if(node)node.scrollTop=node.scrollHeight;setNewCount(0);}
    else if(lastSeq.current&&latest>lastSeq.current){const added=chat.messages.filter(m=>m.seq>lastSeq.current&&!m.deleted&&m.sender_id!==user?.id).length;setNewCount(n=>n+added);}
    lastSeq.current=Math.max(latest,lastSeq.current);
    const frame=requestAnimationFrame(markVisible);return()=>cancelAnimationFrame(frame);
  },[chat.messages,chat.revision,user?.id,update]);
  useEffect(()=>{
    const check=()=>markVisible();
    const observer=typeof IntersectionObserver!=="undefined"?new IntersectionObserver(check):null;
    if(end.current)observer?.observe(end.current);
    document.addEventListener("visibilitychange",check);window.addEventListener("scroll",check,{passive:true});
    return()=>{observer?.disconnect();document.removeEventListener("visibilitychange",check);window.removeEventListener("scroll",check);};
  });
  // A request can finish after leaving this tab. Its stable identity remains retryable.
  useEffect(()=>()=>{update(old=>({...old,pending:old.pending.map(p=>p.status==="sending"?{...p,status:"failed",error:"Chưa xác nhận kết quả. Có thể thử lại an toàn."}:p)}));},[update]);

  async function deliver(pending:PendingMessage){
    if(!chat.access?.write||sending.current.has(pending.clientId))return;
    sending.current.add(pending.clientId);
    update(old=>({...old,pending:old.pending.map(p=>p.clientId===pending.clientId?{...p,status:"sending",error:undefined}:p)}));
    const generation=chat.generation.current;
    const controller=new AbortController(),timeout=window.setTimeout(()=>controller.abort(),15000);
    try{
      const message=unwrapData<ChatMessage>(await apiFetch(`${base}/messages`,{method:"POST",signal:controller.signal,body:{client_message_id:pending.clientId,body:pending.body,linked_task_id:pending.taskId||null,...(pending.replyId?{reply_to_id:pending.replyId}:{})}}));
      if(!message?.id)throw new Error("Missing acknowledgement");
      update(old=>({...old,pending:old.pending.filter(p=>p.clientId!==pending.clientId)}));
      if(generation===chat.generation.current){chat.merge([message]);void chat.sync();}
    }catch(error){
      update(old=>({...old,pending:old.pending.map(p=>p.clientId===pending.clientId?{...p,status:"failed",error:(error as {uiMessage?:string}).uiMessage??"Chưa gửi được. Kiểm tra kết nối và thử lại."}:p)}));
      if(generation===chat.generation.current&&[401,403,404].includes((error as {status?:number}).status??0))void chat.sync();
    }finally{window.clearTimeout(timeout);sending.current.delete(pending.clientId);}
  }
  function send(){
    const text=draft.body.trim();
    if(composeLock.current||!text||!chat.access?.write||Array.from(text).length>4000)return;
    composeLock.current=true;
    const retry=draft.retry,replyId=draft.reply?.id;
    const pending:PendingMessage={clientId:retry&&retry.body===text&&retry.taskId===draft.taskId&&retry.replyId===replyId?retry.clientId:crypto.randomUUID(),body:text,taskId:draft.taskId,replyId,status:"sending"};
    atBottom.current=true;setNewCount(0);
    update(old=>({...old,body:"",taskId:"",reply:undefined,retry:undefined,pending:[...old.pending,pending]}));setPicker(false);void deliver(pending);
    requestAnimationFrame(()=>{if(log.current)log.current.scrollTop=log.current.scrollHeight;});
  }
  async function older(){
    const node=log.current;if(!node)return;
    const height=node.scrollHeight,top=node.scrollTop;atBottom.current=false;
    await chat.older();requestAnimationFrame(()=>{if(log.current)log.current.scrollTop=top+log.current.scrollHeight-height;});
  }
  async function remove(){
    if(!deleting||deletingLock.current)return;
    deletingLock.current=true;setDeleteBusy(true);setActionError("");const generation=chat.generation.current;
    try{const message=unwrapData<ChatMessage>(await apiFetch(`${base}/messages/${deleting.id}`,{method:"DELETE",ifMatch:String(deleting.changed_seq),body:{reason:reason.trim()||null}}));
      if(!message?.id)throw new Error("Missing deletion result");
      if(generation===chat.generation.current){chat.merge([message]);setDeleting(null);void chat.sync();}
    }catch(e){if(generation===chat.generation.current){setActionError((e as {uiMessage?:string}).uiMessage??"Không xóa được tin. Hãy tải lại rồi thử lại.");void chat.sync();}}
    finally{if(generation===chat.generation.current)setDeleteBusy(false);deletingLock.current=false;}
  }
  const bodyLength=Array.from(draft.body).length;
  const firstUnread = chat.unread.count > 0 ? chat.messages.find(message =>
    message.seq > chat.unread.last_read_seq && message.sender_id !== user?.id && !message.deleted)?.id : undefined;
  return <section className={`project-chat${standalone?" chat-room":""}`} aria-label="Chat dự án">
    <header className="chat-heading">
      {standalone&&<Link className="btn btn-ghost chat-back" to={`/projects/${projectId}`} aria-label="Về dự án"><AppIcon name="arrow-left"/></Link>}
      <span className="chat-room-icon"><AppIcon name="message-square" size={20}/></span>
      <div className="chat-room-title">{standalone?<h1>{projectName??"Trò chuyện dự án"}</h1>:<h2>{projectName??"Trò chuyện dự án"}</h2>}
        <span role="status" className={`chat-connection${chat.connected?" is-connected":""}`}>{chat.connected?"Đã kết nối":"Đang kết nối lại…"}</span>
      </div>
      {!standalone&&<Link className="btn btn-secondary chat-open" to={`/projects/${projectId}/chat`}>Mở phòng chat</Link>}
    </header>
    <ErrorAlert message={chat.error} onRetry={()=>void chat.sync()}/>
    {chat.access?.read&&<div className="chat-tools">
      <form onSubmit={event=>{event.preventDefault();void findMessages();}}>
        <input aria-label="Tìm tin nhắn" className="input" placeholder="Tìm tin nhắn" maxLength={200} value={search} onChange={event=>setSearch(event.target.value)}/>
        <Button disabled={searching||!search.trim()} variant="secondary">Tìm</Button>
      </form>
      {pinned&&!chat.messages.some(row=>row.id===pinned.id&&row.deleted)&&<div className="chat-pin-bar">
        <button type="button" onClick={()=>void openMessage(pinned.id)}><strong>Tin ghim</strong> {pinned.body}</button>
        {chat.access.write&&chat.access.moderate&&<Button variant="ghost" onClick={()=>void togglePin(pinned)}>Bỏ ghim</Button>}
      </div>}
      <ErrorAlert message={toolError}/>
      {results&&<div className="chat-search-panel">
        <Button variant="ghost" onClick={()=>setResults(null)}>Đóng kết quả</Button>
        {results.length===0?<p>Không tìm thấy tin nào.</p>:<ul>{results.filter(row=>!chat.messages.some(message=>message.id===row.id&&message.deleted)).map(row=><li key={row.id}><button type="button" onClick={()=>{setResults(null);requestAnimationFrame(()=>void openMessage(row.id));}}>{row.body}</button></li>)}</ul>}
        {searchPage.before&&<Button type="button" disabled={searching} onClick={()=>void findMessages(true)}>Tải thêm kết quả</Button>}
      </div>}
      {preview&&<div className="chat-pin-bar"><p>{preview.deleted||chat.messages.some(row=>row.id===preview.id&&row.deleted)?"Tin nhắn đã được xóa":preview.body}</p><Button variant="ghost" onClick={()=>setPreview(undefined)}>Đóng tin gốc</Button></div>}
      {taskSource&&!chat.messages.some(message=>message.id===taskSource.id&&message.deleted)&&<div className="chat-search-panel">
        <Button variant="ghost" onClick={()=>setTaskSource(undefined)}>Đóng biểu mẫu</Button>
        <CreateProjectTask key={taskSource.id} projectId={projectId} projectName={projectName} initialOpen
          sourceText={taskSource.body??""} assignees={assignees} onCreated={()=>{}}/>
      </div>}
    </div>}
    <div className="chat-messages" ref={log} role="log" aria-label="Tin nhắn dự án" aria-live="polite" aria-relevant="additions text" onScroll={()=>{
      const node=log.current;if(!node)return;atBottom.current=node.scrollHeight-node.scrollTop-node.clientHeight<48;if(atBottom.current){setNewCount(0);markVisible();}
    }}>
      {chat.loading&&<p role="status">Đang tải tin nhắn…</p>}
      {chat.before!=null&&<div className="chat-history"><Button variant="secondary" disabled={chat.olderBusy} onClick={()=>void older()}>{chat.olderBusy?"Đang tải…":"Xem tin trước"}</Button></div>}
      {!chat.loading&&!chat.error&&!chat.messages.length&&<div className="chat-empty">
        <AppIcon name="message-square" size={28}/>
        <p>{chat.access?.write?"Gửi tin nhắn đầu tiên cho dự án":"Chưa có tin nhắn"}</p>
      </div>}
      {chat.messages.map((message,index)=>{
        const mine=message.sender_id===user?.id;
        const created=new Date(message.created_at);
        const day=messageDay.format(created);
        const previous=chat.messages[index-1];
        const continued = message.id !== firstUnread && sameGroup(previous, message);
        const next = chat.messages[index+1];
        const continues = next?.id !== firstUnread && sameGroup(message, next);
        return <Fragment key={message.id}>
          {(!previous||messageDay.format(new Date(previous.created_at))!==day)&&<p className="chat-date">{day}</p>}
          {message.id === firstUnread && <p ref={unreadDivider} className="chat-unread-divider">Tin chưa đọc</p>}
          <article id={`chat-message-${message.id}`} tabIndex={-1} className={`chat-message${mine?" is-mine":""}${continued?" is-continued":""}${continues?" has-continuation":""}`} aria-label={`Tin của ${message.sender_name}`}>
            {!mine&&<span aria-hidden="true" className={`chat-avatar${continued?" is-spacer":""}`}>{Array.from(message.sender_name.trim())[0]?.toLocaleUpperCase("vi-VN")||"?"}</span>}
            <div className="chat-message-content">
              {!mine&&!continued&&<strong className="chat-sender">{message.sender_name}</strong>}
              <div className={`chat-bubble${message.deleted?" is-deleted":""}`}>
                {message.reply_to&&!message.deleted&&<button type="button" className="chat-quote" onClick={()=>void openMessage(message.reply_to!.id)}>{message.reply_to.body??"Tin gốc đã được xóa"}</button>}
                <p className="chat-body">{message.deleted?"Tin nhắn đã được xóa":message.body}</p>
                {message.linked_task&&!message.deleted&&<Link className="chat-task-link" to={`/tasks/${message.linked_task.id}`}><strong>{message.linked_task.title}</strong><span>{stateLabel(message.linked_task.state)} · Mở công việc</span></Link>}
                {message.task_unavailable&&!message.deleted&&<p className="chat-hint">Công việc liên quan không còn khả dụng hoặc bạn chưa có quyền xem.</p>}
              </div>
              <div className="chat-message-heading">
                <time dateTime={message.created_at}>{messageTime.format(created)}</time>
                {mine&&!message.deleted&&<span className="chat-hint">Đã gửi</span>}
                {!message.deleted&&chat.access?.write&&<button className="chat-delete" onClick={()=>{update(old=>({...old,reply:{id:message.id,body:message.body}}));document.getElementById(`chat-input-${projectId}`)?.focus();}}>Trả lời</button>}
                {!message.deleted&&chat.access?.write&&chat.access.moderate&&<button className="chat-delete" onClick={()=>void togglePin(message)}>{message.pinned?"Bỏ ghim":"Ghim"}</button>}
                {!message.deleted&&projectAccess?.canCreateTask&&<button className="chat-delete" onClick={()=>void createFrom(message)}>Tạo công việc</button>}
                {!message.deleted&&chat.access?.write&&(mine||chat.access.moderate)&&<button type="button" className="chat-delete" aria-label={`Xóa tin của ${message.sender_name} lúc ${created.toLocaleTimeString("vi-VN")}`} onClick={()=>{setDeleting(message);setReason("");setActionError("");}}>Xóa</button>}
              </div>
            </div>
          </article>
        </Fragment>;
      })}
      {draft.pending.map(p=><article key={p.clientId} className="chat-message is-mine chat-pending"><div className="chat-message-content"><div className="chat-bubble"><p className="chat-body">{p.body}</p>{p.taskId&&<p className="chat-hint">Kèm liên kết công việc</p>}</div><p role="status">{p.status==="sending"?"Đang gửi…":p.error}</p>{p.status==="failed"&&<div className="flex flex-wrap gap-2"><Button variant="secondary" disabled={!chat.access?.write} onClick={()=>void deliver(p)}>Thử lại tin nhắn</Button><Button variant="ghost" disabled={!chat.access?.write||!!draft.body||!!draft.taskId} onClick={()=>{update(old=>({...old,body:p.body,taskId:p.taskId,reply:p.replyId?{id:p.replyId,body:chat.messages.find(row=>row.id===p.replyId)?.body??null}:undefined,retry:p,pending:old.pending.filter(item=>item.clientId!==p.clientId)}));setPicker(!!p.taskId);document.getElementById(`chat-input-${projectId}`)?.focus();}}>Sửa bản gửi</Button></div>}</div></article>)}
      <div ref={end} className="chat-end"/>
    </div>
    {newCount>0&&<button type="button" className="btn btn-secondary chat-new" onClick={()=>{atBottom.current=true;if(log.current)log.current.scrollTop=log.current.scrollHeight;setNewCount(0);requestAnimationFrame(markVisible);}}>Có {newCount} tin mới ↓</button>}
    {deleting&&<form ref={confirmation} className="chat-delete-confirm" aria-label="Xác nhận xóa tin nhắn" onSubmit={e=>{e.preventDefault();void remove();}}><h3>Xóa tin nhắn này?</h3><p>Nội dung sẽ được thay bằng “Tin nhắn đã được xóa”.</p>{deleting.sender_id!==user?.id&&<label>Lý do xóa tin của người khác<input className="input" autoFocus required minLength={3} maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/></label>}<ErrorAlert message={actionError}/><div><Button variant="danger" disabled={deleteBusy||deleting.sender_id!==user?.id&&reason.trim().length<3}>{deleteBusy?"Đang xóa…":"Xác nhận xóa tin"}</Button><Button type="button" variant="secondary" disabled={deleteBusy} onClick={()=>setDeleting(null)}>Giữ lại</Button></div></form>}
    {chat.access?.write?<form className="chat-composer" onSubmit={e=>{e.preventDefault();send();}}>
      {draft.reply&&<div className="chat-pin-bar"><span>Trả lời {draft.reply.body?.slice(0,120)}</span><Button type="button" variant="ghost" onClick={()=>update(old=>({...old,reply:undefined}))}>Hủy trả lời</Button></div>}
      {picker&&<ResourcePicker kind="tasks" projectId={projectId} label="Công việc liên quan" value={draft.taskId} onChange={taskId=>update(old=>({...old,taskId}))}/>}
      <div className="chat-composer-actions">
        <Button type="button" variant="ghost" aria-label="Liên kết công việc" aria-expanded={picker} title={draft.taskId?"Đã liên kết công việc":"Liên kết công việc"} onClick={()=>setPicker(v=>!v)}><AppIcon name="paperclip"/>{draft.taskId&&<span className="chat-attachment-dot" aria-hidden="true"/>}</Button>
        <label htmlFor={`chat-input-${projectId}`} className="sr-only">Tin nhắn cho dự án</label>
        <textarea id={`chat-input-${projectId}`} className="input" placeholder="Nhập tin hoặc @tên…" title="Dùng @tên_đăng_nhập để nhắc" rows={1} value={draft.body} onChange={e=>update(old=>({...old,body:e.target.value}))} onFocus={markVisible} onKeyDown={e=>{if(e.key==="Enter"&&!e.shiftKey&&!e.nativeEvent.isComposing&&e.nativeEvent.keyCode!==229&&!window.matchMedia("(pointer: coarse)").matches){e.preventDefault();send();}}}/>
        <Button type="submit" aria-label="Gửi tin nhắn" disabled={!draft.body.trim()||bodyLength>4000}>Gửi<span className="sr-only"> tin nhắn</span></Button>
      </div>
      {bodyLength>4000&&<p className="chat-too-long" role="alert">Tin nhắn tối đa 4.000 ký tự.</p>}
    </form>:!chat.loading&&chat.access?.read&&<p className="chat-readonly">Dự án hiện chỉ cho phép xem trao đổi.</p>}
  </section>;
}
