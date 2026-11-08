import React, { useEffect, useState, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { apiFetch } from "../api/client";
import AppIcon from "./AppIcon";

interface SearchResult {
  id: string;
  type: "task" | "project" | "note";
  title: string;
  subtitle?: string;
  url: string;
}

export default function GlobalSearchModal({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();

  useEffect(() => {
    if (open) {
      setQuery("");
      setResults([]);
      setSelectedIndex(0);
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [open]);

  useEffect(() => {
    if (!query.trim() || !open) {
      setResults([]);
      return;
    }

    const timer = setTimeout(async () => {
      setLoading(true);
      try {
        const q = query.trim().toLowerCase();
        const items: SearchResult[] = [];

        // 1. Search Tasks
        try {
          const taskRes = await apiFetch<any>(`/tasks?limit=10`);
          const taskItems = (taskRes?.items || taskRes?.data?.items || taskRes || []) as any[];
          for (const t of taskItems) {
            if (t.title?.toLowerCase().includes(q) || t.description?.toLowerCase().includes(q)) {
              items.push({
                id: t.id,
                type: "task",
                title: t.title,
                subtitle: `Công việc • ${t.state ?? "TODO"} • ${t.priority ?? "MEDIUM"}`,
                url: `/tasks/${t.id}`,
              });
            }
          }
        } catch {}

        // 2. Search Projects
        try {
          const projRes = await apiFetch<any>(`/projects?limit=10`);
          const projItems = (projRes?.items || projRes?.data?.items || projRes || []) as any[];
          for (const p of projItems) {
            if (p.name?.toLowerCase().includes(q) || p.description?.toLowerCase().includes(q)) {
              items.push({
                id: p.id,
                type: "project",
                title: p.name,
                subtitle: `Dự án • ${p.status ?? "PLANNING"}`,
                url: `/projects?id=${p.id}`,
              });
            }
          }
        } catch {}

        // 3. Search Notes
        try {
          const noteRes = await apiFetch<any>(`/notes?search=${encodeURIComponent(q)}`);
          const noteItems = (noteRes?.data || noteRes || []) as any[];
          for (const n of noteItems) {
            items.push({
              id: n.id,
              type: "note",
              title: n.title,
              subtitle: `Ghi chú • ${n.scope ?? "PERSONAL"}`,
              url: `/notes?id=${n.id}`,
            });
          }
        } catch {}

        setResults(items.slice(0, 8));
        setSelectedIndex(0);
      } finally {
        setLoading(false);
      }
    }, 250);

    return () => clearTimeout(timer);
  }, [query, open]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Escape") {
      onClose();
    } else if (e.key === "ArrowDown") {
      e.preventDefault();
      setSelectedIndex((prev) => (results.length > 0 ? (prev + 1) % results.length : 0));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setSelectedIndex((prev) => (results.length > 0 ? (prev - 1 + results.length) % results.length : 0));
    } else if (e.key === "Enter" && results[selectedIndex]) {
      e.preventDefault();
      navigate(results[selectedIndex].url);
      onClose();
    }
  };

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-start justify-center pt-20 bg-slate-900/50 backdrop-blur-xs p-4"
      onClick={onClose}
    >
      <div
        className="w-full max-w-xl rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-2xl overflow-hidden animate-scale-in"
        onClick={(e) => e.stopPropagation()}
        onKeyDown={handleKeyDown}
      >
        <div className="flex items-center gap-3 px-4 py-3 border-b border-slate-200 dark:border-slate-800">
          <AppIcon name="search" size={20} className="text-slate-400" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Tìm kiếm công việc, dự án, ghi chú... (Nhấn Esc để đóng)"
            className="flex-1 bg-transparent text-slate-900 dark:text-slate-100 placeholder-slate-400 focus:outline-hidden text-sm"
          />
          {loading && (
            <span className="text-xs text-slate-400 animate-pulse">Đang tìm...</span>
          )}
        </div>

        <div className="max-h-80 overflow-y-auto p-2">
          {query.trim() && results.length === 0 && !loading && (
            <div className="p-6 text-center text-sm text-slate-500">
              Không tìm thấy kết quả phù hợp cho "{query}"
            </div>
          )}

          {results.map((item, idx) => (
            <div
              key={item.id + item.type}
              onClick={() => {
                navigate(item.url);
                onClose();
              }}
              onMouseEnter={() => setSelectedIndex(idx)}
              className={`flex items-center gap-3 px-3.5 py-2.5 rounded-xl cursor-pointer text-sm transition-colors ${
                idx === selectedIndex
                  ? "bg-slate-100 dark:bg-slate-800 text-slate-900 dark:text-white"
                  : "text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-850"
              }`}
            >
              <span className="shrink-0 p-1.5 rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-500">
                <AppIcon
                  name={item.type === "task" ? "tasks" : item.type === "project" ? "project" : "files"}
                  size={16}
                />
              </span>
              <div className="flex-1 min-w-0">
                <div className="font-medium truncate">{item.title}</div>
                {item.subtitle && (
                  <div className="text-xs text-slate-400 truncate">{item.subtitle}</div>
                )}
              </div>
              <span className="text-xs text-slate-400">↵ Chọn</span>
            </div>
          ))}

          {!query.trim() && (
            <div className="p-4 text-xs text-slate-400 flex items-center justify-between">
              <span>Gợi ý: Nhập tên task, tên dự án hoặc từ khóa trong ghi chú</span>
              <span className="border border-slate-200 dark:border-slate-700 rounded px-1.5 py-0.5">Esc đóng</span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
