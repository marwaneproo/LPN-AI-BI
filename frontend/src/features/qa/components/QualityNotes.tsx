import type { Message } from "../types/qa.types";

export function QualityNotes({ message }: { message: Message }) {
  const issues = [
    ...(message.semanticValidation?.issues ?? []),
    ...(message.resultAssessment?.issues ?? []),
  ];
  const warnings = [
    ...(message.semanticValidation?.warnings ?? []),
    ...(message.resultAssessment?.warnings ?? []),
  ];
  if (!issues.length && !warnings.length && !message.intent?.intents?.length) return null;

  return (
    <div className="quality-notes">
      {message.intent?.intents?.length ? (
        <span>Intent: {message.intent.intents.slice(0, 4).join(", ")}</span>
      ) : null}
      {issues.slice(0, 2).map((issue) => (
        <span key={issue} className="quality-issue">{issue}</span>
      ))}
      {warnings.slice(0, 2).map((warning) => (
        <span key={warning}>{warning}</span>
      ))}
    </div>
  );
}
