package com.zettais.db2db.core.workflow;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable audit record of a single migration step's execution.
 */
public record StepExecutionRecord(
        MigrationStepId stepId,
        String title,
        StepStatus status,
        long startTimeEpochMs,
        long endTimeEpochMs,
        long durationMs,
        List<String> logs,
        Map<String, Object> metrics,
        String errorMessage
) implements Serializable {

    public StepExecutionRecord {
        logs = logs != null ? List.copyOf(logs) : Collections.emptyList();
        metrics = metrics != null ? Map.copyOf(metrics) : Collections.emptyMap();
        if (durationMs <= 0 && endTimeEpochMs > startTimeEpochMs && startTimeEpochMs > 0) {
            durationMs = endTimeEpochMs - startTimeEpochMs;
        }
    }

    public static StepExecutionRecord initial(MigrationStepId stepId) {
        return new StepExecutionRecord(
                stepId,
                stepId.getDisplayName(),
                StepStatus.PENDING,
                0, 0, 0,
                List.of(),
                Map.of(),
                null
        );
    }

    public StepExecutionRecord withStatus(StepStatus newStatus) {
        return new StepExecutionRecord(
                stepId, title, newStatus, startTimeEpochMs, endTimeEpochMs, durationMs, logs, metrics, errorMessage
        );
    }
}
