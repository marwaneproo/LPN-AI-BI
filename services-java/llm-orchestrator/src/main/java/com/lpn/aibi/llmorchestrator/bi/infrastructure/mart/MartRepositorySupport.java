package com.lpn.aibi.llmorchestrator.bi.infrastructure.mart;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.lpn.aibi.llmorchestrator.bi.application.BiQuery;

abstract class MartRepositorySupport {

    protected static final BigDecimal ZERO = BigDecimal.ZERO;
    protected final NamedParameterJdbcTemplate jdbc;

    MartRepositorySupport(@Qualifier("biReadonlyJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    protected MapSqlParameterSource params(BiQuery query) {
        return new MapSqlParameterSource()
                .addValue("from", Date.valueOf(query.fromInclusive()), Types.DATE)
                .addValue("to", Date.valueOf(query.toExclusive()), Types.DATE)
                .addValue("granularity", query.granularity(), Types.VARCHAR)
                .addValue("commercial", query.commercial(), Types.INTEGER)
                .addValue("category", query.category(), Types.INTEGER)
                .addValue("supplier", query.supplier(), Types.INTEGER)
                .addValue("customer", query.customer(), Types.INTEGER)
                .addValue("documentType", query.documentType(), Types.INTEGER)
                .addValue("region", query.region(), Types.VARCHAR)
                .addValue("city", query.city(), Types.VARCHAR)
                .addValue("paymentStatus", query.paymentStatus(), Types.VARCHAR)
                .addValue("limit", query.limit(), Types.INTEGER)
                .addValue("offset", query.offset(), Types.INTEGER);
    }

    /**
     * The same parameters as {@link #params(BiQuery)} with the window shifted one
     * year back — used by the {@code compare=true} previous-period trend overlay.
     */
    protected MapSqlParameterSource previousPeriodParams(BiQuery query) {
        return params(query)
                .addValue("from", Date.valueOf(query.fromInclusive().minusYears(1)), Types.DATE)
                .addValue("to", Date.valueOf(query.toExclusive().minusYears(1)), Types.DATE);
    }

    /**
     * Parameters for the year-to-date comparison cards: current window =
     * Jan 1 → today (exclusive of tomorrow), previous window = the same span one
     * year back. Deliberately independent of the page's selected date range so
     * the cards stay a fixed "this year so far vs last year at the same point"
     * reading.
     */
    protected static MapSqlParameterSource yearToDateParams(LocalDate today) {
        LocalDate from = today.withDayOfYear(1);
        LocalDate toExclusive = today.plusDays(1);
        return new MapSqlParameterSource()
                .addValue("from", Date.valueOf(from), Types.DATE)
                .addValue("to", Date.valueOf(toExclusive), Types.DATE)
                .addValue("prevFrom", Date.valueOf(from.minusYears(1)), Types.DATE)
                .addValue("prevTo", Date.valueOf(toExclusive.minusYears(1)), Types.DATE);
    }

    protected static BigDecimal money(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    /** numerator / denominator as a percentage rounded to 2 decimals; 0 when the denominator is 0. */
    protected static BigDecimal ratioPercent(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return ZERO;
        }
        return numerator.multiply(BigDecimal.valueOf(100))
                .divide(denominator, 2, java.math.RoundingMode.HALF_UP);
    }
}
