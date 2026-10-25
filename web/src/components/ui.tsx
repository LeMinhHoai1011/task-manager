import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from "react";

type ButtonVariant =
  | "primary"
  | "secondary"
  | "danger"
  | "ghost"
  | "success"
  | "warning"
  | "indigo"
  | "purple"
  | "gray";

const btnVariant: Record<ButtonVariant, string> = {
  primary: "btn-primary",
  secondary: "btn-secondary",
  danger: "btn-danger",
  ghost: "btn-ghost",
  success: "btn-success",
  warning: "btn-warning",
  indigo: "btn-indigo",
  purple: "btn-purple",
  gray: "btn-gray",
};

export function Button({
  variant = "primary",
  size,
  className = "",
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant; size?: "sm" }) {
  return (
    <button
      className={`btn ${btnVariant[variant]} ${size === "sm" ? "btn-sm" : ""} ${className}`}
      {...rest}
    />
  );
}

export function Card({ className = "", children }: { className?: string; children: ReactNode }) {
  return <div className={`card ${className}`}>{children}</div>;
}

export function TextInput({ className = "", ...rest }: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={`input ${className}`} {...rest} />;
}

export function Select({ className = "", children, ...rest }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select className={`select ${className}`} {...rest}>
      {children}
    </select>
  );
}

const STATE_TONE: Record<string, string> = {
  TODO: "badge-todo",
  IN_PROGRESS: "badge-in-progress",
  PENDING_APPROVAL: "badge-pending",
  COMPLETED: "badge-completed",
  CANCELLED: "badge-cancelled",
  BLOCKED: "badge-blocked",
};

const PRIORITY_TONE: Record<string, string> = {
  LOW: "badge-todo",
  MEDIUM: "badge-in-progress",
  HIGH: "badge-pending",
  URGENT: "badge-blocked",
  CRITICAL: "badge-blocked",
};

export function stateBadgeClass(state: string): string {
  return STATE_TONE[state] ?? "badge-todo";
}

export function priorityBadgeClass(priority: string): string {
  return PRIORITY_TONE[priority] ?? "badge-todo";
}

export function Badge({
  state,
  priority,
  tone,
  children,
}: {
  state?: string;
  priority?: string;
  tone?: string;
  children: ReactNode;
}) {
  const cls = state ? stateBadgeClass(state) : priority ? priorityBadgeClass(priority) : (tone ?? "badge-todo");
  return <span className={`badge-state ${cls}`}>{children}</span>;
}

export function EmptyState({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="empty-state">
      <p className="font-medium text-slate-700">{title}</p>
      {description && <p className="mt-1">{description}</p>}
      {action && <div className="mt-3 flex justify-center">{action}</div>}
    </div>
  );
}

/** Hiển thị ErrorEnvelope trung tính (chỉ uiMessage đã map, không render text server). */
export function ErrorAlert({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <p role="alert" className="alert-error">
      {message}
    </p>
  );
}

export function Spinner({ label = "Loading…" }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 text-sm text-slate-500" role="status" aria-live="polite">
      <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-primary" />
      {label}
    </div>
  );
}
