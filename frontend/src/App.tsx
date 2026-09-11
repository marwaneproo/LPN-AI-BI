import { useEffect, useState } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import type { Theme, Density } from "./types/common.types";
import type { AuthSession } from "./features/auth/types/auth.types";
import { useStoredState } from "./hooks/useStoredState";
import { LandingPage } from "./features/auth/pages/LandingPage";
import { LoginPage } from "./features/auth/pages/LoginPage";
import { getCurrentSession, logout } from "./features/auth/api/authApi";
import { AUTH_UNAUTHORIZED_EVENT } from "./api/client";
import { AppShell } from "./components/layout/AppShell";

function App() {
  const [theme, setTheme] = useStoredState<Theme>("lpn-theme", "dark");
  const [density, setDensity] = useStoredState<Density>("lpn-density", "comfortable");
  const [authSession, setAuthSession] = useState<AuthSession | null>(null);
  const [isSessionLoading, setIsSessionLoading] = useState(true);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    document.documentElement.dataset.density = density;
  }, [theme, density]);

  useEffect(() => {
    let active = true;
    sessionStorage.removeItem("lpn-session");
    void getCurrentSession()
      .then((session) => {
        if (active) setAuthSession(session);
      })
      .catch(() => {
        if (active) setAuthSession(null);
      })
      .finally(() => {
        if (active) setIsSessionLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    const handleUnauthorized = () => setAuthSession(null);
    window.addEventListener(AUTH_UNAUTHORIZED_EVENT, handleUnauthorized);
    return () => window.removeEventListener(AUTH_UNAUTHORIZED_EVENT, handleUnauthorized);
  }, []);

  function signOut() {
    setAuthSession(null);
    void logout().catch(() => {
      // Local state is cleared even if the backend is temporarily unavailable.
    });
  }

  if (isSessionLoading) {
    return (
      <main className="auth-boot" aria-busy="true" aria-label="Vérification de la session">
        <span className="auth-boot-mark" aria-hidden="true">·</span>
        <span>Vérification de votre session…</span>
      </main>
    );
  }

  if (!authSession) {
    return (
      <Routes>
        <Route path="/login" element={<LoginPage onSignIn={setAuthSession} />} />
        <Route path="*" element={<LandingPage />} />
      </Routes>
    );
  }

  return (
    <Routes>
      <Route path="/" element={<Navigate to="/tableau-de-bord" replace />} />
      <Route path="/login" element={<Navigate to="/tableau-de-bord" replace />} />
      <Route
        path="/*"
        element={
          <AppShell
            theme={theme}
            density={density}
            setTheme={setTheme}
            setDensity={setDensity}
            session={authSession}
            onSignOut={signOut}
          />
        }
      />
    </Routes>
  );
}

export default App;
