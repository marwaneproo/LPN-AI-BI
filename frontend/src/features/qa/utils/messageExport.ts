import type { Message } from "../types/qa.types";

function safeFilePart(value: string) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 60)
    .toLowerCase() || "reponse";
}

function formatTimestamp(iso?: string) {
  const date = iso ? new Date(iso) : new Date();
  return date.toLocaleString("fr-FR", { dateStyle: "long", timeStyle: "short" });
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

/**
 * Exports one assistant answer (with its paired question) as a PDF. Uses jsPDF the same way
 * biExport.ts already does for BI reports, dynamically imported to keep it out of the initial
 * bundle. Text is wrapped and paginated so the full answer is always included, never truncated.
 */
export async function exportMessageAsPdf(message: Message, question: string | undefined) {
  const { jsPDF } = await import("jspdf");
  const pdf = new jsPDF({ orientation: "portrait", unit: "pt", format: "a4" });
  const pageWidth = pdf.internal.pageSize.getWidth();
  const pageHeight = pdf.internal.pageSize.getHeight();
  const margin = 48;
  const contentWidth = pageWidth - margin * 2;
  let y = margin;

  const ensureSpace = (lineHeight: number) => {
    if (y + lineHeight > pageHeight - margin) {
      pdf.addPage();
      y = margin;
    }
  };

  pdf.setFont("helvetica", "bold");
  pdf.setFontSize(16);
  pdf.text("Assistant Business Intelligence — LPN", margin, y);
  y += 22;

  pdf.setFont("helvetica", "normal");
  pdf.setFontSize(9);
  pdf.setTextColor(110, 110, 110);
  pdf.text(`Généré le ${formatTimestamp(message.createdAt)}`, margin, y);
  y += 24;
  pdf.setTextColor(0, 0, 0);

  if (question) {
    pdf.setFont("helvetica", "bold");
    pdf.setFontSize(11);
    ensureSpace(16);
    pdf.text("Question", margin, y);
    y += 16;
    pdf.setFont("helvetica", "normal");
    pdf.setFontSize(11);
    const questionLines: string[] = pdf.splitTextToSize(question, contentWidth);
    for (const line of questionLines) {
      ensureSpace(15);
      pdf.text(line, margin, y);
      y += 15;
    }
    y += 14;
  }

  pdf.setFont("helvetica", "bold");
  pdf.setFontSize(11);
  ensureSpace(16);
  pdf.text("Réponse", margin, y);
  y += 18;

  pdf.setFont("helvetica", "normal");
  pdf.setFontSize(10.5);
  const answerParagraphs = message.text.split("\n");
  for (const paragraph of answerParagraphs) {
    if (paragraph.trim() === "") {
      y += 10;
      continue;
    }
    const lines: string[] = pdf.splitTextToSize(paragraph, contentWidth);
    for (const line of lines) {
      ensureSpace(15);
      pdf.text(line, margin, y);
      y += 15;
    }
  }

  pdf.save(`lpn-assistant-bi-${safeFilePart(question ?? "reponse")}.pdf`);
}

/**
 * Exports the same question/answer as a Word (.docx) document, using the `docx` package
 * (pure client-side, no server round-trip). Paragraphs map 1:1 onto the answer's own line
 * breaks so nothing is condensed or truncated.
 */
export async function exportMessageAsWord(message: Message, question: string | undefined) {
  const { Document, Packer, Paragraph, TextRun, HeadingLevel } = await import("docx");

  const answerParagraphs = message.text
    .split("\n")
    .map(
      (line) =>
        new Paragraph({
          children: [new TextRun({ text: line || " ", size: 22 })],
          spacing: { after: 120 },
        }),
    );

  const doc = new Document({
    sections: [
      {
        children: [
          new Paragraph({
            children: [new TextRun({ text: "Assistant Business Intelligence — LPN", bold: true, size: 30 })],
            spacing: { after: 80 },
          }),
          new Paragraph({
            children: [
              new TextRun({ text: `Généré le ${formatTimestamp(message.createdAt)}`, italics: true, size: 18, color: "6E6E6E" }),
            ],
            spacing: { after: 240 },
          }),
          ...(question
            ? [
                new Paragraph({
                  text: "Question",
                  heading: HeadingLevel.HEADING_2,
                  spacing: { after: 100 },
                }),
                new Paragraph({
                  children: [new TextRun({ text: question, size: 22 })],
                  spacing: { after: 240 },
                }),
              ]
            : []),
          new Paragraph({
            text: "Réponse",
            heading: HeadingLevel.HEADING_2,
            spacing: { after: 100 },
          }),
          ...answerParagraphs,
        ],
      },
    ],
  });

  const blob = await Packer.toBlob(doc);
  downloadBlob(blob, `lpn-assistant-bi-${safeFilePart(question ?? "reponse")}.docx`);
}
