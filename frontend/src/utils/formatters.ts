export type DeltaTone = "good" | "bad" | "neutral";

export function formatNumber(value: number) {
  return new Intl.NumberFormat("fr-FR", {
    maximumFractionDigits: 0,
  }).format(Number(value ?? 0));
}

/** Formats a full MAD amount with fr-FR grouping, no decimals, and the MAD currency code. */
export function formatMoneyFull(value: number) {
  return new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency: "MAD",
    maximumFractionDigits: 0,
  }).format(Number(value ?? 0));
}

/** Formats money in thousands of dirhams: value / 1,000, fr-FR, max 1 decimal, suffixed with KDH. */
export function formatCompactMoney(value: number) {
  const valueInKdh = Number(value ?? 0) / 1000;
  const text = new Intl.NumberFormat("fr-FR", {
    maximumFractionDigits: Math.abs(valueInKdh) >= 100 ? 0 : 1,
  }).format(valueInKdh);
  return `${text} KDH`;
}

/** Formats a percentage with fr-FR grouping, up to 1 decimal, and a trailing percent sign. */
export function formatPercent(value: number) {
  return new Intl.NumberFormat("fr-FR", {
    maximumFractionDigits: 1,
    style: "percent",
  }).format(Number(value ?? 0) / 100);
}

/** Formats a current-vs-previous delta as signed percent text and a business tone. */
export function formatDelta(currentValue: number, previousValue: number, increaseIsGood = true): { text: string; tone: DeltaTone } {
  const current = Number(currentValue ?? 0);
  const previous = Number(previousValue ?? 0);
  if (!Number.isFinite(current) || !Number.isFinite(previous) || previous === 0) {
    return { text: "—", tone: "neutral" };
  }
  const deltaPercent = ((current - previous) / Math.abs(previous)) * 100;
  if (!Number.isFinite(deltaPercent) || deltaPercent === 0) {
    return { text: "0 %", tone: "neutral" };
  }
  const rose = deltaPercent > 0;
  const tone: DeltaTone = rose === increaseIsGood ? "good" : "bad";
  const sign = rose ? "+" : "";
  return { text: `${sign}${formatPercent(deltaPercent)}`, tone };
}

/** Axis tick for money scales: the compact KDH figure without its unit — tooltips and data labels carry "KDH". */
export function formatMoneyTick(value: number) {
  return formatCompactMoney(value).replace(/\s*KDH$/, "");
}

/** Formats a compact fr-FR axis tick for non-money numeric scales, max 1 decimal. */
export function compactAxisTick(value: number) {
  return new Intl.NumberFormat("fr-FR", {
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(value);
}

export function formatMoney(value: number) {
  return formatMoneyFull(value);
}

export function shortenLabel(value: string, maxLength = 22) {
  if (!value) return "Sans libellé";
  return value.length > maxLength ? `${value.slice(0, maxLength - 1)}...` : value;
}

export function formatCell(value: string | number | boolean | null) {
  if (value === null) return "";
  if (typeof value === "boolean") return value ? "Oui" : "Non";
  return value;
}

export function formatShortDate(value: string) {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "date inconnue";
  return parsed.toLocaleDateString("fr-FR", {
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}
