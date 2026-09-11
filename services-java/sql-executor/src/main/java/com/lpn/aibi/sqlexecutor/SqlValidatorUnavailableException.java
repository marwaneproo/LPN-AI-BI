package com.lpn.aibi.sqlexecutor;

class SqlValidatorUnavailableException extends RuntimeException {

    SqlValidatorUnavailableException(String message) {
        super(message);
    }

    SqlValidatorUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
