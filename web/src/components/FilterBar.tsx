import { useId, useState, type ReactNode } from "react";
import AppIcon from "./AppIcon";

export type ActiveFilter = { key: string; label: string; onRemove: () => void };

type Props = {
  children: ReactNode;
  advanced?: ReactNode;
  activeFilters?: ActiveFilter[];
  onReset?: () => void;
  resultLabel?: string;
  busy?: boolean;
};

export default function FilterBar({ children, advanced, activeFilters = [], onReset, resultLabel, busy }: Props) {
  const [expanded, setExpanded] = useState(false);
  const panelId = useId();
  return (
    <section className="filter-bar" aria-label="Tìm kiếm và bộ lọc">
      <div className="filter-main">
        <div className="filter-primary">{children}</div>
        <div className="filter-tools">
          {advanced && (
            <button type="button" className={`btn btn-secondary filter-toggle ${expanded ? "is-expanded" : ""}`} aria-expanded={expanded} aria-controls={panelId} onClick={() => setExpanded(value => !value)}>
              <AppIcon name="filter" size={16} />
              <span>Lọc chi tiết</span>
              {activeFilters.length > 0 && <span className="filter-count">{activeFilters.length}</span>}
              <span aria-hidden="true" className="filter-chevron">{expanded ? "−" : "+"}</span>
            </button>
          )}
          {onReset && activeFilters.length > 0 && <button type="button" className="filter-reset" onClick={onReset}>Xóa tất cả</button>}
        </div>
      </div>
      {advanced && <div id={panelId} className="filter-advanced" hidden={!expanded}>{advanced}</div>}
      {(activeFilters.length > 0 || resultLabel || busy) && (
        <div className="filter-footer">
          <div className="filter-chips" aria-label="Điều kiện đang áp dụng">
            {activeFilters.map(filter => (
              <button key={filter.key} type="button" className="filter-chip" onClick={filter.onRemove} aria-label={`Bỏ lọc ${filter.label}`}>
                <span>{filter.label}</span><span aria-hidden="true">×</span>
              </button>
            ))}
          </div>
          <p className="filter-results" role="status" aria-live="polite">{busy ? "Đang cập nhật…" : resultLabel}</p>
        </div>
      )}
    </section>
  );
}
