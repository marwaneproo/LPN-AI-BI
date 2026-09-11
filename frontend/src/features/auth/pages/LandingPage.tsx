import { Link } from "react-router-dom";
import {
  TrendingUp,
  BarChart3,
  MessageSquareCode,
  Cpu,
  Users,
  Truck,
  ArrowRight,
} from "lucide-react";
import logo from "../../../assets/brand/lpn-logo-real.png";
import hero from "../../../assets/brand/hero-lpn.png";

const FEATURES = [
  {
    icon: TrendingUp,
    title: "Prévision du Chiffre d'Affaires",
    desc: "Modèles prédictifs entraînés sur l'historique des ventes pour anticiper la demande à 3, 6 ou 12 mois.",
  },
  {
    icon: BarChart3,
    title: "Reporting Dynamique",
    desc: "Tableaux de bord vivants : commandes, facturation, clients et produits, à jour en continu.",
  },
  {
    icon: MessageSquareCode,
    title: "Assistant Conversationnel",
    desc: "Posez une question en langage naturel, obtenez la requête SQL, les données et la réponse.",
  },
  {
    icon: Cpu,
    title: "Automatisation Intelligente",
    desc: "Le pipeline IA route, valide et sécurise chaque requête avant de l'exécuter sur l'entrepôt de données.",
  },
  {
    icon: Users,
    title: "Portail B2B Libre-Service",
    desc: "Un accès dédié pour les partenaires et maisons d'édition afin de suivre commandes et facturation.",
  },
  {
    icon: Truck,
    title: "Diffusion & Distribution",
    desc: "Visibilité en temps réel sur la couverture logistique et les délais de diffusion du livre marocain.",
  },
];

const STATS = [
  { value: "160+", label: "maisons d'édition partenaires actives" },
  { value: "70+", label: "ans d'expertise, depuis 1951" },
  { value: "94.2%", label: "confiance IA sur les prévisions" },
];

export function LandingPage() {
  return (
    <div className="min-h-screen bg-surface text-ink">
      {/* Header */}
      <header className="sticky top-0 z-30 border-b border-slate-100 bg-white/80 backdrop-blur">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
          <img src={logo} alt="Librairie Papeterie Nationale" className="h-10 w-auto" />
          <nav className="hidden items-center gap-8 text-sm font-medium text-slate-600 md:flex">
            <a href="#apropos" className="hover:text-lpn-600">À propos</a>
            <a href="#fonctionnalites" className="hover:text-lpn-600">Fonctionnalités</a>
            <a href="#partenaires" className="hover:text-lpn-600">Partenaires</a>
          </nav>
          <div className="flex items-center gap-3">
            <Link to="/login" className="lb-btn-primary !px-4 !py-2">Se connecter</Link>
          </div>
        </div>
      </header>

      {/* Hero */}
      <section className="relative overflow-hidden bg-grid-glow">
        <div className="mx-auto grid max-w-7xl items-center gap-12 px-6 py-20 md:grid-cols-2 md:py-28">
          <div>
            <span className="lb-badge bg-lpn-50 text-lpn-600">Depuis 1951 · Rabat-Salé, Maroc</span>
            <h1 className="mt-5 text-4xl font-extrabold leading-tight tracking-tight text-ink md:text-5xl">
              L'Intelligence au service du <span className="text-lpn-500">Livre Marocain</span>
            </h1>
            <p className="mt-5 max-w-lg text-base leading-relaxed text-slate-600">
              La Librairie Papeterie Nationale combine IA générative, Business Intelligence et analyse
              prédictive pour piloter la diffusion du livre marocain depuis plus de 70 ans — une seule
              plateforme pour décider plus vite et plus juste.
            </p>
            <div className="mt-8 flex flex-wrap gap-4">
              <Link to="/login" className="lb-btn-primary">
                Accéder à la plateforme <ArrowRight size={16} />
              </Link>
              <a href="#fonctionnalites" className="lb-btn-secondary">Voir les fonctionnalités</a>
            </div>
          </div>
          <div className="relative">
            <div className="absolute -inset-6 -z-10 rounded-[2rem] bg-lpn-500/10 blur-2xl" />
            <img
              src={hero}
              alt="Illustration de la plateforme d'intelligence business LPN"
              className="mx-auto w-full max-w-md rounded-2xl"
            />
          </div>
        </div>
      </section>

      {/* Stats */}
      <section className="border-y border-slate-100 bg-white">
        <div className="mx-auto grid max-w-5xl grid-cols-1 gap-8 px-6 py-14 text-center sm:grid-cols-3">
          {STATS.map((s) => (
            <div key={s.label}>
              <p className="text-4xl font-extrabold text-lpn-500">{s.value}</p>
              <p className="mt-2 text-sm text-slate-500">{s.label}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Features */}
      <section id="fonctionnalites" className="mx-auto max-w-7xl px-6 py-24">
        <div className="mx-auto max-w-2xl text-center">
          <h2 className="text-3xl font-bold tracking-tight text-ink">Une plateforme, six leviers de décision</h2>
          <p className="mt-3 text-slate-500">
            Chaque module s'appuie sur le même socle de données pour donner à chaque équipe l'information
            dont elle a besoin, au moment où elle en a besoin.
          </p>
        </div>
        <div className="mt-14 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map((f) => (
            <div key={f.title} className="lb-card p-6 transition hover:-translate-y-0.5 hover:shadow-lg">
              <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-lpn-50 text-lpn-500">
                <f.icon size={22} />
              </div>
              <h3 className="mt-4 text-base font-semibold text-ink">{f.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-slate-500">{f.desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* About / partners */}
      <section id="apropos" className="border-t border-slate-100 bg-white py-20">
        <div className="mx-auto max-w-4xl px-6 text-center">
          <h2 className="text-2xl font-bold text-ink">Un partenaire historique de l'édition marocaine</h2>
          <p className="mt-4 text-slate-500">
            Depuis 1951, la Librairie Papeterie Nationale accompagne la diffusion du savoir au Maroc.
            Cette plateforme prolonge cet engagement avec des outils d'aide à la décision fondés sur la donnée.
          </p>
        </div>
      </section>
      <section id="partenaires" className="mx-auto max-w-5xl px-6 py-16 text-center text-sm text-slate-400">
        160+ maisons d'édition et institutions font confiance à la LPN pour la diffusion de leurs ouvrages.
      </section>

      {/* Footer */}
      <footer className="border-t border-slate-100 bg-white">
        <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-4 px-6 py-8 text-sm text-slate-400 md:flex-row">
          <img src={logo} alt="LPN" className="h-8 w-auto opacity-80" />
          <nav className="flex gap-6">
            <a href="#apropos" className="hover:text-lpn-600">À propos</a>
            <a href="#fonctionnalites" className="hover:text-lpn-600">Fonctionnalités</a>
            <Link to="/login" className="hover:text-lpn-600">Connexion</Link>
          </nav>
          <p>© 2026 Librairie Papeterie Nationale — Depuis 1951</p>
        </div>
      </footer>
    </div>
  );
}
