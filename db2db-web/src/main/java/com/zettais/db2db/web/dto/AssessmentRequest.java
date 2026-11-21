package com.zettais.db2db.web.dto;

/**
 * Modern Java Record representing a schema assessment and DDL export request.
 */
public record AssessmentRequest(
        String sourceDsn,
        String sourceUser,
        String sourcePassword,
        String schema,
        String targetDialect
) {
    public AssessmentRequest(String sourceDsn, String sourceUser, String sourcePassword, String schema) {
        this(sourceDsn, sourceUser, sourcePassword, schema, "POSTGRESQL");
    }
}
