import { useEffect, useMemo, useState } from "react";
import { CalendarDays, Check, X } from "lucide-react";

export type BiRangeMode = "day" | "month" | "year";

export type BiDateRange = {
  mode: BiRangeMode;
  from: string;
  to: string;
};

const monthLabels = [
  "Jan", "Fév", "Mar", "Avr", "Mai", "Juin",
  "Juil", "Août", "Sep", "Oct", "Nov", "Déc",
];

const yearOptions = [2024, 2025, 2026];

function toMonthValue(dateIso: string) {
  return dateIso.slice(0, 7);
}

function toYearValue(dateIso: string) {
  return dateIso.slice(0, 4);
}

function monthStart(monthValue: string) {
  return `${monthValue}-01`;
}

function monthEnd(monthValue: string) {
  const [year, month] = monthValue.split("-").map(Number);
  const lastDay = new Date(year, month, 0).getDate();
  return `${monthValue}-${String(lastDay).padStart(2, "0")}`;
}

function yearStart(year: string) {
  return `${year}-01-01`;
}

function yearEnd(year: string) {
  return `${year}-12-31`;
}

function todayIso() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
}

function daysAgoIso(days: number) {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function currentMonthRange(): BiDateRange {
  const now = new Date();
  const month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
  return { mode: "month", from: monthStart(month), to: monthEnd(month) };
}

function normalizeRange(mode: BiRangeMode, fromValue: string, toValue: string): BiDateRange {
  if (mode === "month") {
    return { mode, from: monthStart(fromValue), to: monthEnd(toValue) };
  }
  if (mode === "year") {
    return { mode, from: yearStart(fromValue), to: yearEnd(toValue) };
  }
  return { mode, from: fromValue, to: toValue };
}

export function formatBiRangeLabel(range: BiDateRange) {
  const dateFormatter = new Intl.DateTimeFormat("fr-FR", { day: "2-digit", month: "short", year: "numeric" });
  const monthFormatter = new Intl.DateTimeFormat("fr-FR", { month: "short", year: "numeric" });

  if (range.mode === "year") {
    return `${toYearValue(range.from)} – ${toYearValue(range.to)}`;
  }

  if (range.mode === "month") {
    return `${monthFormatter.format(new Date(range.from))} – ${monthFormatter.format(new Date(range.to))}`;
  }

  return `${dateFormatter.format(new Date(range.from))} – ${dateFormatter.format(new Date(range.to))}`;
}

type PresetItem = { label: string; range: BiDateRange };

const presets: PresetItem[] = [
  { label: "7 jours", range: { mode: "day", from: daysAgoIso(6), to: todayIso() } },
  { label: "30 jours", range: { mode: "day", from: daysAgoIso(29), to: todayIso() } },
  { label: "Mois courant", range: currentMonthRange() },
  { label: "Jan – Juin 2026", range: { mode: "month", from: "2026-01-01", to: "2026-06-30" } },
  { label: "2024 – 2026", range: { mode: "year", from: "2024-01-01", to: "2026-12-31" } },
];

function MonthGrid({
  year,
  selectedFrom,
  selectedTo,
  onSelect,
}: {
  year: number;
  selectedFrom: string;
  selectedTo: string;
  onSelect: (monthValue: string) => void;
}) {
  return (
    <div className="bi-month-grid-year">
      <span className="bi-month-grid-label">{year}</span>
      <div className="bi-month-grid">
        {monthLabels.map((label, index) => {
          const value = `${year}-${String(index + 1).padStart(2, "0")}`;
          const isFrom = value === selectedFrom;
          const isTo = value === selectedTo;
          const isInRange = value >= selectedFrom && value <= selectedTo;
          const classes = [
            "bi-month-cell",
            isInRange ? "in-range" : "",
            isFrom ? "is-from" : "",
            isTo ? "is-to" : "",
          ].filter(Boolean).join(" ");

          return (
            <button
              key={value}
              type="button"
              className={classes}
              onClick={() => onSelect(value)}
              aria-label={`${label} ${year}`}
              aria-pressed={isFrom || isTo}
            >
              {label}
            </button>
          );
        })}
      </div>
    </div>
  );
}

function YearGrid({
  selectedFrom,
  selectedTo,
  onSelect,
}: {
  selectedFrom: string;
  selectedTo: string;
  onSelect: (year: string) => void;
}) {
  return (
    <div className="bi-year-grid">
      {yearOptions.map((year) => {
        const value = String(year);
        const isFrom = value === selectedFrom;
        const isTo = value === selectedTo;
        const isInRange = year >= Number(selectedFrom) && year <= Number(selectedTo);
        const classes = [
          "bi-year-cell",
          isInRange ? "in-range" : "",
          isFrom ? "is-from" : "",
          isTo ? "is-to" : "",
        ].filter(Boolean).join(" ");

        return (
          <button
            key={year}
            type="button"
            className={classes}
            onClick={() => onSelect(value)}
            aria-label={`Année ${year}`}
            aria-pressed={isFrom || isTo}
          >
            {year}
          </button>
        );
      })}
    </div>
  );
}

export function BiDateRangeDialog({
  open,
  value,
  onClose,
  onApply,
}: {
  open: boolean;
  value: BiDateRange;
  onClose: () => void;
  onApply: (range: BiDateRange) => void;
}) {
  const [mode, setMode] = useState<BiRangeMode>(value.mode);
  const [fromDay, setFromDay] = useState(value.from);
  const [toDay, setToDay] = useState(value.to);
  const [fromMonth, setFromMonth] = useState(toMonthValue(value.from));
  const [toMonth, setToMonth] = useState(toMonthValue(value.to));
  const [fromYear, setFromYear] = useState(toYearValue(value.from));
  const [toYear, setToYear] = useState(toYearValue(value.to));

  // Track whether user is picking the "from" or "to" end of a month/year range
  const [pickingEnd, setPickingEnd] = useState<"from" | "to">("from");

  useEffect(() => {
    if (!open) return;
    setMode(value.mode);
    setFromDay(value.from);
    setToDay(value.to);
    setFromMonth(toMonthValue(value.from));
    setToMonth(toMonthValue(value.to));
    setFromYear(toYearValue(value.from));
    setToYear(toYearValue(value.to));
    setPickingEnd("from");
  }, [open, value]);

  useEffect(() => {
    if (!open) return undefined;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose, open]);

  const draftRange = useMemo(() => {
    if (mode === "month") return normalizeRange(mode, fromMonth, toMonth);
    if (mode === "year") return normalizeRange(mode, fromYear, toYear);
    return normalizeRange(mode, fromDay, toDay);
  }, [fromDay, fromMonth, fromYear, mode, toDay, toMonth, toYear]);

  const hasInvalidRange = draftRange.from > draftRange.to;

  if (!open) return null;

  const applyPreset = (range: BiDateRange) => {
    setMode(range.mode);
    setFromDay(range.from);
    setToDay(range.to);
    setFromMonth(toMonthValue(range.from));
    setToMonth(toMonthValue(range.to));
    setFromYear(toYearValue(range.from));
    setToYear(toYearValue(range.to));
  };

  const handleMonthSelect = (monthValue: string) => {
    if (pickingEnd === "from") {
      setFromMonth(monthValue);
      setToMonth(monthValue);
      setPickingEnd("to");
    } else {
      if (monthValue < fromMonth) {
        setFromMonth(monthValue);
        setToMonth(fromMonth);
      } else {
        setToMonth(monthValue);
      }
      setPickingEnd("from");
    }
  };

  const handleYearSelect = (yearValue: string) => {
    if (pickingEnd === "from") {
      setFromYear(yearValue);
      setToYear(yearValue);
      setPickingEnd("to");
    } else {
      if (yearValue < fromYear) {
        setFromYear(yearValue);
        setToYear(fromYear);
      } else {
        setToYear(yearValue);
      }
      setPickingEnd("from");
    }
  };

  const handleApply = () => {
    if (hasInvalidRange) return;
    onApply(draftRange);
  };

  return (
    <div className="bi-modal-backdrop" role="presentation" onMouseDown={onClose}>
      <section
        className="bi-date-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="bi-date-dialog-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="bi-modal-header">
          <div>
            <span className="bi-modal-eyebrow">Période d'analyse</span>
            <h3 id="bi-date-dialog-title">Choisir une plage</h3>
          </div>
          <button type="button" className="bi-icon-button" onClick={onClose} aria-label="Fermer">
            <X size={17} aria-hidden="true" />
          </button>
        </header>

        {/* Mode segmented control */}
        <div className="bi-mode-segmented" role="tablist" aria-label="Type de plage">
          <button
            type="button"
            className={`bi-mode-tab${mode === "day" ? " is-active" : ""}`}
            role="tab"
            aria-selected={mode === "day"}
            onClick={() => setMode("day")}
          >
            Jour
          </button>
          <button
            type="button"
            className={`bi-mode-tab${mode === "month" ? " is-active" : ""}`}
            role="tab"
            aria-selected={mode === "month"}
            onClick={() => setMode("month")}
          >
            Mois
          </button>
          <button
            type="button"
            className={`bi-mode-tab${mode === "year" ? " is-active" : ""}`}
            role="tab"
            aria-selected={mode === "year"}
            onClick={() => setMode("year")}
          >
            Année
          </button>
        </div>

        {/* Section: quick presets */}
        <div className="bi-dialog-section">
          <span className="bi-dialog-section-label">Raccourcis</span>
          <div className="bi-range-presets">
            {presets.map((preset) => (
              <button
                key={preset.label}
                type="button"
                className="bi-preset-chip"
                onClick={() => applyPreset(preset.range)}
              >
                {preset.label}
              </button>
            ))}
          </div>
        </div>

        {/* Section: range selection */}
        <div className="bi-dialog-section">
          <span className="bi-dialog-section-label">
            {mode === "day" ? "Sélectionnez les dates" : mode === "month" ? "Sélectionnez les mois" : "Sélectionnez les années"}
          </span>

          {mode === "day" && (
            <div className="bi-range-fields">
              <label>
                <span>De</span>
                <input type="date" value={fromDay} onChange={(event) => setFromDay(event.target.value)} />
              </label>
              <label>
                <span>À</span>
                <input type="date" value={toDay} min={fromDay} onChange={(event) => setToDay(event.target.value)} />
              </label>
            </div>
          )}

          {mode === "month" && (
            <div className="bi-month-picker" aria-label="Sélection de mois">
              <p className="bi-pick-hint">
                {pickingEnd === "from" ? "Cliquez pour choisir le début" : "Cliquez pour choisir la fin"}
              </p>
              {yearOptions.map((year) => (
                <MonthGrid
                  key={year}
                  year={year}
                  selectedFrom={fromMonth}
                  selectedTo={toMonth}
                  onSelect={handleMonthSelect}
                />
              ))}
            </div>
          )}

          {mode === "year" && (
            <div className="bi-year-picker" aria-label="Sélection d'années">
              <p className="bi-pick-hint">
                {pickingEnd === "from" ? "Cliquez pour choisir le début" : "Cliquez pour choisir la fin"}
              </p>
              <YearGrid
                selectedFrom={fromYear}
                selectedTo={toYear}
                onSelect={handleYearSelect}
              />
            </div>
          )}
        </div>

        {/* Summary */}
        <div className="bi-range-summary" aria-live="polite">
          <CalendarDays size={16} aria-hidden="true" />
          <span>
            {hasInvalidRange
              ? "La date de fin doit être après la date de début."
              : `Période : ${formatBiRangeLabel(draftRange)}`}
          </span>
        </div>

        <footer className="bi-modal-footer">
          <button type="button" className="bi-modal-secondary" onClick={onClose}>
            Annuler
          </button>
          <button type="button" className="bi-modal-primary" onClick={handleApply} disabled={hasInvalidRange}>
            <Check size={16} aria-hidden="true" />
            Appliquer
          </button>
        </footer>
      </section>
    </div>
  );
}
