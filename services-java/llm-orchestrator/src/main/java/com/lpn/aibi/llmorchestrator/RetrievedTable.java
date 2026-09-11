package com.lpn.aibi.llmorchestrator;

import com.fasterxml.jackson.annotation.JsonProperty;

record RetrievedTable(
        @JsonProperty("table_name") String tableName,
        String module,
        @JsonProperty("description_en") String descriptionEn,
        @JsonProperty("description_fr") String descriptionFr,
        @JsonProperty("key_columns") String keyColumns,
        String relations,
        String notes,
        double score,
        @JsonProperty("expanded_from") String expandedFrom) {
}
