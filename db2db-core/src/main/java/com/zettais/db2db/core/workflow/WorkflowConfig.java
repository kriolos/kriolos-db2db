package com.zettais.db2db.core.workflow;

import com.zettais.db2db.core.i18n.Language;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Immutable configuration record for a DB2DB migration workflow execution.
 */
public record WorkflowConfig(
        String sourceDsn,
        String sourceUser,
        String sourcePassword,
        String sourceSchema,
        String targetDsn,
        String targetUser,
        String targetPassword,
        String targetSchema,
        WorkflowMode mode,
        Set<MigrationStepId> skippedSteps,
        boolean stopOnError,
        int parallelWorkers,
        String outputDir,
        Language language
) implements Serializable {

    public WorkflowConfig {
        sourceDsn = sourceDsn != null ? sourceDsn : "";
        sourceUser = sourceUser != null ? sourceUser : "";
        sourcePassword = sourcePassword != null ? sourcePassword : "";
        sourceSchema = sourceSchema != null && !sourceSchema.isBlank() ? sourceSchema : "SYSTEM";
        targetDsn = targetDsn != null ? targetDsn : "jdbc:postgresql://localhost:5432/postgres";
        targetUser = targetUser != null ? targetUser : "postgres";
        targetPassword = targetPassword != null ? targetPassword : "";
        targetSchema = targetSchema != null && !targetSchema.isBlank() ? targetSchema : "public";
        mode = mode != null ? mode : WorkflowMode.AUTOMATIC;
        skippedSteps = skippedSteps != null ? Set.copyOf(skippedSteps) : Collections.emptySet();
        if (parallelWorkers <= 0) parallelWorkers = 4;
        outputDir = outputDir != null && !outputDir.isBlank() ? outputDir : "./reports";
        language = language != null ? language : Language.EN;
    }

    public WorkflowConfig(
            String sourceDsn, String sourceUser, String sourcePassword, String sourceSchema,
            String targetDsn, String targetUser, String targetPassword, String targetSchema,
            WorkflowMode mode, Set<MigrationStepId> skippedSteps, boolean stopOnError,
            int parallelWorkers, String outputDir
    ) {
        this(sourceDsn, sourceUser, sourcePassword, sourceSchema,
                targetDsn, targetUser, targetPassword, targetSchema,
                mode, skippedSteps, stopOnError, parallelWorkers, outputDir, Language.EN);
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isStepSkipped(MigrationStepId stepId) {
        return skippedSteps.contains(stepId);
    }

    public static final class Builder {
        private String sourceDsn = "jdbc:oracle:thin:@localhost:1521/XEPDB1";
        private String sourceUser = "OSADMIN";
        private String sourcePassword = "";
        private String sourceSchema = "OSADMIN";
        private String targetDsn = "jdbc:postgresql://localhost:5432/postgres";
        private String targetUser = "postgres";
        private String targetPassword = "";
        private String targetSchema = "public";
        private WorkflowMode mode = WorkflowMode.AUTOMATIC;
        private final Set<MigrationStepId> skippedSteps = new HashSet<>();
        private boolean stopOnError = true;
        private int parallelWorkers = 4;
        private String outputDir = "./reports";
        private Language language = Language.EN;

        public Builder sourceDsn(String sourceDsn) { this.sourceDsn = sourceDsn; return this; }
        public Builder sourceUser(String sourceUser) { this.sourceUser = sourceUser; return this; }
        public Builder sourcePassword(String sourcePassword) { this.sourcePassword = sourcePassword; return this; }
        public Builder sourceSchema(String sourceSchema) { this.sourceSchema = sourceSchema; return this; }
        public Builder targetDsn(String targetDsn) { this.targetDsn = targetDsn; return this; }
        public Builder targetUser(String targetUser) { this.targetUser = targetUser; return this; }
        public Builder targetPassword(String targetPassword) { this.targetPassword = targetPassword; return this; }
        public Builder targetSchema(String targetSchema) { this.targetSchema = targetSchema; return this; }
        public Builder mode(WorkflowMode mode) { this.mode = mode; return this; }
        public Builder skipStep(MigrationStepId step) { this.skippedSteps.add(step); return this; }
        public Builder skippedSteps(Set<MigrationStepId> steps) { if (steps != null) this.skippedSteps.addAll(steps); return this; }
        public Builder stopOnError(boolean stopOnError) { this.stopOnError = stopOnError; return this; }
        public Builder parallelWorkers(int parallelWorkers) { this.parallelWorkers = parallelWorkers; return this; }
        public Builder outputDir(String outputDir) { this.outputDir = outputDir; return this; }
        public Builder language(Language language) { this.language = language; return this; }

        public WorkflowConfig build() {
            return new WorkflowConfig(
                    sourceDsn, sourceUser, sourcePassword, sourceSchema,
                    targetDsn, targetUser, targetPassword, targetSchema,
                    mode, skippedSteps, stopOnError, parallelWorkers, outputDir, language
            );
        }
    }
}
