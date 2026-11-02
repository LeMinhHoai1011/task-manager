import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { apiFetch } from "../api/client";
import { useRealtimeEvent } from "../realtime/RealtimeContext";
export default function NotificationBell() {
  const [count,setCount]=useState(0); const [failed,setFailed]=useState(false);
  const load=useCallback(async()=>{try{const r=await apiFetch<{data:{count:number}}>("/notifications/unread-count");setCount(r.data.count);setFailed(false);}catch{setFailed(true);}},[]);
  useEffect(()=>{void load();},[load]);
  useRealtimeEvent(["notification"],()=>{void load();});
  return <Link className="btn btn-ghost" to="/notifications" aria-label={failed?"Mở thông báo, chưa tải được số chưa đọc":`Thông báo, ${count} chưa đọc`} title={failed?"Không tải được số chưa đọc":"Thông báo"}>🔔 {failed?"?":count>99?"99+":count}</Link>;
}
