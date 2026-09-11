import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { LogIn, AlertCircle, Eye, EyeOff, ShieldCheck } from "lucide-react";
import logo from "../../../assets/brand/lpn-logo-real.png";
import type { AuthSession } from "../types/auth.types";
import { login, toSession } from "../api/authApi";

export function LoginPage({ onSignIn }: { onSignIn: (session: AuthSession) => void }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [rememberMe, setRememberMe] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (loading) return;
    setError(null);
    setLoading(true);
    try {
      const response = await login(username.trim(), password, rememberMe);
      if (response.authenticated && response.user) {
        onSignIn(toSession(response.user));
        return;
      }
      setError(response.message);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Connexion impossible. Réessayez dans un instant.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-surface px-4">
      <div className="pointer-events-none absolute inset-0 bg-grid-glow" />

      <div className="relative w-full max-w-sm">
        <div className="mb-8 flex flex-col items-center">
          <Link to="/">
            <img src={logo} alt="LPN" className="h-12 w-auto" />
          </Link>
          <h1 className="mt-5 text-xl font-bold text-ink">Connexion à la plateforme</h1>
          <p className="mt-1 text-center text-sm text-slate-500">
            Accès réservé aux collaborateurs et partenaires LPN.
          </p>
        </div>

        <form onSubmit={onSubmit} className="lb-card space-y-4 p-6">
          {error && (
            <div className="flex items-start gap-2 rounded-xl border border-alert/20 bg-alert/5 px-3 py-2 text-sm text-alert">
              <AlertCircle size={16} className="mt-0.5 shrink-0" />
              <span>{error}</span>
            </div>
          )}
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-600">Identifiant professionnel</label>
            <input
              type="text"
              required
              autoComplete="username"
              autoFocus
              placeholder="prenom.nom ou adresse e-mail"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="lb-input-field"
              disabled={loading}
            />
          </div>
          <div>
            <div className="mb-1 flex items-center justify-between">
              <label className="block text-sm font-medium text-slate-600">Mot de passe</label>
            </div>
            <div className="relative">
              <input
                type={showPassword ? "text" : "password"}
                required
                autoComplete="current-password"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="lb-input-field pr-10"
                disabled={loading}
              />
              <button
                type="button"
                onClick={() => setShowPassword((s) => !s)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                aria-label={showPassword ? "Masquer le mot de passe" : "Afficher le mot de passe"}
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
          </div>
          <label className="flex items-center gap-2 text-sm text-slate-500">
            <input
              type="checkbox"
              checked={rememberMe}
              onChange={(e) => setRememberMe(e.target.checked)}
              disabled={loading}
              className="h-4 w-4 rounded border-slate-300 text-lpn-500 focus:ring-lpn-200"
            />
            Rester connecté sur cet appareil
          </label>
          <button type="submit" disabled={loading} className="lb-btn-primary w-full">
            <LogIn size={16} /> {loading ? "Connexion…" : "Se connecter"}
          </button>

          <div className="flex items-center gap-2 rounded-xl border border-lpn-100 bg-lpn-50/60 px-3 py-2 text-xs text-lpn-700">
            <ShieldCheck size={14} className="shrink-0" />
            <span>Session sécurisée · Accès selon votre rôle · Aucune auto-inscription</span>
          </div>
        </form>

        <p className="mt-6 text-center text-xs text-slate-400">
          Plateforme d'entreprise fermée — les comptes sont créés uniquement par un administrateur.
          Mot de passe oublié ? Contactez votre administrateur LPN.
        </p>
      </div>
    </div>
  );
}
