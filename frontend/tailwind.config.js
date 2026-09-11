/** @type {import('tailwindcss').Config} */
export default {
  // Scoped to the new "brand" surfaces only (landing, login, floating assistant).
  // Kept separate from the existing hand-written theme.css design system.
  content: [
    "./src/features/auth/pages/LandingPage.tsx",
    "./src/features/auth/pages/LoginPage.tsx",
    "./src/components/chatbot/**/*.{ts,tsx}",
    "./src/features/admin/**/*.{ts,tsx}",
  ],
  corePlugins: {
    // Preflight resets margins/paddings/typography globally, which would
    // clash with the existing app-wide theme.css. We disable it so Tailwind
    // only contributes utility classes on the new components.
    preflight: false,
  },
  theme: {
    extend: {
      colors: {
        lpn: {
          50: "#eef4fd",
          100: "#dbe9fb",
          200: "#b0cdf5",
          300: "#84b1ee",
          400: "#4a83e2",
          500: "#1f54bf",
          600: "#1a46a3",
          700: "#153887",
          800: "#112a66",
          900: "#0f2354",
        },
        ink: "#0f172a",
        surface: "#f8fafc",
        positive: "#059669",
        alert: "#f43f5e",
      },
      fontFamily: {
        sans: ["Inter", "ui-sans-serif", "system-ui", "sans-serif"],
      },
      boxShadow: {
        card: "0 1px 2px 0 rgb(15 23 42 / 0.04), 0 1px 6px -1px rgb(15 23 42 / 0.06)",
      },
      backgroundImage: {
        "grid-glow":
          "radial-gradient(circle at 20% 20%, rgba(31,84,191,0.12), transparent 40%), radial-gradient(circle at 80% 0%, rgba(31,84,191,0.10), transparent 35%)",
      },
    },
  },
  plugins: [],
};
