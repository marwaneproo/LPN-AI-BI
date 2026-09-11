package com.lpn.aibi.llmorchestrator;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

record SemanticSqlValidation(
        boolean valid,
        @JsonProperty("repair_required") boolean repairRequired,
        List<String> issues,
        List<String> warnings) {

    static SemanticSqlValidation ok(List<String> warnings) {
        return new SemanticSqlValidation(true, false, List.of(), warnings == null ? List.of() : warnings);
    }
}
