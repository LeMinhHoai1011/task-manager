import {useEffect, useRef, useState} from "react";
import {apiFetch, newIdempotencyKey, unwrapData} from "../api/client";
import {Button, Select, TextInput, priorityLabel} from "./ui";
import MemberPicker from "./MemberPicker";

export type BatchSelection = {id:string; title:string; version:number};
type BatchResult = {id:string; success:boolean; state?:string; version?:number; code?:string; reason_code?:string};
const actions = {
  START:"Bắt đầu", SUBMIT:"Nộp hoàn thành", ASSIGN:"Thêm người thực hiện",
  PRIORITY:"Đổi ưu tiên", ADD_TAG:"Thêm nhãn", REMOVE_TAG:"Gỡ nhãn", DELETE:"Xóa mềm",
};
export type BatchAction = keyof typeof actions;

export default function TaskBatchToolbar({selected, onComplete, onClear, onBusyChange,allowedActions=Object.keys(actions) as BatchAction[]}: {
  selected:BatchSelection[]; onComplete:(ids:string[])=>void; onClear:()=>void; onBusyChange:(busy:boolean)=>void;
  allowedActions?:BatchAction[];
}) {
  const [requestedAction,setAction] = useState<BatchAction>("START");
  const action=allowedActions.includes(requestedAction)?requestedAction:allowedActions[0]??"START";
  const [value,setValue] = useState("");
  const selectionKey=selected.map(row=>row.id).sort().join(",");
  useEffect(()=>{setValue(action==="PRIORITY"?"MEDIUM":"");setConfirmed(false);},[selectionKey,action]);
  const [busy,setBusy] = useState(false);
  const [confirmed,setConfirmed] = useState(false);
  const [message,setMessage] = useState("");
  const [failures,setFailures] = useState<{title:string; message:string}[]>([]);
  const pending = useRef<{signature:string; key:string}|null>(null);
  const sending = useRef(false);
  const needsValue = ["ASSIGN","PRIORITY","ADD_TAG","REMOVE_TAG"].includes(action);

  async function apply() {
    if (sending.current || !allowedActions.includes(action) || !selected.length || (needsValue && !value.trim()) || (action === "DELETE" && !confirmed)) return;
    const snapshot = [...selected];
    const body = {items:snapshot.map(({id,version})=>({id,version})), action, value:needsValue?value.trim():null};
    const signature = JSON.stringify(body);
    if (pending.current?.signature !== signature) pending.current = {signature,key:newIdempotencyKey()};
    sending.current = true;
    setBusy(true); onBusyChange(true); setMessage(""); setFailures([]);
    try {
      const response = await apiFetch("/tasks/batch", {method:"PATCH", body, idempotencyKey:pending.current.key});
      const results = unwrapData<BatchResult[]>(response);
      if (!results || results.length !== snapshot.length) throw new Error("Missing batch results");
      const successIds = results.filter(r=>r.success).map(r=>r.id);
      setMessage(`Đã xử lý ${successIds.length}/${snapshot.length} công việc.`);
      setFailures(results.filter(r=>!r.success).map(r=>({
        title:snapshot.find(row=>row.id===r.id)?.title??"Công việc",
        message:r.code==="VERSION_CONFLICT"
          ?"Dữ liệu đã thay đổi. Bỏ chọn rồi chọn lại sau khi tải lại danh sách."
          :r.reason_code==="IDEMPOTENCY_CONFLICT"?"Yêu cầu thử lại đã thay đổi. Hãy tải lại danh sách và chọn lại công việc."
          :r.code==="RESOURCE_NOT_FOUND"?"Công việc không còn khả dụng."
          :r.code==="FORBIDDEN"?"Bạn không có quyền thực hiện thao tác này."
          :r.code==="EXECUTION_LOCKED"?"Công việc đang chờ duyệt."
          :"Không thể áp dụng. Kiểm tra trạng thái và quyền ở trang chi tiết.",
      })));
      pending.current = null;
      setConfirmed(false);
      onComplete(successIds);
    } catch (error:unknown) {
      setMessage((error as {uiMessage?:string}).uiMessage??"Chưa nhận đủ kết quả. Có thể thử lại an toàn với cùng lựa chọn và thao tác.");
    } finally { sending.current = false; setBusy(false); onBusyChange(false); }
  }

  if (!selected.length && !message) return null;
  return <section aria-label="Thao tác nhiều công việc" className="card space-y-3">
    {selected.length>0&&<>
      <div className="flex flex-wrap items-center gap-2">
        <strong>Đã chọn {selected.length}/100 công việc</strong>
        <Select aria-label="Thao tác hàng loạt" disabled={busy} value={action} onChange={e=>{setAction(e.target.value as BatchAction);setValue(e.target.value==="PRIORITY"?"MEDIUM":"");setConfirmed(false);}}>
          {Object.entries(actions).filter(([key])=>allowedActions.includes(key as BatchAction)).map(([key,label])=><option key={key} value={key}>{label}</option>)}
        </Select>
        {action==="PRIORITY"?<Select aria-label="Ưu tiên hàng loạt" disabled={busy} value={value} onChange={e=>setValue(e.target.value)}>
          {["LOW","MEDIUM","HIGH","URGENT"].map(priority=><option key={priority} value={priority}>{priorityLabel(priority)}</option>)}
        </Select>:action==="ASSIGN"?<MemberPicker key={selected.map(row=>row.id).sort().join(",")} label="Người thực hiện hàng loạt" taskIds={selected.map(row=>row.id)} disabled={busy} value={value} onChange={setValue}/>:needsValue&&<TextInput aria-label="Nhãn hàng loạt" disabled={busy} maxLength={50} placeholder="Tên nhãn" value={value} onChange={e=>setValue(e.target.value)}/>}
        <Button disabled={busy||!allowedActions.length||(needsValue&&!value.trim())||(action==="DELETE"&&!confirmed)} onClick={()=>void apply()}>{busy?"Đang xử lý…":"Áp dụng"}</Button>
        <Button variant="secondary" disabled={busy} onClick={onClear}>Bỏ chọn tất cả</Button>
      </div>
      {action==="DELETE"&&<label className="flex items-center gap-2"><input type="checkbox" disabled={busy} checked={confirmed} onChange={e=>setConfirmed(e.target.checked)}/>Xác nhận xóa mềm các công việc đã chọn và việc con của chúng.</label>}
      {action==="SUBMIT"&&<p className="text-sm">Công việc sẽ chuyển sang chờ duyệt hoặc hoàn thành theo cấu hình từng dự án.</p>}
      <p className="text-xs text-slate-500">Chỉ áp dụng cho công việc đã chọn. Hệ thống báo riêng những việc chưa xử lý được.</p>
    </>}
    {message&&<p role="status">{message}</p>}
    {failures.length>0&&<ul aria-label="Công việc chưa xử lý" className="space-y-1 text-sm">{failures.map((failure,index)=><li key={index}><strong>{failure.title}</strong>: {failure.message}</li>)}</ul>}
  </section>;
}
