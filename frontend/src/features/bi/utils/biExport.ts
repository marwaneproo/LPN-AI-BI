import { API_BASE_URL, apiFetch } from "../../../api/client";

export type BiExportFormat = "png" | "pdf" | "excel";

export type BiExportContext = {
  title: string;
  subtitle: string;
  rangeLabel: string;
  comparisonEnabled: boolean;
  pageSlug: string;
  from: string;
  to: string;
  granularity: "day" | "month";
};

export type BiExportErrorCode =
  | "target-missing"
  | "capture-failed"
  | "pdf-failed"
  | "excel-failed";

/**
 * Typed export error so the UI can show a precise French message instead of a
 * single generic fallback. The original cause is preserved for dev logging.
 */
export class BiExportError extends Error {
  readonly code: BiExportErrorCode;

  constructor(code: BiExportErrorCode, message: string, cause?: unknown) {
    super(message);
    this.name = "BiExportError";
    this.code = code;
    if (cause !== undefined) this.cause = cause;
  }
}

const ERROR_MESSAGES_FR: Record<BiExportErrorCode, string> = {
  "target-missing":
    "La zone à exporter est introuvable. Rechargez la page puis réessayez.",
  "capture-failed":
    "La capture visuelle de la vue a échoué. Réessayez ou choisissez un autre format.",
  "pdf-failed":
    "La génération du PDF a échoué. Réessayez ou exportez en PNG.",
  "excel-failed":
    "La génération du fichier Excel a échoué. Réessayez plus tard.",
};

export function biExportErrorMessage(error: unknown): string {
  if (error instanceof BiExportError) return ERROR_MESSAGES_FR[error.code];
  return "L'export n'a pas pu être généré. Réessayez ou choisissez un autre format.";
}

export function safeFilePart(value: string) {
  return value
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .toLowerCase();
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function dataUrlToBlob(dataUrl: string) {
  const [meta, base64Data] = dataUrl.split(",");
  const mimeMatch = meta.match(/data:(.*?);base64/);
  const mimeType = mimeMatch?.[1] ?? "application/octet-stream";
  const binary = window.atob(base64Data);
  const bytes = new Uint8Array(binary.length);

  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }

  return new Blob([bytes], { type: mimeType });
}

// CSS classes of UI controls that must never appear inside the exported image.
// `.bi-spotlight-actions` holds the date picker, compare toggle, status pill
// and export button; `.bi-filter-chips` holds the removable période/comparaison
// (and any page-specific) filter chips; `.bi-widget-detail-btn` is the
// "Voir les données" drawer trigger and `.bi-widget-download-btn` the per-widget
// PNG trigger on widget cards. All interactive-only chrome.
const EXPORT_EXCLUDE_CLASSES = ["bi-spotlight-actions", "bi-filter-chips", "bi-widget-detail-btn", "bi-widget-download-btn", "bi-widget-fullscreen-btn"];

// Per-widget captures additionally drop the collapse toggle ("Afficher le
// graphique"/"Réduire" on the yearly CA card) — inside a single-widget image it
// is dead chrome, while the full-page export keeps it to document the state.
const WIDGET_EXPORT_EXCLUDE_CLASSES = [...EXPORT_EXCLUDE_CLASSES, "bi-collapse-toggle"];

/**
 * Wait for fonts to be ready and for the browser to paint one more frame so the
 * layout (and any in-flight Recharts animation) has settled before capture.
 */
async function waitForRenderSettled() {
  try {
    if (document.fonts?.ready) await document.fonts.ready;
  } catch {
    // Font readiness is best-effort; continue regardless.
  }
  await new Promise<void>((resolve) => {
    requestAnimationFrame(() => requestAnimationFrame(() => resolve()));
  });
}

/**
 * Capture the BI report area into a canvas.
 *
 * Uses `html-to-image`, which serializes the DOM into an SVG <foreignObject>
 * and lets the browser rasterize it. Because the browser does the painting,
 * modern CSS (oklch(), color-mix(), CSS variables, gradients, shadows) and
 * Recharts SVG are all rendered natively — no custom color parser to choke on
 * them, which is exactly what made html2canvas fail on this theme.
 */
async function captureElement(
  element: HTMLElement,
  { backgroundColor = "#f4f8fb", excludeClasses = EXPORT_EXCLUDE_CLASSES }: { backgroundColor?: string; excludeClasses?: string[] } = {},
): Promise<HTMLCanvasElement> {
  const { toCanvas } = await import("html-to-image");
  await waitForRenderSettled();

  return toCanvas(element, {
    backgroundColor,
    pixelRatio: Math.min(2, window.devicePixelRatio || 1.5),
    cacheBust: true,
    width: element.scrollWidth,
    height: element.scrollHeight,
    filter: (node) =>
      !(node instanceof HTMLElement) ||
      !excludeClasses.some((className) => node.classList.contains(className)),
  });
}

/** Resolves the active theme's page background so a widget PNG's rounded corners blend in; falls back to the full-export tint. */
function themePageBackground() {
  const value = getComputedStyle(document.documentElement).getPropertyValue("--dashboard-page-bg").trim();
  return value || "#f4f8fb";
}

/**
 * Capture ONE widget card into a full-resolution PNG data URL, without downloading it.
 * Used by the export gallery to show a real (not approximated) thumbnail of every widget in
 * the current view before the user picks which ones to download — same capture path as
 * exportBiWidgetPng, so the eventual download is byte-identical to what was previewed.
 */
