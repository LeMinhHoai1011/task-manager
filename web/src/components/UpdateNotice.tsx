import {useEffect,useState} from "react";
export default function UpdateNotice(){
  const [registration,setRegistration]=useState<ServiceWorkerRegistration|null>(null);
  useEffect(()=>{const update=(e:Event)=>setRegistration((e as CustomEvent<ServiceWorkerRegistration>).detail);
    window.addEventListener("smarttask:update",update);return()=>window.removeEventListener("smarttask:update",update);},[]);
  function apply(){
    if(!registration?.waiting)return;
    navigator.serviceWorker.addEventListener("controllerchange",()=>window.location.reload(),{once:true});
    registration.waiting.postMessage({type:"SKIP_WAITING"});
  }
  return registration?.waiting?<div role="status" className="card mb-4">Có phiên bản mới. Hãy lưu nội dung đang sửa trước khi cập nhật. <button className="btn btn-secondary" onClick={apply}>Cập nhật phiên bản</button></div>:null;
}
