package com.lpn.aibi.llmorchestrator;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

record QuestionIntent(
        List<String> intents,
        @JsonProperty("mentioned_dates") List<String> mentionedDates,
        @JsonProperty("explicit_last_month") boolean explicitLastMonth,
        @JsonProperty("explicit_completed_only") boolean explicitCompletedOnly,
        @JsonProperty("explicit_paid_unpaid") boolean explicitPaidUnpaid,
        @JsonProperty("explicit_all_data") boolean explicitAllData,
        @JsonProperty("comparison_question") boolean comparisonQuestion,
        @JsonProperty("ranking_question") boolean rankingQuestion,
        @JsonProperty("customer_ranking_question") boolean customerRankingQuestion,
        @JsonProperty("product_ranking_question") boolean productRankingQuestion,
        @JsonProperty("supplier_ranking_question") boolean supplierRankingQuestion,
        @JsonProperty("sales_metric_question") boolean salesMetricQuestion,
        @JsonProperty("invoice_metric_question") boolean invoiceMetricQuestion,
        @JsonProperty("order_metric_question") boolean orderMetricQuestion,
        @JsonProperty("relative_month_range") boolean relativeMonthRange,
        @JsonProperty("aggregate_question") boolean aggregateQuestion,
        @JsonProperty("explicit_today") boolean explicitToday,
        @JsonProperty("explicit_yesterday") boolean explicitYesterday,
        @JsonProperty("explicit_this_week") boolean explicitThisWeek,
        @JsonProperty("explicit_last_week") boolean explicitLastWeek,
        @JsonProperty("explicit_this_month") boolean explicitThisMonth,
        @JsonProperty("domains") List<String> domains,
        @JsonProperty("normalized_question") String normalizedQuestion,
        @JsonProperty("trend_question") boolean trendQuestion,
        @JsonProperty("breakdown_question") boolean breakdownQuestion,
        @JsonProperty("breakdown_dimension") String breakdownDimension,
        @JsonProperty("summary_question") boolean summaryQuestion) {

    boolean hasIntent(String intent) {
        return intents != null && intents.contains(intent);
    }

    boolean hasDomain(String domain) {
        return domains != null && domains.contains(domain);
    }

    String promptBlock() {
        return """
                Detected intent hints:
                - intents: %s
                - domains: %s
                - normalized_question: %s
                - mentioned_dates: %s
                - explicit_last_month: %s
                - explicit_today: %s
                - explicit_yesterday: %s
                - explicit_this_week: %s
                - explicit_last_week: %s
                - explicit_this_month: %s
                - explicit_completed_only: %s
                - explicit_paid_unpaid: %s
                - explicit_all_data: %s
                - comparison_question: %s
                - ranking_question: %s
                - customer_ranking_question: %s
                - product_ranking_question: %s
                - supplier_ranking_question: %s
                - sales_metric_question: %s
                - invoice_metric_question: %s
                - order_metric_question: %s
                - relative_month_range: %s
                - aggregate_question: %s
                - trend_question: %s (if true: return a time series grouped by period, ordered chronologically, not a single total)
                - breakdown_question: %s, breakdown_dimension: %s (if breakdown_question is true: GROUP BY the given dimension and return one row per value, not one aggregate row)
                - summary_question: %s (if true and no other specific intent is set: the user wants an open-ended overview; return a small multi-metric result, e.g. counts and totals across the domains listed above, rather than refusing)
                """.formatted(
                intents,
                domains,
                normalizedQuestion,
                mentionedDates,
                explicitLastMonth,
                explicitToday,
                explicitYesterday,
                explicitThisWeek,
                explicitLastWeek,
                explicitThisMonth,
                explicitCompletedOnly,
                explicitPaidUnpaid,
                explicitAllData,
                comparisonQuestion,
                rankingQuestion,
                customerRankingQuestion,
                productRankingQuestion,
                supplierRankingQuestion,
                salesMetricQuestion,
                invoiceMetricQuestion,
                orderMetricQuestion,
                relativeMonthRange,
                aggregateQuestion,
                trendQuestion,
                breakdownQuestion,
                breakdownDimension,
                summaryQuestion).trim();
    }
}
