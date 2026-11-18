package com.zettais.db2db.core.assessment;

import com.zettais.db2db.core.model.*;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Calculates migration assessment metrics, difficulty levels, and human-days cost
 * for DB2DB by ZettaIS.
 */
public class AssessmentEngine {

    // DB2DB Migration Unit constants
    private static final double COST_TABLE = 1.0;
    private static final double COST_COLUMN = 0.1;
    private static final double COST_CONSTRAINT = 0.5;
    private static final double COST_INDEX = 0.5;
    private static final double COST_SEQUENCE = 0.2;
    private static final double COST_VIEW = 2.0;
    private static final double COST_TRIGGER = 4.0;
    private static final double COST_PROCEDURE = 5.0;
    private static final double COST_FUNCTION = 5.0;
    private static final double COST_PACKAGE = 15.0;
    private static final double COST_PER_PLSQL_LINE = 0.05;

    // 1 Human-Day = ~40 units (equivalent to ~8 hours with 5 units/hr)
    private static final double UNITS_PER_HUMAN_DAY = 40.0;

    private static final Set<String> PG_RESERVED_KEYWORDS = Set.of(
            "ALL", "ANALYSE", "ANALYZE", "AND", "ANY", "ARRAY", "AS", "ASC", "ASYMMETRIC", "BOTH",
            "CASE", "CAST", "CHECK", "COLLATE", "COLUMN", "CONSTRAINT", "CREATE", "CURRENT_DATE",
            "CURRENT_ROLE", "CURRENT_TIME", "CURRENT_TIMESTAMP", "CURRENT_USER", "DEFAULT", "DEFERRABLE",
            "DESC", "DISTINCT", "DO", "ELSE", "END", "EXCEPT", "FALSE", "FETCH", "FOR", "FOREIGN",
            "FROM", "GRANT", "GROUP", "HAVING", "IN", "INITIALLY", "INTERSECT", "INTO", "LATERAL",
            "LEADING", "LIMIT", "LOCALTIME", "LOCALTIMESTAMP", "NOT", "NULL", "OFFSET", "ON", "ONLY",
            "OR", "ORDER", "PLACING", "PRIMARY", "REFERENCES", "RETURNING", "SELECT", "SESSION_USER",
            "SOME", "SYMMETRIC", "TABLE", "THEN", "TO", "TRAILING", "TRUE", "UNION", "UNIQUE", "USER",
            "USING", "VARIADIC", "WHEN", "WHERE", "WINDOW", "WITH"
    );

    private static final Pattern PATTERN_AUTONOMOUS = Pattern.compile("(?i)PRAGMA\\s+AUTONOMOUS_TRANSACTION");
    private static final Pattern PATTERN_DYNAMIC_SQL = Pattern.compile("(?i)EXECUTE\\s+IMMEDIATE");
    private static final Pattern PATTERN_CURSOR = Pattern.compile("(?i)(SYS_REFCURSOR|\\bCURSOR\\b)");

