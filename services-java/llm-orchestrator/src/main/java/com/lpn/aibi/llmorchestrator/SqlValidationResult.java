package com.lpn.aibi.llmorchestrator;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

record SqlValidationResult(
        @JsonProperty("is_valid") boolean valid,
        @JsonProperty("is_safe") boolean safe,
        @JsonProperty("statement_count") int statementCount,
        @JsonProperty("first_statement_kind") String firstStatementKind,
        @JsonProperty("tables_referenced") List<String> tablesReferenced,
        @JsonProperty("forbidden_constructs") List<String> forbiddenConstructs,
        List<String> warnings,
        @JsonProperty("rewritten_sql") String rewrittenSql,
        List<String> errors) {
}
