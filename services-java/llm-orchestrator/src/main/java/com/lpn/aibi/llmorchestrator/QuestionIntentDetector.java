package com.lpn.aibi.llmorchestrator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
class QuestionIntentDetector {

    private static final Pattern DATE = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");
    private static final Pattern COUNT = Pattern.compile("(?iU)\\b(how many|combien|count|number of|nombre de|volume)\\b");
    private static final Pattern SUM = Pattern.compile(
            "(?iU)\\b(total|value|amount|sum|valeur|montant|chiffre d'affaires|chiffre affaires|ca)\\b");
    private static final Pattern AVG = Pattern.compile("(?iU)\\b(avg|average|moyenne|moyen)\\b");
    private static final Pattern TOP = Pattern.compile(
            "(?iU)\\b(top|highest|largest|best|plus haut|meilleurs?|premiers?|principaux?|principales?|classement|ten|cinq)\\b"
                    + "|\\ble plus\\b|\\bla plus\\b|\\bles plus\\b|\\ble mieux\\b|\\bla mieux\\b|\\bles mieux\\b"
                    + "|\\b(?:which|quels?|quelles?)\\s+\\d+\\b|\\b\\d+\\s+(?:clients?|customers?|products?|produits?|articles?)\\b");
    private static final Pattern COMPARE = Pattern.compile("(?iU)\\b(compare|vs|versus|difference|différence|higher|lower|comparer|par rapport)\\b");
    private static final Pattern STATUS = Pattern.compile(
            "(?iU)\\b(status|statut|docstatus|paid|unpaid|payées?|payees?|impayées?|impayees?|completed|closed|complétées?|completees?|clôturées?|cloturees?)\\b");
    private static final Pattern STOCK = Pattern.compile(
            "(?iU)\\b(stock|rupture|available|availability|disponible|disponibilité|reserved|reserve|risques?|risks?|inventaires?|inventory)\\b");
    private static final Pattern CUSTOMER = Pattern.compile("(?iU)\\b(customers?|clients?|partners?|tiers|bpartner|acheteurs?)\\b");
    private static final Pattern PRODUCT = Pattern.compile("(?iU)\\b(products?|produits?|articles?|références?|references?|sku)\\b");
    private static final Pattern INVOICE = Pattern.compile("(?iU)\\b(invoices?|invoice|invoiced|billed|factures?|facture|facturé|facturee|facturée|facturation)\\b");
    private static final Pattern ORDER = Pattern.compile("(?iU)\\b(orders?|order value|order total|commandes?|commande|ordres?)\\b");
    private static final Pattern SALES_METRIC = Pattern.compile(
            "(?iU)\\b(sales|revenue|turnover|totalsales|total sales|chiffre d'affaires|chiffre affaires|ca|ventes?)\\b");
    private static final Pattern LAST_MONTH = Pattern.compile(
            "(?iU)\\b(last month|past month|previous month|prior month|mois dernier|dernier mois|mois passe|mois passé|précédent|precedent)\\b");
    private static final Pattern RELATIVE_MONTH_RANGE = Pattern.compile(
            "(?iU)\\b(last|past|previous|prior)\\s+\\d+\\s+months?\\b|\\b\\d+\\s+derniers?\\s+mois\\b");

    // --- Temporal expressions matched against the accent-stripped, typo-corrected
    // normalized text (see QuestionNormalizer) so "aujourd'hui", "aujourdhui" and a
    // minor typo of either all resolve the same way. ---
    private static final Pattern TODAY = Pattern.compile("\\b(aujourdhui|today)\\b");
    private static final Pattern YESTERDAY = Pattern.compile("\\b(hier|yesterday)\\b");
    private static final Pattern THIS_WEEK = Pattern.compile("\\b(cette semaine|this week|semaine en cours)\\b");
    private static final Pattern LAST_WEEK = Pattern.compile(
            "\\b(semaine derniere|derniere semaine|semaine passee|last week|past week|previous week)\\b");
    private static final Pattern THIS_MONTH = Pattern.compile("\\b(ce mois|this month|mois en cours|mois actuel)\\b");

    // --- Broader natural-language signals: not tied to one domain, but to HOW the
    // answer should be shaped (trend over time, grouped breakdown, or an open-ended
    // summary). Matched against the normalized/typo-corrected canonical text so
    // "evoluent"/"évoluent"/typos of either still resolve. These are prompt hints
    // for the LLM fallback, not deterministic templates — the shape of a trend or a
    // free-form summary genuinely needs the model, not a fixed query. ---
    private static final Pattern TREND = Pattern.compile(
            "\\b(evolue\\w*|evolution|tendances?|trends?|progression|au fil du temps|dans le temps)\\b");
    private static final Pattern BREAKDOWN = Pattern.compile(
            "\\bpar (categorie|produit|client|fournisseur|mois|region|statut)\\b"
                    + "|\\bby (category|product|customer|supplier|month|region|status)\\b"
                    + "|\\b(repartition|ventilation|breakdown)\\b");
    private static final Pattern SUMMARY = Pattern.compile(
            "\\b(resume\\w*|summary|summarize|bilan|situation|apercu)\\b|\\bvue d ensemble\\b|\\boverview\\b");

