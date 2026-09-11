package com.lpn.aibi.sqlexecutor;

class UnsafeSqlException extends RuntimeException {

    private final SqlValidationResult validation;

    UnsafeSqlException(SqlValidationResult validation) {
        super("SQL validation rejected this query.");
        this.validation = validation;
    }

    SqlValidationResult validation() {
        return validation;
    }
}
