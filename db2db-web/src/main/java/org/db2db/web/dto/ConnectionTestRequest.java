package org.db2db.web.dto;

/**
 * Modern Java Record representing a connection test request.
 */
public record ConnectionTestRequest(
        String sourceDsn,
        String sourceUser,
        String sourcePassword,
        String targetDsn,
        String targetUser,
        String targetPassword
) {}
