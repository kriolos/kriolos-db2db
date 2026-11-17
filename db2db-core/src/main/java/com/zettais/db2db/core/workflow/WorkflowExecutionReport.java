package com.zettais.db2db.core.workflow;

import com.zettais.db2db.core.i18n.Language;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Complete executive audit report of an end-to-end migration execution.
 */
public record WorkflowExecutionReport(
        String migrationId,
        String sourceDatabase,
        String targetDatabase,
        String sourceSchema,
        String targetSchema,
        WorkflowMode mode,
        long startedAtEpochMs,
        long completedAtEpochMs,
        long totalDurationMs,
        boolean overallSuccess,
        List<StepExecutionRecord> stepRecords,
        Map<String, Object> summaryMetrics,
        Language language
) implements Serializable {

    public WorkflowExecutionReport {
        stepRecords = stepRecords != null ? List.copyOf(stepRecords) : Collections.emptyList();
        summaryMetrics = summaryMetrics != null ? Map.copyOf(summaryMetrics) : Collections.emptyMap();
        if (totalDurationMs <= 0 && completedAtEpochMs > startedAtEpochMs && startedAtEpochMs > 0) {
            totalDurationMs = completedAtEpochMs - startedAtEpochMs;
        }
        language = language != null ? language : Language.EN;
    }

    public WorkflowExecutionReport(
            String migrationId, String sourceDatabase, String targetDatabase,
            String sourceSchema, String targetSchema, WorkflowMode mode,
            long startedAtEpochMs, long completedAtEpochMs, long totalDurationMs,
            boolean overallSuccess, List<StepExecutionRecord> stepRecords,
            Map<String, Object> summaryMetrics
    ) {
        this(migrationId, sourceDatabase, targetDatabase, sourceSchema, targetSchema,
                mode, startedAtEpochMs, completedAtEpochMs, totalDurationMs,
                overallSuccess, stepRecords, summaryMetrics, Language.EN);
    }

    public long getSuccessCount() {
        return stepRecords.stream().filter(s -> s.status() == StepStatus.SUCCESS).count();
    }

    public long getSkippedCount() {
        return stepRecords.stream().filter(s -> s.status() == StepStatus.SKIPPED).count();
    }

    public long getFailedCount() {
        return stepRecords.stream().filter(s -> s.status() == StepStatus.FAILED).count();
    }
}