    // --- Business domains the platform reports on. Matched against the same
    // normalized text, so synonyms/typos/abbreviations (e.g. "fourn" -> "fournisseur",
    // "kpi" -> "kpi indicateur") are recognized under one canonical domain tag. ---
    private static final Pattern DOMAIN_SUPPLIER = Pattern.compile(
            "\\b(fournisseurs?|fournit|fournissent|suppliers?|vendors?)\\b");
    private static final Pattern DOMAIN_PURCHASING = Pattern.compile("\\b(achats?|purchas\\w*)\\b");
    private static final Pattern DOMAIN_SALES = Pattern.compile("\\b(ventes?|sales|commercial\\w*)\\b");
    private static final Pattern DOMAIN_REVENUE = Pattern.compile("\\b(chiffre affaires|revenue|turnover)\\b");
    private static final Pattern DOMAIN_PRODUCT = Pattern.compile("\\b(produits?|articles?|products?|references?|sku)\\b");
    private static final Pattern DOMAIN_CUSTOMER = Pattern.compile("\\b(clients?|customers?|partners?|acheteurs?)\\b");
    private static final Pattern DOMAIN_ORDER = Pattern.compile("\\b(commandes?|orders?|ordres?)\\b");
    private static final Pattern DOMAIN_FORECAST = Pattern.compile(
            "\\b(previsions?|prevoir|forecasts?|forecasting|tendances?|trends?)\\b");
    private static final Pattern DOMAIN_ANALYTICS = Pattern.compile(
            "\\b(kpi\\w*|indicateurs?|analyses?|statistiques?|stats)\\b");

    QuestionIntent detect(String question) {
        String text = question == null ? "" : question.trim();
        String normalized = text.toLowerCase(Locale.ROOT);
        String canonical = QuestionNormalizer.normalize(text);
        List<String> intents = new ArrayList<>();

        addIf(intents, "COUNT", COUNT.matcher(text).find());
        addIf(intents, "SUM_TOTAL", SUM.matcher(text).find());
        addIf(intents, "AVERAGE", AVG.matcher(text).find());
        addIf(intents, "TOP_N", TOP.matcher(text).find());
        addIf(intents, "COMPARISON", COMPARE.matcher(text).find() || mentionedDates(text).size() >= 2);
        addIf(intents, "STATUS_BREAKDOWN", STATUS.matcher(text).find());
        addIf(intents, "STOCK_RISK", STOCK.matcher(text).find());
        addIf(intents, "CUSTOMER_RANKING", CUSTOMER.matcher(text).find() && TOP.matcher(text).find());
        addIf(intents, "PRODUCT_RANKING", PRODUCT.matcher(text).find() && TOP.matcher(text).find());
        boolean explicitLastMonth = LAST_MONTH.matcher(text).find();
        boolean relativeMonthRange = RELATIVE_MONTH_RANGE.matcher(text).find();
        boolean explicitToday = TODAY.matcher(canonical).find();
        boolean explicitYesterday = YESTERDAY.matcher(canonical).find();
        boolean explicitThisWeek = THIS_WEEK.matcher(canonical).find();
        boolean explicitLastWeek = LAST_WEEK.matcher(canonical).find();
        boolean explicitThisMonth = THIS_MONTH.matcher(canonical).find();
        boolean customerRanking = CUSTOMER.matcher(text).find() && TOP.matcher(text).find();
        boolean productRanking = PRODUCT.matcher(text).find() && TOP.matcher(text).find();
        boolean supplierRanking = DOMAIN_SUPPLIER.matcher(canonical).find() && TOP.matcher(canonical).find();
        addIf(intents, "SUPPLIER_RANKING", supplierRanking);
        boolean trendQuestion = TREND.matcher(canonical).find();
        addIf(intents, "TREND", trendQuestion);
        Matcher breakdownMatcher = BREAKDOWN.matcher(canonical);
        boolean breakdownQuestion = false;
        String breakdownDimension = null;
        while (breakdownMatcher.find()) {
            breakdownQuestion = true;
            String dimension = breakdownDimension(breakdownMatcher);
            if (dimension != null) {
                breakdownDimension = dimension;
                break;
            }
        }
        addIf(intents, "BREAKDOWN", breakdownQuestion);
        boolean summaryQuestion = SUMMARY.matcher(canonical).find();
        addIf(intents, "SUMMARY", summaryQuestion);
        boolean invoiceMetric = INVOICE.matcher(text).find();
        boolean salesMetric = SALES_METRIC.matcher(text).find();
        boolean orderMetric = ORDER.matcher(text).find();
        addIf(intents, "DATE_FILTER",
                DATE.matcher(text).find() || explicitLastMonth || relativeMonthRange || explicitToday
                        || explicitYesterday || explicitThisWeek || explicitLastWeek || explicitThisMonth);
        addIf(intents, "SALES_METRIC", salesMetric);
        addIf(intents, "INVOICE_METRIC", invoiceMetric);
        addIf(intents, "ORDER_METRIC", orderMetric);

        List<String> domains = detectDomains(canonical);
        for (String domain : domains) {
            addIf(intents, "DOMAIN_" + domain, true);
        }

        boolean explicitCompletedOnly = normalized.contains("completed")
                || normalized.contains("complete")
                || normalized.contains("complét")
                || normalized.contains("clotur")
                || normalized.contains("clôtur")
                || normalized.contains("status co")
                || normalized.contains("docstatus co");
        boolean explicitPaidUnpaid = normalized.contains("paid")
                || normalized.contains("unpaid")
                || normalized.contains("payé")
                || normalized.contains("payee")
                || normalized.contains("payées")
                || normalized.contains("impay");
        boolean explicitAllData = normalized.contains("all ")
                || normalized.contains("toutes")
                || normalized.contains("tous")
                || normalized.contains("global")
                || normalized.contains("overall");

        return new QuestionIntent(
                List.copyOf(intents),
                mentionedDates(text),
                explicitLastMonth,
                explicitCompletedOnly,
                explicitPaidUnpaid,
                explicitAllData,
                intents.contains("COMPARISON"),
                intents.contains("TOP_N") || intents.contains("CUSTOMER_RANKING") || intents.contains("PRODUCT_RANKING")
                        || supplierRanking,
                customerRanking,
                productRanking,
                supplierRanking,
                salesMetric,
                invoiceMetric,
                orderMetric,
                relativeMonthRange,
                intents.contains("COUNT") || intents.contains("SUM_TOTAL") || intents.contains("AVERAGE"),
                explicitToday,
                explicitYesterday,
                explicitThisWeek,
                explicitLastWeek,
                explicitThisMonth,
                domains,
                canonical,
                trendQuestion,
                breakdownQuestion,
                breakdownDimension,
                summaryQuestion);
    }

