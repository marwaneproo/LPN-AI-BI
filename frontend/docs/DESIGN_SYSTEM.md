# LPN AI-BI — Frontend Design System

**Version:** 0.1 (Phase 1 — design definition only, no code)
**Owner:** Youssef Bahaddou
**Last revised:** 2026-04-29
**Default theme:** dark
**Target viewport:** ≥1280px desktop primary, ≥1024px tablet acceptable, <1024px collapses sidebar

This document is the source of truth for visual and interaction decisions. Every component built in Phases 2–7 must reference a token defined here. If a screen needs a value not defined here, the value is added to this document **first**, then used in code.

---

## 0. Design Position

The LPN AI-BI frontend sits in the lineage of **Linear, Vercel Dashboard, Stripe Dashboard, Notion, Cursor, Claude.ai, Raycast, Datadog, and Grafana**. It is a serious internal data tool. It is not:

- a SaaS marketing site
- a generic AI dashboard ("purple-blue gradient on every card")
- an admin template
- a consumer chat product

The design north stars are **restraint**, **density**, **typographic discipline**, and **calm motion**. White space is a feature; decoration is a tax.

---

## 1. Reference Research — what we are adapting

For each reference below, 2–3 specific patterns we are **adapting** (not copying). All patterns are concrete enough to implement.

### Linear (linear.app)

1. **LCH/OKLCH-based palette with three generative inputs** — Linear's redesigned theming defines `base`, `accent`, and `contrast` and derives the rest. We will adopt this in spirit: a small set of OKLCH ramps generated from a controlled lightness ladder, not eyeballed hex values. *(Source: Linear "How we redesigned the Linear UI part II".)*
2. **Active-route indication via 2px left accent bar + low-contrast hover background** — never a heavy fill, never a saturated color. Active = quiet authority.
3. **Semi-low-contrast 1px borders carry hierarchy; shadows are reserved for true overlays only** — we will copy this rule literally.

### Vercel Dashboard (vercel.com)

1. **240–280px persistent sidebar with collapsible-to-icon behavior** — we use 240px expanded, 64px collapsed. Sidebar items are 36px tall (desktop, not 44px touch).
2. **Status badges are functional, not decorative** — Vercel uses small monochrome status pills with a single colored dot. We mirror this for "Données du JJ MMM YYYY" snapshot freshness and for "Payé/Impayé" invoice status.
3. **Top-bar Cmd+K trigger is visible and labeled** — not just a hidden shortcut. Surfacing the trigger teaches the keyboard model.

### Stripe Dashboard (stripe.com)

