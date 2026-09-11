package com.lpn.aibi.llmorchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SemanticSqlValidatorTest {

    private final QuestionIntentDetector intentDetector = new QuestionIntentDetector();
    private final SemanticSqlValidator validator = new SemanticSqlValidator();

    @Test
    void rejectsCompletedFilterWhenUserAskedAllOrders() {
        QuestionIntent intent = intentDetector.detect("What is the total order value and average order value?");

        SemanticSqlValidation validation = validator.validateBeforeExecution(
                "What is the total order value and average order value?",
                "SELECT SUM(GRANDTOTAL), AVG(GRANDTOTAL) FROM C_ORDER WHERE DOCSTATUS = 'CO'",
                intent);

        assertThat(validation.valid()).isFalse();
        assertThat(validation.repairRequired()).isTrue();
        assertThat(validation.issues()).anyMatch(issue -> issue.contains("DOCSTATUS"));
    }

    @Test
    void rejectsLimitOneForDateComparison() {
        QuestionIntent intent = intentDetector.detect(
                "Compare 2026-04-21 vs 2026-04-24: which day had higher order value?");

        SemanticSqlValidation validation = validator.validateBeforeExecution(
                "Compare 2026-04-21 vs 2026-04-24: which day had higher order value?",
                """
                SELECT CAST(DATEORDERED AS DATE) AS order_date, SUM(GRANDTOTAL) AS total_value
                FROM C_ORDER
                WHERE CAST(DATEORDERED AS DATE) IN ('2026-04-21', '2026-04-24')
                GROUP BY CAST(DATEORDERED AS DATE)
                ORDER BY total_value DESC
                LIMIT 1
                """,
                intent);

        assertThat(validation.valid()).isFalse();
        assertThat(validation.issues()).anyMatch(issue -> issue.contains("LIMIT 1"));
    }

    @Test
    void acceptsExplicitCompletedOrderQuestion() {
        QuestionIntent intent = intentDetector.detect("What is the value of completed orders?");

        SemanticSqlValidation validation = validator.validateBeforeExecution(
                "What is the value of completed orders?",
                "SELECT SUM(GRANDTOTAL) FROM C_ORDER WHERE DOCSTATUS = 'CO'",
                intent);

        assertThat(validation.valid()).isTrue();
    }

    @Test
    void treatsPastMonthAsExplicitLastMonth() {
        QuestionIntent intent = intentDetector.detect("Top 10 clients in the past month?");

        SemanticSqlValidation validation = validator.validateBeforeExecution(
                "Top 10 clients in the past month?",
                """
                SELECT bp.NAME, COUNT(*) AS order_count, SUM(o.GRANDTOTAL) AS total_order_value
                FROM C_ORDER o
                JOIN C_BPARTNER bp ON bp.C_BPARTNER_ID = o.C_BPARTNER_ID
                WHERE o.DATEORDERED >= date_trunc('month', CURRENT_DATE) - INTERVAL '1 month'
                  AND o.DATEORDERED < date_trunc('month', CURRENT_DATE)
                GROUP BY bp.NAME
                ORDER BY total_order_value DESC
                LIMIT 10
                """,
                intent);

        assertThat(intent.explicitLastMonth()).isTrue();
        assertThat(validation.valid()).isTrue();
    }

    @Test
    void rejectsMissingRequestedLastMonthFilter() {
        QuestionIntent intent = intentDetector.detect("Top 10 clients in the past month?");

        SemanticSqlValidation validation = validator.validateBeforeExecution(
                "Top 10 clients in the past month?",
                "SELECT C_BPARTNER_ID, SUM(GRANDTOTAL) FROM C_ORDER GROUP BY C_BPARTNER_ID ORDER BY SUM(GRANDTOTAL) DESC LIMIT 10",
                intent);

        assertThat(validation.valid()).isFalse();
        assertThat(validation.issues()).anyMatch(issue -> issue.contains("last-month"));
    }

    @Test
    void rejectsCustomerRankingWithoutCustomerNameJoin() {
        QuestionIntent intent = intentDetector.detect("Top 10 clients in the past month?");

        SemanticSqlValidation validation = validator.validateBeforeExecution(
                "Top 10 clients in the past month?",
                """
                SELECT C_BPARTNER_ID, SUM(GRANDTOTAL) AS total_order_value
                FROM C_ORDER
                WHERE DATEORDERED >= date_trunc('month', CURRENT_DATE) - INTERVAL '1 month'
                GROUP BY C_BPARTNER_ID
                ORDER BY total_order_value DESC
                LIMIT 10
                """,
                intent);

        assertThat(validation.valid()).isFalse();
        assertThat(validation.issues()).anyMatch(issue -> issue.contains("C_BPARTNER"));
    }

    @Test
    void rejectsGenericTopClientRankingFromInvoicesWhenSalesWasNotRequested() {
        QuestionIntent intent = intentDetector.detect("Top 6 clients in the past month?");

        SemanticSqlValidation validation = validator.validateBeforeExecution(
                "Top 6 clients in the past month?",
                """
                SELECT bp.NAME, COUNT(i.C_INVOICE_ID) AS invoice_count, SUM(i.GRANDTOTAL) AS total_sales
                FROM C_BPARTNER bp
                JOIN C_INVOICE i ON bp.C_BPARTNER_ID = i.C_BPARTNER_ID
                WHERE i.DATEINVOICED >= date_trunc('month', CURRENT_DATE) - INTERVAL '1 month'
                  AND i.DATEINVOICED < date_trunc('month', CURRENT_DATE)
                GROUP BY bp.NAME
                ORDER BY total_sales DESC
                LIMIT 6
                """,
                intent);

        assertThat(validation.valid()).isFalse();
        assertThat(validation.issues()).anyMatch(issue -> issue.contains("Generic top-client"));
    }

    @Test
    void rejectsSalesQuestionFromOrdersWhenInvoicesWereRequestedByMetric() {
        QuestionIntent intent = intentDetector.detect(
                "compare the client L'AVENIR DU LIVRE SIEL 2026 past 2 month totalsales");

        SemanticSqlValidation validation = validator.validateBeforeExecution(
                "compare the client L'AVENIR DU LIVRE SIEL 2026 past 2 month totalsales",
                """
                SELECT DATE_TRUNC('MONTH', DATEORDERED) AS month, SUM(TOTALLINES) AS total_sales
                FROM C_ORDER
                JOIN C_BPARTNER ON C_ORDER.C_BPARTNER_ID = C_BPARTNER.C_BPARTNER_ID
                WHERE C_BPARTNER.NAME = 'L''AVENIR DU LIVRE SIEL 2026'
                  AND DATEORDERED >= DATE_TRUNC('MONTH', CURRENT_DATE) - INTERVAL '2 MONTHS'
                GROUP BY month
                ORDER BY month
                """,
                intent);

        assertThat(validation.valid()).isFalse();
        assertThat(validation.issues()).anyMatch(issue -> issue.contains("Sales/revenue"));
        assertThat(validation.issues()).anyMatch(issue -> issue.contains("upper bound"));
    }

    @Test
    void rejectsNonPostgresDateFunctions() {
        QuestionIntent intent = intentDetector.detect(
                "Which 10 products generated the highest order value in the past month?");

        SemanticSqlValidation validation = validator.validateBeforeExecution(
                "Which 10 products generated the highest order value in the past month?",
                """
                SELECT p.NAME, SUM(ol.LINENETAMT) AS total_order_value
                FROM C_ORDERLINE ol
                JOIN M_PRODUCT p ON p.M_PRODUCT_ID = ol.M_PRODUCT_ID
                WHERE ol.CREATED >= DATEADD(month, -1, GETDATE())
                GROUP BY p.NAME
                ORDER BY total_order_value DESC
                LIMIT 10
                """,
                intent);

        assertThat(validation.valid()).isFalse();
        assertThat(validation.issues()).anyMatch(issue -> issue.contains("non-PostgreSQL"));
    }

    @Test
    void rejectsBadProductRankingShape() {
        QuestionIntent intent = intentDetector.detect(
                "Which 10 products generated the highest order value in the past month?");

        SemanticSqlValidation validation = validator.validateBeforeExecution(
                "Which 10 products generated the highest order value in the past month?",
                """
                SELECT p.NAME, SUM(ol.GRANDTOTAL) AS total_order_value
                FROM C_ORDER o
                JOIN C_ORDERLINE ol ON ol.C_ORDER_ID = o.C_ORDER_ID
                JOIN M_PRODUCT p ON p.M_PRODUCT_ID = ol.M_PRODUCT_ID
                WHERE o.DATEORDERED >= date_trunc('month', CURRENT_DATE) - INTERVAL '1 month'
                  AND o.DATEORDERED < date_trunc('month', CURRENT_DATE)
                GROUP BY p.NAME
                LIMIT 1000
                """,
                intent);

        assertThat(validation.valid()).isFalse();
        assertThat(validation.issues()).anyMatch(issue -> issue.contains("LINENETAMT"));
        assertThat(validation.issues()).anyMatch(issue -> issue.contains("Product ranking"));
    }

    @Test
    void rejectsOrderDateInvoicedForOrderComparison() {
        QuestionIntent intent = intentDetector.detect(
                "Compare total order value and total invoiced sales for the past month.");

        SemanticSqlValidation validation = validator.validateBeforeExecution(
                "Compare total order value and total invoiced sales for the past month.",
                """
                WITH LastMonthOrders AS (
                    SELECT SUM(o.GRANDTOTAL) AS total_order_value
                    FROM C_ORDER o
                    WHERE o.DATEINVOICED >= DATE_TRUNC('month', CURRENT_DATE) - INTERVAL '1 month'
                      AND o.DATEINVOICED < DATE_TRUNC('month', CURRENT_DATE)
                )
                SELECT * FROM LastMonthOrders
                """,
                intent);

        assertThat(validation.valid()).isFalse();
        assertThat(validation.issues()).anyMatch(issue -> issue.contains("DATEORDERED"));
    }

    @Test
    void flagsIncompleteComparisonResultAfterExecution() {
        QuestionIntent intent = intentDetector.detect(
                "Compare 2026-04-21 vs 2026-04-24: which day had higher order value?");

        SemanticSqlValidator.ResultAssessment assessment = validator.assessResult(
                "SELECT * FROM C_ORDER LIMIT 1",
                java.util.List.of(java.util.Map.of("order_date", "2026-04-21", "total_value", 703883.76)),
                intent);

        assertThat(assessment.incompleteResult()).isTrue();
        assertThat(assessment.issues()).isNotEmpty();
    }
}
