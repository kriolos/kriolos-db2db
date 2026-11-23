package com.zettais.db2db.core.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationWorkflowTest {

    @Test
    @DisplayName("WorkflowConfig builder and record immutability")
    void testWorkflowConfig() {
        WorkflowConfig config = WorkflowConfig.builder()
                .sourceDsn("jdbc:oracle:thin:@localhost:1521/XEPDB1")
                .sourceUser("OSADMIN")
                .sourcePassword("Passw0rd")
                .sourceSchema("OSADMIN")
                .targetDsn("jdbc:postgresql://localhost:5432/postgres")
                .targetUser("postgres")
                .targetPassword("secret")
                .targetSchema("osadmin_mig")
                .mode(WorkflowMode.INTERACTIVE)
                .skipStep(MigrationStepId.PRE_FLIGHT_CHECK)
                .skipStep(MigrationStepId.PARALLEL_DATA_STREAMING)
                .stopOnError(true)
                .parallelWorkers(8)
                .outputDir("./target/workflow-reports")
                .build();

        assertThat(config.sourceSchema()).isEqualTo("OSADMIN");
        assertThat(config.targetSchema()).isEqualTo("osadmin_mig");
        assertThat(config.mode()).isEqualTo(WorkflowMode.INTERACTIVE);
        assertThat(config.isStepSkipped(MigrationStepId.PRE_FLIGHT_CHECK)).isTrue();
        assertThat(config.isStepSkipped(MigrationStepId.PARALLEL_DATA_STREAMING)).isTrue();
        assertThat(config.isStepSkipped(MigrationStepId.SCHEMA_ASSESSMENT)).isFalse();
        assertThat(config.parallelWorkers()).isEqualTo(8);
        assertThat(config.targetDialect()).isEqualTo(com.zettais.db2db.core.model.DatabaseDialect.POSTGRESQL);
    }

    @Test
    @DisplayName("WorkflowConfig should support MariaDB target dialect")
    void testWorkflowConfigMariaDb() {
        WorkflowConfig config = WorkflowConfig.builder()
                .targetDsn("jdbc:mariadb://localhost:3306/targetdb")
                .targetDialect(com.zettais.db2db.core.model.DatabaseDialect.MARIADB)
                .build();

        assertThat(config.targetDialect()).isEqualTo(com.zettais.db2db.core.model.DatabaseDialect.MARIADB);
    }

    @Test
    @DisplayName("StepExecutionRecord lifecycle and status transitions")
    void testStepExecutionRecord() {
        StepExecutionRecord initial = StepExecutionRecord.initial(MigrationStepId.SCHEMA_ASSESSMENT);
        assertThat(initial.stepId()).isEqualTo(MigrationStepId.SCHEMA_ASSESSMENT);
        assertThat(initial.status()).isEqualTo(StepStatus.PENDING);
        assertThat(initial.durationMs()).isZero();

        StepExecutionRecord running = initial.withStatus(StepStatus.RUNNING);
        assertThat(running.status()).isEqualTo(StepStatus.RUNNING);

        StepExecutionRecord success = new StepExecutionRecord(
                MigrationStepId.SCHEMA_ASSESSMENT,
                "Schema Assessment",
                StepStatus.SUCCESS,
                1000, 2500, 1500,
                List.of("Log line 1", "Log line 2"),
                Map.of("tables", 12),
                null
        );

        assertThat(success.status()).isEqualTo(StepStatus.SUCCESS);
        assertThat(success.durationMs()).isEqualTo(1500);
        assertThat(success.logs()).hasSize(2);
        assertThat(success.metrics()).containsEntry("tables", 12);
        assertThat(success.errorMessage()).isNull();
    }

    @Test
    @DisplayName("WorkflowReportGenerator JSON and Standalone HTML generation")
    void testWorkflowReportGenerator() throws Exception {
        StepExecutionRecord r1 = new StepExecutionRecord(
                MigrationStepId.PRE_FLIGHT_CHECK, "Pre-Flight Check", StepStatus.SUCCESS,
                1000, 1200, 200, List.of("Checked connectivity"), Map.of("source", "Oracle 21c"), null
        );
        StepExecutionRecord r2 = new StepExecutionRecord(
                MigrationStepId.SCHEMA_ASSESSMENT, "Schema Assessment", StepStatus.SUCCESS,
                1200, 1600, 400, List.of("Catalog extracted"), Map.of("tables", 15, "costUnits", 42), null
        );
        StepExecutionRecord r3 = new StepExecutionRecord(
                MigrationStepId.PARALLEL_DATA_STREAMING, "Parallel Data Streaming", StepStatus.SKIPPED,
                0, 0, 0, List.of("Skipped by user config"), Map.of(), null
        );

        WorkflowExecutionReport report = new WorkflowExecutionReport(
                "MIG-TEST-123",
                "jdbc:oracle:thin:@localhost:1521/XEPDB1",
                "jdbc:postgresql://localhost:5432/postgres",
                "OSADMIN",
                "public",
                WorkflowMode.AUTOMATIC,
                1000, 2500, 1500,
                true,
                List.of(r1, r2, r3),
                Map.of("overall", "SUCCESS")
        );

        WorkflowReportGenerator generator = new WorkflowReportGenerator();
        String json = generator.toJson(report);
        assertThat(json).contains("MIG-TEST-123");
        assertThat(json).contains("OSADMIN");
        assertThat(json).contains("SCHEMA_ASSESSMENT");

        String html = generator.toHtml(report);
        assertThat(html).contains("DB2DB by ZettaIS");
        assertThat(html).contains("MIG-TEST-123");
        assertThat(html).contains("SUCCESSFUL MIGRATION");
        assertThat(html).contains("Pre-Flight Check");
        assertThat(html).contains("Schema Assessment");
        assertThat(html).doesNotContain("ora2pg");
    }

    @Test
    @DisplayName("Orchestrator interactive mode approval and skipping")
    void testInteractiveOrchestratorLifecycle() throws Exception {
        MigrationWorkflowOrchestrator orchestrator = new MigrationWorkflowOrchestrator();

        // Skip all steps except the first one for fast unit test
        WorkflowConfig config = WorkflowConfig.builder()
                .mode(WorkflowMode.INTERACTIVE)
                .skipStep(MigrationStepId.SCHEMA_ASSESSMENT)
                .skipStep(MigrationStepId.PRE_SCHEMA_SETUP)
                .skipStep(MigrationStepId.DDL_EXPORT)
                .skipStep(MigrationStepId.DDL_APPLY)
                .skipStep(MigrationStepId.PARALLEL_DATA_STREAMING)
                .skipStep(MigrationStepId.POST_DATA_INDEXES)
                .skipStep(MigrationStepId.FOREIGN_KEYS)
                .skipStep(MigrationStepId.PROCEDURAL_CODE)
                .skipStep(MigrationStepId.SEQUENCE_SYNC)
                .skipStep(MigrationStepId.DATA_VALIDATION_AUDIT)
                .skipStep(MigrationStepId.FINAL_AUDIT_REPORT)
                .build();

        CompletableFuture<WorkflowExecutionReport> future = orchestrator.startWorkflow(config);

        // Wait until it enters WAITING_APPROVAL on PRE_FLIGHT_CHECK
        int attempts = 0;
        while (!orchestrator.isWaitingApproval() && attempts++ < 50) {
            Thread.sleep(50);
        }

        assertThat(orchestrator.isWaitingApproval()).isTrue();
        var status = orchestrator.getStatus();
        assertThat(status.currentStep()).isEqualTo(MigrationStepId.PRE_FLIGHT_CHECK);
        assertThat(status.currentStepStatus()).isEqualTo(StepStatus.WAITING_APPROVAL);

        // Operator decides to skip PRE_FLIGHT_CHECK
        boolean skipped = orchestrator.skipCurrentStep();
        assertThat(skipped).isTrue();

        WorkflowExecutionReport finalReport = future.get(5, TimeUnit.SECONDS);
        assertThat(finalReport).isNotNull();
        assertThat(finalReport.getSkippedCount()).isGreaterThanOrEqualTo(1);
    }
}