    public AssessmentReport assess(DatabaseCatalog catalog) {
        var report = new AssessmentReport();
        report.setSchemaName(catalog.getSchemaName());
        report.setDatabaseType(catalog.getDialect() != null ? catalog.getDialect().name() : "ORACLE");

        int totalTables = catalog.getTables().size();
        int totalCols = 0;
        int totalConstraints = 0;
        int totalIndexes = 0;
        int totalPartitions = 0;
        int totalSpatialCols = 0;

        var reservedWarnings = new ArrayList<String>();
        var topTables = new ArrayList<AssessmentReport.TableMetric>();

        for (Table t : catalog.getTables()) {
            int colCount = t.getColumns().size();
            int constCount = t.getConstraints().size();
            int idxCount = t.getIndexes().size();

            totalCols += colCount;
            totalConstraints += constCount;
            totalIndexes += idxCount;

            if (t.getPartitionInfo() != null && !t.getPartitionInfo().getPartitions().isEmpty()) {
                totalPartitions += t.getPartitionInfo().getPartitions().size();
            }

            // Check table name reserved keyword
            if (PG_RESERVED_KEYWORDS.contains(t.getName().toUpperCase(Locale.ROOT))) {
                reservedWarnings.add("Table '" + t.getName() + "' matches PostgreSQL reserved keyword (double-quoted)");
            }

            // Scan columns for spatial and reserved keywords
            for (Column c : t.getColumns()) {
                String typeUpper = (c.getOriginalType() != null ? c.getOriginalType() : "").toUpperCase(Locale.ROOT);
                if (typeUpper.contains("SDO_GEOMETRY") || typeUpper.contains("GEOMETRY") || typeUpper.contains("GEOGRAPHY")) {
                    totalSpatialCols++;
                }
                if (PG_RESERVED_KEYWORDS.contains(c.getName().toUpperCase(Locale.ROOT))) {
                    reservedWarnings.add("Table '" + t.getName() + "' contains column '" + c.getName() + "' (PostgreSQL reserved keyword - double-quoted)");
                }
            }

            long estRows = Math.max(100L, colCount * 50L);
            String estSize = String.format("%.1f MB", Math.max(0.1, (colCount * 0.05) + (idxCount * 0.02)));
            topTables.add(new AssessmentReport.TableMetric(t.getName(), estRows, estSize, colCount, idxCount));
        }

        // Sort top tables by columns + indexes descending
        topTables.sort((a, b) -> Integer.compare(b.columnCount() + b.indexCount(), a.columnCount() + a.indexCount()));
        report.setTopTablesByRows(topTables.stream().limit(5).toList());
        report.setTopTablesBySize(topTables.stream().limit(5).toList());

        int totalSeqs = catalog.getSequences().size();
        int totalViews = catalog.getViews().size();
        int totalTriggers = catalog.getTriggers().size();

        int totalFuncs = 0;
        int totalProcs = 0;
        int totalPkgs = 0;
        int totalPlSqlLines = 0;
        int autonomousTxCount = 0;
        int dynamicSqlCount = 0;
        int cursorCount = 0;

        for (PlSqlUnit unit : catalog.getPlSqlUnits()) {
            totalPlSqlLines += unit.getLineCount();
            switch (unit.getType()) {
                case FUNCTION -> totalFuncs++;
                case PROCEDURE -> totalProcs++;
                case PACKAGE_BODY, PACKAGE_SPEC -> totalPkgs++;
                default -> {}
            }

            String source = unit.getSourceCode() != null ? unit.getSourceCode() : "";
            if (PATTERN_AUTONOMOUS.matcher(source).find()) {
                autonomousTxCount++;
            }
            if (PATTERN_DYNAMIC_SQL.matcher(source).find()) {
                dynamicSqlCount++;
            }
            if (PATTERN_CURSOR.matcher(source).find()) {
                cursorCount++;
            }
        }

        report.setTotalTables(totalTables);
        report.setTotalColumns(totalCols);
        report.setTotalConstraints(totalConstraints);
        report.setTotalIndexes(totalIndexes);
        report.setTotalSequences(totalSeqs);
        report.setTotalViews(totalViews);
        report.setTotalTriggers(totalTriggers);
        report.setTotalFunctions(totalFuncs);
        report.setTotalProcedures(totalProcs);
        report.setTotalPackages(totalPkgs);
        report.setTotalPlSqlLines(totalPlSqlLines);
        report.setTotalInvalidObjects(catalog.getInvalidObjects().size());

        // Calculate Cost Units
        double tableCost = (totalTables * COST_TABLE) + (totalCols * COST_COLUMN);
        double constraintCost = totalConstraints * COST_CONSTRAINT;
        double indexCost = totalIndexes * COST_INDEX;
        double seqCost = totalSeqs * COST_SEQUENCE;
        double viewCost = totalViews * COST_VIEW;
        double triggerCost = totalTriggers * COST_TRIGGER;
        double plsqlCost = (totalFuncs * COST_FUNCTION) + (totalProcs * COST_PROCEDURE) +
                           (totalPkgs * COST_PACKAGE) + (totalPlSqlLines * COST_PER_PLSQL_LINE);
        double spatialCost = totalSpatialCols * 4.5;

        double totalUnits = tableCost + constraintCost + indexCost + seqCost + viewCost + triggerCost + plsqlCost + spatialCost;
        report.setTotalMigrationCostUnits(Math.round(totalUnits * 100.0) / 100.0);

        double humanDays = totalUnits / UNITS_PER_HUMAN_DAY;
        report.setEstimatedHumanDays(Math.round(humanDays * 10.0) / 10.0);

        // Difficulty classification
        if (totalUnits < 50) {
            report.setDifficultyLevel("LOW");
        } else if (totalUnits < 200) {
            report.setDifficultyLevel("MEDIUM");
        } else if (totalUnits < 800) {
            report.setDifficultyLevel("HIGH");
        } else {
            report.setDifficultyLevel("CRITICAL");
        }

        report.getObjectCountByType().put("TABLE", totalTables);
        if (totalPartitions > 0) {
            report.getObjectCountByType().put("TABLE PARTITION", totalPartitions);
        }
        report.getObjectCountByType().put("COLUMN", totalCols);
        report.getObjectCountByType().put("CONSTRAINT", totalConstraints);
        report.getObjectCountByType().put("INDEX", totalIndexes);
        report.getObjectCountByType().put("SEQUENCE", totalSeqs);
        report.getObjectCountByType().put("VIEW", totalViews);
        report.getObjectCountByType().put("TRIGGER", totalTriggers);
        report.getObjectCountByType().put("PLSQL_UNIT", totalFuncs + totalProcs + totalPkgs);

        report.getCostUnitsByType().put("TABLES", tableCost);
        report.getCostUnitsByType().put("CONSTRAINTS", constraintCost);
        report.getCostUnitsByType().put("INDEXES", indexCost);
        report.getCostUnitsByType().put("VIEWS", viewCost);
        report.getCostUnitsByType().put("TRIGGERS", triggerCost);
        report.getCostUnitsByType().put("PLSQL", plsqlCost);
        if (spatialCost > 0) {
            report.getCostUnitsByType().put("SPATIAL", spatialCost);
        }

        // Granular Object Details
        report.getObjectDetails().put("TABLE", new AssessmentReport.ObjectAssessmentDetail(
                "TABLE", totalTables, 0, tableCost,
                totalTables + " table(s) analyzed with " + totalCols + " columns across schema " + catalog.getSchemaName() + ".",
                totalPartitions > 0 ? List.of(totalPartitions + " partition elements mapped to PostgreSQL declarative partitioning") : List.of("Standard relational tables")
        ));

        if (totalIndexes > 0) {
            report.getObjectDetails().put("INDEX", new AssessmentReport.ObjectAssessmentDetail(
                    "INDEX", totalIndexes, 0, indexCost,
                    totalIndexes + " index(es) detected for foreign keys, uniqueness, and filtering.",
                    List.of("Indexes exported as PostgreSQL CREATE INDEX (B-Tree/GiST)")
            ));
        }

        if (totalViews > 0) {
            report.getObjectDetails().put("VIEW", new AssessmentReport.ObjectAssessmentDetail(
                    "VIEW", totalViews, 0, viewCost,
                    totalViews + " SQL view(s) detected.",
                    List.of("Transpiled to PostgreSQL CREATE OR REPLACE VIEW")
            ));
        }

        if (totalTriggers > 0) {
            report.getObjectDetails().put("TRIGGER", new AssessmentReport.ObjectAssessmentDetail(
                    "TRIGGER", totalTriggers, 0, triggerCost,
                    totalTriggers + " trigger(s) detected.",
                    List.of("Converted into trigger functions returning TRIGGER followed by CREATE TRIGGER")
            ));
        }

        int totalPlSql = totalFuncs + totalProcs + totalPkgs;
        if (totalPlSql > 0 || totalPlSqlLines > 0) {
            var plsqlRemarks = new ArrayList<String>();
            if (totalPkgs > 0) plsqlRemarks.add(totalPkgs + " package(s) mapped into dedicated schema namespaces");
            if (autonomousTxCount > 0) plsqlRemarks.add(autonomousTxCount + " PRAGMA AUTONOMOUS_TRANSACTION requires pg_background/dblink");
            if (dynamicSqlCount > 0) plsqlRemarks.add(dynamicSqlCount + " dynamic SQL (EXECUTE IMMEDIATE) calls detected");
            if (plsqlRemarks.isEmpty()) plsqlRemarks.add("Stored logic transpiled to PostgreSQL PL/pgSQL");

            report.getObjectDetails().put("PLSQL", new AssessmentReport.ObjectAssessmentDetail(
                    "PLSQL", totalPlSql, catalog.getInvalidObjects().size(), plsqlCost,
                    totalPlSql + " procedural units totaling " + totalPlSqlLines + " lines of code.",
                    plsqlRemarks
            ));
        }

        if (totalSpatialCols > 0) {
            report.getObjectDetails().put("SPATIAL", new AssessmentReport.ObjectAssessmentDetail(
                    "SPATIAL", totalSpatialCols, 0, spatialCost,
                    totalSpatialCols + " spatial geometry columns found.",
                    List.of("Mapped to PostGIS geometry and GiST spatial indexes")
            ));
        }

        // Granular PL/SQL detail
        report.setPlSqlDetail(new AssessmentReport.PlSqlAssessmentDetail(
                totalPkgs, totalProcs, totalFuncs, totalTriggers, totalPlSqlLines,
                autonomousTxCount, dynamicSqlCount, cursorCount, plsqlCost
        ));

        // Spatial detail
        if (totalSpatialCols > 0) {
            report.setSpatialDetail(new AssessmentReport.SpatialAssessmentDetail(
                    totalSpatialCols, Math.max(1, totalSpatialCols / 2), Math.max(1, totalSpatialCols / 2), 0,
                    "CREATE EXTENSION IF NOT EXISTS postgis;"
            ));
        }

        report.setReservedKeywordWarnings(reservedWarnings);

        // Dynamic Recommendations
        var recs = new ArrayList<AssessmentReport.MigrationRecommendation>();
        if (totalPkgs > 0) {
            recs.add(new AssessmentReport.MigrationRecommendation(
                    "ARCHITECTURE",
                    "Emulate Oracle Packages with PostgreSQL Schemas",
                    "PostgreSQL does not natively support package specifications and bodies. DB2DB organizes package functions into isolated schema namespaces.",
                    "MEDIUM"
            ));
        }
        if (autonomousTxCount > 0) {
            recs.add(new AssessmentReport.MigrationRecommendation(
                    "TRANSACTIONS",
                    "Rewrite Autonomous Transactions with pg_background",
                    "Found " + autonomousTxCount + " PRAGMA AUTONOMOUS_TRANSACTION statements. In PostgreSQL, rewrite using the pg_background extension or dblink.",
                    "HIGH"
            ));
        }
        if (totalSpatialCols > 0) {
            recs.add(new AssessmentReport.MigrationRecommendation(
                    "SPATIAL",
                    "Enable PostGIS Extension Before Schema DDL",
                    "Found " + totalSpatialCols + " spatial columns. Run 'CREATE EXTENSION postgis;' on target database before loading DDL.",
                    "LOW"
            ));
        }
        if (totalTables > 10) {
            recs.add(new AssessmentReport.MigrationRecommendation(
                    "DATA_STREAMING",
                    "Stream Tables in Parallel Using JDK 25 Virtual Threads",
                    "Leverage DB2DB high-throughput COPY streaming engine with concurrency level 8+ for rapid data transfer.",
                    "LOW"
            ));
        }
        if (totalTriggers > 0) {
            recs.add(new AssessmentReport.MigrationRecommendation(
                    "TRIGGERS",
                    "Separate Trigger Execution Logic into Functions",
                    "PostgreSQL separates trigger functions from trigger bindings. DB2DB automatically generates both definitions.",
                    "LOW"
            ));
        }
        if (recs.isEmpty()) {
            recs.add(new AssessmentReport.MigrationRecommendation(
                    "MIGRATION_READY",
                    "Schema is Fully Migration Ready",
                    "No complex edge cases detected. Proceed to DDL export and table data streaming.",
                    "LOW"
            ));
        }
        report.setRecommendations(recs);

        return report;
    }

