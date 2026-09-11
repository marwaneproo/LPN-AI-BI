package com.lpn.aibi.sqlexecutor;

import java.sql.SQLTimeoutException;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SqlExecutionController {

    private final SqlExecutionService sqlExecutionService;

    SqlExecutionController(SqlExecutionService sqlExecutionService) {
        this.sqlExecutionService = sqlExecutionService;
    }

    @PostMapping("/v1/execute-sql")
    SqlExecutionResult executeSql(@RequestBody ExecuteSqlRequest request) {
        return sqlExecutionService.execute(request.sql());
    }

    @ExceptionHandler(UnsafeSqlException.class)
    ResponseEntity<SqlExecutionError> unsafeSql(UnsafeSqlException exception) {
        SqlValidationResult validation = exception.validation();
        return ResponseEntity.unprocessableEntity().body(new SqlExecutionError(
                "UNSAFE_SQL",
                "SQL validation rejected this query.",
                validation == null ? List.of() : validation.forbiddenConstructs(),
                validation == null ? List.of() : validation.errors()));
    }

    @ExceptionHandler(QueryTimeoutException.class)
    ResponseEntity<SqlExecutionError> queryTimeout(QueryTimeoutException exception) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(new SqlExecutionError(
                "SQL_TIMEOUT",
                "SQL execution exceeded the configured timeout.",
                List.of(),
                List.of(rootMessage(exception))));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<SqlExecutionError> dataAccess(DataAccessException exception) {
        HttpStatus status = hasTimeoutCause(exception) ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.INTERNAL_SERVER_ERROR;
        String code = status == HttpStatus.GATEWAY_TIMEOUT ? "SQL_TIMEOUT" : "SQL_EXECUTION_ERROR";
        String message = status == HttpStatus.GATEWAY_TIMEOUT
                ? "SQL execution exceeded the configured timeout."
                : "SQL execution failed.";
        return ResponseEntity.status(status).body(new SqlExecutionError(
                code,
                message,
                List.of(),
                List.of(rootMessage(exception))));
    }

    @ExceptionHandler(SqlValidatorUnavailableException.class)
    ResponseEntity<SqlExecutionError> validatorUnavailable(SqlValidatorUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new SqlExecutionError(
                "SQL_VALIDATOR_UNAVAILABLE",
                "SQL validator could not be reached.",
                List.of(),
                List.of(rootMessage(exception))));
    }

    record ExecuteSqlRequest(String sql) {
    }

    record SqlExecutionError(
            String code,
            String message,
            @JsonProperty("forbidden_constructs")
            List<String> forbiddenConstructs,
            List<String> errors) {
    }

    private static boolean hasTimeoutCause(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLTimeoutException) {
                return true;
            }
            if (current instanceof PSQLException psqlException
                    && "57014".equals(psqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
