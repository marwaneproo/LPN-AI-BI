import type { ReactNode } from "react";
import { createContext, useCallback, useContext, useMemo, useRef } from "react";

export type RegisteredBiWidget = {
  id: string;
  title: string;
  subtitle?: string;
  element: HTMLElement;
};

type BiWidgetRegistryValue = {
  register: (id: string, title: string, subtitle: string | undefined, element: HTMLElement) => void;
  unregister: (id: string) => void;
  getWidgets: () => RegisteredBiWidget[];
};

const BiWidgetRegistryContext = createContext<BiWidgetRegistryValue | null>(null);

/**
 * Wraps a BI view (see BiShell) so every BiWidgetCard mounted inside it can register itself —
 * automatically, with no per-page wiring — letting the export gallery enumerate exactly the
 * charts currently visible in THIS view. Because registration happens on mount/unmount, a
 * widget from another tab/page is never in the list (React Router unmounts it), which is what
 * guarantees the export gallery never pulls in another view's charts.
 */
export function BiWidgetRegistryProvider({ children }: { children: ReactNode }) {
  // Kept in a ref (not state) since registration/unregistration happens on every widget's
  // mount/unmount and must never itself trigger a re-render of the whole view.
  const widgetsRef = useRef<Map<string, RegisteredBiWidget>>(new Map());

  const register = useCallback((id: string, title: string, subtitle: string | undefined, element: HTMLElement) => {
    widgetsRef.current.set(id, { id, title, subtitle, element });
  }, []);

  const unregister = useCallback((id: string) => {
    widgetsRef.current.delete(id);
  }, []);

  const getWidgets = useCallback(() => Array.from(widgetsRef.current.values()), []);

  const value = useMemo(() => ({ register, unregister, getWidgets }), [register, unregister, getWidgets]);

  return <BiWidgetRegistryContext.Provider value={value}>{children}</BiWidgetRegistryContext.Provider>;
}

/** Used by BiShell's export button to snapshot exactly which widgets are on screen right now. */
export function useBiWidgetRegistry() {
  const context = useContext(BiWidgetRegistryContext);
  if (!context) {
    throw new Error("useBiWidgetRegistry must be used within a BiWidgetRegistryProvider");
  }
  return context;
}

/** Returns register/unregister bound to a stable no-op when used outside a provider, so
 * BiWidgetCard keeps working even on any page that hasn't been wrapped yet. */
export function useOptionalBiWidgetRegistry() {
  return useContext(BiWidgetRegistryContext);
}
