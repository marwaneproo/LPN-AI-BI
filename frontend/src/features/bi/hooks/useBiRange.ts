import { useCallback, useMemo, useState } from "react";
import { useLocation } from "react-router-dom";
import type { BiDateRange } from "../components/controls/BiDateRangeDialog";
import type { BiGranularity } from "../types/bi.types";

// Single source of truth for the "no selection yet" / "reset" range. Exported
// so anything that needs to reset the period (e.g. the filter chips' "Période"
// chip) applies the exact same default instead of re-declaring it.
export const BI_DEFAULT_RANGE: BiDateRange = {
  mode: "month",
  from: "2026-01-01",
  to: "2026-06-30",
};

function readStoredRange(storageKey: string): BiDateRange {
  try {
    const rawValue = window.localStorage.getItem(storageKey);
    if (!rawValue) return BI_DEFAULT_RANGE;
    const parsed = JSON.parse(rawValue) as BiDateRange;
    if (!parsed.from || !parsed.to || !["day", "month", "year"].includes(parsed.mode)) return BI_DEFAULT_RANGE;
    return parsed;
  } catch {
    return BI_DEFAULT_RANGE;
  }
}

export type BiSelectedFilters = {
  from: string;
  to: string;
  granularity: BiGranularity;
  compare: boolean;
};

/**
 * Single source of truth for a BI page's period selection. Backed by localStorage
 * (per page) so the choice survives reloads. Both the page's data hook and the
 * BiShell header read from this, so the date picker actually filters the data and
 * the header label always matches what the charts show.
 */
export function useBiRange() {
  const location = useLocation();
  const pageKey = useMemo(
    () => location.pathname.replace(/[^a-z0-9-]/gi, "-").replace(/^-+|-+$/g, ""),
    [location.pathname],
  );
  const rangeStorageKey = `lpn-bi-range:${pageKey}`;
  const compareStorageKey = `lpn-bi-compare:${pageKey}`;

  const [range, setRange] = useState<BiDateRange>(() => readStoredRange(rangeStorageKey));
  const [compareEnabled, setCompareEnabled] = useState(() => window.localStorage.getItem(compareStorageKey) === "true");

  const applyRange = useCallback(
    (next: BiDateRange) => {
      setRange(next);
      window.localStorage.setItem(rangeStorageKey, JSON.stringify(next));
    },
    [rangeStorageKey],
  );

  const toggleCompare = useCallback(() => {
    setCompareEnabled((current) => {
      const next = !current;
      window.localStorage.setItem(compareStorageKey, String(next));
      return next;
    });
  }, [compareStorageKey]);

  const filters = useMemo<BiSelectedFilters>(
    () => ({
      from: range.from,
      to: range.to,
      granularity: range.mode === "day" ? "day" : "month",
      compare: compareEnabled,
    }),
    [range, compareEnabled],
  );

  return { range, applyRange, compareEnabled, toggleCompare, filters };
}
