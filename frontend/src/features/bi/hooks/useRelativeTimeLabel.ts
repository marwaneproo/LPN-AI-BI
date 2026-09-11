import { useEffect, useMemo, useState } from "react";

/** Re-render cadence: fine enough that "il y a X min" never lags visibly, cheap enough to be free. */
const TICK_MS = 30_000;

function relativeLabel(elapsedMs: number) {
  // Clock skew between the backend's generated_at and the client can make the
  // diff slightly negative right after a fetch — read that as "just now".
  if (elapsedMs < 45_000) return "à l'instant";
  const minutes = Math.floor(elapsedMs / 60_000);
  if (minutes < 60) return `il y a ${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `il y a ${hours} h`;
  const days = Math.floor(hours / 24);
  return `il y a ${days} j`;
}

/**
 * French relative-time label for a data timestamp ("à l'instant", "il y a
 * 20 min", "il y a 1 h", "il y a 2 j"), kept accurate by ticking every 30 s.
 * Also returns the absolute fr-FR datetime for tooltips. Both are null when
 * no (or an unparsable) timestamp is given.
 */
export function useRelativeTimeLabel(isoDate: string | undefined) {
  const timestamp = useMemo(() => {
    if (!isoDate) return null;
    const parsed = Date.parse(isoDate);
    return Number.isNaN(parsed) ? null : parsed;
  }, [isoDate]);

  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (timestamp === null) return;
    setNow(Date.now());
    const interval = window.setInterval(() => setNow(Date.now()), TICK_MS);
    return () => window.clearInterval(interval);
  }, [timestamp]);

  if (timestamp === null) return { label: null, absolute: null };

  return {
    label: relativeLabel(Math.max(0, now - timestamp)),
    absolute: new Intl.DateTimeFormat("fr-FR", { dateStyle: "long", timeStyle: "short" }).format(timestamp),
  };
}
