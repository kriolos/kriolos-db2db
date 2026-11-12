package com.zettais.db2db.core.workflow;

/**
 * Listener interface for reacting to real-time workflow lifecycle events.
 */
public interface WorkflowEventListener {

    void onWorkflowStarted(String migrationId, WorkflowConfig config);

    void onStepStatusChanged(MigrationStepId stepId, StepStatus oldStatus, StepStatus newStatus, String message);

    void onStepLog(MigrationStepId stepId, String logLine);

    void onWorkflowCompleted(WorkflowExecutionReport report);

    void onWorkflowFailed(String migrationId, String error);
}