export async function captureBiWidgetPng(element: HTMLElement): Promise<string> {
  const canvas = await captureElement(element, {
    backgroundColor: themePageBackground(),
    excludeClasses: WIDGET_EXPORT_EXCLUDE_CLASSES,
  });
  return canvas.toDataURL("image/png");
}

/** Downloads an already-captured PNG data URL (see captureBiWidgetPng) under the given filename base. */
export function downloadPngDataUrl(dataUrl: string, filenameBase: string) {
  downloadBlob(dataUrlToBlob(dataUrl), `${filenameBase}.png`);
}

/**
 * Download ONE widget card as a PNG (the per-chart download button), captured
 * in the current theme with its title/subtitle but without any interactive
 * chrome. Filename: `lpn-bi-<widget-title>-<yyyy-mm-dd>.png`.
 */
export async function exportBiWidgetPng(element: HTMLElement, title: string) {
  let dataUrl: string;
  try {
    dataUrl = await captureBiWidgetPng(element);
  } catch (error) {
    throw new BiExportError("capture-failed", "Widget capture failed", error);
  }

  const datePart = new Date().toISOString().slice(0, 10);
  downloadPngDataUrl(dataUrl, `lpn-bi-${safeFilePart(title)}-${datePart}`);
}

async function exportPdf(
  canvas: HTMLCanvasElement,
  imageData: string,
  filenameBase: string,
  context: BiExportContext,
) {
  const { jsPDF } = await import("jspdf");
  const pdf = new jsPDF({ orientation: "landscape", unit: "pt", format: "a4" });
  const pageWidth = pdf.internal.pageSize.getWidth();
  const pageHeight = pdf.internal.pageSize.getHeight();
  const margin = 28;
  const headerHeight = 60;
  const maxImageWidth = pageWidth - margin * 2;
  const maxImageHeight = pageHeight - margin * 2 - headerHeight;
  const imageRatio = canvas.width / canvas.height;
  let imageWidth = maxImageWidth;
  let imageHeight = imageWidth / imageRatio;

  if (imageHeight > maxImageHeight) {
    imageHeight = maxImageHeight;
    imageWidth = imageHeight * imageRatio;
  }

  // Center the image if it is narrower than the page
  const imageX = margin + (maxImageWidth - imageWidth) / 2;

  // Header
  pdf.setFillColor(8, 120, 209);
  pdf.rect(margin, margin, 4, 36, "F");
  pdf.setFont("helvetica", "bold");
  pdf.setFontSize(17);
  pdf.setTextColor(17, 24, 39);
  pdf.text(context.title, margin + 12, margin + 14);
  pdf.setFont("helvetica", "normal");
  pdf.setFontSize(9);
  pdf.setTextColor(107, 114, 128);
  pdf.text(
    `${context.rangeLabel}  ·  ${context.comparisonEnabled ? "Comparaison activée" : "Sans comparaison"}  ·  LPN AI-BI`,
    margin + 12,
    margin + 28,
  );

  // Separator line
  pdf.setDrawColor(215, 225, 234);
  pdf.setLineWidth(0.5);
  pdf.line(margin, margin + headerHeight - 8, pageWidth - margin, margin + headerHeight - 8);

  // Chart image
  pdf.addImage(imageData, "PNG", imageX, margin + headerHeight, imageWidth, imageHeight, undefined, "FAST");

  pdf.save(`${filenameBase}.pdf`);
}

function filenameFromContentDisposition(header: string | null) {
  if (!header) return null;
  const utf8Match = header.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match?.[1]) return decodeURIComponent(utf8Match[1].replace(/"/g, ""));
  const asciiMatch = header.match(/filename="?([^";]+)"?/i);
  return asciiMatch?.[1] ?? null;
}

async function exportExcel(filenameBase: string, context: BiExportContext) {
  const params = new URLSearchParams({
    from: context.from,
    to: context.to,
    granularity: context.granularity,
    compare: String(context.comparisonEnabled),
  });
  const response = await apiFetch(`${API_BASE_URL}/v1/bi/export/${context.pageSlug}?${params}`, {
    headers: {
      Accept: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    },
  });
  if (!response.ok) {
    throw new Error(`Backend ${response.status}`);
  }
  const blob = await response.blob();
  downloadBlob(blob, filenameFromContentDisposition(response.headers.get("Content-Disposition")) ?? `${filenameBase}.xlsx`);
}

export async function exportBiView(
  element: HTMLElement,
  format: BiExportFormat,
  context: BiExportContext,
) {
  const filenameBase = `lpn-bi-${safeFilePart(context.title)}-${safeFilePart(context.rangeLabel)}`;

  // Excel never needs a canvas capture — it is a structured data export.
  if (format === "excel") {
    try {
      await exportExcel(filenameBase, context);
    } catch (error) {
      throw new BiExportError("excel-failed", "Excel generation failed", error);
    }
    return;
  }

  let canvas: HTMLCanvasElement;
  try {
    canvas = await captureElement(element);
  } catch (error) {
    throw new BiExportError("capture-failed", "Visual capture failed", error);
  }

  const imageData = canvas.toDataURL("image/png");

  if (format === "png") {
    downloadBlob(dataUrlToBlob(imageData), `${filenameBase}.png`);
    return;
  }

  if (format === "pdf") {
    try {
      await exportPdf(canvas, imageData, filenameBase, context);
    } catch (error) {
      throw new BiExportError("pdf-failed", "PDF generation failed", error);
    }
  }
}
