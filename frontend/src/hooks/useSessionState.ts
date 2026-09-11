import { useMemo, useState } from "react";

export function useSessionState<T>(key: string, fallback: T) {
  const [value, setValue] = useState<T>(() => {
    const stored = sessionStorage.getItem(key);
    return stored ? (JSON.parse(stored) as T) : fallback;
  });

  const setStoredValue = useMemo(
    () => (next: T) => {
      if (next === null) {
        sessionStorage.removeItem(key);
      } else {
        sessionStorage.setItem(key, JSON.stringify(next));
      }
      setValue(next);
    },
    [key],
  );

  return [value, setStoredValue] as const;
}
