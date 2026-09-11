import { useMemo, useState } from "react";

export function useStoredState<T>(key: string, fallback: T) {
  const [value, setValue] = useState<T>(() => {
    const stored = localStorage.getItem(key);
    return stored ? (JSON.parse(stored) as T) : fallback;
  });

  const setStoredValue = useMemo(
    () => (next: T) => {
      localStorage.setItem(key, JSON.stringify(next));
      setValue(next);
    },
    [key],
  );

  return [value, setStoredValue] as const;
}
