import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { ChevronDown, ChevronLeft, ChevronRight } from "lucide-react";
import type { BiDateRange } from "./BiDateRangeDialog";

// ─── date helpers ───────────────────────────────────────────────────────────

const MONTHS_FR = [
  "janvier", "février", "mars", "avril", "mai", "juin",
  "juillet", "août", "septembre", "octobre", "novembre", "décembre",
];

// Monday-first week (French convention)
const WEEKDAYS_FR = ["L", "M", "M", "J", "V", "S", "D"];

const HEADLINE_FMT = new Intl.DateTimeFormat("fr-FR", {
  weekday: "short",
  day: "numeric",
  month: "short",
});

function pad(n: number) {
  return String(n).padStart(2, "0");
}

function dateToIso(d: Date) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function isoToDate(iso: string) {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d);
}

function startOfDay(d: Date) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

function firstOfMonth(d: Date) {
  return new Date(d.getFullYear(), d.getMonth(), 1);
}

function addMonths(d: Date, n: number) {
  return new Date(d.getFullYear(), d.getMonth() + n, 1);
}

function sameDay(a: Date, b: Date) {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  );
}

function capitalize(s: string) {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

function detectMode(from: Date, to: Date): BiDateRange["mode"] {
  const diff = Math.round((startOfDay(to).getTime() - startOfDay(from).getTime()) / 86_400_000);
  return diff > 60 ? "month" : "day";
}

/** Cells for a month grid (Monday-first); null = blank pad cell. */
function buildMonthMatrix(view: Date): (Date | null)[] {
  const year = view.getFullYear();
  const month = view.getMonth();
  const firstWeekday = (new Date(year, month, 1).getDay() + 6) % 7; // Mon = 0
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const cells: (Date | null)[] = [];
  for (let i = 0; i < firstWeekday; i++) cells.push(null);
  for (let d = 1; d <= daysInMonth; d++) cells.push(new Date(year, month, d));
  while (cells.length % 7 !== 0) cells.push(null);
  return cells;
}

const YEAR_MIN = 2018;
const YEAR_MAX = 2032;
const YEARS = Array.from({ length: YEAR_MAX - YEAR_MIN + 1 }, (_, i) => YEAR_MIN + i);

// ─── single MD3 calendar ────────────────────────────────────────────────────

function Md3Calendar({
  label,
  selected,
  view,
  today,
  minDate,
  onViewChange,
  onSelect,
}: {
  label: string;
  selected: Date;
  view: Date;
  today: Date;
  minDate?: Date;
  onViewChange: (next: Date) => void;
  onSelect: (next: Date) => void;
}) {
  const [yearOpen, setYearOpen] = useState(false);
  const cells = useMemo(() => buildMonthMatrix(view), [view]);

  const handlePickYear = (year: number) => {
    onViewChange(new Date(year, view.getMonth(), 1));
    setYearOpen(false);
  };

  return (
    <div className="bi-md3-cal">
      {/* headline zone */}
      <div className="bi-md3-cal__head">
        <span className="bi-md3-cal__support">{label}</span>
        <span className="bi-md3-cal__headline">
          {capitalize(HEADLINE_FMT.format(selected))}
        </span>
      </div>

      {/* month / year navigation */}
      <div className="bi-md3-cal__nav">
        <button
          type="button"
          className={`bi-md3-cal__monthbtn${yearOpen ? " is-open" : ""}`}
          onClick={() => setYearOpen((o) => !o)}
          aria-expanded={yearOpen}
        >
          {capitalize(MONTHS_FR[view.getMonth()])} {view.getFullYear()}
          <ChevronDown size={18} aria-hidden="true" />
        </button>

        <div className="bi-md3-cal__arrows">
          <button
            type="button"
            className="bi-md3-cal__arrow"
            onClick={() => onViewChange(addMonths(view, -1))}
            aria-label="Mois précédent"
          >
            <ChevronLeft size={20} aria-hidden="true" />
          </button>
          <button
            type="button"
            className="bi-md3-cal__arrow"
            onClick={() => onViewChange(addMonths(view, 1))}
            aria-label="Mois suivant"
          >
            <ChevronRight size={20} aria-hidden="true" />
          </button>
        </div>
      </div>

      {yearOpen ? (
        <div className="bi-md3-cal__years" role="listbox" aria-label="Année">
          {YEARS.map((y) => (
            <button
              key={y}
              type="button"
              role="option"
              aria-selected={y === view.getFullYear()}
              className={`bi-md3-cal__year${y === view.getFullYear() ? " is-selected" : ""}`}
              onClick={() => handlePickYear(y)}
            >
              {y}
            </button>
          ))}
        </div>
      ) : (
        <>
          <div className="bi-md3-cal__weekdays" aria-hidden="true">
            {WEEKDAYS_FR.map((w, i) => (
              <span key={i} className="bi-md3-cal__weekday">{w}</span>
            ))}
          </div>

          <div className="bi-md3-cal__grid" role="grid">
            {cells.map((cell, i) => {
              if (!cell) return <span key={`b${i}`} className="bi-md3-cal__pad" />;
              const isSelected = sameDay(cell, selected);
              const isToday = sameDay(cell, today);
              const disabled = minDate ? startOfDay(cell) < startOfDay(minDate) : false;
              const cls = [
                "bi-md3-cal__day",
                isSelected ? "is-selected" : "",
                isToday && !isSelected ? "is-today" : "",
                disabled ? "is-disabled" : "",
              ].filter(Boolean).join(" ");
              return (
                <button
                  key={dateToIso(cell)}
                  type="button"
                  className={cls}
                  disabled={disabled}
                  aria-pressed={isSelected}
                  aria-label={cell.toLocaleDateString("fr-FR", {
                    weekday: "long", day: "numeric", month: "long", year: "numeric",
                  })}
                  onClick={() => onSelect(cell)}
                >
                  {cell.getDate()}
                </button>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}

// ─── popover position (centered on button, divider over its center) ──────────

const POPOVER_WIDTH = 624;
const POPOVER_EST_HEIGHT = 432;

function computePos(anchor: DOMRect) {
  const M = 12;
  const vw = window.innerWidth;
  const vh = window.innerHeight;

  const center = anchor.left + anchor.width / 2;
  let left = center - POPOVER_WIDTH / 2;
  left = Math.min(Math.max(M, left), Math.max(M, vw - POPOVER_WIDTH - M));

  let top = anchor.bottom + 8;
  if (top + POPOVER_EST_HEIGHT > vh - M) {
    top = Math.max(M, anchor.top - POPOVER_EST_HEIGHT - 8);
  }
  return { top, left };
}

// ─── main popover ────────────────────────────────────────────────────────────

export function BiDateRangePopover({
  open,
  value,
  onClose,
  onApply,
  anchorRef,
}: {
  open: boolean;
  value: BiDateRange;
  onClose: () => void;
  onApply: (range: BiDateRange) => void;
  anchorRef: React.RefObject<HTMLButtonElement | null>;
}) {
  const today = useMemo(() => startOfDay(new Date()), []);
  const [from, setFrom] = useState<Date>(() => isoToDate(value.from));
  const [to, setTo] = useState<Date>(() => isoToDate(value.to));
  const [fromView, setFromView] = useState<Date>(() => firstOfMonth(isoToDate(value.from)));
  const [toView, setToView] = useState<Date>(() => firstOfMonth(isoToDate(value.to)));
  const [pos, setPos] = useState({ top: 0, left: 0 });
  const ref = useRef<HTMLDivElement>(null);

  // Sync from incoming value whenever the popover opens
  useEffect(() => {
    if (!open) return;
    const f = isoToDate(value.from);
    const t = isoToDate(value.to);
    setFrom(f);
    setTo(t);
    setFromView(firstOfMonth(f));
    setToView(firstOfMonth(t));
    if (anchorRef.current) setPos(computePos(anchorRef.current.getBoundingClientRect()));
  }, [open, value, anchorRef]);

  // Escape closes
  useEffect(() => {
    if (!open) return undefined;
    const h = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
    window.addEventListener("keydown", h);
    return () => window.removeEventListener("keydown", h);
  }, [open, onClose]);

  // Outside click closes (matches existing behavior)
  useEffect(() => {
    if (!open) return undefined;
    let active = false;
    const timer = setTimeout(() => { active = true; }, 0);
    const h = (e: MouseEvent) => {
      if (!active) return;
      const target = e.target as Node;
      if (ref.current?.contains(target)) return;
      if (anchorRef.current?.contains(target)) return;
      onClose();
    };
    window.addEventListener("mousedown", h);
    return () => { clearTimeout(timer); window.removeEventListener("mousedown", h); };
  }, [open, onClose, anchorRef]);

  // Left = start ("De"). Picking a start after current end pushes the end along.
  const handleSelectFrom = (d: Date) => {
    setFrom(d);
    if (startOfDay(d) > startOfDay(to)) {
      setTo(d);
      setToView(firstOfMonth(d));
    }
  };

  // Right = end ("À"). Days before `from` are disabled, so `to >= from` always holds.
  const handleSelectTo = (d: Date) => {
    setTo(d);
  };

  const handleOk = () => {
    onApply({
      mode: detectMode(from, to),
      from: dateToIso(from),
      to: dateToIso(to),
    });
  };

  if (!open) return null;

  return createPortal(
    <div
      ref={ref}
      className="bi-md3"
      role="dialog"
      aria-modal="false"
      aria-label="Choisir une période"
      style={{ top: pos.top, left: pos.left, width: POPOVER_WIDTH }}
    >
      <div className="bi-md3__body">
        <Md3Calendar
          label="De"
          selected={from}
          view={fromView}
          today={today}
          onViewChange={setFromView}
          onSelect={handleSelectFrom}
        />

        <div className="bi-md3__divider" aria-hidden="true" />

        <Md3Calendar
          label="À"
          selected={to}
          view={toView}
          today={today}
          minDate={from}
          onViewChange={setToView}
          onSelect={handleSelectTo}
        />
      </div>

      <footer className="bi-md3__footer">
        <button type="button" className="bi-md3__text-btn" onClick={onClose}>
          Annuler
        </button>
        <button type="button" className="bi-md3__text-btn bi-md3__text-btn--primary" onClick={handleOk}>
          OK
        </button>
      </footer>
    </div>,
    document.body,
  );
}
