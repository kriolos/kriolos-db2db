package org.db2db.web.dto;

/**
 * Modern Java Record representing a schema assessment request.
 */
public record AssessmentRequest(
        String sourceDsn,
        String sourceUser,
        String sourcePassword,
        String schema
) {}
