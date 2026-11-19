package com.zettais.db2db.cli;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import com.zettais.db2db.core.assessment.AssessmentEngine;
import com.zettais.db2db.core.assessment.AssessmentReport;
import com.zettais.db2db.core.assessment.AssessmentReportGenerator;
import com.zettais.db2db.core.config.Db2DbConfig;
import com.zettais.db2db.core.ddl.PostgreSqlDdlGenerator;
import com.zettais.db2db.core.extractor.JdbcMetadataExtractor;
import com.zettais.db2db.core.extractor.MetadataExtractor;
import com.zettais.db2db.core.extractor.OracleMetadataExtractor;
import com.zettais.db2db.core.model.DatabaseCatalog;
import com.zettais.db2db.core.model.Table;
import com.zettais.db2db.core.streaming.DataStreamingEngine;
import com.zettais.db2db.core.transpiler.PlSqlTranspiler;
import com.zettais.db2db.core.workflow.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.Callable;

@TopCommand
@Command(name = "db2db",
        mixinStandardHelpOptions = true,
        version = "db2db 1.0.0 (JDK 25)",
        description = "DB2DB by ZettaIS - Enterprise Multi-Dialect Database Migration Platform",
        subcommands = {
                Db2DbCliApplication.TestConnectionCommand.class,
                Db2DbCliApplication.AnalyzeCommand.class,
                Db2DbCliApplication.ExportSchemaCommand.class,
                Db2DbCliApplication.ExportDataCommand.class,
                Db2DbCliApplication.ConvertSqlCommand.class,
                Db2DbCliApplication.WorkflowCommand.class
        })
public class Db2DbCliApplication implements Callable<Integer> {

    @Option(names = {"-c", "--config"}, description = "Path to db2db configuration or properties file")
    protected File configFile;

    @Option(names = {"-s", "--schema"}, description = "Source schema name to extract")
    protected String schema;