    /**
     * Creates a comprehensive showcase assessment report modeled after an enterprise
     * Oracle ERP/Financial core database to present all reporting features.
     */
    public AssessmentReport createSampleReport() {
        var report = new AssessmentReport();
        report.setSchemaName("FINANCE_CORE");
        report.setDatabaseType("ORACLE");
        report.setDatabaseVersion("Oracle Database 19c Enterprise Edition Release 19.3.0.0.0 - 64bit Production");
        report.setDatabaseSize("184.6 GB");
        report.setTotalRows(4_852_400L);

        report.setTotalTables(84);
        report.setTotalColumns(1142);
        report.setTotalConstraints(312);
        report.setTotalIndexes(198);
        report.setTotalSequences(26);
        report.setTotalViews(42);
        report.setTotalTriggers(18);
        report.setTotalFunctions(34);
        report.setTotalProcedures(28);
        report.setTotalPackages(12);
        report.setTotalPlSqlLines(8450);
        report.setTotalInvalidObjects(2);

        // Object count map
        report.getObjectCountByType().put("TABLE", 84);
        report.getObjectCountByType().put("TABLE PARTITION", 36);
        report.getObjectCountByType().put("COLUMN", 1142);
        report.getObjectCountByType().put("CONSTRAINT", 312);
        report.getObjectCountByType().put("INDEX", 198);
        report.getObjectCountByType().put("SEQUENCE", 26);
        report.getObjectCountByType().put("VIEW", 36);
        report.getObjectCountByType().put("MATERIALIZED VIEW", 6);
        report.getObjectCountByType().put("TRIGGER", 18);
        report.getObjectCountByType().put("PACKAGE", 12);
        report.getObjectCountByType().put("PROCEDURE", 28);
        report.getObjectCountByType().put("FUNCTION", 34);
        report.getObjectCountByType().put("TYPE", 8);
        report.getObjectCountByType().put("SYNONYM", 14);
        report.getObjectCountByType().put("DATABASE LINK", 3);

        // Cost Units Calculation
        double tableCost = 198.2;
        double constraintCost = 156.0;
        double indexCost = 112.5;
        double viewCost = 84.0;
        double triggerCost = 72.0;
        double plsqlCost = 285.5;
        double spatialCost = 18.0;

        double totalUnits = tableCost + constraintCost + indexCost + viewCost + triggerCost + plsqlCost + spatialCost;
        report.setTotalMigrationCostUnits(Math.round(totalUnits * 10.0) / 10.0);
        report.setEstimatedHumanDays(Math.round((totalUnits / UNITS_PER_HUMAN_DAY) * 10.0) / 10.0);
        report.setDifficultyLevel("HIGH");

        report.getCostUnitsByType().put("TABLES & COLUMNS", tableCost);
        report.getCostUnitsByType().put("CONSTRAINTS & KEYS", constraintCost);
        report.getCostUnitsByType().put("INDEXES & BITMAPS", indexCost);
        report.getCostUnitsByType().put("VIEWS & MVIEWS", viewCost);
        report.getCostUnitsByType().put("TRIGGERS", triggerCost);
        report.getCostUnitsByType().put("PL/SQL PACKAGES & CODE", plsqlCost);
        report.getCostUnitsByType().put("SPATIAL & POSTGIS", spatialCost);

        // Detailed Object Breakdown
        report.getObjectDetails().put("TABLE", new AssessmentReport.ObjectAssessmentDetail(
                "TABLE", 84, 0, tableCost,
                "84 standard tables including 36 range/list partition elements and 4 global temporary tables.",
                List.of(
                        "Range partitioning detected on GL_TRANSACTIONS by TRAN_DATE (converted to PostgreSQL native declarative partitioning)",
                        "List partitioning detected on INVOICE_ARCHIVE by REGION_CODE",
                        "4 Global Temporary Tables (PRESERVE ROWS) converted to PostgreSQL TEMPORARY tables or session schemas"
                )
        ));

        report.getObjectDetails().put("INDEX", new AssessmentReport.ObjectAssessmentDetail(
                "INDEX", 198, 0, indexCost,
                "152 standard B-tree indexes, 24 Bitmap indexes, 16 Function-based indexes, and 6 Reverse-key indexes.",
                List.of(
                        "24 Bitmap indexes detected: Oracle bitmap indexes will be transpiled to PostgreSQL btree_gin or bloom indexes",
                        "16 Function-based indexes (e.g. UPPER(EMAIL), TRUNC(CREATED_DATE)): fully supported in PostgreSQL with explicit parens",
                        "6 Reverse-key indexes: mapped to standard B-Tree or pg_trgm expression indexes"
                )
        ));

        report.getObjectDetails().put("VIEW", new AssessmentReport.ObjectAssessmentDetail(
                "VIEW", 42, 1, viewCost,
                "36 standard SQL views, 6 Materialized Views (2 configured with REFRESH ON COMMIT).",
                List.of(
                        "6 Materialized Views exported as PostgreSQL MATERIALIZED VIEW with REFRESH MATERIALIZED VIEW CONCURRENTLY",
                        "2 Materialized Views have REFRESH ON COMMIT: Oracle commit-refresh must be emulated using AFTER INSERT/UPDATE triggers",
                        "1 View has compilation error in source catalog (VIEW_CORP_REV_2020: ORA-00942 table not found)"
                )
        ));

        report.getObjectDetails().put("TRIGGER", new AssessmentReport.ObjectAssessmentDetail(
                "TRIGGER", 18, 0, triggerCost,
                "12 BEFORE INSERT/UPDATE row triggers, 4 INSTEAD OF view triggers, and 2 COMPOUND triggers.",
                List.of(
                        "In PostgreSQL, triggers require a separate trigger function returning TRIGGER before CREATE TRIGGER",
                        "2 Compound triggers: Oracle compound triggers split into BEFORE ROW and AFTER STATEMENT trigger pairs",
                        "All sequences referenced inside :NEW.ID := SEQ.NEXTVAL converted to IDENTITY or DEFAULT nextval('seq')"
                )
        ));

        report.getObjectDetails().put("PLSQL", new AssessmentReport.ObjectAssessmentDetail(
                "PLSQL", 74, 1, plsqlCost,
                "12 Packages (spec + body), 28 Stored Procedures, 34 User-Defined Functions totaling 8,450 lines of code.",
                List.of(
                        "Oracle Packages transpiled into PostgreSQL schemas (e.g., pkg_gl_ledger.func() -> finance_core_pkg_gl_ledger.func())",
                        "3 PRAGMA AUTONOMOUS_TRANSACTION occurrences detected: must be rewritten using dblink or pg_background",
                        "8 Dynamic SQL occurrences (EXECUTE IMMEDIATE) requiring parameter verification",
                        "1 Invalid Package: PKG_LEGACY_IMPORT has unresolved dependencies"
                )
        ));

        report.getObjectDetails().put("SPATIAL", new AssessmentReport.ObjectAssessmentDetail(
                "SPATIAL", 4, 0, spatialCost,
                "4 MDSYS.SDO_GEOMETRY columns detected across BRANCH_OFFICES and LOGISTICS_TERRITORIES.",
                List.of(
                        "Spatial columns converted to PostGIS geometry(Point, 4326) and geometry(Polygon, 4326)",
                        "Spatial indexes (MDSYS.SPATIAL_INDEX) converted to PostgreSQL GiST indexes"
                )
        ));

        // Top tables by row count
        report.setTopTablesByRows(List.of(
                new AssessmentReport.TableMetric("GL_TRANSACTIONS_PART", 2_450_000L, "78.4 GB", 38, 12),
                new AssessmentReport.TableMetric("AUDIT_LOG_EVENTS", 1_120_000L, "32.1 GB", 16, 4),
                new AssessmentReport.TableMetric("INVOICE_LINE_ITEMS", 640_000L, "24.6 GB", 28, 8),
                new AssessmentReport.TableMetric("CUSTOMER_PAYMENTS", 412_000L, "16.8 GB", 22, 6),
                new AssessmentReport.TableMetric("PRODUCT_CATALOG", 125_000L, "4.2 GB", 34, 5)
        ));

        // Top tables by size
        report.setTopTablesBySize(List.of(
                new AssessmentReport.TableMetric("GL_TRANSACTIONS_PART", 2_450_000L, "78.4 GB", 38, 12),
                new AssessmentReport.TableMetric("AUDIT_LOG_EVENTS", 1_120_000L, "32.1 GB", 16, 4),
                new AssessmentReport.TableMetric("ATTACHMENT_BLOBS", 42_000L, "28.5 GB", 8, 2),
                new AssessmentReport.TableMetric("INVOICE_LINE_ITEMS", 640_000L, "24.6 GB", 28, 8),
                new AssessmentReport.TableMetric("CUSTOMER_PAYMENTS", 412_000L, "16.8 GB", 22, 6)
        ));

        // PL/SQL granular detail
        report.setPlSqlDetail(new AssessmentReport.PlSqlAssessmentDetail(
                12, 28, 34, 18, 8450, 3, 8, 14, plsqlCost
        ));

        // Spatial granular detail
        report.setSpatialDetail(new AssessmentReport.SpatialAssessmentDetail(
                4, 2, 2, 0,
                "CREATE EXTENSION IF NOT EXISTS postgis;"
        ));

        // Reserved keywords
        report.setReservedKeywordWarnings(List.of(
                "Table 'USER_ROLES' contains column 'USER' (PostgreSQL reserved keyword - double-quoted)",
                "Table 'PURCHASE_ORDERS' contains column 'ORDER' (PostgreSQL reserved keyword - double-quoted)",
                "Table 'EXPENSE_CATEGORIES' contains column 'OFFSET' (PostgreSQL reserved keyword - double-quoted)",
                "Table 'ORGANIZATION_UNITS' contains column 'GROUP' (PostgreSQL reserved keyword - double-quoted)"
        ));

        // Recommendations
        report.setRecommendations(List.of(
                new AssessmentReport.MigrationRecommendation(
                        "ARCHITECTURE",
                        "Emulate Oracle Packages with PostgreSQL Schemas",
                        "PostgreSQL lacks native PACKAGE encapsulation. DB2DB automates decomposing package functions into dedicated schema namespaces while preserving call semantics.",
                        "MEDIUM"
                ),
                new AssessmentReport.MigrationRecommendation(
                        "CONCURRENCY",
                        "Rewrite Autonomous Transactions with pg_background",
                        "Found 3 instances of PRAGMA AUTONOMOUS_TRANSACTION in PKG_AUDIT_TRAIL. PostgreSQL transactions are strictly bounded; rewrite via pg_background or dblink independent connections.",
                        "HIGH"
                ),
                new AssessmentReport.MigrationRecommendation(
                        "STORAGE",
                        "Migrate Bitmap Indexes to btree_gin or Bloom Indexes",
                        "Found 24 Bitmap indexes. PostgreSQL does not have read-mostly bitmap indexes; enable btree_gin extension or BRIN indexes for high-cardinality multi-column filtering.",
                        "MEDIUM"
                ),
                new AssessmentReport.MigrationRecommendation(
                        "SPATIAL",
                        "Enable PostGIS Extension Before Schema DDL",
                        "Found 4 SDO_GEOMETRY columns. Run 'CREATE EXTENSION postgis;' on target database before applying tables containing spatial geometries.",
                        "LOW"
                ),
                new AssessmentReport.MigrationRecommendation(
                        "DATA_STREAMING",
                        "Use Parallel Copy Streaming with Virtual Threads",
                        "For large tables (>1M rows, e.g. GL_TRANSACTIONS_PART), utilize DB2DB high-throughput streaming engine with concurrency level 16+.",
                        "LOW"
                )
        ));

        return report;
    }
}
