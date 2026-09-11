package com.lpn.aibi.llmorchestrator;

import java.util.List;

class SqlExecutionException extends RuntimeException {

    private final int statusCode;
    private final String code;
    private final List<String> forbiddenConstructs;
    private final List<String> errors;

    SqlExecutionException(int statusCode, String code, String message, List<String> forbiddenConstructs, List<String> errors) {
        super(message);
        this.statusCode = statusCode;
        this.code = code;
        this.forbiddenConstructs = forbiddenConstructs == null ? List.of() : List.copyOf(forbiddenConstructs);
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    int statusCode() {
        return statusCode;
    }

    String code() {
        return code;
    }

    List<String> forbiddenConstructs() {
        return forbiddenConstructs;
    }

    List<String> errors() {
        return errors;
    }
}