    private static List<String> detectDomains(String canonical) {
        Set<String> domains = new LinkedHashSet<>();
        addDomainIf(domains, "FOURNISSEURS", DOMAIN_SUPPLIER.matcher(canonical).find());
        addDomainIf(domains, "ACHATS", DOMAIN_PURCHASING.matcher(canonical).find());
        addDomainIf(domains, "VENTES", DOMAIN_SALES.matcher(canonical).find());
        addDomainIf(domains, "CHIFFRE_AFFAIRES", DOMAIN_REVENUE.matcher(canonical).find());
        addDomainIf(domains, "PRODUITS", DOMAIN_PRODUCT.matcher(canonical).find());
        addDomainIf(domains, "CLIENTS", DOMAIN_CUSTOMER.matcher(canonical).find());
        addDomainIf(domains, "COMMANDES", DOMAIN_ORDER.matcher(canonical).find());
        addDomainIf(domains, "PREVISIONS", DOMAIN_FORECAST.matcher(canonical).find());
        addDomainIf(domains, "ANALYSES_KPI", DOMAIN_ANALYTICS.matcher(canonical).find());
        return List.copyOf(domains);
    }

    private static String breakdownDimension(Matcher matcher) {
        String french = matcher.group(1);
        String english = matcher.group(2);
        String raw = french != null ? french : english;
        if (raw == null) {
            return null;
        }
        return switch (raw) {
            case "categorie", "category" -> "CATEGORY";
            case "produit", "product" -> "PRODUCT";
            case "client", "customer" -> "CUSTOMER";
            case "fournisseur", "supplier" -> "SUPPLIER";
            case "mois", "month" -> "MONTH";
            case "region" -> "REGION";
            case "statut", "status" -> "STATUS";
            default -> null;
        };
    }

    private static void addDomainIf(Set<String> domains, String domain, boolean condition) {
        if (condition) {
            domains.add(domain);
        }
    }

    private static void addIf(List<String> intents, String intent, boolean condition) {
        if (condition && !intents.contains(intent)) {
            intents.add(intent);
        }
    }

    private static List<String> mentionedDates(String text) {
        List<String> dates = new ArrayList<>();
        Matcher matcher = DATE.matcher(text == null ? "" : text);
        while (matcher.find()) {
            dates.add(matcher.group());
        }
        return List.copyOf(dates);
    }
}
