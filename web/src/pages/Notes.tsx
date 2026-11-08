import { useEffect, useState, useRef, useMemo } from "react";
import { useSearchParams } from "react-router-dom";
import { apiFetch } from "../api/client";
import { useToast } from "../components/ToastContext";
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
  const [showPreview, setShowPreview] = useState<boolean>(false);
  const [mobileEditorOpen, setMobileEditorOpen] = useState<boolean>(false);

  // Active note editor state
  const [editTitle, setEditTitle] = useState("");
  const [editContent, setEditContent] = useState("");
  const [editScope, setEditScope] = useState<"PERSONAL" | "PROJECT" | "SHARED">("PERSONAL");
  const [editTags, setEditTags] = useState("");
  const [editPinned, setEditPinned] = useState(false);
  const [editProjectId, setEditProjectId] = useState<string>("");

  const [projects, setProjects] = useState<Array<{ id: string; name: string }>>([]);

  const activeNote = useMemo(
    () => notes.find((n) => n.id === selectedId) || null,
    [notes, selectedId]
  );

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

  const fetchProjects = async () => {
    try {
      const res = await apiFetch<any>(`/projects?limit=50`);
      const items = (res?.items || res?.data?.items || res || []) as Array<{ id: string; name: string }>;
      setProjects(items);
    } catch {}
  };

  useEffect(() => {
    fetchNotes();
    fetchProjects();
  }, []);

  // Sync editor fields when active note changes
  useEffect(() => {
    if (activeNote) {
      setEditTitle(activeNote.title || "");
      setEditContent(activeNote.content || "");
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

  // Debounced Auto-save
  const saveTimeoutRef = useRef<any>(null);
  const handleContentChange = (newTitle: string, newContent: string) => {
    setEditTitle(newTitle);
    setEditContent(newContent);
    if (!selectedId) return;

    if (saveTimeoutRef.current) clearTimeout(saveTimeoutRef.current);
    saveTimeoutRef.current = setTimeout(async () => {
      setSaving(true);
      try {
        const payload = {
          title: newTitle.trim() || "Ghi chú không tên",
          content: newContent,
          scope: editScope,
          tags: editTags,
          isPinned: editPinned,
          projectId: editProjectId || null,
        };
        const updated = await apiFetch<any>(`/notes/${selectedId}`, {
          method: "PUT",
          body: JSON.stringify(payload),
        });
        const noteData = updated?.data || updated;
        setNotes((prev) =>
          prev.map((n) => (n.id === selectedId ? { ...n, ...noteData } : n))
        );
      } catch (e: any) {
        // silent fail on draft debounce
      } finally {
        setSaving(false);
      }
    }, 800);
  };

  const handleCreateNote = async () => {
    try {
      const payload = {
        title: "Ghi chú mới",
        content: "",
        scope: filterScope !== "ALL" ? filterScope : "PERSONAL",
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
    try {
      const res = await apiFetch<any>(`/notes/${noteId}/pin`, { method: "PATCH" });
      const updated = res?.data || res;
      setNotes((prev) =>
        prev.map((n) => (n.id === noteId ? { ...n, isPinned: !n.isPinned } : n))
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
    if (!selectedId || !activeNote) return;
    const deletedNote = activeNote;
    const deletedId = selectedId;

    try {
      await apiFetch(`/notes/${deletedId}`, { method: "DELETE" });
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
    if (!selectedId) return;
    try {
      const payload = {
        title: editTitle,
        content: editContent,
        scope: updates.scope ?? editScope,
        tags: updates.tags !== undefined ? updates.tags : editTags,
        isPinned: updates.isPinned !== undefined ? updates.isPinned : editPinned,
        projectId: updates.projectId !== undefined ? updates.projectId : (editProjectId || null),
      };
      const res = await apiFetch<any>(`/notes/${selectedId}`, {
        method: "PUT",
        body: JSON.stringify(payload),
      });
      const updated = res?.data || res;
      setNotes((prev) =>
        prev.map((n) => (n.id === selectedId ? { ...n, ...updated } : n))
      );
      showToast({ type: "success", message: "Đã lưu thông tin ghi chú" });
    } catch {
      showToast({ type: "error", message: "Lỗi lưu thông tin ghi chú" });
    }
  };

  // Insert markdown helper tokens
  const insertMarkdown = (before: string, after: string = "") => {
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
      {/* Top action bar */}
      <div className="flex items-center justify-between pb-4 border-b border-slate-200 dark:border-slate-800">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
            <AppIcon name="files" size={24} className="text-primary" />
            <span>Ghi chú</span>
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            Quản lý ghi chú cá nhân, dự án, chia sẻ nhóm với Markdown editor
          </p>
        </div>
        <button
          onClick={handleCreateNote}
          className="btn btn-primary shadow-xs hover:shadow-md transition flex items-center gap-2"
        >
          <AppIcon name="plus" size={16} />
          <span>Tạo ghi chú mới</span>
        </button>
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
                placeholder="Tìm ghi chú hoặc tags..."
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
                <span className="text-slate-400 text-[11px]">Tags:</span>
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
                        onClick={(e) => handleTogglePin(e, note.id)}
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
                      onClick={() => insertMarkdown("**", "**")}
                      title="In đậm (Bold)"
                      className="px-2 py-1 rounded hover:bg-slate-200 dark:hover:bg-slate-700 font-bold"
                    >
                      B
                    </button>
                    <button
                      onClick={() => insertMarkdown("*", "*")}
                      title="In nghiêng (Italic)"
                      className="px-2 py-1 rounded hover:bg-slate-200 dark:hover:bg-slate-700 italic"
                    >
                      I
                    </button>
                    <button
                      onClick={() => insertMarkdown("### ")}
                      title="Tiêu đề"
                      className="px-2 py-1 rounded hover:bg-slate-200 dark:hover:bg-slate-700 font-semibold"
                    >
                      H3
                    </button>
                    <button
                      onClick={() => insertMarkdown("- ")}
                      title="Danh sách gạch đầu dòng"
                      className="px-2 py-1 rounded hover:bg-slate-200 dark:hover:bg-slate-700"
                    >
                      • List
                    </button>
                    <button
                      onClick={() => insertMarkdown("- [ ] ")}
                      title="Checklist công việc"
                      className="px-2 py-1 rounded hover:bg-slate-200 dark:hover:bg-slate-700 font-mono"
                    >
                      ☑ Task
                    </button>
                    <button
                      onClick={() => insertMarkdown("`", "`")}
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

              {/* Title input */}
              <div className="px-6 pt-5 pb-2">
                <input
                  type="text"
                  placeholder="Tiêu đề ghi chú..."
                  value={editTitle}
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
                    value={editContent}
                    onChange={(e) => handleContentChange(editTitle, e.target.value)}
                    placeholder="Bắt đầu viết ghi chú bằng Markdown... (# Tiêu đề, - Checklist, **in đậm**)"
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
                value={editScope}
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
                  value={editProjectId}
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
                placeholder="idea, sprint, design..."
                value={editTags}
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
                type="button"
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
                onClick={handleDeleteNote}
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
