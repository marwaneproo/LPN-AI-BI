package com.lpn.aibi.llmorchestrator;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
class SemanticSqlValidator {

    private static final Pattern DOCSTATUS_COMPLETED = Pattern.compile("(?is)\\bdocstatus\\s*=\\s*'?CO'?");
    private static final Pattern LAST_MONTH_FILTER = Pattern.compile("(?is)date_trunc\\s*\\(\\s*'month'\\s*,\\s*current_date\\s*\\)\\s*-\\s*interval\\s*'1 month'");
    private static final Pattern CURRENT_MONTH_UPPER_BOUND = Pattern.compile("(?is)<\\s*date_trunc\\s*\\(\\s*'month'\\s*,\\s*current_date\\s*\\)");
    private static final Pattern LIMIT_ONE = Pattern.compile("(?is)\\blimit\\s+1\\b");
    private static final Pattern ORDER_BY_SUM = Pattern.compile("(?is)order\\s+by\\s+sum\\s*\\(");
    private static final Pattern SUM_SELECT = Pattern.compile("(?is)select\\s+.*sum\\s*\\(");
    private static final Pattern C_BPARTNER_TABLE = Pattern.compile("(?is)\\bc_bpartner\\b");
    private static final Pattern C_BPARTNER_NAME = Pattern.compile("(?is)\\bname\\b|customer_name|client_name|businesspartnername");
    private static final Pattern C_ORDER_TABLE = Pattern.compile("(?is)\\bc_order\\b");
    private static final Pattern C_INVOICE_TABLE = Pattern.compile("(?is)\\bc_invoice\\b");
    private static final Pattern C_ORDER_DATEINVOICED = Pattern.compile("(?is)\\bc_order\\s+\\w+.*\\b\\w+\\.dateinvoiced\\b|\\bc_order\\.dateinvoiced\\b");
    private static final Pattern C_ORDERLINE_GRANDTOTAL = Pattern.compile("(?is)\\bc_orderline\\s+\\w+.*\\b\\w+\\.grandtotal\\b|\\bc_orderline\\.grandtotal\\b");
    private static final Pattern C_PAYMENT = Pattern.compile("(?is)\\bc_payment\\b");
    private static final Pattern C_INVOICE_ISPAID = Pattern.compile("(?is)\\bc_invoice\\b.*\\bispaid\\b|\\bispaid\\b.*\\bc_invoice\\b");
    private static final Pattern TOTAL_LINES = Pattern.compile("(?is)\\btotallines\\b");
    private static final Pattern NON_POSTGRES_DATE_FUNCTION = Pattern.compile("(?is)\\b(dateadd|getdate|date_sub|strftime)\\s*\\(");
    private static final Pattern ORDER_BY_DESC = Pattern.compile("(?is)\\border\\s+by\\b.+\\bdesc\\b");
    private static final Pattern LIMIT_N = Pattern.compile("(?is)\\blimit\\s+\\d+\\b");

