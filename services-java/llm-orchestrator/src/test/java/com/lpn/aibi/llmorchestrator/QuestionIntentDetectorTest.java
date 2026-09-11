package com.lpn.aibi.llmorchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuestionIntentDetectorTest {

    private final QuestionIntentDetector detector = new QuestionIntentDetector();

    @Test
    void recognizesSynonymousPhrasingsAsTheSameIntent() {
        QuestionIntent byCombien = detector.detect("Combien de clients avons-nous ?");
        QuestionIntent byHowMany = detector.detect("How many customers do we have?");

        assertThat(byCombien.hasIntent("COUNT")).isTrue();
        assertThat(byHowMany.hasIntent("COUNT")).isTrue();
        assertThat(byCombien.hasDomain("CLIENTS")).isTrue();
        assertThat(byHowMany.hasDomain("CLIENTS")).isTrue();
    }

    @Test
    void toleratesCommonTypos() {
        QuestionIntent typo = detector.detect("Quel est le chifre d'affaire des fournisseurss ce mois ?");

        assertThat(typo.hasDomain("FOURNISSEURS")).isTrue();
        assertThat(typo.explicitThisMonth()).isTrue();
    }

    @Test
    void expandsAbbreviations() {
        QuestionIntent ca = detector.detect("Quel est le CA du mois ?");
        QuestionIntent kpi = detector.detect("Montre-moi les KPI achats");

        assertThat(ca.salesMetricQuestion()).isTrue();
        assertThat(kpi.hasDomain("ANALYSES_KPI")).isTrue();
        assertThat(kpi.hasDomain("ACHATS")).isTrue();
    }

    @Test
    void recognizesTemporalExpressionsInFrenchAndEnglish() {
        assertThat(detector.detect("Ventes d'aujourd'hui").explicitToday()).isTrue();
        assertThat(detector.detect("Sales today").explicitToday()).isTrue();
        assertThat(detector.detect("Commandes d'hier").explicitYesterday()).isTrue();
        assertThat(detector.detect("Orders yesterday").explicitYesterday()).isTrue();
        assertThat(detector.detect("Ventes de cette semaine").explicitThisWeek()).isTrue();
        assertThat(detector.detect("Sales this week").explicitThisWeek()).isTrue();
        assertThat(detector.detect("CA de la semaine dernière").explicitLastWeek()).isTrue();
        assertThat(detector.detect("Revenue last week").explicitLastWeek()).isTrue();
        assertThat(detector.detect("Achats de ce mois").explicitThisMonth()).isTrue();
        assertThat(detector.detect("Purchases this month").explicitThisMonth()).isTrue();
        assertThat(detector.detect("Ventes du mois dernier").explicitLastMonth()).isTrue();
    }

    @Test
    void recognizesBusinessDomains() {
        assertThat(detector.detect("Top 10 fournisseurs par CA").hasDomain("FOURNISSEURS")).isTrue();
        assertThat(detector.detect("Répartition des achats par catégorie").hasDomain("ACHATS")).isTrue();
        assertThat(detector.detect("Evolution des ventes").hasDomain("VENTES")).isTrue();
        assertThat(detector.detect("Produits les plus vendus").hasDomain("PRODUITS")).isTrue();
        assertThat(detector.detect("Nombre de commandes").hasDomain("COMMANDES")).isTrue();
        assertThat(detector.detect("Prévisions de ventes pour le trimestre").hasDomain("PREVISIONS")).isTrue();
        assertThat(detector.detect("Statistiques et analyses globales").hasDomain("ANALYSES_KPI")).isTrue();
    }

    @Test
    void recognizesSupplierRankingAcrossAllRequiredFormulations() {
        assertThat(detector.detect("Top fournisseurs").supplierRankingQuestion()).isTrue();
        assertThat(detector.detect("Meilleurs fournisseurs").supplierRankingQuestion()).isTrue();
        assertThat(detector.detect("Classement fournisseurs").supplierRankingQuestion()).isTrue();
        assertThat(detector.detect("Fournisseurs principaux").supplierRankingQuestion()).isTrue();
        assertThat(detector.detect("Fournisseurs les plus actifs").supplierRankingQuestion()).isTrue();
        assertThat(detector.detect("Quels sont nos meilleurs fournisseurs ?").supplierRankingQuestion()).isTrue();
        assertThat(detector.detect("Qui fournit le plus de produits ?").supplierRankingQuestion()).isTrue();
        assertThat(detector.detect("Quels fournisseurs travaillent le plus avec nous ?").supplierRankingQuestion())
                .isTrue();
    }

    @Test
    void recognizesOpenEndedNaturalLanguageQuestions() {
        QuestionIntent evolution = detector.detect("Comment evoluent les ventes ?");
        assertThat(evolution.trendQuestion()).isTrue();
        assertThat(evolution.hasDomain("VENTES")).isTrue();

        QuestionIntent bestArticles = detector.detect("Quels articles performent le mieux ?");
        assertThat(bestArticles.productRankingQuestion()).isTrue();

        QuestionIntent risks = detector.detect("Quels sont les risques actuels ?");
        assertThat(risks.hasIntent("STOCK_RISK")).isTrue();

        QuestionIntent compareMonths = detector.detect("Compare ce mois avec le précédent.");
        assertThat(compareMonths.comparisonQuestion()).isTrue();
        assertThat(compareMonths.explicitThisMonth()).isTrue();
        assertThat(compareMonths.explicitLastMonth()).isTrue();

        QuestionIntent summary = detector.detect("Résume la situation commerciale.");
        assertThat(summary.summaryQuestion()).isTrue();
        assertThat(summary.hasDomain("VENTES")).isTrue();

        QuestionIntent byCategory = detector.detect("Quel est le chiffre d'affaires par catégorie ?");
        assertThat(byCategory.breakdownQuestion()).isTrue();
        assertThat(byCategory.breakdownDimension()).isEqualTo("CATEGORY");
        assertThat(byCategory.salesMetricQuestion()).isTrue();

        QuestionIntent volume = detector.detect("Quel est le volume de commandes du mois dernier ?");
        assertThat(volume.hasIntent("COUNT")).isTrue();
        assertThat(volume.explicitLastMonth()).isTrue();

        // "Répartition" alone matches the generic breakdown keyword before the regex
        // engine even reaches "par catégorie" later in the string — the dimension
        // extraction must keep scanning rather than stop at the first (dimension-less) match.
        QuestionIntent repartitionParCategorie = detector.detect("Répartition par catégorie");
        assertThat(repartitionParCategorie.breakdownQuestion()).isTrue();
        assertThat(repartitionParCategorie.breakdownDimension()).isEqualTo("CATEGORY");
    }

    @Test
    void sameIntentAcrossDifferentFormulationsOfTheSameQuestion() {
        QuestionIntent variantA = detector.detect("Quel est le chiffre d'affaires du mois dernier ?");
        QuestionIntent variantB = detector.detect("Quelle est la valeur des ventes le mois passé ?");

        assertThat(variantA.salesMetricQuestion()).isTrue();
        assertThat(variantB.salesMetricQuestion()).isTrue();
        assertThat(variantA.explicitLastMonth()).isTrue();
        assertThat(variantB.explicitLastMonth()).isTrue();
    }
}
