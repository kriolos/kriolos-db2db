package org.db2db.cli;

import org.db2db.core.assessment.AssessmentEngine;
import org.db2db.core.assessment.AssessmentReport;
import org.db2db.core.assessment.AssessmentReportGenerator;
import org.db2db.core.config.Db2DbConfig;
import org.db2db.core.ddl.PostgreSqlDdlGenerator;
import org.db2db.core.extractor.JdbcMetadataExtractor;
import org.db2db.core.extractor.MetadataExtractor;
import org.db2db.core.extractor.OracleMetadataExtractor;
import org.db2db.core.model.DatabaseCatalog;
import org.db2db.core.model.Table;
import org.db2db.core.streaming.DataStreamingEngine;
import org.db2db.core.transpiler.PlSqlTranspiler;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.concurrent.Callable;

@Command(name = "db2db",
        mixinStandardHelpOptions = true,
        version = "db2db 1.0.0 (JDK 25)",
        description = "Enterprise Oracle & MySQL to PostgreSQL migration engine (Java implementation of ora2pg)",
        subcommands = {
                Db2DbCliApplication.TestConnectionCommand.class,
                Db2DbCliApplication.AnalyzeCommand.class,
                Db2DbCliApplication.ExportSchemaCommand.class,
                Db2DbCliApplication.ExportDataCommand.class,
                Db2DbCliApplication.ConvertSqlCommand.class
        })
public class Db2DbCliApplication implements Callable<Integer> {

    @Option(names = {"-c", "--config"}, description = "Path to ora2pg.conf or db2db properties file")
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
            return new org.db2db.core.extractor.MySqlMetadataExtractor(config);
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

        @Override
        public Integer call() throws Exception {
            Db2DbConfig cfg = parent.resolveConfig();
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

                // Tables & Constraints
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

                // Sequences
                File seqFile = new File(outDir, "sequences.sql");
                try (FileWriter sw = new FileWriter(seqFile)) {
                    for (var s : catalog.getSequences()) {
                        sw.write(ddlGen.generateSequenceDdl(s));
                    }
                }

                // Views
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
}
