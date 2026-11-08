import { createContext, useContext, useState, useCallback, ReactNode } from "react";

export interface ToastItem {
  id: string;
  type: "success" | "error" | "info" | "warning";
  message: string;
  undoAction?: () => void;
  undoLabel?: string;
}

interface ToastContextType {
  showToast: (toast: Omit<ToastItem, "id"> & { durationMs?: number }) => string;
  hideToast: (id: string) => void;
}

const ToastContext = createContext<ToastContextType | undefined>(undefined);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const hideToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const showToast = useCallback(
    ({
      type,
      message,
      undoAction,
      undoLabel = "Hoàn tác",
      durationMs = 4500,
    }: Omit<ToastItem, "id"> & { durationMs?: number }) => {
      const id = "toast-" + Math.random().toString(36).substring(2, 9);
      const item: ToastItem = { id, type, message, undoAction, undoLabel };
      setToasts((prev) => [...prev, item]);

      if (durationMs > 0) {
        setTimeout(() => {
          hideToast(id);
        }, durationMs);
      }
      return id;
    },
    [hideToast]
  );

  return (
    <ToastContext.Provider value={{ showToast, hideToast }}>
      {children}
      {/* Toast container floating at bottom right */}
      <div
        aria-live="polite"
        className="fixed bottom-5 right-5 z-50 flex flex-col gap-2.5 max-w-sm w-full pointer-events-none"
      >
        {toasts.map((t) => (
          <div
            key={t.id}
            role="alert"
            className={`pointer-events-auto flex items-center justify-between gap-3 p-3.5 rounded-xl border shadow-lg text-sm transition-all duration-200 animate-slide-up ${
              t.type === "success"
                ? "bg-emerald-50 border-emerald-200 text-emerald-900 dark:bg-emerald-950 dark:border-emerald-800 dark:text-emerald-100"
                : t.type === "error"
                ? "bg-rose-50 border-rose-200 text-rose-900 dark:bg-rose-950 dark:border-rose-800 dark:text-rose-100"
                : t.type === "warning"
                ? "bg-amber-50 border-amber-200 text-amber-900 dark:bg-amber-950 dark:border-amber-800 dark:text-amber-100"
                : "bg-slate-50 border-slate-200 text-slate-900 dark:bg-slate-900 dark:border-slate-800 dark:text-slate-100"
            }`}
          >
            <span className="flex-1 font-medium">{t.message}</span>
            {t.undoAction && (
              <button
                type="button"
                onClick={() => {
                  t.undoAction?.();
                  hideToast(t.id);
                }}
                className="font-semibold text-primary underline hover:opacity-80 px-1 py-0.5"
              >
                {t.undoLabel}
              </button>
            )}
            <button
              type="button"
              onClick={() => hideToast(t.id)}
              className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 text-base leading-none p-1"
              aria-label="Đóng"
            >
              ×
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    return {
      showToast: () => "",
      hideToast: () => {},
    };
  }
  return ctx;
}
