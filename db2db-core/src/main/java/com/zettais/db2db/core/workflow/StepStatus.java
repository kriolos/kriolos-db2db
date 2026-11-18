package com.zettais.db2db.core.workflow;

/**
 * Status lifecycle of a migration workflow step.
 */
public enum StepStatus {
    PENDING,
    WAITING_APPROVAL,
    RUNNING,
    SUCCESS,
    SKIPPED,
    FAILED
}
