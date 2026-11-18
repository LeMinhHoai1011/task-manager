import {useEffect, useId, useRef, useState} from "react";

export type Choice = {id:string; name:string; detail?:string};
type Props = {
  label:string; value:string; onChange:(value:string)=>void; options:Choice[];
  placeholder?:string; disabled?:boolean; loading?:boolean; error?:string;
  onRetry?:()=>void; onMore?:()=>void; required?:boolean;
  onOpen?:()=>void;
};

/** Giữ mã nội bộ trong value; người dùng chỉ tìm và chọn tên. */
export default function SearchSelect({label,value,onChange,options,placeholder="Chọn một mục",disabled,loading,error,onRetry,onMore,required,onOpen}:Props) {
  const [open,setOpen]=useState(false),[query,setQuery]=useState(""),[active,setActive]=useState(0);
  const root=useRef<HTMLDivElement>(null),trigger=useRef<HTMLButtonElement>(null),input=useRef<HTMLInputElement>(null);
  const id=useId();
  const normalized=query.trim().toLocaleLowerCase("vi").normalize("NFD").replace(/\p{Diacritic}/gu,"").replace(/đ/g,"d");
  const shown=options.filter(o=>`${o.name} ${o.detail??""}`.toLocaleLowerCase("vi").normalize("NFD").replace(/\p{Diacritic}/gu,"").replace(/đ/g,"d").includes(normalized));
  const selected=options.find(o=>o.id===value);
  function close(){setOpen(false);trigger.current?.focus();}
  function choose(next:string){onChange(next);close();}
  useEffect(()=>{if(open){input.current?.focus();setQuery("");setActive(0);}},[open]);
  useEffect(()=>{if(open)document.getElementById(`${id}-option-${active}`)?.scrollIntoView?.({block:"nearest"});},[active,id,open]);
  useEffect(()=>{
    const outside=(e:PointerEvent)=>{if(!root.current?.contains(e.target as Node))setOpen(false);};
    document.addEventListener("pointerdown",outside);return()=>document.removeEventListener("pointerdown",outside);
  },[]);
  return <div className="search-select" ref={root} onKeyDown={e=>{if(open&&e.key==="Escape"){e.preventDefault();e.stopPropagation();close();}}} onBlur={e=>{if(!e.currentTarget.contains(e.relatedTarget as Node))setOpen(false);}}>
    <span id={`${id}-label`} className="field-label">{label}{required?" *":""}</span>
    <button ref={trigger} type="button" className="select search-select-trigger" aria-label={label} aria-haspopup="listbox" aria-expanded={open&&!disabled} aria-controls={`${id}-list`} disabled={disabled} onClick={()=>{if(!open)onOpen?.();setOpen(v=>!v);}} onKeyDown={e=>{if(e.key==="ArrowDown"){e.preventDefault();onOpen?.();setOpen(true);}}}>
      <span>{selected?.name||(value?"Mục đang chọn":placeholder)}</span><span aria-hidden="true">⌄</span>
    </button>
    {open&&!disabled&&<div className="search-select-popup">
      <input ref={input} className="input" role="combobox" aria-label={`Tìm ${label.toLocaleLowerCase("vi")}`} aria-expanded="true" aria-autocomplete="list" aria-controls={`${id}-list`} aria-activedescendant={shown[active]?`${id}-option-${active}`:undefined} placeholder="Gõ tên để tìm…" value={query} onChange={e=>{setQuery(e.target.value);setActive(0);}} onKeyDown={e=>{
        if(e.key==="Escape"){e.preventDefault();e.stopPropagation();close();}
        if(e.key==="ArrowDown"||e.key==="ArrowUp"){e.preventDefault();setActive(n=>Math.max(0,Math.min(shown.length-1,n+(e.key==="ArrowDown"?1:-1))));}
        if(e.key==="Enter"){e.preventDefault();if(shown[active])choose(shown[active].id);}
      }}/>
      <div id={`${id}-list`} role="listbox" aria-labelledby={`${id}-label`} className="search-select-options">
        {shown.map((o,index)=><button type="button" role="option" id={`${id}-option-${index}`} aria-selected={value===o.id} className={`search-select-option ${index===active?"is-active":""}`} key={o.id} onClick={()=>choose(o.id)}><span>{o.name}</span>{o.detail&&<small>{o.detail}</small>}</button>)}
      </div>
      {loading&&<p role="status">Đang tải lựa chọn…</p>}
      {!loading&&!error&&!shown.length&&<p role="status">{query?"Không tìm thấy trong danh sách đã tải.":"Chưa có lựa chọn phù hợp."}</p>}
      {error&&<p role="alert">{error} {onRetry&&<button type="button" className="filter-reset" onClick={onRetry}>Thử lại</button>}</p>}
      <div className="search-select-footer">{!required&&value&&<button type="button" className="btn btn-ghost" onClick={()=>choose("")}>Bỏ lựa chọn</button>}{onMore&&<button type="button" className="btn btn-secondary" disabled={loading} onClick={onMore}>Tải thêm lựa chọn</button>}</div>
      {onMore&&<small>Tìm trong danh sách đã tải. Tải thêm nếu chưa thấy mục cần chọn.</small>}
    </div>}
  </div>;
}