1. **KPI strip = 4 cards: number + sparkline + delta-with-arrow** — concrete, monetary, instantly scannable. Our KPI cards (CA total, commandes du mois, taux d'impayés, rupture stock) follow this structure exactly.
2. **Money formatting is non-negotiably tabular-nums** — every digit aligned. We apply `font-feature-settings: 'tnum'` globally to numbers in tables, KPIs, and chart axes.
3. **Sidebar information architecture by domain not by feature** — Stripe groups around "Balances/Transactions/Customers/Products" (nouns), not "Reports/Settings/Tools". Our sidebar mirrors this: Conversation / Tableau de bord / Historique are the user's mental nouns.

### Notion (notion.so)

1. **Hover-revealed affordances** — controls appear on hover beside the row, not always-visible. We apply this to message rows in Conversation (copy/regenerate icons appear on row hover only).
2. **No bubble chat** — message turns are typographic, not boxed. Avatar + label + body + 24px gap, full-bleed within content column. (Same pattern Claude.ai uses.)
3. **Restraint with color** — Notion uses neutrals for 95% of the UI; color is reserved for state. We follow this strictly: accent is for one focal action per surface, max.

### Cursor + Claude.ai

1. **Streaming with reserved space** — answer text streams in token-by-token but the layout does not reflow. Container height is reserved on submit, skeleton occupies it, tokens replace the skeleton.
2. **Inline progress narration** — between submit and first token, show "Récupération du schéma… → Génération de la requête… → Exécution… → Formulation". Each step replaces the previous. Calms 3-second waits.
3. **Chip-based source/SQL/data expansion below the answer** — small low-key chips (`Sources (3)`, `SQL`, `Données (24 lignes)`), each expands inline. We copy this exact pattern.

### Tremor official examples

1. **`Card + Metric + BadgeDelta` composition** for KPIs — semantic structure (label, value, delta), no decoration.
2. **Charts inside Cards with consistent 16px internal padding and a 1px border** — never floating, never shadowed.
3. **`BarList` for ranked categorical data** — we use this for top-revenue categories rather than reaching for a bar chart that needs axes.

### Datadog + Grafana

1. **Chart legibility under information density** — minimal axes, no chart titles inside cards (the card title is the chart title), gridlines at the lowest contrast that's still readable.
2. **Numerical density over whitespace** — power users want data. Our default density is "comfortable" but the line-height and padding are tuned for power use, not marketing reading.

### Raycast + cmdk (paco-coursey/cmdk)

1. **Cmd+K is the second navigation system** — not a search box, an action palette: jump to dashboard, switch theme, log out, search past conversations. Verbs first.
2. **List rows are 36px with 12px horizontal padding, icon-left, label-center, hint-right** — copy this geometry exactly.
3. **No empty state inside the palette** — show recent commands as defaults so the palette is never blank.

---

## 2. Reference Anti-Patterns — what we will NOT do

These are explicit failure modes for AI-generated frontends. Listed so they can be cited in review.

- ❌ Purple-to-blue gradients on cards or backgrounds
- ❌ Glassmorphism (`backdrop-blur` everywhere) without a structural reason
- ❌ Oversized hero sections in an internal tool
- ❌ Emoji-as-icon in serious BI surfaces (icons are Lucide only)
- ❌ `rounded-3xl` on small elements
- ❌ "Floating" cards on gradient mesh backgrounds
- ❌ Generic Tailwind-UI hero patterns
- ❌ Drop shadows with high blur and heavy opacity (modern shadows are tight, near-imperceptible)
- ❌ Animated mesh / aurora / parallax backgrounds
- ❌ "AI sparkle" iconography unless contextually meaningful (the only sparkle allowed is the reasoning-mode toggle, and it's a Lucide `Sparkles` at 14px, used once)
- ❌ `cursor: pointer` on non-interactive elements
- ❌ Bright "success green / danger red" — use desaturated state colors that read as data, not as alarms
- ❌ Spinner on first content load (skeletons only)
- ❌ Multiple primary buttons per surface
- ❌ Centered marketing-style hero on internal pages

---

## 3. Brand

LPN does not provide a logo asset. We define an understated wordmark for this product.

### 3.1 Wordmark

```
lpn·ai
```

- Lowercase wordmark `lpn·ai` set in the body sans (Inter Variable / Geist Sans), weight **600**, size **15px**, letter-spacing **-0.01em**, color `--text-primary`.
- The `·` (middle dot, U+00B7) is the only ornament. It uses `--accent` color at the same size.
- The "AI" suffix is intentional — distinguishes the AI-BI tool from any future LPN-internal product.
- Never set on a colored background panel; never inside a logo lockup.
- Minimum size: 13px. Maximum size in product chrome: 15px (sidebar header). Larger sizes only on the login page (max 22px).

### 3.2 Favicon / app icon

A single character `·` rendered in a 32×32 rounded square (`rounded-md`, `--accent` background, white dot). No "L", no "LPN", no logomark. The dot is the brand.

### 3.3 Brand color

The accent color is `--accent` (defined in §5). It is **not** a brand color in the marketing sense — it is the focal-action color used sparingly. There is no secondary brand color.

---

## 4. Typography

### 4.1 Font family

- **Sans (UI + body):** `Inter Variable` self-hosted via `@fontsource-variable/inter`. Fallback stack: `system-ui, -apple-system, "Segoe UI", sans-serif`.
- **Mono (SQL, code, latency, IDs):** `JetBrains Mono Variable` self-hosted via `@fontsource-variable/jetbrains-mono`. Fallback: `ui-monospace, SFMono-Regular, Menlo, Consolas, monospace`.
- No serif. No display face.
- **Variable axis policy:** use `font-variation-settings` for weight, never load multiple static weights.
- **Numerical features:** apply `font-feature-settings: "tnum", "cv11"` to body root; `cv11` gives Inter's straight-sided digit `1` which aligns better in tables.

Rationale: Inter is the reference for data-app UI (Linear, Stripe, Vercel). JetBrains Mono is the most legible variable mono at small sizes; SQL code blocks render cleanly at 13px.

### 4.2 Type scale (7 tokens, no more)

| Token | px / rem | Line height | Weight | Tracking | Use |
|-------|----------|-------------|--------|----------|-----|
| `--text-display` | 28 / 1.75rem | 1.15 | 600 | -0.02em | Login page only; never inside the app |
| `--text-h1` | 20 / 1.25rem | 1.3 | 600 | -0.015em | Page title (one per page, in top bar slot) |
| `--text-h2` | 15 / 0.9375rem | 1.4 | 600 | -0.005em | Section headers (KPI strip label, panel title) |
| `--text-body` | 14 / 0.875rem | 1.5 | 400 | 0 | Default body, message text, table cells |
| `--text-small` | 13 / 0.8125rem | 1.45 | 400 | 0 | Sidebar items, secondary labels, chips |
| `--text-micro` | 12 / 0.75rem | 1.4 | 500 | 0.005em | Captions, metadata, badge text, timestamps |
| `--text-mono` | 13 / 0.8125rem | 1.5 | 400 | 0 | SQL blocks, latency, IDs (always JetBrains Mono) |

**Rules:**
- Page title (`h1`) is rendered **once** per page, inside the top bar's breadcrumb slot — not as a giant title at the top of the content.
- The display token exists for the login page and 404/500. Never appears inside the app shell.
- Body and small are the workhorses. ~85% of the UI is one of these two.
- Headings get `font-feature-settings: "ss01"` (Inter stylistic set with curved single-storey `a`) for a slightly more refined heading feel; body keeps the default.

### 4.3 Weights used

`400` (body), `500` (micro labels, small emphasis), `600` (headings, KPI numbers, primary buttons). Nothing else. No 300, no 700, no 800.

---

## 5. Color Tokens (OKLCH)

Tailwind v4 with `@theme` directive. All colors expressed in OKLCH for perceptual uniformity. Dark mode is the **default**; light mode is a supported variant.

### 5.1 Naming convention

Two layers:

- **Primitive ramps** (reference layer): `--gray-50` … `--gray-950`, `--accent-50` … `--accent-950`, plus state ramps. Never used directly in components.
- **Semantic tokens** (consumed layer): `--bg`, `--bg-elevated`, `--border`, `--text-primary`, etc. Components use these only.

This indirection is what lets us swap themes without touching component code.

### 5.2 Primitive ramps

**Gray ramp (neutral, slightly cool):**

| Token | OKLCH | Notional hex |
|-------|-------|--------------|
| `--gray-50`  | `oklch(98.4% 0.002 250)` | #f7f8f9 |
| `--gray-100` | `oklch(96.0% 0.003 250)` | #ecedef |
| `--gray-200` | `oklch(91.5% 0.004 250)` | #dcdee2 |
| `--gray-300` | `oklch(83.0% 0.005 250)` | #c1c4ca |
| `--gray-400` | `oklch(70.0% 0.006 250)` | #9a9ea6 |
| `--gray-500` | `oklch(56.5% 0.008 250)` | #757982 |
| `--gray-600` | `oklch(45.0% 0.008 250)` | #5a5e66 |
| `--gray-700` | `oklch(34.0% 0.008 250)` | #43464d |
| `--gray-800` | `oklch(24.5% 0.007 250)` | #2f3137 |
| `--gray-850` | `oklch(20.0% 0.006 250)` | #25272b |
| `--gray-900` | `oklch(16.5% 0.005 250)` | #1d1e22 |
| `--gray-950` | `oklch(12.5% 0.004 250)` | #14151a |

Hue 250° = a faint cool blue undertone. Chroma stays ≤ 0.008 — true neutral, no perceptible tint.

**Accent ramp (single hue, used sparingly):**

| Token | OKLCH | Notional hex |
|-------|-------|--------------|
| `--accent-100` | `oklch(94.0% 0.030 245)` | #e2eaf6 |
| `--accent-300` | `oklch(80.0% 0.080 245)` | #a8bee5 |
| `--accent-500` | `oklch(64.0% 0.140 245)` | #5a86d3 |
| `--accent-600` | `oklch(58.0% 0.150 245)` | #4a76c5 |
| `--accent-700` | `oklch(50.0% 0.140 245)` | #3d63ab |

Hue 245° = a desaturated indigo-blue. Chroma capped at 0.150 (no neon). On dark backgrounds we use `--accent-500`; on light backgrounds `--accent-600`.

**State ramps** — each defined as `bg / border / fg` triplets, two values per token (dark / light context). Hues chosen to remain readable as **data signals**, not alarms.

| State | Dark `bg / border / fg` | Light `bg / border / fg` |
|-------|--------------------------|---------------------------|
| Success | `oklch(22% 0.04 155)` / `oklch(38% 0.08 155)` / `oklch(78% 0.13 155)` | `oklch(96% 0.03 155)` / `oklch(82% 0.08 155)` / `oklch(40% 0.12 155)` |
| Warning | `oklch(24% 0.05 75)`  / `oklch(42% 0.10 75)`  / `oklch(82% 0.13 75)`  | `oklch(96% 0.04 75)`  / `oklch(82% 0.10 75)`  / `oklch(45% 0.13 75)`  |
| Danger  | `oklch(24% 0.06 27)`  / `oklch(42% 0.13 27)`  / `oklch(78% 0.14 27)`  | `oklch(96% 0.04 27)`  / `oklch(80% 0.10 27)`  / `oklch(48% 0.16 27)`  |
| Info    | `oklch(22% 0.05 230)` / `oklch(40% 0.10 230)` / `oklch(82% 0.12 230)` | `oklch(96% 0.04 230)` / `oklch(80% 0.10 230)` / `oklch(48% 0.14 230)` |

### 5.3 Semantic tokens (the layer components use)

#### Dark theme (default)

| Token | Maps to | Use |
|-------|---------|-----|
| `--bg` | `--gray-950` | Page background |
| `--bg-elevated` | `--gray-900` | Cards, sidebar, panels |
| `--bg-overlay` | `--gray-850` | Popover, dropdown, command palette, modal surface |
| `--bg-hover` | `oklch(20% 0.006 250 / 0.6)` | Sidebar/list row hover (semi-transparent) |
| `--bg-active` | `oklch(24% 0.007 250 / 0.8)` | Sidebar active row |
| `--bg-input` | `--gray-900` | Form inputs, textarea |
| `--border` | `oklch(28% 0.007 250)` | Default dividers, card edges |
| `--border-strong` | `oklch(36% 0.008 250)` | Input focus border, emphasized dividers |
| `--text-primary` | `--gray-100` | Body, headings |
| `--text-secondary` | `--gray-400` | Labels, secondary content |
| `--text-tertiary` | `--gray-500` | Captions, metadata, placeholder |
| `--text-disabled` | `--gray-700` | Disabled text |
| `--accent` | `--accent-500` | Focal action, active route bar, focus ring |
| `--accent-fg` | `oklch(98% 0.005 245)` | Text on `--accent` background |
| `--ring` | `--accent-500` | Focus ring color |

#### Light theme

| Token | Maps to |
|-------|---------|
| `--bg` | `--gray-50` |
| `--bg-elevated` | `oklch(100% 0 0)` (pure white) |
| `--bg-overlay` | `oklch(100% 0 0)` |
| `--bg-hover` | `oklch(94% 0.003 250)` |
| `--bg-active` | `oklch(91% 0.004 250)` |
| `--bg-input` | `oklch(100% 0 0)` |
| `--border` | `--gray-200` |
| `--border-strong` | `--gray-300` |
| `--text-primary` | `--gray-900` |
| `--text-secondary` | `--gray-600` |
| `--text-tertiary` | `--gray-500` |
| `--text-disabled` | `--gray-300` |
| `--accent` | `--accent-600` |
| `--accent-fg` | `oklch(99% 0.005 245)` |
| `--ring` | `--accent-600` |

### 5.4 Color usage rules

- **One accent action per surface.** A page has at most one primary button. If the page seems to need two, one is wrong.
- **State colors only carry meaning.** Never use success-green as a brand color, never warning-orange as a callout color. If it's not a state, it's neutral.
- **Charts use neutrals + accent first**, state colors only when the data dimension *is* a state. CA trend = single accent line. Stock rupture risk = state ramp because the dimension is risk.
- **Borders carry hierarchy**, shadows do not. If a card needs to feel elevated, raise the bg one step (`--bg` → `--bg-elevated`), don't add shadow.

---

## 6. Spacing

Tailwind's default 4px scale, no invented values. The full scale is available; the table below documents **which sizes are used for which purpose** so usage stays consistent.

| Tailwind class | px | Use |
|----------------|----|-----|
| `gap-1` / `p-1` | 4 | Icon ↔ label inside a chip; never as outer padding |
| `gap-1.5` / `p-1.5` | 6 | Compact chip vertical padding |
| `gap-2` / `p-2` | 8 | Inline element gap; chip outer padding; small button |
| `gap-3` / `p-3` | 12 | Sidebar row padding; list-item internal gap |
| `gap-4` / `p-4` | 16 | **Default card internal padding**; default form field gap |
| `gap-5` / `p-5` | 20 | Card padding for dense charts |
| `gap-6` / `p-6` | 24 | **Default section gap inside a page**; conversation message vertical gap |
| `gap-8` / `p-8` | 32 | Major section gap; page top padding |
| `gap-12` / `py-12` | 48 | Empty state vertical centering padding |
| `gap-16` | 64 | Login page card top margin |

**Page horizontal margins** are handled by `max-w-screen-2xl mx-auto px-8` on the content container. No inner page wrapper adds horizontal padding.

**No arbitrary values** like `p-[13px]` allowed. If a component needs an unlisted size, the scale is wrong; reconsider.

---

## 7. Radius

Three values, total. No more.

| Token | Value | Use |
|-------|-------|-----|
| `--radius-sm` | `4px` | Inputs, small chips, status pills, table cell focus |
| `--radius-md` | `6px` | Cards, panels, message containers, buttons |
| `--radius-lg` | `10px` | Modals, popovers, command palette, sheet |

- The favicon/app icon uses `--radius-md` (6px) at 32×32.
- Avatars are circular (`rounded-full`) — exception, not part of the scale.
- Charts and tables are radius-zero on their inner edges; the **card** carries the radius.

---

## 8. Borders & Dividers

- Always **1px**, never thicker.
- Default: `--border` (low contrast). Emphasized: `--border-strong`.
- **Borders over shadows for hierarchy.** A card on the page is `bg-elevated` + 1px border, no shadow. A card on a card is `bg-overlay` + 1px border, no shadow. Shadows are reserved for true overlays (popover, dropdown, modal — see §10).
- Dividers between rows in a list use `--border` on `border-b`, not a custom hairline.

---

## 9. Shadows

Used only for **true overlay surfaces** that float above the page coordinate space. Tight, near-imperceptible, modern.

| Token | Value | Use |
|-------|-------|-----|
| `--shadow-overlay-sm` | `0 1px 2px 0 oklch(0% 0 0 / 0.06), 0 0 0 1px oklch(0% 0 0 / 0.04)` | Tooltip, small popover |
| `--shadow-overlay-md` | `0 4px 12px -2px oklch(0% 0 0 / 0.10), 0 0 0 1px oklch(0% 0 0 / 0.05)` | Dropdown, select menu, command palette |
| `--shadow-overlay-lg` | `0 16px 32px -8px oklch(0% 0 0 / 0.18), 0 0 0 1px oklch(0% 0 0 / 0.06)` | Modal, sheet |

Note the integrated 1px ring in each — replaces a separate border on the floating element.

**Cards never have shadow.** Buttons never have shadow. Inputs never have shadow. If you find yourself reaching for shadow on a non-overlay, reach for `--bg-elevated` instead.

---

## 10. Motion

Four timings, total. Every animation maps to exactly one.

| Token | Duration | Easing | Use |
|-------|----------|--------|-----|
| `--motion-instant` | 75ms | `cubic-bezier(0, 0, 0.2, 1)` (ease-out) | Checkbox tick, toggle flip, chip press feedback |
| `--motion-fast` | 150ms | `cubic-bezier(0, 0, 0.2, 1)` (ease-out) | Hover background, focus ring, color transitions |
| `--motion-medium` | 250ms | `cubic-bezier(0.16, 1, 0.3, 1)` (ease-out-expo) | Sheet open/close, dialog open/close, sidebar collapse, inline panel expand |
| `--motion-slow` | 400ms | `cubic-bezier(0.22, 1, 0.36, 1)` (ease-out-quart) | Route transition (fade + 4px translate-y) |

**Rules:**

- Default property to animate: `opacity`, `transform`, `background-color`. Never `width`, `height`, `top`, `left` (layout thrash).
- Sheet/dialog enter: `opacity 0 → 1` + `translate-y 8px → 0` over `--motion-medium`.
- Streaming text reveal: instant per-token paint, no per-character animation. The "typing" effect is the token cadence itself.
- Skeleton shimmer: a single 1.5s linear loop with **5% opacity amplitude** (default `animate-pulse` is too noisy at ~50%). Override:
  ```css
  @keyframes pulse-quiet { 0%, 100% { opacity: 1 } 50% { opacity: 0.95 } }
  ```
- **`prefers-reduced-motion: reduce`** — every transition with duration > `--motion-instant` becomes instant. Implemented via global media query in theme.css, not per-component.

---

## 11. Density

The product is data-dense. Two density modes are supported.

| Variable | Comfortable (default) | Compact |
|----------|------------------------|---------|
| Sidebar row height | 36px | 32px |
| Top bar height | 48px | 44px |
| Table row height | 40px | 32px |
| KPI card padding | 20px | 16px |
| Default card padding | 16px | 12px |
| Conversation message vertical gap | 24px | 16px |
| Form field height | 36px | 32px |

**Components with density variants:** sidebar, top bar, table, KPI card, conversation message list, form field.
**Components without density variants** (intentionally fixed): chart cards, modal, command palette, login page.

Density is set on `<html data-density="comfortable|compact">` and consumed via CSS variables. Persisted to localStorage. Switch lives in Settings > Apparence.

---

## 12. Iconography

- **Library:** Lucide React. **No other icon set.** No emoji as icon.
- **Stroke width: 1.5** (override Lucide's default of 2). Lucide at stroke-2 reads heavy in dense UI.
- **Sizes:**
  - 14px: inside chips, status pills, micro contexts
  - 16px: **default** — sidebar, table cells, body inline icons
  - 20px: section headers, prominent inline use
  - 24px: empty states, primary navigation only
  - 32px+: never (use illustration or none)
- **Color:** `currentColor`, inheriting from `--text-secondary` by default. Active/focus states inherit from `--text-primary` or `--accent`.
- **Alignment:** all icons are vertically centered against the cap-height of adjacent text using `inline-flex items-center` on the wrapper, never margin-hacked.

---

## 13. Layout & Information Architecture

### 13.1 Global grid

| Variable | Value |
|----------|-------|
| Page max width | `max-w-screen-2xl` (1536px) |
| Page horizontal padding | `px-8` (32px) at ≥1280px, `px-6` (24px) at 1024–1279px |
| Sidebar (expanded) | 240px |
| Sidebar (collapsed) | 64px |
| Top bar height | 48px (comfortable) / 44px (compact) |
| Page top padding | 32px (comfortable) / 24px (compact) |
| Page bottom padding | 64px (clears the conversation input bar bottom shadow) |
| Content column max width | 960px for prose surfaces (Conversation answer column); full content area otherwise |

### 13.2 App shell composition

```
┌──────────────────────────────────────────────────────────────────────┐
│ Top bar  [breadcrumb]              [snapshot badge] [⌘K] [user menu] │  48px
├────────────┬─────────────────────────────────────────────────────────┤
│            │                                                         │
│  Sidebar   │  Main content area                                      │
│  240px     │  max-w-screen-2xl, px-8                                 │
│            │                                                         │
│            │                                                         │
└────────────┴─────────────────────────────────────────────────────────┘
```

### 13.3 Conversation variant

When route is `/conversation`, the global sidebar is **replaced** (not added to) by a **conversation history sidebar** of the same 240px width. The top bar is unchanged. The main area is split:

```
┌──────────────────────────────────────────────────────────────────────┐
│ Top bar                                                              │
├────────────┬─────────────────────────────────────────────────────────┤
│ Conv. hist │  Message scroll area (max-w 960px, centered)            │
│ sidebar    │                                                         │
│ 240px      │                                                         │
│            ├─────────────────────────────────────────────────────────┤
│            │  Input bar (sticky, border-top, 16px padding)           │
└────────────┴─────────────────────────────────────────────────────────┘
```

The decision: replace, not add. Two sidebars side-by-side is a tell of poor IA. Switching contexts swaps the panel.

### 13.4 Breakpoints

| Breakpoint | Tailwind | Behavior |
|------------|----------|----------|
| ≥1536px | `2xl:` | Full layout, page padding `px-8`, content centered with leftover ghost margin |
| ≥1280px | `xl:` | Full layout, page padding `px-8` |
| ≥1024px | `lg:` | Full layout, page padding `px-6`, KPI strip stays 4-up |
| <1024px | `md:` and below | Sidebar collapses into off-canvas Sheet; KPI strip becomes 2-up; charts stack 1-column. **Not the v1 target** — degrade gracefully, do not overinvest. |

### 13.5 Scroll behavior

- **Page body** is the scroll container, except on Conversation where the **message list** is its own scroll container so the input bar stays sticky at viewport-bottom inside the content column.
- Sidebar is its own scroll container (`overflow-y: auto`, `scrollbar-gutter: stable`).
- Custom scrollbar: 8px wide, `--gray-700` thumb, transparent track. Only visible on hover of the scroll container (`scrollbar-color` + `:hover` override).

---

## 14. French Copy Guidelines

All UI copy is **French (vous)**. English is wired as a fallback in `i18n.ts` but not surfaced in v1.

### 14.1 Tone

- Formal **vous** throughout. Never **tu**.
- Calm, declarative. No exclamation marks. No "Oups !" or "Aïe !".
- No anglicisms when a natural French term exists.
- Errors and empty states explain **what happened** and **what to do**, in that order, in two sentences max.

### 14.2 Glossary (canonical terms)

| French term | English equivalent | Notes |
|-------------|---------------------|-------|
| **Tableau de bord** | Dashboard | Use this, never "Dashboard" |
| **Conversation** | Chat / Conversation | "Conversation" is the term |
| **Historique** | History | |
| **Réglages** | Settings | "Réglages" is shorter than "Paramètres", we prefer it |
| **Apparence** | Appearance | |
| **Mode sombre / Mode clair / Système** | Dark / Light / System | Theme labels |
| **Mode raisonnement** | Reasoning mode | The cloud-LLM toggle |
| **Mode développeur** / **Mode debug** | Debug mode | Role-gated debug toggle. Use "Mode développeur". |
| **Sources** | Sources | French is the same word |
| **Données** | Data | When followed by row count: "Données (24 lignes)" |
| **Latence** | Latency | Always followed by ms or s, mono font |
| **Chiffre d'affaires** / **CA** | Revenue / Sales | "CA" abbreviation OK in dense contexts; full term in headers |
| **Commande** | Order | |
| **Facture** | Invoice | |
| **Client** | Customer | |
| **Produit** | Product | |
| **Rupture (de stock)** | Stockout | "Risque de rupture" for stockout risk |
| **Rentrée scolaire** | Back-to-school | Important LPN term, leave untranslated |
| **Données du JJ MMM YYYY** | Data as of … | Snapshot freshness banner format, e.g. "Données du 12 avr. 2026" |
| **Poser une question** | Ask a question | Primary CTA in conversation empty state |
| **Nouvelle conversation** | New conversation | |
| **Reformuler** | Rephrase | Recovery action after an error |
| **Se connecter** | Sign in | Login button |
| **Déconnexion** | Sign out | User menu item |

### 14.3 Number, date, currency formatting

- **Numbers:** French locale — thousands separated by **non-breaking space** (` `), decimal **comma**. `1 234 567,89`. Implemented via `Intl.NumberFormat('fr-FR')`.
- **Currency:** Moroccan dirham, formatted as `1 234 567,89 MAD` (suffix, space before unit). Charts may abbreviate: `1,2 M MAD` for millions.
- **Dates:** short form `12 avr. 2026` for badges and lists; long form `mardi 12 avril 2026` only on detail surfaces. Implemented via `Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium' })`.
- **Time:** 24-hour `14:32`. Latency in ms (`245 ms`) under 1000ms, in seconds with one decimal (`1,2 s`) above.
- **Percentages:** with non-breaking space before `%`: `12 %`. Decimals with comma.

### 14.4 Canonical error/state strings

These are the strings actually used. Every component that displays one of these states uses **this exact text** unless explicitly overridden.

| State | Copy |
|-------|------|
| Internal error | « Une erreur interne est survenue. Veuillez réessayer ou reformuler votre demande. » |
| Out-of-scope question | « Je suis l'assistant BI de LPN. Je ne peux répondre qu'aux questions concernant les données de l'entreprise. » |
| Empty result | « Je n'ai trouvé aucune donnée correspondant à votre demande. » |
| Unsafe SQL (422) | « Désolé, je ne peux pas répondre à cette question pour des raisons de sécurité. » |
| Timeout (504) | « La requête a pris trop de temps. Essayez de la simplifier. » |
| Model unavailable | « Le modèle d'IA est temporairement indisponible. » |
| Connection lost | « Connexion perdue. Tentative de reconnexion… » |
| 404 | « Cette page n'existe pas. » + « Retour à l'accueil » CTA |
| 500 | « Une erreur inattendue est survenue. » + « Retour à l'accueil » CTA |
| No conversations yet | « Aucune conversation pour l'instant. Posez votre première question. » |
| No data imported | « Aucun snapshot importé. Le tableau de bord sera disponible après la première importation. » |
| No forecast available | « Prévision non disponible. Données historiques insuffisantes. » |
| Reasoning mode tooltip | « Active un modèle plus puissant pour les questions complexes. Quota limité. » |
| Snapshot freshness badge | « Données du {{date}} » |

### 14.5 Capitalization

French sentence-case for headings (only first word + proper nouns capitalized). Buttons and chips: sentence-case (`Nouvelle conversation`, not `Nouvelle Conversation`).

---

## 15. Accessibility Baseline

Documented here so it is not deferred to "polish."

- **Contrast:** every text/background pair meets WCAG AA (4.5:1 for body, 3:1 for large text). Verified via the dev script `scripts/check-contrast.ts` (Phase 7).
- **Focus rings:** custom 2px ring in `--ring` with 2px offset. Browser default outlines are removed only where replaced. Every focusable element is reachable by Tab in logical DOM order.
- **Keyboard:** Cmd/Ctrl+K opens the command palette globally. Cmd/Ctrl+Enter submits the conversation input. Esc closes any overlay.
- **Reduced motion:** see §10.
- **ARIA:** every icon button has `aria-label`. Every chart has a textual fallback (caption or sr-only summary).
- **Screen reader announcements:** route changes announce the new page title via a `aria-live="polite"` region. Streaming AI answers announce only on completion.

---

## Appendix A — Component Inventory

This is the full list of components and screens the frontend will need. Built in Phase 2 onward. **No shadcn component is installed before its appearance in this list is justified.**

Convention: ✦ = primitive (shadcn or wrapper), ▣ = composite (LPN-specific), ◇ = page-level.

### A.1 Cross-cutting primitives (used by many screens)

| Symbol | Name | Source | Notes |
|--------|------|--------|-------|
| ✦ | Button | shadcn | Variants: `primary` (one accent), `secondary` (border + bg-elevated), `ghost` (no border, hover-bg), `destructive` (state-danger). Sizes: `sm`, `md`, `icon`. |
| ✦ | Input | shadcn | 36px (compact 32px). `--bg-input`, 1px `--border`, focus → `--border-strong` + 2px ring. |
| ✦ | Textarea | shadcn | Autosize 1–6 lines for the conversation input. |
| ✦ | Label | shadcn | Paired with Input/Textarea. |
| ✦ | Tooltip | shadcn (Radix) | `--shadow-overlay-sm`, `--motion-fast` enter. |
| ✦ | Popover | shadcn (Radix) | `--shadow-overlay-md`. |
| ✦ | Dialog | shadcn (Radix) | `--shadow-overlay-lg`, `--motion-medium`. |
| ✦ | Sheet | shadcn (Radix) | Right-side slide-in, used for off-canvas mobile sidebar and import-history detail. |
| ✦ | DropdownMenu | shadcn (Radix) | User menu, table row actions. |
| ✦ | Select | shadcn (Radix) | Settings, filter dropdowns. |
| ✦ | Switch | shadcn (Radix) | Settings toggles, reasoning-mode toggle. |
| ✦ | Tabs | shadcn (Radix) | Settings sections, admin filters. |
| ✦ | Table | shadcn | Recent orders, admin debug log. Density-aware. |
| ✦ | Badge | shadcn | Wrap with state variants. |
| ✦ | Skeleton | shadcn | Override animation per §10 (5% amplitude). |
| ✦ | Separator | shadcn | Used as section divider in settings/menus. |
| ✦ | ScrollArea | shadcn (Radix) | Conversation message list, sidebar. |
| ✦ | Command | shadcn (cmdk) | Powers Cmd+K palette. |
| ✦ | Toast / Sonner | shadcn (sonner) | Reserved use only — confirmations of irreversible actions. Never for routine success. |
| ▣ | Chip | LPN | Inline pill: `Sources (3)`, `SQL`, `Données (24 lignes)`. Click expands an inline panel. |
| ▣ | StatusPill | LPN | Single colored dot + label: `Payé`, `Partiellement payé`, `Impayé`. |
| ▣ | Kbd | LPN | Monospace key hints: `⌘K`, `⌘↵`. |
| ▣ | EmptyState | LPN | Centered icon (24px) + title + body + optional CTA. One template, used everywhere empty. |
| ▣ | ErrorState | LPN | Same shape as EmptyState but with state-warning/danger framing. Used for 422/504/model-unavailable. |
| ▣ | InlineProgress | LPN | The streaming-step narrator (`Récupération du schéma…` etc.). Single line, 14px text, 12px dot. |
| ▣ | Brand / Wordmark | LPN | The `lpn·ai` mark (§3). |
| ▣ | DateBadge | LPN | "Données du 12 avr. 2026" snapshot freshness badge. Click → ImportHistoryDialog. |

### A.2 App shell

| Symbol | Name | Composes | Notes |
|--------|------|----------|-------|
| ▣ | AppLayout | Sidebar + TopBar + `<Outlet/>` + ErrorBoundary | Wraps all authed routes. |
| ▣ | Sidebar | Brand, NavList, UserMenu | 240/64px. Collapse persisted to localStorage. |
| ▣ | NavItem | Lucide icon + label + optional kbd hint | Active = 2px left bar + `--bg-active`. |
| ▣ | TopBar | Breadcrumb, DateBadge, CmdKTrigger, NotificationsBell, UserMenu | 48/44px. |
| ▣ | Breadcrumb | LPN | Crumbs separated by 12px gap and a 12px right-chevron at 14px text-secondary. |
| ▣ | UserMenu | DropdownMenu wrapper | Avatar + name → menu (Réglages, Mode sombre toggle, Déconnexion). |
| ▣ | CmdKTrigger | Button-like with `⌘K` Kbd on the right | Opens CommandPalette. |
| ▣ | NotificationsBell | Lucide bell, decorative in v1 | No badge in v1 (no notifications source yet). |
| ▣ | ImportHistoryDialog | Dialog | Lists last 10 imports from `app.import_history`. Triggered by DateBadge click. |
| ▣ | CommandPalette | Command (cmdk) | Sections: Navigation, Actions, Conversations récentes, Apparence. |

### A.3 Conversation surface (`/conversation`)

| Symbol | Name | Notes |
|--------|------|-------|
| ◇ | ConversationPage | Layout variant: replaces Sidebar with ConversationHistorySidebar. |
| ▣ | ConversationHistorySidebar | Search input + "Nouvelle conversation" button + ConversationList. |
| ▣ | ConversationList | Grouped by date (Aujourd'hui, Hier, Cette semaine, Ce mois-ci, Plus ancien). |
| ▣ | ConversationListItem | First user message (truncated, 2 lines max), timestamp, message count. |
| ▣ | MessageList | ScrollArea + Message(s). Reserves space on submit. |
| ▣ | Message | Avatar + label + body + per-row hover actions (Copy, Regenerate). 24px gap above next. |
| ▣ | UserMessage | Variant of Message with "Vous" label. |
| ▣ | AnswerCard | The AI message body. Composes AnswerBody + AnswerChipsRow. |
| ▣ | AnswerBody | Streaming text, prose-rendered (markdown light: bold, lists, code spans). |
| ▣ | AnswerChipsRow | Row of Chips: SourcesChip, SqlChip, DataChip, plus inline LatencyText. |
| ▣ | SourcesChip + SourcesPanel | Inline expanding panel, lists tables (name, FR description, score). |
| ▣ | SqlChip + SqlPanel | Syntax-highlighted SQL block. Use **shiki** (decision: shiki over react-syntax-highlighter — smaller, themeable via CSS variables, works with our dark-default). |
| ▣ | DataChip + DataPanel | Compact table of the returned rows, paginated 50 at a time. |
| ▣ | LatencyText | `Latence : 1,2 s` in mono, `--text-tertiary`. |
| ▣ | InputBar | Textarea + reasoning toggle + submit button. Sticky bottom, border-top. |
| ▣ | ReasoningToggle | Switch + label `Mode raisonnement` + Tooltip. |
| ▣ | InlineProgress | Step narrator (see A.1). |
| ▣ | ExampleQuestionCard | Empty-state CTA card; click populates input + submits. 4 cards in a 2x2 grid on the empty state. |
| ▣ | ConversationEmptyState | Centered card with title + 4 ExampleQuestionCards. |
| ▣ | AnswerErrorState | Inline error variant: 422 / 504 / model-unavailable, with Reformuler button. |

### A.4 Dashboards (`/tableau-de-bord`)

| Symbol | Name | Notes |
|--------|------|-------|
| ◇ | DashboardPage | KPI strip + main charts grid + secondary charts + recent activity table. |
| ▣ | KpiStrip | 4-up grid of KpiCards. |
| ▣ | KpiCard | Tremor `Card` + `Metric` + `BadgeDelta`. Sparkline via Tremor. Tabular-nums. |
| ▣ | RevenueLineChart | Recharts `LineChart`, 24 months, forecast region shaded. Custom tooltip. |
| ▣ | CategoryBarList | Tremor `BarList`, top 8 categories, sortable. |
| ▣ | StockRiskHeatmap | ECharts heatmap, SKU × week. State-warning → state-danger ramp. |
| ▣ | RecentActivityTable | shadcn Table, 10 rows, StatusPill column, sortable. |
| ▣ | ChartCard | Card wrapper used by every chart: title (text-h2), optional toolbar (date range), 1px border, no shadow. |
| ▣ | ChartLoadingSkeleton | Skeleton in the shape of the chart (line vs bar vs heatmap). Three variants. |
| ▣ | ChartEmptyState | EmptyState specialized for "no data for this metric". |

### A.5 Forecasts (`/previsions`) — secondary view

| Symbol | Name | Notes |
|--------|------|-------|
| ◇ | ForecastsPage | Header (model version + trained-at) + RevenueForecastChart + StockRiskTable. |
| ▣ | RevenueForecastChart | Recharts area chart with 80% / 95% confidence intervals shaded. |
| ▣ | StockRiskTable | shadcn Table, sortable by stockout probability and expected date. |
| ▣ | ModelMetadataBadge | Mono text: `prophet-v1.3 · entraîné 12 avr. 2026`. |

### A.6 History (`/historique`)

| Symbol | Name | Notes |
|--------|------|-------|
| ◇ | HistoryPage | SearchInput + grouped ConversationList (read-only variant). |
| ▣ | HistoryDateGroup | `<h2>` with date label + ConversationList. |
| ▣ | HistoryConversationItem | Title, timestamp, message count, click → read-only Conversation view. |

### A.7 Settings (`/reglages`)

| Symbol | Name | Notes |
|--------|------|-------|
| ◇ | SettingsPage | Tabs: Apparence, Préférences, Compte. |
| ▣ | AppearanceSection | ThemeToggle (light/dark/system), DensityToggle (comfortable/compact). |
| ▣ | PreferencesSection | Language Select (French only enabled, Arabic shown disabled with "Bientôt disponible"). |
| ▣ | AccountSection | Display info, Change-password placeholder button (stubbed). |
| ▣ | ThemeToggle | Three-state segmented control. |
| ▣ | DensityToggle | Two-state segmented control. |

### A.8 Admin / Debug (`/admin`) — role-gated

| Symbol | Name | Notes |
|--------|------|-------|
| ◇ | AdminPage | Filters bar + AuditLogTable. |
| ▣ | AuditLogFilters | User Select, date range, success/failure toggle. |
| ▣ | AuditLogTable | Paginated table: timestamp, user, question, model, status, latency, row count. Click row → AuditLogDetail. |
| ▣ | AuditLogDetail | Sheet showing: question (FR), retrieved tables, generated SQL, executed SQL, raw rows preview, latency breakdown bar. |
| ▣ | LatencyBreakdownBar | Stacked horizontal bar: retrieve / generate / execute / narrate, in mono ms. |
| ▣ | RoleGuard | Wrapper that checks role context; renders `null` or 403 EmptyState if denied. |

### A.9 Authentication (`/connexion`)

| Symbol | Name | Notes |
|--------|------|-------|
| ◇ | LoginPage | Standalone responsive split layout: product context on desktop, focused form on mobile. |
| ▣ | LoginCard | Brand wordmark + sign-in/sign-up tabs + accessible form + password recovery guidance. |
| ▣ | LoginForm | Typed React form. Professional identifier + password, confirmation/strength on sign-up, French validation, secure backend integration. |

### A.10 Error & system pages

| Symbol | Name | Notes |
|--------|------|-------|
| ◇ | NotFoundPage | Centered `404` + « Cette page n'existe pas. » + Retour à l'accueil. |
| ◇ | ServerErrorPage | Centered `500` + « Une erreur inattendue est survenue. » + Retour à l'accueil. |
| ▣ | RootErrorBoundary | Layout-level error boundary; catches render errors and renders ServerErrorPage shell. |
| ▣ | ConnectionLostBanner | Sticky top banner under TopBar when `navigator.onLine` is false or backend unreachable. |

### A.11 Loading states (skeleton variants)

Every primary surface has a named skeleton. No spinner is ever the first paint.

| Skeleton | Used by |
|----------|---------|
| `ConversationSkeleton` | First load of `/conversation` before history fetch. |
| `AnswerSkeleton` | Between submit and first streaming token. 3 lines + 3 chips. |
| `KpiStripSkeleton` | DashboardPage initial load — 4 card-shaped skeletons. |
| `LineChartSkeleton` | RevenueLineChart loading. |
| `BarListSkeleton` | CategoryBarList loading. |
| `HeatmapSkeleton` | StockRiskHeatmap loading. |
| `TableSkeleton` | Any data table loading. Configurable row count. |
| `HistorySkeleton` | HistoryPage initial load. |

### A.12 Empty states (one per surface)

| Empty state | Surface | Copy |
|-------------|---------|------|
| ConversationEmptyState | `/conversation` (no current convo) | « Posez votre première question. » + 4 example cards |
| HistoryEmptyState | `/historique` (no past convos) | « Aucune conversation pour l'instant. » + CTA `Nouvelle conversation` |
| DashboardNoDataState | `/tableau-de-bord` (no snapshot) | « Aucun snapshot importé. » |
| ForecastUnavailableState | `/previsions` (insufficient data) | « Prévision non disponible. Données historiques insuffisantes. » |
| AdminNoLogsState | `/admin` (no entries match filter) | « Aucune entrée correspondant aux filtres. » |
| ChartEmptyState | any chart | « Aucune donnée pour cette période. » |

### A.13 Mock layer (Phase 2/4)

Not components, but inventoried here because the design depends on them.

| Module | Purpose |
|--------|---------|
| `src/lib/mocks/qa.ts` | `mockQa({question, language})` returning realistic streaming responses with delays. Switch via `VITE_USE_MOCKS`. |
| `src/lib/mocks/dashboard.ts` | 24 months of CA with rentrée scolaire seasonality, 8 categories, stock-risk SKU × week grid, recent activity. |
| `src/lib/mocks/history.ts` | 20 past conversations across the 5 date groups. |
| `src/lib/mocks/audit.ts` | 100 audit log rows with realistic latency distribution and a 5% failure rate. |

---

## Appendix B — Decisions deferred / open questions

Items intentionally not decided in Phase 1, to be confirmed before the relevant Phase begins.

| # | Decision | Defer until | Default if not decided |
|---|----------|-------------|------------------------|
| B1 | Syntax highlighter library | Phase 4 (Conversation SQL panel) | Use **shiki** — smaller bundle, CSS-variable theming, dark-first. (Recorded as the working default; revisit if bundle target is missed.) |
| B2 | Prose markdown renderer for AnswerBody | Phase 4 | `react-markdown` + `remark-gfm` if needed; otherwise hand-roll a tiny renderer for **bold**, lists, code spans. Decide once we see real model output shape. |
| B3 | Chart numeric tick formatter | Phase 5 | Hand-roll over `Intl.NumberFormat('fr-FR')` with a `compact` mode for million/thousand abbreviations (`1,2 M`). |
| B4 | Whether `/previsions` is a separate page or a tab on `/tableau-de-bord` | Phase 5 | Separate page. Reverts to a tab if Phase 5 feels thin. |
| B5 | Exact OKLCH values for state ramps in light theme | Phase 2 (token rollout) | Values in §5.2 are the working defaults; verify contrast on real surfaces in dev. |
| B6 | Use of `cva` (class-variance-authority) vs hand-written variant maps | Phase 2 | `cva` for ≥3-variant components (Button, Badge, Chip), hand-written conditionals for single-variant components. |

---

## Self-review against the brief's bar

> "Would a designer at Linear approve this document?"

- ✅ Token count is restrained: 7 type tokens, 3 radii, 4 motion durations, ~12 semantic colors. Nothing decorative.
- ✅ OKLCH used for perceptual uniformity, with a low-chroma neutral ramp (chroma ≤ 0.008) and a single capped-chroma accent.
- ✅ Borders carry hierarchy; shadows reserved for true overlays. Stated as a rule.
- ✅ Motion has four timings with explicit easing curves and `prefers-reduced-motion` policy at the system level, not per-component.
- ✅ French copy guidelines include canonical strings (15 of them), formatting locale, and a glossary, not just a tone description.
- ✅ Every screen has a named skeleton, empty state, and error state inventoried.
- ✅ Anti-patterns enumerated specifically (purple gradients, glassmorphism, rounded-3xl, cursor:pointer everywhere) so review can cite them.
- ✅ Component inventory enumerates 110+ components/screens with their compositional source (shadcn ✦ vs LPN-built ▣ vs page ◇), and lists the mocks the design depends on.
- ✅ Density modes documented with concrete pixel values per affected component.
- ✅ Accessibility is a section, not a polish-pass deferral.

Anything decorative for decoration's sake — found and removed in this draft? Two candidates I considered and **cut**:

- A `--shadow-card-rest` / `--shadow-card-hover` token pair. Cut: cards never have shadow per §8.
- A "tertiary" button variant. Cut: ghost covers it.

Document ready for Phase 2.