    @Option(names = {"-o", "--output"}, description = "Output directory for generated files")
    protected String outputDir;

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Db2DbCliApplication()).execute(args);
        System.exit(exitCode);
    }

    protected Db2DbConfig resolveConfig() {
        Db2DbConfig config = new Db2DbConfig();
        if (configFile != null && configFile.exists()) {
            try {
                config = Db2DbConfig.loadFromFile(configFile.toPath());
            } catch (IOException e) {
                System.err.println("Error reading config file: " + e.getMessage());
            }
        }
        if (schema != null) config.setSourceSchema(schema);
        if (outputDir != null) config.setOutputPath(outputDir);
        return config;
    }

    protected MetadataExtractor getExtractor(Db2DbConfig config) {
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

    // --- Subcommand: test-connection ---
    @Command(name = "test-connection", description = "Test connectivity to source and target databases")
    public static class TestConnectionCommand implements Callable<Integer> {
        @CommandLine.ParentCommand
        private Db2DbCliApplication parent;

        @Override
        public Integer call() {
            Db2DbConfig cfg = parent.resolveConfig();
            System.out.println("Testing Source connection: " + cfg.getSourceDsn());
            try (Connection conn = DriverManager.getConnection(cfg.getSourceDsn(), cfg.getSourceUser(), cfg.getSourcePassword())) {
                System.out.println("  [OK] Successfully connected to Source database! Product: " + conn.getMetaData().getDatabaseProductName() + " " + conn.getMetaData().getDatabaseProductVersion());
            } catch (Exception e) {
                System.err.println("  [FAIL] Failed to connect to Source: " + e.getMessage());
            }

            System.out.println("Testing Target connection: " + cfg.getTargetDsn());
            try (Connection conn = DriverManager.getConnection(cfg.getTargetDsn(), cfg.getTargetUser(), cfg.getTargetPassword())) {
                System.out.println("  [OK] Successfully connected to Target PostgreSQL database! Version: " + conn.getMetaData().getDatabaseProductVersion());
            } catch (Exception e) {
                System.err.println("  [FAIL] Failed to connect to Target: " + e.getMessage());
            }
            return 0;
        }
    }

    // --- Subcommand: analyze ---
    @Command(name = "analyze", description = "Generate schema migration assessment, cost score, and report")
    public static class AnalyzeCommand implements Callable<Integer> {
        @CommandLine.ParentCommand
        private Db2DbCliApplication parent;

        @Option(names = {"--format"}, defaultValue = "html", description = "Report format: html or json")
        private String format;

        @Option(names = {"--sample", "--fake-report"}, description = "Generate a comprehensive showcase/fake assessment report illustrating all features without connecting to a database")
        private boolean sample;

        @Override
        public Integer call() throws Exception {
            Db2DbConfig cfg = parent.resolveConfig();

            if (sample) {
                System.out.println("Generating comprehensive Showcase / Sample Assessment Report...");
                AssessmentEngine engine = new AssessmentEngine();
                AssessmentReport report = engine.createSampleReport();
                AssessmentReportGenerator reportGen = new AssessmentReportGenerator();
                File outDir = new File(cfg.getOutputPath());
                outDir.mkdirs();

                if ("json".equalsIgnoreCase(format)) {
                    File outFile = new File(outDir, "assessment_report.json");
                    try (FileWriter fw = new FileWriter(outFile)) {
                        fw.write(reportGen.toJson(report));
                    }
                    System.out.println("JSON Showcase Assessment report written to: " + outFile.getAbsolutePath());
                } else {
                    File outFile = new File(outDir, "assessment_report.html");
                    try (FileWriter fw = new FileWriter(outFile)) {
                        fw.write(reportGen.toHtml(report));
                    }
                    System.out.println("HTML Showcase Assessment report written to: " + outFile.getAbsolutePath());
                }

                System.out.println("\n--- Showcase Summary ---");
                System.out.println("Schema: " + report.getSchemaName());
                System.out.println("Total Tables: " + report.getTotalTables());
                System.out.println("Total Rows: " + String.format("%,d", report.getTotalRows()));
                System.out.println("Total PL/SQL Lines: " + report.getTotalPlSqlLines());
                System.out.println("Total Migration Cost Units: " + report.getTotalMigrationCostUnits());
                System.out.println("Estimated Effort: " + report.getEstimatedHumanDays() + " human-days");
                System.out.println("Difficulty Level: " + report.getDifficultyLevel());
                return 0;
            }

            System.out.println("Analyzing schema: " + cfg.getSourceSchema() + " from " + cfg.getSourceDsn());

            try (Connection conn = DriverManager.getConnection(cfg.getSourceDsn(), cfg.getSourceUser(), cfg.getSourcePassword())) {
                MetadataExtractor extractor = parent.getExtractor(cfg);
                DatabaseCatalog catalog = extractor.extractCatalog(conn, cfg.getSourceSchema());

                AssessmentEngine engine = new AssessmentEngine();
                AssessmentReport report = engine.assess(catalog);

                AssessmentReportGenerator reportGen = new AssessmentReportGenerator();
                File outDir = new File(cfg.getOutputPath());
                outDir.mkdirs();

                if ("json".equalsIgnoreCase(format)) {
                    File outFile = new File(outDir, "assessment_report.json");
                    try (FileWriter fw = new FileWriter(outFile)) {
                        fw.write(reportGen.toJson(report));
                    }
                    System.out.println("JSON Assessment report written to: " + outFile.getAbsolutePath());
                } else {
                    File outFile = new File(outDir, "assessment_report.html");
                    try (FileWriter fw = new FileWriter(outFile)) {
                        fw.write(reportGen.toHtml(report));
                    }
                    System.out.println("HTML Assessment report written to: " + outFile.getAbsolutePath());
                }

                System.out.println("\n--- Summary ---");
                System.out.println("Total Tables: " + report.getTotalTables());
                System.out.println("Total Views: " + report.getTotalViews());
                System.out.println("Total Sequences: " + report.getTotalSequences());
                System.out.println("Total PL/SQL Units: " + (report.getTotalFunctions() + report.getTotalProcedures() + report.getTotalPackages()));
                System.out.println("Total Migration Cost Units: " + report.getTotalMigrationCostUnits());
                System.out.println("Estimated Effort: " + report.getEstimatedHumanDays() + " human-days");
                System.out.println("Difficulty Level: " + report.getDifficultyLevel());
            }
            return 0;
        }
    }

    // --- Subcommand: export-schema ---
    @Command(name = "export-schema", description = "Export schema objects to PostgreSQL DDL script files")
    public static class ExportSchemaCommand implements Callable<Integer> {
        @CommandLine.ParentCommand
        private Db2DbCliApplication parent;

        @Override
        public Integer call() throws Exception {
            Db2DbConfig cfg = parent.resolveConfig();
            System.out.println("Exporting schema DDL for: " + cfg.getSourceSchema());

            try (Connection conn = DriverManager.getConnection(cfg.getSourceDsn(), cfg.getSourceUser(), cfg.getSourcePassword())) {
                MetadataExtractor extractor = parent.getExtractor(cfg);
                DatabaseCatalog catalog = extractor.extractCatalog(conn, cfg.getSourceSchema());

                PostgreSqlDdlGenerator ddlGen = new PostgreSqlDdlGenerator(cfg);
                File outDir = new File(cfg.getOutputPath());
                outDir.mkdirs();

                File tablesFile = new File(outDir, "tables.sql");
                File fkFile = new File(outDir, "foreign_keys.sql");
                File idxFile = new File(outDir, "indexes.sql");

                try (FileWriter tw = new FileWriter(tablesFile);
                     FileWriter fkw = new FileWriter(fkFile);
                     FileWriter idxw = new FileWriter(idxFile)) {
                    for (Table t : catalog.getTables()) {
                        tw.write(ddlGen.generateTableDdl(t));
                        tw.write("\n");
                        fkw.write(ddlGen.generateDeferredForeignKeys(t));
                        idxw.write(ddlGen.generateIndexesDdl(t));
                    }
                }

                File seqFile = new File(outDir, "sequences.sql");
                try (FileWriter sw = new FileWriter(seqFile)) {
                    for (var s : catalog.getSequences()) {
                        sw.write(ddlGen.generateSequenceDdl(s));
                    }
                }

                File viewFile = new File(outDir, "views.sql");
                try (FileWriter vw = new FileWriter(viewFile)) {
                    for (var v : catalog.getViews()) {
                        vw.write(ddlGen.generateViewDdl(v));
                    }
                }

                System.out.println("DDL generation complete! Files generated in: " + outDir.getAbsolutePath());
            }
            return 0;
        }
    }

    // --- Subcommand: export-data ---
    @Command(name = "export-data", description = "Stream table data directly from source to PostgreSQL")
    public static class ExportDataCommand implements Callable<Integer> {
        @CommandLine.ParentCommand
        private Db2DbCliApplication parent;

        @Override
        public Integer call() throws Exception {
            Db2DbConfig cfg = parent.resolveConfig();
            System.out.println("Starting high-performance data migration to " + cfg.getTargetDsn());

            try (Connection conn = DriverManager.getConnection(cfg.getSourceDsn(), cfg.getSourceUser(), cfg.getSourcePassword())) {
                MetadataExtractor extractor = parent.getExtractor(cfg);
                DatabaseCatalog catalog = extractor.extractCatalog(conn, cfg.getSourceSchema());

                DataStreamingEngine streamer = new DataStreamingEngine(cfg);
                streamer.streamAllTables(catalog.getTables(), (tableName, rows, bytes, completed, error) -> {
                    if (error != null) {
                        System.err.println("Error streaming table " + tableName + ": " + error.getMessage());
                    } else if (completed) {
                        System.out.println("[DONE] " + tableName + " - Total rows: " + rows + " (" + (bytes / 1024) + " KB)");
                    } else {
                        System.out.print("\rMigrating " + tableName + ": " + rows + " rows...");
                    }
                });
            }
            System.out.println("\nData migration finished.");
            return 0;
        }
    }

    // --- Subcommand: convert-sql ---
    @Command(name = "convert-sql", description = "Transpile Oracle PL/SQL or SQL expressions into PL/pgSQL")
    public static class ConvertSqlCommand implements Callable<Integer> {
        @Parameters(index = "0", arity = "0..1", description = "PL/SQL code string or expression")
        private String sqlCode;

        @Option(names = {"-f", "--file"}, description = "Input SQL file to transpile")
        private File inputFile;

        @Override
        public Integer call() throws Exception {
            String input = sqlCode;
            if (inputFile != null && inputFile.exists()) {
                input = java.nio.file.Files.readString(inputFile.toPath());
            }

            if (input == null || input.isBlank()) {
                System.err.println("No input SQL provided. Pass an argument or use -f <file>.");
                return 1;
            }

            PlSqlTranspiler transpiler = new PlSqlTranspiler();
            String transpiled = transpiler.transpile(input);

            System.out.println("=== Transpiled PostgreSQL Code ===");
            System.out.println(transpiled);
            return 0;
        }
    }

    // --- Subcommand: workflow ---
    @Command(name = "workflow", description = "Execute end-to-end multi-step migration workflow (Automatic or Interactive)")
    public static class WorkflowCommand implements Callable<Integer> {
        @CommandLine.ParentCommand
        private Db2DbCliApplication parent;

        @Option(names = {"--auto"}, description = "Run workflow unattended in automatic mode (default)")
        private boolean auto = true;

        @Option(names = {"-i", "--interactive"}, description = "Run workflow interactively with manual step approvals")
        private boolean interactive;

        @Option(names = {"--skip-step"}, description = "Step(s) to skip: PRE_FLIGHT_CHECK, SCHEMA_ASSESSMENT, PRE_SCHEMA_SETUP, DDL_EXPORT, DDL_APPLY, PARALLEL_DATA_STREAMING, POST_DATA_INDEXES, FOREIGN_KEYS, PROCEDURAL_CODE, SEQUENCE_SYNC, DATA_VALIDATION_AUDIT, FINAL_AUDIT_REPORT")
        private List<String> skipStepNames = new ArrayList<>();

        @Option(names = {"--no-stop-on-error"}, description = "Continue subsequent steps even if a step encounters errors")
        private boolean continueOnError;

        @Option(names = {"--workers"}, defaultValue = "4", description = "Number of parallel virtual thread streaming workers")
        private int workers;

        @Override
        public Integer call() throws Exception {
            Db2DbConfig cfg = parent.resolveConfig();
            WorkflowMode mode = interactive ? WorkflowMode.INTERACTIVE : WorkflowMode.AUTOMATIC;

            Set<MigrationStepId> skipped = new HashSet<>();
            for (String stepName : skipStepNames) {
                try {
                    skipped.add(MigrationStepId.valueOf(stepName.trim().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    System.err.println("Unknown step name to skip: " + stepName);
                }
            }

            WorkflowConfig wfConfig = new WorkflowConfig(
                    cfg.getSourceDsn(),
                    cfg.getSourceUser(),
                    cfg.getSourcePassword(),
                    cfg.getSourceSchema(),
                    cfg.getTargetDsn(),
                    cfg.getTargetUser(),
                    cfg.getTargetPassword(),
                    cfg.getTargetSchema(),
                    mode,
                    skipped,
                    !continueOnError,
                    workers,
                    cfg.getOutputPath()
            );

            System.out.println("==================================================================");
            System.out.println("   DB2DB by ZettaIS - End-to-End Migration Workflow Engine");
            System.out.println("==================================================================");
            System.out.println(" Mode:           " + mode);
            System.out.println(" Source:         " + wfConfig.sourceDsn() + " (" + wfConfig.sourceSchema() + ")");
            System.out.println(" Target:         " + wfConfig.targetDsn() + " (" + wfConfig.targetSchema() + ")");
            System.out.println(" Parallelism:    " + wfConfig.parallelWorkers() + " Virtual Threads");
            System.out.println(" Output Folder:  " + wfConfig.outputDir());
            if (!skipped.isEmpty()) {
                System.out.println(" Skipped Steps:  " + skipped);
            }
            System.out.println("==================================================================\n");

            MigrationWorkflowOrchestrator orchestrator = new MigrationWorkflowOrchestrator();

            orchestrator.addListener(new WorkflowEventListener() {
                @Override
                public void onWorkflowStarted(String migrationId, WorkflowConfig config) {
                    System.out.println("[DB2DB] Migration " + migrationId + " started in " + config.mode() + " mode.\n");
                }

                @Override
                public void onStepStatusChanged(MigrationStepId stepId, StepStatus oldStatus, StepStatus newStatus, String message) {
                    String symbol = switch (newStatus) {
                        case SUCCESS -> "  [✓] SUCCESS: ";
                        case SKIPPED -> "  [⊘] SKIPPED: ";
                        case FAILED -> "  [✗] FAILED:  ";
                        case RUNNING -> "  [⚙] RUNNING: ";
                        case WAITING_APPROVAL -> "  [⏳] APPROVAL REQUIRED: ";
                        default -> "  [ ] ";
                    };
                    System.out.println(symbol + stepId.getDisplayName() + (message != null ? " (" + message + ")" : ""));
                }

                @Override
                public void onStepLog(MigrationStepId stepId, String logLine) {
                    System.out.println("      " + logLine);
                }

                @Override
                public void onWorkflowCompleted(WorkflowExecutionReport report) {
                    System.out.println("\n[DB2DB] Workflow completed successfully in " + (report.totalDurationMs() / 1000.0) + "s!");
                }

                @Override
                public void onWorkflowFailed(String migrationId, String error) {
                    System.err.println("\n[DB2DB] Workflow FAILED: " + error);
                }
            });

            var future = orchestrator.startWorkflow(wfConfig);

            // If in interactive mode, handle console input prompt
            if (mode == WorkflowMode.INTERACTIVE) {
                Scanner scanner = new Scanner(System.in);
                while (orchestrator.isRunning() || orchestrator.isWaitingApproval()) {
                    if (orchestrator.isWaitingApproval()) {
                        var status = orchestrator.getStatus();
                        System.out.print("\n>>> Execute step [" + status.currentStep().getDisplayName() + "]? ([y]es / [s]kip / [q]uit): ");
                        if (scanner.hasNextLine()) {
                            String input = scanner.nextLine().trim().toLowerCase();
                            if ("s".equals(input) || "skip".equals(input)) {
                                orchestrator.skipCurrentStep();
                            } else if ("q".equals(input) || "quit".equals(input) || "abort".equals(input)) {
                                orchestrator.cancelWorkflow();
                                break;
                            } else {
                                orchestrator.approveCurrentStep();
                            }
                        }
                    }
                    Thread.sleep(100);
                }
            }

            WorkflowExecutionReport report = future.get();
            System.out.println("\n==================================================================");
            System.out.println("   MIGRATION WORKFLOW SUMMARY");
            System.out.println("==================================================================");
            System.out.println(" Total Steps:    " + report.stepRecords().size());
            System.out.println(" Successful:     " + report.getSuccessCount());
            System.out.println(" Skipped:        " + report.getSkippedCount());
            System.out.println(" Failed:         " + report.getFailedCount());
            System.out.println(" Total Duration: " + (report.totalDurationMs() / 1000.0) + " seconds");
            System.out.println(" HTML Report:    " + new File(wfConfig.outputDir(), "migration_audit_report.html").getAbsolutePath());
            System.out.println("==================================================================");

            return report.overallSuccess() ? 0 : 1;
        }
    }
}
