package com.zettais.db2db.core.workflow;

/**
 * Execution mode for the DB2DB migration workflow.
 */
public enum WorkflowMode {
    /**
     * Automatic / Unattended mode: Runs all non-skipped steps consecutively without pausing.
     */
    AUTOMATIC,

    /**
     * Interactive mode: Pauses before executing each step, waiting for manual user approval or skip.
     */
    INTERACTIVE
}
