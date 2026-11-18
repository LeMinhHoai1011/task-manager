import SearchSelect from "../components/SearchSelect";
import { useEffect, useState, useRef, useMemo } from "react";
import { useBlocker, useSearchParams } from "react-router-dom";
import { apiFetch } from "../api/client";
import { useToast } from "../components/ToastContext";
import {useAccess,type TaskAccess} from "../auth/AccessContext";
import {useAuth} from "../auth/AuthContext";
import {unwrapData} from "../api/client";
import AppIcon from "../components/AppIcon";

export interface NoteItem {
  id: string;
  workspaceId?: string;
  projectId?: string;
  taskId?: string;
  ownerUserId: string;
  scope: "PERSONAL" | "PROJECT" | "SHARED" | "TASK";
  title: string;
  content: string;
  tags?: string;
  isPinned: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export default function Notes() {
  const access=useAccess();const {user}=useAuth();
  const [taskAccess,setTaskAccess]=useState<TaskAccess|null>(null);
  const [notes, setNotes] = useState<NoteItem[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [searchParams, setSearchParams] = useSearchParams();
  const { showToast } = useToast();

  // Filters
  const [filterScope, setFilterScope] = useState<string>("ALL");
  const [filterTag, setFilterTag] = useState<string>("");
  const [search, setSearch] = useState<string>("");
  const [createProject,setCreateProject]=useState(searchParams.get("project_id")??"");
  const [showPreview, setShowPreview] = useState<boolean>(false);
  const [mobileEditorOpen, setMobileEditorOpen] = useState<boolean>(false);

  // Active note editor state
  const [editTitle, setEditTitle] = useState("");
  const [editContent, setEditContent] = useState("");
  const [editScope, setEditScope] = useState<"PERSONAL" | "PROJECT" | "SHARED">("PERSONAL");
  const [editTags, setEditTags] = useState("");
  const [editPinned, setEditPinned] = useState(false);
  const [editProjectId, setEditProjectId] = useState<string>("");

  const projects=access.projects;

  const activeNote = useMemo(
    () => notes.find((n) => n.id === selectedId) || null,
    [notes, selectedId]
  );

  function canEdit(note:NoteItem|null):boolean {
    if(!note)return false;
    if(note.scope==="PERSONAL")return note.ownerUserId===user?.id;
    if(note.scope==="PROJECT")return access.projects.some(p=>p.id===note.projectId&&p.canManage);
    if(note.scope==="TASK")return note.id===activeNote?.id&&!!taskAccess?.metadata;
    const ws=access.workspaces.find(w=>w.id===note.workspaceId);
    return !!ws&&ws.status==="ACTIVE"&&(ws.canManage||(ws.role==="MEMBER"&&note.ownerUserId===user?.id));
  }
  const editable=canEdit(activeNote);
  useEffect(()=>{
    let current=true;setTaskAccess(null);
    if(activeNote?.scope==="TASK"&&activeNote.taskId)void apiFetch(`/tasks/${activeNote.taskId}/access`).then(r=>{if(current)setTaskAccess(unwrapData<TaskAccess>(r)??null);}).catch(()=>{});
    return()=>{current=false;};
  },[activeNote?.id,activeNote?.taskId,activeNote?.scope]);

  const fetchNotes = async () => {
    try {
      setLoading(true);
      const res = await apiFetch<any>(`/notes`);
      const items = (res?.data || res || []) as NoteItem[];
      setNotes(items);

      // Pre-select note from URL param or first available note
      const urlId = searchParams.get("id");
      if (urlId && items.some((n) => n.id === urlId)) {
        setSelectedId(urlId);
      } else if (items.length > 0 && !selectedId) {
        setSelectedId(items[0].id);
      }
    } catch (e: any) {
      showToast({ type: "error", message: "Không thể tải danh sách ghi chú" });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchNotes();
  }, []);

  const notesRef=useRef(notes);notesRef.current=notes;
  const drafts=useRef(new Map<string,{title:string;content:string}>());
  const timers=useRef(new Map<string,ReturnType<typeof setTimeout>>());
  const saveQueue=useRef(Promise.resolve(true));
  const blocker=useBlocker(({currentLocation,nextLocation})=>drafts.current.size>0&&currentLocation.pathname!==nextLocation.pathname);
  const [leaving,setLeaving]=useState(false);
  useEffect(()=>{if(blocker.state==="blocked")document.getElementById("note-unsaved-navigation")?.focus();},[blocker.state]);
  const syncedId=useRef<string|null>(null);
  const [saveError,setSaveError]=useState(false);
  const [dirty,setDirty]=useState(false);
  function persistNote(id:string, extra:Partial<NoteItem>={}) {
    const draft=drafts.current.get(id);
    const job=saveQueue.current.then(async()=>{
      const note=notesRef.current.find(n=>n.id===id);if(!note)return false;
      setSaving(true);
      try {
        const response=await apiFetch<any>(`/notes/${id}`,{method:"PUT",ifMatch:String(note.version),body:{title:draft?.title.trim()||note.title||"Ghi chú không tên",content:draft?.content??note.content,...extra}});
        const updated=response.data??response;
        notesRef.current=notesRef.current.map(n=>n.id===id?{...n,...updated}:n);
        setNotes(notesRef.current);
        if(drafts.current.get(id)===draft)drafts.current.delete(id);
        setDirty(drafts.current.size>0);setSaveError(false);return true;
      }catch{setSaveError(true);showToast({type:"error",message:"Chưa lưu được ghi chú. Nội dung nhập vẫn được giữ lại. Hãy thử lưu lại."});return false;}
      finally{setSaving(false);}
    });saveQueue.current=job;return job;
  }
  function saveNow(id:string){const timer=timers.current.get(id);if(timer)clearTimeout(timer);timers.current.delete(id);return persistNote(id);}
  useEffect(()=>{
    const warn=(event:BeforeUnloadEvent)=>{if(drafts.current.size){event.preventDefault();event.returnValue="";}};
    window.addEventListener("beforeunload",warn);
    return()=>{window.removeEventListener("beforeunload",warn);for(const id of timers.current.keys())void saveNow(id);};
  },[]);
  async function saveAndLeave(){
    if(leaving||blocker.state!=="blocked")return;
    setLeaving(true);
    try{
      for(const id of Array.from(drafts.current.keys()))await saveNow(id);
      await saveQueue.current;
      if(!drafts.current.size)blocker.proceed();
    }finally{setLeaving(false);}
  }
  function discardAndLeave(){
    if(leaving||blocker.state!=="blocked")return;
    for(const timer of timers.current.values())clearTimeout(timer);
    timers.current.clear();drafts.current.clear();setDirty(false);blocker.proceed();
  }
  // A save response must not replace text typed after that request began.
  useEffect(() => {
    if(syncedId.current===activeNote?.id)return;
    syncedId.current=activeNote?.id??null;
    if (activeNote) {
      const draft=drafts.current.get(activeNote.id);
      setEditTitle(draft?.title??activeNote.title??"");
      setEditContent(draft?.content??activeNote.content??"");
      setEditScope((activeNote.scope as any) || "PERSONAL");
      setEditTags(activeNote.tags || "");
      setEditPinned(activeNote.isPinned || false);
      setEditProjectId(activeNote.projectId || "");
    } else {
      setEditTitle("");
      setEditContent("");
      setEditScope("PERSONAL");
      setEditTags("");
      setEditPinned(false);
      setEditProjectId("");
    }
  }, [activeNote]);

  const handleContentChange = (newTitle:string,newContent:string) => {
    if(!editable||!selectedId)return;
    setEditTitle(newTitle);setEditContent(newContent);setDirty(true);
    drafts.current.set(selectedId,{title:newTitle,content:newContent});
    const timer=timers.current.get(selectedId);if(timer)clearTimeout(timer);
    const id=selectedId;timers.current.set(id,setTimeout(()=>{timers.current.delete(id);void persistNote(id);},800));
  };

  const handleCreateNote = async () => {
    if(createProject&&!access.projects.some(p=>p.id===createProject&&p.canManage)){showToast({type:"error",message:"Bạn không có quyền tạo ghi chú trong dự án này"});return;}
    try {
      const payload = {
        title: "Ghi chú mới",
        content: "",
        scope: createProject?"PROJECT":"PERSONAL",
        ...(createProject?{projectId:createProject}:{}),
        isPinned: false,
      };
      const res = await apiFetch<any>(`/notes`, {
        method: "POST",
        body: JSON.stringify(payload),
      });
      const created = (res?.data || res) as NoteItem;
      setNotes((prev) => [created, ...prev]);
      setSelectedId(created.id);
      setMobileEditorOpen(true);
      showToast({ type: "success", message: "Đã tạo ghi chú mới" });
    } catch (e: any) {
      showToast({ type: "error", message: "Không thể tạo ghi chú" });
    }
  };

  const handleTogglePin = async (e: React.MouseEvent, noteId: string) => {
    e.stopPropagation();
    if(!canEdit(notes.find(n=>n.id===noteId)??null))return;
    try {
      const res = await apiFetch<any>(`/notes/${noteId}/pin`, { method: "PATCH" });
      const updated = res?.data || res;
      setNotes((prev) =>
          prev.map((n) => (n.id === noteId ? { ...n, ...updated } : n))
      );
      if (selectedId === noteId) setEditPinned(!editPinned);
      showToast({
        type: "info",
        message: updated.isPinned ? "Đã ghim ghi chú" : "Đã bỏ ghim ghi chú",
      });
    } catch {
      showToast({ type: "error", message: "Lỗi cập nhật ghim" });
    }
  };

  const handleDeleteNote = async () => {
    if (!selectedId || !activeNote || !editable) return;
    if(saving)return;
    const timer=timers.current.get(selectedId);if(timer)clearTimeout(timer);timers.current.delete(selectedId);
    const deletedNote = activeNote;
    const deletedId = selectedId;

    try {
      await apiFetch(`/notes/${deletedId}`, { method: "DELETE" });
      drafts.current.delete(deletedId);setDirty(drafts.current.size>0);
      setNotes((prev) => prev.filter((n) => n.id !== deletedId));
      setSelectedId(null);
      setMobileEditorOpen(false);

      showToast({
        type: "warning",
        message: `Đã xóa ghi chú "${deletedNote.title}"`,
        undoAction: async () => {
          try {
            const restored = await apiFetch<any>(`/notes/${deletedId}/restore`, {
              method: "POST",
            });
            const item = (restored?.data || restored) as NoteItem;
            setNotes((prev) => [item, ...prev]);
            setSelectedId(item.id);
            showToast({ type: "success", message: "Đã khôi phục ghi chú" });
          } catch {
            showToast({ type: "error", message: "Không thể khôi phục ghi chú" });
          }
        },
      });
    } catch {
      showToast({ type: "error", message: "Không thể xóa ghi chú" });
    }
  };

  const handleMetadataChange = async (updates: Partial<NoteItem>) => {
    if (!selectedId || !editable) return;
    const timer=timers.current.get(selectedId);if(timer)clearTimeout(timer);timers.current.delete(selectedId);
    await persistNote(selectedId,updates);
  };

  // Insert markdown helper tokens
  const insertMarkdown = (before: string, after: string = "") => {
    if(!editable)return;
    const textarea = document.getElementById("note-editor-textarea") as HTMLTextAreaElement;
    if (!textarea) return;
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const text = textarea.value;
    const selected = text.substring(start, end);
    const replacement = before + selected + after;
    const newContent = text.substring(0, start) + replacement + text.substring(end);
    handleContentChange(editTitle, newContent);
    setTimeout(() => {
      textarea.focus();
      textarea.setSelectionRange(start + before.length, end + before.length);
    }, 20);
  };

  // Filtered Notes
  const filteredNotes = useMemo(() => {
    return notes.filter((n) => {
      if (filterScope === "PINNED" && !n.isPinned) return false;
      if (filterScope !== "ALL" && filterScope !== "PINNED" && n.scope !== filterScope) return false;
      if (filterTag && (!n.tags || !n.tags.toLowerCase().includes(filterTag.toLowerCase()))) return false;
      if (search) {
        const q = search.toLowerCase();
        return (
          n.title?.toLowerCase().includes(q) ||
          n.content?.toLowerCase().includes(q) ||
          n.tags?.toLowerCase().includes(q)
        );
      }
      return true;
    });
  }, [notes, filterScope, filterTag, search]);

  // Unique tags across all notes
  const allTags = useMemo(() => {
    const set = new Set<string>();
    notes.forEach((n) => {
      if (n.tags) {
        n.tags.split(",").map((t) => t.trim()).filter(Boolean).forEach((t) => set.add(t));
      }
    });
    return Array.from(set);
  }, [notes]);

  return (
    <div className="flex flex-col h-[calc(100vh-5rem)]">
      {saveError&&<p role="alert" className="notice">Chưa lưu được thay đổi. Giữ trang này mở và nhấn Lưu ngay để thử lại.</p>}
      {editable&&selectedId&&<div className="flex items-center justify-end gap-3 pb-2"><span role="status" className="text-xs text-slate-500">{saving?"Đang lưu…":dirty?"Có thay đổi chưa lưu":"Đã lưu"}</span><button className="btn btn-secondary btn-sm" disabled={saving} onClick={()=>void saveNow(selectedId)}>Lưu ngay</button></div>}
      {blocker.state==="blocked"&&<div id="note-unsaved-navigation" tabIndex={-1} role="alertdialog" aria-label="Ghi chú chưa lưu" className="notice">
        <p>Bạn có ghi chú chưa lưu. Lưu thành công trước khi rời trang hoặc chọn bỏ thay đổi.</p>
        <div className="mt-2 flex flex-wrap gap-2">
          <button className="btn btn-primary" disabled={leaving} onClick={()=>void saveAndLeave()}>{leaving?"Đang lưu…":"Lưu và rời trang"}</button>
          <button className="btn btn-secondary" disabled={leaving} onClick={()=>blocker.reset()}>Ở lại</button>
          <button className="btn btn-danger" disabled={leaving||saving} onClick={discardAndLeave}>Bỏ thay đổi và rời trang</button>
        </div>
      </div>}
      {/* Top action bar */}
      <div className="flex items-center justify-between pb-4 border-b border-slate-200 dark:border-slate-800">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
            <AppIcon name="files" size={24} className="text-primary" />
            <span>Thông tin đã lưu</span>
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            Lưu nội dung cần nhớ. Phân biệt rõ ghi chú riêng và thông tin chia sẻ trong dự án.
          </p>
        </div>
        <div className="flex flex-wrap gap-2"><SearchSelect label="Nơi tạo ghi chú" value={createProject} onChange={setCreateProject} placeholder="Cá nhân (riêng tư)" options={access.projects.filter(p=>p.canManage).map(p=>({id:p.id,name:p.name}))}/><button
          onClick={handleCreateNote}
          className="btn btn-primary shadow-xs hover:shadow-md transition flex items-center gap-2"
        >
          <AppIcon name="plus" size={16} />
          <span>Tạo ghi chú mới</span>
        </button></div>
      </div>

      {/* 3-Column Workspace */}
      <div className="flex-1 flex overflow-hidden pt-4 gap-4">
        {/* COL 1: Notes Sidebar List */}
        <div
          className={`w-full md:w-80 shrink-0 flex flex-col rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 overflow-hidden ${
            mobileEditorOpen ? "hidden md:flex" : "flex"
          }`}
        >
          {/* Search & Scope Filters */}
          <div className="p-3 border-b border-slate-200 dark:border-slate-800 space-y-2.5">
            <div className="relative">
              <AppIcon
                name="search"
                size={16}
                className="absolute left-3 top-2.5 text-slate-400 pointer-events-none"
              />
              <input
                type="text"
                placeholder="Tìm ghi chú hoặc nhãn..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="input pl-9 text-xs"
              />
            </div>
            {/* Scope tabs */}
            <div className="flex gap-1 overflow-x-auto text-xs pb-1">
              {[
                { id: "ALL", label: "Tất cả" },
                { id: "PINNED", label: "Đã ghim" },
                { id: "PERSONAL", label: "Cá nhân" },
                { id: "PROJECT", label: "Dự án" },
                { id: "SHARED", label: "Chia sẻ" },
              ].map((tab) => (
                <button
                  key={tab.id}
                  onClick={() => setFilterScope(tab.id)}
                  className={`px-2.5 py-1 rounded-lg font-medium transition whitespace-nowrap ${
                    filterScope === tab.id
                      ? "bg-primary text-white"
                      : "text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800"
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            {/* Tag Pills */}
            {allTags.length > 0 && (
              <div className="flex items-center gap-1.5 overflow-x-auto text-xs pt-1">
                <span className="text-slate-400 text-[11px]">Nhãn:</span>
                {allTags.slice(0, 6).map((tag) => (
                  <button
                    key={tag}
                    onClick={() => setFilterTag(filterTag === tag ? "" : tag)}
                    className={`px-2 py-0.5 rounded-full border text-[11px] font-medium transition ${
                      filterTag === tag
                        ? "bg-slate-900 text-white border-slate-900 dark:bg-white dark:text-slate-900"
                        : "border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-400 hover:border-slate-400"
                    }`}
                  >
                    #{tag}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Note Items List */}
          <div className="flex-1 overflow-y-auto divide-y divide-slate-100 dark:divide-slate-800/60 p-2 space-y-1">
            {loading ? (
              <div className="p-6 text-center text-xs text-slate-400">Đang tải ghi chú...</div>
            ) : filteredNotes.length === 0 ? (
              <div className="p-6 text-center text-xs text-slate-400">
                Không tìm thấy ghi chú nào phù hợp
              </div>
            ) : (
              filteredNotes.map((note) => {
                const isSelected = note.id === selectedId;
                return (
                  <div
                    key={note.id}
                    onClick={() => {
                      setSelectedId(note.id);
                      setMobileEditorOpen(true);
                      setSearchParams({ id: note.id });
                    }}
                    className={`p-3 rounded-xl cursor-pointer transition relative group ${
                      isSelected
                        ? "bg-slate-100 dark:bg-slate-800 border-l-4 border-primary"
                        : "hover:bg-slate-50 dark:hover:bg-slate-850"
                    }`}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <h4 className="font-semibold text-sm text-slate-900 dark:text-slate-100 truncate flex-1">
                        {note.title || "Ghi chú không tên"}
                      </h4>
                      <button
                        disabled={!canEdit(note)} onClick={(e) => handleTogglePin(e, note.id)}
                        title={note.isPinned ? "Bỏ ghim" : "Ghim"}
                        className={`shrink-0 p-1 rounded-sm text-xs transition ${
                          note.isPinned
                            ? "text-amber-500"
                            : "text-slate-300 dark:text-slate-600 hover:text-slate-500"
                        }`}
                      >
                        ★
                      </button>
                    </div>

                    <p className="text-xs text-slate-500 dark:text-slate-400 line-clamp-2 mt-1">
                      {note.content?.replace(/[#*`\-_]/g, "") || "Trống..."}
                    </p>

                    <div className="flex items-center gap-2 mt-2 text-[11px] text-slate-400">
                      <span className="uppercase font-semibold tracking-wider text-[10px] px-1.5 py-0.5 rounded bg-slate-200/60 dark:bg-slate-700/60 text-slate-700 dark:text-slate-300">
                        {note.scope}
                      </span>
                      <span>
                        {new Date(note.updatedAt).toLocaleDateString("vi-VN", {
                          month: "numeric",
                          day: "numeric",
                        })}
                      </span>
                      {note.tags && (
                        <span className="truncate max-w-[100px] text-primary">#{note.tags}</span>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* COL 2: Main Editor & Preview */}
        <div
          className={`flex-1 flex flex-col rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 overflow-hidden ${
            !mobileEditorOpen ? "hidden md:flex" : "flex"
          }`}
        >
          {activeNote ? (
            <>
              {/* Editor Header / Toolbar */}
              <div className="flex flex-wrap items-center justify-between gap-2 px-4 py-3 border-b border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-850/50">
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setMobileEditorOpen(false)}
                    className="md:hidden p-1.5 rounded-lg border text-slate-600 hover:bg-slate-100 dark:text-slate-300"
                  >
                    ←
                  </button>

                  {/* Markdown formatting tools */}
                  <div className="flex items-center gap-1 text-xs">
                    <button
                      disabled={!editable} onClick={() => insertMarkdown("**", "**")}
                      title="In đậm (Bold)"
                      className="px-2 py-1 rounded hover:bg-slate-200 dark:hover:bg-slate-700 font-bold"
                    >
                      B
                    </button>
                    <button
                      disabled={!editable} onClick={() => insertMarkdown("*", "*")}
                      title="In nghiêng (Italic)"
                      className="px-2 py-1 rounded hover:bg-slate-200 dark:hover:bg-slate-700 italic"
                    >
                      I
                    </button>
                    <button
                      disabled={!editable} onClick={() => insertMarkdown("### ")}
                      title="Tiêu đề"
                      className="px-2 py-1 rounded hover:bg-slate-200 dark:hover:bg-slate-700 font-semibold"
                    >
                      H3
                    </button>
                    <button
                      disabled={!editable} onClick={() => insertMarkdown("- ")}
                      title="Danh sách gạch đầu dòng"
                      className="px-2 py-1 rounded hover:bg-slate-200 dark:hover:bg-slate-700"
                    >
                      • List
                    </button>
                    <button
                      disabled={!editable} onClick={() => insertMarkdown("- [ ] ")}
                      title="danh sách kiểm tra công việc"
                      className="px-2 py-1 rounded hover:bg-slate-200 dark:hover:bg-slate-700 font-mono"
                    >
                      ☑ công việc
                    </button>
                    <button
                      disabled={!editable} onClick={() => insertMarkdown("`", "`")}
                      title="Mã (Code)"
                      className="px-2 py-1 rounded hover:bg-slate-200 dark:hover:bg-slate-700 font-mono"
                    >
                      &lt;/&gt;
                    </button>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  {saving && (
                    <span className="text-xs text-slate-400 flex items-center gap-1">
                      <span className="h-1.5 w-1.5 rounded-full bg-primary animate-ping" />
                      Đang lưu...
                    </span>
                  )}
                  <button
                    onClick={() => setShowPreview(!showPreview)}
                    className={`px-3 py-1.5 rounded-lg text-xs font-semibold border transition ${
                      showPreview
                        ? "bg-primary text-white border-primary"
                        : "border-slate-300 dark:border-slate-700 text-slate-700 dark:text-slate-300 hover:bg-slate-100"
                    }`}
                  >
                    {showPreview ? "Chỉnh sửa" : "Xem trước (Preview)"}
                  </button>
                </div>
              </div>

              {!editable&&<p role="note" className="px-6 text-sm">Ghi chú được chia sẻ chỉ đọc. Bạn vẫn có thể tạo ghi chú cá nhân riêng.</p>}
              {/* Title input */}
              <div className="px-6 pt-5 pb-2">
                <input
                  type="text"
                  placeholder="Tiêu đề ghi chú..."
                  readOnly={!editable} value={editTitle}
                  onChange={(e) => handleContentChange(e.target.value, editContent)}
                  className="w-full text-2xl font-bold bg-transparent border-0 focus:outline-hidden text-slate-900 dark:text-white placeholder-slate-300 dark:placeholder-slate-600"
                />
              </div>

              {/* Content area */}
              <div className="flex-1 overflow-y-auto px-6 pb-6">
                {showPreview ? (
                  <div className="prose dark:prose-invert max-w-none py-2 text-sm leading-relaxed text-slate-800 dark:text-slate-200 space-y-2">
                    {editContent ? (
                      editContent.split("\n").map((line, i) => {
                        if (line.startsWith("### ")) {
                          return (
                            <h3 key={i} className="text-lg font-bold mt-4 mb-2 text-slate-900 dark:text-white">
                              {line.substring(4)}
                            </h3>
                          );
                        }
                        if (line.startsWith("## ")) {
                          return (
                            <h2 key={i} className="text-xl font-bold mt-5 mb-2 text-slate-900 dark:text-white">
                              {line.substring(3)}
                            </h2>
                          );
                        }
                        if (line.startsWith("- [ ] ")) {
                          return (
                            <div key={i} className="flex items-center gap-2 text-slate-700 dark:text-slate-300">
                              <input type="checkbox" disabled className="rounded text-primary" />
                              <span>{line.substring(6)}</span>
                            </div>
                          );
                        }
                        if (line.startsWith("- [x] ") || line.startsWith("- [X] ")) {
                          return (
                            <div key={i} className="flex items-center gap-2 line-through text-slate-400">
                              <input type="checkbox" checked disabled className="rounded text-primary" />
                              <span>{line.substring(6)}</span>
                            </div>
                          );
                        }
                        if (line.startsWith("- ")) {
                          return (
                            <li key={i} className="ml-4 list-disc">
                              {line.substring(2)}
                            </li>
                          );
                        }
                        return (
                          <p key={i} className="min-h-[1.25rem]">
                            {line}
                          </p>
                        );
                      })
                    ) : (
                      <p className="text-slate-400 italic">Chưa có nội dung xem trước</p>
                    )}
                  </div>
                ) : (
                  <textarea
                    id="note-editor-textarea"
                    readOnly={!editable} value={editContent}
                    onChange={(e) => handleContentChange(editTitle, e.target.value)}
                    placeholder="Bắt đầu viết ghi chú bằng Markdown... (# Tiêu đề, - danh sách kiểm tra, **in đậm**)"
                    className="w-full h-full bg-transparent resize-none border-0 focus:outline-hidden text-sm leading-relaxed text-slate-800 dark:text-slate-200 placeholder-slate-400"
                  />
                )}
              </div>
            </>
          ) : (
            <div className="flex-1 flex flex-col items-center justify-center p-8 text-center text-slate-400">
              <AppIcon name="files" size={48} className="text-slate-300 dark:text-slate-700 mb-3" />
              <p className="text-base font-medium text-slate-700 dark:text-slate-300">
                Chọn hoặc tạo ghi chú mới
              </p>
              <p className="text-xs text-slate-500 max-w-xs mt-1">
                Tất cả ghi chú được lưu an toàn theo phân quyền và hỗ trợ định dạng Markdown đầy đủ.
              </p>
            </div>
          )}
        </div>

        {/* COL 3: Metadata & Actions Panel (Desktop) */}
        {activeNote && (
          <div className="hidden lg:flex w-72 shrink-0 flex-col rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-4 space-y-4">
            <h3 className="font-bold text-sm text-slate-900 dark:text-white border-b border-slate-100 dark:border-slate-800 pb-2">
              Thuộc tính ghi chú
            </h3>

            {/* Scope selection */}
            <div>
              <label className="block text-xs font-semibold text-slate-500 mb-1">
                Phạm vi chia sẻ
              </label>
              <select
                disabled value={editScope}
                onChange={(e) => {
                  const s = e.target.value as any;
                  setEditScope(s);
                  handleMetadataChange({ scope: s });
                }}
                className="select text-xs"
              >
                <option value="PERSONAL">Cá nhân (Riêng tư)</option>
                <option value="PROJECT">Thuộc Dự án</option>
                <option value="SHARED">Không gian chung</option>
              </select>
            </div>

            {/* Project association if scope is PROJECT */}
            {editScope === "PROJECT" && (
              <div>
                <label className="block text-xs font-semibold text-slate-500 mb-1">
                  Dự án liên quan
                </label>
                <select
                  disabled value={editProjectId}
                  onChange={(e) => {
                    setEditProjectId(e.target.value);
                    handleMetadataChange({ projectId: e.target.value });
                  }}
                  className="select text-xs"
                >
                  <option value="">-- Chọn dự án --</option>
                  {projects.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </select>
              </div>
            )}

            {/* Tags Input */}
            <div>
              <label className="block text-xs font-semibold text-slate-500 mb-1">
                Thẻ (phân cách bằng dấu phẩy)
              </label>
              <input
                type="text"
                placeholder="ý tưởng, kế hoạch, thiết kế…"
                disabled={!editable} value={editTags}
                onChange={(e) => setEditTags(e.target.value)}
                onBlur={() => handleMetadataChange({ tags: editTags })}
                className="input text-xs"
              />
            </div>

            {/* Pinned switch */}
            <div className="flex items-center justify-between pt-2">
              <span className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                Ghim lên đầu
              </span>
              <button
                type="button" disabled={!editable}
                onClick={() => {
                  const nextPinned = !editPinned;
                  setEditPinned(nextPinned);
                  handleMetadataChange({ isPinned: nextPinned });
                }}
                className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                  editPinned ? "bg-primary" : "bg-slate-200 dark:bg-slate-700"
                }`}
              >
                <span
                  className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                    editPinned ? "translate-x-6" : "translate-x-1"
                  }`}
                />
              </button>
            </div>

            <div className="pt-4 border-t border-slate-100 dark:border-slate-800 text-xs text-slate-400 space-y-1">
              <div>
                Tạo lúc:{" "}
                {new Date(activeNote.createdAt).toLocaleString("vi-VN", {
                  dateStyle: "short",
                  timeStyle: "short",
                })}
              </div>
              <div>
                Cập nhật:{" "}
                {new Date(activeNote.updatedAt).toLocaleString("vi-VN", {
                  dateStyle: "short",
                  timeStyle: "short",
                })}
              </div>
              <div>Phiên bản: #{activeNote.version}</div>
            </div>

            {/* Delete button with Toast Undo */}
            <div className="mt-auto pt-4 border-t border-slate-100 dark:border-slate-800">
              <button
                disabled={!editable} onClick={handleDeleteNote}
                className="btn btn-danger w-full text-xs flex items-center justify-center gap-1.5"
              >
                <AppIcon name="close" size={14} />
                <span>Xóa ghi chú</span>
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
