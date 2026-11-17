package com.zettais.db2db.core.workflow;

import com.zettais.db2db.core.assessment.AssessmentEngine;
import com.zettais.db2db.core.assessment.AssessmentReport;
import com.zettais.db2db.core.config.Db2DbConfig;
import com.zettais.db2db.core.ddl.PostgreSqlDdlGenerator;
import com.zettais.db2db.core.extractor.JdbcMetadataExtractor;
import com.zettais.db2db.core.extractor.MetadataExtractor;
import com.zettais.db2db.core.extractor.OracleMetadataExtractor;
import com.zettais.db2db.core.model.DatabaseCatalog;
import com.zettais.db2db.core.model.Table;
import com.zettais.db2db.core.streaming.DataStreamingEngine;
import com.zettais.db2db.core.transpiler.PlSqlTranspiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Industrial-grade End-to-End Migration Workflow Orchestrator.
 * Powered by JDK 25 Virtual Threads and modern Java constructs.
 */
public class MigrationWorkflowOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MigrationWorkflowOrchestrator.class);

    public enum StepDecision {
        APPROVE,
        SKIP,
        ABORT
    }

    public record OrchestratorStatus(
            String migrationId,
            boolean isRunning,
            boolean isPaused,
            MigrationStepId currentStep,
            StepStatus currentStepStatus,
            int currentStepIndex,
            int totalSteps,
            int progressPercent,
            List<StepExecutionRecord> stepRecords,
            WorkflowExecutionReport finalReport
    ) {}

    private final List<WorkflowEventListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicReference<WorkflowConfig> activeConfig = new AtomicReference<>();
    private final AtomicReference<String> activeMigrationId = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean pausedForApproval = new AtomicBoolean(false);
    private final AtomicReference<MigrationStepId> currentStepRef = new AtomicReference<>();
    private final List<StepExecutionRecord> stepRecords = new CopyOnWriteArrayList<>();
    private final AtomicReference<CompletableFuture<StepDecision>> approvalFutureRef = new AtomicReference<>();
    private final AtomicReference<WorkflowExecutionReport> finalReportRef = new AtomicReference<>();

    private DatabaseCatalog extractedCatalog;
    private long workflowStartTime = 0;

    public MigrationWorkflowOrchestrator() {
        for (MigrationStepId step : MigrationStepId.values()) {
            stepRecords.add(StepExecutionRecord.initial(step));
        }
    }

    public void addListener(WorkflowEventListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(WorkflowEventListener listener) {
        listeners.remove(listener);
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isWaitingApproval() {
        return pausedForApproval.get();
    }

    public OrchestratorStatus getStatus() {
        String migId = activeMigrationId.get();
        MigrationStepId curStep = currentStepRef.get();
        int curIdx = curStep != null ? curStep.ordinal() : 0;
        int total = MigrationStepId.values().length;
        int pct = (int) Math.round(((double) curIdx / total) * 100.0);
        if (!running.get() && finalReportRef.get() != null) {
            pct = 100;
        }

        StepStatus curStatus = StepStatus.PENDING;
        if (curStep != null) {
            for (var r : stepRecords) {
                if (r.stepId() == curStep) {
                    curStatus = r.status();
                    break;
                }
            }
        }

        return new OrchestratorStatus(
                migId,
                running.get(),
                pausedForApproval.get(),
                curStep,
                curStatus,
                curIdx,
                total,
                pct,
                List.copyOf(stepRecords),
                finalReportRef.get()
        );
    }

    public CompletableFuture<WorkflowExecutionReport> startWorkflow(WorkflowConfig config) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A migration workflow is already running: " + activeMigrationId.get());
        }

        String migrationId = "MIG-" + System.currentTimeMillis();
        this.activeMigrationId.set(migrationId);
        this.activeConfig.set(config);
        this.finalReportRef.set(null);
        this.workflowStartTime = System.currentTimeMillis();
        this.stepRecords.clear();

        for (MigrationStepId step : MigrationStepId.values()) {
            stepRecords.add(StepExecutionRecord.initial(step));
        }

        CompletableFuture<WorkflowExecutionReport> completionFuture = new CompletableFuture<>();

        for (var listener : listeners) {
            try {
                listener.onWorkflowStarted(migrationId, config);
            } catch (Exception ex) {
                log.error("Error invoking listener onWorkflowStarted", ex);
            }
        }

        // Execute in a JDK 25 Virtual Thread
        Thread.ofVirtual().name("db2db-workflow-" + migrationId).start(() -> {
            try {
                executeLifecycle();
                WorkflowExecutionReport report = compileFinalReport(true, null);
                finalReportRef.set(report);
                for (var listener : listeners) {
                    try {
                        listener.onWorkflowCompleted(report);
                    } catch (Exception ex) {
                        log.error("Error invoking listener onWorkflowCompleted", ex);
                    }
                }
                completionFuture.complete(report);
            } catch (Throwable t) {
                log.error("Workflow failed with error: {}", t.getMessage(), t);
                WorkflowExecutionReport report = compileFinalReport(false, t.getMessage());
                finalReportRef.set(report);
                for (var listener : listeners) {
                    try {
                        listener.onWorkflowFailed(migrationId, t.getMessage());
                    } catch (Exception ex) {
                        log.error("Error invoking listener onWorkflowFailed", ex);
                    }
                }
                completionFuture.completeExceptionally(t);
            } finally {
                running.set(false);
                pausedForApproval.set(false);
                currentStepRef.set(null);
            }
        });

        return completionFuture;
    }

    public boolean approveCurrentStep() {
        CompletableFuture<StepDecision> future = approvalFutureRef.get();
        if (future != null && !future.isDone()) {
            pausedForApproval.set(false);
            return future.complete(StepDecision.APPROVE);
        }
        return false;
    }

    public boolean skipCurrentStep() {
        CompletableFuture<StepDecision> future = approvalFutureRef.get();
        if (future != null && !future.isDone()) {
            pausedForApproval.set(false);
            return future.complete(StepDecision.SKIP);
        }
        return false;
    }

    public boolean cancelWorkflow() {
        CompletableFuture<StepDecision> future = approvalFutureRef.get();
        if (future != null && !future.isDone()) {
            future.complete(StepDecision.ABORT);
        }
        running.set(false);
        pausedForApproval.set(false);
        return true;
    }

    private void executeLifecycle() throws Exception {
        WorkflowConfig config = activeConfig.get();

        for (MigrationStepId step : MigrationStepId.values()) {
            if (!running.get()) {
                log.info("Workflow execution cancelled.");
                break;
            }

            currentStepRef.set(step);

            // Check if explicitly configured as skipped
            if (config.isStepSkipped(step)) {
                recordStepSkipped(step, "Skipped by user configuration");
                continue;
            }

            // Interactive mode approval check
            if (config.mode() == WorkflowMode.INTERACTIVE) {
                pausedForApproval.set(true);
                updateStepStatus(step, StepStatus.WAITING_APPROVAL, "Waiting for operator approval to begin step");

                CompletableFuture<StepDecision> approvalFuture = new CompletableFuture<>();
                approvalFutureRef.set(approvalFuture);

                StepDecision decision = approvalFuture.get(); // Waits on virtual thread
                approvalFutureRef.set(null);
                pausedForApproval.set(false);

                if (decision == StepDecision.ABORT) {
                    recordStepFailed(step, "Workflow aborted by operator");
                    throw new RuntimeException("Workflow aborted by operator at step: " + step.getDisplayName());
                } else if (decision == StepDecision.SKIP) {
                    recordStepSkipped(step, "Skipped by operator intervention");
                    continue;
                }
            }

            // Execute the step
            long stepStart = System.currentTimeMillis();
            updateStepStatus(step, StepStatus.RUNNING, "Executing " + step.getDisplayName());
            List<String> stepLogs = new ArrayList<>();
            Map<String, Object> stepMetrics = new HashMap<>();

            try {
                executeStepAction(step, config, stepLogs, stepMetrics);
                long stepEnd = System.currentTimeMillis();
                recordStepSuccess(step, stepStart, stepEnd, stepLogs, stepMetrics);
            } catch (Exception ex) {
                long stepEnd = System.currentTimeMillis();
                log.error("Step {} failed: {}", step, ex.getMessage(), ex);
                recordStepFailed(step, ex.getMessage());
                if (config.stopOnError()) {
                    throw ex;
                }
            }
        }
    }

    private void executeStepAction(
            MigrationStepId step,
            WorkflowConfig config,
            List<String> logs,
            Map<String, Object> metrics
    ) throws Exception {
        switch (step) {
            case PRE_FLIGHT_CHECK -> executePreFlight(config, logs, metrics);
            case SCHEMA_ASSESSMENT -> executeAssessment(config, logs, metrics);
            case PRE_SCHEMA_SETUP -> executePreSchemaSetup(config, logs, metrics);
            case DDL_EXPORT -> executeDdlExport(config, logs, metrics);
            case DDL_APPLY -> executeDdlApply(config, logs, metrics);
            case PARALLEL_DATA_STREAMING -> executeDataStreaming(config, logs, metrics);
            case POST_DATA_INDEXES -> executeIndexes(config, logs, metrics);
            case FOREIGN_KEYS -> executeForeignKeys(config, logs, metrics);
            case PROCEDURAL_CODE -> executeProceduralCode(config, logs, metrics);
            case SEQUENCE_SYNC -> executeSequenceSync(config, logs, metrics);
            case DATA_VALIDATION_AUDIT -> executeDataValidation(config, logs, metrics);
            case FINAL_AUDIT_REPORT -> executeFinalReportStep(config, logs, metrics);
        }
    }

    private void executePreFlight(WorkflowConfig config, List<String> logs, Map<String, Object> metrics) throws Exception {
        logToStep(MigrationStepId.PRE_FLIGHT_CHECK, logs, "Verifying connectivity to Source: " + config.sourceDsn());
        try (Connection srcConn = DriverManager.getConnection(config.sourceDsn(), config.sourceUser(), config.sourcePassword())) {
            var meta = srcConn.getMetaData();
            String prodName = meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion();
            logToStep(MigrationStepId.PRE_FLIGHT_CHECK, logs, "Source Database Verified: " + prodName);
            metrics.put("sourceProduct", prodName);
        }

        logToStep(MigrationStepId.PRE_FLIGHT_CHECK, logs, "Verifying connectivity to Target PostgreSQL: " + config.targetDsn());
        try (Connection tgtConn = DriverManager.getConnection(config.targetDsn(), config.targetUser(), config.targetPassword())) {
            var meta = tgtConn.getMetaData();
            String prodName = meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion();
            logToStep(MigrationStepId.PRE_FLIGHT_CHECK, logs, "Target Database Verified: " + prodName);
            metrics.put("targetProduct", prodName);
        }
        metrics.put("connectivity", "OK");
    }

    private void executeAssessment(WorkflowConfig config, List<String> logs, Map<String, Object> metrics) throws Exception {
        logToStep(MigrationStepId.SCHEMA_ASSESSMENT, logs, "Introspecting metadata catalog for schema: " + config.sourceSchema());
        Db2DbConfig coreCfg = toCoreConfig(config);
        MetadataExtractor extractor = getExtractor(coreCfg);

        try (Connection conn = DriverManager.getConnection(config.sourceDsn(), config.sourceUser(), config.sourcePassword())) {
            this.extractedCatalog = extractor.extractCatalog(conn, config.sourceSchema());
        }

        AssessmentEngine engine = new AssessmentEngine();
        AssessmentReport report = engine.assess(this.extractedCatalog);

        logToStep(MigrationStepId.SCHEMA_ASSESSMENT, logs, "Assessment complete: " + report.getTotalTables() + " tables, "
                + report.getTotalRows() + " rows, " + report.getTotalMigrationCostUnits() + " cost units.");
        metrics.put("totalTables", report.getTotalTables());
        metrics.put("totalViews", report.getTotalViews());
        metrics.put("totalSequences", report.getTotalSequences());
        metrics.put("totalRows", report.getTotalRows());
        metrics.put("costUnits", report.getTotalMigrationCostUnits());
        metrics.put("difficulty", report.getDifficultyLevel());
    }

    private void executePreSchemaSetup(WorkflowConfig config, List<String> logs, Map<String, Object> metrics) throws Exception {
        String schema = config.targetSchema() != null && !config.targetSchema().isBlank() ? config.targetSchema().toLowerCase() : "public";
        logToStep(MigrationStepId.PRE_SCHEMA_SETUP, logs, "Preparing target schema '" + schema + "' in PostgreSQL...");

        try (Connection conn = DriverManager.getConnection(config.targetDsn(), config.targetUser(), config.targetPassword());
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE SCHEMA IF NOT EXISTS \"" + schema + "\";");
            logToStep(MigrationStepId.PRE_SCHEMA_SETUP, logs, "Ensured target schema exists: " + schema);

            try {
                stmt.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";");
                stmt.execute("CREATE EXTENSION IF NOT EXISTS \"btree_gin\";");
                logToStep(MigrationStepId.PRE_SCHEMA_SETUP, logs, "Enabled standard extensions: uuid-ossp, btree_gin");
                metrics.put("extensionsInstalled", true);
            } catch (Exception ex) {
                logToStep(MigrationStepId.PRE_SCHEMA_SETUP, logs, "Extension installation skipped: " + ex.getMessage());
                metrics.put("extensionsInstalled", false);
            }
        }
    }

    private void executeDdlExport(WorkflowConfig config, List<String> logs, Map<String, Object> metrics) throws Exception {
        if (extractedCatalog == null) {
            logToStep(MigrationStepId.DDL_EXPORT, logs, "Catalog not cached; extracting schema: " + config.sourceSchema());
            executeAssessment(config, logs, metrics);
        }

        Db2DbConfig coreCfg = toCoreConfig(config);
        PostgreSqlDdlGenerator ddlGen = new PostgreSqlDdlGenerator(coreCfg);
        File outDir = new File(config.outputDir());
        outDir.mkdirs();

        File tablesDdlFile = new File(outDir, "tables.sql");
        try (FileWriter fw = new FileWriter(tablesDdlFile)) {
            for (Table t : extractedCatalog.getTables()) {
                fw.write(ddlGen.generateTableDdl(t));
                fw.write("\n\n");
            }
        }
        logToStep(MigrationStepId.DDL_EXPORT, logs, "Generated DDL for " + extractedCatalog.getTables().size() + " tables in: " + tablesDdlFile.getAbsolutePath());
        metrics.put("ddlFile", tablesDdlFile.getName());
        metrics.put("tableCount", extractedCatalog.getTables().size());
    }

    private void executeDdlApply(WorkflowConfig config, List<String> logs, Map<String, Object> metrics) throws Exception {
        if (extractedCatalog == null) {
            executeAssessment(config, logs, metrics);
        }

        Db2DbConfig coreCfg = toCoreConfig(config);
        PostgreSqlDdlGenerator ddlGen = new PostgreSqlDdlGenerator(coreCfg);

        try (Connection conn = DriverManager.getConnection(config.targetDsn(), config.targetUser(), config.targetPassword());
             Statement stmt = conn.createStatement()) {

            int applied = 0;
            for (Table t : extractedCatalog.getTables()) {
                String ddl = ddlGen.generateTableDdl(t);
                try {
                    stmt.execute(ddl);
                    applied++;
                } catch (Exception ex) {
                    logToStep(MigrationStepId.DDL_APPLY, logs, "Warning creating table " + t.getName() + ": " + ex.getMessage());
                }
            }
            logToStep(MigrationStepId.DDL_APPLY, logs, "Successfully applied " + applied + " table DDLs to target database.");
            metrics.put("tablesApplied", applied);
        }
    }

    private void executeDataStreaming(WorkflowConfig config, List<String> logs, Map<String, Object> metrics) throws Exception {
        if (extractedCatalog == null) {
            executeAssessment(config, logs, metrics);
        }

        Db2DbConfig coreCfg = toCoreConfig(config);
        DataStreamingEngine streamer = new DataStreamingEngine(coreCfg);
        logToStep(MigrationStepId.PARALLEL_DATA_STREAMING, logs, "Initiating parallel data streaming via JDK 25 Virtual Threads...");

        long[] totalTransferred = new long[]{0, 0}; // rows, bytes

        streamer.streamAllTables(extractedCatalog.getTables(), (tableName, rows, bytes, completed, error) -> {
            if (error != null) {
                logToStep(MigrationStepId.PARALLEL_DATA_STREAMING, logs, "Streaming error for " + tableName + ": " + error.getMessage());
            } else if (completed) {
                totalTransferred[0] += rows;
                totalTransferred[1] += bytes;
                logToStep(MigrationStepId.PARALLEL_DATA_STREAMING, logs, "Streamed table " + tableName + " -> " + rows + " rows (" + (bytes / 1024) + " KB)");
            }
        });

        metrics.put("totalRowsMigrated", totalTransferred[0]);
        metrics.put("totalBytesMigrated", totalTransferred[1]);
        logToStep(MigrationStepId.PARALLEL_DATA_STREAMING, logs, "Data streaming completed: " + totalTransferred[0] + " total rows migrated.");
    }

    private void executeIndexes(WorkflowConfig config, List<String> logs, Map<String, Object> metrics) throws Exception {
        if (extractedCatalog == null) return;
        Db2DbConfig coreCfg = toCoreConfig(config);
        PostgreSqlDdlGenerator ddlGen = new PostgreSqlDdlGenerator(coreCfg);

        try (Connection conn = DriverManager.getConnection(config.targetDsn(), config.targetUser(), config.targetPassword());
             Statement stmt = conn.createStatement()) {

            int indexCount = 0;
            for (Table t : extractedCatalog.getTables()) {
                String idxDdl = ddlGen.generateIndexesDdl(t);
                if (idxDdl != null && !idxDdl.isBlank()) {
                    for (String sql : idxDdl.split(";")) {
                        String trimmed = sql.trim();
                        if (!trimmed.isEmpty()) {
                            try {
                                stmt.execute(trimmed);
                                indexCount++;
                            } catch (Exception ex) {
                                logToStep(MigrationStepId.POST_DATA_INDEXES, logs, "Notice on index (" + t.getName() + "): " + ex.getMessage());
                            }
                        }
                    }
                }
            }
            logToStep(MigrationStepId.POST_DATA_INDEXES, logs, "Created " + indexCount + " indexes post-data streaming.");
            metrics.put("indexesCreated", indexCount);
        }
    }

    private void executeForeignKeys(WorkflowConfig config, List<String> logs, Map<String, Object> metrics) throws Exception {
        if (extractedCatalog == null) return;
        Db2DbConfig coreCfg = toCoreConfig(config);
        PostgreSqlDdlGenerator ddlGen = new PostgreSqlDdlGenerator(coreCfg);

        try (Connection conn = DriverManager.getConnection(config.targetDsn(), config.targetUser(), config.targetPassword());
             Statement stmt = conn.createStatement()) {

            int fkCount = 0;
            for (Table t : extractedCatalog.getTables()) {
                String fkDdl = ddlGen.generateDeferredForeignKeys(t);
                if (fkDdl != null && !fkDdl.isBlank()) {
                    for (String sql : fkDdl.split(";")) {
                        String trimmed = sql.trim();
                        if (!trimmed.isEmpty()) {
                            try {
                                stmt.execute(trimmed);
                                fkCount++;
                            } catch (Exception ex) {
                                logToStep(MigrationStepId.FOREIGN_KEYS, logs, "Notice on FK (" + t.getName() + "): " + ex.getMessage());
                            }
                        }
                    }
                }
            }
            logToStep(MigrationStepId.FOREIGN_KEYS, logs, "Created " + fkCount + " foreign key constraints.");
            metrics.put("foreignKeysCreated", fkCount);
        }
    }

    private void executeProceduralCode(WorkflowConfig config, List<String> logs, Map<String, Object> metrics) throws Exception {
        if (extractedCatalog == null) return;
        PlSqlTranspiler transpiler = new PlSqlTranspiler();
        int converted = 0;

        File outDir = new File(config.outputDir());
        outDir.mkdirs();
        File plsqlOut = new File(outDir, "procedural_plpgsql.sql");

        try (FileWriter fw = new FileWriter(plsqlOut)) {
            for (var unit : extractedCatalog.getPlSqlUnits()) {
                if (unit.getSourceCode() != null && !unit.getSourceCode().isBlank()) {
                    String pgSql = transpiler.transpile(unit.getSourceCode());
                    fw.write("-- " + unit.getType() + ": " + unit.getName() + "\n");
                    fw.write(pgSql);
                    fw.write("\n\n");
                    converted++;
                }
            }
        }

        logToStep(MigrationStepId.PROCEDURAL_CODE, logs, "Transpiled " + converted + " procedural routines to: " + plsqlOut.getName());
        metrics.put("routinesTranspiled", converted);
    }

    private void executeSequenceSync(WorkflowConfig config, List<String> logs, Map<String, Object> metrics) {
        logToStep(MigrationStepId.SEQUENCE_SYNC, logs, "Synchronizing sequence values with table maximum IDs...");
        SequenceSynchronizer synchronizer = new SequenceSynchronizer();
        var syncResults = synchronizer.synchronizeSequences(
                config.targetDsn(), config.targetUser(), config.targetPassword(), config.targetSchema()
        );

        long successCount = syncResults.stream().filter(SequenceSynchronizer.SequenceSyncResult::success).count();
        logToStep(MigrationStepId.SEQUENCE_SYNC, logs, "Synchronized " + successCount + " / " + syncResults.size() + " sequences.");
        metrics.put("sequencesSynchronized", successCount);
        metrics.put("totalSequences", syncResults.size());
    }

    private void executeDataValidation(WorkflowConfig config, List<String> logs, Map<String, Object> metrics) {
        if (extractedCatalog == null || extractedCatalog.getTables().isEmpty()) {
            logToStep(MigrationStepId.DATA_VALIDATION_AUDIT, logs, "No tables detected for validation.");
            return;
        }

        logToStep(MigrationStepId.DATA_VALIDATION_AUDIT, logs, "Performing cross-database row count validation...");
        List<String> tableNames = extractedCatalog.getTables().stream().map(Table::getName).toList();

        DataValidationEngine engine = new DataValidationEngine();
        var report = engine.validate(
                config.sourceDsn(), config.sourceUser(), config.sourcePassword(), config.sourceSchema(),
                config.targetDsn(), config.targetUser(), config.targetPassword(), config.targetSchema(),
                tableNames
        );

        logToStep(MigrationStepId.DATA_VALIDATION_AUDIT, logs, "Validation Complete: " + report.matchedTables() + " matched, " + report.mismatchedTables() + " mismatched.");
        metrics.put("matchedTables", report.matchedTables());
        metrics.put("mismatchedTables", report.mismatchedTables());
        metrics.put("totalSourceRows", report.totalSourceRows());
        metrics.put("totalTargetRows", report.totalTargetRows());
    }

    private void executeFinalReportStep(WorkflowConfig config, List<String> logs, Map<String, Object> metrics) throws IOException {
        logToStep(MigrationStepId.FINAL_AUDIT_REPORT, logs, "Compiling final migration audit reports...");
        WorkflowExecutionReport report = compileFinalReport(true, null);

        WorkflowReportGenerator generator = new WorkflowReportGenerator();
        File outDir = new File(config.outputDir());
        outDir.mkdirs();

        File jsonFile = new File(outDir, "migration_audit_report.json");
        Files.writeString(jsonFile.toPath(), generator.toJson(report));

        File htmlFile = new File(outDir, "migration_audit_report.html");
        Files.writeString(htmlFile.toPath(), generator.toHtml(report));

        logToStep(MigrationStepId.FINAL_AUDIT_REPORT, logs, "Audit reports saved: " + jsonFile.getName() + ", " + htmlFile.getName());
        metrics.put("htmlReportPath", htmlFile.getAbsolutePath());
        metrics.put("jsonReportPath", jsonFile.getAbsolutePath());
    }

    private WorkflowExecutionReport compileFinalReport(boolean success, String error) {
        WorkflowConfig cfg = activeConfig.get();
        long now = System.currentTimeMillis();
        long duration = now - workflowStartTime;

        Map<String, Object> summary = new HashMap<>();
        summary.put("status", success ? "SUCCESS" : "FAILED");
        if (error != null) summary.put("error", error);

        return new WorkflowExecutionReport(
                activeMigrationId.get(),
                cfg != null ? cfg.sourceDsn() : "N/A",
                cfg != null ? cfg.targetDsn() : "N/A",
                cfg != null ? cfg.sourceSchema() : "N/A",
                cfg != null ? cfg.targetSchema() : "N/A",
                cfg != null ? cfg.mode() : WorkflowMode.AUTOMATIC,
                workflowStartTime,
                now,
                duration,
                success,
                List.copyOf(stepRecords),
                summary
        );
    }

    private void logToStep(MigrationStepId stepId, List<String> logs, String message) {
        String logLine = "[" + DateTimeFormatterHolder.formatNow() + "] " + message;
        logs.add(logLine);
        for (var listener : listeners) {
            try {
                listener.onStepLog(stepId, logLine);
            } catch (Exception ex) {
                log.error("Error in onStepLog", ex);
            }
        }
    }

    private void updateStepStatus(MigrationStepId stepId, StepStatus newStatus, String message) {
        for (int i = 0; i < stepRecords.size(); i++) {
            var rec = stepRecords.get(i);
            if (rec.stepId() == stepId) {
                StepStatus oldStatus = rec.status();
                stepRecords.set(i, rec.withStatus(newStatus));
                for (var listener : listeners) {
                    try {
                        listener.onStepStatusChanged(stepId, oldStatus, newStatus, message);
                    } catch (Exception ex) {
                        log.error("Error in onStepStatusChanged", ex);
                    }
                }
                break;
            }
        }
    }

    private void recordStepSuccess(MigrationStepId stepId, long start, long end, List<String> logs, Map<String, Object> metrics) {
        for (int i = 0; i < stepRecords.size(); i++) {
            var rec = stepRecords.get(i);
            if (rec.stepId() == stepId) {
                stepRecords.set(i, new StepExecutionRecord(
                        stepId, stepId.getDisplayName(), StepStatus.SUCCESS,
                        start, end, end - start, logs, metrics, null
                ));
                for (var listener : listeners) {
                    try {
                        listener.onStepStatusChanged(stepId, StepStatus.RUNNING, StepStatus.SUCCESS, "Completed successfully");
                    } catch (Exception ex) {
                        log.error("Error in onStepStatusChanged", ex);
                    }
                }
                break;
            }
        }
    }

    private void recordStepFailed(MigrationStepId stepId, String error) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < stepRecords.size(); i++) {
            var rec = stepRecords.get(i);
            if (rec.stepId() == stepId) {
                stepRecords.set(i, new StepExecutionRecord(
                        stepId, stepId.getDisplayName(), StepStatus.FAILED,
                        rec.startTimeEpochMs() > 0 ? rec.startTimeEpochMs() : now,
                        now, now - (rec.startTimeEpochMs() > 0 ? rec.startTimeEpochMs() : now),
                        rec.logs(), rec.metrics(), error
                ));
                for (var listener : listeners) {
                    try {
                        listener.onStepStatusChanged(stepId, rec.status(), StepStatus.FAILED, error);
                    } catch (Exception ex) {
                        log.error("Error in onStepStatusChanged", ex);
                    }
                }
                break;
            }
        }
    }

    private void recordStepSkipped(MigrationStepId stepId, String reason) {
        for (int i = 0; i < stepRecords.size(); i++) {
            var rec = stepRecords.get(i);
            if (rec.stepId() == stepId) {
                stepRecords.set(i, new StepExecutionRecord(
                        stepId, stepId.getDisplayName(), StepStatus.SKIPPED,
                        0, 0, 0,
                        List.of("[" + DateTimeFormatterHolder.formatNow() + "] " + reason),
                        Map.of(), null
                ));
                for (var listener : listeners) {
                    try {
                        listener.onStepStatusChanged(stepId, rec.status(), StepStatus.SKIPPED, reason);
                    } catch (Exception ex) {
                        log.error("Error in onStepStatusChanged", ex);
                    }
                }
                break;
            }
        }
    }

    private Db2DbConfig toCoreConfig(WorkflowConfig cfg) {
        Db2DbConfig c = new Db2DbConfig();
        c.setSourceDsn(cfg.sourceDsn());
        c.setSourceUser(cfg.sourceUser());
        c.setSourcePassword(cfg.sourcePassword());
        c.setSourceSchema(cfg.sourceSchema());
        c.setTargetDsn(cfg.targetDsn());
        c.setTargetUser(cfg.targetUser());
        c.setTargetPassword(cfg.targetPassword());
        c.setTargetSchema(cfg.targetSchema());
        c.setOutputPath(cfg.outputDir());
        c.setParallelWorkers(cfg.parallelWorkers());
        return c;
    }

    private MetadataExtractor getExtractor(Db2DbConfig config) {
        String dsn = config.getSourceDsn().toLowerCase();
        if (dsn.contains("oracle")) {
            return new OracleMetadataExtractor(config);
        } else if (dsn.contains("mysql") || dsn.contains("mariadb")) {
            return new com.zettais.db2db.core.extractor.MySqlMetadataExtractor(config);
        } else if (dsn.contains("sqlserver") || dsn.contains("jtds") || dsn.contains("mssql")) {
            return new com.zettais.db2db.core.extractor.MssqlMetadataExtractor(config);
        }
        return new JdbcMetadataExtractor(config);
    }

    private static class DateTimeFormatterHolder {
        private static final java.time.format.DateTimeFormatter FMT =
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
                        .withZone(java.time.ZoneId.systemDefault());

        static String formatNow() {
            return FMT.format(Instant.now());
        }
    }
}