    SemanticSqlValidation validateBeforeExecution(String question, String sql, QuestionIntent intent) {
        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String normalizedSql = sql == null ? "" : sql.toLowerCase(Locale.ROOT);

        if (NON_POSTGRES_DATE_FUNCTION.matcher(normalizedSql).find()) {
            issues.add("SQL uses non-PostgreSQL date functions; use date_trunc, CURRENT_DATE, and INTERVAL.");
        }

        if (DOCSTATUS_COMPLETED.matcher(normalizedSql).find() && !intent.explicitCompletedOnly()) {
            issues.add("SQL filters DOCSTATUS = 'CO' but the user did not ask for completed orders only.");
        }

        if (LAST_MONTH_FILTER.matcher(normalizedSql).find() && !intent.explicitLastMonth() && !intent.relativeMonthRange()) {
            issues.add("SQL adds a last-month date filter but the user did not ask for last month.");
        }

        if (intent.explicitLastMonth() && !LAST_MONTH_FILTER.matcher(normalizedSql).find()) {
            issues.add("SQL misses the requested last-month date filter.");
        }

        if ((intent.explicitLastMonth() || intent.relativeMonthRange())
                && normalizedSql.contains("date_trunc('month', current_date)")
                && !CURRENT_MONTH_UPPER_BOUND.matcher(normalizedSql).find()) {
            issues.add("Relative month filters must be bounded with an exclusive current-month upper bound.");
        }

        if (intent.comparisonQuestion() && LIMIT_ONE.matcher(normalizedSql).find()) {
            issues.add("Comparison questions must return every compared item/date; LIMIT 1 would hide the losing side.");
        }

        if (intent.rankingQuestion() && ORDER_BY_SUM.matcher(normalizedSql).find() && !SUM_SELECT.matcher(normalizedSql).find()) {
            issues.add("SQL ranks by SUM(...) but does not expose the total value metric in SELECT.");
        }

        if (intent.customerRankingQuestion() && !C_BPARTNER_TABLE.matcher(normalizedSql).find()) {
            issues.add("Customer ranking questions should join C_BPARTNER to expose customer names, not only IDs.");
        }

        if (intent.customerRankingQuestion()
                && C_BPARTNER_TABLE.matcher(normalizedSql).find()
                && !C_BPARTNER_NAME.matcher(normalizedSql).find()) {
            issues.add("Customer ranking SQL should expose the customer name.");
        }

        if (intent.customerRankingQuestion()
                && !intent.salesMetricQuestion()
                && !intent.invoiceMetricQuestion()
                && C_INVOICE_TABLE.matcher(normalizedSql).find()) {
            issues.add("Generic top-client questions should rank customers by order value from C_ORDER unless sales or invoices are explicitly requested.");
        }

        if ((intent.salesMetricQuestion() || intent.invoiceMetricQuestion())
                && !intent.orderMetricQuestion()
                && C_ORDER_TABLE.matcher(normalizedSql).find()
                && !C_INVOICE_TABLE.matcher(normalizedSql).find()) {
            issues.add("Sales/revenue customer questions should use C_INVOICE.GRANDTOTAL unless the user explicitly asks for order value.");
        }

        if (intent.orderMetricQuestion() && TOTAL_LINES.matcher(normalizedSql).find()) {
            issues.add("Order value questions should use C_ORDER.GRANDTOTAL, not C_ORDER.TOTALLINES.");
        }

        if (C_ORDER_DATEINVOICED.matcher(normalizedSql).find()) {
            issues.add("Order date filters should use C_ORDER.DATEORDERED, not C_ORDER.DATEINVOICED.");
        }

        if (C_ORDERLINE_GRANDTOTAL.matcher(normalizedSql).find()
                || (intent.productRankingQuestion() && normalizedSql.contains("c_orderline") && normalizedSql.contains(".grandtotal"))) {
            issues.add("Order line value should use C_ORDERLINE.LINENETAMT, not C_ORDERLINE.GRANDTOTAL.");
        }

        if (intent.productRankingQuestion() && (!ORDER_BY_DESC.matcher(normalizedSql).find() || !LIMIT_N.matcher(normalizedSql).find())) {
            issues.add("Product ranking questions must order by the ranking metric descending and apply the requested LIMIT.");
        }

        if (intent.supplierRankingQuestion() && (!ORDER_BY_DESC.matcher(normalizedSql).find() || !LIMIT_N.matcher(normalizedSql).find())) {
            issues.add("Supplier ranking questions must order by the ranking metric descending and apply the requested LIMIT.");
        }

        if (intent.explicitPaidUnpaid()
                && normalizedSql.contains("invoice")
                && C_PAYMENT.matcher(normalizedSql).find()
                && !C_INVOICE_ISPAID.matcher(normalizedSql).find()
                && !mentionsPaymentRecords(question)) {
            issues.add("Paid/unpaid invoice questions should use C_INVOICE.ISPAID unless the user explicitly asks for payment records.");
        }

        if (intent.comparisonQuestion() && intent.mentionedDates().size() >= 2) {
            for (String date : intent.mentionedDates()) {
                if (!normalizedSql.contains(date)) {
                    issues.add("Comparison SQL does not include requested date " + date + ".");
                }
            }
        }

        if (LIMIT_ONE.matcher(normalizedSql).find()) {
            warnings.add("SQL returns only the top row; narration must not claim other rows do not exist.");
        }
        if (intent.aggregateQuestion()) {
            warnings.add("SQL result is an aggregate; narration must describe a total/count/average, not a single business object.");
        }

        return new SemanticSqlValidation(issues.isEmpty(), !issues.isEmpty(), List.copyOf(issues), List.copyOf(warnings));
    }

    ResultAssessment assessResult(String sql, List<java.util.Map<String, Object>> rows, QuestionIntent intent) {
        List<String> warnings = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        boolean incomplete = false;
        int rowCount = rows == null ? 0 : rows.size();

        if (intent.comparisonQuestion() && intent.mentionedDates().size() >= 2 && rowCount < intent.mentionedDates().size()) {
            incomplete = true;
            issues.add("The comparison requested " + intent.mentionedDates().size()
                    + " dates/items but SQL returned only " + rowCount + " row(s).");
        }
        if (LIMIT_ONE.matcher(sql == null ? "" : sql).find()) {
            warnings.add("Result was limited to one row; do not say it is the only existing row.");
        }
        if (intent.aggregateQuestion() && rowCount == 1) {
            warnings.add("One row represents one aggregate result, not one order/invoice/customer.");
        }

        return new ResultAssessment(incomplete, List.copyOf(issues), List.copyOf(warnings));
    }

    private static boolean mentionsPaymentRecords(String question) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        return normalized.contains("payment")
                || normalized.contains("paiement")
                || normalized.contains("allocation")
                || normalized.contains("lettrage");
    }

    record ResultAssessment(
            @JsonProperty("incomplete_result") boolean incompleteResult,
            List<String> issues,
            List<String> warnings) {
    }
}
