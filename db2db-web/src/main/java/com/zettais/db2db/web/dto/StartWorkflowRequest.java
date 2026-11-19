package com.zettais.db2db.web.dto;

import java.util.List;

public record StartWorkflowRequest(
        String sourceDsn,
        String sourceUser,
        String sourcePassword,
        String sourceSchema,
        String targetDsn,
        String targetUser,
        String targetPassword,
        String targetSchema,
        String mode,
        List<String> skippedSteps,
        Boolean stopOnError,
        Integer parallelWorkers
) {}
