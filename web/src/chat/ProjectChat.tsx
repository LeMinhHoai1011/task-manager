import {useEffect,useRef,useState} from "react";
import {Link} from "react-router-dom";
import {apiFetch,unwrapData} from "../api/client";
import {useAuth} from "../auth/AuthContext";
import ResourcePicker from "../components/ResourcePicker";
import {Button,ErrorAlert,stateLabel} from "../components/ui";
import {useChatDraft,type PendingMessage} from "./ChatDraftContext";
import useProjectChat,{type ChatMessage} from "./useProjectChat";

export default function ProjectChat({projectId,onUnread}:{projectId:string;onUnread:(count:number)=>void}) {
  const {user}=useAuth(),{draft,update}=useChatDraft(projectId);
  const chat=useProjectChat(projectId,()=>update(()=>({body:"",taskId:"",pending:[]})));
  const [picker,setPicker]=useState(!!draft.taskId),[deleting,setDeleting]=useState<ChatMessage|null>(null),[reason,setReason]=useState(""),[deleteBusy,setDeleteBusy]=useState(false),[actionError,setActionError]=useState("");
  const [newCount,setNewCount]=useState(0);
  const log=useRef<HTMLDivElement>(null),end=useRef<HTMLDivElement>(null),atBottom=useRef(true),lastSeq=useRef(0),sending=useRef(new Set<string>()),deletingLock=useRef(false);
  const composeLock=useRef(false);
  const confirmation=useRef<HTMLFormElement>(null);
  useEffect(()=>{composeLock.current=false;},[draft.body,draft.taskId]);
  useEffect(()=>{if(deleting){confirmation.current?.scrollIntoView?.({block:"nearest"});confirmation.current?.querySelector<HTMLElement>('input,button[type="button"]')?.focus();}},[deleting]);
  const base=`/projects/${projectId}`;
  function markVisible(){
    const rect=end.current?.getBoundingClientRect();
    if(atBottom.current&&document.visibilityState==="visible"&&rect&&rect.height>0&&rect.top>=0&&rect.bottom<=window.innerHeight){
      const latest=chat.messages.filter(m=>m.seq<=(chat.revision??0)).slice(-1)[0]?.seq;
      if(latest)void chat.read(latest);
    }
  }
  useEffect(()=>{onUnread(chat.unread.count);},[chat.unread.count,onUnread]);
  useEffect(()=>{
    const ids=new Set(chat.messages.filter(m=>m.sender_id===user?.id).map(m=>m.client_message_id));
    update(old=>old.pending.some(p=>ids.has(p.clientId))?{...old,pending:old.pending.filter(p=>!ids.has(p.clientId))}:old);
    const latest=chat.messages[chat.messages.length-1]?.seq??0;
    if(atBottom.current){if(log.current)log.current.scrollTop=log.current.scrollHeight;setNewCount(0);}
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
      const message=unwrapData<ChatMessage>(await apiFetch(`${base}/messages`,{method:"POST",signal:controller.signal,body:{client_message_id:pending.clientId,body:pending.body,linked_task_id:pending.taskId||null}}));
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
    const retry=draft.retry;
    const pending:PendingMessage={clientId:retry&&retry.body===text&&retry.taskId===draft.taskId?retry.clientId:crypto.randomUUID(),body:text,taskId:draft.taskId,status:"sending"};
    atBottom.current=true;setNewCount(0);
    update(old=>({...old,body:"",taskId:"",retry:undefined,pending:[...old.pending,pending]}));setPicker(false);void deliver(pending);
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
  return <section className="project-chat" aria-label="Trao đổi trong dự án">
    <header className="chat-heading"><div><h2>Trao đổi trong dự án</h2><p>Trao đổi chung tại đây; nội dung của từng đầu việc lưu trong bình luận công việc.</p></div><span role="status" className="chat-connection">{chat.connected?"Đã kết nối":"Đang kết nối lại…"}</span></header>
    <ErrorAlert message={chat.error} onRetry={()=>void chat.sync()}/>
    <div className="chat-messages" ref={log} role="log" aria-label="Tin nhắn dự án" aria-live="polite" aria-relevant="additions text" onScroll={()=>{
      const node=log.current;if(!node)return;atBottom.current=node.scrollHeight-node.scrollTop-node.clientHeight<48;if(atBottom.current){setNewCount(0);markVisible();}
    }}>
      {chat.loading&&<p role="status">Đang tải tin nhắn…</p>}
      {chat.before!=null&&<div className="chat-history"><Button variant="secondary" disabled={chat.olderBusy} onClick={()=>void older()}>{chat.olderBusy?"Đang tải…":"Xem tin trước"}</Button></div>}
      {!chat.loading&&!chat.error&&!chat.messages.length&&<p className="chat-empty">Chưa có trao đổi. Hãy gửi tin đầu tiên cho dự án.</p>}
      {chat.messages.map(message=><article key={message.id} className={`chat-message ${message.sender_id===user?.id?"is-mine":""}`} aria-label={`Tin của ${message.sender_name}`}>
        <div className="chat-message-heading"><strong>{message.sender_name}</strong><span className="chat-username">@{message.sender_username}</span><time dateTime={message.created_at}>{new Date(message.created_at).toLocaleString("vi-VN",{day:"2-digit",month:"2-digit",hour:"2-digit",minute:"2-digit"})}</time>
          {!message.deleted&&chat.access?.write&&(message.sender_id===user?.id||chat.access.moderate)&&<button type="button" className="chat-delete" aria-label={`Xóa tin của ${message.sender_name} lúc ${new Date(message.created_at).toLocaleTimeString("vi-VN")}`} onClick={()=>{setDeleting(message);setReason("");setActionError("");}}>Xóa</button>}
        </div>
        <p className={`chat-body ${message.deleted?"is-deleted":""}`}>{message.deleted?"Tin nhắn đã được xóa":message.body}</p>
        {message.sender_id===user?.id&&!message.deleted&&<span className="chat-hint">Đã gửi</span>}
        {message.linked_task&&!message.deleted&&<Link className="chat-task-link" to={`/tasks/${message.linked_task.id}`}><strong>{message.linked_task.title}</strong><span>{stateLabel(message.linked_task.state)} · Mở công việc</span></Link>}
        {message.task_unavailable&&!message.deleted&&<p className="chat-hint">Công việc liên quan không còn khả dụng hoặc bạn chưa có quyền xem.</p>}
      </article>)}
      {draft.pending.map(p=><article key={p.clientId} className="chat-message is-mine chat-pending"><strong>Bạn</strong><p className="chat-body">{p.body}</p>{p.taskId&&<p className="chat-hint">Kèm liên kết công việc</p>}<p role="status">{p.status==="sending"?"Đang gửi…":p.error}</p>{p.status==="failed"&&<div className="flex flex-wrap gap-2"><Button variant="secondary" disabled={!chat.access?.write} onClick={()=>void deliver(p)}>Thử lại tin nhắn</Button><Button variant="ghost" disabled={!chat.access?.write||!!draft.body||!!draft.taskId} onClick={()=>{update(old=>({...old,body:p.body,taskId:p.taskId,retry:p,pending:old.pending.filter(item=>item.clientId!==p.clientId)}));setPicker(!!p.taskId);document.getElementById(`chat-input-${projectId}`)?.focus();}}>Sửa bản gửi</Button></div>}</article>)}
      <div ref={end} className="chat-end"/>
    </div>
    {newCount>0&&<button type="button" className="btn btn-secondary chat-new" onClick={()=>{atBottom.current=true;if(log.current)log.current.scrollTop=log.current.scrollHeight;setNewCount(0);requestAnimationFrame(markVisible);}}>Có {newCount} tin mới ↓</button>}
    {deleting&&<form ref={confirmation} className="chat-delete-confirm" aria-label="Xác nhận xóa tin nhắn" onSubmit={e=>{e.preventDefault();void remove();}}><h3>Xóa tin nhắn này?</h3><p>Nội dung sẽ được thay bằng “Tin nhắn đã được xóa”.</p>{deleting.sender_id!==user?.id&&<label>Lý do xóa tin của người khác<input className="input" autoFocus required minLength={3} maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/></label>}<ErrorAlert message={actionError}/><div><Button variant="danger" disabled={deleteBusy||deleting.sender_id!==user?.id&&reason.trim().length<3}>{deleteBusy?"Đang xóa…":"Xác nhận xóa tin"}</Button><Button type="button" variant="secondary" disabled={deleteBusy} onClick={()=>setDeleting(null)}>Giữ lại</Button></div></form>}
    {chat.access?.write?<form className="chat-composer" onSubmit={e=>{e.preventDefault();send();}}>
      {picker&&<ResourcePicker kind="tasks" projectId={projectId} label="Công việc liên quan" value={draft.taskId} onChange={taskId=>update(old=>({...old,taskId}))}/>}
      <label htmlFor={`chat-input-${projectId}`}>Tin nhắn cho dự án</label><textarea id={`chat-input-${projectId}`} className="input" placeholder="Nhập tin nhắn…" rows={3} value={draft.body} onChange={e=>update(old=>({...old,body:e.target.value}))} onFocus={markVisible} onKeyDown={e=>{if(e.key==="Enter"&&!e.shiftKey&&!e.nativeEvent.isComposing&&e.nativeEvent.keyCode!==229&&!window.matchMedia("(pointer: coarse)").matches){e.preventDefault();send();}}}/>
      <div className="chat-composer-actions"><Button type="button" variant="ghost" onClick={()=>setPicker(v=>!v)}>Liên kết công việc{draft.taskId?" · Đã chọn":""}</Button><span className={bodyLength>4000?"chat-too-long":"chat-hint"}>{bodyLength}/4.000 ký tự</span><Button type="submit" disabled={!draft.body.trim()||bodyLength>4000}>Gửi tin nhắn</Button></div><p className="chat-hint">Enter gửi · Shift + Enter xuống dòng. Bản nháp chỉ giữ trong phiên trang này. “Đã gửi” nghĩa là máy chủ đã lưu.</p>
    </form>:!chat.loading&&chat.access?.read&&<p className="chat-readonly">Dự án hiện chỉ cho phép xem trao đổi.</p>}
  </section>;
}
