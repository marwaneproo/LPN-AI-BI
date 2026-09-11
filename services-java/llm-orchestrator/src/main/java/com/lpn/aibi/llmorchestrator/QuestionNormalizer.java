package com.lpn.aibi.llmorchestrator;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Normalizes a raw user question so that different phrasings of the same
 * business request — synonyms, common typos, abbreviations, accents — line
 * up before {@link QuestionIntentDetector} runs its pattern matching.
 *
 * <p>The raw question is never discarded: date literals, exact wording, and
 * anything downstream that needs the user's original text still reads from
 * it. {@link #normalize(String)} produces an <em>additional</em>, accent-
 * stripped / typo-corrected / abbreviation-expanded string that
 * {@link QuestionIntentDetector} matches domain and temporal patterns
 * against, so "fournisseur", "fournisseurr" (typo) and "fourn" (abbreviation)
 * all resolve to the same signal.
 *
 * <p>Deliberately dependency-free: the business vocabulary this pipeline
 * needs to understand is small and fixed, so a plain Levenshtein distance is
 * enough and keeps this service self-contained (no fuzzy-matching library).
 */
final class QuestionNormalizer {

    /**
     * Canonical, accent-stripped business vocabulary. A word in the question
     * within edit-distance 1 (2 for words of 8+ letters) of one of these is
     * corrected to the canonical form before domain/temporal matching runs.
     * Words under 4 letters are never fuzzy-corrected — too ambiguous (e.g.
     * "ca" vs "ce") — those are handled by the exact ABBREVIATIONS map only.
     */
    private static final List<String> VOCABULARY = List.of(
            "fournisseur", "fournisseurs", "achat", "achats", "vente", "ventes",
            "produit", "produits", "client", "clients", "commande", "commandes",
            "chiffre", "affaires", "prevision", "previsions", "analyse", "analyses",
            "statistique", "statistiques", "facture", "factures", "stock",
            "categorie", "categories", "hier", "aujourdhui", "semaine", "dernier",
            "derniere", "derniers", "dernieres", "mois", "passee", "passe", "annee",
            "moyenne", "total", "totaux", "combien", "meilleur", "meilleurs",
            "disponible", "disponibilite", "quantite", "prevoir", "forecast",
            "supplier", "suppliers", "customer", "customers", "product", "products",
            "order", "orders", "yesterday", "today", "week", "month",
            "classement", "principal", "principaux", "principale", "principales",
            "tendance", "tendances", "trend", "trends", "comparaison", "comparaisons",
            "resume", "bilan", "situation", "apercu", "overview", "summary",
            "commercial", "commerciale", "commerciaux", "region", "statut", "status",
            "repartition", "ventilation", "breakdown", "volume", "risque", "risques",
            "evolue", "evolution", "progression",
            "acheteur", "acheteurs", "reference", "references", "ordre", "ordres",
            "inventaire", "inventory", "vendor", "vendors");

    /** Exact-match abbreviations, expanded to their full canonical phrase. */
    private static final Map<String, String> ABBREVIATIONS = Map.ofEntries(
            Map.entry("ca", "chiffre affaires"),
            Map.entry("kpi", "kpi indicateur"),
            Map.entry("kpis", "kpi indicateur"),
            Map.entry("qte", "quantite"),
            Map.entry("qty", "quantite"),
            Map.entry("ht", "hors taxe"),
            Map.entry("ttc", "toutes taxes comprises"),
            Map.entry("maj", "mise a jour"),
            Map.entry("cmd", "commande"),
            Map.entry("cmds", "commandes"),
            Map.entry("fourn", "fournisseur"),
            Map.entry("prod", "produit"),
            Map.entry("stats", "statistiques"),
            Map.entry("tva", "taxe valeur ajoutee"),
            Map.entry("id", "identifiant"),
            Map.entry("sku", "reference produit"));

    private static final Pattern APOSTROPHE = Pattern.compile("['’]");
    private static final Pattern NON_LETTER = Pattern.compile("[^\\p{L}\\s]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private QuestionNormalizer() {
    }

    static String normalize(String question) {
        if (question == null || question.isBlank()) {
            return "";
        }
        String stripped = stripAccents(question.toLowerCase(Locale.ROOT));
        // Elisions (d'affaires, l'année, qu'est) must lose the apostrophe WITHOUT
        // splitting into two words, or "chiffre d'affaires" becomes the three
        // tokens "chiffre", "d", "affaires" and phrase patterns never match.
        // The stray leading consonant this leaves on the following word (e.g.
        // "daffaires") is then close enough in edit distance for correctTypo to
        // fold back onto its vocabulary form ("affaires").
        String withoutApostrophes = APOSTROPHE.matcher(stripped).replaceAll("");
        String cleaned = NON_LETTER.matcher(withoutApostrophes).replaceAll(" ").trim();
        if (cleaned.isEmpty()) {
            return "";
        }
        String[] words = WHITESPACE.split(cleaned);
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            String resolved = ABBREVIATIONS.getOrDefault(word, correctTypo(word));
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(resolved);
        }
        return result.toString();
    }

    private static String correctTypo(String word) {
        if (word.length() < 4 || VOCABULARY.contains(word)) {
            return word;
        }
        int allowedDistance = word.length() >= 8 ? 2 : 1;
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : VOCABULARY) {
            if (Math.abs(candidate.length() - word.length()) > allowedDistance) {
                continue;
            }
            int distance = levenshtein(word, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best != null && bestDistance <= allowedDistance ? best : word;
    }

    private static String stripAccents(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
