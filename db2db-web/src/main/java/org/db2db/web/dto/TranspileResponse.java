package org.db2db.web.dto;

/**
 * Modern Java Record representing a transpilation response.
 */
public record TranspileResponse(
        String originalSql,
        String transpiledSql,
        long executionTimeMs
) {}
