package com.zettais.db2db.web.dto;

/**
 * Modern Java Record representing a connection test response.
 */
public record ConnectionTestResponse(
        boolean sourceOk,
        String sourceMessage,
        boolean targetOk,
        String targetMessage
) {}
