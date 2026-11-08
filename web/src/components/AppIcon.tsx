import type { CSSProperties } from "react";

const paths: Record<string, string> = {
  overview: "M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z",
  personal: "M9 5H5v16h14V5h-4 M9 3h6v4H9z M8 13l2 2 5-5",
  team: "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M16 3a4 4 0 0 1 0 8 M22 21v-2a4 4 0 0 0-3-3.87 M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0",
  roles: "M12 3 3 7v5c0 5 9 9 9 9s9-4 9-9V7z M8 12l3 3 5-6",
  workspace: "M3 21V5h12v16 M15 10h6v11 M7 9h4 M7 13h4 M7 17h4 M2 21h20",
  project: "M3 7h7l2 2h9v11H3z M3 7V4h7l2 3",
  tasks: "M9 6h12 M9 12h12 M9 18h12 M3 5l1 1 2-2 M3 11l1 1 2-2 M3 17l1 1 2-2",
  calendar: "M3 5h18v16H3z M16 3v4 M8 3v4 M3 11h18 M7 15h2 M12 15h2 M7 18h2",
  files: "M14 2H5v20h14V7z M14 2v6h5 M8 13h8 M8 17h6",
  bell: "M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9 M10 21h4",
  report: "M4 3v18h17 M8 16v-4 M13 16V8 M18 16V5",
  ai: "M12 3l2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5z",
  settings: "M12 3v3 M12 18v3 M3 12h3 M18 12h3 M5.6 5.6l2.1 2.1 M16.3 16.3l2.1 2.1 M5.6 18.4l2.1-2.1 M16.3 7.7l2.1-2.1 M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0",
  logout: "M9 3H3v18h6 M14 7l5 5-5 5 M7 12h12",
  menu: "M3 6h18 M3 12h18 M3 18h18",
  close: "M6 6l12 12 M6 18 18 6",
};
export default function AppIcon({ name, size = 20, style, className }: { name: string; size?: number; style?: CSSProperties; className?: string }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" style={style} className={className}><path d={paths[name] ?? paths.tasks}/></svg>;
}
