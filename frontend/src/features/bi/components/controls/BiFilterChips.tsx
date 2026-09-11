import { X } from "lucide-react";

export type BiFilterChip = {
  key: string;
  label: string;
  ariaLabel: string;
  onRemove: () => void;
};

type BiFilterChipsProps = {
  chips: BiFilterChip[];
};

/**
 * Removable summary of the filters currently shaping the page's data — period,
 * comparison toggle, and (where a page wires them in) any extra filters. Kept
 * separate from `.bi-spotlight-actions` so it can render only when at least one
 * chip exists, without disturbing the action buttons' layout.
 */
export function BiFilterChips({ chips }: BiFilterChipsProps) {
  if (!chips.length) return null;

  return (
    <div className="bi-filter-chips" role="list" aria-label="Filtres actifs">
      {chips.map((chip) => (
        <button
          key={chip.key}
          type="button"
          role="listitem"
          className="bi-filter-chip"
          aria-label={chip.ariaLabel}
          onClick={chip.onRemove}
        >
          <span>{chip.label}</span>
          <X size={11} aria-hidden="true" />
        </button>
      ))}
    </div>
  );
}
